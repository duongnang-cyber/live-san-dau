package com.vangnang.youtubelive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.Surface
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/** Applied AFTER the score overlay: replay already contains the historical board/ticker. */
class ReplayFilter(private val replay: ReplayEngine) : BaseFilterRender() {
    private val vertices = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f)); position(0)
    }
    private val textures = IntArray(3)
    private val transform = FloatArray(16)
    private val available = AtomicBoolean(false)
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var badge: Bitmap? = null
    private var transitionGraphic: Bitmap? = null
    private var program = 0
    private var pos = -1
    private var uv = -1
    private var videoUniform = -1
    private var replayUniform = -1
    private var labelUniform = -1
    private var matrixUniform = -1
    private var playingUniform = -1
    private var transitionUniform = -1
    private var styleUniform = -1
    private var enterTimeUniform = -1
    private var exitTimeUniform = -1
    override fun initGlFilter(context: Context) {
        program = GlUtil.createProgram(GlUtil.getStringFromRaw(context, R.raw.score_vertex), GlUtil.getStringFromRaw(context, R.raw.replay_fragment))
        check(program != 0)
        pos = GLES20.glGetAttribLocation(program, "aPosition")
        uv = GLES20.glGetAttribLocation(program, "aTextureCoord")
        videoUniform = GLES20.glGetUniformLocation(program, "uVideo")
        replayUniform = GLES20.glGetUniformLocation(program, "uReplay")
        labelUniform = GLES20.glGetUniformLocation(program, "uLabel")
        matrixUniform = GLES20.glGetUniformLocation(program, "uReplayMatrix")
        playingUniform = GLES20.glGetUniformLocation(program, "uPlaying")
        transitionUniform = GLES20.glGetUniformLocation(program, "uTransition")
        styleUniform = GLES20.glGetUniformLocation(program, "uStyle")
        enterTimeUniform = GLES20.glGetUniformLocation(program, "uEnterTime")
        exitTimeUniform = GLES20.glGetUniformLocation(program, "uExitTime")
        Matrix.setIdentityM(transform, 0)
        GLES20.glGenTextures(3, textures, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        textureParams(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        surfaceTexture = SurfaceTexture(textures[0]).also {
            it.setDefaultBufferSize(width, height)
            it.setOnFrameAvailableListener { available.set(true) }
        }
        surface = Surface(surfaceTexture!!)
        badge = Bitmap.createBitmap(200, 48, Bitmap.Config.ARGB_8888)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1])
        textureParams(GLES20.GL_TEXTURE_2D)
        drawBadge()
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, badge!!, 0)
        transitionGraphic = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        drawTransitionGraphic()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[2])
        textureParams(GLES20.GL_TEXTURE_2D)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, transitionGraphic!!, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        replay.attach(surface!!)
    }
    private fun textureParams(target: Int) {
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }
    private fun drawBadge() {
        val canvas = Canvas(badge!!)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xF0101B28.toInt() }
        canvas.drawRoundRect(0f, 0f, 200f, 48f, 4f, 4f, paint)
        paint.color = 0xFF9DDCCD.toInt(); canvas.drawRect(0f, 0f, 5f, 48f, paint)
        paint.color = Color.WHITE; paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = 24f; paint.textAlign = Paint.Align.CENTER
        canvas.drawText("PHÁT LẠI", 104f, 32f, paint)
    }
    /** Original court-line artwork, rendered offline from the shared vector scene. */
    private fun drawTransitionGraphic() {
        ScorePainter().render(Canvas(transitionGraphic!!), BroadcastDesign.replayBumper())
    }
    override fun drawFilter() {
        if (available.getAndSet(false)) {
            try {
                surfaceTexture?.let { it.updateTexImage(); it.getTransformMatrix(transform); replay.frameLatched(it.timestamp / 1000) }
            } catch (_: Exception) { replay.cancel() }
        }
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(playingUniform, if (replay.showing) 1 else 0)
        GLES20.glUniform1i(styleUniform, replay.transition.ordinal)
        val now = System.nanoTime()
        GLES20.glUniform1f(enterTimeUniform, if (replay.transitionStartedNs == 0L) 0f else (now - replay.transitionStartedNs) / 1_000_000_000f)
        GLES20.glUniform1f(exitTimeUniform, if (replay.transitionOutStartedNs == 0L) -1f else (now - replay.transitionOutStartedNs) / 1_000_000_000f)
        GLES20.glUniformMatrix4fv(matrixUniform, 1, false, transform, 0)
        vertices.position(0); GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, vertices); GLES20.glEnableVertexAttribArray(pos)
        vertices.position(2); GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices); GLES20.glEnableVertexAttribArray(uv)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId); GLES20.glUniform1i(videoUniform, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1); GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]); GLES20.glUniform1i(replayUniform, 1)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1]); GLES20.glUniform1i(labelUniform, 2)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[2]); GLES20.glUniform1i(transitionUniform, 3)
    }
    override fun disableResources() {
        GLES20.glDisableVertexAttribArray(pos); GLES20.glDisableVertexAttribArray(uv)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }
    override fun release() {
        replay.close()
        surfaceTexture?.setOnFrameAvailableListener(null)
        surface?.release(); surface = null
        surfaceTexture?.release(); surfaceTexture = null
        badge?.recycle(); badge = null
        transitionGraphic?.recycle(); transitionGraphic = null
        GLES20.glDeleteTextures(3, textures, 0)
        GLES20.glDeleteProgram(program)
    }
}
