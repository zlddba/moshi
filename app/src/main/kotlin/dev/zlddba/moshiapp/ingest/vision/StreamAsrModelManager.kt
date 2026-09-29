package dev.zlddba.moshiapp.ingest.vision

import android.content.Context
import dev.zlddba.moshiapp.ingest.models.ModelCatalog
import dev.zlddba.moshiapp.ingest.models.ModelFileManager
import java.io.File

object StreamAsrModelManager {

    private const val ENCODER_FILE = "encoder-epoch-99-avg-1.int8.onnx"
    private const val DECODER_FILE = "decoder-epoch-99-avg-1.int8.onnx"
    private const val JOINER_FILE = "joiner-epoch-99-avg-1.int8.onnx"
    private const val TOKENS_FILE = "tokens.txt"

    fun isReady(context: Context): Boolean =
        ModelFileManager.isReady(context, ModelCatalog.STREAM_ASR)

    fun totalBytes(): Long = ModelFileManager.totalBytes(ModelCatalog.STREAM_ASR)

    fun encoderFile(context: Context): File =
        ModelFileManager.file(context, ModelCatalog.STREAM_ASR, ENCODER_FILE)

    fun decoderFile(context: Context): File =
        ModelFileManager.file(context, ModelCatalog.STREAM_ASR, DECODER_FILE)

    fun joinerFile(context: Context): File =
        ModelFileManager.file(context, ModelCatalog.STREAM_ASR, JOINER_FILE)

    fun tokensFile(context: Context): File =
        ModelFileManager.file(context, ModelCatalog.STREAM_ASR, TOKENS_FILE)

    suspend fun download(context: Context, onProgress: (Long) -> Unit) =
        ModelFileManager.download(context, ModelCatalog.STREAM_ASR, onProgress)

    suspend fun delete(context: Context) = ModelFileManager.delete(context, ModelCatalog.STREAM_ASR)
}
