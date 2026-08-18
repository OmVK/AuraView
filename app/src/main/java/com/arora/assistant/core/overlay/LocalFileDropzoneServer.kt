package com.arora.assistant.core.overlay

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arora.assistant.ui.components.NeonButton
import com.arora.assistant.ui.theme.PastelRose
import com.arora.assistant.ui.theme.SageMint
import com.arora.assistant.ui.theme.SkyOpal
import com.arora.assistant.ui.theme.SoftAmber
import com.arora.assistant.ui.theme.SoftCardBorder
import com.arora.assistant.ui.theme.SoftDarkBg
import com.arora.assistant.ui.theme.SoftLavender
import com.arora.assistant.ui.theme.SoftSurface
import com.arora.assistant.ui.theme.SoftSurfaceElevated
import com.arora.assistant.ui.theme.TextMuted
import com.arora.assistant.ui.theme.TextOffWhite
import com.arora.assistant.ui.theme.TextPureWhite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import kotlin.concurrent.thread
import kotlin.random.Random

data class SharedFileInfo(
    val name: String,
    val size: Long,
    val path: String,
    val type: String,
    val isDirectory: Boolean = false
)

object LocalFileDropzoneServer {

    private var serverSocket: ServerSocket? = null
    var isRunning = false
        private set

    var serverUrl: String = ""
        private set

    var pairingPin: String = ""
        private set

    private var authToken: String = ""
    private var appContext: Context? = null

