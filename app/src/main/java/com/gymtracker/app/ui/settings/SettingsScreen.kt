package com.gymtracker.app.ui.settings

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.BuildConfig
import com.gymtracker.app.data.backup.BackupCodec
import com.gymtracker.app.data.cloud.CloudBackupService
import com.gymtracker.app.data.cloud.RemoteBackupMeta
import com.gymtracker.app.data.cloud.requestGoogleIdToken
import com.gymtracker.app.data.local.AppDatabase
import com.gymtracker.app.data.prefs.CloudPrefs
import com.gymtracker.app.data.repository.WorkoutRepository
import com.gymtracker.app.work.BackupScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 마지막 백업/복원 결과. 화면 아래 안내문으로 그대로 보여준다. */
data class BackupStatus(
    val message: String = "",
    val isError: Boolean = false,
    val busy: Boolean = false
)

/** 클라우드 백업 카드가 그리는 데 필요한 것 전부. */
data class CloudState(
    val signedIn: Boolean = false,
    val email: String? = null,
    val autoBackupEnabled: Boolean = false,
    val backupHour: Int = 3,
    val lastUploadAtMillis: Long = 0L,
    val lastUploadRawBytes: Int = 0,
    val remote: RemoteBackupMeta? = null,
    /** 클라우드에 이 기기가 모르는 백업이 있는가. 배지 표시용이고, 아무것도 자동 실행하지 않는다. */
    val cloudIsNewer: Boolean = false,
    val lastAutoBackupResult: String = ""
)

