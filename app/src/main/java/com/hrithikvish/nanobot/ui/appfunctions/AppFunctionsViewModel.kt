package com.hrithikvish.nanobot.ui.appfunctions

import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hrithikvish.nanobot.domain.appfunction.GetAppFunctionsUseCase
import com.hrithikvish.nanobot.ui.appfunctions.model.AppFunctionsUiState
import com.hrithikvish.nanobot.ui.appfunctions.model.AppWithToolsUiModel
import com.hrithikvish.nanobot.ui.appfunctions.model.ToolUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AppFunctionsViewModel @Inject constructor(
    private val getAppFunctions: GetAppFunctionsUseCase,
    private val parser: AppFunctionMetadataParser,
    private val appDetailsProvider: AppDetailsProvider,
    appFunctionManager: AppFunctionManager?,
) : ViewModel() {

    private val isSupported = appFunctionManager != null
    private val rawFunctions = MutableStateFlow<List<AppFunctionMetadata>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val collapsedAppPackages = MutableStateFlow<Set<String>>(emptySet())
    private val expandedToolIds = MutableStateFlow<Set<String>>(emptySet())
    private val isLoading = MutableStateFlow(true)

    val state: StateFlow<AppFunctionsUiState> = combine(
        rawFunctions,
        searchQuery,
        collapsedAppPackages,
        expandedToolIds,
        isLoading,
    ) { functions, query, collapsedApps, expandedTools, loading ->
        buildUiState(
            functions = functions,
            query = query,
            collapsedApps = collapsedApps,
            expandedTools = expandedTools,
            loading = loading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppFunctionsUiState(
            isLoading = true,
            isAppFunctionsSupported = isSupported,
        ),
    )

    init {
        viewModelScope.launch {
            getAppFunctions().collect { functions ->
                rawFunctions.value = functions
                isLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun toggleAppExpanded(packageName: String) {
        collapsedAppPackages.update { current ->
            if (packageName in current) current - packageName else current + packageName
        }
    }

    fun toggleToolExpanded(toolId: String) {
        expandedToolIds.update { current ->
            if (toolId in current) current - toolId else current + toolId
        }
    }

    fun expandAll() {
        collapsedAppPackages.value = emptySet()
        val allToolIds = rawFunctions.value.map { it.id }.toSet()
        expandedToolIds.value = allToolIds
    }

    fun collapseAll() {
        val allPackages = rawFunctions.value.map { it.packageName }.toSet()
        collapsedAppPackages.value = allPackages
        expandedToolIds.value = emptySet()
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading.value = true
            val functions = getAppFunctions.search()
            rawFunctions.value = functions
            isLoading.value = false
        }
    }

    private fun buildUiState(
        functions: List<AppFunctionMetadata>,
        query: String,
        collapsedApps: Set<String>,
        expandedTools: Set<String>,
        loading: Boolean,
    ): AppFunctionsUiState {
        val totalTools = functions.size
        val grouped = functions.groupBy { it.packageName }
        val totalApps = grouped.size

        val trimmedQuery = query.trim()

        val appUiModels = grouped.mapNotNull { (packageName, pkgFunctions) ->
            val (appName, icon) = appDetailsProvider.getAppInfo(packageName)
            val parsedTools = pkgFunctions.map { func ->
                val parsed = parser.parse(func)
                parsed.copy(isExpanded = parsed.id in expandedTools || trimmedQuery.isNotBlank())
            }

            val matchingTools = if (trimmedQuery.isBlank()) {
                parsedTools
            } else {
                val appMatches = appName.contains(trimmedQuery, ignoreCase = true) ||
                    packageName.contains(trimmedQuery, ignoreCase = true)

                if (appMatches) {
                    parsedTools
                } else {
                    parsedTools.filter { tool ->
                        toolMatchesQuery(tool, trimmedQuery)
                    }
                }
            }

            if (matchingTools.isEmpty() && trimmedQuery.isNotBlank()) {
                null
            } else {
                AppWithToolsUiModel(
                    packageName = packageName,
                    appName = appName,
                    appIcon = icon,
                    tools = matchingTools,
                    isExpanded = packageName !in collapsedApps || trimmedQuery.isNotBlank(),
                )
            }
        }.sortedBy { it.appName.lowercase() }

        return AppFunctionsUiState(
            isLoading = loading,
            searchQuery = query,
            apps = appUiModels,
            totalToolsCount = totalTools,
            totalAppsCount = totalApps,
            isAppFunctionsSupported = isSupported,
        )
    }

    private fun toolMatchesQuery(tool: ToolUiModel, query: String): Boolean {
        if (tool.name.contains(query, ignoreCase = true)) return true
        if (tool.description.contains(query, ignoreCase = true)) return true
        if (tool.id.contains(query, ignoreCase = true)) return true
        if (tool.response.typeDisplayName.contains(query, ignoreCase = true)) return true
        if (tool.response.description.contains(query, ignoreCase = true)) return true
        return tool.parameters.any { param ->
            param.name.contains(query, ignoreCase = true) ||
                param.description.contains(query, ignoreCase = true) ||
                param.typeDisplayName.contains(query, ignoreCase = true) ||
                param.nestedProperties.any { prop ->
                    prop.name.contains(query, ignoreCase = true) ||
                        prop.description.contains(query, ignoreCase = true) ||
                        prop.typeDisplayName.contains(query, ignoreCase = true)
                }
        }
    }
}
