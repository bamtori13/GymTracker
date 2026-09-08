package com.gymtracker.app.data.cloud

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** 클라우드에 올라가 있는 백업의 요약. Firestore 매니페스트 문서를 그대로 옮긴 것. */
data class RemoteBackupMeta(
    /** 이 백업의 신원. 배지 판정은 시각이 아니라 이 값의 동일성으로 한다. */
    val generation: String,
    val chunkCount: Int,
    val encoding: String,
    val totalChars: Int,
    val rawBytes: Int,
    val sha256: String,
    val backupVersion: Int,
    val dbVersion: Int,
    val appVersionCode: Int,
    val deviceName: String,
    /** 서버 시계 기준 업로드 시각. 표시용. */
    val updatedAtMillis: Long
)

/**
 * 백업 JSON 한 덩어리를 Firestore에 올리고 내린다. Room은 그대로 주인이고, 여기는 운반만 한다.
 *
 * 문서 배치:
 *   users/{uid}/backups/current                     <- 살아있는 백업을 가리키는 매니페스트
 *   users/{uid}/backups/{generation}                <- 세대별 매니페스트
 *   users/{uid}/backups/{generation}/chunks/{index} <- { i, data }
 *
 * Firestore에는 rename도, 1 MiB를 넘는 트랜잭션도 없으므로 원자성은 **쓰기 순서**로 얻는다.
 * 청크 → 세대 매니페스트 → 마지막에 current. current는 단일 문서 쓰기라 원자적이고 복원은
 * current만 신뢰하므로, 중간에 죽어도 current는 여전히 직전 완성본을 가리킨다.
 */
