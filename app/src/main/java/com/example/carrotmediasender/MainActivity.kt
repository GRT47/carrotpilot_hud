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
                            text = "?ÑÏû¨ Î°úÏª¨ ?§Ìä∏?åÌÅ¨ ?ÑÏ≤¥Î°?Í≥??ïÎ≥¥Î•?Î∏åÎ°ú?úÏ∫ê?§ÌåÖ Ï§ëÏûÖ?àÎã§.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // ?ÑÏû¨ ?¨ÏÉù Ï§ëÏù∏ ÎØ∏Îîî???ïÎ≥¥ Ïπ¥Îìú
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
                                    text = "?ÑÏû¨ ?¨ÏÉù Ï§ëÏù∏ Í≥?,
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
                        
                        // ?åÎßà ?§Ï†ï Ïπ¥Îìú
                        val selectedTheme by MediaState.themeMode.collectAsState()
                        val themeOptions = listOf("auto" to "?êÎèô (?§Ìîà?åÏùº???∞Îèô)", "dark" to "??ÉÅ ?§ÌÅ¨ Î™®Îìú", "light" to "??ÉÅ ?ºÏù¥??Î™®Îìú")
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp).fillMaxWidth()
                            ) {
                                Text(
                                    text = "HUD ?åÎßà ?§Ï†ï",
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
                                    text = "SSH ?∏Ï¶ù ??,
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
                                        text = if (sshKeyExists) "?????åÏùº???±Î°ù?? else "?????åÏùº ?ÜÏùå",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Button(onClick = { launcher.launch("*/*") }) {
                                        Text(if (sshKeyExists) "?åÏùº Í∞Ä?∏Ïò§Í∏? else "?åÏùº ?†ÌÉù")
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
                                                    Toast.makeText(context, "?àÎ°ú??SSH ?§Í? ?ùÏÑ±?òÏóà?µÎãà??", Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "???ùÏÑ± ?§Ìå®: ${e.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }) {
                                        Text("???êÎèô ?ùÏÑ±")
                                    }
                                    
                                    if (sshKeyExists) {
                                        Button(onClick = {
                                            val pubFile = File(context.filesDir, "id_rsa.pub")
                                            if (pubFile.exists()) {
                                                val pubKey = pubFile.readText()
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("SSH Public Key", pubKey)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, "Í≥µÍ∞ú??Î≥µÏÇ¨ ?ÑÎ£å! ÍπÉÌóàÎ∏??§Ï†ï ?òÏù¥ÏßÄÍ∞Ä ?¥Î¶Ω?àÎã§.", Toast.LENGTH_LONG).show()
                                                
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/ssh/new"))
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(context, "?êÎèô ?ùÏÑ±??Í≥µÍ∞ú?§Í? ?ÜÏäµ?àÎã§. Í∏∞Ï°¥ ?åÏùº???¨Ïö© Ï§ëÏûÖ?àÎã§.", Toast.LENGTH_LONG).show()
                                            }
                                        }) {
                                            Text("ÍπÉÌóàÎ∏åÏóê ???±Î°ù?òÍ∏∞")
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
                                    text = "ÏΩ§Îßà Í∏∞Í∏∞ IP (?†ÌÉù ?¨Ìï≠)",
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
                                    placeholder = { Text("?? 192.168.1.30 (ÎπÑÏõå?êÎ©¥ ?êÎèô ?§Ï∫î)") },
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
                            Text("?åÎ¶º ?ëÍ∑º Í∂åÌïú ?àÏö©?òÍ∏∞")
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = {
                                val targetIp = manualIp.trim()
                                if (targetIp.isNotEmpty()) {
                                    foundCommaIp = targetIp
                                    showConfirmDialog = true
                                    patchStatus = "IP ?òÎèô ?ÖÎ†•??"
                                } else {
                                    patchStatus = "ÏΩ§Îßà Í∏∞Í∏∞ IP ?§Ï∫î Ï§?.."
                                    coroutineScope.launch {
                                        val client = CommaSshClient(context)
                                        val ip = client.findCommaDeviceIp()
                                        if (ip != null) {
                                            foundCommaIp = ip
                                            showConfirmDialog = true
                                            patchStatus = "IP ?§Ï∫î ?ÑÎ£å."
                                        } else {
                                            patchStatus = "?®Ïπò ?ÅÏö© ?§Ìå®: ?§Ìä∏?åÌÅ¨?êÏÑú ÏΩ§Îßà Í∏∞Í∏∞(?¨Ìä∏ 7000)Î•?Ï∞æÏùÑ ???ÜÏäµ?àÎã§."
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            Text("ÏΩ§Îßà Í∏∞Í∏∞??HUD ?®Ïπò(USB Î™®Îãà?? ?ÅÏö©?òÍ∏∞")
                        }
                        
                        if (patchStatus.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = patchStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (patchStatus.contains("?§Ìå®")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            if (showConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showConfirmDialog = false },
                    title = { Text("?®Ïπò ÏßÑÌñâ ?ïÏù∏") },
                    text = { Text("ÏΩ§Îßà Í∏∞Í∏∞Î•?Ï∞æÏïò?µÎãà??(IP: $foundCommaIp).\n\nHUD ?®Ïπò(USB Î™®Îãà??Í¥Ä???ëÏóÖ ?¥Ïó≠)Î•?ÏΩ§Îßà Í∏∞Í∏∞????ñ¥?∞ÏãúÍ≤†Ïäµ?àÍπå?\n???ëÏóÖ?Ä ÏΩ§Îßà Í∏∞Í∏∞??HUDÎ•??¨Ïãú?ëÌï©?àÎã§.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                showConfirmDialog = false
                                showProgressDialog = true
                                patchLogs = "SSH ?ëÏÜç Î∞??®Ïπò ?ÅÏö© ?úÏûë...\n"
                                patchStatus = "?®Ïπò ?ÅÏö© Ï§?.."
                                coroutineScope.launch {
                                    val client = CommaSshClient(context)
                                    val result = client.applyUsbMonitorPatch(foundCommaIp) { log ->
                                        patchLogs += log
                                    }
                                    if (result.isSuccess) {
                                        patchLogs += "\n\n?®Ïπò ?ÅÏö© ?ÑÎ£å!"
                                        patchStatus = "?®Ïπò ?ÅÏö© ?±Í≥µ!"
                                    } else {
                                        patchLogs += "\n\n?®Ïπò ?ÅÏö© ?§Ìå®: ${result.exceptionOrNull()?.message}"
                                        patchStatus = "?®Ïπò ?ÅÏö© ?§Ìå®: ${result.exceptionOrNull()?.message}"
                                    }
                                }
                            }
                        ) {
                            Text("ÏßÑÌñâ")
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = { showConfirmDialog = false }
                        ) {
                            Text("Ï∑®ÏÜå")
                        }
                    }
                )
            }
            
            if (showProgressDialog) {
                AlertDialog(
                    onDismissRequest = { /* ÏßÑÌñâ Ï§ëÏóê??Î∞îÍπ• ?∞ÏπòÎ°??´ÌûàÏßÄ ?äÍ≤å ??*/ },
                    title = { Text("?®Ïπò ÏßÑÌñâ ?ÑÌô©") },
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
                        if (patchLogs.contains("?®Ïπò ?ÅÏö© ?ÑÎ£å!") || patchLogs.contains("?®Ïπò ?ÅÏö© ?§Ìå®:")) {
                            Button(
                                onClick = { showProgressDialog = false }
                            ) {
                                Text("?´Í∏∞")
                            }
                        }
                    }
                )
            }
        }
    }
}
