package com.anilili.manga
import org.junit.Assert.*
import org.junit.Test
class ProviderCompatibilityReportTest {
    @Test fun reportDoesNotContainUrlOrQuery() {
        val out = providerCompatibilityReport(mapOf("engine:EXAMPLE" to ProviderDiagnostic("HOST_SCOPE","PAGE_DOWNLOAD","https://example.org/page?token=private")))
        assertTrue(out.contains("HOST_SCOPE"))
        assertFalse(out.contains("private"))
        assertFalse(out.contains("https://"))
    }
}
