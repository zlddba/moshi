package dev.zlddba.moshiapp.activities.searchPage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.zlddba.moshiapp.activities.detailPage.DetailActivity
import dev.zlddba.moshiapp.ui.theme.MoshiTheme
import kotlinx.coroutines.launch

class SearchActivity : ComponentActivity() {

    private val viewModel: SearchViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SearchViewModel(applicationContext) as T
                }
            }
        )[SearchViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.onEvent(
            SearchViewModel.SearchEvent.Init(intent.getStringExtra(EXTRA_QUERY).orEmpty())
        )
        setContent {
            MoshiTheme {
                val uiState by viewModel.uiState.collectAsState()
                SearchPageScreen(
                    uiState = uiState,
                    onBack = { finish() },
                    onEvent = viewModel::onEvent
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.effects.collect { effect ->
                    when (effect) {
                        is SearchViewModel.SearchEffect.OpenDetail -> {
                            startActivity(
                                DetailActivity.createIntent(
                                    context = this@SearchActivity,
                                    noteId = effect.noteId,
                                    chunkId = effect.chunkId,
                                    keyword = effect.keyword
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val EXTRA_QUERY = "query"

        fun start(context: Context, query: String) {
            context.startActivity(createIntent(context, query))
        }

        fun createIntent(context: Context, query: String): Intent =
            Intent(context, SearchActivity::class.java).putExtra(EXTRA_QUERY, query)
    }
}
