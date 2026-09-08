package com.gymtracker.app.data.prefs

import android.content.Context
import com.gymtracker.app.data.cloud.RemoteBackupMeta

/**
 * 클라우드 백업 설정과 마지막 동기화 흔적.
 * DataStore가 아니라 SharedPreferences인 이유: 스칼라 몇 개뿐이고, Worker가 시작할 때
 * **동기**로 읽어야 해서 Flow 표면이 오히려 걸리적거린다.
 */
class CloudPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("cloud_backup", Context.MODE_PRIVATE)

    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_ENABLED, value).apply()

    var backupHour: Int
        get() = prefs.getInt(KEY_HOUR, DEFAULT_HOUR).coerceIn(0, 23)
        set(value) = prefs.edit().putInt(KEY_HOUR, value.coerceIn(0, 23)).apply()

    /** 이 기기가 마지막으로 업로드한 로컬 시각. 0이면 한 번도 안 올림. 표시용. */
    var lastUploadAtMillis: Long
        get() = prefs.getLong(KEY_LAST_UPLOAD_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPLOAD_AT, value).apply()

    /** 마지막으로 올린 백업의 압축 전 크기. 다음 백업이 수상하게 작은지 판단하는 데도 쓴다. */
    var lastUploadRawBytes: Int
        get() = prefs.getInt(KEY_LAST_UPLOAD_BYTES, 0)
        set(value) = prefs.edit().putInt(KEY_LAST_UPLOAD_BYTES, value).apply()

    /** 이 기기가 마지막으로 올렸거나 복원한 세대. 배지 판정의 기준점. */
    var lastKnownRemoteGeneration: String?
        get() = prefs.getString(KEY_LAST_GENERATION, null)
        set(value) = prefs.edit().putString(KEY_LAST_GENERATION, value).apply()

    /** 자동 백업이 마지막으로 남긴 한 줄. 설정 화면에서 그대로 보여준다. */
    var lastAutoBackupResult: String
        get() = prefs.getString(KEY_LAST_AUTO_RESULT, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_AUTO_RESULT, value).apply()

    /**
     * 클라우드에 이 기기가 모르는 백업이 있는가.
     *
     * 시각이 아니라 generation 동일성으로 비교한다 — 기기 간 시계나 시간대가 어긋나면
     * 시각 비교는 틀린 답을 낸다. 반면 "클라우드의 살아있는 세대가 내가 마지막으로 쓰거나
     * 복원한 그 세대가 아니다"는 항상 정확하다.
     */
    fun isCloudNewer(remote: RemoteBackupMeta?): Boolean =
        remote != null && remote.generation != lastKnownRemoteGeneration

    /** 업로드/복원 성공 후 "내가 아는 세대"를 갱신한다. */
    fun rememberGeneration(meta: RemoteBackupMeta) {
        lastKnownRemoteGeneration = meta.generation
    }

    private companion object {
        const val DEFAULT_HOUR = 3
        const val KEY_AUTO_ENABLED = "autoBackupEnabled"
        const val KEY_HOUR = "backupHour"
        const val KEY_LAST_UPLOAD_AT = "lastUploadAtMillis"
        const val KEY_LAST_UPLOAD_BYTES = "lastUploadRawBytes"
        const val KEY_LAST_GENERATION = "lastKnownRemoteGeneration"
        const val KEY_LAST_AUTO_RESULT = "lastAutoBackupResult"
    }
}
