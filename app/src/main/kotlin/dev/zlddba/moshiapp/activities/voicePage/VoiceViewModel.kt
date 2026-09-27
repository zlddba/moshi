package dev.zlddba.moshiapp.activities.voicePage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.ingest.vision.AsrModelManager
import dev.zlddba.moshiapp.ingest.vision.SpeechRecognizer
import dev.zlddba.moshiapp.ingest.voice.VoiceRecorder
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

private const val MAX_RECORD_SECONDS = 180
private const val RECORD_FILE_NAME = "voice_record.wav"

class VoiceViewModel(context: Context) : ViewModel() {

    enum class VoicePhase {
        MODEL_MISSING,
        DOWNLOADING,
        READY,
        RECORDING,
        TRANSCRIBING,
        CONFIRM
    }

    data class VoiceUiState(
        val phase: VoicePhase = VoicePhase.MODEL_MISSING,
        val downloadPercent: Int = 0,
        val elapsedSeconds: Int = 0,
        val transcript: String = "",
        val durationMs: Long = 0L,
        val keepAudio: Boolean = true,
        val isPlaying: Boolean = false,
        val transcribeFailed: Boolean = false
    )

    sealed interface VoiceEvent {
        data object DownloadClicked : VoiceEvent
        data object RecordClicked : VoiceEvent
        data class PermissionResult(val granted: Boolean) : VoiceEvent
        data object StopRecordClicked : VoiceEvent
        data object PlayClicked : VoiceEvent
        data class TranscriptChanged(val text: String) : VoiceEvent
        data class KeepAudioChanged(val checked: Boolean) : VoiceEvent
        data object RerecordClicked : VoiceEvent
        data object ConfirmClicked : VoiceEvent
    }

    sealed interface VoiceEffect {
        data class ShowToast(val messageRes: Int) : VoiceEffect
        data object RequestAudioPermission : VoiceEffect
        data object Close : VoiceEffect
    }

    private val appContext = context.applicationContext
    private val recorder = VoiceRecorder()
    private var asr: SpeechRecognizer? = null
    private var mediaPlayer: MediaPlayer? = null
    private var timerJob: Job? = null

    private val _voiceUiState = MutableStateFlow(
        VoiceUiState(
            phase = if (AsrModelManager.isReady(appContext)) VoicePhase.READY else VoicePhase.MODEL_MISSING
        )
    )
    val voiceUiState: StateFlow<VoiceUiState> = _voiceUiState.asStateFlow()

    private val _effects = Channel<VoiceEffect>(Channel.BUFFERED)
    val effects: Flow<VoiceEffect> = _effects.receiveAsFlow()

    fun onEvent(event: VoiceEvent) {
        when (event) {
            VoiceEvent.DownloadClicked -> startDownload()
            VoiceEvent.RecordClicked -> onRecordClicked()
            is VoiceEvent.PermissionResult -> {
                if (event.granted) {
                    startRecording()
                } else {
                    sendEffect(VoiceEffect.ShowToast(R.string.voice_permission_denied))
                }
            }

            VoiceEvent.StopRecordClicked -> stopRecording()
            VoiceEvent.PlayClicked -> togglePlayback()
            is VoiceEvent.TranscriptChanged -> _voiceUiState.update { it.copy(transcript = event.text) }
            is VoiceEvent.KeepAudioChanged -> _voiceUiState.update { it.copy(keepAudio = event.checked) }
            VoiceEvent.RerecordClicked -> resetToReady()
            VoiceEvent.ConfirmClicked -> confirm()
        }
    }

    private fun startDownload() {
        if (_voiceUiState.value.phase != VoicePhase.MODEL_MISSING) return
        _voiceUiState.update { it.copy(phase = VoicePhase.DOWNLOADING, downloadPercent = 0) }
        viewModelScope.launch {
            try {
                AsrModelManager.download(appContext) { transferred ->
                    val percent = ((transferred * 100) / AsrModelManager.totalBytes())
                        .toInt()
                        .coerceIn(0, 100)
                    _voiceUiState.update { state ->
                        if (state.downloadPercent == percent) state else state.copy(downloadPercent = percent)
                    }
                }
                _voiceUiState.update { it.copy(phase = VoicePhase.READY, downloadPercent = 100) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _voiceUiState.update { it.copy(phase = VoicePhase.MODEL_MISSING, downloadPercent = 0) }
                sendEffect(VoiceEffect.ShowToast(R.string.voice_model_download_fail))
            }
        }
    }

    private fun onRecordClicked() {
        if (_voiceUiState.value.phase != VoicePhase.READY) return
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecording()
        } else {
            sendEffect(VoiceEffect.RequestAudioPermission)
        }
    }

