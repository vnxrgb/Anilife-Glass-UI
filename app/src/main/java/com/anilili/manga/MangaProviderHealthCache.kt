package com.anilili.manga

import android.content.SharedPreferences
import java.util.Locale

/** Revision-scoped provider health only. No tokens, pages or catalogue data. */
internal class MangaProviderHealthCache(private val prefs: SharedPreferences, private val now: () -> Long = System::currentTimeMillis) {
    data class Entry(val ready: Boolean, val remainingMs: Long, val reason: String)
    private fun key(id: String) = "health:" + id
    fun read(id: String, revision: String): Entry? = decode(prefs.getString(key(id), null), revision, now())
    fun record(id: String, revision: String, ready: Boolean, reason: String) {
        val clean = reason.uppercase(Locale.ROOT).replace(Regex("[^A-Z0-9_:/]"), "_").take(80)
        prefs.edit().putString(key(id), listOf(revision, now().toString(), if (ready && clean != "CATALOGUE") "READY" else clean).joinToString("|")).apply()
    }
    companion object {
        internal fun decode(value: String?, revision: String, now: Long): Entry? {
            val parts = value?.split('|') ?: return null
            if (parts.size != 3 || parts[0] != revision) return null
            val time = parts[1].toLongOrNull() ?: return null
            val elapsed = now - time
            if (elapsed < 0) return null
            val reason = parts[2].takeIf { it.matches(Regex("[A-Z0-9_:/]{1,80}")) } ?: return null
            val ttl = when {
                reason == "READY" || reason == "CATALOGUE" -> 604_800_000L
                reason.startsWith("RATE_LIMIT") || reason.startsWith("ACCESS_RESTRICTED") -> 3_600_000L
                else -> 900_000L
            }
            return if (elapsed < ttl) Entry(reason == "READY" || reason == "CATALOGUE", ttl - elapsed, reason) else null
        }
    }
}
