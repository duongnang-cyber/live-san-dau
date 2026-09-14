package com.vangnang.youtubelive

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CameraRetryTest {
    private val config = StreamConfig(Destination.YOUTUBE, Quality.FULL_HD, 60)
    private val normal = CameraMode("0", 1920, 1080, 60, 60, 16_666_667)

    @Test fun candidatesKeepAlternativeRangesAndCameras() {
        val variable = normal.copy(minFps = 15)
        val otherCamera = normal.copy(cameraId = "2")
        assertEquals(listOf(normal, otherCamera, variable),
            cameraProbeModes(listOf(variable, normal, otherCamera, normal), Quality.FULL_HD, 60))
    }
    @Test fun nativeResolutionStillPreferred() {
        val fourK = normal.copy(width = 3840, height = 2160)
        assertEquals(listOf(normal, fourK), cameraProbeModes(listOf(fourK, normal), Quality.FULL_HD, 60))
    }
    @Test fun unverifiedManualModesAreExcludedFromRetryQueue() {
        val manual = normal.copy(manual = true)
        val other = normal.copy(cameraId = "1")
        assertEquals(listOf(other, normal), cameraProbeModes(listOf(manual, other, normal), Quality.FULL_HD, 60))
    }
    @Test fun queueDoesNotInventSupportOrDownshiftFps() {
        val onlyThirty = normal.copy(minFps = 30, maxFps = 30)
        val highSpeed = normal.copy(minFps = 120, maxFps = 120, highSpeed = true)
        assertTrue(cameraProbeModes(listOf(onlyThirty, highSpeed), Quality.FULL_HD, 60).isEmpty())
    }
    @Test fun startupBudgetIsBoundedAndOtherCamerasGetAChance() {
        val many = (0..15).map { normal.copy(minFps = it + 1) }
        val other = normal.copy(cameraId = "2")
        val candidates = cameraProbeModes(many + other, Quality.FULL_HD, 60, 4)
        assertEquals(4, candidates.size)
        assertEquals(setOf("0", "2"), candidates.take(2).map { it.cameraId }.toSet())
    }
    @Test fun retryQueueExhaustsWithoutRepeatingFirstMode() {
        val other = normal.copy(cameraId = "2")
        val previous = config.copy(fps = 30)
        val plan = CameraAttemptPlan(config, false, previous, false, false, listOf(normal, other))
        assertEquals(normal, plan.next())
        assertTrue(plan.recordFailure("sensor=30").contains("Lần 1/2"))
        assertTrue(plan.hasNext())
        assertEquals(other, plan.next())
        plan.recordFailure("GL=30")
        assertFalse(plan.hasNext())
        repeat(3) { assertNull(plan.next()) }
        assertEquals(2, plan.attempted)
        assertEquals(60, plan.config.fps)
        assertEquals(30, plan.restoreConfig!!.fps)
        assertTrue(plan.failureReport().contains("sensor=30"))
        assertTrue(plan.failureReport().contains("GL=30"))
    }
    @Test fun diagnosticSeparatesGlBottleneckFromSensor() {
        assertTrue(frameRateFailure(60, 59.9, 30.0).contains("khâu nhận/xử lý hình"))
        assertTrue(frameRateFailure(60, 30.0, 30.0).contains("Cấu hình camera đang thử"))
        assertTrue(frameRateFailure(60, 0.0, 60.0).contains("Chưa đủ kết quả cảm biến"))
    }
    @Test fun stable59Point94IsValidBut30IsNot() {
        val gate = CameraDeliveryGate(60)
        assertFalse(gate.sample(30.0))
        assertFalse(gate.sample(59.94))
        assertTrue(gate.sample(59.94))
    }
    @Test fun regularAndManualSessionsBothGetInitialParametersOnApi28() {
        val source = File("src/main/java/com/vangnang/youtubelive/LiveCameraSource.kt").readText()
        val session = source.substringAfter("private fun createSession(").substringBefore("private fun requestReport")
        assertTrue(session.contains("if (Build.VERSION.SDK_INT >= 28)"))
        assertFalse(session.contains("if (manual && Build.VERSION.SDK_INT"))
        assertTrue(session.indexOf("config.setSessionParameters(initial)") < session.indexOf("camera.createCaptureSession(config)"))
        assertTrue(session.contains("SessionConfiguration.SESSION_REGULAR"))
    }
    @Test fun failedNewConfigurationCannotStartRestoredPreview() {
        val main = File("src/main/java/com/vangnang/youtubelive/MainActivity.kt").readText()
        val failed = main.substringAfter("private fun failPreparation(").substringBefore("private fun startLive()")
        assertTrue(failed.contains("!wasLive"))
        assertTrue(failed.contains("!plan.recovering"))
        assertTrue(failed.contains("return prepareNext(plan, pendingStart)"))
        assertTrue(failed.trimEnd().endsWith("return false\n    }"))
    }
    @Test fun fpsLimitAppliedAfterEncoderPreparation() {
        val main = File("src/main/java/com/vangnang/youtubelive/MainActivity.kt").readText()
        assertTrue(main.indexOf("current.prepareVideo(") < main.indexOf("forceFpsLimit(config.fps)"))
    }
    @Test fun synchronousStartupFailureWaitsForCameraClose() {
        val main = File("src/main/java/com/vangnang/youtubelive/MainActivity.kt").readText()
        assertTrue(main.contains("!camera.isClosed()"))
        assertTrue(main.contains("camera.stopForRetry { ui(token)"))
        val source = File("src/main/java/com/vangnang/youtubelive/LiveCameraSource.kt").readText()
        assertTrue(source.contains("val run = latestRun"))
        assertTrue(source.indexOf("run.closed.countDown()") < source.indexOf("callback?.invoke()"))
    }
}
