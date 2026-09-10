package com.camcon.camcon

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


object CameraServer {

    const val PORT = 8080


    data class PendingRequest(
        val requestId: String
    )


    sealed class AccessResult {

        data class Requested(
            val requestId: String
        ) : AccessResult()


        data object WrongPassword :
            AccessResult()


        data object Error :
            AccessResult()
    }


    enum class ApprovalResult {

        WAITING,

        APPROVED,

        DENIED,

        ERROR
    }


    @Volatile
    private var password =
        ""


    @Volatile
    private var pendingRequest:
            PendingRequest? =
        null


    @Volatile
    private var approvedRequestId:
            String? =
        null


    @Volatile
    private var deniedRequestId:
            String? =
        null


    @Volatile
    private var serverRunning =
        false


    @Volatile
    var latestJpeg:
            ByteArray? =
        null


    @Volatile
    private var latestAudioPcm:
            ByteArray? =
        null

    @Volatile
    private var audioSequence:
            Long =
        0L


    private var serverSocket:
            ServerSocket? =
        null


    private var serverThread:
            Thread? =
        null


    fun setPassword(
        value: String
    ) {

        password =
            value

        pendingRequest =
            null

        approvedRequestId =
            null

        deniedRequestId =
            null

        clearAudio()
    }


    fun getPassword():
            String {

        return password
    }


    fun getPendingRequest():
            PendingRequest? {

        return pendingRequest
    }


    fun approveRequest(
        requestId: String
    ) {

        if (
            pendingRequest?.requestId ==
            requestId
        ) {

            approvedRequestId =
                requestId

            deniedRequestId =
                null
        }
    }


    fun denyRequest(
        requestId: String
    ) {

        if (
            pendingRequest?.requestId ==
            requestId
        ) {

            deniedRequestId =
                requestId

            approvedRequestId =
                null

            pendingRequest =
                null
        }
    }


    fun clearRequest() {

        pendingRequest =
            null

        approvedRequestId =
            null

        deniedRequestId =
            null
    }


    fun isApproved(
        requestId: String
    ): Boolean {

        return approvedRequestId ==
                requestId
    }


    fun isDenied(
        requestId: String
    ): Boolean {

        return deniedRequestId ==
                requestId
    }


    private fun createRequest():
            String {

        val id =
            UUID.randomUUID()
                .toString()


        pendingRequest =
            PendingRequest(id)


        approvedRequestId =
            null


        deniedRequestId =
            null


        return id
    }


    fun setAudioPcm(data: ByteArray) {
        latestAudioPcm = data
        audioSequence++
    }


    fun clearAudio() {
        latestAudioPcm = null
        audioSequence = 0L
    }


    fun startServer() {

        if (serverRunning) {
            return
        }


        serverRunning =
            true


        serverThread =
            Thread {

                try {

                    serverSocket =
                        ServerSocket(PORT)


                    while (
                        serverRunning
                    ) {

                        try {

                            val client =
                                serverSocket
                                    ?.accept()
                                    ?: break


                            Thread {

                                handleClient(
                                    client
                                )

                            }.start()

                        } catch (_: Exception) {
                        }
                    }

                } catch (_: Exception) {

                    serverRunning =
                        false
                }

            }.apply {

                name =
                    "CamCon-Server"

                start()
            }
    }


    private fun handleClient(
        socket: Socket
    ) {

        socket.use { client ->

            client.soTimeout =
                15000


            val reader =
                BufferedReader(

                    InputStreamReader(
                        client
                            .getInputStream()
                    )
                )


            val requestLine =
                reader.readLine()
                    ?: return


            while (true) {

                val line =
                    reader.readLine()
                        ?: break


                if (
                    line.isEmpty()
                ) {
                    break
                }
            }


            val path =
                requestLine
                    .split(" ")
                    .getOrNull(1)
                    ?: "/"


            when {

                path == "/" ->
                    sendHome(client)


                path == "/viewer" || path.startsWith("/viewer?") ->
                    sendViewerPage(client, path)


                path.startsWith(
                    "/login"
                ) ->
                    handleLogin(
                        client,
                        path
                    )


                path.startsWith(
                    "/status"
                ) ->
                    handleStatus(
                        client,
                        path
                    )


                path.startsWith(
                    "/stream"
                ) ->
                    handleStream(
                        client,
                        path
                    )


                path.startsWith(
                    "/audio"
                ) ->
                    handleAudio(
                        client,
                        path
                    )


                path == "/ping" ->
                    sendText(
                        client,
                        "CamCon OK"
                    )


                else ->
                    send404(client)
            }
        }
    }


