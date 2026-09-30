package dev.zlddba.moshiapp.activities.detailPage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

class DetailActivity : ComponentActivity() {

    private val viewModel: DetailViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DetailViewModel(applicationContext) as T
                }
            }
        )[DetailViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.onEvent(
            DetailViewModel.DetailEvent.Init(
                noteId = intent.getStringExtra(EXTRA_NOTE_ID),
                chunkId = intent.getIntExtra(EXTRA_CHUNK_ID, -1),
                keyword = intent.getStringExtra(EXTRA_KEYWORD)
            )
        )
        setContent {
            MoshiTheme {
                val uiState by viewModel.uiState.collectAsState()
                val deleted by viewModel.deleted.collectAsState()
                LaunchedEffect(Unit) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            is DetailViewModel.DetailEffect.ShowToast -> Toast.makeText(
                                this@DetailActivity,
                                effect.messageRes,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                LaunchedEffect(deleted, uiState.missing) {
                    if (deleted) {
                        Toast.makeText(
                            this@DetailActivity,
                            R.string.detail_deleted,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    } else if (uiState.missing) {
                        Toast.makeText(
                            this@DetailActivity,
                            R.string.detail_missing,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }
                DetailPageScreen(
                    uiState = uiState,
                    onBack = { finish() },
                    onRelatedClick = { noteId ->
                        startActivity(
                            createIntent(
                                context = this@DetailActivity,
                                noteId = noteId,
                                chunkId = -1,
                                keyword = null
                            )
                        )
                    },
                    onToggleSensitive = {
                        viewModel.onEvent(DetailViewModel.DetailEvent.ToggleSensitive)
                    },
                    onDelete = {
                        viewModel.onEvent(DetailViewModel.DetailEvent.Delete)
                    },
                    onEvent = viewModel::onEvent
                )
            }
        }
    }

    companion object {
        private const val EXTRA_NOTE_ID = "note_id"
        private const val EXTRA_CHUNK_ID = "chunk_id"
        private const val EXTRA_KEYWORD = "keyword"

        fun start(context: Context) {
            context.startActivity(Intent(context, DetailActivity::class.java))
        }

        fun createIntent(
            context: Context,
            noteId: String,
            chunkId: Int,
            keyword: String?
        ): Intent = Intent(context, DetailActivity::class.java)
            .putExtra(EXTRA_NOTE_ID, noteId)
            .putExtra(EXTRA_CHUNK_ID, chunkId)
            .putExtra(EXTRA_KEYWORD, keyword)
    }
}
