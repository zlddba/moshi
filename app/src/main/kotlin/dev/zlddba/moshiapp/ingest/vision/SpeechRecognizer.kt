package dev.zlddba.moshiapp.ingest.vision

import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import java.io.File

class SpeechRecognizer(
    private val modelFile: File,
    private val tokensFile: File
) {

    private var recognizer: OfflineRecognizer? = null

    fun transcribe(samples: ShortArray): String {
        val engine = recognizer ?: createRecognizer().also { recognizer = it }
        val stream = engine.createStream()
        try {
            val floats = FloatArray(samples.size) { samples[it] / 32768.0f }
            stream.acceptWaveform(floats, SAMPLE_RATE)
            engine.decode(stream)
            return engine.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    fun release() {
        recognizer?.release()
        recognizer = null
    }

    private fun createRecognizer(): OfflineRecognizer {
        val config = OfflineRecognizerConfig()
        config.modelConfig.tokens = tokensFile.absolutePath
        config.modelConfig.numThreads = 2
        config.modelConfig.provider = "cpu"
        config.modelConfig.modelType = "sense-voice"
        config.modelConfig.debug = false
        config.modelConfig.senseVoice = OfflineSenseVoiceModelConfig().also { senseVoice ->
            senseVoice.model = modelFile.absolutePath
            senseVoice.language = "zh"
            senseVoice.useInverseTextNormalization = false
        }
        return OfflineRecognizer(config = config)
    }

    companion object {
        private const val SAMPLE_RATE = 16000
    }
}
