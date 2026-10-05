package dev.zlddba.moshiapp.domain.qa

import android.content.Context
import android.util.Log
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.prefs.CloudConfigPrefs
import dev.zlddba.moshiapp.data.prefs.ModelPrefs
import dev.zlddba.moshiapp.domain.diag.SafeLog
import dev.zlddba.moshiapp.domain.error.ErrorCode
import dev.zlddba.moshiapp.domain.retrieve.QueryExpansion
import dev.zlddba.moshiapp.domain.retrieve.RetrieveService
import dev.zlddba.moshiapp.domain.tool.KnowledgeTools
import dev.zlddba.moshiapp.engine.cloud.CloudConfig
import dev.zlddba.moshiapp.engine.cloud.CloudGateway
import dev.zlddba.moshiapp.engine.cloud.CloudMessage
import dev.zlddba.moshiapp.engine.cloud.CloudToolSpec
import dev.zlddba.moshiapp.engine.cloud.CloudTurn
import dev.zlddba.moshiapp.engine.cloud.OpenAiCloudGateway
import dev.zlddba.moshiapp.engine.local.GenerationOptions
import dev.zlddba.moshiapp.engine.local.LiteRtLlmEngine
import dev.zlddba.moshiapp.ingest.models.ModelFileManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object QaOrchestrator {

    const val QA_TOP_K = 5

    private const val TAG = "QaOrchestrator"

    private val cloudGateway: CloudGateway = OpenAiCloudGateway()

    sealed interface Outcome {
        data object Refusal : Outcome
        data class ModelMissing(val hits: List<RetrieveService.Hit>) : Outcome
        data class Excerpt(val hits: List<RetrieveService.Hit>) : Outcome
        data class CloudFailed(
            val statusCode: Int,
            val hits: List<RetrieveService.Hit>
        ) : Outcome
        data class Generated(val hits: List<RetrieveService.Hit>) : Outcome
    }

    data class ParsedAnswer(val thinking: String, val answer: String)

    fun parseAnswer(raw: String, fallbackToThinking: Boolean = true): ParsedAnswer {
        val parsed = QaFormat.parse(raw, fallbackToThinking)
        return ParsedAnswer(thinking = parsed.thinking, answer = parsed.answer)
    }

    fun parseDetailed(raw: String): QaFormat.ParsedAnswer = QaFormat.parse(raw)

    private suspend fun askCloudWithTools(
        appContext: Context,
        config: CloudConfig,
        question: String,
        hits: List<RetrieveService.Hit>,
        trace: StringBuilder,
        onToken: (String) -> Unit
    ): Outcome? {
        val specs = KnowledgeTools.SPECS.map {
            CloudToolSpec(
                name = it.name,
                description = it.description,
                parametersJson = it.parametersJson
            )
        }
        val systemPrompt = QaFormat.toolSystemPrompt()
        val userPrompt = QaFormat.userPrompt(question, hits)
        SafeLog.out(TAG, "cloud.system=", systemPrompt)
        SafeLog.out(TAG, "cloud.user=", userPrompt, SafeLog.LIMIT_LONG)
        val messages = ArrayList<CloudMessage>()
        messages.add(CloudMessage.System(systemPrompt))
        messages.add(CloudMessage.User(userPrompt))
        var collected = hits
        for (round in 0..KnowledgeTools.MAX_ROUNDS) {
            val tools = if (round < KnowledgeTools.MAX_ROUNDS) specs else emptyList()
            val turn = withContext(Dispatchers.IO) {
                cloudGateway.askWithTools(
                    config = config,
                    messages = messages,
                    tools = tools,
                    onToken = onToken
                )
            }
            when (turn) {
                is CloudTurn.Answered -> {
                    Log.i(
                        TAG,
                        "cloud answer round=$round len=${turn.text.length} " +
                            "mode=${config.mode} hits=${collected.size}"
                    )
                    logOutput("cloud", trace)
                    return Outcome.Generated(collected)
                }

                is CloudTurn.NeedTools -> {
                    Log.i(TAG, "cloud tool round=$round calls=${turn.calls.map { it.name }}")
                    turn.calls.forEach { call ->
                        SafeLog.out(
                            TAG,
                            "tool.call=${call.name}",
                            call.arguments,
                            SafeLog.LIMIT_SHORT
                        )
                    }
                    messages.add(CloudMessage.Assistant("", turn.calls))
                    val extra = ArrayList<RetrieveService.Hit>()
                    for (call in turn.calls) {
                        val outcome = withContext(Dispatchers.IO) {
                            KnowledgeTools.execute(
                                context = appContext,
                                name = call.name,
                                argumentsJson = call.arguments,
                                allowSensitive = false
                            )
                        }
                        SafeLog.out(
                            TAG,
                            "tool.result=${call.name}",
                            outcome.text,
                            SafeLog.LIMIT_LONG
                        )
                        messages.add(CloudMessage.Tool(call.id, outcome.text))
                        extra.addAll(outcome.hits)
                    }
                    if (extra.isNotEmpty()) {
                        collected = (collected + extra)
                            .distinctBy { it.chunkId }
                            .sortedByDescending { it.score }
                            .take(QA_TOP_K * 2)
                    }
                }

                is CloudTurn.Failed -> {
                    Log.e(
                        TAG,
                        "cloud failed ${ErrorCode.forCloudStatus(turn.statusCode).code} " +
                            "status=${turn.statusCode} mode=${config.mode}"
                    )
                    logOutput("cloud-failed", trace)
                    if (config.mode == CloudConfig.MODE_CLOUD) {
                        return Outcome.CloudFailed(turn.statusCode, collected)
                    }
                    return null
                }
            }
        }
        Log.w(TAG, "tool rounds exhausted without answer")
        logOutput("cloud-exhausted", trace)
        return null
    }

    private fun logOutput(stage: String, trace: StringBuilder) {
        if (!SafeLog.enabled()) return
        val raw = trace.toString()
        val parsed = QaFormat.parse(raw)
        Log.i(
            TAG,
            "output stage=$stage rawLen=${raw.length} refused=${parsed.refused} " +
                "thinkingLen=${parsed.thinking.length} answerLen=${parsed.answer.length}"
        )
        SafeLog.out(TAG, "output.thinking=", parsed.thinking)
        SafeLog.out(TAG, "output.answer=", parsed.answer)
        SafeLog.out(TAG, "output.raw=", raw, SafeLog.LIMIT_LONG)
    }

    internal fun shouldUseCloud(config: CloudConfig, hits: List<RetrieveService.Hit>): Boolean {
        if (!config.usesCloud() || !config.isComplete()) return false
        if (config.forceLocal && hits.any { it.isSensitive }) return false
        return true
    }

    fun localOptions(context: Context): QaFormat.Options =
        QaFormat.options(QaFormat.of(ModelPrefs(context.applicationContext).currentLlm()))

    suspend fun ask(
        context: Context,
        question: String,
        onToken: (String) -> Unit
    ): Outcome {
        val appContext = context.applicationContext
        val trace = StringBuilder()
        val traced: (String) -> Unit = { token ->
            trace.append(token)
            onToken(token)
        }
        return try {
            val hits = QueryExpansion.retrieveExpanded(appContext, question, QA_TOP_K)
            if (hits.isEmpty()) {
                Log.i(TAG, "refuse ${ErrorCode.RETRIEVE_EMPTY.code} question=$question")
                return Outcome.Refusal
            }
            val cloudConfig = CloudConfigPrefs(appContext).load()
            if (shouldUseCloud(cloudConfig, hits)) {
                askCloudWithTools(appContext, cloudConfig, question, hits, trace, traced)
                    ?.let { return it }
            }
            val modelId = ModelPrefs(appContext).currentLlm()
            if (!ModelFileManager.isReady(appContext, modelId)) {
                return Outcome.ModelMissing(hits)
            }
            val profile = QaFormat.of(modelId)
            val options = QaFormat.options(profile)
            val prompt = QaFormat.userPrompt(question, hits)
            Log.i(
                TAG,
                "local generate model=$modelId profile=$profile temp=${options.temperature} " +
                    "topK=${options.topK} maxTokens=${options.maxOutputTokens} " +
                    "constrained=${options.constraint != null} hits=${hits.size}"
            )
            SafeLog.out(TAG, "local.system=", options.systemPrompt)
            SafeLog.out(TAG, "local.user=", prompt, SafeLog.LIMIT_LONG)
            withContext(Dispatchers.IO) {
                try {
                    LiteRtLlmEngine.generate(
                        context = appContext,
                        prompt = prompt,
                        options = GenerationOptions(
                            systemPrompt = options.systemPrompt,
                            temperature = options.temperature,
                            topK = options.topK,
                            topP = options.topP,
                            maxOutputTokens = options.maxOutputTokens,
                            constraint = options.constraint,
                            thinkingTokenBudget = options.thinkingTokenBudget
                        ),
                        onToken = traced
                    )
                    logOutput("local", trace)
                    Outcome.Generated(hits)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "generate oom ${ErrorCode.OOM.code} hits=${hits.size}", e)
                    logOutput("local-oom", trace)
                    Outcome.Excerpt(hits)
                } catch (t: Throwable) {
                    Log.e(TAG, "generate failed hits=${hits.size}", t)
                    logOutput("local-failed", trace)
                    Outcome.Excerpt(hits)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "ask failed", t)
            Outcome.Excerpt(emptyList())
        }
    }

    fun excerptText(context: Context, hits: List<RetrieveService.Hit>): String {
        val builder = StringBuilder(context.getString(R.string.chat_excerpt_label))
        for (hit in hits) {
            builder.append("\n\n【")
            builder.append(hit.noteTitle)
            builder.append("】")
            builder.append(hit.text.take(QaFormat.MAX_SNIPPET_CHARS))
        }
        return builder.toString()
    }
}
