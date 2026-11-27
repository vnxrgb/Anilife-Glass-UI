package com.anilili.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TabletNarrowWindowColumnsTest {
    private val tablet = AppDeviceProfile(AppFormFactor.TABLET, 500)
    private val expandedTablet = AppDeviceProfile(AppFormFactor.TABLET, 900)

    @Test fun splitScreenFitsOneReadablePoster() {
        assertEquals(1, tablet.homePosterColumns(180.dp, 12.dp))
        assertEquals(1, tablet.homePosterColumns(0.dp, 12.dp))
    }

    @Test fun resizingRestoresColumnsAtActualCardBoundaries() {
        assertEquals(1, tablet.homePosterColumns(275.dp, 12.dp))
        assertEquals(2, tablet.homePosterColumns(276.dp, 12.dp))
        assertEquals(2, tablet.homePosterColumns(419.dp, 12.dp))
        assertEquals(3, tablet.homePosterColumns(420.dp, 12.dp))
        assertEquals(4, tablet.homePosterColumns(1600.dp, 12.dp))
        assertEquals(6, expandedTablet.homePosterColumns(1600.dp, 12.dp))
    }

    @Test fun expandedTabletUsesLargerPosterMinimum() {
        assertEquals(1, expandedTablet.homePosterColumns(311.dp, 12.dp))
        assertEquals(2, expandedTablet.homePosterColumns(312.dp, 12.dp))
        assertEquals(2, expandedTablet.homePosterColumns(473.dp, 12.dp))
        assertEquals(3, expandedTablet.homePosterColumns(474.dp, 12.dp))
    }

    @Test fun narrowTabletWindowKeepsTabletPolicy() {
        val profile = resolveAppDeviceProfile(
            uiModeType = android.content.res.Configuration.UI_MODE_TYPE_NORMAL,
            widthDp = 320,
            deviceSmallestWidthDp = 700,
        )
        assertEquals(AppFormFactor.TABLET, profile.formFactor)
        assertEquals(1, profile.homePosterColumns(180.dp, 12.dp))
        assertEquals(2, profile.homePosterColumns(312.dp, 12.dp))
    }

    @Test fun narrowTabletUsesPhoneSizedMarginsUntilWindowIsMedium() {
        val narrow = resolveAppDeviceProfile(
            uiModeType = android.content.res.Configuration.UI_MODE_TYPE_NORMAL,
            widthDp = 320,
            deviceSmallestWidthDp = 700,
        )
        assertEquals(AppFormFactor.TABLET, narrow.formFactor)
        assertEquals(16.dp, narrow.pagePadding)
        assertEquals(24.dp, narrow.copy(widthDp = 600).pagePadding)
        assertEquals(32.dp, narrow.copy(widthDp = 840).pagePadding)
        assertEquals(16.dp, AppDeviceProfile(AppFormFactor.PHONE, 390).pagePadding)
        assertEquals(48.dp, AppDeviceProfile(AppFormFactor.TV, 1920).pagePadding)
    }

    @Test fun narrowPhoneDoesNotBecomeTablet() {
        val profile = resolveAppDeviceProfile(
            uiModeType = android.content.res.Configuration.UI_MODE_TYPE_NORMAL,
            widthDp = 390,
            deviceSmallestWidthDp = 390,
        )
        assertEquals(AppFormFactor.PHONE, profile.formFactor)
        assertEquals(3, profile.homePosterColumns(180.dp, 12.dp))
    }

    @Test fun phoneAndTvPoliciesRemainUnchanged() {
        assertEquals(3, AppDeviceProfile(AppFormFactor.PHONE, 390).homePosterColumns(100.dp, 12.dp))
        assertEquals(3, AppDeviceProfile(AppFormFactor.PHONE, 390).homePosterColumns(212.dp, 12.dp))
        assertEquals(3, AppDeviceProfile(AppFormFactor.PHONE, 390).homePosterColumns(328.dp, 12.dp))
        assertEquals(6, AppDeviceProfile(AppFormFactor.TV, 1920).homePosterColumns(100.dp, 12.dp))
    }
}