    private fun handleLogin(

        socket: Socket,

        path: String

    ) {

        val suppliedPassword =
            getQueryParameter(
                path,
                "password"
            )


        if (
            suppliedPassword == null
        ) {

            sendText(
                socket,
                "ERROR"
            )

            return
        }


        if (
            suppliedPassword !=
            password
        ) {

            sendText(
                socket,
                "WRONG_PASSWORD"
            )

            return
        }


        val requestId =
            createRequest()


        sendText(

            socket,

            "REQUEST:$requestId"
        )
    }


    private fun handleStatus(

        socket: Socket,

        path: String

    ) {

        val id =
            getQueryParameter(
                path,
                "id"
            )


        if (id == null) {

            sendText(
                socket,
                "ERROR"
            )

            return
        }


        val result =
            when {

                isApproved(id) ->
                    "APPROVED"


                isDenied(id) ->
                    "DENIED"


                pendingRequest
                    ?.requestId == id ->
                    "WAITING"


                else ->
                    "DENIED"
            }


        sendText(
            socket,
            result
        )
    }


    private fun handleStream(

        socket: Socket,

        path: String

    ) {

        val id =
            getQueryParameter(
                path,
                "id"
            )


        if (
            id == null ||
            !isApproved(id)
        ) {

            sendText(
                socket,
                "ACCESS_DENIED"
            )

            return
        }


        val output =
            socket.getOutputStream()


        val header =

            "HTTP/1.1 200 OK\r\n" +

                    "Content-Type: " +
                    "multipart/x-mixed-replace; " +
                    "boundary=frame\r\n" +

                    "Cache-Control: no-cache, " +
                    "no-store, must-revalidate\r\n" +

                    "Pragma: no-cache\r\n" +

                    "Connection: close\r\n" +

                    "\r\n"


        output.write(
            header.toByteArray()
        )

        output.flush()


        try {

            while (

                serverRunning &&

                isApproved(id) &&

                !socket.isClosed

            ) {

                val frame =
                    latestJpeg


                if (
                    frame != null
                ) {

                    output.write(
                        "--frame\r\n"
                            .toByteArray(Charsets.UTF_8)
                    )


                    output.write(
                        ("Content-Type: image/jpeg\r\n")
                            .toByteArray(Charsets.UTF_8)
                    )


                    output.write(
                        ("Content-Length: ${frame.size}\r\n\r\n")
                            .toByteArray(Charsets.UTF_8)
                    )


                    output.write(frame)


                    output.write(
                        "\r\n".toByteArray(Charsets.UTF_8)
                    )


                    output.flush()
                }


                Thread.sleep(80)
            }

        } catch (_: Exception) {
        }
    }


    private fun handleAudio(
        socket: Socket,
        path: String
    ) {
        val id =
            getQueryParameter(
                path,
                "id"
            )

        if (
            id == null ||
            !isApproved(id)
        ) {
            sendText(
                socket,
                "ACCESS_DENIED"
            )
            return
        }

        val output =
            socket.getOutputStream()

        val header =
            "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/octet-stream\r\n" +
                    "Cache-Control: no-cache, no-store, must-revalidate\r\n" +
                    "Pragma: no-cache\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"

        output.write(header.toByteArray(Charsets.UTF_8))
        output.flush()

        var lastSequence = -1L

        try {
            while (
                serverRunning &&
                isApproved(id) &&
                !socket.isClosed
            ) {
                val sequence = audioSequence
                val pcm = latestAudioPcm

                if (
                    pcm != null &&
                    sequence != lastSequence
                ) {
                    // 4-byte big-endian PCM packet length.
                    output.write((pcm.size ushr 24) and 0xFF)
                    output.write((pcm.size ushr 16) and 0xFF)
                    output.write((pcm.size ushr 8) and 0xFF)
                    output.write(pcm.size and 0xFF)
                    output.write(pcm)
                    output.flush()
                    lastSequence = sequence
                }

                Thread.sleep(20)
            }
        } catch (_: Exception) {
        }
    }


