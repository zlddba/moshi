package dev.zlddba.moshiapp.activities.lockPage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.prefs.SecurityPrefs
import dev.zlddba.moshiapp.domain.security.AppLock
import dev.zlddba.moshiapp.domain.security.BiometricUnlock
import dev.zlddba.moshiapp.domain.security.LockCredential
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LockViewModel(context: Context) : ViewModel() {

    enum class Stage {
        Unlock,
        SetupPin,
        ConfirmPin,
        SetupPattern,
        ConfirmPattern
    }

    data class LockUiState(
        val stage: Stage = Stage.Unlock,
        val method: String = SecurityPrefs.METHOD_NONE,
        val input: String = "",
        val pattern: List<Int> = emptyList(),
        val messageRes: Int? = null,
        val error: Boolean = false,
        val biometricAvailable: Boolean = false,
        val biometricEnabled: Boolean = false
    ) {
        val isSetup: Boolean
            get() = stage != Stage.Unlock

        val titleRes: Int
            get() = when (stage) {
                Stage.Unlock -> R.string.lock_title
                Stage.SetupPin -> R.string.lock_set_pin_title
                Stage.ConfirmPin -> R.string.lock_confirm_pin_title
                Stage.SetupPattern -> R.string.lock_set_pattern_title
                Stage.ConfirmPattern -> R.string.lock_confirm_pattern_title
            }

        val usesPin: Boolean
            get() = stage == Stage.SetupPin || stage == Stage.ConfirmPin ||
                (stage == Stage.Unlock && method == SecurityPrefs.METHOD_PIN)

        val showsPattern: Boolean
            get() = stage == Stage.SetupPattern || stage == Stage.ConfirmPattern ||
                (stage == Stage.Unlock && method == SecurityPrefs.METHOD_PATTERN)
    }

    sealed interface LockEvent {
        data class Start(val setupMethod: String?) : LockEvent
        data class Digit(val value: String) : LockEvent
        data object Delete : LockEvent
        data class PatternDrawn(val sequence: List<Int>) : LockEvent
        data object BiometricRequested : LockEvent
        data object BiometricSucceeded : LockEvent
        data object ForgotCredential : LockEvent
    }

    sealed interface LockEffect {
        data object Unlocked : LockEffect
        data object CredentialStored : LockEffect
        data object RequestBiometric : LockEffect
        data object ResetLibrary : LockEffect
        data class ShowToast(val messageRes: Int) : LockEffect
    }

    private val appContext = context.applicationContext
    private val securityPrefs = SecurityPrefs(appContext)

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    private val _effects = Channel<LockEffect>(Channel.BUFFERED)
    val effects: Flow<LockEffect> = _effects.receiveAsFlow()

    private var pendingCredential: String? = null

    fun onEvent(event: LockEvent) {
        when (event) {
            is LockEvent.Start -> start(event.setupMethod)
            is LockEvent.Digit -> appendDigit(event.value)
            LockEvent.Delete -> deleteDigit()
            is LockEvent.PatternDrawn -> submitPattern(event.sequence)
            LockEvent.BiometricRequested -> sendEffect(LockEffect.RequestBiometric)
            LockEvent.BiometricSucceeded -> succeed()
            LockEvent.ForgotCredential -> resetLibrary()
        }
    }

    private fun start(setupMethod: String?) {
        val method = securityPrefs.lockMethod()
        val stage = when (setupMethod) {
            SecurityPrefs.METHOD_PIN -> Stage.SetupPin
            SecurityPrefs.METHOD_PATTERN -> Stage.SetupPattern
            else -> Stage.Unlock
        }
        pendingCredential = null
        val biometricAvailable = BiometricUnlock.isAvailable(appContext)
        val biometricEnabled = securityPrefs.isBiometricEnabled()
        _uiState.update {
            it.copy(
                stage = stage,
                method = method,
                input = "",
                pattern = emptyList(),
                messageRes = null,
                error = false,
                biometricAvailable = biometricAvailable,
                biometricEnabled = biometricEnabled
            )
        }
        if (stage == Stage.Unlock && biometricEnabled && biometricAvailable) {
            sendEffect(LockEffect.RequestBiometric)
        }
    }

    private fun appendDigit(value: String) {
        val state = _uiState.value
        if (!state.usesPin || state.input.length >= PIN_LENGTH) return
        val next = state.input + value
        _uiState.update { it.copy(input = next, messageRes = null, error = false) }
        if (next.length == PIN_LENGTH) submitPin(next)
    }

    private fun deleteDigit() {
        val state = _uiState.value
        if (!state.usesPin || state.input.isEmpty()) return
        _uiState.update {
            it.copy(input = it.input.dropLast(1), messageRes = null, error = false)
        }
    }

    private fun submitPin(pin: String) {
        when (_uiState.value.stage) {
            Stage.Unlock -> {
                if (LockCredential.verify(pin, securityPrefs.pinSalt(), securityPrefs.pinHash())) {
                    succeed()
                } else {
                    fail(R.string.lock_pin_error)
                }
            }

            Stage.SetupPin -> {
                pendingCredential = pin
                _uiState.update {
                    it.copy(stage = Stage.ConfirmPin, input = "", messageRes = null, error = false)
                }
            }

            Stage.ConfirmPin -> {
                if (pin == pendingCredential) {
                    store(SecurityPrefs.METHOD_PIN, pin)
                } else {
                    pendingCredential = null
                    _uiState.update {
                        it.copy(
                            stage = Stage.SetupPin,
                            input = "",
                            messageRes = R.string.lock_mismatch,
                            error = true
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    private fun submitPattern(sequence: List<Int>) {
        val encoded = sequence.joinToString(SEQUENCE_SEPARATOR)
        if (sequence.size < MIN_PATTERN) {
            _uiState.update {
                it.copy(
                    pattern = emptyList(),
                    messageRes = R.string.lock_pattern_too_short,
                    error = true
                )
            }
            return
        }
        when (_uiState.value.stage) {
            Stage.Unlock -> {
                if (LockCredential.verify(
                        encoded,
                        securityPrefs.patternSalt(),
                        securityPrefs.patternHash()
                    )
                ) {
                    succeed()
                } else {
                    fail(R.string.lock_pattern_error)
                }
            }

            Stage.SetupPattern -> {
                pendingCredential = encoded
                _uiState.update {
                    it.copy(
                        stage = Stage.ConfirmPattern,
                        pattern = emptyList(),
                        messageRes = null,
                        error = false
                    )
                }
            }

            Stage.ConfirmPattern -> {
                if (encoded == pendingCredential) {
                    store(SecurityPrefs.METHOD_PATTERN, encoded)
                } else {
                    pendingCredential = null
                    _uiState.update {
                        it.copy(
                            stage = Stage.SetupPattern,
                            pattern = emptyList(),
                            messageRes = R.string.lock_mismatch,
                            error = true
                        )
                    }
                }
            }

            else -> Unit
        }
    }

    private fun store(method: String, credential: String) {
        val salt = LockCredential.newSalt()
        val hash = LockCredential.hash(credential, salt)
        securityPrefs.setLockMethod(method, hash, salt)
        pendingCredential = null
        sendEffect(LockEffect.ShowToast(R.string.lock_credential_saved))
        sendEffect(LockEffect.CredentialStored)
    }

    private fun fail(messageRes: Int) {
        _uiState.update {
            it.copy(input = "", pattern = emptyList(), messageRes = messageRes, error = true)
        }
    }

    private fun succeed() {
        AppLock.markUnlocked()
        sendEffect(LockEffect.Unlocked)
    }

    private fun resetLibrary() {
        sendEffect(LockEffect.ResetLibrary)
    }

    private fun sendEffect(effect: LockEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    companion object {
        const val PIN_LENGTH = 6
        const val MIN_PATTERN = 4
        private const val SEQUENCE_SEPARATOR = "-"
    }
}
