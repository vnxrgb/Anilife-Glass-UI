package com.anilili.experience

import android.content.Context
import android.opengl.GLES30
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/** Original MIT Anime4K v3.2 CNN weights, translated from mpv hook syntax. */
@UnstableApi
internal class AnimeCnnEffect(private val mode: AnimeUpscaleMode) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        try { AnimeCnnProgram(context, mode, useHdr) }
        catch (e: Exception) { throw VideoFrameProcessingException(e) }
}

internal data class CnnPass(val binds: List<String>, val output: String, val body: String)
internal fun parseCnnPasses(source: String): List<CnnPass> =
    source.split("//!DESC ").drop(1).map { section ->
        val lines = section.lines()
        val binds = lines.filter { it.startsWith("//!BIND ") }.map { it.removePrefix("//!BIND ").trim() }
        val output = lines.first { it.startsWith("//!SAVE ") }.removePrefix("//!SAVE ").trim()
        require(binds.isNotEmpty() && binds.size <= 7)
        val body = lines.drop(1).filterNot { it.startsWith("//!") }.joinToString("\n")
        require(body.contains("vec4 hook()"))
        CnnPass(binds, output, body)
    }

@UnstableApi
private class AnimeCnnProgram(context: Context, private val mode: AnimeUpscaleMode, private val hdr: Boolean) :
    BaseGlShaderProgram(hdr, 1) {
    private data class Target(val texture: Int, val fbo: Int)
    private val passes = if (hdr) emptyList() else context.assets.open("anime4k/" +
        (if (mode == AnimeUpscaleMode.HIGH) "M" else "S") + ".glsl").bufferedReader().use { parseCnnPasses(it.readText()) }
    private val programs = mutableListOf<GlProgram>()
    private val targets = mutableMapOf<String, Target>()
    private var copy: GlProgram? = null
    private var width = 1
    private var height = 1
    private var output = Size(1, 1)
    private var active = false
    private val vertices = floatArrayOf(-1f,-1f, 1f,-1f, -1f,1f, 1f,1f)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        try {
            clearTargets()
            width = inputWidth
            height = inputHeight
            val max = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, max, 0)
            output = upscaleSize(width, height, mode, max[0], hdr)
            active = !hdr && output.width > width && output.height > height &&
                width * 2 <= max[0] && height * 2 <= max[0]
            if (copy == null) copy = GlProgram(VERTEX, COPY)
            if (!active) {
                output = Size(width, height)
                AnimeUpscalePreferences.report("CNN bypass: HDR or resolution limit")
                return output
            }
            if (programs.isEmpty()) {
                require(passes.size == if (mode == AnimeUpscaleMode.HIGH) 9 else 5)
                require(passes.last().output == "MAIN")
                passes.forEach { programs.add(GlProgram(VERTEX, fragment(it))) }
            }
            val previous = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, previous, 0)
            try {
                passes.forEach { pass ->
                    val final = pass.output == "MAIN"
                    targets[pass.output] = target(if (final) width * 2 else width, if (final) height * 2 else height)
                }
            } finally { GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, previous[0]) }
            AnimeUpscalePreferences.report("CNN " + (if (mode == AnimeUpscaleMode.HIGH) "M" else "S") +
                ": " + width + "×" + height + " → " + output.width + "×" + output.height)
            return output
        } catch (e: Exception) {
            clearTargets()
            programs.forEach { runCatching { it.delete() } }
            programs.clear()
            throw VideoFrameProcessingException(e)
        }
    }

    // Signed half-float intermediates are mandatory: normalized RGBA8 clips negative activations.
    private fun target(w: Int, h: Int): Target {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val texture = ids[0]
        var fbo = 0
        try {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, w, h, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
            GLES30.glGenFramebuffers(1, ids, 0)
            fbo = ids[0]
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)
            check(GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE) { "Half-float render target unavailable" }
            GlUtil.checkGlError()
            return Target(texture, fbo)
        } catch (e: Exception) {
            if (fbo != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
            throw e
        }
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val previous = IntArray(1)
        val viewport = IntArray(4)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, previous, 0)
        GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, viewport, 0)
        val blend = GLES30.glIsEnabled(GLES30.GL_BLEND)
        try {
            GLES30.glDisable(GLES30.GL_BLEND)
            if (active) {
                passes.forEachIndexed { index, pass ->
                    val target = targets.getValue(pass.output)
                    val final = pass.output == "MAIN"
                    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, target.fbo)
                    GLES30.glViewport(0, 0, if (final) width * 2 else width, if (final) height * 2 else height)
                    val shader = programs[index]
                    shader.use()
                    shader.setBufferAttribute("aPosition", vertices, 2)
                    shader.setFloatsUniformIfPresent("uSize", floatArrayOf(width.toFloat(), height.toFloat()))
                    pass.binds.forEachIndexed { unit, name ->
                        shader.setSamplerTexIdUniform("tex_" + name, if (name == "MAIN") inputTexId else targets.getValue(name).texture, unit)
                    }
                    shader.bindAttributesAndUniforms()
                    GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
                    GlUtil.checkGlError()
                }
            }
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, previous[0])
            GLES30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            val shader = checkNotNull(copy)
            shader.use()
            shader.setBufferAttribute("aPosition", vertices, 2)
            shader.setSamplerTexIdUniform("uTexture", if (active) targets.getValue("MAIN").texture else inputTexId, 0)
            shader.bindAttributesAndUniforms()
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: Exception) { throw VideoFrameProcessingException(e, presentationTimeUs) }
        finally {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, previous[0])
            GLES30.glViewport(viewport[0], viewport[1], viewport[2], viewport[3])
            if (blend) GLES30.glEnable(GLES30.GL_BLEND)
        }
    }

    private fun clearTargets() {
        targets.values.forEach {
            GLES30.glDeleteFramebuffers(1, intArrayOf(it.fbo), 0)
            GLES30.glDeleteTextures(1, intArrayOf(it.texture), 0)
        }
        targets.clear()
    }
    override fun release() {
        try { clearTargets(); programs.forEach { runCatching { it.delete() } }; programs.clear(); runCatching { copy?.delete() } }
        finally { super.release() }
    }

    private fun fragment(pass: CnnPass): String = buildString {
        append("#version 300 es\nprecision highp float;\nin vec2 vUv;\nout vec4 fragColor;\nuniform vec2 uSize;\n")
        pass.binds.forEach { name ->
            append("uniform sampler2D tex_" + name + ";\n")
            append("#define " + name + "_pos vUv\n")
            append("#define " + name + "_size uSize\n")
            append("#define " + name + "_pt (1.0 / uSize)\n")
            append("#define " + name + "_tex(p) texture(tex_" + name + ", (p))\n")
            append("#define " + name + "_texOff(p) texture(tex_" + name + ", vUv + (p) / uSize)\n")
        }
        append(pass.body)
        if (pass.output == "MAIN") append("\nvoid main(){ vec4 c = hook(); fragColor = vec4(clamp(c.rgb,0.0,1.0),1.0); }\n")
        else append("\nvoid main(){ fragColor = hook(); }\n")
    }

    companion object {
        private const val VERTEX = """#version 300 es
in vec2 aPosition;
out vec2 vUv;
void main(){ gl_Position=vec4(aPosition,0.0,1.0); vUv=aPosition*0.5+0.5; }
"""
        private const val COPY = """#version 300 es
precision highp float;
in vec2 vUv;
out vec4 fragColor;
uniform sampler2D uTexture;
void main(){ fragColor=texture(uTexture,vUv); }
"""
    }
}
