package dev.zlddba.moshiapp.ingest.vision

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import java.io.File

class StreamingSpeechRecognizer(
    private val encoderFile: File,
    private val decoderFile: File,
    private val joinerFile: File,
    private val tokensFile: File
) {

    @Volatile
    private var running = false

    @Volatile
    private var lastText = ""

    private var recognizer: OnlineRecognizer? = null
    private var worker: Thread? = null

    fun lastResult(): String = lastText

    fun start(onPartial: (String) -> Unit, onError: () -> Unit): Boolean {
        val previous = worker
        if (running || (previous != null && previous.isAlive())) return false
        running = true
        lastText = ""
        worker = Thread {
            var active: OnlineStream? = null
            var record: AudioRecord? = null
            try {
                val engine = recognizer ?: createRecognizer().also { recognizer = it }
                val stream = engine.createStream()
                active = stream
                record = openRecord()
                if (record == null) {
                    running = false
                    onError()
                    return@Thread
                }
                record.startRecording()
                val shorts = ShortArray(READ_CHUNK)
                var lastPublish = 0L
                while (running) {
                    val n = record.read(shorts, 0, shorts.size)
                    if (n < 0) break
                    if (n == 0) continue
                    val floats = FloatArray(n) { shorts[it] / 32768.0f }
                    stream.acceptWaveform(floats, SAMPLE_RATE)
                    while (engine.isReady(stream)) {
                        engine.decode(stream)
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastPublish >= PARTIAL_INTERVAL_MS) {
                        lastPublish = now
                        val text = engine.getResult(stream).text
                        lastText = text
                        onPartial(text)
                    }
                }
                stream.inputFinished()
                while (engine.isReady(stream)) {
                    engine.decode(stream)
                }
                val finalText = engine.getResult(stream).text
                lastText = finalText
                onPartial(finalText)
            } catch (e: Throwable) {
                running = false
                onError()
            } finally {
                running = false
                try {
                    active?.release()
                } catch (e: Throwable) {
                }
                val current = record
                if (current != null) {
                    try {
                        current.stop()
                    } catch (e: IllegalStateException) {
                    }
                    current.release()
                }
            }
        }.also { it.start() }
        return true
    }

    fun stop() {
        running = false
    }

    fun release() {
        running = false
        val current = worker
        worker = null
        try {
            current?.join(1000L)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        recognizer?.release()
        recognizer = null
    }

    private fun createRecognizer(): OnlineRecognizer {
        val config = OnlineRecognizerConfig()
        config.featConfig.sampleRate = SAMPLE_RATE
        config.featConfig.featureDim = FEATURE_DIM
        config.modelConfig.transducer.encoder = encoderFile.absolutePath
        config.modelConfig.transducer.decoder = decoderFile.absolutePath
        config.modelConfig.transducer.joiner = joinerFile.absolutePath
        config.modelConfig.tokens = tokensFile.absolutePath
        config.modelConfig.numThreads = NUM_THREADS
        config.modelConfig.provider = PROVIDER
        config.modelConfig.debug = false
        config.enableEndpoint = false
        return OnlineRecognizer(config = config)
    }

    private fun openRecord(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBuffer <= 0) return null
        val created = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                maxOf(minBuffer, READ_CHUNK * 2)
            )
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (created.state != AudioRecord.STATE_INITIALIZED) {
            created.release()
            return null
        }
        return created
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FEATURE_DIM = 80
        private const val NUM_THREADS = 2
        private const val PROVIDER = "cpu"
        private const val READ_CHUNK = 1024
        private const val PARTIAL_INTERVAL_MS = 150L
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
