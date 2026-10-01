package com.hrithikvish.nanobot.agent

import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import com.hrithikvish.nanobot.ai.GeminiNanoClient
import com.hrithikvish.nanobot.ai.model.AgentDecision
import com.hrithikvish.nanobot.domain.appfunction.AppFunctionTool
import com.hrithikvish.nanobot.domain.appfunction.ConvertAppFunctionDataToJsonUseCase
import com.hrithikvish.nanobot.domain.appfunction.ConvertInputToAppFunctionDataUseCase
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import org.json.JSONException
import org.json.JSONObject

/** The next step after a user message. */
sealed interface AgentStep {
    data class Reply(val text: String) : AgentStep

    /** A validated tool call. It runs only after the user confirms it. */
    data class ToolCall(
        val tool: AppFunctionTool,
        val arguments: JSONObject,
        val parameters: AppFunctionData,
    ) : AgentStep
}

/**
 * Runs one agent turn: Gemini Nano chooses a tool and fills its arguments, the user confirms the call,
 * the AppFunction runs, and Gemini Nano explains the result. One user message makes at most one tool call.
 */
@ViewModelScoped
class AgentOrchestrator @Inject constructor(
    private val nano: GeminiNanoClient,
    private val appFunctionManager: AppFunctionManager?,
    private val convertInput: ConvertInputToAppFunctionDataUseCase,
    private val convertOutput: ConvertAppFunctionDataToJsonUseCase,
) {
    suspend fun decide(userMessage: String, tools: List<AppFunctionTool>): AgentStep {
        // Two model calls: all tool schemas together do not fit in Gemini Nano's context.
        val choice = nano.chooseTool(tools, userMessage)
        if (choice.action != AgentDecision.CALL_TOOL) {
            return AgentStep.Reply(choice.message.ifBlank { "I do not have an answer for that." })
        }
        val tool = tools.firstOrNull { it.name == choice.toolName }
            ?: return AgentStep.Reply("Gemini Nano picked the tool '${choice.toolName}', but no app exposes it.")
        val decision = nano.fillArguments(tool, userMessage)
        if (decision.action != AgentDecision.CALL_TOOL) {
            return AgentStep.Reply(decision.message.ifBlank { "I need more details to run ${tool.name}." })
        }
        val arguments = try {
            wrapSingleObjectParameter(tool, JSONObject(decision.argumentsJson.ifBlank { "{}" }))
        } catch (e: JSONException) {
            return AgentStep.Reply("Gemini Nano wrote arguments that are not valid JSON: ${decision.argumentsJson}")
        }
        val parameters = try {
            convertInput(tool.function, arguments)
        } catch (e: IllegalArgumentException) {
            return AgentStep.Reply("I could not call ${tool.name}: ${e.message}")
        }
        return AgentStep.ToolCall(tool, arguments, parameters)
    }

    /** Executes a confirmed tool call. Returns the result as JSON, or throws the error of the target app. */
    suspend fun execute(call: AgentStep.ToolCall): String {
        val manager = checkNotNull(appFunctionManager) {
            "This device does not support AppFunctions."
        }
        val function = call.tool.function
        return when (
            val response = manager.executeAppFunction(
                ExecuteAppFunctionRequest(
                    function.packageName,
                    function.id,
                    call.parameters,
                )
            )
        ) {
            is ExecuteAppFunctionResponse.Success -> convertOutput(function, response.returnValue)
            is ExecuteAppFunctionResponse.Error -> throw response.error
        }
    }

    suspend fun explain(userMessage: String, call: AgentStep.ToolCall, resultJson: String): String =
        nano.explain(userMessage, call.tool.name, resultJson)

    /**
     * A function with one object parameter, such as `calculateEmi(params)`, expects `{"params": {...}}`.
     * Gemini Nano often writes only the inner object. Put it back inside the parameter.
     */
    private fun wrapSingleObjectParameter(
        tool: AppFunctionTool,
        arguments: JSONObject,
    ): JSONObject {
        val parameter = tool.function.parameters.singleOrNull() ?: return arguments
        val isObject = parameter.dataType is AppFunctionObjectTypeMetadata ||
            parameter.dataType is AppFunctionReferenceTypeMetadata
        return if (isObject && !arguments.has(parameter.name)) {
            JSONObject().put(parameter.name, arguments)
        } else {
            arguments
        }
    }
}
