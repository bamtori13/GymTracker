package com.gymtracker.app.di

import android.content.Context
import com.gymtracker.app.data.cloud.CloudBackupService
import com.gymtracker.app.data.local.AppDatabase
import com.gymtracker.app.data.prefs.CloudPrefs
import com.gymtracker.app.data.repository.WorkoutRepository

/**
 * WorkManager Worker와 Activity가 같은 Repository/서비스를 공유하기 위한 최소 조립소.
 *
 * Application 서브클래스를 두지 않는 이유: AppDatabase.getInstance()가 이미 프로세스 싱글턴이고
 * WorkoutRepository는 그걸 감싼 무상태 래퍼라 Application이 지켜야 할 상태가 없다.
 * 매니페스트를 건드릴 필요도, WorkManager가 띄운 프로세스에서 Worker가 Application.onCreate와
 * 경합할 걱정도 없어진다.
 */
object ServiceLocator {

    @Volatile
    private var repository: WorkoutRepository? = null

    @Volatile
    private var cloudBackupService: CloudBackupService? = null

    @Volatile
    private var prefs: CloudPrefs? = null

    fun repository(context: Context): WorkoutRepository =
        repository ?: synchronized(this) {
            repository ?: WorkoutRepository(AppDatabase.getInstance(context.applicationContext))
                .also { repository = it }
        }

    fun cloudBackupService(): CloudBackupService =
        cloudBackupService ?: synchronized(this) {
            cloudBackupService ?: CloudBackupService().also { cloudBackupService = it }
        }

    fun prefs(context: Context): CloudPrefs =
        prefs ?: synchronized(this) {
            prefs ?: CloudPrefs(context.applicationContext).also { prefs = it }
        }
}
