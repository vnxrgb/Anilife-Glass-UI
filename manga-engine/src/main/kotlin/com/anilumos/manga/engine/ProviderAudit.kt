package com.anilumos.manga.engine

import kotlinx.coroutines.CancellationException
import java.io.IOException

internal enum class AuditState { DECLARED_BROKEN, CATEGORY_UNSUPPORTED, INITIALIZED, INITIALIZATION_FAILED }
internal data class CapabilityRow(
    val descriptor: SourceDescriptor,
    val state: AuditState,
    val search: Boolean? = null,
    val sortOrders: List<String> = emptyList(),
    val reason: String = "",
)

/** Static capability inventory. Initialization never grants network permission. */
internal class ProviderAudit(private val registry: ParserRegistry) {
    fun inventory(): List<CapabilityRow> = registry.descriptors().sortedBy { it.id }.map { descriptor ->
        when {
            descriptor.markedBroken -> CapabilityRow(descriptor, AuditState.DECLARED_BROKEN)
            descriptor.category !in ParserRegistry.supportedCategories -> CapabilityRow(descriptor, AuditState.CATEGORY_UNSUPPORTED)
            else -> inspect(descriptor)
        }
    }

    private fun inspect(descriptor: SourceDescriptor): CapabilityRow = try {
        // No real hostname is allowed, and the session explicitly rejects every network call.
        val scope = SourceScope(descriptor.id, setOf("offline.invalid"))
        SourceSession(scope, offline = true).use { session ->
            val parser = registry.bundle.newParser(registry.requireSource(scope), AndroidParserContext(registry.bundle, session))
            CapabilityRow(descriptor, AuditState.INITIALIZED,
                parser.filterCapabilities.isSearchSupported,
                parser.availableSortOrders.map { it.name }.sorted())
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CapabilityRow(descriptor, AuditState.INITIALIZATION_FAILED, reason = e.javaClass.simpleName)
    } catch (e: LinkageError) {
        CapabilityRow(descriptor, AuditState.INITIALIZATION_FAILED, reason = e.javaClass.simpleName)
    }
}

/** A parser's declaration or offline initialization is never sufficient for visibility. */
internal fun visibleProviders(rows: List<CapabilityRow>, reviewedIds: Set<String>, health: ProviderHealth): List<SourceDescriptor> =
    rows.filter { it.state == AuditState.INITIALIZED && it.descriptor.id in reviewedIds && health.visible(it.descriptor.id) }
        .map { it.descriptor }
