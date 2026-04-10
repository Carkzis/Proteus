package com.carkzis.proteus

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A simple AudioProcessor that inverts the audio samples (for demonstration).
 */
@UnstableApi
class InvertAudioProcessor : AudioProcessor {
    private var inputEnded = false
    private var buffer: ByteBuffer = EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET

    @OptIn(UnstableApi::class)
    override fun configure(audioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputAudioFormat = audioFormat
        outputAudioFormat = audioFormat
        return audioFormat
    }

    override fun isActive(): Boolean =
        inputAudioFormat.encoding == C.ENCODING_PCM_16BIT

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive() || inputBuffer.remaining() == 0) {
            outputBuffer = EMPTY_BUFFER
            return
        }
        val length = inputBuffer.remaining()
        // Only process full 16-bit samples
        val sampleCount = length / 2
        val out = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.LITTLE_ENDIAN)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            val sample = inputBuffer.short
            out.putShort((-sample).toShort())
        }
        out.flip()
        outputBuffer = out
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded

    override fun queueEndOfStream() {
        inputEnded = true
    }

    @Deprecated("Deprecated in Java")
    override fun flush() {
        buffer.clear()
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        buffer = EMPTY_BUFFER
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)
    }
}