class SettingsViewModel(
    private val repository: WorkoutRepository,
    private val cloud: CloudBackupService,
    private val prefs: CloudPrefs
) : ViewModel() {

    private val _status = MutableStateFlow(BackupStatus())
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    private val _cloudState = MutableStateFlow(CloudState())
    val cloudState: StateFlow<CloudState> = _cloudState.asStateFlow()

    init {
        readPrefsIntoState()
    }

    // ------------------------------------------------------------ 로컬 파일 백업

    /**
     * 내보내기. 파일 쓰기 자체는 화면(SAF Uri를 가진 쪽)에서 하고,
     * 여기서는 JSON 문자열만 만들어 넘긴다 — ViewModel이 Android Uri를 몰라도 되게.
     */
    fun export(write: (String) -> Unit) {
        _status.value = BackupStatus(busy = true)
        viewModelScope.launch {
            runCatching {
                val json = repository.exportBackup()
                write(json)
                json.length
            }.onSuccess { size ->
                _status.value = BackupStatus("내보내기 완료 (${size / 1024 + 1}KB)")
            }.onFailure { e ->
                _status.value = BackupStatus("내보내기 실패: ${e.message}", isError = true)
            }
        }
    }

    /** 가져오기. 기존 데이터를 전부 덮어쓴다 — 화면에서 확인 팝업을 거친 뒤 호출한다. */
    fun import(read: () -> String) {
        _status.value = BackupStatus(busy = true)
        viewModelScope.launch {
            runCatching {
                repository.importBackup(read())
            }.onSuccess {
                _status.value = BackupStatus("가져오기 완료 — 다른 탭으로 이동하면 반영됩니다")
            }.onFailure { e ->
                _status.value = BackupStatus("가져오기 실패: ${e.message}", isError = true)
            }
        }
    }

    // ------------------------------------------------------------ 클라우드

    /** activityContext는 계정 선택 시트를 띄우기 위한 것. Composable의 LocalContext를 넘긴다. */
    fun signIn(activityContext: Context) {
        _status.value = BackupStatus(busy = true)
        viewModelScope.launch {
            runCatching {
                cloud.signInWithGoogleIdToken(requestGoogleIdToken(activityContext))
            }.onSuccess { email ->
                _status.value = BackupStatus("${email ?: "계정"}으로 로그인했습니다")
                readPrefsIntoState()
                refreshRemote()
            }.onFailure { e ->
                _status.value = BackupStatus("로그인 실패: ${e.message}", isError = true)
            }
        }
    }

    fun signOut(context: Context) {
        cloud.signOut()
        // 다른 계정으로 다시 들어올 수 있으니 "내가 아는 세대"를 비운다.
        prefs.lastKnownRemoteGeneration = null
        prefs.autoBackupEnabled = false
        BackupScheduler.cancel(context)
        _status.value = BackupStatus("로그아웃했습니다")
        readPrefsIntoState()
    }

    /**
     * 클라우드 매니페스트를 다시 읽어 배지 상태를 갱신한다.
     * 실패는 조용히 무시한다 — 오프라인은 정상 상태이고, 탭을 열자마자 빨간 오류를 띄울 일이 아니다.
     */
    fun refreshRemote() {
        if (!cloud.isSignedIn) return
        viewModelScope.launch {
            runCatching { cloud.fetchRemoteMeta() }.onSuccess { meta ->
                _cloudState.value = _cloudState.value.copy(
                    remote = meta,
                    cloudIsNewer = prefs.isCloudNewer(meta)
                )
            }
        }
    }

    fun uploadNow() {
        _status.value = BackupStatus(busy = true)
        viewModelScope.launch {
            runCatching {
                cloud.upload(
                    json = repository.exportBackup(),
                    appVersionCode = BuildConfig.VERSION_CODE,
                    dbVersion = AppDatabase.SCHEMA_VERSION,
                    backupVersion = BackupCodec.VERSION,
                    deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
                )
            }.onSuccess { meta ->
                prefs.lastUploadAtMillis = System.currentTimeMillis()
                prefs.lastUploadRawBytes = meta.rawBytes
                prefs.rememberGeneration(meta)
                _status.value = BackupStatus("클라우드 백업 완료 (${meta.rawBytes / 1024 + 1}KB)")
                readPrefsIntoState()
                _cloudState.value = _cloudState.value.copy(remote = meta, cloudIsNewer = false)
            }.onFailure { e ->
                _status.value = BackupStatus("클라우드 백업 실패: ${e.message}", isError = true)
            }
        }
    }

    fun restoreFromCloud() {
        _status.value = BackupStatus(busy = true)
        viewModelScope.launch {
            runCatching {
                val meta = cloud.fetchRemoteMeta() ?: error("클라우드에 백업이 없습니다")
                // 더 새 앱에서 만든 백업은 지금 코드가 온전히 읽어낸다고 보장할 수 없다.
                if (meta.backupVersion > BackupCodec.VERSION) {
                    error("이 백업은 더 새 버전의 앱에서 만들어졌습니다. 앱을 먼저 업데이트해주세요.")
                }
                val json = cloud.download() ?: error("클라우드에 백업이 없습니다")
                repository.importBackup(json)
                meta
            }.onSuccess { meta ->
                // 복원한 세대를 "내가 아는 세대"로 기록해서 배지를 내린다.
                prefs.rememberGeneration(meta)
                _status.value = BackupStatus("복원 완료 — 다른 탭으로 이동하면 반영됩니다")
                readPrefsIntoState()
                _cloudState.value = _cloudState.value.copy(remote = meta, cloudIsNewer = false)
            }.onFailure { e ->
                _status.value = BackupStatus("복원 실패: ${e.message}", isError = true)
            }
        }
    }

    fun setAutoBackup(context: Context, enabled: Boolean) {
        prefs.autoBackupEnabled = enabled
        if (enabled) BackupScheduler.reschedule(context, prefs.backupHour)
        else BackupScheduler.cancel(context)
        readPrefsIntoState()
    }

    fun setBackupHour(context: Context, hour: Int) {
        prefs.backupHour = hour
        if (prefs.autoBackupEnabled) BackupScheduler.reschedule(context, prefs.backupHour)
        readPrefsIntoState()
    }

    /** SharedPreferences는 Flow가 아니라서, 바뀔 때마다 직접 읽어 상태에 옮긴다. */
    private fun readPrefsIntoState() {
        _cloudState.value = _cloudState.value.copy(
            signedIn = cloud.isSignedIn,
            email = cloud.currentUserEmail,
            autoBackupEnabled = prefs.autoBackupEnabled,
            backupHour = prefs.backupHour,
            lastUploadAtMillis = prefs.lastUploadAtMillis,
            lastUploadRawBytes = prefs.lastUploadRawBytes,
            lastAutoBackupResult = prefs.lastAutoBackupResult
        )
    }
}
