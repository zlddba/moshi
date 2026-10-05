package dev.zlddba.moshiapp.domain.qa

import android.content.Context
import android.util.Log
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.prefs.CloudConfigPrefs
import dev.zlddba.moshiapp.data.prefs.ModelPrefs
import dev.zlddba.moshiapp.domain.error.ErrorCode
import dev.zlddba.moshiapp.domain.retrieve.RetrieveService
import dev.zlddba.moshiapp.engine.cloud.CloudAnswer
import dev.zlddba.moshiapp.engine.cloud.CloudConfig
import dev.zlddba.moshiapp.engine.cloud.CloudGateway
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
        data class Generated(val hits: List<RetrieveService.Hit>) : Outcome
    }

    data class ParsedAnswer(val thinking: String, val answer: String)

    fun parseAnswer(raw: String, fallbackToThinking: Boolean = true): ParsedAnswer {
        val parsed = QaFormat.parse(raw, fallbackToThinking)
        return ParsedAnswer(thinking = parsed.thinking, answer = parsed.answer)
    }

    fun parseDetailed(raw: String): QaFormat.ParsedAnswer = QaFormat.parse(raw)

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
        return try {
            val hits = RetrieveService.retrieve(appContext, question, QA_TOP_K)
            if (hits.isEmpty()) {
                Log.i(TAG, "refuse ${ErrorCode.RETRIEVE_EMPTY.code} question=$question")
                return Outcome.Refusal
            }
            val cloudConfig = CloudConfigPrefs(appContext).load()
            if (shouldUseCloud(cloudConfig, hits)) {
                val answer = withContext(Dispatchers.IO) {
                    cloudGateway.ask(
                        config = cloudConfig,
                        systemPrompt = QaFormat.systemPrompt(QaFormat.Profile.STANDARD),
                        userPrompt = QaFormat.userPrompt(question, hits),
                        onToken = onToken
                    )
                }
                when (answer) {
                    is CloudAnswer.Success -> {
                        Log.i(
                            TAG,
                            "cloud answer len=${answer.raw.length} " +
                                "mode=${cloudConfig.mode} hits=${hits.size}"
                        )
                        return Outcome.Generated(hits)
                    }

                    is CloudAnswer.Failure -> {
                        Log.e(
                            TAG,
                            "cloud failed ${ErrorCode.forCloudStatus(answer.statusCode).code} " +
                                "status=${answer.statusCode} mode=${cloudConfig.mode}"
                        )
                        if (cloudConfig.mode == CloudConfig.MODE_CLOUD) {
                            return Outcome.Excerpt(hits)
                        }
                    }
                }
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
                        onToken = onToken
                    )
                    Outcome.Generated(hits)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "generate oom ${ErrorCode.OOM.code} hits=${hits.size}", e)
                    Outcome.Excerpt(hits)
                } catch (t: Throwable) {
                    Log.e(TAG, "generate failed hits=${hits.size}", t)
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
