package com.vangnang.youtubelive

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.view.Surface
import java.nio.ByteBuffer

/** One temporary decoder; the existing camera, microphone and network encoder keep running. */
class ReplayEngine(byteLimit: Int, private val notify: (String) -> Unit) {
    private data class Format(val width: Int, val height: Int, val csd: Map<String, ByteArray>)
    private val ring = ReplayBuffer(byteLimit)
    private val lock = Object()
    private var format: Format? = null
    private var surface: Surface? = null
    private var worker: Thread? = null
    private var session = 0L
    private var closed = false
    private var captureAfterMs = 0L
    private var expectedPtsUs = Long.MIN_VALUE
    private var expectedSession = -1L
    private var frameAck = false
    @Volatile var showing = false
        private set
    @Volatile var speed = 0.5
        private set
    @Volatile var transition = ReplayTransition.SPORTS
        private set
    @Volatile var transitionStartedNs = 0L
        private set
    @Volatile var transitionOutStartedNs = 0L
        private set
    val busy: Boolean get() = synchronized(lock) { worker != null }
    val availableSeconds: Double get() = ring.availableUs() / 1_000_000.0

    fun attach(surface: Surface) = synchronized(lock) { if (!closed) this.surface = surface }
    fun setFormat(value: MediaFormat) {
        try {
            val csd = (0..2).mapNotNull { n ->
                val key = "csd-$n"
                value.getByteBuffer(key)?.duplicate()?.let { b -> key to ByteArray(b.remaining()).also { b.get(it) } }
            }.toMap()
            synchronized(lock) {
                if (closed) return
                format = Format(value.getInteger(MediaFormat.KEY_WIDTH), value.getInteger(MediaFormat.KEY_HEIGHT), csd)
                ring.clear()
            }
        } catch (_: Exception) { synchronized(lock) { format = null; ring.clear() } }
    }
    fun resetFormat() { cancel(); synchronized(lock) { format = null; ring.clear() } }
    fun capture(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        if (info.size <= 0 || info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0 || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
        synchronized(lock) {
            if (closed || worker != null || format == null || SystemClock.elapsedRealtime() < captureAfterMs) return
            try {
                val view = buffer.duplicate()
                if (info.offset < 0 || info.size > view.capacity() - info.offset) return
                view.clear(); view.position(info.offset); view.limit(info.offset + info.size)
                val bytes = ByteArray(info.size); view.get(bytes)
                ring.add(ReplayPacket(bytes, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0))
            } catch (_: OutOfMemoryError) { ring.clear(); captureAfterMs = SystemClock.elapsedRealtime() + 3000 }
              catch (_: Exception) { ring.clear() }
        }
    }
    /** Returns an operator-facing error, or null when loading has started. */
    fun play(seconds: Int, requestedSpeed: Double, requestedTransition: ReplayTransition): String? {
        synchronized(lock) {
            if (closed) return "Replay đã đóng. Hãy bắt đầu live lại."
            if (worker != null) return "Đang phát lại hoặc trở về LIVE."
            if (seconds !in listOf(3, 5, 8)) return "Chọn đoạn replay 3, 5 hoặc 8 giây."
            if (requestedSpeed !in listOf(0.5, 0.25)) return "Tốc độ replay không hợp lệ."
            val output = surface ?: return "Hình camera chưa sẵn sàng."
            val video = format ?: return "Bắt đầu LIVE rồi chờ bộ đệm video."
            val clip = ring.snapshot(seconds) ?: return "Chưa đủ $seconds giây video. Hãy đợi thêm hoặc chọn đoạn ngắn hơn."
            ring.clear()
            speed = requestedSpeed; transition = requestedTransition; showing = false
            transitionStartedNs = 0L; transitionOutStartedNs = 0L
            expectedPtsUs = Long.MIN_VALUE; frameAck = false
            val token = ++session
            worker = Thread({ decode(clip, video, output, token, requestedSpeed) }, "SlowMotionReplay").also { it.start() }
        }
        notify("Đang chuẩn bị phát lại…")
        return null
    }
    fun cancel() {
        synchronized(lock) {
            session++; showing = false; transitionStartedNs = 0L; transitionOutStartedNs = 0L; expectedPtsUs = Long.MIN_VALUE
            captureAfterMs = SystemClock.elapsedRealtime() + 1200
            ring.clear(); worker?.interrupt(); lock.notifyAll()
        }
    }
    fun close() {
        synchronized(lock) { closed = true; surface = null }
        cancel()
    }
    /** Called only after updateTexImage on the GL thread. Unique session PTS rejects stale frames. */
    fun frameLatched(ptsUs: Long) {
        var first = false
        synchronized(lock) {
            if (!closed && worker != null && expectedSession == session && ptsUs == expectedPtsUs) {
                first = !showing
                if (first) transitionStartedNs = System.nanoTime()
                showing = true; frameAck = true; lock.notifyAll()
            }
        }
        if (first) notify("PHÁT LẠI • Micro trực tiếp")
    }
    private fun active(token: Long) = synchronized(lock) { !closed && session == token }
    private fun waitUntil(targetNs: Long, token: Long) {
        while (active(token)) {
            val remaining = targetNs - System.nanoTime()
            if (remaining <= 0) return
            Thread.sleep((remaining / 1_000_000).coerceIn(1, 20))
        }
        throw InterruptedException()
    }
    private fun decode(clip: ReplayClip, video: Format, output: Surface, token: Long, rate: Double) {
        var codec: MediaCodec? = null
        var failure = false
        try {
            val decoderFormat = MediaFormat.createVideoFormat("video/avc", video.width, video.height)
            video.csd.forEach { (key, data) -> decoderFormat.setByteBuffer(key, ByteBuffer.wrap(data)) }
            decoderFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, clip.packets.maxOf { it.bytes.size })
            codec = MediaCodec.createDecoderByType("video/avc")
            codec.configure(decoderFormat, output, null, 0)
            codec.start()
            val tagUs = token * 100_000_000L
            val baseUs = clip.packets.first().ptsUs
            val visibleStartUs = tagUs + clip.startUs - baseUs
            val info = MediaCodec.BufferInfo()
            var input = 0
            var eosSent = false
            var done = false
            var wallStartNs = 0L
            var lastProgressMs = SystemClock.elapsedRealtime()
            while (!done && active(token)) {
                if (!eosSent) {
                    val index = codec.dequeueInputBuffer(0)
                    if (index >= 0) {
                        if (input < clip.packets.size) {
                            val packet = clip.packets[input++]
                            val data = codec.getInputBuffer(index) ?: error("Missing decoder input")
                            check(data.capacity() >= packet.bytes.size)
                            data.clear(); data.put(packet.bytes)
                            codec.queueInputBuffer(index, 0, packet.bytes.size, tagUs + packet.ptsUs - baseUs, 0)
                        } else {
                            codec.queueInputBuffer(index, 0, 0, tagUs + clip.endUs - baseUs + 1, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            eosSent = true
                        }
                        lastProgressMs = SystemClock.elapsedRealtime()
                    }
                }
                val index = codec.dequeueOutputBuffer(info, 10_000)
                if (index >= 0) {
                    if (info.size > 0 && info.presentationTimeUs >= visibleStartUs) {
                        val deltaUs = info.presentationTimeUs - visibleStartUs
                        if (wallStartNs == 0L) wallStartNs = System.nanoTime() - replayDelayNs(deltaUs, rate)
                        waitUntil(wallStartNs + replayDelayNs(deltaUs, rate), token)
                        synchronized(lock) {
                            if (!active(token)) throw InterruptedException()
                            expectedSession = token; expectedPtsUs = info.presentationTimeUs; frameAck = false
                        }
                        codec.releaseOutputBuffer(index, true)
                        // One frame in flight prevents SurfaceTexture from skipping slow-motion frames.
                        val deadline = SystemClock.elapsedRealtime() + 1800
                        synchronized(lock) {
                            while (!frameAck && active(token) && SystemClock.elapsedRealtime() < deadline) lock.wait(30)
                            if (!active(token)) throw InterruptedException()
                            check(frameAck) { "Replay frame was not consumed" }
                        }
                    } else codec.releaseOutputBuffer(index, false)
                    lastProgressMs = SystemClock.elapsedRealtime()
                    done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                }
                check(SystemClock.elapsedRealtime() - lastProgressMs < 5000) { "Decoder stalled" }
            }
            if (active(token) && wallStartNs != 0L) {
                waitUntil(wallStartNs + replayDelayNs(clip.durationUs, rate) + 40_000_000, token)
                if (transition.exitDurationMs > 0) {
                    transitionOutStartedNs = System.nanoTime()
                    waitUntil(transitionOutStartedNs + transition.exitDurationMs * 1_000_000L, token)
                }
            }
        } catch (_: InterruptedException) { /* Immediate return to camera was requested. */ }
          catch (_: Exception) { failure = active(token) }
          catch (_: OutOfMemoryError) { failure = active(token) }
        finally {
            // Stop showing replay before releasing the decoder; keep the network encoder untouched.
            synchronized(lock) {
                showing = false; transitionStartedNs = 0L; transitionOutStartedNs = 0L
                expectedPtsUs = Long.MIN_VALUE; lock.notifyAll()
            }
            try { codec?.stop() } catch (_: Exception) { }
            try { codec?.release() } catch (_: Exception) { }
            val report = synchronized(lock) {
                worker = null; ring.clear(); captureAfterMs = SystemClock.elapsedRealtime() + 1200
                !closed
            }
            if (report) notify(if (failure) "Replay không chạy được trên cấu hình này. Đã về LIVE; thử 720p30." else "Đã về LIVE • Đang nạp lại bộ đệm replay")
        }
    }
}
