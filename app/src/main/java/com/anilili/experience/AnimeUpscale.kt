package com.anilili.experience

import android.content.Context
import android.opengl.GLES20
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

internal enum class AnimeUpscaleMode(val label: String, val factor: Float, val strength: Float) {
    OFF("Off", 1f, 0f), PERFORMANCE("Performance · DoG", 1.5f, 0.45f),
    BALANCED("Balanced · CNN S", 2f, 0f), HIGH("High quality · CNN M", 2f, 0f)
}

internal object AnimeUpscalePreferences {
    private var prefs: android.content.SharedPreferences? = null
    private val mutable = MutableStateFlow(AnimeUpscaleMode.OFF)
    val mode = mutable.asStateFlow()
    private val mutableStatus = MutableStateFlow("Off")
    val status = mutableStatus.asStateFlow()
    @Synchronized fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("experience_upscale", Context.MODE_PRIVATE)
        prefs = p
        mutable.value = if (Build.VERSION.SDK_INT < 26) AnimeUpscaleMode.OFF else
            AnimeUpscaleMode.entries.firstOrNull { it.name == p.getString("mode", "OFF") } ?: AnimeUpscaleMode.OFF
    }
    @Synchronized fun select(next: AnimeUpscaleMode) {
        val supported = if (Build.VERSION.SDK_INT >= 26) next else AnimeUpscaleMode.OFF
        prefs?.edit()?.putString("mode", supported.name)?.apply()
        mutable.value = supported
    }
    fun report(message: String) { mutableStatus.value = message }
    fun fallback() {
        val next = when (mutable.value) {
            AnimeUpscaleMode.HIGH -> AnimeUpscaleMode.BALANCED
            AnimeUpscaleMode.BALANCED -> AnimeUpscaleMode.PERFORMANCE
            else -> AnimeUpscaleMode.OFF
        }
        select(next)
        report("Reduced to " + next.label + " after processing trouble")
    }
    fun disableAfterFailure() {
        select(AnimeUpscaleMode.OFF)
        report("Disabled after a video processing error")
    }
}

internal fun upscaleSize(width: Int, height: Int, mode: AnimeUpscaleMode, maxTexture: Int, hdr: Boolean): Size {
    if (hdr || mode == AnimeUpscaleMode.OFF || width <= 0 || height <= 0 ||
        height >= 1080 || width >= 1920 || maxTexture <= 0) return Size(width, height)
    val factor = minOf(mode.factor, 1920f / width, 1080f / height, maxTexture.toFloat() / width, maxTexture.toFloat() / height)
    if (factor <= 1f) return Size(width, height)
    return Size((width * factor).roundToInt().coerceAtMost(maxTexture), (height * factor).roundToInt().coerceAtMost(maxTexture))
}

/**
 * Single-pass adaptation of Anime4K v3.2 Upscale DoG x2 (MIT, bloc97 2019–2021).
 * Uses a 5x5 separable Gaussian evaluated in one shader, local 3x3 luminance
 * bounds and clamped detail restoration. Not the upstream CNN model.
 * License: assets/licenses/anime4k-MIT.txt.
 */
@UnstableApi
internal class AnimeDogEffect(private val mode: AnimeUpscaleMode) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        try { AnimeDogProgram(mode, useHdr) }
        catch (e: Exception) { throw VideoFrameProcessingException(e) }
}

@UnstableApi
private class AnimeDogProgram(private val mode: AnimeUpscaleMode, private val hdr: Boolean) :
    BaseGlShaderProgram(hdr, 1) {
    private val program = GlProgram(VERTEX, FRAGMENT)
    private var width = 1
    private var height = 1
    private var strength = 0f
    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth
        height = inputHeight
        val max = IntArray(1)
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, max, 0)
        val output = upscaleSize(width, height, mode, max[0], hdr)
        strength = if (output.width > width || output.height > height) mode.strength else 0f
        AnimeUpscalePreferences.report(if (strength > 0f)
            "Processing: ${width}×${height} → ${output.width}×${output.height}" else "Bypass: HDR or resolution limit")
        return output
    }
    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setBufferAttribute("aPosition", floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f), 2)
            program.setSamplerTexIdUniform("uTexture", inputTexId, 0)
            program.setFloatsUniform("uPixel", floatArrayOf(1f / width, 1f / height))
            program.setFloatUniform("uStrength", strength)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) { throw VideoFrameProcessingException(e, presentationTimeUs) }
    }
    override fun release() {
        try { super.release() } finally {
            try { program.delete() } catch (e: Exception) { throw VideoFrameProcessingException(e) }
        }
    }
    companion object {
        private const val VERTEX = """
            attribute vec2 aPosition;
            varying vec2 vUv;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vUv = aPosition * 0.5 + 0.5;
            }
        """
        private const val FRAGMENT = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTexture;
            uniform vec2 uPixel;
            uniform float uStrength;
            float lum(vec3 rgb) { return dot(rgb, vec3(0.299, 0.587, 0.114)); }
            float weight(int i) {
                if (i == 0) return 0.38774;
                if (i == -1 || i == 1) return 0.24477;
                return 0.06136;
            }
            void main() {
                vec4 original = texture2D(uTexture, vUv);
                if (uStrength <= 0.0) { gl_FragColor = original; return; }
                float center = lum(original.rgb);
                float gaussian = 0.0;
                float lo = center;
                float hi = center;
                for (int y = -2; y <= 2; y++) {
                    for (int x = -2; x <= 2; x++) {
                        vec2 uv = clamp(vUv + vec2(float(x), float(y)) * uPixel, uPixel * 0.5, vec2(1.0) - uPixel * 0.5);
                        float sampleY = lum(texture2D(uTexture, uv).rgb);
                        gaussian += sampleY * weight(x) * weight(y);
                        if (x >= -1 && x <= 1 && y >= -1 && y <= 1) {
                            lo = min(lo, sampleY);
                            hi = max(hi, sampleY);
                        }
                    }
                }
                float correction = clamp(center + (center - gaussian) * uStrength, lo, hi) - center;
                gl_FragColor = vec4(clamp(original.rgb + vec3(correction), 0.0, 1.0), original.a);
            }
        """
    }
}

@Composable
internal fun AnimeUpscaleControls() {
    val context = LocalContext.current
    LaunchedEffect(context) { AnimeUpscalePreferences.init(context) }
    val mode by AnimeUpscalePreferences.mode.collectAsState()
    val status by AnimeUpscalePreferences.status.collectAsState()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Anime4K DoG / CNN · Experimental local GPU enhancement", style = MaterialTheme.typography.labelMedium)
        AnimeUpscaleMode.entries.forEach { option ->
            FilterChip(selected = mode == option, enabled = Build.VERSION.SDK_INT >= 26 || option == AnimeUpscaleMode.OFF,
                onClick = { AnimeUpscalePreferences.select(option) }, label = { Text(option.label) })
        }
        Text(if (Build.VERSION.SDK_INT < 26) "Requires Android 8 or later" else status, style = MaterialTheme.typography.labelSmall)
        Text("Up to 1080p. SDR only. Not available for external players or casting. May increase battery use.", style = MaterialTheme.typography.labelSmall)
    }
}
