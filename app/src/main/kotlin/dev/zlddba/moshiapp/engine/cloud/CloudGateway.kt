package dev.zlddba.moshiapp.engine.cloud

data class CloudConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val forceLocal: Boolean = true,
    val mode: Int = MODE_LOCAL
) {
    fun usesCloud(): Boolean = mode != MODE_LOCAL

    fun isComplete(): Boolean =
        baseUrl.startsWith("http") &&
            apiKey.isNotBlank() &&
            modelName.isNotBlank()

    companion object {
        const val MODE_LOCAL = 0
        const val MODE_HYBRID = 1
        const val MODE_CLOUD = 2
    }
}

sealed interface CloudTestResult {
    data object Success : CloudTestResult
    data class Failure(val statusCode: Int = 0, val detail: String = "") : CloudTestResult
}

sealed interface CloudAnswer {
    data class Success(val raw: String) : CloudAnswer
    data class Failure(val statusCode: Int = 0, val detail: String = "") : CloudAnswer
}

interface CloudGateway {
    suspend fun testConnection(config: CloudConfig): CloudTestResult

    suspend fun ask(
        config: CloudConfig,
        systemPrompt: String,
        userPrompt: String,
        onToken: (String) -> Unit
    ): CloudAnswer

    suspend fun askWithTools(
        config: CloudConfig,
        messages: List<CloudMessage>,
        tools: List<CloudToolSpec>,
        onToken: (String) -> Unit
    ): CloudTurn
}