    private fun sendViewerPage(
        socket: Socket,
        path: String
    ) {
        val preauthorizedId =
            getQueryParameter(path, "id") ?: ""

        val html = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CamCon Viewer</title>
<style>
* { box-sizing: border-box; }
html, body { margin:0; padding:0; width:100%; min-height:100%; }
body {
    background:#0b0f14;
    color:white;
    font-family:Arial,sans-serif;
    min-height:100vh;
    display:flex;
    align-items:center;
    justify-content:center;
    padding:24px;
}
.card {
    width:min(94vw,900px);
    background:#151b23;
    border-radius:20px;
    padding:28px;
    box-shadow:0 20px 60px rgba(0,0,0,.45);
    text-align:center;
}
h1 { margin-top:0; }
input {
    width:100%; padding:14px; margin:12px 0;
    border-radius:10px; border:1px solid #384352;
    background:#0d1218; color:white; font-size:16px;
    text-align:center;
}
button {
    width:100%; padding:14px; border:0; border-radius:10px;
    background:#00bcd4; color:#001014; font-size:16px;
    font-weight:bold; cursor:pointer;
}
#status { margin-top:18px; color:#b8c2cc; text-align:center; }
#video {
    width:100%; height:auto; max-width:100%; max-height:78vh;
    margin-top:20px; border-radius:12px; display:none;
    background:black; object-fit:contain;
}
#controls { display:none; margin-top:12px; gap:10px; }
#controls button { width:auto; min-width:130px; margin:auto; }
</style>
</head>
<body>
<div class="card">
    <h1>CamCon Viewer</h1>
    <input id="password" type="password" placeholder="Enter password" autocomplete="current-password">
    <button id="connectButton" onclick="connect()">Connect</button>
    <div id="status">Enter the camera password.</div>
    <img id="video" alt="CamCon live feed">
    <div id="controls">
        <button id="muteButton" onclick="toggleMute()">Mute</button>
    </div>
</div>
<script>
let requestId = "${preauthorizedId}";
let timer = null;
let audioContext = null;
let gainNode = null;
let nextAudioTime = 0;
let muted = false;
let audioAbort = null;

function setStatus(text) {
    const status = document.getElementById("status");
    status.innerText = text;
    const lower = text.toLowerCase();
    if (
        lower.includes("wrong") ||
        lower.includes("denied") ||
        lower.includes("error") ||
        lower.includes("could not") ||
        lower.includes("lost") ||
        lower.includes("please enter")
    ) {
        status.style.color = "#f44336";
    } else {
        status.style.color = "#00bcd4";
    }
}

async function connect() {
    const password = document.getElementById("password").value;
    if (!password) {
        setStatus("Please enter the password.");
        return;
    }

    setStatus("Checking password...");

    // Initialize/resume Web Audio directly from the user's tap.
    // The approval may arrive later, so doing this here avoids autoplay blocking.
    setupAudio();
    if (audioContext) {
        try { await audioContext.resume(); } catch (e) {}
    }

    try {
        const response = await fetch("/login?password=" + encodeURIComponent(password));
        const text = await response.text();

        if (text === "WRONG_PASSWORD") {
            setStatus("Wrong password.");
            return;
        }

        if (!text.startsWith("REQUEST:")) {
            setStatus("Connection error.");
            return;
        }

        requestId = text.substring(8);
        setStatus("Password correct. Waiting for owner approval...");
        checkStatus();
    } catch (error) {
        setStatus("Could not connect to CamCon.");
    }
}

async function checkStatus() {
    if (!requestId) return;

    try {
        const response = await fetch(
            "/status?id=" + encodeURIComponent(requestId),
            { cache:"no-store" }
        );
        const status = await response.text();

        if (status === "WAITING") {
            setStatus("Waiting for camera owner's approval...");
            timer = setTimeout(checkStatus, 1000);
        } else if (status === "APPROVED") {
            setStatus("LIVE");
            document.getElementById("video").src =
                "/stream?id=" + encodeURIComponent(requestId);
            document.getElementById("video").style.display = "block";
            document.getElementById("controls").style.display = "flex";
            document.getElementById("connectButton").style.display = "none";
            document.getElementById("password").style.display = "none";
            startAudio();
        } else {
            setStatus("Access denied by camera owner.");
        }
    } catch (error) {
        setStatus("Connection lost.");
    }
}

function setupAudio() {
    if (audioContext) return;
    const AudioContextClass = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextClass) return;
    audioContext = new AudioContextClass({ sampleRate: 16000 });
    gainNode = audioContext.createGain();
    gainNode.gain.value = muted ? 0 : 1;
    gainNode.connect(audioContext.destination);
    nextAudioTime = audioContext.currentTime + 0.05;
}

