package dev.zlddba.moshiapp.ingest.vision

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

object AsrModelManager {

    private const val MODEL_NAME = "model.int8.onnx"
    private const val TOKENS_NAME = "tokens.txt"
    private const val REPO_PATH = "csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main"
    private const val MODEL_BYTES = 239233841L
    private const val TOKENS_BYTES = 315894L
    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 30000
    private val HOSTS = listOf("https://hf-mirror.com", "https://huggingface.co")
    private val FILES = listOf(TOKENS_NAME to TOKENS_BYTES, MODEL_NAME to MODEL_BYTES)
    private val downloadMutex = Mutex()

    fun isReady(context: Context): Boolean = FILES.all { (name, size) ->
        File(dir(context), name).length() == size
    }

    fun totalBytes(): Long = FILES.sumOf { it.second }

    fun modelFile(context: Context): File = File(dir(context), MODEL_NAME)

    fun tokensFile(context: Context): File = File(dir(context), TOKENS_NAME)

    suspend fun download(context: Context, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            val target = dir(context)
            target.mkdirs()
            onProgress(progressOf(target))
            for ((name, size) in FILES) {
                val finalFile = File(target, name)
                if (finalFile.length() == size) continue
                val part = File(target, "$name.part")
                var lastError: IOException? = null
                for (host in HOSTS) {
                    try {
                        fetch(host, part, name, size, target, onProgress)
                        lastError = null
                        break
                    } catch (e: IOException) {
                        lastError = e
                    }
                }
                if (lastError != null) throw lastError
                if (finalFile.exists()) finalFile.delete()
                if (!part.renameTo(finalFile)) throw IOException("rename failed: $name")
                onProgress(progressOf(target))
            }
        }
    }

    suspend fun delete(context: Context) = withContext(Dispatchers.IO) {
        val target = dir(context)
        for ((name, _) in FILES) {
            File(target, name).delete()
            File(target, "$name.part").delete()
        }
    }

    private suspend fun fetch(
        host: String,
        part: File,
        name: String,
        size: Long,
        target: File,
        onProgress: (Long) -> Unit
    ) {
        var offset = part.length()
        if (offset > size) {
            part.delete()
            offset = 0
        }
        val connection = URL("$host/$REPO_PATH/$name").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Accept-Encoding", "identity")
            if (offset > 0) {
                connection.setRequestProperty("Range", "bytes=$offset-")
            }
            val code = connection.responseCode
            if (code != 200 && code != 206) throw IOException("HTTP $code")
            val append = code == 206 && offset > 0
            if (offset > 0 && !append) {
                part.delete()
            }
            connection.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        if (part.length() > size) throw IOException("oversize: $name")
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 150) {
                            lastReport = now
                            onProgress(progressOf(target))
                        }
                    }
                    output.flush()
                }
            }
            if (part.length() != size) throw IOException("incomplete: $name")
            onProgress(progressOf(target))
        } finally {
            connection.disconnect()
        }
    }

    private fun dir(context: Context): File = File(context.filesDir, "asr")

    private fun progressOf(dir: File): Long = FILES.sumOf { (name, size) ->
        val finalFile = File(dir, name)
        if (finalFile.length() == size) size else File(dir, "$name.part").length()
    }
}
