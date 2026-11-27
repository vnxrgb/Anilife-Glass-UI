package com.anilumos.manga.engine

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/** Ephemeral, offline JavaScript/DOM computation for parser-supplied code.
 * No remote navigation, provider cookies, JS bridges, file access, challenge handling
 * or HTTP interception. Origin is a synthetic local document, never a provider origin. */
public object LocalScriptRuntime {
    @Volatile private var application: Context? = null
    private val gate = Mutex()
    public fun initialize(context: Context) { application = context.applicationContext }

    @SuppressLint("SetJavaScriptEnabled")
    internal suspend fun evaluate(script: String, timeoutMs: Long, bodyHtml: String = ""): String? {
        require(script.length <= 512 * 1024 && bodyHtml.length <= 512 * 1024)
        val context = application ?: throw ProviderFeatureException(UnsupportedFeature.BROWSER_EXECUTION)
        return gate.withLock {
            withContext(Dispatchers.Main.immediate) {
                val view = WebView(context)
                try {
                    view.settings.apply {
                        javaScriptEnabled = true
                        blockNetworkLoads = true
                        allowFileAccess = false
                        allowContentAccess = false
                        domStorageEnabled = false
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        safeBrowsingEnabled = true
                    }
                    withTimeout(timeoutMs.coerceIn(250L, 8_000L)) {
                        suspendCancellableCoroutine { continuation ->
                            var started = false
                            view.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                                    WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                                override fun onPageFinished(view: WebView, url: String) {
                                    if (started || !continuation.isActive) return
                                    started = true
                                    view.evaluateJavascript(script) { raw ->
                                        if (continuation.isActive) continuation.resume(decodeResult(raw))
                                    }
                                }
                            }
                            view.loadDataWithBaseURL(null, "<!doctype html><meta charset=utf-8><meta http-equiv=Content-Security-Policy content=\"default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval'; connect-src 'none'; form-action 'none'\"><body>$bodyHtml</body>", "text/html", "UTF-8", null)
                        }
                    }
                } finally {
                    view.stopLoading()
                    view.destroy()
                }
            }
        }
    }
    internal fun decodeResult(raw: String?): String? {
        if (raw == null || raw == "null" || raw.length > 1024 * 1024) return null
        return try { (JSONTokener(raw).nextValue() as? String) ?: raw }
            catch (_: Exception) { null }
    }
}
