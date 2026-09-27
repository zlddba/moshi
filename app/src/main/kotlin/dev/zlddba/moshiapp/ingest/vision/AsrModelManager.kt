package dev.zlddba.moshiapp.ingest.vision

import android.content.Context
import dev.zlddba.moshiapp.ingest.models.ModelCatalog
import dev.zlddba.moshiapp.ingest.models.ModelFileManager
import java.io.File

object AsrModelManager {

    private const val MODEL_FILE = "model.int8.onnx"
    private const val TOKENS_FILE = "tokens.txt"

    fun isReady(context: Context): Boolean =
        ModelFileManager.isReady(context, ModelCatalog.SENSE_VOICE)

    fun totalBytes(): Long = ModelFileManager.totalBytes(ModelCatalog.SENSE_VOICE)

    fun modelFile(context: Context): File =
        ModelFileManager.file(context, ModelCatalog.SENSE_VOICE, MODEL_FILE)

    fun tokensFile(context: Context): File =
        ModelFileManager.file(context, ModelCatalog.SENSE_VOICE, TOKENS_FILE)

    suspend fun download(context: Context, onProgress: (Long) -> Unit) =
        ModelFileManager.download(context, ModelCatalog.SENSE_VOICE, onProgress)

    suspend fun delete(context: Context) = ModelFileManager.delete(context, ModelCatalog.SENSE_VOICE)
}
