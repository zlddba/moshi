package dev.zlddba.moshiapp.engine.local

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import dev.zlddba.moshiapp.data.prefs.ModelPrefs
import dev.zlddba.moshiapp.ingest.models.ModelCatalog
import dev.zlddba.moshiapp.ingest.models.ModelFileManager

object LiteRtLlmEngine : LlmEngine {

    private const val TAG = "LiteRtLlm"

    private const val DEFAULT_TOP_K = 40
    private const val DEFAULT_TOP_P = 0.95
    private const val DEFAULT_TEMPERATURE = 0.3

    private val lock = Any()

    @Volatile
    private var engine: Engine? = null

    @Volatile
    private var loadedModelId: String? = null

    @Volatile
    private var conversation: Conversation? = null

    @Volatile
    private var backendKind: BackendKind = BackendKind.CPU

    fun currentBackend(): BackendKind = backendKind

    fun setBackend(kind: BackendKind) {
        synchronized(lock) {
            if (kind == backendKind) return
            backendKind = kind
            releaseLocked()
        }
    }

    fun warmUp(context: Context) {
        ensureEngine(context.applicationContext)
    }

    override suspend fun generate(
        context: Context,
        prompt: String,
        options: GenerationOptions,
        onToken: (String) -> Unit
    ) {
        val appContext = context.applicationContext
        val active = ensureEngine(appContext)
            ?: throw IllegalStateException("llm engine unavailable")
        val convo = freshConversation(active, options)
        Log.i(
            TAG,
            "generate start convo=${System.identityHashCode(convo)} alive=${convo.isAlive} " +
                "engine=${System.identityHashCode(active)} promptLen=${prompt.length} " +
                "constrained=${options.constraint != null} maxTokens=${options.maxOutputTokens}"
        )
        try {
            convo.sendMessageAsync(
                prompt,
                emptyMap<String, Any>(),
                null,
                null,
                null,
                null,
                null,
                options.constraint?.let { ResponseFormat.regex(it) }
            ).collect { message ->
                val delta = message.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "") { it.text }
                if (delta.isNotEmpty()) onToken(delta)
            }
            Log.i(TAG, "generate done convo=${System.identityHashCode(convo)}")
        } catch (t: Throwable) {
            Log.w(
                TAG,
                "generate error convo=${System.identityHashCode(convo)} alive=${convo.isAlive}",
                t
            )
            throw t
        }
    }

    override fun stop() {
        try {
            conversation?.cancelProcess()
        } catch (e: Exception) {
            Log.w(TAG, "cancel failed", e)
        }
    }

    fun resetConversation() {
        synchronized(lock) {
            closeConversationLocked()
        }
    }

    private fun ensureEngine(appContext: Context): Engine? {
        val wanted = ModelPrefs(appContext).currentLlm()
        engine?.let { current ->
            if (loadedModelId == wanted) return current
        }
        synchronized(lock) {
            engine?.let { current ->
                if (loadedModelId == wanted) return current
            }
            if (engine != null) {
                Log.i(TAG, "model changed $loadedModelId -> $wanted, rebuilding engine")
            }
            releaseLocked()
            val kind = backendKind
            if (!ModelFileManager.isReady(appContext, wanted)) return null
            val modelFile = ModelCatalog.descriptor(wanted).files.firstOrNull() ?: return null
            val modelPath = ModelFileManager
                .file(appContext, wanted, modelFile.name)
                .absolutePath
            return try {
                val created = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = backendOf(appContext, kind)
                    )
                )
                created.initialize()
                engine = created
                loadedModelId = wanted
                Log.i(TAG, "engine ready backend=${kind.name} model=$wanted")
                created
            } catch (t: Throwable) {
                Log.w(TAG, "engine init failed backend=${kind.name}", t)
                releaseLocked()
                if (backendKind != BackendKind.CPU) {
                    backendKind = BackendKind.CPU
                    return ensureEngine(appContext)
                }
                null
            }
        }
    }

    private fun freshConversation(active: Engine, options: GenerationOptions): Conversation {
        synchronized(lock) {
            closeConversationLocked()
            val created = try {
                active.createConversation(buildConfig(options))
            } catch (t: Throwable) {
                if (options.constraint == null) throw t
                Log.w(TAG, "constrained conversation failed, retry without constraint", t)
                active.createConversation(buildConfig(options.copy(constraint = null)))
            }
            conversation = created
            Log.i(TAG, "conversation recreated id=${System.identityHashCode(created)}")
            return created
        }
    }

    private fun buildConfig(options: GenerationOptions): ConversationConfig = ConversationConfig(
        systemInstruction = options.systemPrompt?.let { Contents.of(it) },
        samplerConfig = SamplerConfig(
            options.topK ?: DEFAULT_TOP_K,
            options.topP ?: DEFAULT_TOP_P,
            options.temperature ?: DEFAULT_TEMPERATURE,
            options.seed
        ),
        maxOutputToken = options.maxOutputTokens,
        thinkingConfig = options.thinkingTokenBudget?.let { ThinkingConfig(true, it) },
        enableResponseFormat = options.constraint != null
    )

    private fun backendOf(context: Context, kind: BackendKind): Backend = when (kind) {
        BackendKind.CPU -> Backend.CPU()
        BackendKind.GPU -> Backend.GPU()
        BackendKind.NPU -> Backend.NPU(context.applicationInfo.nativeLibraryDir)
    }

    private fun releaseLocked() {
        closeConversationLocked()
        engine?.let { current ->
            try {
                current.close()
            } catch (e: Exception) {
                Log.w(TAG, "engine close failed", e)
            }
        }
        engine = null
        loadedModelId = null
    }

    private fun closeConversationLocked() {
        conversation?.let { current ->
            try {
                current.close()
            } catch (e: Exception) {
                Log.w(TAG, "conversation close failed", e)
            }
        }
        conversation = null
    }
}
