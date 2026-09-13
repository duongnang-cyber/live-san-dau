package com.vangnang.youtubelive

import org.junit.Assert.*
import org.junit.Test

class ReplayBufferTest {
    private fun packet(second: Double, key: Boolean = false, bytes: Int = 10) = ReplayPacket(ByteArray(bytes) { 7 }, (second * 1_000_000).toLong(), key)
    private fun fill(buffer: ReplayBuffer, until: Int = 10) { for (s in 0..until) buffer.add(packet(s.toDouble(), s % 2 == 0)) }
    @Test fun emptyBufferHasNoClip() { val b = ReplayBuffer(); assertEquals(0L, b.availableUs()); assertNull(b.snapshot(3)) }
    @Test fun waitsForFirstKeyframe() { val b = ReplayBuffer(); b.add(packet(1.0)); b.add(packet(2.0)); assertEquals(0, b.byteSize()) }
    @Test fun oneKeyframeIsNotAClip() { val b = ReplayBuffer(); b.add(packet(0.0, true)); assertEquals(0L, b.availableUs()); assertNull(b.snapshot(3)) }
    @Test fun requiresRequestedDuration() { val b = ReplayBuffer(); fill(b, 4); assertNotNull(b.snapshot(3)); assertNull(b.snapshot(5)) }
    @Test fun selectsLastFiveSecondsWithDecodePreroll() {
        val b = ReplayBuffer(); fill(b)
        val c = b.snapshot(5)!!
        assertEquals(4_000_000L, c.packets.first().ptsUs)
        assertTrue(c.packets.first().keyFrame)
        assertEquals(5_000_000L, c.startUs); assertEquals(10_000_000L, c.endUs); assertEquals(5_000_000L, c.durationUs)
    }
    @Test fun exactKeyframeBoundaryNeedsNoExtraPreroll() {
        val b = ReplayBuffer(); fill(b)
        val c = b.snapshot(8)!!
        assertEquals(2_000_000L, c.startUs); assertEquals(c.startUs, c.packets.first().ptsUs)
    }
    @Test fun eachSupportedDurationMatchesRequest() {
        val b = ReplayBuffer(); fill(b)
        for (s in listOf(3, 5, 8)) assertEquals(s * 1_000_000L, b.snapshot(s)!!.durationUs)
    }
    @Test fun snapshotSurvivesRingClear() {
        val b = ReplayBuffer(); fill(b)
        val c = b.snapshot(5)!!; b.clear()
        assertEquals(0, b.byteSize()); assertEquals(7.toByte(), c.packets.first().bytes[0])
        assertEquals(7, c.packets.size)
    }
    @Test fun evictionDropsWholeGops() {
        val b = ReplayBuffer(byteLimit = 60); fill(b, 8)
        assertTrue(b.byteSize() <= 60)
        val c = b.snapshot(3)!!
        assertTrue(c.packets.first().keyFrame); assertEquals(4_000_000L, c.packets.first().ptsUs)
    }
    @Test fun oversizedPacketResetsInsteadOfBreakingReferences() {
        val b = ReplayBuffer(byteLimit = 50); fill(b, 2)
        b.add(packet(3.0, bytes = 100)); assertEquals(0, b.byteSize())
        b.add(packet(4.0)); assertEquals(0, b.byteSize())
        b.add(packet(5.0, true)); assertEquals(10, b.byteSize())
    }
    @Test fun durationLimitRetainsKeyframeAtHead() {
        val b = ReplayBuffer(durationLimitUs = 6_000_000); fill(b, 12)
        assertTrue(b.availableUs() <= 6_000_000)
        assertTrue(b.snapshot(5)!!.packets.first().keyFrame)
    }
    @Test fun encoderTimestampRestartClearsOldTimeline() {
        val b = ReplayBuffer(); fill(b, 8); b.add(packet(0.0))
        assertEquals(0, b.byteSize()); b.add(packet(0.0, true)); assertEquals(10, b.byteSize())
    }
    @Test fun smallPresentationReorderingKeepsDecodeOrder() {
        val b = ReplayBuffer(); fill(b, 6); b.add(packet(5.8))
        val c = b.snapshot(3)!!
        assertEquals(6_000_000L, c.endUs); assertEquals(5_800_000L, c.packets.last().ptsUs)
    }
    @Test fun emptyPacketsDoNotConsumeMemory() { val b = ReplayBuffer(); b.add(packet(0.0, true, 0)); assertEquals(0, b.byteSize()) }
    @Test fun frameCountGuardAlsoBoundsVerySmallPackets() {
        val b = ReplayBuffer()
        repeat(4000) { b.add(ReplayPacket(byteArrayOf(1), it.toLong(), it % 10 == 0)) }
        assertTrue(b.byteSize() <= 1000)
    }
    @Test fun halfSpeedDoublesDuration() { assertEquals(10_000_000_000L, replayDelayNs(5_000_000, 0.5)) }
    @Test fun quarterSpeedQuadruplesDuration() { assertEquals(20_000_000_000L, replayDelayNs(5_000_000, 0.25)) }
    @Test fun negativeTimeCannotScheduleBackwards() { assertEquals(0L, replayDelayNs(-1, 0.5)) }
    @Test(expected = IllegalArgumentException::class) fun unsupportedSpeedIsRejected() { replayDelayNs(100, 0.0) }
    @Test(expected = IllegalArgumentException::class) fun unsupportedDurationIsRejected() { ReplayBuffer().snapshot(60) }
    @Test fun repeatedCaptureAndClearRemainBounded() {
        val b = ReplayBuffer(byteLimit = 100)
        repeat(100) { fill(b, 10); assertTrue(b.byteSize() <= 100); b.clear(); assertEquals(0, b.byteSize()) }
    }
}
