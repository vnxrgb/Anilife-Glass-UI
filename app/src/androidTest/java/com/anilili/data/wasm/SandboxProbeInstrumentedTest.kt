package com.anilili.data.wasm

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run on an ARM64 Android device with the experimental native library packaged. */
@RunWith(AndroidJUnit4::class)
class SandboxProbeInstrumentedTest {
    @Test fun isolatedRuntimeInstantiatesFixture() = runBlocking {
        assumeTrue(Build.SUPPORTED_ABIS.contains("arm64-v8a"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Isolated native runtime fixture did not execute", SandboxProbeClient(context).probe())
    }
}
