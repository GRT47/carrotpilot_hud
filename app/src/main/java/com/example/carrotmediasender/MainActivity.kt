package com.example.carrotmediasender

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        try {
            val componentName = ComponentName(this, MediaNotificationService::class.java)
            val pm = packageManager
            pm.setComponentEnabledSetting(
                componentName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                componentName,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val sharedPrefs = getSharedPreferences("carrot_prefs", Context.MODE_PRIVATE)
        val initialTheme = sharedPrefs.getString("theme", "auto") ?: "auto"
        MediaState.setThemeMode(initialTheme)
        
        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            var patchStatus by remember { mutableStateOf("") }
            var showConfirmDialog by remember { mutableStateOf(false) }
            var foundCommaIp by remember { mutableStateOf("") }
            var sshKeyExists by remember { mutableStateOf(File(context.filesDir, "id_ed25519").exists()) }

            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let {
                    try {
                        context.contentResolver.openInputStream(it)?.use { input ->
                            val outFile = File(context.filesDir, "id_ed25519")
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                            sshKeyExists = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "HUD config",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Text(
                            text = "현재 로컬 네트워크 전체로 곡 정보를 브로드캐스팅 중입니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // 현재 재생 중인 미디어 정보 카드
                        val title by MediaState.title.collectAsState()
                        val artist by MediaState.artist.collectAsState()
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "현재 재생 중인 곡",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                if (artist.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = artist,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 테마 설정 카드
                        val selectedTheme by MediaState.themeMode.collectAsState()
                        val themeOptions = listOf("auto" to "자동 (오픈파일럿 연동)", "dark" to "항상 다크 모드", "light" to "항상 라이트 모드")
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth()
                            ) {
                                Text(
                                    text = "HUD 테마 설정",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                themeOptions.forEach { (value, label) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(
                                            selected = selectedTheme == value,
                                            onClick = {
                                                sharedPrefs.edit().putString("theme", value).apply()
                                                MediaState.setThemeMode(value)
                                            }
                                        )
                                        Text(text = label)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                Text(
                                    text = "SSH 인증 키",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (sshKeyExists) "✅ 키 파일이 등록됨" else "❌ 키 파일 없음",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Button(onClick = { launcher.launch("*/*") }) {
                                        Text(if (sshKeyExists) "변경하기" else "파일 선택")
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Text("알림 접근 권한 허용하기")
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                patchStatus = "콤마 기기 IP 스캔 중..."
                                coroutineScope.launch {
                                    val client = CommaSshClient(context)
                                    val ip = client.findCommaDeviceIp()
                                    if (ip != null) {
                                        foundCommaIp = ip
                                        showConfirmDialog = true
                                        patchStatus = "IP 스캔 완료."
                                    } else {
                                        patchStatus = "패치 적용 실패: 네트워크에서 콤마 기기(포트 7000)를 찾을 수 없습니다."
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Text("콤마 기기에 HUD 패치(USB 모니터) 적용하기")
                        }
                        
                        if (patchStatus.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = patchStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (patchStatus.contains("실패")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            if (showConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    title = { Text("패치 진행 확인") },
                    text = { Text("콤마 기기를 찾았습니다 (IP: $foundCommaIp).\n\nHUD 패치(USB 모니터 관련 작업 내역)를 콤마 기기에 덮어쓰시겠습니까?\n이 작업은 콤마 기기의 HUD를 재시작합니다.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showConfirmDialog = false
                                patchStatus = "패치 적용 중..."
                                coroutineScope.launch {
                                    val client = CommaSshClient(context)
                                    val result = client.applyUsbMonitorPatch(foundCommaIp)
                                    patchStatus = if (result.isSuccess) {
                                        "패치 적용 성공!"
                                    } else {
                                        "패치 적용 실패: ${result.exceptionOrNull()?.message}"
                                    }
                                }
                            }
                        ) {
                            Text("진행")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showConfirmDialog = false }
                        ) {
                            Text("취소")
                        }
                    }
                )
            }
        }
    }
}
