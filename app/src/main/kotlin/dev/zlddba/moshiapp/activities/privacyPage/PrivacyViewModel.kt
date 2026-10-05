package dev.zlddba.moshiapp.activities.privacyPage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.db.DatabaseCipher
import dev.zlddba.moshiapp.data.db.KeywordIndex
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.prefs.CloudConfigPrefs
import dev.zlddba.moshiapp.data.prefs.SecurityPrefs
import dev.zlddba.moshiapp.data.vector.VectorStoreClient
import dev.zlddba.moshiapp.domain.index.IndexOrchestrator
import dev.zlddba.moshiapp.domain.security.AppLock
import dev.zlddba.moshiapp.domain.security.BiometricUnlock
import dev.zlddba.moshiapp.domain.security.CryptoManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrivacyViewModel(context: Context) : ViewModel() {

    data class PrivacyUiState(
        val encryptionEnabled: Boolean = false,
        val forceLocal: Boolean = true,
        val qaHistoryEnabled: Boolean = true,
        val busy: Boolean = false,
        val storageEncrypted: Boolean = false,
        val lockMethod: String = SecurityPrefs.METHOD_NONE,
        val biometricEnabled: Boolean = false,
        val biometricAvailable: Boolean = false,
        val autoLockEnabled: Boolean = true
    ) {
        val lockStateRes: Int
            get() = when (lockMethod) {
                SecurityPrefs.METHOD_PIN -> R.string.privacy_lock_state_pin
                SecurityPrefs.METHOD_PATTERN -> R.string.privacy_lock_state_pattern
                else -> R.string.privacy_lock_state_none
            }

        val lockConfigured: Boolean
            get() = lockMethod != SecurityPrefs.METHOD_NONE
    }

    sealed interface PrivacyEvent {
        data object Reload : PrivacyEvent
        data class EncryptionChanged(val enabled: Boolean) : PrivacyEvent
        data class ForceLocalChanged(val enabled: Boolean) : PrivacyEvent
        data class QaHistoryChanged(val enabled: Boolean) : PrivacyEvent
        data object SetLockPin : PrivacyEvent
        data object SetLockPattern : PrivacyEvent
        data object DisableLock : PrivacyEvent
        data class BiometricChanged(val enabled: Boolean) : PrivacyEvent
        data class AutoLockChanged(val enabled: Boolean) : PrivacyEvent
    }

    sealed interface PrivacyEffect {
        data class ShowToast(val messageRes: Int) : PrivacyEffect
        data class OpenLockSetup(val method: String) : PrivacyEffect
    }

    private val appContext = context.applicationContext
    private val securityPrefs = SecurityPrefs(appContext)
    private val cloudPrefs = CloudConfigPrefs(appContext)

    private val _uiState = MutableStateFlow(PrivacyUiState())
    val uiState: StateFlow<PrivacyUiState> = _uiState.asStateFlow()

    private val _effects = Channel<PrivacyEffect>(Channel.BUFFERED)
    val effects: Flow<PrivacyEffect> = _effects.receiveAsFlow()

    fun onEvent(event: PrivacyEvent) {
        when (event) {
            PrivacyEvent.Reload -> reload()
            is PrivacyEvent.EncryptionChanged -> setEncryption(event.enabled)
            is PrivacyEvent.ForceLocalChanged -> setForceLocal(event.enabled)
            is PrivacyEvent.QaHistoryChanged -> setQaHistory(event.enabled)
            PrivacyEvent.SetLockPin ->
                sendEffect(PrivacyEffect.OpenLockSetup(SecurityPrefs.METHOD_PIN))

            PrivacyEvent.SetLockPattern ->
                sendEffect(PrivacyEffect.OpenLockSetup(SecurityPrefs.METHOD_PATTERN))

            PrivacyEvent.DisableLock -> disableLock()
            is PrivacyEvent.BiometricChanged -> setBiometric(event.enabled)
            is PrivacyEvent.AutoLockChanged -> setAutoLock(event.enabled)
        }
    }

    fun reload() {
        val config = cloudPrefs.load()
        _uiState.update {
            it.copy(
                encryptionEnabled = securityPrefs.isEncryptionEnabled(),
                forceLocal = config.forceLocal,
                qaHistoryEnabled = securityPrefs.isQaHistoryEnabled(),
                storageEncrypted = storageEncrypted(),
                lockMethod = securityPrefs.lockMethod(),
                biometricEnabled = securityPrefs.isBiometricEnabled(),
                biometricAvailable = BiometricUnlock.isAvailable(appContext),
                autoLockEnabled = securityPrefs.isAutoLockEnabled()
            )
        }
    }

    private fun storageEncrypted(): Boolean =
        DatabaseCipher.isEncryptedOnDisk(appContext.getDatabasePath(STORAGE_NAME)) ||
            DatabaseCipher.isEncryptedOnDisk(keywordsFile())

    private fun setForceLocal(enabled: Boolean) {
        cloudPrefs.save(cloudPrefs.load().copy(forceLocal = enabled))
        _uiState.update { it.copy(forceLocal = enabled) }
    }

    private fun setQaHistory(enabled: Boolean) {
        securityPrefs.setQaHistoryEnabled(enabled)
        _uiState.update { it.copy(qaHistoryEnabled = enabled) }
    }

    private fun disableLock() {
        securityPrefs.setLockMethod(SecurityPrefs.METHOD_NONE, null, null)
        securityPrefs.setBiometricEnabled(false)
        AppLock.markUnlocked()
        reload()
        sendEffect(PrivacyEffect.ShowToast(R.string.privacy_lock_disabled))
    }

    private fun setBiometric(enabled: Boolean) {
        val state = _uiState.value
        when {
            enabled && !state.lockConfigured ->
                sendEffect(PrivacyEffect.ShowToast(R.string.privacy_biometric_need_lock))

            enabled && !state.biometricAvailable ->
                sendEffect(PrivacyEffect.ShowToast(R.string.privacy_biometric_unavailable))

            else -> {
                securityPrefs.setBiometricEnabled(enabled)
                reload()
            }
        }
    }

    private fun setAutoLock(enabled: Boolean) {
        securityPrefs.setAutoLockEnabled(enabled)
        reload()
    }

    private fun setEncryption(enabled: Boolean) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { applyEncryption(enabled) }
            _uiState.update { it.copy(busy = false) }
            reload()
            sendEffect(
                PrivacyEffect.ShowToast(
                    when {
                        !ok -> R.string.privacy_encrypt_failed
                        enabled -> R.string.privacy_encrypt_on
                        else -> R.string.privacy_encrypt_off
                    }
                )
            )
        }
    }

    private suspend fun applyEncryption(enabled: Boolean): Boolean {
        MoshiDatabase.reset()
        KeywordIndex.reset()
        val passphrase = CryptoManager.databasePassphrase(appContext) ?: return false
        if (!DatabaseCipher.prepare(appContext.getDatabasePath(STORAGE_NAME), enabled, passphrase)) {
            return false
        }
        if (!DatabaseCipher.prepare(keywordsFile(), enabled, passphrase)) {
            return false
        }
        securityPrefs.setEncryptionEnabled(enabled)
        if (enabled) purgeVectorText()
        return true
    }

    private suspend fun purgeVectorText() {
        VectorStoreClient.clearAll(appContext)
        MoshiDatabase.get(appContext).chunkDao().clearEmbeddings()
        IndexOrchestrator.requestSweep()
    }

    private fun keywordsFile(): File =
        File(File(appContext.filesDir, KEYWORDS_DIR), KEYWORDS_NAME)

    private fun sendEffect(effect: PrivacyEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val STORAGE_NAME = "moshi.db"
        const val KEYWORDS_DIR = "keywords"
        const val KEYWORDS_NAME = "keywords.db"
    }
}
