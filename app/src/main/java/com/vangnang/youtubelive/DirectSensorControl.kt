package com.vangnang.youtubelive

import kotlin.math.roundToInt

/** Public Camera2 controls, only within the camera's reported ranges. */
data class SensorControlLimits(val exposureMinNs: Long, val exposureMaxNs: Long,
    val isoMin: Int, val isoMax: Int, val maxFrameNs: Long) {
    fun valid() = exposureMinNs > 0 && exposureMaxNs >= exposureMinNs &&
        isoMin > 0 && isoMax >= isoMin && maxFrameNs >= exposureMinNs
    fun allows(fps: Int) = fps in FPS_OPTIONS && valid() &&
        exposureMinNs <= 1_000_000_000L / (2L * fps) && maxFrameNs >= frameDurationNs(fps)
}

data class DirectExposure(val frameNs: Long, val exposureNs: Long, val iso: Int)

fun frameDurationNs(fps: Int): Long {
    require(fps > 0)
    return (1_000_000_000L + fps - 1) / fps
}

fun directExposure(limits: SensorControlLimits, meteredExposureNs: Long, meteredIso: Int, fps: Int = 60): DirectExposure {
    require(limits.allows(fps)) { "Các khoảng điều khiển cảm biến không phù hợp $fps FPS." }
    require(meteredExposureNs > 0 && meteredIso > 0) { "Thiếu số đo phơi sáng/ISO từ camera." }
    // Half a frame or shorter leaves readout headroom; shutter speed is not capture FPS.
    val exposure = meteredExposureNs.coerceIn(limits.exposureMinNs, minOf(limits.exposureMaxNs, 1_000_000_000L / (2L * fps)))
    val iso = (meteredIso.toDouble() * meteredExposureNs / exposure)
        .coerceIn(limits.isoMin.toDouble(), limits.isoMax.toDouble()).roundToInt()
    return DirectExposure(frameDurationNs(fps), exposure, iso)
}
