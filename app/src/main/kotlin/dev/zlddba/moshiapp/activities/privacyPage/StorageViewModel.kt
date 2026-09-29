package dev.zlddba.moshiapp.activities.privacyPage

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.repo.IngestRepository
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StorageViewModel(context: Context) : ViewModel() {

    data class StorageUiState(
        val noteCount: Int = 0,
        val chunkCount: Int = 0,
        val sizeText: String = "",
        val busy: Boolean = false
    )

    sealed interface StorageEvent {
        data object Refresh : StorageEvent
        data object ExportRequested : StorageEvent
        data class ExportChosen(val uri: Uri) : StorageEvent
        data object ClearConfirmed : StorageEvent
    }

    sealed interface StorageEffect {
        data object PickExportDestination : StorageEffect
        data class ShowToast(val messageRes: Int, val arg: Int = -1) : StorageEffect
    }

    private val appContext = context.applicationContext

    private val _uiState = MutableStateFlow(StorageUiState())
    val uiState: StateFlow<StorageUiState> = _uiState.asStateFlow()

    private val _effects = Channel<StorageEffect>(Channel.BUFFERED)
    val effects: Flow<StorageEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            merge(IngestRepository.savedNotes, IngestRepository.deletedNotes).collect {
                refresh()
            }
        }
    }

    fun onEvent(event: StorageEvent) {
        when (event) {
            StorageEvent.Refresh -> refresh()
            StorageEvent.ExportRequested -> sendEffect(StorageEffect.PickExportDestination)
            is StorageEvent.ExportChosen -> export(event.uri)
            StorageEvent.ClearConfirmed -> clearAll()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            val stats = withContext(Dispatchers.IO) { loadStats() }
            _uiState.update {
                it.copy(
                    noteCount = stats.noteCount,
                    chunkCount = stats.chunkCount,
                    sizeText = formatSize(stats.sizeBytes),
                    busy = false
                )
            }
        }
    }

    private suspend fun loadStats(): Stats {
        val database = MoshiDatabase.get(appContext)
        return Stats(
            noteCount = database.noteDao().count(),
            chunkCount = database.chunkDao().count(),
            sizeBytes = dataSize()
        )
    }

    private fun dataSize(): Long {
        var total = 0L
        val database = appContext.getDatabasePath(DATABASE_NAME)
        total += database.length()
        total += File(database.path + "-wal").length()
        total += File(database.path + "-shm").length()
        total += dirSize(File(appContext.filesDir, "notes"))
        total += dirSize(File(appContext.filesDir, "keywords"))
        total += dirSize(File(appContext.filesDir, "vectors"))
        return total
    }

    private fun dirSize(dir: File): Long {
        val files = dir.listFiles() ?: return 0L
        var total = 0L
        for (file in files) {
            total += if (file.isDirectory) dirSize(file) else file.length()
        }
        return total
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= GIGABYTE ->
            appContext.getString(R.string.storage_size_gb, decimal(bytes / GIGABYTE, 2))

        bytes >= MEGABYTE ->
            appContext.getString(R.string.storage_size_mb, decimal(bytes / MEGABYTE, 1))

        bytes >= KILOBYTE ->
            appContext.getString(R.string.storage_size_kb, decimal(bytes / KILOBYTE, 1))

        else -> appContext.getString(R.string.storage_size_b, bytes.toString())
    }

    private fun decimal(value: Double, digits: Int): String =
        "%,.${digits}f".format(Locale.CHINA, value)

    private fun export(uri: Uri) {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val count = try {
                IngestRepository.exportJson(appContext, uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(busy = false) }
                sendEffect(StorageEffect.ShowToast(R.string.storage_export_failed))
                return@launch
            }
            _uiState.update { it.copy(busy = false) }
            sendEffect(StorageEffect.ShowToast(R.string.storage_export_success, count))
        }
    }

    private fun clearAll() {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                IngestRepository.clearAll(appContext)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { it.copy(busy = false) }
                sendEffect(StorageEffect.ShowToast(R.string.storage_clear_failed))
                return@launch
            }
            sendEffect(StorageEffect.ShowToast(R.string.storage_clear_success))
            refresh()
        }
    }

    private fun sendEffect(effect: StorageEffect) {
        viewModelScope.launch {
            _effects.send(effect)
        }
    }

    private data class Stats(
        val noteCount: Int,
        val chunkCount: Int,
        val sizeBytes: Long
    )

    private companion object {
        const val DATABASE_NAME = "moshi.db"
        const val KILOBYTE = 1024.0
        const val MEGABYTE = 1024.0 * 1024
        const val GIGABYTE = 1024.0 * 1024 * 1024
    }
}
