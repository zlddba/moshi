package dev.zlddba.moshiapp.ingest.voice

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceRecorder {

    data class Recording(val samples: ShortArray, val durationMs: Long)

    private val lock = Any()
    private val buffer = ShortArray(MAX_SECONDS * SAMPLE_RATE)
    private var sampleCount = 0
    private var audioRecord: AudioRecord? = null
    private var worker: Thread? = null
    private var recording = false

    @Volatile
    private var stopRequested = false

    fun start(): Boolean {
        if (recording) return false
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuffer <= 0) return false
        val created = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                maxOf(minBuffer, READ_CHUNK * 4)
            )
        } catch (e: IllegalArgumentException) {
            return false
        }
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            return false
        }
        sampleCount = 0
        stopRequested = false
        try {
            created.startRecording()
        } catch (e: IllegalStateException) {
            created.release()
            return false
        }
        if (created.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            created.release()
            return false
        }
        audioRecord = created
        recording = true
        worker = Thread {
            val chunk = ShortArray(READ_CHUNK)
            while (!stopRequested) {
                val record = audioRecord ?: break
                val n = record.read(chunk, 0, chunk.size)
                if (n < 0) break
                if (n == 0) continue
                val reachedLimit = synchronized(lock) {
                    val remaining = buffer.size - sampleCount
                    val toCopy = minOf(n, remaining)
                    if (toCopy > 0) {
                        System.arraycopy(chunk, 0, buffer, sampleCount, toCopy)
                        sampleCount += toCopy
                    }
                    toCopy == 0
                }
                if (reachedLimit) break
            }
        }.also { it.start() }
        return true
    }

    fun stop(): Recording? {
        if (!recording) return null
        stopRequested = true
        worker?.join(1000)
        worker = null
        val record = audioRecord
        audioRecord = null
        if (record != null) {
            try {
                record.stop()
            } catch (e: IllegalStateException) {
            }
            record.release()
        }
        recording = false
        val count = synchronized(lock) {
            val current = sampleCount
            sampleCount = 0
            current
        }
        if (count <= 0) return null
        return Recording(buffer.copyOf(count), count * 1000L / SAMPLE_RATE)
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val MAX_SECONDS = 180
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val READ_CHUNK = 1024
        private const val FORMAT_PCM: Short = 1
        private const val CHANNELS: Short = 1
        private const val BLOCK_ALIGN: Short = 2
        private const val BITS_PER_SAMPLE: Short = 16
        private val RIFF_BYTES = "RIFF".toByteArray(Charsets.US_ASCII)
        private val WAVE_BYTES = "WAVE".toByteArray(Charsets.US_ASCII)
        private val FMT_BYTES = "fmt ".toByteArray(Charsets.US_ASCII)
        private val DATA_BYTES = "data".toByteArray(Charsets.US_ASCII)

        fun writeWav(file: File, samples: ShortArray) {
            val dataSize = samples.size * 2
            FileOutputStream(file).use { out ->
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                header.put(RIFF_BYTES)
                header.putInt(36 + dataSize)
                header.put(WAVE_BYTES)
                header.put(FMT_BYTES)
                header.putInt(16)
                header.putShort(FORMAT_PCM)
                header.putShort(CHANNELS)
                header.putInt(SAMPLE_RATE)
                header.putInt(SAMPLE_RATE * 2)
                header.putShort(BLOCK_ALIGN)
                header.putShort(BITS_PER_SAMPLE)
                header.put(DATA_BYTES)
                header.putInt(dataSize)
                out.write(header.array())
                val body = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
                body.asShortBuffer().put(samples)
                out.write(body.array())
            }
        }
    }
}
