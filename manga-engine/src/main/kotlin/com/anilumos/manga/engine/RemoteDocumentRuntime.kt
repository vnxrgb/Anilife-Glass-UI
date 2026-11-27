package com.anilumos.manga.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.io.ByteArrayInputStream

/** Public, ordinary HTML only. WebView receives a sanitized inert snapshot, not an active
 * remote origin. No credentials, forms, page-owned scripts, service workers, challenges,
 * remote subresources, or request capture. Parser-owned local DOM scripts run separately. */
internal object RemoteDocumentRuntime {
    private const val MAX_HTML = 512 * 1024
    internal fun sanitize(html: String): String {
        require(html.length <= MAX_HTML)
        val doc = Jsoup.parse(html)
        doc.select("script,iframe,frame,frameset,object,embed,applet,link,base,meta,form,input,button,textarea,select,svg,math,video,audio,source,style,template,noscript").remove()
        for (element in doc.allElements) {
            val keys = element.attributes().map { it.key }
            for (key in keys) {
                val lower = key.lowercase(java.util.Locale.ROOT)
                if (lower.startsWith("on") || lower in setOf("src","srcset","href","xlink:href","action","formaction","poster","background","style","ping","srcdoc","data","codebase","manifest","autofocus"))
                    element.removeAttr(key)
            }
        }
        // Strip arbitrary custom elements from the live DOM while retaining their text/children.
        doc.select("*").filter { it.normalName().contains('-') }.forEach { it.unwrap() }
        return doc.body().html().takeIf { it.length <= MAX_HTML } ?: throw IOException("Document exceeds render budget")
    }
    internal fun readHtml(response: Response): String {
        if (!response.isSuccessful) throw IOException("Document HTTP response unavailable")
        // Do not attempt to process challenge, access-control or authentication pages.
        if (response.header("cf-mitigated") != null || response.header("WWW-Authenticate") != null)
            throw ProviderAccessException(403)
        val body = response.body
        val type = body.contentType()
        if (type?.type != "text" || type.subtype != "html") throw IOException("Not an HTML document")
        if (body.contentLength() > MAX_HTML) throw IOException("Document exceeds render budget")
        val input = body.source()
        input.request(MAX_HTML.toLong() + 1L)
        if (input.buffer.size > MAX_HTML) throw IOException("Document exceeds render budget")
        val bytes = input.readByteArray()
        val text = bytes.toString(type.charset(Charsets.UTF_8) ?: Charsets.UTF_8)
        if (text.contains("cf-chl-", true) || text.contains("challenge-platform", true) ||
            text.contains("captcha", true) || Jsoup.parse(text).select("input[type=password]").isNotEmpty())
            throw ProviderAccessException(403)
        return sanitize(text)
    }
    suspend fun evaluate(session: SourceSession, url: String, script: String, timeout: Long): String? {
        val document = withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url).header("Accept", "text/html").build()
            session.client.newCall(request).execute().use(::readHtml)
        }
        return LocalScriptRuntime.evaluate(script, timeout, document)
    }
}
