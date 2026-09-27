package dev.zlddba.moshiapp.engine.cloud

data class CloudConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val modelName: String = "",
    val forceLocal: Boolean = true
)

sealed interface CloudTestResult {
    data object Success : CloudTestResult
    data class Failure(val statusCode: Int = 0, val detail: String = "") : CloudTestResult
}

interface CloudGateway {
    suspend fun testConnection(config: CloudConfig): CloudTestResult
}
