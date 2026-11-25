package com.anilili.data.wasm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class WasmFallbackTest {
    @Test fun failureDoesNotPreventNextApprovedSource() = runBlocking {
        val attempted = mutableListOf<String>()
        val result = firstSuccessfulSource(listOf("broken", "empty", "valid", "unused")) { id ->
            attempted.add(id)
            when (id) {
                "broken" -> error("failed")
                "valid" -> listOf("stream")
                else -> emptyList()
            }
        }
        assertEquals(listOf("broken", "empty", "valid"), attempted)
        assertEquals(listOf("stream"), result)
    }

    @Test fun emptyResultAllowsNativeFallback() = runBlocking {
        assertTrue(firstSuccessfulSource<String>(listOf("empty")) { emptyList() }.isEmpty())
    }

    @Test fun cancellationIsNeverTreatedAsSourceFailure() = runBlocking {
        try {
            firstSuccessfulSource<String>(listOf("cancelled", "never")) {
                throw CancellationException("cancelled")
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            // expected
        }
    }
}
