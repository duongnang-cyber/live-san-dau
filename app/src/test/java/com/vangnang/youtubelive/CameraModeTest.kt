package com.vangnang.youtubelive

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class CameraModeTest {
    private fun mode(id: String = "0", w: Int = 1920, h: Int = 1080, low: Int = 30, high: Int = 60,
                     duration: Long = 16_666_667, fast: Boolean = false) = CameraMode(id, w, h, low, high, duration, fast)
    @Test fun unknownDurationDoesNotBlock60() {
        val candidate = mode(duration = 0)
        assertEquals(candidate, chooseCameraMode(listOf(candidate), Quality.FULL_HD, 60))
    }
    @Test fun known30OnlySizeCannotPretendToBe60() {
        assertNull(chooseCameraMode(listOf(mode(duration = 33_333_333)), Quality.FULL_HD, 60))
    }
    @Test fun thirtyOnlyAeCannotPretendToBe60EvenIfDurationUnknown() {
        assertNull(chooseCameraMode(listOf(mode(high = 30, duration = 0)), Quality.FULL_HD, 60))
    }
    @Test fun checksOtherCamerasRatherThanOnlyFirstOne() {
        val fastCamera = mode(id = "3")
        assertEquals(fastCamera, chooseCameraMode(listOf(mode(high = 30), fastCamera), Quality.FULL_HD, 60))
    }
    @Test fun acceptsFourKSourceForTwoKOutputWithoutNativeTwoKSize() {
        val fourK = mode(w = 3840, h = 2160, high = 30, duration = 33_333_333)
        assertEquals(fourK, chooseCameraMode(listOf(mode(high = 30), fourK), Quality.QHD, 30))
    }
    @Test fun doesNotUpscale1080ToFakeTwoK() {
        assertNull(chooseCameraMode(listOf(mode()), Quality.QHD, 60))
    }
    @Test fun doesNotStretchFourByThreeSource() {
        assertNull(chooseCameraMode(listOf(mode(w = 4000, h = 3000)), Quality.QHD, 60))
    }
    @Test fun smallestSuitableSourcePreferred() {
        val full = mode()
        assertEquals(full, chooseCameraMode(listOf(mode(w = 3840, h = 2160), full), Quality.FULL_HD, 60))
    }
    @Test fun fixed60PreferredToVariable60() {
        val fixed = mode(low = 60)
        assertEquals(fixed, chooseCameraMode(listOf(mode(low = 15), fixed), Quality.FULL_HD, 60))
    }
    @Test fun neverUsesHigherThanRequestedAeRange() {
        assertNull(chooseCameraMode(listOf(mode(low = 30, high = 120, duration = 8_333_333)), Quality.FULL_HD, 60))
    }
    @Test fun normalSessionAlwaysPreferredToHighSpeed() {
        val normal = mode()
        assertEquals(normal, chooseCameraMode(listOf(mode(low = 120, high = 120, fast = true), normal), Quality.FULL_HD, 60))
    }
    @Test fun highSpeed120CannotStandInForDirect60() {
        val fast = mode(low = 120, high = 120, fast = true)
        assertNull(chooseCameraMode(listOf(mode(high = 30), fast), Quality.FULL_HD, 60))
    }
    @Test fun neverInvents60RangeFromHighSpeedMetadata() {
        val fast = mode(low = 120, high = 120, fast = true)
        assertNull(chooseCameraMode(listOf(fast), Quality.FULL_HD, 60))
    }
    @Test fun avoidsVariableHighSpeedAndUnnecessary240Capture() {
        assertNull(chooseCameraMode(listOf(mode(low = 30, high = 120, fast = true)), Quality.FULL_HD, 60))
        assertNull(chooseCameraMode(listOf(mode(low = 240, high = 240, fast = true)), Quality.FULL_HD, 60))
        assertNull(chooseCameraMode(listOf(mode(low = 120, high = 120, fast = true)), Quality.FULL_HD, 30))
    }
    @Test fun highSpeedMustAlsoHaveEnoughPixelsForTwoK() {
        assertNull(chooseCameraMode(listOf(mode(low = 120, high = 120, fast = true)), Quality.QHD, 60))
    }
    @Test fun meterCountsFramesNotBatchedCallbacks() {
        val meter = CameraFpsMeter()
        assertNull(meter.add(1_000_000_000, 100))
        assertEquals(120.0, meter.add(2_000_000_000, 220)!!, 0.0001)
        assertEquals(60.0, meter.add(3_000_000_000, 280)!!, 0.0001)
    }
    @Test fun meterHandlesMissingTimestampAndClockReset() {
        val meter = CameraFpsMeter()
        assertNull(meter.add(0, 0)); assertNull(meter.add(2_000_000_000, 20))
        assertNull(meter.add(1_000_000_000, 0))
        assertEquals(30.0, meter.add(2_000_000_000, 30)!!, 0.0001)
    }
    @Test fun liveWaitsForCameraReadinessAndStaleCallbacksAreGuarded() {
        val main = File("src/main/java/com/vangnang/youtubelive/MainActivity.kt").readText()
        assertTrue(main.contains("if (!cameraReady)"))
        assertTrue(main.contains("onReady = { ui(token)"))
        assertTrue(main.contains("onFailure = { reason -> ui(token)"))
        assertTrue(main.contains("forceFpsLimit(config.fps)"))
        assertFalse(main.contains("setCustomOnCaptureCompletedCallback"))
    }
    @Test fun sourcePreservesAutoOrientationAndDoesNotSetItsOwnFrameListener() {
        val source = File("src/main/java/com/vangnang/youtubelive/LiveCameraSource.kt").readText()
        assertFalse(source.contains("override fun getOrientationConfig"))
        assertFalse(source.contains("setOnFrameAvailableListener"))
        assertFalse(source.contains("surfaceTexture.release"))
    }
}
