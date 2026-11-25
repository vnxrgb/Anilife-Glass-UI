package com.anilili.ui.detail

import android.content.Context
import com.anilili.data.AppGraph
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import okhttp3.Request
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File
import java.util.concurrent.TimeUnit

internal data class CuratedStep(val id: Int, val optional: Boolean = false)
internal data class CuratedOrder(val name: String, val source: String, val steps: List<CuratedStep>)

internal object CuratedWatchOrders {
    const val COMMUNITY = "r/anime community via Bas1874"
    const val METADATA = "Anime Metadata DB (ODbL / DbCL)"
    private const val communityUrl = "https://raw.githubusercontent.com/Bas1874/Anime-Watch-Order-Api/main/data/watch_order_api.json"
    private const val metadataBase = "https://raw.githubusercontent.com/michael-freling/anime-metadata-db/main/dataset/data/"
    private const val limit = 8L * 1024 * 1024
    private val client by lazy { AppGraph.httpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build() }

    private fun cached(context: Context, name: String, url: String): String? {
        val directory = File(context.cacheDir, "franchise-orders").apply { mkdirs() }
        val file = File(directory, name)
        fun old(): String? = file.takeIf { it.isFile && it.length() in 1L..limit }?.let { runCatching { it.readText() }.getOrNull() }
        if (file.isFile && System.currentTimeMillis() - file.lastModified() < 24 * 60 * 60 * 1000L) return old()
        return try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return old()
                val source = response.body?.source() ?: return old()
                if (source.request(limit + 1)) return old()
                val text = source.readUtf8()
                if (text.isBlank()) return old()
                val tmp = File.createTempFile("order-", ".tmp", directory)
                try {
                    tmp.writeText(text)
                    if (!tmp.renameTo(file)) tmp.delete()
                } finally { tmp.delete() }
                text
            }
        } catch (e: Exception) { old() }
    }

    private fun attempt(block: () -> List<CuratedOrder>): List<CuratedOrder> =
        try { block() } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }

    suspend fun load(context: Context, ids: Set<Int>): List<CuratedOrder> = withContext(Dispatchers.IO) {
        coroutineScope {
            val community = async { attempt { cached(context, "community.json", communityUrl)?.let { parseCommunity(it, ids) }.orEmpty() } }
            val metadata = async { attempt {
                val index = cached(context, "metadata-index.tsv", metadataBase + "index.tsv") ?: return@attempt emptyList()
                val files = metadataFiles(index, ids)
                files.take(4).flatMap { file ->
                    cached(context, "metadata-" + file, metadataBase + "series/" + file)
                        ?.let { parseMetadata(it, ids) }.orEmpty()
                }
            } }
            (community.await() + metadata.await()).distinctBy { it.source to (it.name to it.steps) }
        }
    }

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonElement?.arr() = (this as? JsonArray).orEmpty()
    private fun JsonElement?.str() = (this as? JsonPrimitive)?.contentOrNull

    internal fun parseCommunity(text: String, ids: Set<Int>): List<CuratedOrder> {
        val franchises = Json.parseToJsonElement(text).obj()?.get("data").arr()
        return franchises.flatMap { franchise ->
            val orders = franchise.obj()?.get("watch_orders").arr().mapNotNull { value ->
                val order = value.obj() ?: return@mapNotNull null
                val name = order["name"].str()?.take(100)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val steps = order["steps"].arr().mapNotNull { step ->
                    val item = step.obj() ?: return@mapNotNull null
                    val id = item["media"].obj()?.get("id").str()?.toIntOrNull()?.takeIf { it > 0 }
                        ?: return@mapNotNull null
                    CuratedStep(id, item["is_optional"].str() == "true")
                }.distinctBy { it.id }
                CuratedOrder(name, COMMUNITY, steps).takeIf { steps.size >= 2 }
            }
            if (orders.any { order -> order.steps.any { it.id in ids } }) orders else emptyList()
        }
    }

    internal fun metadataFiles(text: String, ids: Set<Int>): List<String> {
        if (!text.startsWith("!anime-metadata-db-index\t1")) return emptyList()
        var section = ""
        var columns = emptyList<String>()
        val series = mutableListOf<String>()
        val matches = linkedSetOf<String>()
        text.lineSequence().forEach { line ->
            val cells = line.split('\t')
            if (line.startsWith("!")) {
                section = cells.first().removePrefix("!")
                columns = cells.drop(1)
            } else if (line.isNotBlank()) {
                fun field(name: String) = cells.getOrNull(columns.indexOf(name))
                when (section) {
                    "series" -> series.add(field("file").orEmpty())
                    "works" -> if (field("anilist")?.toIntOrNull()?.let { it in ids } == true) {
                        val row = field("series")?.toIntOrNull()?.minus(1) ?: -1
                        series.getOrNull(row)?.takeIf { it.matches(Regex("[a-zA-Z0-9_-]+\\.yaml")) }?.let(matches::add)
                    }
                }
            }
        }
        return matches.toList()
    }

    private fun Any?.map() = this as? Map<*, *>
    private fun Any?.list() = (this as? List<*>).orEmpty()
    private fun Map<*, *>.mediaId(): Int? = (get("externalIds").map()?.get("anilistId") as? Number)?.toInt()?.takeIf { it > 0 }

    internal fun parseMetadata(text: String, ids: Set<Int>): List<CuratedOrder> {
        val options = LoaderOptions().apply { maxAliasesForCollections = 0; codePointLimit = 8 * 1024 * 1024 }
        val root = Yaml(SafeConstructor(options)).load<Any>(text).map() ?: return emptyList()
        val franchise = root["franchise"].map() ?: return emptyList()
        val references = mutableMapOf<String, List<CuratedStep>>()
        franchise["series"].list().forEach { value ->
            val series = value.map() ?: return@forEach
            val seriesId = series["id"] as? String ?: return@forEach
            val works = listOf("seasons", "movies", "specials").flatMap { kind ->
                series[kind].list().mapNotNull { it.map() }
            }
            val sorted = works.sortedWith(compareBy<Map<*, *>>(
                { (it["releaseYear"] as? Number)?.toInt() ?: Int.MAX_VALUE },
                { when (it["releaseSeason"]) { "WINTER" -> 1; "SPRING" -> 2; "SUMMER" -> 3; "FALL" -> 4; else -> 5 } },
                { (it["number"] as? Number)?.toInt() ?: Int.MAX_VALUE },
                { (it["part"] as? Number)?.toInt() ?: 0 }
            ))
            references[seriesId] = sorted.mapNotNull { work ->
                val id = work.mediaId() ?: return@mapNotNull null
                val step = CuratedStep(id, work["alternateCutOf"] != null)
                (work["id"] as? String)?.let { references[it] = listOf(step) }
                step
            }
        }
        if (references.values.flatten().none { it.id in ids }) return emptyList()
        return franchise["watchOrders"].list().mapNotNull { value ->
            val order = value.map() ?: return@mapNotNull null
            val name = (order["name"] as? String)?.take(100)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val refs = order["entries"].list().map { it.map()?.get("ref") as? String }
            if (refs.any { it == null || it !in references }) return@mapNotNull null
            val steps = refs.filterNotNull().flatMap { references[it].orEmpty() }.distinctBy { it.id }
            CuratedOrder(name, METADATA, steps).takeIf { steps.size >= 2 }
        }
    }
}
