package com.vangnang.youtubelive

import java.util.Locale

/** Diagnosis is about this configuration, never a blanket claim about the phone's hardware. */
fun frameRateFailure(targetFps: Int, sensorFps: Double, deliveryFps: Double): String {
    val values = String.format(Locale.US, "Cảm biến %.1f FPS; hình nhận qua GL %.1f FPS; mục tiêu %d FPS.",
        sensorFps, deliveryFps, targetFps)
    val threshold = targetFps * 0.95
    val cause = when {
        sensorFps <= 0.0 -> "Chưa đủ kết quả cảm biến để xác nhận tốc độ."
        sensorFps >= threshold && deliveryFps < threshold ->
            "Cảm biến đạt ngưỡng nhưng khâu nhận/xử lý hình chưa theo kịp; chưa thể kết luận camera chỉ hỗ trợ 30 FPS."
        sensorFps < threshold ->
            "Cấu hình camera đang thử chưa đạt tốc độ yêu cầu; cần kiểm tra chế độ camera và ánh sáng."
        else -> "Chưa có hai cửa sổ ổn định liên tiếp để xác nhận."
    }
    return "$values $cause"
}

/** Counts distinct buffers latched by GL, using elapsed time rather than batched sensor metadata. */
class FrameDeliveryMeter {
    private var lastTimestamp = 0L
    private var startMs = 0L
    private var frames = 0
    fun add(timestampNs: Long, nowMs: Long): Double? {
        if (timestampNs <= 0 || timestampNs == lastTimestamp) return null
        lastTimestamp = timestampNs
        if (startMs == 0L || nowMs <= startMs) {
            startMs = nowMs; frames = 0; return null
        }
        frames++
        if (nowMs - startMs < 1000) return null
        val fps = frames * 1000.0 / (nowMs - startMs)
        startMs = nowMs; frames = 0
        return fps
    }
}

/** A capture callback alone cannot open the live gate. Give startup time, but bound failure. */
class CameraDeliveryGate(private val targetFps: Int) {
    var ready = false
        private set
    private var goodWindows = 0
    private var lowWindows = 0
    fun sample(fps: Double): Boolean {
        if (fps >= targetFps * 0.95) {
            goodWindows++; lowWindows = 0
            if (goodWindows >= 2) ready = true
        } else {
            goodWindows = 0; lowWindows++
        }
        return ready
    }
    fun sustainedLowRate() = lowWindows >= 8
}
