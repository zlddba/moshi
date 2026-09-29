package dev.zlddba.moshiapp.engine.cloud

import android.util.Log
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.exception.OpenAIAPIException
import com.aallam.openai.api.exception.OpenAIException
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.logging.Logger
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException

private const val TAG = "MoshiCloud"

class OpenAiCloudGateway : CloudGateway {

    override suspend fun testConnection(config: CloudConfig): CloudTestResult {
        Log.d(
            TAG,
            "test start: ${config.baseUrl} model=${config.modelName} key=***${config.apiKey.takeLast(4)}"
        )
        val openAI = createClient(config)
        return try {
            openAI.chatCompletion(
                ChatCompletionRequest(
                    model = ModelId(config.modelName),
                    messages = listOf(ChatMessage.User("ping"))
                )
            )
            Log.d(TAG, "test success: ${config.baseUrl} model=${config.modelName}")
            CloudTestResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: OpenAIAPIException) {
            val detail = e.error.detail?.message ?: e.message ?: ""
            Log.d(TAG, "test failed: HTTP ${e.statusCode} $detail")
            CloudTestResult.Failure(statusCode = e.statusCode, detail = detail)
        } catch (e: OpenAIException) {
            val status = (e.cause as? ResponseException)?.response?.status?.value ?: 0
            Log.d(TAG, "test failed: HTTP $status ${e.message}")
            CloudTestResult.Failure(statusCode = status, detail = e.message ?: "")
        } catch (e: Exception) {
            Log.d(TAG, "test failed: ${e.message}")
            CloudTestResult.Failure(statusCode = 0, detail = e.message ?: "")
        } finally {
            openAI.close()
        }
    }

    override suspend fun ask(
        config: CloudConfig,
        systemPrompt: String,
        userPrompt: String,
        onToken: (String) -> Unit
    ): CloudAnswer {
        Log.d(
            TAG,
            "ask start: ${config.baseUrl} model=${config.modelName} " +
                "userLen=${userPrompt.length}"
        )
        val openAI = createClient(config)
        return try {
            val request = ChatCompletionRequest(
                model = ModelId(config.modelName),
                messages = listOf(
                    ChatMessage.System(systemPrompt),
                    ChatMessage.User(userPrompt)
                )
            )
            val builder = StringBuilder()
            openAI.chatCompletions(request).collect { chunk ->
                val delta = chunk.choices.firstOrNull()?.delta?.content
                if (!delta.isNullOrEmpty()) {
                    builder.append(delta)
                    onToken(delta)
                }
            }
            Log.d(TAG, "ask success len=${builder.length}")
            CloudAnswer.Success(builder.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: OpenAIAPIException) {
            val detail = e.error.detail?.message ?: e.message ?: ""
            Log.d(TAG, "ask failed: HTTP ${e.statusCode} $detail")
            CloudAnswer.Failure(statusCode = e.statusCode, detail = detail)
        } catch (e: OpenAIException) {
            val status = (e.cause as? ResponseException)?.response?.status?.value ?: 0
            Log.d(TAG, "ask failed: HTTP $status ${e.message}")
            CloudAnswer.Failure(statusCode = status, detail = e.message ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "ask failed: ${e.message}")
            CloudAnswer.Failure(statusCode = 0, detail = e.message ?: "")
        } finally {
            openAI.close()
        }
    }

    private fun createClient(config: CloudConfig): OpenAI {
        val baseUrl = if (config.baseUrl.endsWith("/")) config.baseUrl else "${config.baseUrl}/"
        return OpenAI(
            token = config.apiKey,
            host = OpenAIHost(baseUrl = baseUrl),
            logging = LoggingConfig(
                logLevel = LogLevel.Body,
                logger = Logger.Simple,
                sanitize = true
            )
        )
    }
}
