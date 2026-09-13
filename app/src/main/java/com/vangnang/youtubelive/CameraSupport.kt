package com.vangnang.youtubelive

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.Range
import android.util.Size

data class CameraSupport(val mode: CameraMode) {
    val captureSize get() = Size(mode.width, mode.height)
    val fpsRange get() = Range(mode.minFps, mode.maxFps)
    val description get() = "Camera ${mode.cameraId}: ${mode.width}×${mode.height} • " +
        if (mode.manual) "mục tiêu ${mode.maxFps} FPS bằng điều khiển cảm biến (thử nghiệm, cần xác nhận FPS thực)\nPhơi sáng/ISO khóa sau đo sáng; đổi ánh sáng cần bấm ÁP DỤNG lại."
        else "${mode.minFps}–${mode.maxFps} FPS trực tiếp"
}

fun sensorLimits(info: CameraCharacteristics): SensorControlLimits? {
    val manual = info.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(
        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true
    val aeOff = info.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.contains(CaptureRequest.CONTROL_AE_MODE_OFF) == true
    val keys = info.availableCaptureRequestKeys.orEmpty()
    if (!manual || !aeOff || !listOf(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.SENSOR_FRAME_DURATION,
            CaptureRequest.SENSOR_EXPOSURE_TIME, CaptureRequest.SENSOR_SENSITIVITY).all { it in keys }) return null
    val exposure = info.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: return null
    val iso = info.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: return null
    val maxFrame = info.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: return null
    return SensorControlLimits(exposure.lower, exposure.upper, iso.lower, iso.upper, maxFrame).takeIf { it.valid() }
}

private fun cameraModes(context: Context, front: Boolean): List<CameraMode> {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val facing = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
    return manager.cameraIdList.flatMap { id ->
        // A broken auxiliary camera must not hide other cameras on the same side.
        runCatching {
            val info = manager.getCameraCharacteristics(id)
            if (info.get(CameraCharacteristics.LENS_FACING) != facing) return@runCatching emptyList()
            val map = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return@runCatching emptyList()
            val ranges = info.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
            val regular = map.getOutputSizes(SurfaceTexture::class.java).orEmpty().flatMap { size ->
                val duration = runCatching { map.getOutputMinFrameDuration(SurfaceTexture::class.java, size) }.getOrDefault(0)
                ranges.map { CameraMode(id, size.width, size.height, it.lower, it.upper, duration) }
            }
            val limits = sensorLimits(info)
            val manual = if (limits == null) emptyList() else regular
                .distinctBy { Pair(it.width, it.height) }
                .flatMap { mode ->
                    FPS_OPTIONS.filter { fps -> fps > 30 && limits.allows(fps) &&
                        mode.durationNs in 1L..frameDurationNs(fps) }
                        .map { fps -> mode.copy(minFps = fps, maxFps = fps, manual = true) }
                }
            regular + manual
        }.getOrDefault(emptyList())
    }
}

private fun supportsEncoder(codec: MediaCodecInfo, config: StreamConfig): Boolean = runCatching {
    if (!codec.isEncoder || codec.supportedTypes.none { it.equals("video/avc", true) }) return@runCatching false
    val caps = codec.getCapabilitiesForType("video/avc")
    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface in caps.colorFormats &&
        caps.videoCapabilities.areSizeAndRateSupported(config.quality.width, config.quality.height, config.fps.toDouble()) &&
        caps.videoCapabilities.bitrateRange.contains(config.bitrate)
}.getOrDefault(false)

fun checkCameraCandidates(context: Context, front: Boolean, config: StreamConfig): List<CameraSupport> {
    config.validate()?.let { throw IllegalArgumentException(it) }
    val modes = cameraProbeModes(cameraModes(context, front), config.quality, config.fps)
    require(modes.isNotEmpty()) { "Camera2 chưa công bố tổ hợp ${config.quality.title}/${config.fps} FPS dùng được cho camera ${if (front) "trước" else "sau"}. " +
            "Không dùng nguồn 120 FPS. Chế độ trực tiếp cần dải AE phù hợp ${config.fps} FPS hoặc MANUAL_SENSOR cùng thời gian khung phù hợp. Mở ☰ → Chẩn đoán camera để xem chi tiết."
    }
    require(MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { supportsEncoder(it, config) }) {
        "Bộ mã hóa H.264 Surface chưa công bố ${config.label}. Xem Chẩn đoán camera."
    }
    return modes.map { CameraSupport(it) }
}

fun checkCameraSupport(context: Context, front: Boolean, config: StreamConfig): CameraSupport =
    checkCameraCandidates(context, front, config).first()

/** No credentials, serial numbers or network identifiers. Only model and capabilities. */
fun cameraDiagnosticReport(context: Context, front: Boolean, config: StreamConfig): String = buildString {
    appendLine("Live Sân Đấu 1.18 • ${Build.MANUFACTURER} ${Build.MODEL} • Android ${Build.VERSION.RELEASE}")
    appendLine("AE/manual đều đặt session parameters từ API 28; thử tối đa 8 cấu hình, giữ nguyên FPS đã chọn.")
    appendLine("Đã chọn: ${config.label} • ${if (front) "trước" else "sau"}")
    appendLine("0 ns = không khai báo thời gian khung, KHÔNG đồng nghĩa chỉ 30 FPS.")
    appendLine("Nguồn lớn hơn 16:9 được thu nhỏ về 2K/1080p, không phóng lớn nguồn nhỏ.")
    appendLine("Đường hình: camera → bộ lọc/bảng điểm → mã hóa. Không dùng bridge 120 FPS.")
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    for (side in listOf(false, true)) {
        appendLine("\n=== Camera ${if (side) "trước" else "sau"} ===")
        val modes = cameraModes(context, side)
        for ((id, camera) in modes.groupBy { it.cameraId }) {
            appendLine("ID $id • AE thường: " + camera.filter { !it.highSpeed && !it.manual }.map { "${it.minFps}–${it.maxFps}" }.distinct().joinToString())
            runCatching {
                val info = manager.getCameraCharacteristics(id)
                appendLine("MANUAL_SENSOR=" + (info.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true))
                appendLine("AE modes=" + info.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)?.joinToString())
                appendLine("Exposure ns=" + info.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) + "; ISO=" + info.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE))
                appendLine("Max frame ns=" + info.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) + "; có khóa điều khiển thủ công hợp lệ=" + (sensorLimits(info) != null) + " (không bảo đảm FPS)")
            }
            camera.filter { it.width >= 1280 && !it.manual }.groupBy { Triple(it.width, it.height, it.highSpeed) }.forEach { (key, values) ->
                appendLine("${key.first}×${key.second} ${if (key.third) "HIGH-SPEED" else "thường"} • ${values.first().durationNs} ns • " +
                    values.map { "${it.minFps}–${it.maxFps}" }.distinct().joinToString())
            }
        }
        for (q in Quality.entries) for (fps in FPS_OPTIONS) {
            val selected = chooseCameraMode(modes, q, fps)
            appendLine("${q.title}/$fps: " + (selected?.let { CameraSupport(it).description } ?: "không có tổ hợp phù hợp"))
        }
    }
    appendLine("\n=== H.264 Surface cho lựa chọn hiện tại ===")
    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter {
        it.isEncoder && it.supportedTypes.any { type -> type.equals("video/avc", true) }
    }.forEach { appendLine("${it.name}: ${if (supportsEncoder(it, config)) "công bố hỗ trợ" else "không công bố hỗ trợ"}") }
    appendLine("\nCallback cảm biến, khung hình vào bộ lọc và FPS mã hóa đo riêng. Chỉ sẵn sàng sau 2 cửa sổ đo hình >=95% mục tiêu; không kiểm tra nội dung sáng/tối của ảnh.")
}
