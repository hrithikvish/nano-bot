package com.hrithikvish.nanobot.ui.chat

import androidx.appfunctions.AppFunctionManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.hrithikvish.nanobot.agent.AgentOrchestrator
import com.hrithikvish.nanobot.agent.AgentStep
import com.hrithikvish.nanobot.ai.GeminiNanoClient
import com.hrithikvish.nanobot.domain.appfunction.AppFunctionTool
import com.hrithikvish.nanobot.domain.appfunction.GeminiToolConverter
import com.hrithikvish.nanobot.domain.appfunction.GetAppFunctionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ModelStatus {
    data object Checking : ModelStatus
    data class Downloading(val bytes: Long) : ModelStatus
    data object Ready : ModelStatus
    data class Unavailable(val reason: String) : ModelStatus
}

enum class ToolStatus {
    AwaitingConfirmation,
    Running,
    Done,
    Failed,
    Cancelled,
}

sealed interface ChatMessage {
    val id: Long

    data class User(
        override val id: Long,
        val text: String,
    ) : ChatMessage

    data class Assistant(
        override val id: Long,
        val text: String,
    ) : ChatMessage

    data class Tool(
        override val id: Long,
        val userMessage: String,
        val call: AgentStep.ToolCall,
        val status: ToolStatus,
        /** The result JSON when [status] is Done, or the error when it is Failed. */
        val output: String? = null,
    ) : ChatMessage
}

data class ChatUiState(
    val modelStatus: ModelStatus = ModelStatus.Checking,
    val appFunctionsSupported: Boolean = true,
    val tools: List<AppFunctionTool> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val busy: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val nano: GeminiNanoClient,
    private val orchestrator: AgentOrchestrator,
    private val getAppFunctions: GetAppFunctionsUseCase,
    private val toolConverter: GeminiToolConverter,
    appFunctionManager: AppFunctionManager?,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChatUiState(appFunctionsSupported = appFunctionManager != null)
    )
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var nextId = 0L

    init {
        viewModelScope.launch { prepareModel() }
        viewModelScope.launch {
            getAppFunctions().collect { functions ->
                _state.update { it.copy(tools = toolConverter(functions)) }
            }
        }
    }

    fun refreshTools() {
        viewModelScope.launch {
            val tools = toolConverter(getAppFunctions.search())
            _state.update { it.copy(tools = tools) }
        }
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.busy) return
        add(ChatMessage.User(nextId++, message))
        runBusy {
            when (val step = orchestrator.decide(message, _state.value.tools)) {
                is AgentStep.Reply -> add(ChatMessage.Assistant(nextId++, step.text))
                is AgentStep.ToolCall -> add(
                    ChatMessage.Tool(
                        id = nextId++,
                        userMessage = message,
                        call = step,
                        status = ToolStatus.AwaitingConfirmation,
                    )
                )
            }
        }
    }

    fun confirmTool(id: Long) {
        val tool = _state.value.messages.firstOrNull { it.id == id } as? ChatMessage.Tool ?: return
        if (tool.status != ToolStatus.AwaitingConfirmation || _state.value.busy) return
        updateTool(id) { it.copy(status = ToolStatus.Running) }
        runBusy {
            val result = try {
                orchestrator.execute(tool.call)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateTool(id) {
                    it.copy(
                        status = ToolStatus.Failed,
                        output = e.message ?: e.javaClass.simpleName,
                    )
                }
                return@runBusy
            }
            updateTool(id) { it.copy(status = ToolStatus.Done, output = result) }
            add(ChatMessage.Assistant(nextId++, orchestrator.explain(tool.userMessage, tool.call, result)))
        }
    }

    fun cancelTool(id: Long) = updateTool(id) { tool ->
        if (tool.status == ToolStatus.AwaitingConfirmation) {
            tool.copy(status = ToolStatus.Cancelled)
        } else {
            tool
        }
    }

    override fun onCleared() = nano.close()

    private suspend fun prepareModel() {
        try {
            when (nano.checkStatus()) {
                FeatureStatus.UNAVAILABLE -> {
                    return setModelStatus(
                        ModelStatus.Unavailable("Gemini Nano is not available on this device.")
                    )
                }

                FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                    nano.download().collect { status ->
                        when (status) {
                            is DownloadStatus.DownloadStarted -> setModelStatus(ModelStatus.Downloading(0))
                            is DownloadStatus.DownloadProgress -> {
                                setModelStatus(ModelStatus.Downloading(status.totalBytesDownloaded))
                            }

                            is DownloadStatus.DownloadFailed -> throw status.e
                            else -> Unit
                        }
                    }
                }
            }
            if (!nano.isStructuredOutputAvailable()) {
                return setModelStatus(
                    ModelStatus.Unavailable("This Gemini Nano version does not support structured output.")
                )
            }
            nano.warmup()
            setModelStatus(ModelStatus.Ready)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setModelStatus(ModelStatus.Unavailable("Gemini Nano failed to start: ${e.message}"))
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                add(ChatMessage.Assistant(nextId++, "Error: ${e.message ?: e.javaClass.simpleName}"))
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun add(message: ChatMessage) = _state.update { it.copy(messages = it.messages + message) }

    private fun setModelStatus(status: ModelStatus) = _state.update { it.copy(modelStatus = status) }

    private fun updateTool(id: Long, change: (ChatMessage.Tool) -> ChatMessage.Tool) = _state.update { state ->
        state.copy(
            messages = state.messages.map { message ->
                if (message.id == id && message is ChatMessage.Tool) change(message) else message
            }
        )
    }
}
