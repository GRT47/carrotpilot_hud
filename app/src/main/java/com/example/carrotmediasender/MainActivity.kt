package com.example.carrotmediasender

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.launch
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast

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
            var showProgressDialog by remember { mutableStateOf(false) }
            var patchLogs by remember { mutableStateOf("") }
            var foundCommaIp by remember { mutableStateOf("") }
            var sshKeyExists by remember { mutableStateOf(File(context.filesDir, "id_rsa").exists()) }
            var manualIp by remember { mutableStateOf(sharedPrefs.getString("comma_ip", "") ?: "") }

            val launcher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                uri?.let {
                    try {
                        context.contentResolver.openInputStream(it)?.use { input ->
                            val outFile = File(context.filesDir, "id_rsa")
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
                                        Text(if (sshKeyExists) "파일 가져오기" else "파일 선택")
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Button(onClick = {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val jsch = JSch()
                                                val kpair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 2048)
                                                val privFile = File(context.filesDir, "id_rsa")
                                                val pubFile = File(context.filesDir, "id_rsa.pub")
                                                kpair.writePrivateKey(privFile.absolutePath)
                                                kpair.writePublicKey(pubFile.absolutePath, "carrothud@android")
                                                kpair.dispose()
                                                withContext(Dispatchers.Main) {
                                                    sshKeyExists = true
                                                    Toast.makeText(context, "새로운 SSH 키가 생성되었습니다.", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "키 생성 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }) {
                                        Text("키 자동 생성")
                                    }
                                    
                                    if (sshKeyExists) {
                                        Button(onClick = {
                                            val pubFile = File(context.filesDir, "id_rsa.pub")
                                            if (pubFile.exists()) {
                                                val pubKey = pubFile.readText()
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("SSH Public Key", pubKey)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "공개키 복사 완료! 깃허브 설정 페이지가 열립니다.", Toast.LENGTH_LONG).show()
                                                
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/ssh/new"))
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(context, "자동 생성된 공개키가 없습니다. 기존 파일을 사용 중입니다.", Toast.LENGTH_LONG).show()
                                            }
                                        }) {
                                            Text("깃허브에 키 등록하기")
                                        }
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
                                    text = "콤마 기기 IP (선택 사항)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = manualIp,
                                    onValueChange = { 
                                        manualIp = it
                                        sharedPrefs.edit().putString("comma_ip", it).apply()
                                    },
                                    placeholder = { Text("예: 192.168.1.30 (비워두면 자동 스캔)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
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
                                val targetIp = manualIp.trim()
                                if (targetIp.isNotEmpty()) {
                                    foundCommaIp = targetIp
                                    showConfirmDialog = true
                                    patchStatus = "IP 수동 입력됨."
                                } else {
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
                                showProgressDialog = true
                                patchLogs = "SSH 접속 및 패치 적용 시작...\n"
                                patchStatus = "패치 적용 중..."
                                coroutineScope.launch {
                                    val client = CommaSshClient(context)
                                    val result = client.applyUsbMonitorPatch(foundCommaIp) { log ->
                                        patchLogs += log
                                    }
                                    if (result.isSuccess) {
                                        patchLogs += "\n\n패치 적용 완료!"
                                        patchStatus = "패치 적용 성공!"
                                    } else {
                                        patchLogs += "\n\n패치 적용 실패: ${result.exceptionOrNull()?.message}"
                                        patchStatus = "패치 적용 실패: ${result.exceptionOrNull()?.message}"
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
            
            if (showProgressDialog) {
                AlertDialog(
                    onDismissRequest = { /* 진행 중에는 바깥 터치로 닫히지 않게 함 */ },
                    title = { Text("패치 진행 현황") },
                    text = {
                        val scrollState = rememberScrollState()
                        LaunchedEffect(patchLogs) {
                            scrollState.animateScrollTo(scrollState.maxValue)
                        }
                        Text(
                            text = patchLogs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .verticalScroll(scrollState),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    confirmButton = {
                        if (patchLogs.contains("패치 적용 완료!") || patchLogs.contains("패치 적용 실패:")) {
                            Button(
                                onClick = { showProgressDialog = false }
                            ) {
                                Text("닫기")
                            }
                        }
                    }
                )
            }
        }
    }
}
