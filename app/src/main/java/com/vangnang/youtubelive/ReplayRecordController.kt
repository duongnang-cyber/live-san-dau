package com.vangnang.youtubelive

import android.media.MediaCodec
import android.media.MediaFormat
import com.pedro.library.base.recording.RecordController
import com.pedro.library.util.AndroidMuxerRecordController
import java.nio.ByteBuffer

/** A tap on the encoder callbacks, not a disk recording. Never changes the network buffer position. */
class ReplayRecordController(private val replay: ReplayEngine,
    private val delegate: RecordController = AndroidMuxerRecordController()) : RecordController by delegate {
    override fun recordVideo(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        replay.capture(buffer, info)
        delegate.recordVideo(buffer, info)
    }
    override fun setVideoFormat(format: MediaFormat) {
        replay.setFormat(format)
        delegate.setVideoFormat(format)
    }
    override fun resetFormats() {
        replay.resetFormat()
        delegate.resetFormats()
    }
}
