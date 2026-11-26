package com.anilili.ui.watch

import com.anilili.data.model.Category
import com.anilili.data.model.SourceVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableServerPickerTest {
    private fun server(name: String, failed: Boolean = false, variant: SourceVariant = SourceVariant.SOFT_SUB) =
        WatchSourceOption(name, Category.SUB, true, 1, variant, failed)

    @Test fun playingServerStartsFirstAndRefreshDoesNotReorderExistingChoices() {
        val initial = stableServerPickerRows(emptyList(), listOf(server("b"), server("a"), server("c")), "a", SourceVariant.SOFT_SUB)
        assertEquals(listOf("a", "b", "c"), initial.map { it.provider })
        val updated = stableServerPickerRows(initial.map(::serverPickerKey),
            listOf(server("new"), server("c"), server("a"), server("b", failed = true)), "a", SourceVariant.SOFT_SUB)
        assertEquals(listOf("a", "b", "c", "new"), updated.map { it.provider })
        assertTrue(updated[1].recentlyFailed)
    }

    @Test fun returningChoiceKeepsItsPositionAndVariantsHaveDistinctKeys() {
        val original = listOf(server("a"), server("b"), server("c"))
        val keys = original.map(::serverPickerKey)
        assertEquals(listOf("a", "c"), stableServerPickerRows(keys, listOf(server("c"), server("a")), "a", SourceVariant.SOFT_SUB).map { it.provider })
        assertEquals(listOf("a", "b", "c"), stableServerPickerRows(keys, original.reversed(), "a", SourceVariant.SOFT_SUB).map { it.provider })
        assertEquals(2, listOf(server("a"), server("a", variant = SourceVariant.HARD_SUB)).map(::serverPickerKey).distinct().size)
    }
}
