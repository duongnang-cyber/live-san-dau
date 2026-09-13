package com.vangnang.youtubelive

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Range
import android.view.Surface
import com.pedro.encoder.input.sources.video.VideoSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Explicit camera ID + capture size + initial AE request. Encoder size is independent. */
class LiveCameraSource(context: Context, private val support: CameraSupport, private val targetFps: Int,
    private val onReady: () -> Unit, private val onFps: (Double) -> Unit,
    private val onCaptureFps: (Double) -> Unit,
    private val onSensorState: (String) -> Unit = {},
    private val onFailure: (String) -> Unit) : VideoSource() {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val info = manager.getCameraCharacteristics(support.mode.cameraId)
    @Volatile private var activeRun: Run? = null
    @Volatile private var latestRun: Run? = null
    @Volatile private var zoom = 1f

    private class Run(val surface: Surface, val texture: SurfaceTexture, targetFps: Int) {
        val thread = HandlerThread("LiveCamera").apply { start() }
        val handler = Handler(thread.looper)
        var opening = false
        var closing = false
        val closed = CountDownLatch(1)
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var sessionGeneration = 0
        var verificationStarted = SystemClock.elapsedRealtime()
        var requestState = "Chưa gửi yêu cầu"
        var sessionState = ""
        var request: CaptureRequest.Builder? = null
        var released = false
        var ready = false
        val started = SystemClock.elapsedRealtime()
        var lastFrame = started
        var lastLatchedTimestamp = 0L
        var deliveryFps = 0.0
        var manualRequested = false
        var manualEffective = false
        var captureFps = 0.0
        var sensorState = "Chưa có kết quả cảm biến"
        var phase = "Đang mở camera"
        var pendingFailure: String? = null
        var afterClosed: (() -> Unit)? = null
        var captureSurface: Surface = surface
        var meter = CameraFpsMeter()
        var deliveryMeter = FrameDeliveryMeter()
        var gate = CameraDeliveryGate(targetFps)
    }

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        require(width > 0 && height > 0 && width % 2 == 0 && height % 2 == 0)
        require(support.mode.width >= width && support.mode.height >= height)
        return true
    }

    // Keep VideoSource's default orientation config: an explicit cameraOrientation disables
    // RootEncoder's automatic display rotation handling in version 2.7.2.
    override fun isRunning() = activeRun != null

    @SuppressLint("MissingPermission")
    override fun start(surfaceTexture: SurfaceTexture) {
        if (activeRun != null) return
        this.surfaceTexture = surfaceTexture
        surfaceTexture.setDefaultBufferSize(support.captureSize.width, support.captureSize.height)
        val run = Run(Surface(surfaceTexture), surfaceTexture, targetFps)
        latestRun = run
        activeRun = run
        run.handler.post {
            if (activeRun !== run) { finish(run); return@post }
            try {
                run.opening = true
                manager.openCamera(support.mode.cameraId, object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        run.opening = false; run.device = camera
                        if (activeRun !== run) { finish(run); return }
                        configure(run, camera)
                    }
                    override fun onDisconnected(camera: CameraDevice) {
                        run.opening = false; run.device = camera
                        fail(run, "Camera ${support.mode.cameraId} bị ngắt kết nối.")
                    }
                    override fun onError(camera: CameraDevice, error: Int) {
                        run.opening = false; run.device = camera
                        fail(run, "Camera ${support.mode.cameraId}: mã lỗi mở camera $error.")
                    }
                    override fun onClosed(camera: CameraDevice) {
                        run.opening = false; run.closing = false; run.device = null
                        finish(run)
                    }
                }, run.handler)
            } catch (e: Exception) {
                run.opening = false
                fail(run, "Mở camera: ${details(e)}")
            }
        }
        run.handler.postDelayed(object : Runnable {
            override fun run() {
                if (activeRun !== run) return
                val now = SystemClock.elapsedRealtime()
                if (now - run.lastFrame > 8000) {
                    fail(run, "Không có khung hình mới đi vào phần hiển thị trong 8 giây. Callback camera không chứng minh có hình.")
                } else if (!run.ready && now - run.verificationStarted > 12000) {
                    fail(run, frameRateFailure(targetFps, run.captureFps, run.deliveryFps))
                } else run.handler.postDelayed(this, 2000)
            }
        }, 2000)
    }

    @Suppress("DEPRECATION")
    private fun configure(run: Run, camera: CameraDevice) {
        try {
            // Initial request must already be valid. Do not wait for a frame to fix its FPS.
            run.request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                addTarget(run.captureSurface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                val aeRange = if (support.mode.manual)
                    info.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
                        .filter { it.upper <= 30 }.maxWithOrNull(compareBy<Range<Int>> { it.upper }.thenBy { it.lower })
                        ?: throw IllegalArgumentException("Không có dải AE đo sáng khởi đầu hợp lệ.")
                    else support.fpsRange
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, aeRange)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                // Stabilization support at 30 FPS does not imply support at 60 FPS.
                if (targetFps > 30 && info.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                        ?.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF) == true)
                    set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
                if (info.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO) == true)
                    set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                applyZoom(this)
            }
            run.phase = if (support.mode.manual) "Đo sáng ngắn trước khi yêu cầu trực tiếp $targetFps FPS" else "Đang yêu cầu AE trực tiếp $targetFps FPS"
            onSensorState(run.phase)
            createSession(run, camera, manual = false)
        } catch (e: Exception) { fail(run, "Tạo phiên camera: ${details(e)}") }
    }

    @Suppress("DEPRECATION")
    private fun createSession(run: Run, camera: CameraDevice, manual: Boolean) {
        if (activeRun !== run) return
        // Invalidate old callbacks BEFORE closing. They must not fail or confirm the new session.
        val generation = ++run.sessionGeneration
        run.requestState = requestReport(requireNotNull(run.request).build())
        run.sessionState = if (manual) "Đang tạo phiên manual mới" else "Đang tạo phiên AE"
        val previous = run.session
        run.session = null
        previous?.stopRepeating()
        previous?.close()
        val callback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                if (activeRun !== run || generation != run.sessionGeneration) {
                    session.close(); return
                }
                run.session = session
                repeat(run)
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                session.close()
                if (activeRun === run && generation == run.sessionGeneration)
                    fail(run, "Camera từ chối phiên ${support.captureSize}/${support.fpsRange}${if (manual) " manual trực tiếp mới" else " AE trực tiếp"}.")
            }
        }
        if (Build.VERSION.SDK_INT >= 28) {
            val initial = requireNotNull(run.request).build()
            val config = SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                listOf(OutputConfiguration(run.captureSurface)),
                java.util.concurrent.Executor { command -> run.handler.post(command) }, callback)
            // Android ignores keys that are not advertised as session keys, as documented.
            config.setSessionParameters(initial)
            val keys = info.availableSessionKeys.orEmpty().filter { initial.get(it) != null }
            run.sessionState = (if (manual) "Phiên manual mới" else "Phiên AE với FPS đặt ngay từ đầu") + "; session keys công bố: " +
                keys.joinToString { it.name }.ifEmpty { "không có" }
            camera.createCaptureSession(config)
        } else {
            run.sessionState = (if (manual) "Phiên manual mới" else "Phiên AE") + "; API <28 không có session parameters"
            camera.createCaptureSession(listOf(run.captureSurface), callback, run.handler)
        }
    }

    private fun requestReport(request: CaptureRequest) = "Yêu cầu: AE=${request.get(CaptureRequest.CONTROL_AE_MODE)}; AE range=${request.get(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE)}; frame ns=${request.get(CaptureRequest.SENSOR_FRAME_DURATION)}; exposure ns=${request.get(CaptureRequest.SENSOR_EXPOSURE_TIME)}; ISO=${request.get(CaptureRequest.SENSOR_SENSITIVITY)}"

    private fun sensorReport(run: Run) = "${run.phase}\n${run.sessionState}\n${run.requestState}\nTrả về: ${run.sensorState}"

    private fun repeat(run: Run) {
        if (activeRun !== run) return
        try {
            val session = run.session ?: return
            val request = run.request?.build() ?: return
            val generation = run.sessionGeneration
            run.requestState = requestReport(request)
            val callback = object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                    if (activeRun !== run || generation != run.sessionGeneration || session !== run.session) return
                    if (support.mode.manual && !run.manualRequested) {
                        val elapsed = SystemClock.elapsedRealtime() - run.started
                        val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                        if (elapsed >= 500 && (ae == CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                                ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED || elapsed >= 1500)) {
                            val exposure = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
                            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
                            if (exposure != null && iso != null) { requestManual(run, exposure, iso); return }
                        }
                    }
                    if (support.mode.manual && run.manualRequested && !run.manualEffective &&
                            result.get(CaptureResult.CONTROL_AE_MODE) == CaptureResult.CONTROL_AE_MODE_OFF) {
                        run.manualEffective = true
                        run.meter = CameraFpsMeter(); run.deliveryMeter = FrameDeliveryMeter()
                        run.gate = CameraDeliveryGate(targetFps)
                        run.phase = "Camera đã trả AE_OFF; đang đo FPS thực nhận"
                    }
                    run.meter.add(result.get(CaptureResult.SENSOR_TIMESTAMP) ?: 0, result.frameNumber)?.let { measured ->
                        run.sensorState = "AE=${result.get(CaptureResult.CONTROL_AE_MODE)}; frame ns=${result.get(CaptureResult.SENSOR_FRAME_DURATION)}; exposure ns=${result.get(CaptureResult.SENSOR_EXPOSURE_TIME)}; ISO=${result.get(CaptureResult.SENSOR_SENSITIVITY)}"
                        run.captureFps = measured; onCaptureFps(measured)
                        onSensorState(sensorReport(run))
                    }
                }
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                    if (activeRun === run && generation == run.sessionGeneration && session === run.session)
                        fail(run, "Camera không hoàn tất khung hình: reason=${failure.reason}, frame=${failure.frameNumber}.")
                }
                override fun onCaptureBufferLost(session: CameraCaptureSession, request: CaptureRequest, target: Surface, frameNumber: Long) {
                    if (activeRun === run && generation == run.sessionGeneration && session === run.session) fail(run, "Camera mất bộ đệm đầu ra ở khung $frameNumber.")
                }
            }
            session.setRepeatingRequest(request, callback, run.handler)
        } catch (e: Exception) { fail(run, "Gửi yêu cầu FPS: ${details(e)}") }
    }

    private fun requestManual(run: Run, exposureNs: Long, iso: Int) {
        try {
            val limits = requireNotNull(sensorLimits(info)) { "Camera không cung cấp MANUAL_SENSOR và các khóa điều khiển cần thiết." }
            val plan = directExposure(limits, exposureNs, iso, targetFps)
            val camera = requireNotNull(run.device)
            // Never inherit the RECORD template's AE range or warm-up controls.
            val manualRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_MANUAL).apply {
                addTarget(run.captureSurface)
                set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, null)
                set(CaptureRequest.SENSOR_FRAME_DURATION, plan.frameNs)
                set(CaptureRequest.SENSOR_EXPOSURE_TIME, plan.exposureNs)
                set(CaptureRequest.SENSOR_SENSITIVITY, plan.iso)
                if (info.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                        ?.contains(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF) == true)
                    set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
                if (info.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO) == true)
                    set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                if (info.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.contains(CameraMetadata.CONTROL_AWB_MODE_AUTO) == true)
                    set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO)
                applyZoom(this)
            }
            run.manualRequested = true
            run.manualEffective = false
            run.request = manualRequest
            run.meter = CameraFpsMeter(); run.deliveryMeter = FrameDeliveryMeter()
            run.gate = CameraDeliveryGate(targetFps)
            run.captureFps = 0.0; run.deliveryFps = 0.0
            run.sensorState = "Chưa có kết quả từ phiên manual mới"
            run.verificationStarted = SystemClock.elapsedRealtime()
            run.lastFrame = run.verificationStarted
            run.phase = "Tạo phiên TEMPLATE_MANUAL mới: yêu cầu frame=${plan.frameNs} ns, exposure=${plan.exposureNs} ns, ISO=${plan.iso}; chưa xác nhận FPS"
            onCaptureFps(0.0); onFps(0.0)
            onSensorState(run.phase)
            createSession(run, camera, manual = true)
        } catch (error: Exception) { fail(run, "Điều khiển cảm biến trực tiếp: ${details(error)}") }
    }

    /** Called on GL thread before image effects/scoreboard, AFTER RootEncoder latches its texture.
     * Do not replace the library's SurfaceTexture listener or call updateTexImage a second time.
     */
    fun observeLatchedFrame() {
        val run = activeRun ?: return
        val timestamp = runCatching { run.texture.timestamp }.getOrDefault(0)
        if (timestamp <= 0 || timestamp == run.lastLatchedTimestamp) return
        run.lastLatchedTimestamp = timestamp
        val now = SystemClock.elapsedRealtime()
        run.handler.post {
            if (activeRun !== run) return@post
            run.lastFrame = now
            run.deliveryMeter.add(timestamp, now)?.let { measured ->
                run.deliveryFps = measured; onFps(measured)
                if (support.mode.manual && !run.manualEffective) return@let
                if (run.captureFps <= 0.0) return@let // Sensor and GL windows may finish in either order.
                val ready = run.gate.sample(minOf(measured, run.captureFps))
                if (!run.ready && ready) {
                    run.ready = true
                    run.phase = "Đã xác nhận FPS cảm biến và hình thực nhận" + if (support.mode.manual) "; phơi sáng/ISO đang khóa" else ""
                    onSensorState(sensorReport(run))
                    onReady()
                }
                if (!run.ready && run.gate.sustainedLowRate())
                    fail(run, frameRateFailure(targetFps, run.captureFps, measured))
            }
        }
    }

    private fun details(error: Exception): String = buildString {
        append(error.javaClass.simpleName)
        if (error is CameraAccessException) append(" reason=${error.reason}")
        error.message?.let { append(": ").append(it) }
        error.cause?.let { append("; cause=").append(it.javaClass.simpleName).append(": ").append(it.message) }
    }

    fun getZoomRange(): Range<Float> = Range(1f,
        (info.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f).coerceAtLeast(1f))
    fun getZoom() = zoom
    fun setZoom(value: Float) {
        zoom = value.coerceIn(1f, getZoomRange().upper)
        val run = activeRun ?: return
        run.handler.post {
            if (activeRun === run) {
                run.request?.let { applyZoom(it); repeat(run) }
            }
        }
    }
    private fun applyZoom(builder: CaptureRequest.Builder) {
        val sensor = info.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        val w = (sensor.width() / zoom).toInt().coerceAtLeast(2)
        val h = (sensor.height() / zoom).toInt().coerceAtLeast(2)
        val x = sensor.left + (sensor.width() - w) / 2
        val y = sensor.top + (sensor.height() - h) / 2
        builder.set(CaptureRequest.SCALER_CROP_REGION, Rect(x, y, x + w, y + h))
    }

    private fun fail(run: Run, reason: String) {
        val notify = activeRun === run
        if (notify) {
            activeRun = null
            run.pendingFailure = "$reason\nLần thử: ${support.description}\n${sensorReport(run)}\nFPS callback=${"%.1f".format(run.captureFps)}; FPS hình=${"%.1f".format(run.deliveryFps)}"
        }
        finish(run)
    }
    // Called on this run's camera thread. Keep it alive until an in-flight open delivers its handle.
    private fun finish(run: Run) {
        runCatching { run.session?.stopRepeating() }
        runCatching { run.session?.close() }; run.session = null
        val device = run.device; run.device = null
        if (device != null && !run.closing) {
            run.closing = true
            try { device.close() } catch (_: Exception) { run.closing = false }
        }
        if (!run.opening && !run.closing) {
            if (!run.released) { run.surface.release(); run.released = true }
            run.closed.countDown()
            run.thread.quitSafely()
            // Recovery may open the same ID: notify only after the old device releases it.
            val reason = run.pendingFailure; run.pendingFailure = null
            if (reason != null) onFailure(reason)
            val callback = run.afterClosed; run.afterClosed = null
            callback?.invoke()
        }
    }

    /** A partially started preview can throw synchronously. Retry only after its device closes. */
    fun isClosed() = latestRun?.closed?.count != 1L

    fun stopForRetry(onClosed: () -> Unit) {
        val run = latestRun
        if (run == null || run.closed.count == 0L) { onClosed(); return }
        val posted = run.handler.post {
            if (run.closed.count == 0L) { onClosed(); return@post }
            run.afterClosed = onClosed
            if (activeRun === run) activeRun = null
            finish(run)
        }
        if (!posted && run.closed.count == 0L) onClosed()
    }

    override fun stop() {
        val run = activeRun ?: return
        activeRun = null
        if (Looper.myLooper() == run.handler.looper) { finish(run); return }
        // Release the old device before a replacement source opens it. Bounded: never hang UI.
        if (run.handler.post { finish(run) }) {
            try { run.closed.await(500, TimeUnit.MILLISECONDS) }
            catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        }
    }
    override fun release() = stop()
}
