package dev.zlddba.moshiapp.ingest.models

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object ModelFileManager {

    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 30000
    private const val PROGRESS_INTERVAL_MS = 150L

    private val locks = ConcurrentHashMap<String, Mutex>()

    fun isReady(context: Context, id: String): Boolean =
        ModelCatalog.descriptor(id).files.all { file(context, id, it.name).length() == it.bytes }

    fun bytesOnDisk(context: Context, id: String): Long =
        ModelCatalog.descriptor(id).files.sumOf { file(context, id, it.name).length() }

    fun totalBytes(id: String): Long = ModelCatalog.totalBytes(id)

    fun file(context: Context, id: String, name: String): File = File(dir(context, id), name)

    suspend fun download(context: Context, id: String, onProgress: (Long) -> Unit): Unit =
        withContext(Dispatchers.IO) {
            lockFor(id).withLock {
                val descriptor = ModelCatalog.descriptor(id)
                val target = dir(context, id)
                target.mkdirs()
                onProgress(progressOf(target, descriptor))
                for (spec in descriptor.files) {
                    val finalFile = File(target, spec.name)
                    if (finalFile.length() == spec.bytes) continue
                    val part = File(target, spec.name + ".part")
                    if (part.length() > spec.bytes) part.delete()
                    if (part.length() != spec.bytes) {
                        var lastError: IOException? = null
                        for (prefix in descriptor.urlPrefixes) {
                            try {
                                fetch(prefix, part, spec, target, descriptor, onProgress)
                                lastError = null
                                break
                            } catch (e: IOException) {
                                lastError = e
                            }
                        }
                        if (lastError != null) throw lastError
                    }
                    val expectedSha = spec.sha256
                    if (expectedSha != null) {
                        if (!verifySha(part, expectedSha)) {
                            part.delete()
                            throw IOException("sha256 mismatch: ${spec.name}")
                        }
                    }
                    if (finalFile.exists()) finalFile.delete()
                    if (!part.renameTo(finalFile)) throw IOException("rename failed: ${spec.name}")
                    onProgress(progressOf(target, descriptor))
                }
            }
        }

    suspend fun delete(context: Context, id: String): Unit = withContext(Dispatchers.IO) {
        lockFor(id).withLock {
            val descriptor = ModelCatalog.descriptor(id)
            val target = dir(context, id)
            for (spec in descriptor.files) {
                File(target, spec.name).delete()
                File(target, spec.name + ".part").delete()
            }
        }
    }

    private suspend fun fetch(
        prefix: String,
        part: File,
        spec: ModelCatalog.ModelFile,
        target: File,
        descriptor: ModelCatalog.Descriptor,
        onProgress: (Long) -> Unit
    ) {
        val offset = part.length()
        val connection = URL("$prefix/${spec.name}").openConnection() as HttpURLConnection
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
            connection.inputStream.use { input ->
                FileOutputStream(part, append).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        if (part.length() > spec.bytes) throw IOException("oversize: ${spec.name}")
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            onProgress(progressOf(target, descriptor))
                        }
                    }
                    output.flush()
                }
            }
            if (part.length() != spec.bytes) throw IOException("incomplete: ${spec.name}")
            onProgress(progressOf(target, descriptor))
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun verifySha(file: File, expected: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                coroutineContext.ensureActive()
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { b -> "%02x".format(b.toInt() and 0xFF) }
        return actual.equals(expected, ignoreCase = true)
    }

    private fun progressOf(dir: File, descriptor: ModelCatalog.Descriptor): Long =
        descriptor.files.sumOf { spec ->
            val finalFile = File(dir, spec.name)
            if (finalFile.length() == spec.bytes) spec.bytes
            else File(dir, spec.name + ".part").length()
        }

    private fun dir(context: Context, id: String): File = File(File(context.filesDir, "models"), id)

    private fun lockFor(id: String): Mutex = locks.computeIfAbsent(id) { Mutex() }
}
