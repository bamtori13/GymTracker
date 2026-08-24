package com.gymtracker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.repository.WorkoutRepository
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

class SettingsViewModel(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val _status = MutableStateFlow(BackupStatus())
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

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
}
