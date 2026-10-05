package dev.zlddba.moshiapp.engine.cloud

import android.util.Log
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.FunctionCall
import com.aallam.openai.api.chat.Tool
import com.aallam.openai.api.chat.ToolCall
import com.aallam.openai.api.chat.ToolChoice
import com.aallam.openai.api.chat.ToolId
import com.aallam.openai.api.chat.assistantMessage
import com.aallam.openai.api.chat.toolMessage
import com.aallam.openai.api.core.Parameters
import com.aallam.openai.api.exception.OpenAIAPIException
import com.aallam.openai.api.exception.OpenAIException
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.logging.Logger
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import io.ktor.client.plugins.ResponseException
import dev.zlddba.moshiapp.domain.diag.SafeLog
import dev.zlddba.moshiapp.domain.error.STATUS_TIMEOUT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "MoshiCloud"

private const val TIMEOUT_MS = 15_000L
private const val MAX_RETRY = 2
private const val BACKOFF_BASE_MS = 1_000

class OpenAiCloudGateway : CloudGateway {

    override suspend fun testConnection(config: CloudConfig): CloudTestResult {
        Log.d(
            TAG,
            "test start: ${config.baseUrl} model=${config.modelName} key=***${config.apiKey.takeLast(4)}"
        )
        val probe = CloudNetworkProbe.probe(config.baseUrl)
        if (!probe.ok) {
            Log.d(TAG, "test aborted before request: ${probe.detail}")
            return CloudTestResult.Failure(statusCode = probe.statusCode, detail = probe.detail)
        }
        val started = System.currentTimeMillis()
        val openAI = createClient(config)
        return try {
            val completed = withTimeoutOrNull(TIMEOUT_MS) {
                openAI.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId(config.modelName),
                        messages = listOf(ChatMessage.User("ping"))
                    )
                )
                true
            }
            if (completed == null) {
                Log.d(
                    TAG,
                    "test timeout after ${TIMEOUT_MS}ms, request reached server but no response " +
                        "(elapsed=${System.currentTimeMillis() - started}ms)"
                )
                CloudTestResult.Failure(statusCode = STATUS_TIMEOUT, detail = "timeout")
            } else {
                Log.d(TAG, "test success: ${config.baseUrl} model=${config.modelName}")
                CloudTestResult.Success
            }
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
        var attempt = 0
        var lastFailure: CloudAnswer.Failure? = null
        while (attempt <= MAX_RETRY) {
            if (attempt > 0) {
                val backoff = BACKOFF_BASE_MS shl (attempt - 1)
                Log.d(TAG, "ask retry attempt=$attempt backoff=${backoff}ms")
                delay(backoff.toLong())
            }
            var emitted = false
            val result = attemptOnce(config, systemPrompt, userPrompt) { token ->
                emitted = true
                onToken(token)
            }
            when (result) {
                is CloudAnswer.Success -> return result
                is CloudAnswer.Failure -> {
                    lastFailure = result
                    if (emitted || !isRetryable(result.statusCode)) {
                        Log.d(
                            TAG,
                            "ask give up attempt=$attempt emitted=$emitted " +
                                "status=${result.statusCode}"
                        )
                        return result
                    }
                }
            }
            attempt++
        }
        return lastFailure ?: CloudAnswer.Failure(statusCode = 0, detail = "unknown")
    }

    private suspend fun attemptOnce(
        config: CloudConfig,
        systemPrompt: String,
        userPrompt: String,
        onToken: (String) -> Unit
    ): CloudAnswer {
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
            val completed = withTimeoutOrNull(TIMEOUT_MS) {
                openAI.chatCompletions(request).collect { chunk ->
                    val delta = chunk.choices.firstOrNull()?.delta?.content
                    if (!delta.isNullOrEmpty()) {
                        builder.append(delta)
                        onToken(delta)
                    }
                }
                true
            }
            if (completed == null) {
                Log.d(TAG, "ask timeout after ${TIMEOUT_MS}ms len=${builder.length}")
                CloudAnswer.Failure(statusCode = STATUS_TIMEOUT, detail = "timeout")
            } else {
                Log.d(TAG, "ask success len=${builder.length}")
                SafeLog.out(TAG, "cloud.ask.answer=", builder.toString())
                CloudAnswer.Success(builder.toString())
            }
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

    override suspend fun askWithTools(
        config: CloudConfig,
        messages: List<CloudMessage>,
        tools: List<CloudToolSpec>,
        onToken: (String) -> Unit
    ): CloudTurn {
        Log.d(TAG, "askWithTools messages=${messages.size} tools=${tools.size}")
        val declared = tools.map { spec ->
            val parameters = runCatching { Parameters.fromJsonString(spec.parametersJson) }
                .getOrNull() ?: Parameters.Empty
            Tool.function(spec.name, spec.description, parameters)
        }
        var attempt = 0
        var lastFailure: CloudTurn.Failed? = null
        while (attempt <= MAX_RETRY) {
            if (attempt > 0) {
                val backoff = BACKOFF_BASE_MS shl (attempt - 1)
                Log.d(TAG, "tool round retry attempt=$attempt backoff=${backoff}ms")
                delay(backoff.toLong())
            }
            var emitted = false
            val result = attemptWithTools(config, messages, declared) { token ->
                emitted = true
                onToken(token)
            }
            when (result) {
                is CloudTurn.Answered -> return result
                is CloudTurn.NeedTools -> return result
                is CloudTurn.Failed -> {
                    lastFailure = result
                    if (emitted || !isRetryable(result.statusCode)) {
                        Log.d(
                            TAG,
                            "tool round give up attempt=$attempt emitted=$emitted " +
                                "status=${result.statusCode}"
                        )
                        return result
                    }
                }
            }
            attempt++
        }
        return lastFailure ?: CloudTurn.Failed(statusCode = 0, detail = "unknown")
    }

    private suspend fun attemptWithTools(
        config: CloudConfig,
        messages: List<CloudMessage>,
        tools: List<Tool>,
        onToken: (String) -> Unit
    ): CloudTurn {
        val openAI = createClient(config)
        return try {
            val request = ChatCompletionRequest(
                model = ModelId(config.modelName),
                messages = messages.map { it.toChatMessage() },
                tools = tools,
                toolChoice = ToolChoice.Auto
            )
            val text = StringBuilder()
            val slots = LinkedHashMap<Int, ToolCallSlot>()
            val completed = withTimeoutOrNull(TIMEOUT_MS) {
                openAI.chatCompletions(request).collect { chunk ->
                    val delta = chunk.choices.firstOrNull()?.delta ?: return@collect
                    val piece = delta.content
                    if (!piece.isNullOrEmpty()) {
                        text.append(piece)
                        onToken(piece)
                    }
                    delta.toolCalls?.forEach { call ->
                        val slot = slots.getOrPut(call.index) { ToolCallSlot() }
                        call.id?.let { toolId ->
                            if (slot.id.isEmpty()) slot.id = toolId.id
                        }
                        val function = call.function
                        if (function != null) {
                            if (slot.name.isEmpty()) slot.name = function.name
                            slot.arguments.append(function.arguments)
                        }
                    }
                }
                true
            }
            if (completed == null) {
                Log.d(TAG, "tool round timeout after ${TIMEOUT_MS}ms")
                CloudTurn.Failed(statusCode = STATUS_TIMEOUT, detail = "timeout")
            } else if (slots.isNotEmpty()) {
                val calls = slots.values.map {
                    CloudToolCall(
                        id = it.id,
                        name = it.name,
                        arguments = it.arguments.toString()
                    )
                }
                Log.d(TAG, "tool round requested=${calls.map { it.name }} textLen=${text.length}")
                SafeLog.out(TAG, "cloud.round.text=", text.toString())
                calls.forEach { call ->
                    SafeLog.out(TAG, "cloud.round.call=${call.name}", call.arguments, SafeLog.LIMIT_SHORT)
                }
                CloudTurn.NeedTools(calls)
            } else {
                Log.d(TAG, "tool round answered len=${text.length}")
                SafeLog.out(TAG, "cloud.round.answer=", text.toString())
                CloudTurn.Answered(text.toString())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OpenAIAPIException) {
            val detail = e.error.detail?.message ?: e.message ?: ""
            Log.d(TAG, "tool round failed: HTTP ${e.statusCode} $detail")
            CloudTurn.Failed(statusCode = e.statusCode, detail = detail)
        } catch (e: OpenAIException) {
            val status = (e.cause as? ResponseException)?.response?.status?.value ?: 0
            Log.d(TAG, "tool round failed: HTTP $status ${e.message}")
            CloudTurn.Failed(statusCode = status, detail = e.message ?: "")
        } catch (e: Exception) {
            Log.e(TAG, "tool round failed: ${e.message}")
            CloudTurn.Failed(statusCode = 0, detail = e.message ?: "")
        } finally {
            openAI.close()
        }
    }

    private class ToolCallSlot {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }

    private fun CloudMessage.toChatMessage(): ChatMessage = when (this) {
        is CloudMessage.System -> ChatMessage.System(text)
        is CloudMessage.User -> ChatMessage.User(text)
        is CloudMessage.Tool -> toolMessage {
            toolCallId = ToolId(callId)
            content = text
        }

        is CloudMessage.Assistant -> {
            val calls = toolCalls.map {
                ToolCall.Function(ToolId(it.id), FunctionCall(it.name, it.arguments))
            }
            assistantMessage {
                content = text
                toolCalls = calls
            }
        }
    }

    private fun isRetryable(statusCode: Int): Boolean =
        statusCode == 0 || statusCode == STATUS_TIMEOUT || statusCode == 429 || statusCode >= 500

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
