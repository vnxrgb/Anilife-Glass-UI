package com.anilili.data.wasm

import org.junit.Assert.*
import org.junit.Test

class WasmEpisodeQueryTest {
    @Test fun validatesUnsignedIdsAndEpisode() {
        val good = WasmEpisodeQuery(123, null, listOf("Fixture"), 12.5, false)
        assertTrue(good.valid())
        assertFalse(good.copy(anilistId = -1).valid())
        assertFalse(good.copy(malId = 4294967296L).valid())
        assertFalse(good.copy(episode = Double.NaN).valid())
        assertFalse(good.copy(titles = emptyList()).valid())
        assertFalse(good.copy(titles = listOf("界".repeat(300))).valid())
    }
}
