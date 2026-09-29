package dev.zlddba.moshiapp.engine.local

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import dev.zlddba.moshiapp.data.prefs.ModelPrefs
import dev.zlddba.moshiapp.ingest.models.ModelCatalog
import dev.zlddba.moshiapp.ingest.models.ModelFileManager

object LiteRtLlmEngine : LlmEngine {

    private const val TAG = "LiteRtLlm"

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
        onToken: (String) -> Unit
    ) {
        val appContext = context.applicationContext
        val active = ensureEngine(appContext)
            ?: throw IllegalStateException("llm engine unavailable")
        val convo = freshConversation(active)
        Log.i(
            TAG,
            "generate start convo=${System.identityHashCode(convo)} alive=${convo.isAlive} " +
                "engine=${System.identityHashCode(active)} promptLen=${prompt.length}"
        )
        try {
            convo.sendMessageAsync(prompt).collect { message ->
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

    private fun freshConversation(active: Engine): Conversation {
        synchronized(lock) {
            closeConversationLocked()
            val created = active.createConversation()
            conversation = created
            Log.i(TAG, "conversation recreated id=${System.identityHashCode(created)}")
            return created
        }
    }

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
