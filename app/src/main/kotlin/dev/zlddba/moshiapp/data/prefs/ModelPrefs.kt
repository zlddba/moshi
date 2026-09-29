package dev.zlddba.moshiapp.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.zlddba.moshiapp.ingest.models.ModelCatalog

class ModelPrefs(context: Context) {

    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun currentLlm(): String =
        preferences.getString(KEY_CURRENT_LLM, ModelCatalog.GEMMA) ?: ModelCatalog.GEMMA

    fun setCurrentLlm(id: String) {
        preferences.edit { putString(KEY_CURRENT_LLM, id) }
    }

    fun currentBackend(): String =
        preferences.getString(KEY_BACKEND, KEY_BACKEND_CPU) ?: KEY_BACKEND_CPU

    fun setCurrentBackend(kind: String) {
        preferences.edit { putString(KEY_BACKEND, kind) }
    }

    private companion object {
        const val PREFS_NAME = "moshi_models"
        const val KEY_CURRENT_LLM = "current_llm"
        const val KEY_BACKEND = "llm_backend"
        const val KEY_BACKEND_CPU = "CPU"
    }
}