async function startAudio() {
    setupAudio();
    if (!audioContext) return;

    try { await audioContext.resume(); } catch (e) {}

    if (audioAbort) audioAbort.abort();
    audioAbort = new AbortController();

    try {
        const response = await fetch(
            "/audio?id=" + encodeURIComponent(requestId),
            { cache:"no-store", signal:audioAbort.signal }
        );

        if (!response.body) return;
        const reader = response.body.getReader();
        let pending = new Uint8Array(0);

        while (true) {
            const result = await reader.read();
            if (result.done) break;

            const incoming = new Uint8Array(result.value);
            const merged = new Uint8Array(pending.length + incoming.length);
            merged.set(pending, 0);
            merged.set(incoming, pending.length);
            pending = merged;

            while (pending.length >= 4) {
                const length =
                    (pending[0] << 24) |
                    (pending[1] << 16) |
                    (pending[2] << 8) |
                    pending[3];

                if (length <= 0 || length > 200000) {
                    pending = new Uint8Array(0);
                    break;
                }

                if (pending.length < 4 + length) break;

                const pcm = pending.slice(4, 4 + length);
                pending = pending.slice(4 + length);
                playPcm16(pcm);
            }
        }
    } catch (error) {
        if (error.name !== "AbortError") {
            // Audio can stop independently; video remains available.
        }
    }
}

function playPcm16(bytes) {
    if (!audioContext || !gainNode) return;

    const sampleCount = Math.floor(bytes.length / 2);
    if (sampleCount <= 0) return;

    const buffer = audioContext.createBuffer(1, sampleCount, 16000);
    const channel = buffer.getChannelData(0);
    const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);

    for (let i = 0; i < sampleCount; i++) {
        channel[i] = view.getInt16(i * 2, true) / 32768.0;
    }

    const source = audioContext.createBufferSource();
    source.buffer = buffer;
    source.connect(gainNode);

    const now = audioContext.currentTime;
    if (nextAudioTime < now + 0.03) nextAudioTime = now + 0.03;
    source.start(nextAudioTime);
    nextAudioTime += buffer.duration;
}

function toggleMute() {
    setupAudio();
    muted = !muted;
    if (gainNode) gainNode.gain.value = muted ? 0 : 1;
    document.getElementById("muteButton").innerText = muted ? "Unmute" : "Mute";
}

if (requestId) {
    document.getElementById("password").style.display = "none";
    document.getElementById("connectButton").style.display = "none";
    checkStatus();
}
</script>
</body>
</html>
        """.trimIndent()

        sendHtml(socket, html)
    }

    private fun sendHome(
        socket: Socket
    ) {

        val html = """
<!DOCTYPE html>

<html>

<head>

<meta name="viewport"
      content="width=device-width, initial-scale=1">

<title>CamCon</title>

</head>

<body>

<h1>CamCon</h1>

<p>
Open <b>/viewer</b>
to watch the camera.
</p>

</body>

