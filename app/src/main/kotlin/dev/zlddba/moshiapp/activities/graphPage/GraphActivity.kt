package dev.zlddba.moshiapp.activities.graphPage

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.zlddba.moshiapp.activities.detailPage.DetailActivity
import dev.zlddba.moshiapp.activities.privacyPage.ModelActivity
import dev.zlddba.moshiapp.ui.theme.MoshiTheme

class GraphActivity : ComponentActivity() {

    private val viewModel: GraphViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GraphViewModel(applicationContext) as T
                }
            }
        )[GraphViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel.onEvent(GraphViewModel.GraphEvent.Load)
        setContent {
            MoshiTheme {
                val uiState by viewModel.uiState.collectAsState()
                LaunchedEffect(Unit) {
                    viewModel.effects.collect { effect ->
                        when (effect) {
                            GraphViewModel.GraphEffect.OpenModelPage ->
                                ModelActivity.start(this@GraphActivity)
                        }
                    }
                }
                GraphPageScreen(
                    uiState = uiState,
                    onBack = { finish() },
                    onNodeClick = { noteId ->
                        startActivity(
                            DetailActivity.createIntent(
                                context = this@GraphActivity,
                                noteId = noteId,
                                chunkId = -1,
                                keyword = null
                            )
                        )
                    },
                    onEvent = viewModel::onEvent,
                    onOpenModel = viewModel::openModelPage
                )
            }
        }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, GraphActivity::class.java))
        }
    }
}
