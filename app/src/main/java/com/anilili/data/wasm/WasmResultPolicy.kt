package com.anilili.data.wasm

import org.json.JSONArray

/** Reject malformed results before they can reach a playback client. Not yet a media proxy. */
internal object WasmResultPolicy {
    fun validated(json: String, approved: ApprovedWasmProvider, dub: Boolean): JSONArray {
        if (json.length > 200000) return JSONArray()
        return runCatching {
            val source = JSONArray(json)
            require(source.length() <= 32)
            val accepted = JSONArray()
            for (i in 0 until source.length()) {
                val server = source.getJSONObject(i)
                if (server.getBoolean("dub") != dub) continue
                require(server.getString("name").length <= 128)
                val headers = server.getJSONArray("headers")
                require(headers.length() <= 16)
                for (h in 0 until headers.length()) {
                    val pair = headers.getJSONArray(h)
                    require(pair.length() == 2)
                    val name = pair.getString(0).lowercase(java.util.Locale.ROOT)
                    val value = pair.getString(1)
                    require(value.length <= 4096 && value.none { it == '\r' || it == '\n' })
                    require(name in setOf("referer", "origin", "user-agent", "accept"))
                    if (name == "referer" || name == "origin") require(WasmPolicy.allowsUrl(value, approved.hosts))
                }
                val streams = server.getJSONArray("streams")
                require(streams.length() in 1..32)
                for (s in 0 until streams.length()) {
                    val stream = streams.getJSONObject(s)
                    require(stream.getString("format") in setOf("hls", "mp4", "dash"))
                    require(WasmPolicy.allowsUrl(stream.getString("url"), approved.hosts))
                }
                val subtitles = server.optJSONArray("subtitles") ?: JSONArray()
                require(subtitles.length() <= 32)
                for (s in 0 until subtitles.length()) require(WasmPolicy.allowsUrl(subtitles.getJSONObject(s).getString("url"), approved.hosts))
                if (!server.isNull("thumbnails")) require(WasmPolicy.allowsUrl(server.getString("thumbnails"), approved.hosts))
                for (key in listOf("skip_intro", "skip_outro")) {
                    if (server.isNull(key)) continue
                    val range = server.getJSONArray(key)
                    require(range.length() == 2)
                    val start = range.getDouble(0)
                    val end = range.getDouble(1)
                    require(start.isFinite() && end.isFinite() && start >= 0 && end > start && end <= 86400)
                }
                accepted.put(server)
            }
            accepted
        }.getOrElse { JSONArray() }
    }
}
