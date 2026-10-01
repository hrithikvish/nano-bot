package com.hrithikvish.nanobot.ai

import android.os.SystemClock
import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.PromptPrefix
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.hrithikvish.nanobot.ai.model.AgentDecision
import com.hrithikvish.nanobot.domain.appfunction.AppFunctionTool
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/** Gemini Nano through the ML Kit Prompt API. The owner calls [close] when it is destroyed. */
@ViewModelScoped
class GeminiNanoClient @Inject constructor() : AutoCloseable {

    private val model = Generation.getClient()
    private var cachingAvailable: Boolean? = null

    /** Returns one of the `FeatureStatus` values. */
    suspend fun checkStatus(): Int = logged("checkStatus") { model.checkStatus() }

    fun download(): Flow<DownloadStatus> = model.download().onEach { status ->
        when (status) {
            is DownloadStatus.DownloadStarted -> {
                Log.d(TAG, "download started: ${status.bytesToDownload} bytes")
            }

            is DownloadStatus.DownloadProgress -> {
                Log.d(TAG, "download progress: ${status.totalBytesDownloaded} bytes")
            }

            is DownloadStatus.DownloadFailed -> {
                Log.e(TAG, "download failed", status.e)
            }

            else -> Log.d(TAG, "download: $status")
        }
    }

    suspend fun warmup() {
        logged("baseModelName") { model.getBaseModelName() }
        logged("tokenLimit") { model.getTokenLimit() }
        logged("warmup") { model.warmup() }
    }

    suspend fun isStructuredOutputAvailable(): Boolean =
        logged("isStructuredOutputAvailable") { model.isStructuredOutputFeatureAvailable() }

    /** Step 1: chooses the tool for [userMessage] from the one-line summaries, or answers directly. */
    suspend fun chooseTool(tools: List<AppFunctionTool>, userMessage: String): AgentDecision {
        // The tool list changes only when an app changes, so it is the cached prefix.
        val prefix = CHOOSE_INSTRUCTIONS + tools.joinToString("\n") { it.summary }.ifEmpty { "(no tools)" }
        return decide("chooseTool (${tools.size} tools)", prefix, userMessage)
    }

    /** Step 2: fills the arguments of [tool] from [userMessage], or asks for a missing value. */
    suspend fun fillArguments(tool: AppFunctionTool, userMessage: String): AgentDecision =
        decide("fillArguments (${tool.name})", ARGUMENTS_INSTRUCTIONS + tool.schema, userMessage)

    private suspend fun decide(label: String, prefix: String, userMessage: String): AgentDecision {
        val request = generateTypedContentRequest(
            request(prefix, "User: $userMessage"),
            AgentDecision::class,
        )
        logLong("$label prompt", "$prefix\n\nUser: $userMessage")
        logged("$label countTokens") { model.countTokens(request).totalTokens }
        val response = logged(label) { model.generateContent(request) }
        response.candidates.forEach {
            Log.d(TAG, "$label candidate: finishReason=${it.finishReason} response=${it.response}")
        }
        return response.candidates.firstOrNull()?.response
            ?: AgentDecision(message = "Gemini Nano returned no answer.")
    }

    /** Writes a short answer to [userMessage] from the result of a tool. */
    suspend fun explain(userMessage: String, toolName: String, resultJson: String): String {
        val text = "User question: $userMessage\nTool: $toolName\nTool result (JSON): $resultJson"
        logLong("explain prompt", "$EXPLAIN_INSTRUCTIONS\n\n$text")
        val response = logged("explain") { model.generateContent(request(EXPLAIN_INSTRUCTIONS, text)) }
        response.candidates.forEach {
            Log.d(TAG, "explain candidate: finishReason=${it.finishReason} text=${it.text}")
        }
        return response.candidates.firstOrNull()?.text.orEmpty()
    }

    override fun close() {
        Log.d(TAG, "close")
        model.close()
    }

    /** Runs [block] and logs its result, duration, or error. */
    private inline fun <T> logged(call: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return try {
            block().also { Log.d(TAG, "$call -> $it (${SystemClock.elapsedRealtime() - start} ms)") }
        } catch (e: Exception) {
            Log.e(TAG, "$call failed after ${SystemClock.elapsedRealtime() - start} ms", e)
            throw e
        }
    }

    /** Logcat cuts a line at about 4 KB, so long text goes out in parts. */
    private fun logLong(label: String, text: String) {
        val parts = text.chunked(3_000)
        parts.forEachIndexed { i, part -> Log.d(TAG, "$label [${i + 1}/${parts.size}]: $part") }
    }

    private suspend fun request(prefix: String, text: String): GenerateContentRequest {
        val caching = cachingAvailable ?: logged("isCachingFeatureAvailable") {
            model.isCachingFeatureAvailable()
        }.also { cachingAvailable = it }
        return if (caching) {
            generateContentRequest(TextPart(text)) {
                promptPrefix = PromptPrefix(prefix)
                temperature = 0f
            }
        } else {
            generateContentRequest(TextPart("$prefix\n\n$text")) {
                temperature = 0f
            }
        }
    }

    private companion object {
        const val TAG = "NanoBot"

        // ponytail: one summary line per tool, about 30 tokens each. Past about 100 tools this overflows Gemini Nano's context again. Then filter tools by app or by keywords first.
        const val CHOOSE_INSTRUCTIONS = """You are NanoBot, an assistant on an Android phone. Apps on the phone expose tools.
                Rules:
                1. If one tool does what the user asks, set action to CALL_TOOL and set toolName to its exact name. Leave argumentsJson empty.
                2. If no tool fits, set action to ANSWER_DIRECTLY and answer in message.
                Tools (name: what it does):
            """

        const val ARGUMENTS_INSTRUCTIONS = """You are NanoBot. Fill in the arguments of the tool below from the user's message.
                Rules:
                1. Set action to CALL_TOOL, set toolName to the tool name, and set argumentsJson to a JSON object keyed by the tool's parameter names. Put the nested fields of an object parameter inside that parameter.
                2. Convert amounts to plain numbers. 1 lakh is 100000. 1 crore is 10000000.
                3. If a required value is not in the user's message, set action to ASK_CLARIFICATION and ask for it in message.
                Tool (JSON):
            """

        const val EXPLAIN_INSTRUCTIONS = """You are NanoBot. A tool on the phone returned a result for the user's question.
                Answer the question in 2 to 4 short sentences. Use only the values in the result. Do not change any number."""
    }
}
