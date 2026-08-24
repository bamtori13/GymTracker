package com.gymtracker.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 설정 탭: 데이터 내보내기 / 가져오기.
 * 파일 위치는 SAF(문서 선택기)로 사용자가 직접 고른다 — 저장소 권한이 필요 없고,
 * 클라우드 드라이브에도 그대로 저장할 수 있다.
 */
@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val status by settingsViewModel.status.collectAsState()
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("데이터 관리", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("내보내기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "운동/루틴/기록 전체를 JSON 파일 하나로 저장합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { createFile.launch(defaultFileName()) },
                    enabled = !status.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("백업 파일 저장")
                }
            }
        }

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("가져오기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "백업 파일로 되돌립니다. 지금 앱에 있는 데이터는 모두 지워집니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { openFile.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    enabled = !status.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("백업 파일 불러오기")
                }
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
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("데이터 가져오기") },
            text = { Text("지금 앱에 있는 운동·루틴·기록이 모두 지워지고 파일 내용으로 대체됩니다. 계속할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    settingsViewModel.import {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            input.readBytes().decodeToString()
                        } ?: error("파일을 열 수 없습니다")
                    }
                }) { Text("가져오기") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text("취소") }
            }
        )
    }
}

private fun defaultFileName(): String {
    val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
    return "mytracker-backup-$stamp.json"
}
