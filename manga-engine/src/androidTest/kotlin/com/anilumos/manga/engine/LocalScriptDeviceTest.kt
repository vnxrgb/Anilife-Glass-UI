package com.anilumos.manga.engine
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class LocalScriptDeviceTest {
    @Test fun localJavascriptAndDom() = runBlocking {
        LocalScriptRuntime.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        assertEquals("42", LocalScriptRuntime.evaluate("String(6 * 7)", 8000))
        assertEquals("fixture", LocalScriptRuntime.evaluate("document.body.textContent='fixture'; document.body.textContent", 8000))
        val snapshot = RemoteDocumentRuntime.sanitize("<h1 id='title'>Rendered</h1><script>document.body.textContent='unsafe'</script>")
        assertEquals("Rendered", LocalScriptRuntime.evaluate("document.getElementById('title').textContent", 8000, snapshot))
    }
}
