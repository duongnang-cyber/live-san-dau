package com.vangnang.youtubelive

/** Android-free policy: unknown frame duration is NOT a 30 FPS limit. */
data class CameraMode(val cameraId: String, val width: Int, val height: Int,
    val minFps: Int, val maxFps: Int, val durationNs: Long = 0, val highSpeed: Boolean = false,
    val manual: Boolean = false) {
    fun supports(q: Quality, fps: Int): Boolean {
        if (width < q.width || height < q.height || width.toLong() * q.height != height.toLong() * q.width) return false
        if (highSpeed) return false // Never use 120/240 FPS as a workaround for 60.
        if (manual) return false // Timing controls are not proof that the HAL will deliver this rate.
        return fps in minFps..maxFps && maxFps <= fps &&
            (durationNs <= 0 || 1_000_000_000.0 / durationNs >= fps - 0.5)
    }
}

fun rankedCameraModes(modes: List<CameraMode>, quality: Quality, fps: Int): List<CameraMode> =
    modes.filter { it.supports(quality, fps) }.distinct().sortedWith(
        compareBy<CameraMode> { it.manual }.thenBy { it.maxFps != fps }
            .thenBy { it.width.toLong() * it.height }.thenBy { it.maxFps }.thenByDescending { it.minFps }
    )

fun chooseCameraMode(modes: List<CameraMode>, quality: Quality, fps: Int): CameraMode? =
    rankedCameraModes(modes, quality, fps).firstOrNull()

/** Bound startup time, but let each camera try its best mode before one ID consumes the budget. */
fun cameraProbeModes(modes: List<CameraMode>, quality: Quality, fps: Int, limit: Int = 8): List<CameraMode> {
    require(limit > 0)
    val ranked = rankedCameraModes(modes, quality, fps)
    val ordered = mutableListOf<CameraMode>()
    for (manual in listOf(false, true)) {
        val groups = ranked.filter { it.manual == manual }.groupBy { it.cameraId }.values.toList()
        val depth = groups.maxOfOrNull { it.size } ?: 0
        for (i in 0 until depth) groups.forEach { group -> group.getOrNull(i)?.let { ordered.add(it) } }
    }
    return ordered.take(limit)
}

/** One request, one finite queue. Never silently replace the requested FPS with 30. */
class CameraAttemptPlan(
    val config: StreamConfig, val targetFront: Boolean,
    val restoreConfig: StreamConfig?, val restoreFront: Boolean, val recovering: Boolean,
    candidates: List<CameraMode>
) {
    private val modes = candidates.toList()
    private val failures = mutableListOf<String>()
    var attempted = 0
        private set
    val total get() = modes.size
    fun hasNext() = attempted < total
    fun next(): CameraMode? = modes.getOrNull(attempted)?.also { attempted++ }
    fun recordFailure(reason: String): String {
        failures.add("Lần $attempted/$total: $reason")
        return failureReport()
    }
    fun failureReport() = failures.joinToString("\n\n")
}

/** High-speed callbacks may be batched: count sensor frame numbers, not callback invocations. */
class CameraFpsMeter {
    private var firstTime = 0L
    private var firstFrame = 0L
    fun add(timestampNs: Long, frameNumber: Long): Double? {
        if (timestampNs <= 0) return null
        if (firstTime == 0L || timestampNs <= firstTime || frameNumber < firstFrame) {
            firstTime = timestampNs; firstFrame = frameNumber; return null
        }
        if (timestampNs - firstTime < 1_000_000_000L) return null
        val fps = (frameNumber - firstFrame) * 1_000_000_000.0 / (timestampNs - firstTime)
        firstTime = timestampNs; firstFrame = frameNumber
        return fps
    }
}