    private fun startRecording() {
        if (_voiceUiState.value.phase != VoicePhase.READY) return
        stopPlayback()
        if (!recorder.start()) {
            sendEffect(VoiceEffect.ShowToast(R.string.voice_record_fail))
            return
        }
        _voiceUiState.update { it.copy(phase = VoicePhase.RECORDING, elapsedSeconds = 0) }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000.milliseconds)
                val next = _voiceUiState.value.elapsedSeconds + 1
                _voiceUiState.update { it.copy(elapsedSeconds = next) }
                if (next >= MAX_RECORD_SECONDS) {
                    stopRecording()
                    return@launch
                }
            }
        }
    }

    private fun stopRecording() {
        if (_voiceUiState.value.phase != VoicePhase.RECORDING) return
        timerJob?.cancel()
        timerJob = null
        val recording = recorder.stop()
        if (recording == null) {
            _voiceUiState.update { it.copy(phase = VoicePhase.READY, elapsedSeconds = 0) }
            sendEffect(VoiceEffect.ShowToast(R.string.voice_record_fail))
            return
        }
        _voiceUiState.update { it.copy(phase = VoicePhase.TRANSCRIBING) }
        transcribe(recording)
    }

    private fun transcribe(recording: VoiceRecorder.Recording) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dir = File(appContext.cacheDir, "voice")
                dir.mkdirs()
                VoiceRecorder.writeWav(File(dir, RECORD_FILE_NAME), recording.samples)
            }
            var failed = false
            var text = ""
            try {
                text = withContext(Dispatchers.Default) {
                    val engine = asr ?: SpeechRecognizer(
                        AsrModelManager.modelFile(appContext),
                        AsrModelManager.tokensFile(appContext)
                    ).also { asr = it }
                    engine.transcribe(recording.samples)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failed = true
                sendEffect(VoiceEffect.ShowToast(R.string.voice_engine_fail))
            }
            if (!failed && text.isBlank()) {
                failed = true
            }
            _voiceUiState.update {
                it.copy(
                    phase = VoicePhase.CONFIRM,
                    transcript = text,
                    durationMs = recording.durationMs,
                    transcribeFailed = failed
                )
            }
        }
    }

    private fun togglePlayback() {
        if (_voiceUiState.value.isPlaying) {
            stopPlayback()
            return
        }
        val file = wavFile()
        if (!file.exists()) return
        val player = MediaPlayer()
        try {
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.setOnCompletionListener {
                stopPlayback()
            }
            player.start()
        } catch (e: Exception) {
            player.release()
            sendEffect(VoiceEffect.ShowToast(R.string.voice_play_fail))
            return
        }
        mediaPlayer = player
        _voiceUiState.update { it.copy(isPlaying = true) }
    }

    private fun stopPlayback() {
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            try {
                player.stop()
            } catch (e: IllegalStateException) {
            }
            player.release()
        }
        _voiceUiState.update { state ->
            if (state.isPlaying) state.copy(isPlaying = false) else state
        }
    }

    private fun resetToReady() {
        if (_voiceUiState.value.phase != VoicePhase.CONFIRM) return
        stopPlayback()
        _voiceUiState.update {
            it.copy(
                phase = VoicePhase.READY,
                transcript = "",
                durationMs = 0L,
                elapsedSeconds = 0,
                transcribeFailed = false
            )
        }
    }

    private fun confirm() {
        val state = _voiceUiState.value
        if (state.phase != VoicePhase.CONFIRM) return
        if (state.transcript.isBlank()) {
            sendEffect(VoiceEffect.ShowToast(R.string.voice_empty_text))
            return
        }
        stopPlayback()
        sendEffect(VoiceEffect.ShowToast(R.string.voice_confirmed))
        sendEffect(VoiceEffect.Close)
    }

    private fun wavFile(): File = File(File(appContext.cacheDir, "voice"), RECORD_FILE_NAME)

    private fun sendEffect(effect: VoiceEffect) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }

    override fun onCleared() {
        recorder.stop()
        timerJob?.cancel()
        asr?.release()
        asr = null
        val player = mediaPlayer
        mediaPlayer = null
        if (player != null) {
            try {
                player.stop()
            } catch (e: IllegalStateException) {
            }
            player.release()
        }
    }
}
