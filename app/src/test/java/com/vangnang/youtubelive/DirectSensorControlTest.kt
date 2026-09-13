package com.vangnang.youtubelive

import org.junit.Assert.*
import org.junit.Test

class DirectSensorControlTest {
    private val limits = SensorControlLimits(100_000, 100_000_000, 50, 1600, 1_000_000_000)
    private val auto30 = CameraMode("0", 1920, 1080, 30, 30, 16_666_667)
    private val manual60 = auto30.copy(minFps = 60, maxFps = 60, manual = true)

    @Test fun a06MinimumDurationAloneDoesNotInventAe60() {
        assertNull(chooseCameraMode(listOf(auto30), Quality.FULL_HD, 60))
    }
    @Test fun manualCapableA06CanTry60WithoutAny120ModeOrCodec() {
        assertEquals(manual60, chooseCameraMode(listOf(auto30, manual60), Quality.FULL_HD, 60))
    }
    @Test fun ordinary60PreferredSoAutoExposureIsPreserved() {
        val normal = manual60.copy(manual = false)
        assertEquals(normal, chooseCameraMode(listOf(manual60, normal), Quality.FULL_HD, 60))
    }
    @Test fun manualDoesNotOverrideKnown30SensorBandwidth() {
        assertNull(chooseCameraMode(listOf(manual60.copy(durationNs = 33_333_333)), Quality.FULL_HD, 60))
    }
    @Test fun manualRequiresKnownFrameDuration() {
        assertNull(chooseCameraMode(listOf(manual60.copy(durationNs = 0)), Quality.FULL_HD, 60))
    }
    @Test fun manual60IsNotSelectedFor30() {
        assertEquals(auto30, chooseCameraMode(listOf(manual60, auto30), Quality.FULL_HD, 30))
    }
    @Test fun shorterExposureCompensatesWithIsoWithinLimits() {
        val plan = directExposure(limits, 16_666_666, 200)
        assertEquals(16_666_667L, plan.frameNs)
        assertEquals(8_333_333L, plan.exposureNs)
        assertEquals(400, plan.iso)
    }
    @Test fun brightSceneKeepsShortExposureAndIso() {
        val plan = directExposure(limits, 1_000_000, 100)
        assertEquals(1_000_000L, plan.exposureNs)
        assertEquals(100, plan.iso)
    }
    @Test fun darkSceneNeverRequestsOutOfRangeIsoOrLongShutter() {
        val plan = directExposure(limits, 100_000_000, 1600)
        assertEquals(1600, plan.iso)
        assertTrue(plan.exposureNs <= 8_333_333)
        assertTrue(plan.exposureNs < plan.frameNs)
    }
    @Test(expected = IllegalArgumentException::class) fun missingMeteredExposureDoesNotUseInventedDefaults() {
        directExposure(limits, 0, 100)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidSensorBoundsAreRejected() {
        directExposure(limits.copy(exposureMinNs = 20_000_000), 33_333_333, 100)
    }
    @Test fun fortyRequests25msFramesAndCompensatesExposure() {
        val plan = directExposure(limits, 25_000_000, 200, 40)
        assertEquals(25_000_000L, plan.frameNs)
        assertEquals(12_500_000L, plan.exposureNs)
        assertEquals(400, plan.iso)
    }
    @Test fun intermediateTargetsUseTheirOwnFrameTiming() {
        val expected = mapOf(40 to 25_000_000L, 45 to 22_222_223L, 50 to 20_000_000L, 60 to 16_666_667L)
        for ((fps, duration) in expected) {
            val plan = directExposure(limits, 30_000_000, 100, fps)
            assertEquals(duration, plan.frameNs)
            assertTrue(plan.exposureNs * 2 <= plan.frameNs)
            assertEquals(fps.toDouble(), 1_000_000_000.0 / plan.frameNs, 0.00001)
        }
    }
    @Test fun sensorCanQualifyFor40ButNot60() {
        val slower = limits.copy(exposureMinNs = 10_000_000)
        assertTrue(slower.allows(40))
        assertFalse(slower.allows(60))
        assertEquals(25_000_000L, directExposure(slower, 25_000_000, 100, 40).frameNs)
    }
    @Test fun fortyModeIsNotReplacedByThirtyOrSixty() {
        val forty = auto30.copy(minFps = 40, maxFps = 40, manual = true)
        assertEquals(forty, chooseCameraMode(listOf(auto30, manual60, forty), Quality.FULL_HD, 40))
        assertNull(chooseCameraMode(listOf(auto30, manual60), Quality.FULL_HD, 40))
        assertNull(chooseCameraMode(listOf(forty.copy(durationNs = 33_333_333)), Quality.FULL_HD, 40))
    }
    @Test fun advertised40AePreferredOverManual40() {
        val forty = auto30.copy(minFps = 40, maxFps = 40, manual = true)
        val ae = forty.copy(manual = false)
        assertEquals(ae, chooseCameraMode(listOf(forty, ae), Quality.FULL_HD, 40))
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedManualTargetIsRejected() {
        directExposure(limits, 10_000_000, 100, 120)
    }
}
