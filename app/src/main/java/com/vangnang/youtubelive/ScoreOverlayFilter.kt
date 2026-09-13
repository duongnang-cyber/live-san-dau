package com.vangnang.youtubelive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.SystemClock
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import com.pedro.encoder.utils.gl.GlUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One GL pass shared by preview and encoder. UI views/stream keys never enter this pass. */
class ScoreOverlayFilter(private val snapshot: () -> ScoreState) : BaseFilterRender() {
    private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 0f, 0f, 1f, -1f, 1f, 0f, -1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f)); position(0)
    }
    private val painter = ScorePainter()
    private var board: Bitmap? = null
    private var ticker: Bitmap? = null
    private var boardCanvas: Canvas? = null
    private var tickerCanvas: Canvas? = null
    private val textures = IntArray(2)
    private var program = 0
    private var position = -1
    private var uv = -1
    private var videoUniform = -1
    private var boardUniform = -1
    private var tickerUniform = -1
    private var showBoardUniform = -1
    private var showTickerUniform = -1
    private var last: ScoreState? = null
    private var lastBoard: ScoreState? = null
    private var boardAt = 0L
    private var lastSecond = -1L
    private var tickerAt = 0L
    private var tickerStart = 0L

    override fun initGlFilter(context: Context) {
        program = GlUtil.createProgram(GlUtil.getStringFromRaw(context, R.raw.score_vertex), GlUtil.getStringFromRaw(context, R.raw.score_fragment))
        check(program != 0) { "Cannot compile scoreboard shader" }
        position = GLES20.glGetAttribLocation(program, "aPosition")
        uv = GLES20.glGetAttribLocation(program, "aTextureCoord")
        videoUniform = GLES20.glGetUniformLocation(program, "uVideo")
        boardUniform = GLES20.glGetUniformLocation(program, "uBoard")
        tickerUniform = GLES20.glGetUniformLocation(program, "uTicker")
        showBoardUniform = GLES20.glGetUniformLocation(program, "uShowBoard")
        showTickerUniform = GLES20.glGetUniformLocation(program, "uShowTicker")
        board = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        ticker = Bitmap.createBitmap(1280, 56, Bitmap.Config.ARGB_8888)
        boardCanvas = Canvas(board!!); tickerCanvas = Canvas(ticker!!)
        GLES20.glGenTextures(2, textures, 0)
        listOf(board!!, ticker!!).forEachIndexed { index, bitmap ->
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[index])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        }
        last = null; lastBoard = null; boardAt = 0; lastSecond = -1; tickerAt = 0; tickerStart = SystemClock.elapsedRealtime()
    }
    override fun drawFilter() {
        val state = snapshot()
        val now = SystemClock.elapsedRealtime()
        val second = state.elapsed(now) / 1000
        // Drag events can arrive faster than video frames. Limit bitmap uploads, without losing the last move.
        if ((lastBoard != state || second != lastSecond) && (lastBoard == null || now - boardAt >= 32)) {
            painter.board(boardCanvas!!, state, now)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, board!!)
            lastSecond = second
            lastBoard = state; boardAt = now
        }
        val tickerChanged = last?.tickerText != state.tickerText || last?.tickerVisible != state.tickerVisible || last?.tickerLabel != state.tickerLabel || last?.tickerSpeed != state.tickerSpeed
        if (tickerChanged) tickerStart = now
        if (tickerChanged || (state.tickerVisible && state.tickerText.isNotBlank() && now - tickerAt >= 32)) {
            painter.ticker(tickerCanvas!!, state, now - tickerStart)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1])
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, ticker!!)
            tickerAt = now
        }
        last = state
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(showBoardUniform, if (state.visible) 1 else 0)
        GLES20.glUniform1i(showTickerUniform, if (state.tickerVisible && state.tickerText.isNotBlank()) 1 else 0)
        vertices.position(0)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(position)
        vertices.position(2)
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices)
        GLES20.glEnableVertexAttribArray(uv)
        bind(0, previousTexId, videoUniform); bind(1, textures[0], boardUniform); bind(2, textures[1], tickerUniform)
    }
    private fun bind(unit: Int, texture: Int, uniform: Int) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unit)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1i(uniform, unit)
    }
    override fun disableResources() {
        GLES20.glDisableVertexAttribArray(position); GLES20.glDisableVertexAttribArray(uv)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }
    override fun release() {
        GLES20.glDeleteTextures(2, textures, 0)
        GLES20.glDeleteProgram(program)
        boardCanvas = null; tickerCanvas = null
        board?.recycle(); ticker?.recycle(); board = null; ticker = null
    }
}
