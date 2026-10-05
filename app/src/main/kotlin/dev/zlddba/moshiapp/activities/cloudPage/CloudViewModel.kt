package dev.zlddba.moshiapp.activities.cloudPage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zlddba.moshiapp.R
import dev.zlddba.moshiapp.data.prefs.CloudConfigPrefs
import dev.zlddba.moshiapp.engine.cloud.CloudConfig
import dev.zlddba.moshiapp.engine.cloud.CloudGateway
import dev.zlddba.moshiapp.engine.cloud.CloudTestResult
import dev.zlddba.moshiapp.ui.CloudMessages
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CloudViewModel(
    private val cloudConfigPrefs: CloudConfigPrefs,
    private val cloudGateway: CloudGateway
) : ViewModel() {

    sealed interface TestState {
        data object Idle : TestState
        data object Testing : TestState
        data object Success : TestState
        data class ValidationFailed(val resId: Int) : TestState
        data class ServerFailed(val resId: Int, val statusCode: Int, val detail: String) : TestState
    }

    data class CloudUiState(
        val baseUrl: String = "",
        val apiKey: String = "",
        val modelName: String = "",
        val forceLocal: Boolean = true,
        val mode: Int = CloudConfig.MODE_LOCAL,
        val keyVisible: Boolean = false,
        val testState: TestState = TestState.Idle
    )

    sealed interface CloudEvent {
        data class BaseUrlChanged(val value: String) : CloudEvent
        data class ApiKeyChanged(val value: String) : CloudEvent
        data class ModelNameChanged(val value: String) : CloudEvent
        data class ForceLocalChanged(val value: Boolean) : CloudEvent
        data class EnabledChanged(val value: Boolean) : CloudEvent
        data object KeyVisibilityToggled : CloudEvent
        data class PresetClicked(val baseUrl: String) : CloudEvent
        data object TestClicked : CloudEvent
        data object SaveClicked : CloudEvent
    }

    sealed interface CloudEffect {
        data object Saved : CloudEffect
    }

    private val _cloudUiState = MutableStateFlow(CloudUiState())
    val cloudUiState: StateFlow<CloudUiState> = _cloudUiState.asStateFlow()

    private val _effects = Channel<CloudEffect>(Channel.BUFFERED)
    val effects: Flow<CloudEffect> = _effects.receiveAsFlow()

    init {
        val saved = cloudConfigPrefs.load()
        _cloudUiState.update {
            it.copy(
                baseUrl = saved.baseUrl,
                apiKey = saved.apiKey,
                modelName = saved.modelName,
                forceLocal = saved.forceLocal,
                mode = saved.mode
            )
        }
    }

    fun onEvent(event: CloudEvent) {
        when (event) {
            is CloudEvent.BaseUrlChanged -> _cloudUiState.update {
                it.copy(baseUrl = event.value, testState = TestState.Idle)
            }

            is CloudEvent.ApiKeyChanged -> _cloudUiState.update {
                it.copy(apiKey = event.value, testState = TestState.Idle)
            }

            is CloudEvent.ModelNameChanged -> _cloudUiState.update {
                it.copy(modelName = event.value, testState = TestState.Idle)
            }

            is CloudEvent.ForceLocalChanged -> _cloudUiState.update {
                it.copy(forceLocal = event.value)
            }

            is CloudEvent.EnabledChanged -> _cloudUiState.update {
                it.copy(
                    mode = when {
                        !event.value -> CloudConfig.MODE_LOCAL
                        it.mode == CloudConfig.MODE_CLOUD -> CloudConfig.MODE_CLOUD
                        else -> CloudConfig.MODE_HYBRID
                    }
                )
            }

            CloudEvent.KeyVisibilityToggled -> _cloudUiState.update {
                it.copy(keyVisible = !it.keyVisible)
            }

            is CloudEvent.PresetClicked -> _cloudUiState.update {
                it.copy(baseUrl = event.baseUrl, testState = TestState.Idle)
            }

            CloudEvent.TestClicked -> testConnection()
            CloudEvent.SaveClicked -> saveConfig()
        }
    }

    private fun validationError(state: CloudUiState): Int? = when {
        !state.baseUrl.startsWith("http://") && !state.baseUrl.startsWith("https://") ->
            R.string.cloud_err_base_url

        state.apiKey.isBlank() -> R.string.cloud_err_api_key
        state.modelName.isBlank() -> R.string.cloud_err_model
        else -> null
    }

    private fun testConnection() {
        val state = _cloudUiState.value
        val error = validationError(state)
        if (error != null) {
            _cloudUiState.update { it.copy(testState = TestState.ValidationFailed(error)) }
            return
        }
        _cloudUiState.update { it.copy(testState = TestState.Testing) }
        viewModelScope.launch {
            val config = CloudConfig(
                baseUrl = state.baseUrl,
                apiKey = state.apiKey,
                modelName = state.modelName,
                forceLocal = state.forceLocal,
                mode = state.mode
            )
            val result = cloudGateway.testConnection(config)
            val testState = when (result) {
                CloudTestResult.Success -> {
                    cloudConfigPrefs.save(config)
                    TestState.Success
                }
                is CloudTestResult.Failure -> TestState.ServerFailed(
                    resId = CloudMessages.errorOf(result.statusCode),
                    statusCode = result.statusCode,
                    detail = result.detail
                )
            }
            _cloudUiState.update { current ->
                if (current.testState == TestState.Testing) {
                    current.copy(testState = testState)
                } else {
                    current
                }
            }
        }
    }

    private fun saveConfig() {
        val state = _cloudUiState.value
        cloudConfigPrefs.save(
            CloudConfig(
                baseUrl = state.baseUrl,
                apiKey = state.apiKey,
                modelName = state.modelName,
                forceLocal = state.forceLocal,
                mode = state.mode
            )
        )
        viewModelScope.launch {
            _effects.send(CloudEffect.Saved)
        }
    }
}
