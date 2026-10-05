package dev.zlddba.moshiapp.engine.embedding

import android.content.Context
import android.util.Log
import com.google.ai.edge.localagents.rag.models.EmbedData
import com.google.ai.edge.localagents.rag.models.EmbeddingRequest
import com.google.ai.edge.localagents.rag.models.GeckoEmbeddingModel
import dev.zlddba.moshiapp.ingest.models.ModelCatalog
import dev.zlddba.moshiapp.ingest.models.ModelFileManager
import java.io.File
import java.util.Optional
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GeckoEmbedding {

    private const val MODEL_FILE = "Gecko_256_quant.tflite"
    private const val TOKENIZER_FILE = "sentencepiece.model"
    private const val CALL_TIMEOUT_SECONDS = 120L
    private const val TAG = "GeckoEmbedding"

    private const val OPENCL_LIBRARY = "OpenCL"

    const val DELEGATE_CPU = "cpu"
    const val DELEGATE_GPU = "gpu"

    private val OPENCL_CANDIDATES = listOf(
        "/vendor/lib64/libOpenCL.so",
        "/vendor/lib64/egl/libOpenCL.so",
        "/system/vendor/lib64/libOpenCL.so",
        "/system/vendor/lib64/egl/libOpenCL.so",
        "/odm/lib64/libOpenCL.so",
        "/system/lib64/libOpenCL.so"
    )

    @Volatile
    private var model: GeckoEmbeddingModel? = null

    @Volatile
    private var unavailable = false

    @Volatile
    private var delegate: String = DELEGATE_CPU

    fun delegateTag(): String = delegate

    fun isReady(context: Context): Boolean = obtain(context.applicationContext) != null

    suspend fun embedQuery(context: Context, text: String): List<Float>? =
        embed(context.applicationContext, text, EmbedData.TaskType.RETRIEVAL_QUERY, true)

    suspend fun embedDocuments(context: Context, texts: List<String>): List<List<Float>>? =
        withContext(Dispatchers.IO) {
            if (texts.isEmpty()) return@withContext emptyList()
            val target = obtain(context.applicationContext) ?: return@withContext null
            val request = EmbeddingRequest.create(
                texts.map { value -> EmbedData.create(value, EmbedData.TaskType.RETRIEVAL_DOCUMENT) }
            )
            try {
                target.getBatchEmbeddings(request)
                    .get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .map { value -> value.toList() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
        }

    private suspend fun embed(
        context: Context,
        text: String,
        task: EmbedData.TaskType,
        isQuery: Boolean
    ): List<Float>? = withContext(Dispatchers.IO) {
        if (text.isEmpty()) return@withContext null
        val target = obtain(context) ?: return@withContext null
        val request = EmbeddingRequest.create(listOf(EmbedData.create(text, task, isQuery)))
        try {
            target.getEmbeddings(request)
                .get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .toList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
    }

    private fun obtain(context: Context): GeckoEmbeddingModel? {
        if (unavailable) return null
        model?.let { return it }
        synchronized(this) {
            model?.let { return it }
            if (unavailable) return null
            if (!ModelFileManager.isReady(context, ModelCatalog.GECKO)) return null
            val modelFile = ModelFileManager.file(context, ModelCatalog.GECKO, MODEL_FILE)
            val tokenizerFile = ModelFileManager.file(context, ModelCatalog.GECKO, TOKENIZER_FILE)
            val useGpu = gpuBridgeReady()
            Log.i(TAG, "gecko initialize useGpu=$useGpu")
            return try {
                GeckoEmbeddingModel(
                    modelFile.absolutePath,
                    Optional.of(tokenizerFile.absolutePath),
                    useGpu
                ).also { created ->
                    model = created
                    delegate = if (useGpu) DELEGATE_GPU else DELEGATE_CPU
                }
            } catch (e: Throwable) {
                unavailable = true
                null
            }
        }
    }

    private fun gpuBridgeReady(): Boolean {
        if (loadOpenClByName()) return true
        for (path in OPENCL_CANDIDATES) {
            if (loadOpenClByPath(path)) return true
        }
        Log.w(TAG, "opencl unavailable on this device, embedding runs on CPU")
        return false
    }

    private fun loadOpenClByName(): Boolean = try {
        System.loadLibrary(OPENCL_LIBRARY)
        Log.i(TAG, "opencl loaded via public.libraries allowlist")
        true
    } catch (e: Throwable) {
        Log.i(TAG, "opencl not in public.libraries: ${e.message}")
        false
    }

    private fun loadOpenClByPath(path: String): Boolean {
        if (!File(path).exists()) {
            Log.i(TAG, "opencl candidate missing: $path")
            return false
        }
        return try {
            System.load(path)
            Log.i(TAG, "opencl loaded from $path")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "opencl candidate not loadable: $path reason=${e.message}")
            false
        }
    }
}
