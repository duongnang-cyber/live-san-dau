package com.vangnang.youtubelive

import android.content.Context
import android.opengl.GLES20
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One five-tap GPU pass BEFORE graphics and replay. No CPU image copies or frame history. */
class SportsLookFilter(private val snapshot: () -> SportsLook,
    private val onVideoFrame: () -> Unit = {}) : BaseFilterRender() {
    private val vertices = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f)); position(0)
    }
    private var program = 0
    private var pos = -1
    private var uv = -1
    private var video = -1
    private var texel = -1
    private var enabled = -1
    private var amounts = -1
    private var detailLimit = -1

    override fun initGlFilter(context: Context) {
        program = GlUtil.createProgram(GlUtil.getStringFromRaw(context, R.raw.score_vertex),
            GlUtil.getStringFromRaw(context, R.raw.sports_look_fragment))
        check(program != 0) { "Cannot compile sports image shader" }
        pos = GLES20.glGetAttribLocation(program, "aPosition")
        uv = GLES20.glGetAttribLocation(program, "aTextureCoord")
        video = GLES20.glGetUniformLocation(program, "uVideo")
        texel = GLES20.glGetUniformLocation(program, "uTexel")
        enabled = GLES20.glGetUniformLocation(program, "uEnabled")
        amounts = GLES20.glGetUniformLocation(program, "uAmounts")
        detailLimit = GLES20.glGetUniformLocation(program, "uDetailLimit")
    }

    override fun drawFilter() {
        onVideoFrame()
        val look = snapshot() // One immutable, volatile-published preset per frame.
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(enabled, if (look == SportsLook.OFF) 0 else 1)
        GLES20.glUniform2f(texel, 1f / width.coerceAtLeast(1), 1f / height.coerceAtLeast(1))
        GLES20.glUniform4f(amounts, look.denoise, look.sharpen, look.contrast, look.saturation)
        GLES20.glUniform1f(detailLimit, look.detailLimit)
        vertices.position(0)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(pos)
        vertices.position(2)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(uv)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)
        GLES20.glUniform1i(video, 0)
    }

    override fun disableResources() {
        GLES20.glDisableVertexAttribArray(pos)
        GLES20.glDisableVertexAttribArray(uv)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    override fun release() { GLES20.glDeleteProgram(program); program = 0 }
}
