package dev.zlddba.moshiapp.activities.graphPage

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.data.db.MoshiDatabase
import dev.zlddba.moshiapp.data.db.NoteEntity
import dev.zlddba.moshiapp.domain.graph.GraphBuilder
import dev.zlddba.moshiapp.domain.graph.GraphInput
import dev.zlddba.moshiapp.domain.graph.KnowledgeGraph
import dev.zlddba.moshiapp.engine.embedding.GeckoEmbedding
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

class GraphViewModel(context: Context) : ViewModel() {

    enum class GraphStage {
        Loading,
        Ready,
        Empty,
        EmbeddingMissing,
        Failed
    }

    data class GraphUiState(
        val stage: GraphStage = GraphStage.Loading,
        val graph: KnowledgeGraph = KnowledgeGraph(),
        val scanned: Int = 0,
        val analyzed: Int = 0,
        val ignored: Int = 0
    )

    sealed interface GraphEvent {
        data object Load : GraphEvent
        data object Reload : GraphEvent
    }

    sealed interface GraphEffect {
        data object OpenModelPage : GraphEffect
    }

    private val appContext = context.applicationContext
    private val database = MoshiDatabase.get(appContext)

    private val _uiState = MutableStateFlow(GraphUiState())
    val uiState: StateFlow<GraphUiState> = _uiState.asStateFlow()

    private val _effects = Channel<GraphEffect>(Channel.BUFFERED)
    val effects: Flow<GraphEffect> = _effects.receiveAsFlow()

    private var running = false

    fun onEvent(event: GraphEvent) {
        when (event) {
            GraphEvent.Load -> load()
            GraphEvent.Reload -> load()
        }
    }

    fun openModelPage() {
        viewModelScope.launch { _effects.send(GraphEffect.OpenModelPage) }
    }

    private fun load() {
        if (running) return
        running = true
        _uiState.update { it.copy(stage = GraphStage.Loading) }
        viewModelScope.launch {
            val state = try {
                withContext(Dispatchers.IO) { buildGraph() }
            } catch (e: CancellationException) {
                running = false
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "build graph failed", e)
                GraphUiState(stage = GraphStage.Failed)
            }
            running = false
            _uiState.update { state }
        }
    }

    private suspend fun buildGraph(): GraphUiState {
        val notes = database.noteDao().recentAll()
            .filter { !it.isBuiltIn }
            .filter { it.content.isNotBlank() || !it.summary.isNullOrBlank() }
        if (notes.isEmpty()) return GraphUiState(stage = GraphStage.Empty)
        val kept = notes.take(MAX_NODES)
        val base = GraphUiState(
            scanned = notes.size,
            analyzed = kept.size,
            ignored = (notes.size - kept.size).coerceAtLeast(0)
        )
        if (!GeckoEmbedding.isReady(appContext)) {
            Log.i(TAG, "embedding model missing notes=${notes.size}")
            return base.copy(stage = GraphStage.EmbeddingMissing)
        }
        val inputs = kept.map { GraphInput(id = it.id, title = it.title, type = it.type) }
        val vectors = embedAll(kept)
        if (vectors == null || vectors.size != inputs.size) {
            Log.e(TAG, "embedding failed kept=${kept.size}")
            return base.copy(stage = GraphStage.Failed)
        }
        val graph = withContext(Dispatchers.Default) { GraphBuilder.build(inputs, vectors) }
        Log.i(
            TAG,
            "graph built nodes=${graph.nodes.size} edges=${graph.edges.size} scanned=${notes.size}"
        )
        return base.copy(stage = GraphStage.Ready, graph = graph)
    }

    private suspend fun embedAll(notes: List<NoteEntity>): List<List<Float>>? {
        val texts = notes.map { representativeText(it) }
        val vectors = ArrayList<List<Float>>(texts.size)
        var index = 0
        while (index < texts.size) {
            val end = minOf(index + EMBED_BATCH_SIZE, texts.size)
            val batch = texts.subList(index, end)
            val embedded = GeckoEmbedding.embedDocuments(appContext, batch) ?: return null
            if (embedded.size != batch.size) return null
            vectors.addAll(embedded)
            index = end
        }
        return vectors
    }

    private fun representativeText(note: NoteEntity): String {
        val body = note.content.ifBlank { note.summary.orEmpty() }
        val head = body.take(REPRESENTATIVE_CHARS)
        return if (note.title.isBlank()) head else note.title + "\n" + head
    }

    companion object {
        const val MAX_NODES = 40
        private const val TAG = "GraphViewModel"
        private const val EMBED_BATCH_SIZE = 8
        private const val REPRESENTATIVE_CHARS = 480
    }
}
