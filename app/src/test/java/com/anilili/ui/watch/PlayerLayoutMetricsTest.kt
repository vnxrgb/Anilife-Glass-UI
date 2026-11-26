package com.anilili.ui.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

class PlayerLayoutMetricsTest {
    @Test
    fun inlinePhoneHidesLongLabelsAndUsesCompactChrome() {
        val metrics = playerLayoutMetrics(360, 205)
        assertTrue(metrics.compactHeight)
        assertFalse(metrics.showPillLabels)
        assertEquals(48.dp, metrics.playButtonSize)
        assertEquals(8.dp, metrics.barTopPadding)
    }

    @Test
    fun landscapePhoneKeepsWideScreenTransport() {
        val metrics = playerLayoutMetrics(760, 360)
        assertFalse(metrics.compactHeight)
        assertFalse(metrics.showPillLabels)
        assertEquals(54.dp, metrics.playButtonSize)
    }

    @Test
    fun tabletWithVeryShortVideoHidesVerboseActionLabels() {
        val metrics = playerLayoutMetrics(1024, 250)
        assertTrue(metrics.compactHeight)
        assertFalse(metrics.showPillLabels)
    }

    @Test
    fun tabletAndTvSizedPlayersGetReadableLabelsWhenTall() {
        assertTrue(playerLayoutMetrics(1024, 576).showPillLabels)
        assertTrue(playerLayoutMetrics(1920, 1080).showPillLabels)
        assertEquals(74.dp, playerLayoutMetrics(1920, 1080).playButtonSize)
    }

    @Test
    fun wideButShortTabletPlayerUsesMediumTransportControls() {
        val metrics = playerLayoutMetrics(1200, 240)
        assertTrue(metrics.compactHeight)
        assertFalse(metrics.showPillLabels)
        assertEquals(54.dp, metrics.playButtonSize)
        assertEquals(8.dp, metrics.barTopPadding)
    }

    @Test
    fun tallTabletRestoresExpandedControlsAfterResize() {
        assertEquals(64.dp, playerLayoutMetrics(1200, 400).playButtonSize)
        assertEquals(54.dp, playerLayoutMetrics(1200, 299).playButtonSize)
    }

    @Test
    fun compactHeightBoundaryIsConsistent() {
        assertTrue(playerLayoutMetrics(650, 299).compactHeight)
        assertFalse(playerLayoutMetrics(650, 300).compactHeight)
    }
}
