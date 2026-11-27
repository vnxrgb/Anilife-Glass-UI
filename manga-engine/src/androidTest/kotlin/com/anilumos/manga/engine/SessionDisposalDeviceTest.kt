package com.anilumos.manga.engine

import android.os.Looper
import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SessionDisposalDeviceTest {
    @Test fun composeDisposalDispatchesCleanupAwayFromMainThread() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val finished = CountDownLatch(1)
        val actual = AtomicReference<Thread>()
        instrumentation.runOnMainSync {
            val old = StrictMode.getThreadPolicy()
            try {
                StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build())
                SessionDisposal.run {
                    actual.set(Thread.currentThread())
                    finished.countDown()
                }
            } finally { StrictMode.setThreadPolicy(old) }
        }
        assertTrue("cleanup must finish", finished.await(10, TimeUnit.SECONDS))
        assertNotSame(Looper.getMainLooper().thread, actual.get())
    }

    @Test fun mainThreadSessionCloseIsIdempotentAndRejectsFutureCalls() {
        val session = SourceSession(SourceScope("ATSUMOE", setOf("example.org")), offline = true)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            session.close()
            session.close()
        }
        // Interceptor rejects the call before any network operation, even if cleanup is queued.
        val result = runCatching {
            session.client.newCall(Request.Builder().url("https://example.org/").build()).execute().close()
        }.exceptionOrNull()
        assertTrue(result is IOException)
        assertEquals("Reader session closed", result?.message)
    }
}
