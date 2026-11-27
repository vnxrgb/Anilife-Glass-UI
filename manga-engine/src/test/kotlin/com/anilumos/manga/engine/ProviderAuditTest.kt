package com.anilumos.manga.engine

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import okhttp3.Request

class ProviderAuditTest {
    @Test fun inventoryEntireBundleWithoutNetwork() {
        val registry = ParserRegistry()
        val rows = ProviderAudit(registry).inventory()
        assertEquals(registry.descriptors().size, rows.size)
        assertEquals(rows.size, rows.map { it.descriptor.id }.distinct().size)
        assertTrue(rows.any { it.state == AuditState.INITIALIZED })
        val atsu = rows.single { it.descriptor.id == "ATSUMOE" }
        assertEquals(AuditState.INITIALIZED, atsu.state)
        assertEquals(true, atsu.search)
        val report = File("build/reports/provider-compatibility/inventory.tsv")
        report.parentFile.mkdirs()
        fun clean(value: String) = value.replace(Regex("[\\t\\r\\n]"), " ")
        report.writeText("id\ttitle\tlocale\tcategory\toffline_state\tsearch\tsorts\treason\tcombined_search\tmultiple_genres\texclude_genres\tlive_status\n" +
            rows.joinToString("\n") { row ->
                listOf(row.descriptor.id, row.descriptor.title, row.descriptor.locale, row.descriptor.category,
                    row.state.name, row.search?.toString().orEmpty(), row.sortOrders.joinToString(","), row.reason,
                    row.combinedSearch?.toString().orEmpty(), row.multipleGenres?.toString().orEmpty(),
                    row.excludeGenres?.toString().orEmpty(), "NOT_TESTED_BY_AUDIT").joinToString("\t", transform = ::clean)
            } + "\n")
        File(report.parentFile, "summary.txt").writeText(
            "Parser revision: ${registry.revision}\nTotal descriptors: ${rows.size}\n" +
                rows.groupingBy { it.state }.eachCount().entries.sortedBy { it.key.name }
                    .joinToString("\n") { "${it.key}: ${it.value}" } +
                "\nOffline initialization is not live certification.\n")
    }

    @Test fun visibilityRequiresReviewedAndFreshLiveSuccess() {
        var now = 100L
        val health = ProviderHealth { now }
        val rows = listOf(
            CapabilityRow(SourceDescriptor("a", "A", "en", "MANGA", false), AuditState.INITIALIZED),
            CapabilityRow(SourceDescriptor("b", "B", "en", "MANGA", false), AuditState.INITIALIZED),
            CapabilityRow(SourceDescriptor("c", "C", "en", "MANGA", true), AuditState.DECLARED_BROKEN)
        )
        health.record("a", ProviderState.READY)
        health.record("b", ProviderState.READY)
        health.record("c", ProviderState.READY)
        assertEquals(listOf("a"), visibleProviders(rows, setOf("a", "c"), health).map { it.id })
        now += 900_001L
        assertTrue(visibleProviders(rows, setOf("a", "c"), health).isEmpty())
    }

    @Test fun offlineSessionRejectsEvenAllowlistedHostBeforeNetwork() {
        val scope = SourceScope("fixture", setOf("offline.invalid"))
        SourceSession(scope, offline = true).use { session ->
            val error = runCatching {
                session.client.newCall(Request.Builder().url("https://offline.invalid").build()).execute().close()
            }.exceptionOrNull()
            assertEquals("Network disabled during offline audit", error?.message)
        }
    }
}
