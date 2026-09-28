package dev.zlddba.moshiapp.engine.local

import android.content.Context

interface LlmEngine {

    suspend fun generate(context: Context, prompt: String, onToken: (String) -> Unit)

    fun stop()
}
