package com.vangnang.youtubelive

data class ReplayPacket(val bytes: ByteArray, val ptsUs: Long, val keyFrame: Boolean)
data class ReplayClip(val packets: List<ReplayPacket>, val startUs: Long, val endUs: Long) {
    val durationUs get() = endUs - startUs
}

/** Encoded H.264 only. Whole GOP eviction keeps every retained clip independently decodable. */
class ReplayBuffer(private val byteLimit: Int = 32 * 1024 * 1024, private val durationLimitUs: Long = 12_000_000) {
    private val packets = ArrayDeque<ReplayPacket>()
    private var bytes = 0
    private var newestUs = 0L
    @Synchronized fun clear() { packets.clear(); bytes = 0; newestUs = 0 }
    @Synchronized fun add(packet: ReplayPacket) {
        if (packet.bytes.isEmpty()) return
        if (packet.bytes.size > byteLimit) { clear(); return }
        // A large backwards timestamp jump means a new encoder timeline, not a usable continuation.
        if (packets.isNotEmpty() && packet.ptsUs < newestUs - 1_000_000) clear()
        if (packets.isEmpty() && !packet.keyFrame) return
        packets.addLast(packet); bytes += packet.bytes.size
        newestUs = maxOf(newestUs, packet.ptsUs)
        while (packets.isNotEmpty() && (bytes > byteLimit || newestUs - packets.first().ptsUs > durationLimitUs || packets.size > 1000)) {
            bytes -= packets.removeFirst().bytes.size
            while (packets.isNotEmpty() && !packets.first().keyFrame) bytes -= packets.removeFirst().bytes.size
        }
    }
    @Synchronized fun availableUs(): Long = if (packets.size < 2) 0 else (newestUs - packets.first().ptsUs).coerceAtLeast(0)
    @Synchronized fun byteSize() = bytes
    @Synchronized fun snapshot(seconds: Int): ReplayClip? {
        require(seconds in listOf(3, 5, 8))
        val duration = seconds * 1_000_000L
        if (availableUs() < duration) return null
        val start = newestUs - duration
        val list = packets.toList()
        val first = list.indexOfLast { it.keyFrame && it.ptsUs <= start }
        if (first < 0) return null
        return ReplayClip(list.drop(first), start, newestUs)
    }
}

fun replayDelayNs(sourceDeltaUs: Long, speed: Double): Long {
    require(speed == 0.5 || speed == 0.25)
    return (sourceDeltaUs.coerceAtLeast(0) * 1000.0 / speed).toLong()
}
