package com.gymtracker.app.work

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gymtracker.app.BuildConfig
import com.gymtracker.app.data.backup.BackupCodec
import com.gymtracker.app.data.local.AppDatabase
import com.gymtracker.app.di.ServiceLocator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 직전 백업보다 이만큼 이하로 쪼그라들면 데이터 유실을 의심한다. */
private const val SUSPICIOUS_SHRINK_RATIO = 0.2

/**
 * 하루 1회 자동 백업. WorkManager가 지정 시각 근처에 깨워서 실행한다.
 *
 * 실패를 대하는 원칙: 사용자가 신경 쓸 필요 없는 실패는 success로 삼키고 메시지만 남긴다.
 * 영구 실패(예: 보안 규칙 오설정)에 retry를 무한 반복하면 배터리만 태운다.
 */
class CloudBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val prefs = ServiceLocator.prefs(applicationContext)
        val cloud = ServiceLocator.cloudBackupService()

        // 토글이 꺼졌는데 예약이 남아 있는 경우. cancel이 호출됐어야 하지만 방어적으로 확인한다.
        if (!prefs.autoBackupEnabled) return Result.success()

        if (!cloud.isSignedIn) {
            // failure로 두면 영구 실패로 남는다. 로그아웃은 사용자의 선택이므로 조용히 넘긴다.
            prefs.lastAutoBackupResult = "자동 백업 건너뜀: 로그인이 필요합니다"
            return Result.success()
        }

        // 오늘 이미 올렸으면 넘긴다. WorkManager의 부정확한 발화와 재시도를 한 번에 흡수한다.
        if (alreadyBackedUpToday(prefs.lastUploadAtMillis)) return Result.success()

        val repository = ServiceLocator.repository(applicationContext)
        return runCatching {
            val json = repository.exportBackup()
            // fallbackToDestructiveMigration이 켜져 있어서 스키마가 바뀌면 Room이 데이터를 말없이
            // 지운다. 그 직후의 자동 백업이 빈 결과로 클라우드를 덮어쓰는 것만은 막아야 한다.
            if (looksLikeDataLoss(json.toByteArray().size, prefs.lastUploadRawBytes)) {
                error("데이터가 갑자기 줄어들어 자동 백업을 중단했습니다. 설정에서 직접 확인해주세요.")
            }
            cloud.upload(
                json = json,
                appVersionCode = BuildConfig.VERSION_CODE,
                dbVersion = AppDatabase.SCHEMA_VERSION,
                backupVersion = BackupCodec.VERSION,
                deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            )
        }.fold(
            onSuccess = { meta ->
                prefs.lastUploadAtMillis = System.currentTimeMillis()
                prefs.lastUploadRawBytes = meta.rawBytes
                prefs.rememberGeneration(meta)
                prefs.lastAutoBackupResult = "자동 백업 완료"
                Result.success()
            },
            onFailure = { e ->
                if (runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    prefs.lastAutoBackupResult = "자동 백업 실패: ${e.message}"
                    Result.success()
                }
            }
        )
    }

    private fun alreadyBackedUpToday(lastUploadAtMillis: Long): Boolean {
        if (lastUploadAtMillis <= 0L) return false
        val zone = ZoneId.systemDefault()
        val last = Instant.ofEpochMilli(lastUploadAtMillis).atZone(zone).toLocalDate()
        return last == LocalDate.now(zone)
    }

    /** 직전에 큰 백업을 올렸는데 지금 export가 눈에 띄게 작아졌으면 유실을 의심한다. */
    private fun looksLikeDataLoss(currentBytes: Int, lastBytes: Int): Boolean =
        lastBytes > 0 && currentBytes < lastBytes * SUSPICIOUS_SHRINK_RATIO

    companion object {
        const val UNIQUE_NAME = "cloud-auto-backup"
        private const val MAX_ATTEMPTS = 3
    }
}
