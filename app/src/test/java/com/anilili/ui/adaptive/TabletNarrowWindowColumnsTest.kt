package com.anilili.ui.adaptive

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TabletNarrowWindowColumnsTest {
    private val tablet = AppDeviceProfile(AppFormFactor.TABLET, 900)

    @Test fun splitScreenFitsOneReadablePoster() {
        assertEquals(1, tablet.homePosterColumns(180.dp, 12.dp))
        assertEquals(1, tablet.homePosterColumns(0.dp, 12.dp))
    }

    @Test fun resizingRestoresColumnsAtActualCardBoundaries() {
        assertEquals(1, tablet.homePosterColumns(311.dp, 12.dp))
        assertEquals(2, tablet.homePosterColumns(312.dp, 12.dp))
        assertEquals(2, tablet.homePosterColumns(473.dp, 12.dp))
        assertEquals(3, tablet.homePosterColumns(474.dp, 12.dp))
        assertEquals(6, tablet.homePosterColumns(1600.dp, 12.dp))
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
