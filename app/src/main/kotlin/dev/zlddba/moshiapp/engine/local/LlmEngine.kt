package dev.zlddba.moshiapp.engine.local

import android.content.Context

interface LlmEngine {

    suspend fun generate(
        context: Context,
        prompt: String,
        options: GenerationOptions,
        onToken: (String) -> Unit
    )

    fun stop()
}

data class GenerationOptions(
    val systemPrompt: String? = null,
    val temperature: Double? = null,
    val topK: Int? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null,
    val constraint: String? = null,
    val thinkingTokenBudget: Int? = null,
    val seed: Int = DEFAULT_SEED
) {
    companion object {
        const val DEFAULT_SEED = 0
    }
}
