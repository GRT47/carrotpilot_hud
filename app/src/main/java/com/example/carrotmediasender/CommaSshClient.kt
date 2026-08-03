package com.example.carrotmediasender

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Logger
import java.io.File
import java.net.NetworkInterface
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class CommaSshClient(private val context: Context, private val sshUser: String = "comma", private val sshPort: Int = 22) {
    companion object {
        private const val TAG = "CommaSshClient"
    }

    suspend fun findCommaDeviceIp(): String? = withContext(Dispatchers.IO) {
        val subnetsToScan = mutableSetOf<String>()
        
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (!intf.isUp || intf.isLoopback) continue
                
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address) {
                        val ipStr = addr.hostAddress
                        if (ipStr != null) {
                            val lastDot = ipStr.lastIndexOf('.')
                            if (lastDot > 0) {
                                subnetsToScan.add(ipStr.substring(0, lastDot))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network interfaces", e)
        }
        
        // Add common default subnets just in case
        subnetsToScan.add("192.168.43")
        subnetsToScan.add("192.168.1")
        subnetsToScan.add("192.168.0")
        
        var foundIp: String? = null
        
        try {
            coroutineScope {
                for (subnet in subnetsToScan) {
                    for (i in 1..254) {
                        val ip = "$subnet.$i"
                        launch {
                            try {
                                val socket = Socket()
                                // Port 7000 is open on Comma devices (often used for websockets or msgq)
                                socket.connect(InetSocketAddress(ip, 7000), 1000)
                                socket.close()
                                foundIp = ip
                                this@coroutineScope.cancel() // Cancel other scans once found
                            } catch (e: Exception) {
                                // Connection failed or timed out, ignore
                            }
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancelled because we found the IP, ignore
        }
        
        return@withContext foundIp
    }

    suspend fun applyUsbMonitorPatch(targetHost: String, onProgress: suspend (String) -> Unit = {}): Result<String> = withContext(Dispatchers.IO) {
        val keyFile = File(context.filesDir, "id_rsa")
        val command = """
            cd /data/openpilot/selfdrive/carrot
            rm -rf cluster_tmp
            git clone -b cluster --depth 1 https://github.com/GRT47/carrotpilot_hud.git cluster_tmp
            if [ -d "cluster_tmp/cluster" ]; then
                rm -rf cluster
                mv cluster_tmp/cluster ./cluster
                rm -rf cluster_tmp
                pkill -f "python.*cluster/main.py" || true
                echo "Patch applied successfully!"
            else
                echo "Error: clone failed or directory not found"
                exit 1
            fi
        """.trimIndent()
        
        var session: Session? = null
        var channel: ChannelExec? = null

        try {
            if (!keyFile.exists()) {
                Log.e(TAG, "Key file not found at ${keyFile.absolutePath}")
                return@withContext Result.failure(Exception("SSH 키 파일을 찾을 수 없습니다."))
            }

            val jsch = JSch()
            JSch.setLogger(object : Logger {
                override fun isEnabled(level: Int): Boolean = true
                override fun log(level: Int, message: String) {
                    Log.d("JSchLog", message)
                }
            })
            jsch.addIdentity(keyFile.absolutePath)
            
            Log.d(TAG, "Attempting to connect with sshUser: ${sshUser} on port: ${sshPort} using key: ${keyFile.absolutePath}")
            session = jsch.getSession(sshUser, targetHost, sshPort)
            
            val config = java.util.Properties()
            config.put("StrictHostKeyChecking", "no")
            // Ensure rsa-sha2 is preferred
            config.put("PubkeyAcceptedAlgorithms", "+ssh-rsa,rsa-sha2-256,rsa-sha2-512")
            session.setConfig(config)
            
            session.connect(10000)
            
            channel = session.openChannel("exec") as ChannelExec
            channel.setCommand(command)
            
            val inputStream = channel.inputStream
            channel.connect()
            
            val output = inputStream.bufferedReader().readText()
            
            while (!channel.isClosed) {
                kotlinx.coroutines.delay(100)
            }
            
            val exitStatus = channel.exitStatus
            
            Log.d(TAG, "Command execution finished with exit status: $exitStatus")
            Log.d(TAG, "Output: $output")
            
            if (exitStatus == 0) {
                return@withContext Result.success("패치가 성공적으로 적용되었습니다.")
            } else {
                return@withContext Result.failure(Exception("패치 적용 실패 (코드: $exitStatus)\n출력: $output"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "SSH connection failed", e)
            return@withContext Result.failure(Exception("기기 연결 실패: ${e.message}"))
        } finally {
            try {
                channel?.disconnect()
                session?.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing SSH session", e)
            }
        }
    }
}