class CloudBackupService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    val isSignedIn: Boolean get() = auth.currentUser != null
    val currentUserEmail: String? get() = auth.currentUser?.email

    fun signOut() = auth.signOut()

    /** Credential Manager에서 받은 Google ID 토큰으로 Firebase에 로그인한다. 이메일을 돌려준다. */
    suspend fun signInWithGoogleIdToken(idToken: String): String? {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = withTimeout(AUTH_TIMEOUT_MS) { auth.signInWithCredential(credential).await() }
        return result.user?.email
    }

    suspend fun fetchRemoteMeta(): RemoteBackupMeta? {
        val snapshot = withTimeout(READ_TIMEOUT_MS) {
            // SERVER로 읽어야 한다 — Firestore 오프라인 캐시가 기본으로 켜져 있어서 평범한 get()은
            // 이 기기가 마지막에 본 값을 돌려주고, 그러면 다른 기기가 올린 백업을 영원히 못 본다.
            backupsCollection().document(CURRENT_DOC).get(Source.SERVER).await()
        }
        return if (snapshot.exists()) snapshot.toMeta() else null
    }

    /** 백업을 올리고, 방금 쓴 세대의 매니페스트를 돌려준다. */
    suspend fun upload(
        json: String,
        appVersionCode: Int,
        dbVersion: Int,
        backupVersion: Int,
        deviceName: String
    ): RemoteBackupMeta {
        val backups = backupsCollection()
        val encoded = BackupPayload.encode(json)
        val generation = System.currentTimeMillis().toString()
        val generationDoc = backups.document(generation)

        // 1) 청크. 500 op 한계보다 Commit RPC의 요청 크기 상한에 먼저 걸리므로 둘 다 본다.
        var batch = firestore.batch()
        var opCount = 0
        var byteCount = 0
        encoded.chunks.forEachIndexed { index, data ->
            batch.set(
                generationDoc.collection(CHUNKS_COLLECTION).document(index.toString()),
                mapOf("i" to index, "data" to data)
            )
            opCount++
            byteCount += data.length
            if (opCount >= MAX_BATCH_OPS || byteCount >= MAX_BATCH_BYTES) {
                withTimeout(WRITE_TIMEOUT_MS) { batch.commit().await() }
                batch = firestore.batch()
                opCount = 0
                byteCount = 0
            }
        }
        if (opCount > 0) withTimeout(WRITE_TIMEOUT_MS) { batch.commit().await() }

        // 2) 세대 매니페스트. 여기까지는 완성됐지만 아무도 이 세대를 가리키지 않는다.
        val manifest = mapOf(
            "generation" to generation,
            "chunkCount" to encoded.chunks.size,
            "encoding" to BackupPayload.ENCODING,
            "totalChars" to encoded.totalChars,
            "rawBytes" to encoded.rawBytes,
            "sha256" to encoded.sha256,
            "backupVersion" to backupVersion,
            "dbVersion" to dbVersion,
            "appVersionCode" to appVersionCode,
            "deviceName" to deviceName,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        withTimeout(WRITE_TIMEOUT_MS) { generationDoc.set(manifest).await() }

        // 3) 마지막에 current를 옮긴다. 이 한 번의 쓰기가 백업을 "살아있게" 만든다.
        withTimeout(WRITE_TIMEOUT_MS) { backups.document(CURRENT_DOC).set(manifest).await() }

        deleteOldGenerations(keepGeneration = generation)

        return withTimeout(READ_TIMEOUT_MS) {
            backups.document(CURRENT_DOC).get(Source.SERVER).await()
        }.toMeta()
    }

    /** 클라우드 백업 JSON. 백업이 없으면 null. */
    suspend fun download(): String? {
        val backups = backupsCollection()
        val current = withTimeout(READ_TIMEOUT_MS) {
            backups.document(CURRENT_DOC).get(Source.SERVER).await()
        }
        if (!current.exists()) return null
        val meta = current.toMeta()
        if (meta.encoding != BackupPayload.ENCODING) {
            error("알 수 없는 백업 형식입니다 (${meta.encoding})")
        }

        val chunksCollection = backups.document(meta.generation).collection(CHUNKS_COLLECTION)
        val chunks = (0 until meta.chunkCount).map { index ->
            val doc = withTimeout(READ_TIMEOUT_MS) {
                chunksCollection.document(index.toString()).get(Source.SERVER).await()
            }
            doc.getString("data") ?: error("백업 데이터가 손상되었습니다 (조각 $index 없음)")
        }
        return BackupPayload.decode(chunks, meta.sha256, meta.totalChars)
    }

    /**
     * 옛 세대의 청크를 지운다. 여기서 실패해도 정확성에는 영향이 없고(current는 이미 새 세대를 가리킴)
     * 저장 용량만 낭비되므로 조용히 넘긴다.
     */
    private suspend fun deleteOldGenerations(keepGeneration: String) {
        runCatching {
            val backups = backupsCollection()
            val all = withTimeout(READ_TIMEOUT_MS) { backups.get(Source.SERVER).await() }
            all.documents
                .filter { it.id != CURRENT_DOC && it.id != keepGeneration }
                .forEach { old ->
                    val chunks = withTimeout(READ_TIMEOUT_MS) {
                        old.reference.collection(CHUNKS_COLLECTION).get(Source.SERVER).await()
                    }
                    val batch = firestore.batch()
                    chunks.documents.forEach { batch.delete(it.reference) }
                    batch.delete(old.reference)
                    withTimeout(WRITE_TIMEOUT_MS) { batch.commit().await() }
                }
        }
    }

    private fun backupsCollection() = firestore
        .collection(USERS_COLLECTION)
        .document(requireUid())
        .collection(BACKUPS_COLLECTION)

    private fun requireUid(): String =
        auth.currentUser?.uid ?: error("로그인이 필요합니다")

    private fun DocumentSnapshot.toMeta() = RemoteBackupMeta(
        generation = getString("generation") ?: id,
        chunkCount = (getLong("chunkCount") ?: 0L).toInt(),
        encoding = getString("encoding") ?: BackupPayload.ENCODING,
        totalChars = (getLong("totalChars") ?: 0L).toInt(),
        rawBytes = (getLong("rawBytes") ?: 0L).toInt(),
        sha256 = getString("sha256") ?: "",
        backupVersion = (getLong("backupVersion") ?: 0L).toInt(),
        dbVersion = (getLong("dbVersion") ?: 0L).toInt(),
        appVersionCode = (getLong("appVersionCode") ?: 0L).toInt(),
        deviceName = getString("deviceName") ?: "알 수 없는 기기",
        // serverTimestamp는 서버 왕복 직후엔 null일 수 있어서 로컬 시계로 대체한다.
        updatedAtMillis = getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
    )

    private companion object {
        const val USERS_COLLECTION = "users"
        const val BACKUPS_COLLECTION = "backups"
        const val CHUNKS_COLLECTION = "chunks"
        const val CURRENT_DOC = "current"

        /** 500 op 한계 아래로 여유를 둔 값. */
        const val MAX_BATCH_OPS = 400

        /** Commit 요청 크기 상한(약 10 MiB)에 여유를 둔 값. */
        const val MAX_BATCH_BYTES = 6_000_000

        // 오프라인 영속성 때문에 set()의 Task는 서버 ack를 무한정 기다린다.
        // 타임아웃이 없으면 Worker가 실행 창이 끝날 때까지 아무 진단도 없이 멈춘다.
        const val AUTH_TIMEOUT_MS = 60_000L
        const val READ_TIMEOUT_MS = 60_000L
        const val WRITE_TIMEOUT_MS = 120_000L
    }
}
