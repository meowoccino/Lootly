package com.lootly.app.bridge

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lootly.app.data.auth.TwitchAccountRepository
import com.lootly.app.data.model.LogLevel
import com.lootly.app.data.model.TwitchSession
import java.io.ByteArrayInputStream
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class WebViewIntegrityBridge(
    private val context: Context,
    private val onLog: (LogLevel, String, String) -> Unit,
    private val onTokenUpdated: (IntegrityTokenData) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fetchMutex = Mutex()
    private val cachedToken = AtomicReference<IntegrityTokenData?>(null)
    private val pendingDeferred = AtomicReference<CompletableDeferred<IntegrityTokenData?>?>(null)

    @Volatile private var webView: WebView? = null
    @Volatile private var activeSession: TwitchSession? = null
    @Volatile private var pageBooted = false
    @Volatile private var destroyed = false

    private val clientSessionId: String = generateHex(16)
    @Volatile private var observedClientVersion: String = DEFAULT_CLIENT_VERSION
    @Volatile private var observedClientId: String = TwitchAccountRepository.TWITCH_BROWSER_CLIENT_ID

    inner class JsIntegrityCallback {
        @JavascriptInterface
        fun onIntegrityResult(
            token: String?,
            expirationMs: Long,
            deviceId: String?,
            clientId: String?,
            clientVersion: String?,
            sessionId: String?
        ) {
            val clean = token?.trim().orEmpty()
            if (clean.isBlank()) {
                onIntegrityError("Empty integrity token received from WebView")
                return
            }
            if (cachedToken.get() == null) {
                onLog(LogLevel.INFO, "Integrity", "Twitch Client-Integrity session ready")
            }
            publishToken(
                token = clean,
                expirationMs = expirationMs,
                deviceId = deviceId.orEmpty(),
                clientId = clientId.orEmpty(),
                clientVersion = clientVersion.orEmpty(),
                sessionId = sessionId.orEmpty()
            )
        }

        @JavascriptInterface
        fun onIntegrityError(message: String?) {
            onLog(LogLevel.WARN, "Integrity", "WebView integrity handshake error: ${message ?: "unknown"}")
        }
    }

    fun start(session: TwitchSession) {
        activeSession = session
        destroyed = false
        mainHandler.post {
            if (!destroyed) {
                ensureWebViewInitialized(session)
            }
        }
    }

    suspend fun getOrRefreshToken(forceRefresh: Boolean = false): IntegrityTokenData? {
        val cached = cachedToken.get()
        if (!forceRefresh && cached != null && cached.isFresh()) {
            return cached
        }
        return fetchMutex.withLock {
            val rechecked = cachedToken.get()
            if (!forceRefresh && rechecked != null && rechecked.isFresh()) {
                return@withLock rechecked
            }
            val session = activeSession ?: return@withLock null
            if (destroyed) return@withLock null

            val deferred = CompletableDeferred<IntegrityTokenData?>()
            pendingDeferred.getAndSet(deferred)?.cancel()

            mainHandler.post {
                if (destroyed) {
                    deferred.complete(null)
                    return@post
                }
                val view = ensureWebViewInitialized(session)
                if (pageBooted) {
                    triggerJsIntegrityFetch(view, session)
                } else {
                    view.loadUrl(BRIDGE_BOOT_URL)
                }
            }

            val first = withTimeoutOrNull(BRIDGE_FETCH_TIMEOUT_MS) { deferred.await() }
            if (first != null) return@withLock first

            onLog(LogLevel.WARN, "Integrity", "Active handshake timed out; reloading headless Twitch session")
            val reloadDeferred = CompletableDeferred<IntegrityTokenData?>()
            pendingDeferred.getAndSet(reloadDeferred)?.cancel()
            mainHandler.post {
                if (destroyed) {
                    reloadDeferred.complete(null)
                    return@post
                }
                pageBooted = false
                webView?.loadUrl(BRIDGE_BOOT_URL)
            }
            withTimeoutOrNull(BRIDGE_RELOAD_TIMEOUT_MS) { reloadDeferred.await() } ?: cachedToken.get()
        }
    }

    fun destroy() {
        destroyed = true
        pendingDeferred.getAndSet(null)?.complete(null)
        cachedToken.set(null)
        pageBooted = false
        mainHandler.post {
            runCatching {
                webView?.apply {
                    stopLoading()
                    removeJavascriptInterface(JS_BRIDGE_NAME)
                    webViewClient = WebViewClient()
                    loadUrl("about:blank")
                    destroy()
                }
            }
            webView = null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebViewInitialized(session: TwitchSession): WebView {
        webView?.let { return it }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setCookie(TWITCH_ORIGIN, "auth-token=${session.authToken}; Domain=.twitch.tv; Path=/; Secure")
        cookieManager.setCookie(TWITCH_ORIGIN, "unique_id=${session.deviceId}; Domain=.twitch.tv; Path=/; Secure")
        cookieManager.flush()

        val view = WebView(context.applicationContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = MOBILE_CHROME_USER_AGENT
            settings.loadsImagesAutomatically = false
            settings.blockNetworkImage = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            cookieManager.setAcceptThirdPartyCookies(this, true)
            addJavascriptInterface(JsIntegrityCallback(), JS_BRIDGE_NAME)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString()?.lowercase(Locale.ROOT) ?: return null
                    if (shouldDropAssetUrl(url)) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (destroyed || view == null) return
                    pageBooted = true
                    view.evaluateJavascript(INJECT_FETCH_OBSERVER_JS, null)
                    val currentSession = activeSession ?: session
                    triggerJsIntegrityFetch(view, currentSession)
                }
            }
        }
        webView = view
        onLog(LogLevel.INFO, "Integrity", "Booting headless Twitch WebView integrity session")
        view.loadUrl(BRIDGE_BOOT_URL)
        return view
    }

    private fun triggerJsIntegrityFetch(view: WebView, session: TwitchSession) {
        val qAuth = JSONObject.quote(session.authToken)
        val qDev = JSONObject.quote(session.deviceId)
        val qCid = JSONObject.quote(observedClientId)
        val qSid = JSONObject.quote(clientSessionId)
        view.evaluateJavascript(
            """
            (function() {
                if (typeof window.__agyFetchIntegrity === 'function') {
                    window.__agyFetchIntegrity($qAuth, $qDev, $qCid, $qSid);
                } else {
                    fetch('https://gql.twitch.tv/integrity', {
                        method: 'POST',
                        credentials: 'include',
                        headers: {
                            'Content-Type': 'application/json',
                            'Authorization': 'OAuth ' + $qAuth,
                            'Client-Id': $qCid,
                            'X-Device-Id': $qDev,
                            'Client-Session-Id': $qSid
                        },
                        body: '{}'
                    })
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        if (data && data.token && window.AndroidIntegrityBridge) {
                            window.AndroidIntegrityBridge.onIntegrityResult(
                                data.token,
                                Number(data.expiration || 0),
                                $qDev,
                                $qCid,
                                '',
                                $qSid
                            );
                        }
                    })
                    .catch(function(err) {
                        if (window.AndroidIntegrityBridge) {
                            window.AndroidIntegrityBridge.onIntegrityError(String(err));
                        }
                    });
                }
            })();
            """.trimIndent(),
            null
        )
    }

    private fun publishToken(
        token: String,
        expirationMs: Long,
        deviceId: String,
        clientId: String,
        clientVersion: String,
        sessionId: String
    ) {
        if (token.isBlank()) return
        val exp = if (expirationMs > System.currentTimeMillis()) {
            expirationMs
        } else {
            System.currentTimeMillis() + 1_800_000L
        }
        val data = IntegrityTokenData(
            token = token,
            expirationEpochMs = exp,
            deviceId = deviceId.ifBlank { activeSession?.deviceId.orEmpty() },
            clientId = clientId.ifBlank { observedClientId },
            clientVersion = clientVersion.ifBlank { observedClientVersion },
            clientSessionId = sessionId.ifBlank { clientSessionId },
            userAgent = MOBILE_CHROME_USER_AGENT
        )
        cachedToken.set(data)
        onTokenUpdated(data)
        pendingDeferred.getAndSet(null)?.complete(data)
    }

    companion object {
        const val TWITCH_ORIGIN = "https://www.twitch.tv"
        const val BRIDGE_BOOT_URL = "https://www.twitch.tv/drops/inventory"
        const val JS_BRIDGE_NAME = "AndroidIntegrityBridge"
        const val DEFAULT_CLIENT_VERSION = "ef928475-9403-42f2-8a34-55784bd08e16"
        const val MOBILE_CHROME_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36"
        private const val BRIDGE_FETCH_TIMEOUT_MS = 12_000L
        private const val BRIDGE_RELOAD_TIMEOUT_MS = 18_000L

        private val BLOCKED_EXTENSIONS = listOf(
            ".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg", ".ico",
            ".woff", ".woff2", ".ttf", ".otf",
            ".mp4", ".webm", ".ts", ".m4s", ".m3u8", ".mp3", ".aac"
        )

        private val INJECT_FETCH_OBSERVER_JS = """
            (function() {
                if (window.__agyIntegrityHooked) return;
                window.__agyIntegrityHooked = true;
                function readCookie(name) {
                    var m = document.cookie.match(new RegExp('(?:^|; )' + name.replace(/([.$?*|{}()\[\]\\\/+^])/g, '\\$1') + '=([^;]*)'));
                    return m ? decodeURIComponent(m[1]) : '';
                }
                var origFetch = window.fetch;
                window.fetch = function(input, init) {
                    var url = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
                    var reqHeaders = (init && init.headers) ? init.headers : null;
                    var capturedVersion = '', capturedClientId = '', capturedSession = '', capturedDevice = '', capturedIntegrity = '';
                    try {
                        if (reqHeaders) {
                            var getH = function(k) {
                                if (typeof reqHeaders.get === 'function') return reqHeaders.get(k) || '';
                                return reqHeaders[k] || reqHeaders[k.toLowerCase()] || '';
                            };
                            capturedVersion = getH('Client-Version');
                            capturedClientId = getH('Client-Id');
                            capturedSession = getH('Client-Session-Id');
                            capturedDevice = getH('X-Device-Id');
                            capturedIntegrity = getH('Client-Integrity');
                            if (capturedVersion) window.__agyClientVersion = capturedVersion;
                            if (capturedClientId) window.__agyClientId = capturedClientId;
                            if (capturedSession) window.__agySessionId = capturedSession;
                            if (capturedIntegrity && window.AndroidIntegrityBridge) {
                                window.AndroidIntegrityBridge.onIntegrityResult(
                                    capturedIntegrity, Date.now() + 1800000,
                                    capturedDevice || readCookie('unique_id'),
                                    capturedClientId || window.__agyClientId || 'kimne78kx3ncx6brgo4mv6wki5h1ko',
                                    capturedVersion || window.__agyClientVersion || '',
                                    capturedSession || window.__agySessionId || ''
                                );
                            }
                        }
                    } catch (e) {}
                    var promise = origFetch.apply(this, arguments);
                    if (url && url.indexOf('gql.twitch.tv/integrity') !== -1) {
                        promise.then(function(resp) {
                            try {
                                resp.clone().json().then(function(data) {
                                    if (data && data.token && window.AndroidIntegrityBridge) {
                                        window.AndroidIntegrityBridge.onIntegrityResult(
                                            data.token, Number(data.expiration || 0),
                                            capturedDevice || readCookie('unique_id'),
                                            capturedClientId || window.__agyClientId || 'kimne78kx3ncx6brgo4mv6wki5h1ko',
                                            capturedVersion || window.__agyClientVersion || '',
                                            capturedSession || window.__agySessionId || ''
                                        );
                                    }
                                }).catch(function(){});
                            } catch (e) {}
                        }).catch(function(){});
                    }
                    return promise;
                };
                window.__agyFetchIntegrity = function(authToken, deviceId, clientId, sessionId) {
                    var dev = readCookie('unique_id') || deviceId;
                    var cid = window.__agyClientId || clientId;
                    var sid = window.__agySessionId || sessionId;
                    var headers = {
                        'Content-Type': 'application/json',
                        'Authorization': 'OAuth ' + authToken,
                        'Client-Id': cid,
                        'X-Device-Id': dev,
                        'Client-Session-Id': sid
                    };
                    if (window.__agyClientVersion) headers['Client-Version'] = window.__agyClientVersion;
                    window.fetch('https://gql.twitch.tv/integrity', {
                        method: 'POST', credentials: 'include', headers: headers, body: '{}'
                    })
                    .then(function(r) { return r.json(); })
                    .then(function(data) {
                        if (data && data.token && window.AndroidIntegrityBridge) {
                            window.AndroidIntegrityBridge.onIntegrityResult(
                                data.token, Number(data.expiration || 0), dev, cid, window.__agyClientVersion || '', sid
                            );
                        } else if (window.AndroidIntegrityBridge) {
                            window.AndroidIntegrityBridge.onIntegrityError('Missing token field in integrity response');
                        }
                    })
                    .catch(function(err) {
                        if (window.AndroidIntegrityBridge) window.AndroidIntegrityBridge.onIntegrityError(String(err));
                    });
                };
            })();
        """.trimIndent()

        private fun shouldDropAssetUrl(lowerUrl: String): Boolean {
            if (lowerUrl.contains("gql.twitch.tv") ||
                lowerUrl.contains("passport.twitch.tv") ||
                lowerUrl.contains("kpsdk") ||
                lowerUrl.contains("149e9513-01fa-4fb0-aad4-566afd725d1b")
            ) {
                return false
            }
            if (lowerUrl.contains("usher.ttvnw.net") ||
                lowerUrl.contains("video-weaver") ||
                lowerUrl.contains("video-edge") ||
                lowerUrl.contains("static-cdn.jtvnw.net/previews") ||
                lowerUrl.contains("static-cdn.jtvnw.net/ttv-boxart")
            ) {
                return true
            }
            val clean = lowerUrl.substringBefore('?')
            return BLOCKED_EXTENSIONS.any { clean.endsWith(it) }
        }

        private fun generateHex(numBytes: Int): String {
            val bytes = ByteArray(numBytes)
            SecureRandom().nextBytes(bytes)
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
