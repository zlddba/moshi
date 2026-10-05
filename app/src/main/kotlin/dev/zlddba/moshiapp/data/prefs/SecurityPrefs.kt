package dev.zlddba.moshiapp.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SecurityPrefs(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLockEnabled(): Boolean = preferences.getBoolean(KEY_LOCK_ENABLED, false)

    fun lockMethod(): String = preferences.getString(KEY_LOCK_METHOD, METHOD_NONE) ?: METHOD_NONE

    fun pinHash(): String? = preferences.getString(KEY_PIN_HASH, null)

    fun pinSalt(): String? = preferences.getString(KEY_PIN_SALT, null)

    fun patternHash(): String? = preferences.getString(KEY_PATTERN_HASH, null)

    fun patternSalt(): String? = preferences.getString(KEY_PATTERN_SALT, null)

    fun isBiometricEnabled(): Boolean = preferences.getBoolean(KEY_BIOMETRIC, false)

    fun setLockMethod(method: String, hash: String?, salt: String?) {
        preferences.edit {
            putString(KEY_LOCK_METHOD, method)
            when (method) {
                METHOD_PIN -> {
                    putString(KEY_PIN_HASH, hash)
                    putString(KEY_PIN_SALT, salt)
                }

                METHOD_PATTERN -> {
                    putString(KEY_PATTERN_HASH, hash)
                    putString(KEY_PATTERN_SALT, salt)
                }
            }
            putBoolean(KEY_LOCK_ENABLED, method != METHOD_NONE)
            if (method == METHOD_NONE) {
                putBoolean(KEY_BIOMETRIC, false)
            }
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_BIOMETRIC, enabled) }
    }

    fun isAutoLockEnabled(): Boolean = preferences.getBoolean(KEY_AUTO_LOCK, true)

    fun setAutoLockEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_AUTO_LOCK, enabled) }
    }

    fun autoLockDelaySeconds(): Int = preferences.getInt(KEY_AUTO_LOCK_DELAY, DEFAULT_DELAY_SECONDS)

    fun setAutoLockDelaySeconds(seconds: Int) {
        preferences.edit { putInt(KEY_AUTO_LOCK_DELAY, seconds) }
    }

    fun isEncryptionEnabled(): Boolean = preferences.getBoolean(KEY_ENCRYPTION, false)

    fun setEncryptionEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_ENCRYPTION, enabled) }
    }

    fun isQaHistoryEnabled(): Boolean = preferences.getBoolean(KEY_QA_HISTORY, true)

    fun setQaHistoryEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_QA_HISTORY, enabled) }
    }

    fun wrappedDek(): String? = preferences.getString(KEY_WRAPPED_DEK, null)

    fun setWrappedDek(value: String?) {
        preferences.edit { putString(KEY_WRAPPED_DEK, value) }
    }

    companion object {
        const val METHOD_NONE = "NONE"
        const val METHOD_PIN = "PIN"
        const val METHOD_PATTERN = "PATTERN"
        const val DEFAULT_DELAY_SECONDS = 30

        private const val PREFS_NAME = "moshi_security"
        private const val KEY_LOCK_ENABLED = "lock_enabled"
        private const val KEY_LOCK_METHOD = "lock_method"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_PATTERN_HASH = "pattern_hash"
        private const val KEY_PATTERN_SALT = "pattern_salt"
        private const val KEY_BIOMETRIC = "biometric_enabled"
        private const val KEY_AUTO_LOCK = "auto_lock_enabled"
        private const val KEY_AUTO_LOCK_DELAY = "auto_lock_delay"
        private const val KEY_ENCRYPTION = "encryption_enabled"
        private const val KEY_QA_HISTORY = "qa_history_enabled"
        private const val KEY_WRAPPED_DEK = "wrapped_dek"
    }
}
