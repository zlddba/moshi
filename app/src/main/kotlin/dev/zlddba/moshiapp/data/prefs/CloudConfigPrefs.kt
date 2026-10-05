package dev.zlddba.moshiapp.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.zlddba.moshiapp.domain.security.CryptoManager
import dev.zlddba.moshiapp.engine.cloud.CloudConfig

class CloudConfigPrefs(context: Context) {

    private val appContext = context.applicationContext

    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): CloudConfig = CloudConfig(
        baseUrl = preferences.getString(KEY_BASE_URL, "").orEmpty(),
        apiKey = CryptoManager
            .decryptText(appContext, preferences.getString(KEY_API_KEY, "").orEmpty())
            .orEmpty(),
        modelName = preferences.getString(KEY_MODEL_NAME, "").orEmpty(),
        forceLocal = preferences.getBoolean(KEY_FORCE_LOCAL, true),
        mode = preferences.getInt(KEY_MODE, CloudConfig.MODE_LOCAL)
    )

    fun save(config: CloudConfig) {
        val storedKey = CryptoManager.encryptText(appContext, config.apiKey) ?: config.apiKey
        preferences.edit {
            putString(KEY_BASE_URL, config.baseUrl)
            putString(KEY_API_KEY, storedKey)
            putString(KEY_MODEL_NAME, config.modelName)
            putBoolean(KEY_FORCE_LOCAL, config.forceLocal)
            putInt(KEY_MODE, config.mode)
        }
    }

    private companion object {
        const val PREFS_NAME = "moshi_cloud_config"
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_FORCE_LOCAL = "force_local"
        const val KEY_MODE = "engine_mode"
    }
}
