package com.hrithikvish.nanobot.ui.appfunctions

import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBooleanTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDataTypeMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionFloatTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionLongTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.hrithikvish.nanobot.domain.appfunction.resolve
import com.hrithikvish.nanobot.ui.appfunctions.model.ToolParameterUiModel
import com.hrithikvish.nanobot.ui.appfunctions.model.ToolPropertyUiModel
import com.hrithikvish.nanobot.ui.appfunctions.model.ToolResponseUiModel
import com.hrithikvish.nanobot.ui.appfunctions.model.ToolUiModel
import javax.inject.Inject
import javax.inject.Singleton

/** Parses [AppFunctionMetadata] into rich [ToolUiModel]s for UI rendering. */
@Singleton
class AppFunctionMetadataParser @Inject constructor() {

    fun parse(function: AppFunctionMetadata): ToolUiModel = try {
        val methodName = function.id.substringAfterLast('#').substringAfterLast('.')
        val parameters = function.parameters.map { param ->
            val resolvedType = param.dataType.safeResolve(function.components)
            val nestedProps = if (resolvedType is AppFunctionObjectTypeMetadata) {
                extractProperties(resolvedType, function.components)
            } else if (resolvedType is AppFunctionArrayTypeMetadata) {
                val itemType = resolvedType.itemType.safeResolve(function.components)
                if (itemType is AppFunctionObjectTypeMetadata) {
                    extractProperties(itemType, function.components)
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val paramDesc = param.description.ifBlank {
                (resolvedType as? AppFunctionObjectTypeMetadata)?.description.orEmpty()
            }

            ToolParameterUiModel(
                name = param.name,
                typeDisplayName = formatTypeName(param.dataType, function.components),
                isRequired = param.isRequired,
                description = paramDesc,
                nestedProperties = nestedProps,
            )
        }

        val responseType = function.response.valueType.safeResolve(function.components)
        val responseProperties = if (responseType is AppFunctionObjectTypeMetadata) {
            extractProperties(responseType, function.components)
        } else if (responseType is AppFunctionArrayTypeMetadata) {
            val itemType = responseType.itemType.safeResolve(function.components)
            if (itemType is AppFunctionObjectTypeMetadata) {
                extractProperties(itemType, function.components)
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        val responseDesc = resolveDescription(responseType)

        val response = ToolResponseUiModel(
            typeDisplayName = formatTypeName(function.response.valueType, function.components),
            description = responseDesc,
            nestedProperties = responseProperties,
        )

        ToolUiModel(
            id = function.id,
            name = methodName,
            description = function.description.trim(),
            packageName = function.packageName,
            isEnabled = true,
            parameters = parameters,
            response = response,
            isExpanded = false,
        )
    } catch (e: Exception) {
        val fallbackMethodName = function.id.substringAfterLast('#').substringAfterLast('.')
        ToolUiModel(
            id = function.id,
            name = fallbackMethodName,
            description = function.description.trim(),
            packageName = function.packageName,
            isEnabled = true,
            parameters = function.parameters.map {
                ToolParameterUiModel(
                    name = it.name,
                    typeDisplayName = "Any",
                    isRequired = it.isRequired,
                    description = it.description,
                )
            },
            response = ToolResponseUiModel(
                typeDisplayName = "Any",
                description = "",
            ),
            isExpanded = false,
        )
    }

    private fun AppFunctionDataTypeMetadata.safeResolve(
        components: AppFunctionComponentsMetadata,
    ): AppFunctionDataTypeMetadata = if (this is AppFunctionReferenceTypeMetadata) {
        components.dataTypes[referenceDataType]?.safeResolve(components) ?: this
    } else {
        this
    }

    private fun extractProperties(
        objectType: AppFunctionObjectTypeMetadata,
        components: AppFunctionComponentsMetadata,
    ): List<ToolPropertyUiModel> = objectType.properties.map { (name, type) ->
        val resolved = type.resolve(components)
        val desc = resolveDescription(resolved).ifBlank { resolveDescription(type) }
        ToolPropertyUiModel(
            name = name,
            typeDisplayName = formatTypeName(type, components),
            isRequired = name in objectType.required,
            description = desc,
        )
    }

    private fun resolveDescription(type: AppFunctionDataTypeMetadata?): String {
        if (type == null) return ""
        return when (type) {
            is AppFunctionStringTypeMetadata -> type.description
            is AppFunctionIntTypeMetadata -> type.description
            is AppFunctionLongTypeMetadata -> type.description
            is AppFunctionDoubleTypeMetadata -> type.description
            is AppFunctionFloatTypeMetadata -> type.description
            is AppFunctionBooleanTypeMetadata -> type.description
            is AppFunctionObjectTypeMetadata -> type.description
            is AppFunctionArrayTypeMetadata -> type.description
            else -> ""
        }
    }

    fun formatTypeName(
        type: AppFunctionDataTypeMetadata?,
        components: AppFunctionComponentsMetadata,
    ): String {
        if (type == null) return "Void"
        return when (val resolved = type.resolve(components)) {
            is AppFunctionStringTypeMetadata -> {
                val enums = resolved.enumValues
                if (!enums.isNullOrEmpty()) {
                    "String (${enums.joinToString(", ") { "\"$it\"" }})"
                } else {
                    "String"
                }
            }
            is AppFunctionIntTypeMetadata -> {
                val enums = resolved.enumValues
                if (!enums.isNullOrEmpty()) {
                    "Int (${enums.joinToString(", ")})"
                } else {
                    "Int"
                }
            }
            is AppFunctionLongTypeMetadata -> "Long"
            is AppFunctionFloatTypeMetadata -> "Float"
            is AppFunctionDoubleTypeMetadata -> "Double"
            is AppFunctionBooleanTypeMetadata -> "Boolean"
            is AppFunctionArrayTypeMetadata -> {
                "List<${formatTypeName(resolved.itemType, components)}>"
            }
            is AppFunctionObjectTypeMetadata -> {
                val name = resolved.qualifiedName?.substringAfterLast('.')
                if (name.isNullOrBlank()) "Object" else "Object ($name)"
            }
            else -> "Any"
        }
    }
}
