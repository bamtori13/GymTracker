package com.gymtracker.app.data.cloud

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** 인코딩 결과. 청크 목록과, 재조립이 제대로 됐는지 검증할 값들. */
data class EncodedBackup(
    val chunks: List<String>,
    /** base64 이전 gzip 바이트의 SHA-256 (hex). */
    val sha256: String,
    /** 청크 길이 합. 값싼 크기 검사용. */
    val totalChars: Int,
    /** 압축 전 원본 JSON 바이트 수. "1.2MB" 같은 표시에 쓴다. */
    val rawBytes: Int
)

/**
 * 백업 JSON을 Firestore 문서에 담을 수 있는 형태로 바꾼다: gzip → base64 → 일정 길이로 분할.
 *
 * base64가 선택이 아닌 이유: Firestore의 문서 1 MiB 제한은 **UTF-8 바이트**를 센다.
 * 한글 운동명·메모는 글자당 3바이트라 JSON 문자열을 글자 수로 자르면 한도를 넘어 쓰기가 거부되고,
 * 바이트 배열로 자르면 멀티바이트 문자가 반토막 난다. base64는 1글자 = 1바이트라
 * chunk.length가 곧 제한이 재는 값이 되어 계산이 정확해진다.
 *
 * gzip은 BackupCodec이 toString(2)로 예쁘게 찍은 JSON을 크게 줄여서 보통 청크 1~2개로 끝낸다.
 * Firestore 무료 한도는 문서 단위로 세므로 청크 수가 곧 비용이다.
 */
object BackupPayload {

    /** 매니페스트에 기록해서 나중에 형식이 바뀌어도 구분할 수 있게 한다. */
    const val ENCODING = "gzip+base64"

    /** 청크 하나의 최대 길이(=바이트). 1 MiB 한도에 필드명 오버헤드까지 여유를 둔 값. */
    const val CHUNK_CHARS = 700_000

    fun encode(json: String): EncodedBackup {
        val rawBytes = json.toByteArray()
        val gzipped = gzip(rawBytes)
        val base64 = Base64.encodeToString(gzipped, Base64.NO_WRAP)
        return EncodedBackup(
            chunks = base64.chunked(CHUNK_CHARS),
            sha256 = sha256Hex(gzipped),
            totalChars = base64.length,
            rawBytes = rawBytes.size
        )
    }

    /**
     * 청크를 이어붙여 원래 JSON으로 되돌린다.
     * 기대값이 주어지면 먼저 검증해서, 깨진 데이터가 DB를 덮어쓰지 못하게 막는다.
     */
    fun decode(chunks: List<String>, expectedSha256: String?, expectedChars: Int?): String {
        val base64 = chunks.joinToString("")
        if (expectedChars != null && base64.length != expectedChars) {
            error("백업 크기가 맞지 않습니다 (기대 $expectedChars, 실제 ${base64.length})")
        }
        val gzipped = Base64.decode(base64, Base64.NO_WRAP)
        if (expectedSha256 != null && sha256Hex(gzipped) != expectedSha256) {
            error("백업 무결성 검사에 실패했습니다")
        }
        return gunzip(gzipped).decodeToString()
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes() }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
