package com.lootly.app.engine

import android.content.Context
import android.net.ConnectivityManager
import android.os.Process as AndroidProcess
import com.lootly.app.data.model.LogLevel
import com.lootly.app.data.model.TwitchSession
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MinerProcessManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val configGenerator: MinerConfigGenerator = MinerConfigGenerator(context.filesDir),
    private val controlClient: MinerControlClient = MinerControlClient(),
    private val onLine: (String) -> Unit,
    private val onAppLog: (LogLevel, String, String) -> Unit,
    private val onProcessExited: (Int) -> Unit
) {
    @Volatile private var process: Process? = null
    @Volatile private var readerJob: Job? = null
    @Volatile private var watchJob: Job? = null
    private val sigStopped = AtomicBoolean(false)

    @Volatile var analyticsPort: Int = -1
        private set
    @Volatile var apiKey: String = ""
        private set

    val isRunning: Boolean
        get() = process?.isAlive == true

    fun resolveBinaryFile(): File {
        return File(context.applicationInfo.nativeLibraryDir, BINARY_NAME)
    }

    suspend fun start(
        session: TwitchSession,
        integrityBridgeUrl: String,
        integrityBridgeSecret: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        stop()
        val binary = resolveBinaryFile()
        if (!binary.exists()) {
            val err = "Go miner binary not found at ${binary.absolutePath}. Ensure libtwitchminer.so is built for arm64-v8a."
            onAppLog(LogLevel.ERROR, "Engine", err)
            return@withContext Result.failure(IllegalStateException(err))
        }

        val paths = configGenerator.generate(session)
        val port = allocateLoopbackPort()
        val key = generateSecretHex(20)
        analyticsPort = port
        apiKey = key

        val dnsServers = discoverActiveDnsServers()
        val certDirs = resolveAndroidCertDirs()

        val cmd = listOf(
            binary.absolutePath,
            "-config", paths.configsDir.absolutePath,
            "-bind", "127.0.0.1",
            "-port", port.toString(),
            "-log-format", "json",
            "-log-level", "DEBUG",
            "-no-banner",
            "-no-tray",
            "-no-config-editor",
            "-skip-unauth"
        )

        val upperLogin = MinerConfigGenerator.sanitizeLogin(session.login)
            .uppercase(Locale.US)
            .replace('-', '_')

        val pb = ProcessBuilder(cmd)
            .directory(context.filesDir)
            .redirectErrorStream(true)

        pb.environment().apply {
            put("HOME", context.filesDir.absolutePath)
            put("DATA_DIR", paths.dataDir.absolutePath)
            put("TWITCH_AUTH_TOKEN", session.authToken)
            put("TWITCH_AUTH_TOKEN_$upperLogin", session.authToken)
            put("TWITCH_DEVICE_ID", session.deviceId)
            put("TWITCH_INTEGRITY_BRIDGE_URL", integrityBridgeUrl)
            put("TWITCH_INTEGRITY_BRIDGE_SECRET", integrityBridgeSecret)
            put("DASHBOARD_API_KEY", key)
            put("TELEMETRY_AGREE", "false")
            put("NO_BANNER", "true")
            put("NO_TRAY", "true")
            put("NO_CONFIG_EDITOR", "true")
            put("SKIP_UNAUTH", "true")
            put("BIND_HOST", "127.0.0.1")
            if (dnsServers.isNotEmpty()) {
                put("ANDROID_DNS_SERVERS", dnsServers.joinToString(","))
            }
            if (certDirs.isNotEmpty()) {
                put("SSL_CERT_DIR", certDirs)
            }
        }

        runCatching {
            val dnsDisplay = dnsServers.ifEmpty { listOf("1.1.1.1") }.joinToString(", ")
            onAppLog(LogLevel.INFO, "Engine", "Launching Go miner for @${session.login} (loopback :$port, DNS: $dnsDisplay)")
            val proc = pb.start()
            process = proc
            sigStopped.set(false)

            readerJob = scope.launch(Dispatchers.IO) {
                runCatching {
                    BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8)).use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            onLine(line)
                        }
                    }
                }
            }

            watchJob = scope.launch(Dispatchers.IO) {
                val exitCode = runCatching { proc.waitFor() }.getOrDefault(-1)
                if (process === proc) {
                    process = null
                    onProcessExited(exitCode)
                }
            }
            port
        }.onFailure { err ->
            onAppLog(LogLevel.ERROR, "Engine", "Failed to start Go miner process: ${err.message}")
        }
    }

    suspend fun pauseWatch(reason: String): Boolean {
        val proc = process ?: return false
        if (!proc.isAlive) return false
        if (controlClient.pause(analyticsPort, apiKey, reason)) return true
        val pid = extractPid(proc)
        if (pid > 0) {
            return runCatching {
                AndroidProcess.sendSignal(pid, SIGNAL_SIGSTOP)
                sigStopped.set(true)
                onAppLog(LogLevel.WARN, "Pause", "Sent SIGSTOP to Go miner process (pid=$pid, reason=$reason)")
                true
            }.getOrDefault(false)
        }
        return false
    }

    suspend fun resumeWatch(): Boolean {
        val proc = process ?: return false
        if (!proc.isAlive) return false
        if (sigStopped.compareAndSet(true, false)) {
            val pid = extractPid(proc)
            if (pid > 0) {
                runCatching {
                    AndroidProcess.sendSignal(pid, SIGNAL_SIGCONT)
                    onAppLog(LogLevel.EVENT, "Resume", "Sent SIGCONT to Go miner process (pid=$pid)")
                }
            }
        }
        return controlClient.resume(analyticsPort, apiKey)
    }

    suspend fun forceRefreshBadges(): Boolean {
        if (!isRunning || sigStopped.get()) return false
        return controlClient.forceRefreshBadges(analyticsPort, apiKey)
    }

    suspend fun pinBadge(campaign: String): Boolean {
        if (!isRunning || sigStopped.get()) return false
        return controlClient.pinBadge(analyticsPort, apiKey, campaign)
    }

    fun stop() {
        val proc = process
        process = null
        watchJob?.cancel()
        watchJob = null

        if (proc != null) {
            if (sigStopped.compareAndSet(true, false)) {
                val pid = extractPid(proc)
                if (pid > 0) {
                    runCatching { AndroidProcess.sendSignal(pid, SIGNAL_SIGCONT) }
                }
            }
            runCatching {
                proc.destroy()
                if (!proc.waitFor(4, TimeUnit.SECONDS)) {
                    proc.destroyForcibly()
                    proc.waitFor(2, TimeUnit.SECONDS)
                }
            }
        }

        readerJob?.cancel()
        readerJob = null
        analyticsPort = -1
        apiKey = ""
    }

    private fun discoverActiveDnsServers(): List<String> = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching emptyList()
        val network = cm.activeNetwork ?: return@runCatching emptyList()
        val props = cm.getLinkProperties(network) ?: return@runCatching emptyList()
        props.dnsServers.mapNotNull { it.hostAddress?.takeIf { addr -> addr.isNotBlank() } }
    }.getOrDefault(emptyList())

    private fun resolveAndroidCertDirs(): String {
        return listOf("/apex/com.android.conscrypt/cacerts", "/system/etc/security/cacerts")
            .filter { File(it).isDirectory }
            .joinToString(":")
    }

    private fun allocateLoopbackPort(): Int {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { return it.localPort }
    }

    private fun extractPid(proc: Process): Int {
        runCatching {
            val pid = Process::class.java.getMethod("pid").invoke(proc) as? Long
            if (pid != null && pid > 0L) return pid.toInt()
        }
        return runCatching {
            val field = proc.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(proc)
        }.getOrDefault(-1)
    }

    companion object {
        const val BINARY_NAME = "libtwitchminer.so"
        private const val SIGNAL_SIGSTOP = 19
        private const val SIGNAL_SIGCONT = 18

        private fun generateSecretHex(numBytes: Int): String {
            val bytes = ByteArray(numBytes)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
