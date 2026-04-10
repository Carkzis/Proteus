package com.carkzis.proteus

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque

/**
 * An AudioProcessor that reverses the audio (plays it backwards).
 * Note: This buffers the entire audio before outputting, so it only works for finite audio.
 */
@UnstableApi
class ReverseAudioProcessor : AudioProcessor {
    private var inputEnded = false
    private var inputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputAudioFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var bufferList = ArrayDeque<ByteArray>()
    private var totalBytes = 0
    private var reversedBuffer: ByteBuffer? = null
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER

    override fun configure(audioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputAudioFormat = audioFormat
        outputAudioFormat = audioFormat
        flush()
        return audioFormat
    }

    override fun isActive(): Boolean = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive() || inputBuffer.remaining() == 0) return
        val bytes = ByteArray(inputBuffer.remaining())
        inputBuffer.get(bytes)
        bufferList.add(bytes)
        totalBytes += bytes.size
    }

    override fun queueEndOfStream() {
        inputEnded = true
        if (!isActive() || totalBytes == 0) return
        // Concatenate all input
        val allBytes = ByteArray(totalBytes)
        var pos = 0
        for (chunk in bufferList) {
            System.arraycopy(chunk, 0, allBytes, pos, chunk.size)
            pos += chunk.size
        }
        // Reverse samples (16-bit)
        val sampleCount = totalBytes / 2
        val reversed = ByteBuffer.allocateDirect(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in sampleCount - 1 downTo 0) {
            val lo = allBytes[i * 2]
            val hi = allBytes[i * 2 + 1]
            reversed.put(lo)
            reversed.put(hi)
        }
        reversed.flip()
        reversedBuffer = reversed
        outputBuffer = reversed
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && (reversedBuffer == null || !reversedBuffer!!.hasRemaining())

    @Deprecated("Deprecated in Java")
    override fun flush() {
        bufferList.clear()
        totalBytes = 0
        reversedBuffer = null
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
        outputAudioFormat = AudioProcessor.AudioFormat.NOT_SET
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0)
    }
}

