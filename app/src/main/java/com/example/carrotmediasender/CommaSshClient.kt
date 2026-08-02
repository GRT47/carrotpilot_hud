package com.example.carrotmediasender

import android.content.Context
import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.File
import java.io.FileOutputStream
import java.net.NetworkInterface
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel

class CommaSshClient(private val context: Context, private val sshUser: String = "root", private val sshPort: Int = 22) {
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
        var session: Session? = null
        try {
            Log.d(TAG, "Connecting to Comma device at $targetHost")
            
            val jsch = JSch()
            
            val keyFile = File(context.filesDir, "id_ed25519")
            if (!keyFile.exists()) {
                return@withContext Result.failure(Exception("SSH 키 파일이 등록되지 않았습니다. 앱 메인 화면에서 키 파일을 먼저 선택해주세요."))
            }

            jsch.addIdentity(keyFile.absolutePath)

            session = jsch.getSession(sshUser, targetHost, sshPort)
            session.setConfig("StrictHostKeyChecking", "no")
            session.connect(10000)

            val command = """
                cd /data/openpilot/selfdrive/carrot
                rm -rf cluster_tmp
                git clone -b cluster --depth 1 https://github.com/GRT47/carrotpilot_hud.git cluster_tmp
                if [ -d "cluster_tmp/cluster" ]; then
                    rm -rf cluster
                    mv cluster_tmp/cluster ./cluster
                    rm -rf cluster_tmp
                    # Assuming HUD is managed by openpilot or systemd, we can restart it.
                    # Or we just kill main.py so it restarts automatically.
                    pkill -f "python.*cluster/main.py" || true
                    echo "Patch applied successfully!"
                else
                    echo "Error: clone failed or directory not found"
                    exit 1
                fi
            """.trimIndent()

            val channel = session.openChannel("exec") as com.jcraft.jsch.ChannelExec
            channel.setCommand(command)
            channel.inputStream = null
            
            val inStream: InputStream = channel.inputStream
            val errStream: InputStream = channel.errStream
            
            channel.connect()

            val output = StringBuilder()
            val buffer = ByteArray(1024)
            
            while (true) {
                while (inStream.available() > 0) {
                    val i = inStream.read(buffer, 0, 1024)
                    if (i < 0) break
                    val str = String(buffer, 0, i)
                    output.append(str)
                    onProgress(str)
                }
                while (errStream.available() > 0) {
                    val i = errStream.read(buffer, 0, 1024)
                    if (i < 0) break
                    val str = String(buffer, 0, i)
                    output.append(str)
                    onProgress(str)
                }
                if (channel.isClosed) {
                    if (inStream.available() > 0 || errStream.available() > 0) continue
                    break
                }
                Thread.sleep(100)
            }

            channel.disconnect()
            
            val exitStatus = channel.exitStatus
            if (exitStatus == 0) {
                Result.success(output.toString())
            } else {
                Result.failure(Exception("Command failed with status $exitStatus\nOutput: $output"))
            }

        } catch (e: Exception) {
            Log.e(TAG, "SSH connection failed", e)
            Result.failure(e)
        } finally {
            session?.disconnect()
        }
    }
}
