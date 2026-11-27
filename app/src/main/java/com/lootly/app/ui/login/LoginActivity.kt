package com.lootly.app.ui.login

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.lootly.app.TwitchMinerApp
import com.lootly.app.bridge.WebViewIntegrityBridge
import com.lootly.app.data.auth.TwitchAccountRepository
import com.lootly.app.service.MinerForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var status: TextView
    private var capturing = false
    private var handledToken: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as TwitchMinerApp

        val container = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
        }

        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                8,
                Gravity.TOP
            )
            max = 100
        }

        status = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            setBackgroundColor(Color.parseColor("#CC000000"))
            setTextColor(Color.WHITE)
            setPadding(24, 16, 24, 16)
            visibility = View.GONE
        }

        container.addView(webView)
        container.addView(progressBar)
        container.addView(status)
        setContentView(container)

        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+\\.\\d+\\s"), "")
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                CookieManager.getInstance().flush()
                inspectCookies(app)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    show("Load failed: ${error?.description}")
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                return !(url.startsWith("http://") || url.startsWith("https://"))
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        lifecycleScope.launch {
            while (isActive && !capturing) {
                inspectCookies(app)
                delay(1_000L)
            }
        }

        webView.loadUrl("https://www.twitch.tv/login")
    }

    private fun show(msg: String) {
        status.text = msg
        status.visibility = View.VISIBLE
    }

    private fun inspectCookies(app: TwitchMinerApp) {
        if (capturing) return
        val cm = CookieManager.getInstance()
        val combined = listOfNotNull(
            cm.getCookie(WebViewIntegrityBridge.TWITCH_ORIGIN)?.takeIf { it.isNotBlank() },
            cm.getCookie("https://m.twitch.tv")?.takeIf { it.isNotBlank() }
        ).joinToString("; ")

        val parsed = TwitchAccountRepository.parseCookieString(combined)
        val token = parsed["auth-token"]?.trim().orEmpty()
        if (token.isBlank() || token == handledToken) return

        handledToken = token
        capturing = true
        show("Verifying Twitch account…")

        lifecycleScope.launch {
            val result = app.accountRepository.buildSessionFromCookies(combined, parsed)
            result.onSuccess { session ->
                app.sessionStore.saveSession(session)
                MinerForegroundService.start(applicationContext)
                setResult(RESULT_OK)
                finish()
            }.onFailure { err ->
                capturing = false
                handledToken = null
                show("Verification failed: ${err.message ?: "retry login"}")
            }
        }
    }

    override fun onDestroy() {
        runCatching {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }
}