    // Privacy sharing flags
    var allowImages = true
    var allowVideos = true
    var allowDocuments = true
    var allowAudio = true
    var allowInternalStorage = false

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.isLoopback || !iface.isUp) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (host != null && (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172."))) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }

    suspend fun startServer(context: Context? = null, port: Int = 8888): String = withContext(Dispatchers.IO) {
        if (context != null) appContext = context.applicationContext
        if (serverSocket != null && isRunning) return@withContext serverUrl

        try {
            stopServer()
            pairingPin = String.format("%06d", Random.nextInt(100000, 999999))
            authToken = "arora_auth_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}"

            val ip = getLocalIpAddress()
            val socket = ServerSocket(port)
            serverSocket = socket
            isRunning = true
            serverUrl = "http://$ip:$port"

            thread(name = "DropzoneServerThread") {
                while (isRunning && !socket.isClosed) {
                    try {
                        val client = socket.accept()
                        handleClient(client)
                    } catch (e: SocketException) {
                        break
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            serverUrl
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun isPathAllowed(path: String): Boolean {
        val baseExternal = "/storage/emulated/0"
        val target = File(path).canonicalPath
        return target.startsWith(baseExternal) && !target.contains("/data/data/")
    }

    private fun handleClient(client: Socket) {
        thread {
            try {
                val inStream = client.getInputStream()
                val reader = BufferedReader(InputStreamReader(inStream))
                val output = client.getOutputStream()

                val requestLine = reader.readLine() ?: return@thread
                val parts = requestLine.split(" ")
                if (parts.size < 2) return@thread

                val method = parts[0]
                val path = parts[1]

                var contentLength = 0
                var cookieHeader = ""
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrEmpty()) break
                    val lower = line!!.lowercase()
                    if (lower.startsWith("content-length:")) {
                        contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                    if (lower.startsWith("cookie:")) {
                        cookieHeader = line!!.substringAfter(":").trim()
                    }
                }

                // Authentication Check (PIN Login & Session Cookie)
                val isAuthenticated = (cookieHeader.isNotBlank() && cookieHeader.contains("arora_session=$authToken")) ||
                        (path.contains("pin=$pairingPin") && pairingPin.isNotBlank())

                // Handle PIN Authentication Submission: POST /auth
                if (method.equals("POST", ignoreCase = true) && path.startsWith("/auth")) {
                    val charBuffer = CharArray(contentLength.coerceAtLeast(1))
                    var charsRead = 0
                    while (charsRead < contentLength) {
                        val count = reader.read(charBuffer, charsRead, contentLength - charsRead)
                        if (count == -1) break
                        charsRead += count
                    }
                    val bodyString = if (charsRead > 0) String(charBuffer, 0, charsRead) else ""

                    val urlPin = if (path.contains("pin=")) URLDecoder.decode(path.substringAfter("pin=").substringBefore("&"), "UTF-8").trim() else ""
                    val formPin = if (bodyString.contains("pin=")) URLDecoder.decode(bodyString.substringAfter("pin=").substringBefore("&"), "UTF-8").trim() else bodyString.trim()
                    val submittedPin = if (urlPin.isNotBlank()) urlPin else formPin

                    if (submittedPin.isNotBlank() && submittedPin == pairingPin) {
                        val respHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="UTF-8">
                                <meta http-equiv="refresh" content="0; url=/">
                                <script>
                                    document.cookie = "arora_session=$authToken; path=/; max-age=86400; SameSite=Lax";
                                    window.location.replace('/');
                                </script>
                            </head>
                            <body style="background:#0E0E12;color:#38BDF8;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh;">
                                <p style="font-size:18px;font-weight:bold;">✓ Unlocked! Redirecting to Dropzone...</p>
                            </body>
                            </html>
                        """.trimIndent()
                        val response = "HTTP/1.1 200 OK\r\n" +
                                "Set-Cookie: arora_session=$authToken; Path=/; Max-Age=86400; SameSite=Lax\r\n" +
                                "Content-Type: text/html\r\n" +
                                "Content-Length: ${respHtml.toByteArray().size}\r\n" +
                                "Connection: close\r\n\r\n" + respHtml
                        output.write(response.toByteArray())
                    } else {
                        val errHtml = renderPinLoginHtml("Invalid 6-digit PIN. Please check the PIN on your phone screen.")
                        val response = "HTTP/1.1 401 Unauthorized\r\n" +
                                "Content-Type: text/html\r\n" +
                                "Content-Length: ${errHtml.toByteArray().size}\r\n" +
                                "Connection: close\r\n\r\n" + errHtml
                        output.write(response.toByteArray())
                    }
                    output.flush()
                    client.close()
                    return@thread
                }

                if (!isAuthenticated) {
                    val loginHtml = renderPinLoginHtml()
                    val response = "HTTP/1.1 401 Unauthorized\r\n" +
                            "Content-Type: text/html\r\n" +
                            "Content-Length: ${loginHtml.toByteArray().size}\r\n" +
                            "Connection: close\r\n\r\n" + loginHtml
                    output.write(response.toByteArray())
                    output.flush()
                    client.close()
                    return@thread
                }

                // 1. Direct File Download Handler: GET /download?path=<encoded_path>
                if (method.equals("GET", ignoreCase = true) && path.startsWith("/download")) {
                    val targetPath = if (path.contains("?path=")) {
                        URLDecoder.decode(path.substringAfter("?path="), "UTF-8")
                    } else if (path.startsWith("/download/")) {
                        URLDecoder.decode(path.removePrefix("/download/"), "UTF-8")
                    } else ""

                    if (isPathAllowed(targetPath)) {
                        val file = File(targetPath)
                        if (file.exists() && file.isFile) {
                            val header = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: application/octet-stream\r\n" +
                                    "Content-Disposition: attachment; filename=\"${file.name}\"\r\n" +
                                    "Content-Length: ${file.length()}\r\n" +
                                    "Connection: close\r\n\r\n"
                            output.write(header.toByteArray())

                            val fileIn = FileInputStream(file)
                            val buffer = ByteArray(16384)
                            var bytesRead: Int
                            while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                            }
                            fileIn.close()
                            output.flush()
                            client.close()
                            return@thread
                        }
                    }
                }

                // 2. Direct Binary File Upload Handler: POST /upload?name=<filename>&dir=<path>
                if (method.equals("POST", ignoreCase = true) && path.startsWith("/upload")) {
                    val rawFileName = if (path.contains("name=")) {
                        URLDecoder.decode(path.substringAfter("name=").substringBefore("&"), "UTF-8")
                    } else {
                        "Uploaded_File_${System.currentTimeMillis()}.bin"
                    }

                    val targetDirPath = if (path.contains("dir=")) {
                        URLDecoder.decode(path.substringAfter("dir=").substringBefore("&"), "UTF-8")
                    } else {
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                    }

                    if (isPathAllowed(targetDirPath)) {
                        val targetDir = File(targetDirPath)
                        if (!targetDir.exists()) targetDir.mkdirs()

                        val targetFile = File(targetDir, rawFileName)
                        val fileOut = FileOutputStream(targetFile)

                        val buffer = ByteArray(16384)
                        var totalRead = 0
                        while (totalRead < contentLength) {
                            val toRead = (contentLength - totalRead).coerceAtMost(buffer.size)
                            val read = inStream.read(buffer, 0, toRead)
                            if (read == -1) break
                            fileOut.write(buffer, 0, read)
                            totalRead += read
                        }
                        fileOut.flush()
                        fileOut.close()

                        val respJson = "{\"status\":\"ok\",\"file\":\"${targetFile.name}\",\"size\":$totalRead}"
                        val response = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: application/json\r\n" +
                                "Content-Length: ${respJson.toByteArray().size}\r\n" +
                                "Connection: close\r\n\r\n" + respJson

                        output.write(response.toByteArray())
                        output.flush()
                        client.close()
                        return@thread
                    }
                }

                // 3. File Delete Handler: POST /delete?path=<filepath>
                if (method.equals("POST", ignoreCase = true) && path.startsWith("/delete")) {
                    val rawPath = if (path.contains("path=")) {
                        URLDecoder.decode(path.substringAfter("path="), "UTF-8")
                    } else ""

                    var success = false
                    if (isPathAllowed(rawPath)) {
                        val file = File(rawPath)
                        if (file.exists()) {
                            success = file.delete()
                        }
                    }

                    val respJson = "{\"status\":\"${if (success) "deleted" else "failed"}\"}"
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${respJson.toByteArray().size}\r\n" +
                            "Connection: close\r\n\r\n" + respJson

                    output.write(response.toByteArray())
                    output.flush()
                    client.close()
                    return@thread
                }

                // 3.5 Push Text to Phone Clipboard Handler: POST /push-text
                if (path.startsWith("/push-text")) {
                    val textToPush = if (path.contains("text=")) {
                        URLDecoder.decode(path.substringAfter("text="), "UTF-8")
                    } else ""

                    if (textToPush.isNotBlank()) {
                        try {
                            val clipboard = appContext?.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("PC Push", textToPush)
                            clipboard?.setPrimaryClip(clip)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    val respJson = "{\"status\":\"copied\"}"
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: application/json\r\n" +
                            "Content-Length: ${respJson.toByteArray().size}\r\n" +
                            "Connection: close\r\n\r\n" + respJson

                    output.write(response.toByteArray())
                    output.flush()
                    client.close()
                    return@thread
                }

                // 4. Main Web Dashboard (Folder navigation enabled)
                val currentBrowseDir = if (path.contains("folder=")) {
                    URLDecoder.decode(path.substringAfter("folder="), "UTF-8")
                } else ""

                val sharedFiles = queryFiles(currentBrowseDir)
                val filesHtml = StringBuilder()

                if (currentBrowseDir.isNotBlank() && currentBrowseDir != "/storage/emulated/0") {
                    val parent = File(currentBrowseDir).parent ?: "/storage/emulated/0"
                    filesHtml.append(
                        """
                        <li style='padding:10px 16px; background:#272A38; border-radius:10px; margin-bottom:8px;'>
                            <a href='/?folder=${parent}' style='color:#A78BFA; text-decoration:none; font-weight:bold; font-size:13px;'>⬆️ [Parent Directory] ..</a>
                        </li>
                        """.trimIndent()
                    )
                }

                if (sharedFiles.isEmpty()) {
                    filesHtml.append("<p style='color:#94A3B8; text-align:center; padding: 20px;'>No files found in this directory.</p>")
                } else {
                    sharedFiles.forEach { fileInfo ->
                        if (fileInfo.isDirectory) {
                            filesHtml.append(
                                """
                                <li style='display:flex; justify-content:space-between; align-items:center; padding:12px 16px; border-bottom:1px solid #373B4E;'>
                                    <div>
                                        <span style='font-size:16px; margin-right:6px;'>📁</span>
                                        <a href='/?folder=${fileInfo.path}' style='color:#38BDF8; font-weight:bold; text-decoration:none; font-size:14px;'>${fileInfo.name}</a>
                                    </div>
                                    <span style='color:#94A3B8; font-size:11px;'>Folder</span>
                                </li>
                                """.trimIndent()
                            )
                        } else {
                            val sizeKb = (fileInfo.size / 1024).coerceAtLeast(1)
                            val icon = when (fileInfo.type) {
                                "img" -> "🖼️"
                                "vid" -> "🎥"
                                "audio" -> "🎵"
                                else -> "📄"
                            }
                            filesHtml.append(
                                """
                                <li style='display:flex; justify-content:space-between; align-items:center; padding:12px 16px; border-bottom:1px solid #373B4E;'>
                                    <div style='overflow:hidden; text-overflow:ellipsis; white-space:nowrap; margin-right:12px;'>
                                        <span style='font-size:16px; margin-right:6px;'>$icon</span>
                                        <span style='color:#38BDF8; font-weight:600;'>${fileInfo.name}</span>
                                        <span style='color:#94A3B8; font-size:12px; margin-left:8px;'>($sizeKb KB)</span>
                                    </div>
                                    <div style='display:flex; gap:8px; align-items:center;'>
                                        <a href='/download?path=${fileInfo.path}' style='background:#A78BFA; color:#fff; padding:6px 12px; border-radius:8px; text-decoration:none; font-size:12px; font-weight:bold; white-space:nowrap;'>📥 Download</a>
                                        <button onclick='deleteFile("${fileInfo.path}", "${fileInfo.name}")' style='background:#EF4444; color:#fff; border:none; padding:6px 10px; border-radius:8px; font-size:12px; font-weight:bold; cursor:pointer; white-space:nowrap;'>🗑️ Delete</button>
                                    </div>
                                </li>
                                """.trimIndent()
                            )
                        }
                    }
                }

                val currentPathDisplay = if (currentBrowseDir.isNotBlank()) currentBrowseDir else "Shared Storage"

                val html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>AuraView Wi-Fi Dropzone</title>
                    <style>
                        * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                        body { background: #0E0E12; color: #F8FAFC; padding: 24px; display: flex; justify-content: center; }
                        .container { width: 100%; max-width: 780px; }
                        .header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 24px; padding-bottom: 16px; border-bottom: 1px solid #272A38; }
                        .logo { font-size: 22px; font-weight: 800; background: linear-gradient(135deg, #38BDF8, #A78BFA); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
                        .card { background: #181A24; border: 1px solid #272A38; border-radius: 18px; padding: 22px; margin-bottom: 20px; box-shadow: 0 10px 30px rgba(0,0,0,0.5); }
                        .dropzone { border: 2px dashed #A78BFA; border-radius: 14px; padding: 32px; text-align: center; background: rgba(167, 139, 250, 0.05); cursor: pointer; transition: all 0.2s; }
                        .dropzone:hover { background: rgba(167, 139, 250, 0.1); border-color: #38BDF8; }
                        .progress-bar { width: 100%; height: 8px; background: #272A38; border-radius: 4px; overflow: hidden; margin-top: 14px; display: none; }
                        .progress-fill { height: 100%; width: 0%; background: linear-gradient(90deg, #38BDF8, #A78BFA); transition: width 0.1s; }
                        ul { list-style: none; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div>
                                <div class="logo">⚡ AuraView Wi-Fi Dropzone</div>
                                <div style="font-size: 11px; color: #34D399; margin-top: 4px;">🔒 Secure Authenticated Session</div>
                            </div>
                            <button onclick="location.reload()" style="background:#272A38; color:#38BDF8; border:1px solid #373B4E; padding:8px 16px; border-radius:10px; font-weight:bold; cursor:pointer;">🔄 Refresh</button>
                        </div>

                        <div class="card">
                            <h3 style="font-size: 15px; margin-bottom: 12px; color: #F8FAFC;">📋 Push Text / Link to Phone Clipboard</h3>
                            <div style="display:flex; gap:10px;">
                                <input type="text" id="clipboardInput" placeholder="Paste link, note, or code snippet to copy on phone..." style="flex:1; background:#0E0E12; border:1px solid #373B4E; color:#F8FAFC; padding:10px 14px; border-radius:10px; font-size:13px; outline:none;">
                                <button onclick="pushToClipboard()" style="background:#A78BFA; color:#0E0E12; border:none; padding:10px 16px; border-radius:10px; font-weight:bold; cursor:pointer; white-space:nowrap;">📲 Copy to Phone</button>
                            </div>
                            <p id="pushStatus" style="font-size: 12px; color: #34D399; margin-top: 6px; font-weight: bold;"></p>
                        </div>

                        <div class="card">
                            <h3 style="font-size: 15px; margin-bottom: 12px; color: #F8FAFC;">📤 Send Files from PC to Phone</h3>
                            <div class="dropzone" id="dropArea" onclick="document.getElementById('fileInput').click()">
                                <p style="font-size: 28px; margin-bottom: 8px;">📁</p>
                                <p style="font-weight: 600; font-size: 14px; color: #E2E8F0;">Click or Drag & Drop files here</p>
                                <p style="font-size: 11px; color: #94A3B8; margin-top: 4px;">Target Directory: <code style="color:#38BDF8;">${currentPathDisplay}</code></p>
                                <input type="file" id="fileInput" multiple style="display: none;" onchange="handleFiles(this.files)">
                            </div>
                            <div class="progress-bar" id="progressBar"><div class="progress-fill" id="progressFill"></div></div>
                            <p id="uploadStatus" style="font-size: 12px; color: #34D399; margin-top: 8px; text-align: center; font-weight: bold;"></p>
                        </div>

                        <div class="card">
                            <h3 style="font-size: 15px; margin-bottom: 14px; color: #F8FAFC;">📥 Phone Files (<code>${currentPathDisplay}</code>)</h3>
                            <ul>
                                $filesHtml
                            </ul>
                        </div>
                    </div>

                    <script>
                        function pushToClipboard() {
                            const input = document.getElementById('clipboardInput');
                            const text = input.value.trim();
                            const status = document.getElementById('pushStatus');
                            if (!text) return;

                            fetch('/push-text?text=' + encodeURIComponent(text), { method: 'POST' })
                                .then(res => res.json())
                                .then(data => {
                                    status.innerText = '✓ Copied to phone clipboard!';
                                    input.value = '';
                                    setTimeout(() => status.innerText = '', 3000);
                                })
                                .catch(() => {
                                    status.innerText = '✗ Failed to copy text.';
                                });
                        }
                        function handleFiles(files) {
                            if (!files || files.length === 0) return;
                            const file = files[0];
                            const progressBar = document.getElementById('progressBar');
                            const progressFill = document.getElementById('progressFill');
                            const status = document.getElementById('uploadStatus');

                            progressBar.style.display = 'block';
                            status.innerText = 'Uploading ' + file.name + '...';

                            const targetDir = "${currentBrowseDir}";
                            const uploadUrl = '/upload?name=' + encodeURIComponent(file.name) + (targetDir ? '&dir=' + encodeURIComponent(targetDir) : '');

                            const xhr = new XMLHttpRequest();
                            xhr.open('POST', uploadUrl, true);
                            xhr.upload.onprogress = function(e) {
                                if (e.lengthComputable) {
                                    const percent = (e.loaded / e.total) * 100;
                                    progressFill.style.width = percent + '%';
                                }
                            };
                            xhr.onload = function() {
                                if (xhr.status === 200) {
                                    status.innerText = '✓ Successfully uploaded ' + file.name + '!';
                                    setTimeout(() => location.reload(), 1000);
                                } else {
                                    status.innerText = '✗ Upload failed.';
                                }
                            };
                            xhr.send(file);
                        }

                        function deleteFile(path, name) {
                            if (confirm('Are you sure you want to delete "' + name + '" from phone storage?')) {
                                fetch('/delete?path=' + encodeURIComponent(path), { method: 'POST' })
                                    .then(res => res.json())
                                    .then(data => {
                                        if (data.status === 'deleted') {
                                            location.reload();
                                        } else {
                                            alert('Failed to delete file.');
                                        }
                                    });
                            }
                        }
                    </script>
                </body>
                </html>
                """.trimIndent()

                val response = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html\r\n" +
                        "Content-Length: ${html.toByteArray().size}\r\n" +
                        "Connection: close\r\n\r\n" + html

                output.write(response.toByteArray())
                output.flush()
                client.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun renderPinLoginHtml(errorMessage: String? = null): String {
        val errorBadge = if (errorMessage != null) {
            "<p id='errorMsg' style='color: #F87171; background: rgba(248,113,113,0.1); border: 1px solid rgba(248,113,113,0.3); border-radius: 8px; padding: 8px; margin-bottom: 16px; font-size: 12px; font-weight: bold;'>$errorMessage</p>"
        } else "<p id='errorMsg' style='display:none; color: #F87171; background: rgba(248,113,113,0.1); border: 1px solid rgba(248,113,113,0.3); border-radius: 8px; padding: 8px; margin-bottom: 16px; font-size: 12px; font-weight: bold;'></p>"

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>AuraView - Authentication Required</title>
            <style>
                * { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; }
                body { background: #0E0E12; color: #F8FAFC; min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 20px; }
                .card { background: #181A24; border: 1px solid #272A38; border-radius: 20px; padding: 32px; width: 100%; max-width: 400px; text-align: center; box-shadow: 0 20px 40px rgba(0,0,0,0.6); }
                .logo { font-size: 24px; font-weight: 800; background: linear-gradient(135deg, #38BDF8, #A78BFA); -webkit-background-clip: text; -webkit-text-fill-color: transparent; margin-bottom: 8px; }
                input { width: 100%; padding: 14px; font-size: 20px; letter-spacing: 6px; text-align: center; font-weight: bold; background: #0E0E12; border: 1px solid #373B4E; border-radius: 12px; color: #38BDF8; margin-bottom: 16px; outline: none; }
                input:focus { border-color: #38BDF8; box-shadow: 0 0 15px rgba(56, 189, 248, 0.3); }
                button { width: 100%; padding: 14px; background: linear-gradient(135deg, #38BDF8, #A78BFA); border: none; border-radius: 12px; color: #0E0E12; font-size: 14px; font-weight: bold; cursor: pointer; transition: opacity 0.2s; }
                button:disabled { opacity: 0.6; cursor: not-allowed; }
            </style>
        </head>
        <body>
            <div class="card">
                <div class="logo">🔒 AuraView Wi-Fi Dropzone</div>
                <p style="color: #94A3B8; font-size: 13px; margin-bottom: 20px;">Enter the 6-digit PIN displayed on your phone to connect.</p>
                $errorBadge
                <form id="authForm" action="/auth" method="POST" onsubmit="submitPin(event)">
                    <input type="text" id="pinInput" name="pin" maxlength="6" pattern="[0-9]{6}" placeholder="••••••" autofocus required autocomplete="off">
                    <button type="submit" id="submitBtn">Unlock Secure Dropzone</button>
                </form>
            </div>
            <script>
                function submitPin(e) {
                    e.preventDefault();
                    const pin = document.getElementById('pinInput').value.trim();
                    if (pin.length !== 6) return;

                    const btn = document.getElementById('submitBtn');
                    const err = document.getElementById('errorMsg');
                    btn.disabled = true;
                    btn.innerText = 'Verifying PIN...';
                    err.style.display = 'none';

                    fetch('/auth', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                        body: 'pin=' + encodeURIComponent(pin)
                    }).then(res => {
                        if (res.ok) {
                            btn.innerText = '✓ Success! Loading Dropzone...';
                            document.cookie = 'arora_session=' + '$authToken' + '; path=/; max-age=86400; SameSite=Lax';
                            setTimeout(() => { window.location.href = '/?pin=' + encodeURIComponent(pin); }, 300);
                        } else {
                            btn.disabled = false;
                            btn.innerText = 'Unlock Secure Dropzone';
                            err.innerText = 'Invalid 6-digit PIN. Please check the PIN on your phone screen.';
                            err.style.display = 'block';
                            document.getElementById('pinInput').select();
                        }
                    }).catch(() => {
                        // Fallback to direct form submit
                        document.getElementById('authForm').submit();
                    });
                }
            </script>
        </body>
        </html>
        """.trimIndent()
    }

    private fun queryFiles(specificDir: String = ""): List<SharedFileInfo> {
        val list = mutableListOf<SharedFileInfo>()
        val context = appContext ?: return list

        if (allowInternalStorage || specificDir.isNotBlank()) {
            val targetDir = if (specificDir.isNotBlank()) File(specificDir) else Environment.getExternalStorageDirectory()
            if (targetDir.exists() && targetDir.isDirectory && isPathAllowed(targetDir.absolutePath)) {
                val files = targetDir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
                for (f in files) {
                    if (f.name.startsWith(".")) continue
                    if (f.isDirectory) {
                        list.add(SharedFileInfo(f.name, 0, f.absolutePath, "folder", true))
                    } else {
                        val type = when (f.extension.lowercase()) {
                            "jpg", "jpeg", "png", "webp", "gif" -> "img"
                            "mp4", "mkv", "webm" -> "vid"
                            "mp3", "wav", "m4a", "flac" -> "audio"
                            else -> "doc"
                        }
                        list.add(SharedFileInfo(f.name, f.length(), f.absolutePath, type, false))
                    }
                }
                return list
            }
        }

        val projection = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATA
        )

        // Images
        if (allowImages) {
            try {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null, null, "${MediaStore.Images.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx) ?: continue
                        val size = cursor.getLong(sizeIdx)
                        val path = cursor.getString(dataIdx) ?: continue
                        list.add(SharedFileInfo(name, size, path, "img", false))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Videos
        if (allowVideos) {
            try {
                context.contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null, null, "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx) ?: continue
                        val size = cursor.getLong(sizeIdx)
                        val path = cursor.getString(dataIdx) ?: continue
                        list.add(SharedFileInfo(name, size, path, "vid", false))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Documents
        if (allowDocuments) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloadsDir.exists()) {
                    downloadsDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.forEach { f ->
                        list.add(SharedFileInfo(f.name, f.length(), f.absolutePath, "doc", false))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Audio
        if (allowAudio) {
            try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null, null, "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIdx) ?: continue
                        val size = cursor.getLong(sizeIdx)
                        val path = cursor.getString(dataIdx) ?: continue
                        list.add(SharedFileInfo(name, size, path, "audio", false))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return list
    }

    fun stopServer() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }
}

@Composable
fun FloatingWiFiDropzoneDialog(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = remember { CoroutineScope(Dispatchers.Main) }

    var serverUrl by remember { mutableStateOf(LocalFileDropzoneServer.serverUrl) }
    var isRunning by remember { mutableStateOf(LocalFileDropzoneServer.isRunning) }
    var pairingPin by remember { mutableStateOf(LocalFileDropzoneServer.pairingPin) }
    var copiedNotice by remember { mutableStateOf(false) }

    var allowImages by remember { mutableStateOf(LocalFileDropzoneServer.allowImages) }
    var allowVideos by remember { mutableStateOf(LocalFileDropzoneServer.allowVideos) }
    var allowDocuments by remember { mutableStateOf(LocalFileDropzoneServer.allowDocuments) }
    var allowAudio by remember { mutableStateOf(LocalFileDropzoneServer.allowAudio) }
    var allowInternalStorage by remember { mutableStateOf(LocalFileDropzoneServer.allowInternalStorage) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!LocalFileDropzoneServer.isRunning) {
            val url = LocalFileDropzoneServer.startServer(context)
            serverUrl = url
            pairingPin = LocalFileDropzoneServer.pairingPin
            isRunning = true
        } else {
            serverUrl = LocalFileDropzoneServer.serverUrl
            pairingPin = LocalFileDropzoneServer.pairingPin
            isRunning = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Master Start / Stop Controls
        if (!isRunning) {
            NeonButton(
                text = "▶ Start Dropzone Server",
                onClick = {
                    scope.launch {
                        val url = LocalFileDropzoneServer.startServer(context)
                        serverUrl = url
                        pairingPin = LocalFileDropzoneServer.pairingPin
                        isRunning = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(40.dp)
            )
        } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PastelRose)
                            .clickable {
                                LocalFileDropzoneServer.stopServer()
                                isRunning = false
                                serverUrl = ""
                                pairingPin = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Server", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Connection URL & Pairing PIN Badge
            if (isRunning && serverUrl.isNotBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SoftSurfaceElevated)
                        .border(1.dp, SageMint.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Open in PC Browser:", color = TextMuted, fontSize = 10.sp)
                            Text(serverUrl, color = SkyOpal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(serverUrl))
                                copiedNotice = true
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, "Copy URL", tint = SageMint, modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 6-Digit PIN Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SageMint.copy(alpha = 0.15f))
                            .border(1.dp, SageMint.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, null, tint = SageMint, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pairing PIN:", color = TextOffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(pairingPin, color = SageMint, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                    }

                    if (copiedNotice) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("✓ URL copied to clipboard!", color = SageMint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SoftSurface)
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tap 'Start' to generate a secure PIN and connect your PC.", color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Privacy Sharing Filters
            Text("Privacy Sharing Filters", color = TextPureWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftSurface)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PrivacyFilterRow("Full Internal Storage", Icons.Default.Storage, allowInternalStorage) {
                    allowInternalStorage = it
                    LocalFileDropzoneServer.allowInternalStorage = it
                }
                PrivacyFilterRow("Images (DCIM & Pictures)", Icons.Default.Image, allowImages) {
                    allowImages = it
                    LocalFileDropzoneServer.allowImages = it
                }
                PrivacyFilterRow("Videos (Movies & Camera)", Icons.Default.Movie, allowVideos) {
                    allowVideos = it
                    LocalFileDropzoneServer.allowVideos = it
                }
                PrivacyFilterRow("Downloads & Documents", Icons.Default.Description, allowDocuments) {
                    allowDocuments = it
                    LocalFileDropzoneServer.allowDocuments = it
                }
            }
        }
    }

@Composable
private fun PrivacyFilterRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (checked) SageMint else TextMuted, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(title, color = if (checked) TextOffWhite else TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = SageMint, checkmarkColor = Color.Black)
        )
    }
}
