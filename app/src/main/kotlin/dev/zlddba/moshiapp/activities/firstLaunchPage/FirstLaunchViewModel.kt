package dev.zlddba.moshiapp.activities.firstLaunchPage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.data.prefs.FirstLaunchPrefs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val FIRST_LAUNCH_PAGE_COUNT = 3

class FirstLaunchViewModel(
    private val firstLaunchPrefs: FirstLaunchPrefs
) : ViewModel() {

    data class FirstLaunchUiState(
        val currentPage: Int = 0,
        val pageCount: Int = FIRST_LAUNCH_PAGE_COUNT
    ) {
        val isLastPage: Boolean
            get() = currentPage >= pageCount - 1
    }

    sealed interface FirstLaunchEvent {
        data class PageChanged(val pageIndex: Int) : FirstLaunchEvent
        data object NextClicked : FirstLaunchEvent
        data object SkipClicked : FirstLaunchEvent
    }

    sealed interface FirstLaunchEffect {
        data object NavigateToMain : FirstLaunchEffect
    }

    private val _firstLaunchUiState = MutableStateFlow(FirstLaunchUiState())
    val firstLaunchUiState: StateFlow<FirstLaunchUiState> = _firstLaunchUiState.asStateFlow()

    private val _effects = Channel<FirstLaunchEffect>(Channel.BUFFERED)
    val effects: Flow<FirstLaunchEffect> = _effects.receiveAsFlow()

    fun onEvent(event: FirstLaunchEvent) {
        when (event) {
            is FirstLaunchEvent.PageChanged -> _firstLaunchUiState.update {
                it.copy(currentPage = event.pageIndex)
            }

            FirstLaunchEvent.NextClicked -> {
                if (_firstLaunchUiState.value.isLastPage) {
                    completeFirstLaunch()
                } else {
                    _firstLaunchUiState.update { it.copy(currentPage = it.currentPage + 1) }
                }
            }

            FirstLaunchEvent.SkipClicked -> completeFirstLaunch()
        }
    }

    private fun completeFirstLaunch() {
        firstLaunchPrefs.markFirstLaunchCompleted()
        viewModelScope.launch {
            _effects.send(FirstLaunchEffect.NavigateToMain)
        }
    }
}
