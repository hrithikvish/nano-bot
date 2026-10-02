package com.hrithikvish.nanobot.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.painterResource
import com.hrithikvish.nanobot.R
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    instrumentationError: String?,
    onNavigateToAppFunctions: (() -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val canSend = state.modelStatus == ModelStatus.Ready && !state.busy && input.isNotBlank()
    val send = {
        viewModel.send(input)
        input = ""
    }

    // Keep the newest item in view when a message arrives, a tool card changes, or the keyboard opens.
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(state.messages.lastOrNull(), state.busy, imeVisible) {
        // Item 0 is the status banner. The progress item follows the messages while busy.
        val lastIndex = state.messages.size + if (state.busy) 1 else 0
        // A large offset scrolls to the bottom of a tall last item. The list stops at its end.
        listState.animateScrollToItem(lastIndex, scrollOffset = Int.MAX_VALUE)
    }

    Scaffold(
        // safeDrawing covers the status bar, the navigation bar, display cutouts, and the keyboard.
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Top,
                ),
                title = {
                    Column {
                        Text("NanoBot")
                        Text(
                            text = "${state.tools.size} tools found",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                actions = {
                    if (onNavigateToAppFunctions != null) {
                        IconButton(onClick = onNavigateToAppFunctions) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_widgets),
                                contentDescription = "View App Functions and Tools",
                            )
                        }
                    }
                    TextButton(onClick = viewModel::refreshTools) {
                        Text("Refresh")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                // The navigation bar inset when the keyboard is closed, the keyboard inset when it is open.
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        )
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask NanoBot") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) send() }),
                )
                Button(onClick = send, enabled = canSend) {
                    Text("Send")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                StatusBanner(state, instrumentationError)
            }
            items(state.messages, key = { it.id }) { message ->
                when (message) {
                    is ChatMessage.User -> Bubble(text = message.text, fromUser = true)
                    is ChatMessage.Assistant -> Bubble(text = message.text, fromUser = false)
                    is ChatMessage.Tool -> ToolCard(
                        message = message,
                        enabled = !state.busy,
                        onRun = { viewModel.confirmTool(message.id) },
                        onCancel = { viewModel.cancelTool(message.id) },
                    )
                }
            }
            if (state.busy) {
                item {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusBanner(state: ChatUiState, instrumentationError: String?) {
    val lines = buildList {
        when (val status = state.modelStatus) {
            ModelStatus.Checking -> add("Checking Gemini Nano...")
            is ModelStatus.Downloading -> add("Downloading Gemini Nano: ${status.bytes / 1_000_000} MB")
            is ModelStatus.Unavailable -> add(status.reason)
            ModelStatus.Ready -> Unit
        }
        if (!state.appFunctionsSupported) add("This device does not support AppFunctions.")
        instrumentationError?.let { add("Privileged mode failed: $it") }
    }
    if (lines.isEmpty()) return
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Text(
            text = lines.joinToString("\n"),
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun Bubble(text: String, fromUser: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (fromUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        OutlinedCard(
            modifier = Modifier.widthIn(max = 320.dp),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (fromUser) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun ToolCard(
    message: ChatMessage.Tool,
    enabled: Boolean,
    onRun: () -> Unit,
    onCancel: () -> Unit,
) {
    val tool = message.call.tool
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Tool: ${tool.name}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = tool.function.packageName,
                style = MaterialTheme.typography.labelSmall,
            )
            Code(message.call.arguments.toString(2))
            when (message.status) {
                ToolStatus.AwaitingConfirmation -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onRun, enabled = enabled) {
                        Text("Run")
                    }
                    OutlinedButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }

                ToolStatus.Running -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp))
                    Text("Running...")
                }

                ToolStatus.Done -> {
                    Text(
                        text = "Result",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Code(message.output.orEmpty().prettyJson())
                }

                ToolStatus.Failed -> Text(
                    text = "Failed: ${message.output}",
                    color = MaterialTheme.colorScheme.error,
                )

                ToolStatus.Cancelled -> Text(
                    text = "Cancelled",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun Code(text: String) {
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun String.prettyJson(): String = runCatching { JSONObject(this).toString(2) }.getOrDefault(this)
