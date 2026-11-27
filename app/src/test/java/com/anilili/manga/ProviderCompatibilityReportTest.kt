package com.anilili.manga
import org.junit.Assert.*
import org.junit.Test
class ProviderCompatibilityReportTest {
    @Test fun includesHttpStatusWithoutSensitiveData() {
        val report = providerCompatibilityReport(mapOf("engine:EXAMPLE" to ProviderDiagnostic("ACCESS_RESTRICTED", "PAGE_DOWNLOAD", status = 403)))
        assertTrue(report.contains("HTTP status"))
        assertTrue(report.contains("403"))
    }
    @Test fun reportDoesNotContainUrlOrQuery() {
        val out = providerCompatibilityReport(mapOf("engine:EXAMPLE" to ProviderDiagnostic("HOST_SCOPE","PAGE_DOWNLOAD","https://example.org/page?token=private")))
        assertTrue(out.contains("HOST_SCOPE"))
        assertFalse(out.contains("private"))
        assertFalse(out.contains("https://"))
    }
}
