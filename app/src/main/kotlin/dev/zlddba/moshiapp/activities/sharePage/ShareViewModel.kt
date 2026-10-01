package dev.zlddba.moshiapp.activities.sharePage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.repo.IngestRepository
import dev.zlddba.moshiapp.domain.title.TitleSuggester
import dev.zlddba.moshiapp.ingest.parse.IngestException
import dev.zlddba.moshiapp.ingest.parse.ParserRegistry
import dev.zlddba.moshiapp.ui.IngestMessages
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareViewModel(context: Context) : ViewModel() {

    data class ShareUiState(
        val fileName: String = "",
        val mimeType: String? = null,
        val title: String = "",
        val tags: String = "",
        val generating: Boolean = false,
        val importing: Boolean = false,
        val preparingImage: Boolean = true,
        val stage: IngestRepository.Stage? = null,
        val unsupported: Boolean = false
    )

    sealed interface ShareEvent {
        data class Init(val uriString: String?, val suggestedTitle: String?) : ShareEvent
        data class TitleChanged(val value: String) : ShareEvent
        data class TagsChanged(val value: String) : ShareEvent
        data object RegenerateTitle : ShareEvent
        data object Confirm : ShareEvent
    }

    sealed interface ShareEffect {
        data class ShowToast(val messageRes: Int) : ShareEffect
        data class OpenOcr(val imageUri: String) : ShareEffect
        data object Close : ShareEffect
    }

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(ShareUiState())
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ShareEffect>(Channel.BUFFERED)
    val effects: Flow<ShareEffect> = _effects.receiveAsFlow()

    private var initialized = false
    private var activeUri: Uri? = null

    fun onEvent(event: ShareEvent) {
        when (event) {
            is ShareEvent.Init -> init(event)
            is ShareEvent.TitleChanged -> _uiState.update { it.copy(title = event.value) }
            is ShareEvent.TagsChanged -> _uiState.update { it.copy(tags = event.value) }
            ShareEvent.RegenerateTitle -> regenerateTitle()
            ShareEvent.Confirm -> confirm()
        }
    }

    private fun init(event: ShareEvent.Init) {
        if (initialized) return
        initialized = true
        val uri = event.uriString?.takeUnless { it.isBlank() }?.let { Uri.parse(it) }
        if (uri == null) {
            _uiState.update { it.copy(preparingImage = false) }
            sendEffect(ShareEffect.ShowToast(R.string.share_missing))
            sendEffect(ShareEffect.Close)
            return
        }
        activeUri = uri
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { queryName(uri) }
                ?: uri.lastPathSegment.orEmpty()
            val mime = withContext(Dispatchers.IO) { queryMime(uri) }
            if (isImage(name, mime)) {
                _uiState.update { it.copy(fileName = name, mimeType = mime) }
                handOverToOcr(uri, name)
                return@launch
            }
            _uiState.update {
                it.copy(
                    fileName = name,
                    mimeType = mime,
                    title = event.suggestedTitle.orEmpty(),
                    preparingImage = false,
                    unsupported = ParserRegistry.resolve(name, mime) == null
                )
            }
        }
    }

    private suspend fun handOverToOcr(uri: Uri, fileName: String) {
        val copied = withContext(Dispatchers.IO) { copyToImageCache(uri, fileName) }
        if (copied == null) {
            sendEffect(ShareEffect.ShowToast(R.string.share_image_failed))
            sendEffect(ShareEffect.Close)
            return
        }
        sendEffect(ShareEffect.OpenOcr(copied.toString()))
    }

    private fun isImage(fileName: String, mimeType: String?): Boolean {
        if (mimeType?.startsWith(IMAGE_MIME_PREFIX) == true) return true
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in IMAGE_EXTENSIONS
    }

    private fun copyToImageCache(uri: Uri, fileName: String): Uri? {
        return try {
            val source = appContext.contentResolver.openInputStream(uri) ?: return null
            val directory = File(appContext.cacheDir, IMAGE_CACHE_DIR).apply { mkdirs() }
            val extension = fileName.substringAfterLast('.', "").lowercase()
                .takeIf { it in IMAGE_EXTENSIONS } ?: DEFAULT_IMAGE_EXTENSION
            val target = File(directory, "shared_${System.currentTimeMillis()}.$extension")
            val copied = source.use { input ->
                copyBounded(input, target, IngestRepository.MAX_FILE_BYTES)
            }
            if (!copied) {
                target.delete()
                return null
            }
            Uri.fromFile(target)
        } catch (e: Throwable) {
            null
        }
    }

    private fun copyBounded(source: InputStream, target: File, limit: Long): Boolean {
        val buffer = ByteArray(COPY_BUFFER_SIZE)
        var total = 0L
        target.outputStream().use { sink ->
            while (true) {
                val read = source.read(buffer)
                if (read == -1) break
                total += read
                if (total > limit) return false
                sink.write(buffer, 0, read)
            }
        }
        return true
    }

    private fun queryName(uri: Uri): String? = try {
        appContext.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
    } catch (e: Throwable) {
        null
    }

    private fun queryMime(uri: Uri): String? = try {
        appContext.contentResolver.getType(uri)
    } catch (e: Throwable) {
        null
    }

    private fun regenerateTitle() {
        val uri = activeUri ?: return
        val state = _uiState.value
        if (state.generating || state.importing) return
        _uiState.update { it.copy(generating = true) }
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                runCatching {
                    val parser = ParserRegistry.resolve(state.fileName, state.mimeType)
                        ?: return@runCatching ""
                    parser.parse(appContext, uri, state.fileName, null).text
                }.getOrDefault("")
            }
            if (preview.isBlank()) {
                _uiState.update { it.copy(generating = false) }
                sendEffect(ShareEffect.ShowToast(R.string.ingest_title_failed))
                return@launch
            }
            val suggestion = TitleSuggester.suggest(appContext, preview)
            _uiState.update { it.copy(title = suggestion.title, generating = false) }
            if (!suggestion.fromModel) {
                sendEffect(ShareEffect.ShowToast(R.string.ingest_title_failed))
            }
        }
    }

    private fun confirm() {
        val uri = activeUri ?: return
        val state = _uiState.value
        if (state.importing || state.generating) return
        if (state.unsupported) {
            sendEffect(ShareEffect.ShowToast(R.string.share_unsupported))
            return
        }
        _uiState.update { it.copy(importing = true) }
        viewModelScope.launch {
            try {
                val summary = IngestRepository.importFile(
                    context = appContext,
                    uri = uri,
                    fileName = state.fileName,
                    mimeType = state.mimeType,
                    title = state.title
                ) { stage -> _uiState.update { it.copy(stage = stage) } }
                if (state.tags.isNotBlank()) {
                    IngestRepository.setTags(appContext, summary.noteId, state.tags)
                }
                sendEffect(ShareEffect.ShowToast(R.string.share_success))
                sendEffect(ShareEffect.Close)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IngestException) {
                _uiState.update { it.copy(importing = false, stage = null) }
                sendEffect(ShareEffect.ShowToast(IngestMessages.errorOf(e.kind)))
            } catch (e: Exception) {
                _uiState.update { it.copy(importing = false, stage = null) }
                sendEffect(ShareEffect.ShowToast(R.string.ingest_failed))
            }
        }
    }

    private fun sendEffect(effect: ShareEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private companion object {
        const val IMAGE_CACHE_DIR = "ocr"
        const val IMAGE_MIME_PREFIX = "image/"
        const val COPY_BUFFER_SIZE = 8192
        const val DEFAULT_IMAGE_EXTENSION = "jpg"

        val IMAGE_EXTENSIONS = setOf(
            "jpg",
            "jpeg",
            "png",
            "webp",
            "gif",
            "bmp",
            "heic",
            "heif"
        )
    }
}