</html>
        """.trimIndent()


        sendHtml(
            socket,
            html
        )
    }


    private fun sendHtml(

        socket: Socket,

        html: String

    ) {

        val bytes =
            html.toByteArray(
                Charsets.UTF_8
            )


        val header =

            "HTTP/1.1 200 OK\r\n" +

                    "Content-Type: " +
                    "text/html; charset=UTF-8\r\n" +

                    "Content-Length: " +
                    "${bytes.size}\r\n" +

                    "Connection: close\r\n" +

                    "\r\n"


        val output =
            socket.getOutputStream()


        output.write(
            header.toByteArray()
        )


        output.write(bytes)


        output.flush()
    }


    private fun sendText(

        socket: Socket,

        text: String

    ) {

        val bytes =
            text.toByteArray(
                Charsets.UTF_8
            )


        val header =

            "HTTP/1.1 200 OK\r\n" +

                    "Content-Type: " +
                    "text/plain; charset=UTF-8\r\n" +

                    "Content-Length: " +
                    "${bytes.size}\r\n" +

                    "Connection: close\r\n" +

                    "\r\n"


        val output =
            socket.getOutputStream()


        output.write(
            header.toByteArray()
        )


        output.write(bytes)


        output.flush()
    }


    private fun send404(
        socket: Socket
    ) {

        val body =
            "404 Not Found"


        val bytes =
            body.toByteArray()


        val header =

            "HTTP/1.1 404 Not Found\r\n" +

                    "Content-Type: " +
                    "text/plain\r\n" +

                    "Content-Length: " +
                    "${bytes.size}\r\n" +

                    "Connection: close\r\n" +

                    "\r\n"


        val output =
            socket.getOutputStream()


        output.write(
            header.toByteArray()
        )


        output.write(bytes)


        output.flush()
    }


    private fun getQueryParameter(

        path: String,

        name: String

    ): String? {

        val queryStart =
            path.indexOf("?")


        if (
            queryStart == -1
        ) {
            return null
        }


        val query =
            path.substring(
                queryStart + 1
            )


        for (
        part in query.split("&")
        ) {

            val pieces =
                part.split(
                    "=",
                    limit = 2
                )


            if (
                pieces.size == 2 &&
                pieces[0] == name
            ) {

                return try {

                    URLDecoder.decode(
                        pieces[1],
                        "UTF-8"
                    )

                } catch (_: Exception) {

                    null
                }
            }
        }


        return null
    }


    fun getLocalIpAddress():
            String {

        try {

            val interfaces =
                Collections.list(
                    NetworkInterface
                        .getNetworkInterfaces()
                )


            for (
            networkInterface
            in interfaces
            ) {

                if (
                    !networkInterface.isUp ||
                    networkInterface.isLoopback
                ) {
                    continue
                }


                val addresses =
                    Collections.list(
                        networkInterface
                            .inetAddresses
                    )


                for (
                address
                in addresses
                ) {

                    if (
                        !address
                            .isLoopbackAddress &&
                        address
                            .hostAddress
                            ?.contains(":")
                        == false
                    ) {

                        return address
                            .hostAddress
                            ?: "Unknown"
                    }
                }
            }

        } catch (_: Exception) {
        }


        return "Unknown"
    }


    fun getViewerUrl(
        ip: String,
        requestId: String
    ): String {
        return "http://$ip:$PORT/viewer?id=" +
                URLEncoder.encode(
                    requestId,
                    "UTF-8"
                )
    }


    fun getStreamUrl(

        ip: String,

        requestId: String

    ): String {

        return "http://$ip:$PORT/stream?id=" +
                URLEncoder.encode(
                    requestId,
                    "UTF-8"
                )
    }


    suspend fun requestAccess(

        ip: String,

        suppliedPassword: String

    ): AccessResult = withContext(Dispatchers.IO) {

        try {

            val cleanIp = ip.trim()

            if (cleanIp.isBlank()) {
                return@withContext AccessResult.Error
            }

            val url =
                URL(
                    "http://$cleanIp:$PORT/login?password=" +
                            URLEncoder.encode(
                                suppliedPassword,
                                "UTF-8"
                            )
                )

            val connection =
                (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    requestMethod = "GET"
                    useCaches = false
                }

            try {
                val responseCode = connection.responseCode
                val stream =
                    if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val response =
                    stream?.bufferedReader()?.use { it.readText() } ?: ""

                when {
                    response.startsWith("REQUEST:") ->
                        AccessResult.Requested(
                            response.substring(8).trim()
                        )

                    response == "WRONG_PASSWORD" ->
                        AccessResult.WrongPassword

                    else ->
                        AccessResult.Error
                }
            } finally {
                connection.disconnect()
            }

        } catch (_: Exception) {
            AccessResult.Error
        }
    }


    suspend fun checkApproval(

        ip: String,

        requestId: String

    ): ApprovalResult = withContext(Dispatchers.IO) {

        try {

            val cleanIp = ip.trim()

            if (cleanIp.isBlank() || requestId.isBlank()) {
                return@withContext ApprovalResult.ERROR
            }

            val url =
                URL(
                    "http://$cleanIp:$PORT/status?id=" +
                            URLEncoder.encode(
                                requestId,
                                "UTF-8"
                            )
                )

            val connection =
                (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    requestMethod = "GET"
                    useCaches = false
                }

            try {
                val responseCode = connection.responseCode
                val stream =
                    if (responseCode in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }

                val response =
                    stream?.bufferedReader()?.use { it.readText() } ?: ""

                when (response) {
                    "WAITING" -> ApprovalResult.WAITING
                    "APPROVED" -> ApprovalResult.APPROVED
                    "DENIED" -> ApprovalResult.DENIED
                    else -> ApprovalResult.ERROR
                }
            } finally {
                connection.disconnect()
            }

        } catch (_: Exception) {
            ApprovalResult.ERROR
        }
    }


    fun stopServer() {

        serverRunning =
            false


        try {

            serverSocket?.close()

        } catch (_: Exception) {
        }


        serverSocket =
            null


        serverThread =
            null


        latestJpeg =
            null


        clearAudio()


        clearRequest()
    }
}
