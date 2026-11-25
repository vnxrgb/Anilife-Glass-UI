package com.anilili.data.recs

/** Weighted reciprocal ranks let independently ranked sources agree without comparing raw votes. */
internal object HybridRanking {
    fun rank(sources: List<Pair<List<Int>, Double>>, excluded: Set<Int>, limit: Int): List<Int> {
        if (limit <= 0) return emptyList()
        val scores = mutableMapOf<Int, Double>()
        sources.forEach { (ids, weight) ->
            if (!weight.isFinite() || weight <= 0) return@forEach
            ids.distinct().forEachIndexed { index, id ->
                if (id > 0 && id !in excluded) scores[id] = (scores[id] ?: 0.0) + weight / (12.0 + index)
            }
        }
        return scores.entries.sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value }.thenBy { it.key })
            .take(limit).map { it.key }
    }
}
