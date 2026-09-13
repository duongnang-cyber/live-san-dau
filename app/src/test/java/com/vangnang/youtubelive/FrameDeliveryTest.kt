package com.vangnang.youtubelive

import org.junit.Assert.*
import org.junit.Test

class FrameDeliveryTest {
    @Test fun repeatedBlackOrFrozenBufferDoesNotCountAsMoreFrames() {
        val meter = FrameDeliveryMeter()
        assertNull(meter.add(100, 1000))
        for (time in 1001L..5000L) assertNull(meter.add(100, time))
    }
    @Test fun thirtyNewBuffersPerSecondStayThirtyDespite120SensorMetadata() {
        val meter = FrameDeliveryMeter()
        meter.add(1, 1000)
        var value: Double? = null
        for (i in 1..30) value = meter.add(1 + i * 33_333_333L, 1000 + i * 1000L / 30)
        assertEquals(30.0, value!!, 0.01)
    }
    @Test fun sixtyDistinctFramesPassReadinessAfterTwoWindows() {
        val gate = CameraDeliveryGate(60)
        assertFalse(gate.sample(59.0))
        assertTrue(gate.sample(59.5))
    }
    @Test fun lgV40ReportedThirtyDoesNotPassSixtyGate() {
        val gate = CameraDeliveryGate(60)
        repeat(8) { assertFalse(gate.sample(30.0)) }
        assertTrue(gate.sustainedLowRate())
    }
    @Test fun thirtyModeStillPassesAtThirty() {
        val gate = CameraDeliveryGate(30)
        assertFalse(gate.sample(30.0)); assertTrue(gate.sample(30.0))
    }
    @Test fun isolatedFastSampleDoesNotMaskSlowDelivery() {
        val gate = CameraDeliveryGate(60)
        repeat(10) { assertFalse(gate.sample(60.0)); assertFalse(gate.sample(30.0)) }
    }
    @Test fun fiftyOneIsNotCloseEnoughToSixty() {
        val gate = CameraDeliveryGate(60)
        repeat(8) { assertFalse(gate.sample(51.0)) }
        assertTrue(gate.sustainedLowRate())
    }
    @Test fun fortyAccepts38And39ButDoesNotAcceptThirty() {
        val pass = CameraDeliveryGate(40)
        assertFalse(pass.sample(38.0))
        assertTrue(pass.sample(39.0))
        val fail = CameraDeliveryGate(40)
        repeat(8) { assertFalse(fail.sample(30.0)) }
        assertTrue(fail.sustainedLowRate())
    }
    @Test fun allIntermediateTargetsRequireTwoConsecutiveGoodWindows() {
        for (fps in listOf(40, 45, 50)) {
            val gate = CameraDeliveryGate(fps)
            assertFalse(gate.sample(fps.toDouble()))
            assertFalse(gate.sample(30.0))
            assertFalse(gate.sample(fps * .95))
            assertTrue(gate.sample(fps.toDouble()))
        }
    }
}
