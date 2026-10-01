package com.hrithikvish.nanobot.ai.model

import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide

/** The structured output of Gemini Nano: what the assistant does next. */
@Generable(description = "The next step of the assistant")
data class AgentDecision(
    @Guide(
        description = "CALL_TOOL to run a tool, ANSWER_DIRECTLY to reply, ASK_CLARIFICATION when a required value is missing",
        enumValues = [CALL_TOOL, ANSWER_DIRECTLY, ASK_CLARIFICATION],
    )
    var action: String = ANSWER_DIRECTLY,

    @Guide(description = "The tool name from the tool list when action is CALL_TOOL. Otherwise empty.")
    var toolName: String = "",

    @Guide(description = "A JSON object with the tool arguments, keyed by parameter name, when action is CALL_TOOL. Otherwise empty.")
    var argumentsJson: String = "",

    @Guide(description = "The reply or the clarifying question for the user. Empty when action is CALL_TOOL.")
    var message: String = "",
) {
    companion object {
        const val CALL_TOOL = "CALL_TOOL"
        const val ANSWER_DIRECTLY = "ANSWER_DIRECTLY"
        const val ASK_CLARIFICATION = "ASK_CLARIFICATION"
    }
}
