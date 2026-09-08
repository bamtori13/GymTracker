package com.gymtracker.app.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gymtracker.app.di.ServiceLocator
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * 자동 백업 예약 관리.
 *
 * 실제 실행 시각은 보장되지 않는다 — Doze, 앱 대기 버킷, 제조사 배터리 관리(삼성이 특히 공격적)로
 * 수십 분에서 몇 시간까지 늦어질 수 있고, 주기 실행은 직전 실행이 끝난 시점을 기준으로 다시 세므로
 * 시각이 서서히 밀린다. CloudBackupWorker의 "오늘 이미 올렸나" 검사가 이 부정확성을 흡수한다.
 */
object BackupScheduler {

    /** 앱을 열 때마다 호출. 이미 예약돼 있으면 건드리지 않는다. */
    fun ensureScheduled(context: Context) {
        val prefs = ServiceLocator.prefs(context)
        if (!prefs.autoBackupEnabled) return
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CloudBackupWorker.UNIQUE_NAME,
            // KEEP이어야 한다. 다른 정책을 쓰면 앱을 열 때마다 초기 지연이 리셋되고,
            // 매일 앱을 켜는 사용자에게는 백업이 영원히 돌지 않는다.
            ExistingPeriodicWorkPolicy.KEEP,
            buildRequest(prefs.backupHour)
        )
    }

    /** 토글을 켰거나 시각을 바꿨을 때. */
    fun reschedule(context: Context, hour: Int) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CloudBackupWorker.UNIQUE_NAME,
            // UPDATE는 주기 앵커를 그대로 두기 때문에 새 initialDelay가 무시되고
            // 시각 변경이 조용히 먹히지 않는다. 반드시 CANCEL_AND_REENQUEUE.
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            buildRequest(hour)
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(CloudBackupWorker.UNIQUE_NAME)
    }

    private fun buildRequest(hour: Int) =
        PeriodicWorkRequestBuilder<CloudBackupWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis(hour), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    // gzip한 백업은 작아서 UNMETERED까지 요구할 이유가 없다.
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

    /**
     * 다음 지정 시각까지 남은 밀리초.
     * plusHours(24)가 아니라 plusDays(1)이어야 서머타임 전환이 있는 지역에서도 맞는다.
     */
    internal fun initialDelayMillis(hour: Int, now: ZonedDateTime = ZonedDateTime.now()): Long {
        val target = now.truncatedTo(ChronoUnit.HOURS).withHour(hour.coerceIn(0, 23))
            .let { if (it.isAfter(now)) it else it.plusDays(1) }
        return Duration.between(now, target).toMillis()
    }
}
