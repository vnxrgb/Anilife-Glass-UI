package com.anilili.data.wasm

import kotlinx.coroutines.CancellationException

/** Ordered, failure-isolated resolution; cancellation is not a source failure. */
internal suspend fun <T> firstSuccessfulSource(ids: List<String>, resolve: suspend (String) -> List<T>): List<T> {
    for (id in ids.distinct()) {
        val result = try { resolve(id) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() }
        if (result.isNotEmpty()) return result
    }
    return emptyList()
}
