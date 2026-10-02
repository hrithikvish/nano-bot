package com.hrithikvish.nanobot.ui.appfunctions.model

import android.graphics.drawable.Drawable

/** UI State for the App Functions screen. */
data class AppFunctionsUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val apps: List<AppWithToolsUiModel> = emptyList(),
    val totalToolsCount: Int = 0,
    val totalAppsCount: Int = 0,
    val isAppFunctionsSupported: Boolean = true,
)

/** Represents an installed app that exposes one or more AppFunctions (tools). */
data class AppWithToolsUiModel(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?,
    val tools: List<ToolUiModel>,
    val isExpanded: Boolean = true,
)

/** Represents a single AppFunction / Tool available within an app. */
data class ToolUiModel(
    val id: String,
    val name: String,
    val description: String,
    val packageName: String,
    val isEnabled: Boolean,
    val parameters: List<ToolParameterUiModel>,
    val response: ToolResponseUiModel,
    val isExpanded: Boolean = false,
)

/** Represents an input parameter taken by an AppFunction. */
data class ToolParameterUiModel(
    val name: String,
    val typeDisplayName: String,
    val isRequired: Boolean,
    val description: String,
    val nestedProperties: List<ToolPropertyUiModel> = emptyList(),
)

/** Represents a nested property inside an Object parameter or response. */
data class ToolPropertyUiModel(
    val name: String,
    val typeDisplayName: String,
    val isRequired: Boolean,
    val description: String,
)

/** Represents the return value / output produced by an AppFunction. */
data class ToolResponseUiModel(
    val typeDisplayName: String,
    val description: String,
    val nestedProperties: List<ToolPropertyUiModel> = emptyList(),
)
