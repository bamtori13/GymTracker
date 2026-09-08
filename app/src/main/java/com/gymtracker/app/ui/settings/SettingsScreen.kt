package com.gymtracker.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 설정 탭: 클라우드 백업(내 기기 간 동기화) + 로컬 파일 내보내기/가져오기.
 *
 * 로컬 파일 쪽은 SAF(문서 선택기)로 위치를 직접 고르게 해서 저장소 권한이 필요 없다.
 * 클라우드 쪽은 Google 로그인 후 Firestore에 백업 JSON을 올린다.
 */
@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val status by settingsViewModel.status.collectAsState()
    val cloud by settingsViewModel.cloudState.collectAsState()
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var confirmCloudRestore by remember { mutableStateOf(false) }
    var showHourPicker by remember { mutableStateOf(false) }

    // 탭을 열 때 클라우드 상태를 한 번 확인한다(실패는 조용히 무시된다).
    LaunchedEffect(Unit) { settingsViewModel.refreshRemote() }

    val createFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            settingsViewModel.export { json ->
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray())
                } ?: error("파일을 열 수 없습니다")
            }
        }
    }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingImportUri = uri }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("데이터 관리", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        CloudBackupCard(
            cloud = cloud,
            busy = status.busy,
            onSignIn = { settingsViewModel.signIn(context) },
            onSignOut = { settingsViewModel.signOut(context) },
            onUpload = { settingsViewModel.uploadNow() },
            onRestoreClick = { confirmCloudRestore = true },
            onAutoBackupChange = { settingsViewModel.setAutoBackup(context, it) },
            onHourClick = { showHourPicker = true }
        )

        SectionCard(
            title = "파일로 내보내기",
            description = "운동/루틴/기록 전체를 JSON 파일 하나로 저장합니다."
        ) {
            Button(
                onClick = { createFile.launch(defaultFileName()) },
                enabled = !status.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("백업 파일 저장")
            }
        }

        SectionCard(
            title = "파일에서 가져오기",
            description = "백업 파일로 되돌립니다. 지금 앱에 있는 데이터는 모두 지워집니다."
        ) {
            OutlinedButton(
                onClick = { openFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                enabled = !status.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("백업 파일 불러오기")
            }
        }

        if (status.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (status.message.isNotBlank()) {
            Text(
                status.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        }
    }

    // 되돌릴 수 없는 동작이므로 실제 복원 전에 한 번 더 물어본다.
    pendingImportUri?.let { uri ->
        ConfirmDialog(
            title = "데이터 가져오기",
            message = "지금 앱에 있는 운동·루틴·기록이 모두 지워지고 파일 내용으로 대체됩니다. 계속할까요?",
            confirmLabel = "가져오기",
            onDismiss = { pendingImportUri = null },
            onConfirm = {
                pendingImportUri = null
                settingsViewModel.import {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().decodeToString()
                    } ?: error("파일을 열 수 없습니다")
                }
            }
        )
    }

    if (confirmCloudRestore) {
        val backupTime = cloud.remote?.let { formatDateTime(it.updatedAtMillis) } ?: "클라우드"
        ConfirmDialog(
            title = "클라우드에서 복원",
            message = "클라우드 백업($backupTime)으로 되돌립니다. 지금 이 기기의 운동·루틴·기록은 모두 지워집니다. 계속할까요?",
            confirmLabel = "복원",
            onDismiss = { confirmCloudRestore = false },
            onConfirm = {
                confirmCloudRestore = false
                settingsViewModel.restoreFromCloud()
            }
        )
    }

    if (showHourPicker) {
        HourPickerDialog(
            selected = cloud.backupHour,
            onDismiss = { showHourPicker = false },
            onPick = { hour ->
                settingsViewModel.setBackupHour(context, hour)
                showHourPicker = false
            }
        )
    }
}

@Composable
private fun CloudBackupCard(
    cloud: CloudState,
    busy: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onUpload: () -> Unit,
    onRestoreClick: () -> Unit,
    onAutoBackupChange: (Boolean) -> Unit,
    onHourClick: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("클라우드 백업", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "같은 Google 계정으로 로그인한 내 기기끼리 기록을 옮깁니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            if (!cloud.signedIn) {
                Button(onClick = onSignIn, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Google 계정으로 로그인")
                }
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    cloud.email ?: "로그인됨",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onSignOut, enabled = !busy) { Text("로그아웃") }
            }

            Text(
                if (cloud.lastUploadAtMillis > 0)
                    "마지막 백업: ${formatDateTime(cloud.lastUploadAtMillis)} · ${cloud.lastUploadRawBytes / 1024 + 1}KB"
                else "아직 백업하지 않았습니다",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 알림일 뿐 아무것도 자동으로 실행하지 않는다 — 덮어쓰기는 항상 사용자 확인 후.
            if (cloud.cloudIsNewer) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "클라우드에 더 최신 백업이 있습니다" +
                            (cloud.remote?.let { " (${it.deviceName} · ${formatDateTime(it.updatedAtMillis)})" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onUpload, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("클라우드에 백업")
                }
                OutlinedButton(onClick = onRestoreClick, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("복원")
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("자동 백업 (매일)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = cloud.autoBackupEnabled, onCheckedChange = onAutoBackupChange, enabled = !busy)
            }
            if (cloud.autoBackupEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("백업 시각", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onHourClick) { Text("%02d:00".format(cloud.backupHour)) }
                }
                Text(
                    "기기 절전 상태에 따라 실제 실행 시각은 다소 늦어질 수 있습니다.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (cloud.lastAutoBackupResult.isNotBlank()) {
                    Text(
                        cloud.lastAutoBackupResult,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 0~23시 목록. 시각만 고르는 데 실험적인 M3 TimePicker를 쓰는 건 과하다. */
@Composable
private fun HourPickerDialog(
    selected: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("백업 시각") },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items((0..23).toList()) { hour ->
                    TextButton(onClick = { onPick(hour) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "%02d:00".format(hour),
                            fontWeight = if (hour == selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun SectionCard(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

private val DATE_TIME = DateTimeFormatter.ofPattern("M/d HH:mm")

private fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_TIME)

private fun defaultFileName(): String {
    val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    return "mytracker-backup-$stamp.json"
}
