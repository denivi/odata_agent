package data.agent.guard

sealed class ToolCallDecision {
    data object Allow : ToolCallDecision()

    data class Deny(
        val payload: String
    ) : ToolCallDecision()
}