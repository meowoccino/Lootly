package com.lootly.app.bridge

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class IntegrityHttpBridgeServer(
    private val scope: CoroutineScope,
    private val integrityBridge: WebViewIntegrityBridge
) {
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    val secret: String = generateSecret()

    val port: Int
        get() = serverSocket?.localPort ?: -1

    val endpointUrl: String
        get() = "http://127.0.0.1:$port/integrity"

    fun start(): Int {
        serverSocket?.let { existing ->
            if (!existing.isClosed) return existing.localPort
        }
        val socket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch(Dispatchers.IO) {
                    handleClient(client)
                }
            }
        }
        return socket.localPort
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { conn ->
            conn.soTimeout = 25_000
            val reader = BufferedReader(InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeResponse(conn.getOutputStream(), 400, "Bad Request", """{"error":"bad request"}""")
                return
            }
            val method = parts[0]
            val target = parts[1]

            val headers = HashMap<String, String>()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase(Locale.ROOT)
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                }
            }

            if (headers["x-bridge-secret"] != secret) {
                writeResponse(conn.getOutputStream(), 401, "Unauthorized", """{"error":"unauthorized"}""")
                return
            }

            val path = target.substringBefore('?')
            val query = target.substringAfter('?', "")

            if (method == "GET" && path == "/health") {
                writeResponse(conn.getOutputStream(), 200, "OK", """{"status":"ok"}""")
                return
            }

            if (method == "GET" && path == "/integrity") {
                val force = query.split('&').any { it == "force=1" || it == "force=true" }
                val tokenData = integrityBridge.getOrRefreshToken(force)
                if (tokenData == null) {
                    writeResponse(
                        conn.getOutputStream(),
                        503,
                        "Service Unavailable",
                        """{"error":"integrity token unavailable"}"""
                    )
                } else {
                    writeResponse(conn.getOutputStream(), 200, "OK", tokenData.toJson())
                }
                return
            }

            writeResponse(conn.getOutputStream(), 404, "Not Found", """{"error":"not found"}""")
        }
    }

    private fun writeResponse(out: OutputStream, status: Int, reason: String, jsonBody: String) {
        val bodyBytes = jsonBody.toByteArray(StandardCharsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n")
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ").append(bodyBytes.size).append("\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(StandardCharsets.US_ASCII)
        out.write(header)
        out.write(bodyBytes)
        out.flush()
    }

    companion object {
        private fun generateSecret(): String {
            val bytes = ByteArray(24)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
