package com.anilili.ui.easteregg

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Calendar
import java.util.Locale

/** Local-only, in-memory session state; no analytics, account data or network work. */
internal object HiddenSparks {
    data class Spark(val text: String, val sequence: Long, val createdAt: Long)
    val message = MutableStateFlow<Spark?>(null)
    private val counts = mutableMapOf<String, Int>()
    private val shown = mutableSetOf<String>()
    private var lastAutomatic = -120_000L
    private var sequence = 0L
    private var prefs: android.content.SharedPreferences? = null
    private var theme: String? = null
    private var started = false
    private var lastTap = 0L
    private var lastEndedKey = ""
    private var lastEndedAt = 0L
    private var backgroundTheme: String? = null
    private val searches = mapOf(
        "anilumos" to "That's me. Hi.",
        "one piece" to "The One Piece is real!",
        "naruto" to "Believe it!",
        "death note" to "Please don't write my name.",
        "dragon ball" to "Your search power is over 9000.",
        "bleach" to "Unfortunately, we don't sell detergent.",
        "jujutsu kaisen" to "Nah, I'd load.",
        "attack on titan" to "Dedicate your watchlist!",
        "demon slayer" to "Total Concentration: Browsing.",
        "pokemon" to "Gotta watch 'em all.",
        "solo leveling" to "Arise.",
        "chainsaw man" to "Sir, this is an anime app.",
        "hunter x hunter" to "Still waiting, aren't we?",
        "frieren" to "What's another ten years?",
        "gintama" to "Even this Easter egg is filler.",
        "jojo" to "Is that a JoJo reference?",
        "spy x family" to "Waku waku.",
        "mob psycho 100" to "Loading... 99%.",
        "fullmetal alchemist" to "Equivalent exchange: one search, one joke.",
        "haikyuu" to "The ball is in your watchlist.",
        "steins gate" to "El Psy Kongroo.",
        "evangelion" to "Get in the watchlist, Shinji.",
        "cowboy bebop" to "See you, space cowboy.",
        "saitama" to "One search is all it takes.",
        "one punch man" to "One search is all it takes."
    )

    fun start(context: Context) {
        if (started) return
        started = true
        prefs = context.applicationContext.getSharedPreferences("hidden_sparks", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val previous = prefs?.getLong("last_open", 0L) ?: 0L
        prefs?.edit()?.putLong("last_open", now)?.apply()
        val cal = Calendar.getInstance()
        when {
            previous > 0 && now - previous >= 30L * 86_400_000L ->
                say("return", "Welcome back, stranger. We kept the light on.", daily = true)
            cal.get(Calendar.HOUR_OF_DAY) == 11 && cal.get(Calendar.MINUTE) == 11 ->
                say("wish", "Make a wish.", daily = true)
            cal.get(Calendar.HOUR_OF_DAY) == 3 ->
                say("night", "Even the moon has gone to sleep. What's your excuse?", daily = true)
            cal.get(Calendar.MONTH) == Calendar.JANUARY && cal.get(Calendar.DAY_OF_MONTH) == 1 ->
                say("newyear", "A new year. Another unfinished watchlist.", daily = true)
        }
    }

    fun search(raw: String) {
        val key = raw.trim().lowercase(Locale.ROOT).replace("é", "e").replace("’", "'")
        searches[key]?.let { say("search:$key", it) }
    }

    fun hit(key: String, threshold: Int, text: String, daily: Boolean = false) {
        val n = ((counts[key] ?: 0) + 1).coerceAtMost(threshold)
        counts[key] = n
        if (n >= threshold && say(key, text, daily)) counts[key] = 0
    }

    fun episodeEnded(animeId: Int, episode: String, seriesComplete: Boolean, total: Int?) {
        if (!started) return
        val key = "$animeId:$episode"
        val now = SystemClock.elapsedRealtime()
        if (key == lastEndedKey && now - lastEndedAt < 15_000L) return
        lastEndedKey = key
        lastEndedAt = now
        val replayKey = "replay:$key"
        val times = (counts[replayKey] ?: 0) + 1
        counts[replayKey] = times
        if (times >= 3 && say(replayKey, "The ending isn't going to change.", daily = true)) counts[replayKey] = 0
        if (seriesComplete) {
            val n = (prefs?.getInt("finished_runs", 0) ?: 0) + 1
            prefs?.edit()?.putInt("finished_runs", n)?.apply()
            when {
                n % 100 == 0 -> say("milestone:$n", "You came for the anime. You stayed for the stories.")
                total != null && total >= 100 -> say("long-series:$animeId", "You survived an entire character arc.")
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY) == 0 -> say("midnight", "A story ends. A new day begins.", daily = true)
                else -> say("series-finished", "Every ending is a new beginning.")
            }
        }
    }

    fun backgroundChanged(value: String) {
        val previous = backgroundTheme
        backgroundTheme = value
        if (previous != null && previous != value) hit("background", 5, "Choose your side, young protagonist.")
    }

    fun themeChanged(value: String) {
        val old = theme
        theme = value
        if (old != null && old != value) hit("theme", 5, "This isn't a character customization screen.")
    }

    fun secretTap(key: String) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastTap > 2500) { counts.remove("logo"); counts.remove("version") }
        lastTap = now
        val n = (counts[key] ?: 0) + 1
        counts[key] = n
        when {
            key == "logo" && n == 7 -> show("Congratulations. You've unlocked absolutely nothing.")
            key == "logo" && n >= 20 -> { counts[key] = 0; show("Okay, now you're just bullying the logo.") }
            key == "version" && n >= 10 -> { counts[key] = 0; show("Powered by caffeine and questionable sleep schedules.") }
        }
    }

    fun say(key: String, text: String, daily: Boolean = false): Boolean {
        if (!started || key in shown) return false
        val now = SystemClock.elapsedRealtime()
        if (message.value?.let { now - it.createdAt > 30 * 60_000L } == true) message.value = null
        if (now - lastAutomatic < 120_000L || message.value != null) return false
        val wall = System.currentTimeMillis()
        if (daily && wall - (prefs?.getLong("last:$key", 0L) ?: 0L) < 86_400_000L) return false
        shown.add(key)
        lastAutomatic = now
        if (daily) prefs?.edit()?.putLong("last:$key", wall)?.apply()
        show(text)
        return true
    }

    private fun show(text: String) { message.value = Spark(text, ++sequence, SystemClock.elapsedRealtime()) }
    fun clear(id: Long) { if (message.value?.sequence == id) message.value = null }
}

@Composable
internal fun HiddenSparkText(modifier: Modifier = Modifier, enabled: Boolean = true) {
    val spark by HiddenSparks.message.collectAsState()
    val lastText = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    LaunchedEffect(spark?.sequence, enabled) {
        val current = spark ?: return@LaunchedEffect
        if (!enabled) return@LaunchedEffect
        if (SystemClock.elapsedRealtime() - current.createdAt > 30 * 60_000L) { HiddenSparks.clear(current.sequence); return@LaunchedEffect }
        lastText.value = current.text
        delay(5000)
        HiddenSparks.clear(current.sequence)
    }
    AnimatedVisibility(visible = spark != null && enabled, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Text(spark?.text ?: lastText.value, modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 24.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center)
    }
}
