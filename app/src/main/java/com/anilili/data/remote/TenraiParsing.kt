package com.anilili.data.remote

import com.anilili.data.model.EpisodeClassification
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** False flags do not establish manga canon or mixed status. */
internal fun tenraiEpisodeKind(row: JsonObject): EpisodeClassification? {
    val recap = row["recap"]?.jsonPrimitive?.booleanOrNull
    val filler = row["filler"]?.jsonPrimitive?.booleanOrNull
    return when {
        recap == true -> EpisodeClassification.RECAP
        filler == true -> EpisodeClassification.FILLER
        else -> null
    }
}

internal fun tenraiRecommendationIds(root: JsonObject, seed: Int): List<Int> {
    val rows = root["data"] as? JsonArray ?: error("Tenrai recommendations data missing")
    return rows.mapNotNull { item ->
        val row = item as? JsonObject ?: return@mapNotNull null
        val entry = row["entry"] as? JsonObject ?: return@mapNotNull null
        val id = entry["mal_id"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 && it != seed } ?: return@mapNotNull null
        id to (row["votes"]?.jsonPrimitive?.intOrNull ?: 0)
    }.sortedByDescending { it.second }.map { it.first }.distinct().take(24)
}
