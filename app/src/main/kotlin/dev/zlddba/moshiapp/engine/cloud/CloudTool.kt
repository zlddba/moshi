package dev.zlddba.moshiapp.engine.cloud

data class CloudToolSpec(
    val name: String,
    val description: String,
    val parametersJson: String
)

data class CloudToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

sealed interface CloudMessage {
    data class System(val text: String) : CloudMessage
    data class User(val text: String) : CloudMessage
    data class Assistant(val text: String, val toolCalls: List<CloudToolCall>) : CloudMessage
    data class Tool(val callId: String, val text: String) : CloudMessage
}

sealed interface CloudTurn {
    data class Answered(val text: String) : CloudTurn
    data class NeedTools(val calls: List<CloudToolCall>) : CloudTurn
    data class Failed(val statusCode: Int = 0, val detail: String = "") : CloudTurn
}
