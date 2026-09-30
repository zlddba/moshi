package dev.zlddba.moshiapp.ingest.models

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLongArray
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object ModelFileManager {

    private const val CONNECT_TIMEOUT = 15000
    private const val READ_TIMEOUT = 30000
    private const val PROGRESS_INTERVAL_MS = 150L
    private const val SEGMENT_COUNT = 4
    private const val SEGMENT_MIN_BYTES = 4_194_304L

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
                    val segs = File(target, spec.name + ".segs")
                    if (part.length() > spec.bytes) {
                        part.delete()
                        segs.delete()
                    }
                    downloadFile(spec, part, segs, target, descriptor, onProgress)
                    val expectedSha = spec.sha256
                    if (expectedSha != null) {
                        if (!verifySha(part, expectedSha)) {
                            part.delete()
                            segs.delete()
                            throw IOException("sha256 mismatch: ${spec.name}")
                        }
                    }
                    if (finalFile.exists()) finalFile.delete()
                    if (!part.renameTo(finalFile)) throw IOException("rename failed: ${spec.name}")
                    segs.delete()
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
                File(target, spec.name + ".segs").delete()
            }
        }
    }

    private suspend fun downloadFile(
        spec: ModelCatalog.ModelFile,
        part: File,
        segs: File,
        target: File,
        descriptor: ModelCatalog.Descriptor,
        onProgress: (Long) -> Unit
    ) {
        val count = segmentCount(spec.bytes)
        if (count > 1) {
            try {
                fetchParallel(spec, part, segs, target, descriptor, onProgress, count)
                return
            } catch (e: RangeNotSupported) {
                part.delete()
                segs.delete()
            }
        }
        fetchSingle(spec, part, target, descriptor, onProgress)
    }

    private suspend fun fetchParallel(
        spec: ModelCatalog.ModelFile,
        part: File,
        segs: File,
        target: File,
        descriptor: ModelCatalog.Descriptor,
        onProgress: (Long) -> Unit,
        count: Int
    ) {
        val saved = loadSegments(segs, spec)
        val offsets = if (saved != null) {
            AtomicLongArray(saved)
        } else {
            val length = part.length()
            AtomicLongArray(
                LongArray(count) { i ->
                    length.coerceIn(
                        segmentStart(i, count, spec.bytes),
                        segmentEnd(i, count, spec.bytes)
                    )
                }
            )
        }
        val persistLock = Mutex()
        persistSegments(segs, offsets)
        onProgress(progressOf(target, descriptor))
        coroutineScope {
            for (index in 0 until count) {
                val start = segmentStart(index, count, spec.bytes)
                val end = segmentEnd(index, count, spec.bytes)
                launch {
                    fetchSegmentAcrossPrefixes(
                        spec, part, segs, target, descriptor,
                        index, start, end, offsets, persistLock, onProgress
                    )
                }
            }
        }
    }

    private suspend fun fetchSegmentAcrossPrefixes(
        spec: ModelCatalog.ModelFile,
        part: File,
        segs: File,
        target: File,
        descriptor: ModelCatalog.Descriptor,
        index: Int,
        start: Long,
        end: Long,
        offsets: AtomicLongArray,
        persistLock: Mutex,
        onProgress: (Long) -> Unit
    ) {
        if (offsets.get(index) >= end) return
        var lastError: IOException? = null
        var rangeUnsupported = false
        for (prefix in descriptor.urlPrefixes) {
            try {
                fetchSegment(
                    prefix, spec, part, index, start, end,
                    offsets, persistLock, segs, target, descriptor, onProgress
                )
                return
            } catch (e: RangeNotSupported) {
                rangeUnsupported = true
                lastError = e
            } catch (e: IOException) {
                lastError = e
            }
        }
        if (rangeUnsupported) throw RangeNotSupported(spec.name)
        throw lastError ?: IOException("download failed: ${spec.name}")
    }

    private suspend fun fetchSegment(
        prefix: String,
        spec: ModelCatalog.ModelFile,
        part: File,
        index: Int,
        start: Long,
        end: Long,
        offsets: AtomicLongArray,
        persistLock: Mutex,
        segs: File,
        target: File,
        descriptor: ModelCatalog.Descriptor,
        onProgress: (Long) -> Unit
    ) {
        val begin = offsets.get(index)
        if (begin >= end) return
        val connection = URL("$prefix/${spec.name}").openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Range", "bytes=$begin-${end - 1}")
            val code = connection.responseCode
            if (code == 200) throw RangeNotSupported(spec.name)
            if (code != 206) throw IOException("HTTP $code")
            val contentRange = connection.getHeaderField("Content-Range")
            if (contentRange == null || !contentRange.startsWith("bytes $begin-")) {
                throw IOException("bad content-range: ${spec.name}")
            }
            var writeAt = begin
            var remaining = end - begin
            connection.inputStream.use { input ->
                RandomAccessFile(part, "rw").use { raf ->
                    raf.seek(writeAt)
                    val buf = ByteArray(64 * 1024)
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        if (n > remaining) throw IOException("oversize: ${spec.name}")
                        raf.write(buf, 0, n)
                        writeAt += n
                        remaining -= n
                        offsets.set(index, writeAt)
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= PROGRESS_INTERVAL_MS) {
                            lastReport = now
                            persistLock.withLock { persistSegments(segs, offsets) }
                            onProgress(progressOf(target, descriptor))
                        }
                        if (remaining <= 0L) break
                    }
                }
            }
            if (remaining > 0L) throw IOException("incomplete: ${spec.name}")
            offsets.set(index, writeAt)
            persistLock.withLock { persistSegments(segs, offsets) }
            onProgress(progressOf(target, descriptor))
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun fetchSingle(
        spec: ModelCatalog.ModelFile,
        part: File,
        target: File,
        descriptor: ModelCatalog.Descriptor,
        onProgress: (Long) -> Unit
    ) {
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

    private fun persistSegments(segs: File, state: AtomicLongArray) {
        val temp = File(segs.parentFile, segs.name + ".tmp")
        val text = buildString {
            for (i in 0 until state.length()) {
                append(state.get(i).toString())
                append('\n')
            }
        }
        temp.writeText(text)
        if (!temp.renameTo(segs)) {
            segs.delete()
            if (!temp.renameTo(segs)) throw IOException("segs persist failed: ${segs.name}")
        }
    }

    private fun loadSegments(segs: File, spec: ModelCatalog.ModelFile): LongArray? {
        if (!segs.exists()) return null
        val count = segmentCount(spec.bytes)
        val lines = try {
            segs.readLines()
        } catch (e: IOException) {
            return null
        }
        if (lines.size != count) return null
        val offsets = LongArray(count)
        for (i in 0 until count) {
            val value = lines[i].toLongOrNull() ?: return null
            val start = segmentStart(i, count, spec.bytes)
            val end = segmentEnd(i, count, spec.bytes)
            if (value < start || value > end) return null
            offsets[i] = value
        }
        return offsets
    }

    private fun segmentCount(bytes: Long): Int =
        if (bytes >= SEGMENT_MIN_BYTES) SEGMENT_COUNT else 1

    private fun segmentStart(index: Int, count: Int, bytes: Long): Long {
        val length = (bytes + count - 1) / count
        return index * length
    }

    private fun segmentEnd(index: Int, count: Int, bytes: Long): Long {
        val length = (bytes + count - 1) / count
        return minOf((index + 1) * length, bytes)
    }

    private fun progressOf(dir: File, descriptor: ModelCatalog.Descriptor): Long =
        descriptor.files.sumOf { spec -> completedOf(dir, spec) }

    private fun completedOf(dir: File, spec: ModelCatalog.ModelFile): Long {
        val finalFile = File(dir, spec.name)
        if (finalFile.length() == spec.bytes) return spec.bytes
        val offsets = loadSegments(File(dir, spec.name + ".segs"), spec)
        if (offsets != null) {
            var sum = 0L
            for (i in offsets.indices) {
                sum += offsets[i] - segmentStart(i, offsets.size, spec.bytes)
            }
            return sum
        }
        return File(dir, spec.name + ".part").length().coerceAtMost(spec.bytes)
    }

    fun dir(context: Context, id: String): File = File(File(context.filesDir, "models"), id)

    /**
     * 从用户选定的目录导入已下载好的模型权重，按文件名匹配后复制到应用私有目录。
     * 返回成功复制的文件数。
     */
    suspend fun importFromTree(context: Context, id: String, treeUri: Uri): Int =
        withContext(Dispatchers.IO) {
            lockFor(id).withLock {
                val descriptor = ModelCatalog.descriptor(id)
                val target = dir(context, id)
                target.mkdirs()
                val root = DocumentFile.fromTreeUri(context.applicationContext, treeUri)
                    ?: return@withLock 0
                val files = root.listFiles()
                var copied = 0
                for (spec in descriptor.files) {
                    val finalFile = File(target, spec.name)
                    if (finalFile.length() == spec.bytes) {
                        copied++
                        continue
                    }
                    val source = files.firstOrNull { it.name == spec.name && it.isFile }
                        ?: continue
                    val stream = try {
                        context.contentResolver.openInputStream(source.uri)
                    } catch (e: Exception) {
                        null
                    } ?: continue
                    val part = File(target, spec.name + ".part")
                    stream.use { input ->
                        part.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (part.length() != spec.bytes) {
                        part.delete()
                        continue
                    }
                    val expectedSha = spec.sha256
                    if (expectedSha != null && !verifySha(part, expectedSha)) {
                        part.delete()
                        continue
                    }
                    if (finalFile.exists()) finalFile.delete()
                    if (!part.renameTo(finalFile)) {
                        part.delete()
                        continue
                    }
                    copied++
                }
                copied
            }
        }

    private fun lockFor(id: String): Mutex = locks.computeIfAbsent(id) { Mutex() }

    private class RangeNotSupported(name: String) : IOException("range unsupported: $name")
}
