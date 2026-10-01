package com.hrithikvish.nanobot.domain.appfunction

import android.util.Log
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
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

/** An AppFunction with the short name that Gemini Nano uses to call it. */
data class AppFunctionTool(
    val name: String,
    val function: AppFunctionMetadata,
    /** "name: first sentence of the description", for the prompt that chooses a tool. */
    val summary: String,
    /** One-line JSON description of the tool and its parameters, for the prompt that fills the arguments. */
    val schema: String,
)

/** Converts AppFunction metadata to JSON tool descriptions for the Gemini Nano prompt. */
class GeminiToolConverter @Inject constructor() {

    operator fun invoke(functions: List<AppFunctionMetadata>): List<AppFunctionTool> {
        // A function ID looks like "com.example.SomeService#doThing". The model copies a short name more reliably.
        val nameCounts = mutableMapOf<String, Int>()
        return functions.map { function ->
            val base = function.id.substringAfterLast('#').substringAfterLast('.')
            val count = nameCounts.merge(base, 1, Int::plus)!!
            val name = if (count == 1) base else "${base}_$count"
            val parameters = JSONObject()
            function.parameters.forEach { parameter ->
                parameters.put(
                    parameter.name,
                    schemaOf(parameter.dataType, function.components, parameter.description),
                )
            }
            val schema = JSONObject()
                .put("name", name)
                .put("app", function.packageName)
                .put("description", function.description)
                .put("parameters", parameters)
                .put("required", JSONArray(function.parameters.filter { it.isRequired }.map { it.name }))
            AppFunctionTool(name, function, "$name: ${firstSentence(function.description)}", schema.toString())
        }.also {
            Log.d("NanoBot ToolConverter", it.toString())
        }
    }

    // Some system tools have descriptions of several thousand characters. The tool list must fit in Gemini Nano's context.
    private fun firstSentence(description: String): String =
        description.trim().substringBefore('\n').substringBefore(". ").take(120).ifBlank { "(no desc)" }

    private fun schemaOf(
        type: AppFunctionDataTypeMetadata,
        components: AppFunctionComponentsMetadata,
        description: String = "",
    ): JSONObject {
        val resolved = type.resolve(components)
        val schema = when (resolved) {
            is AppFunctionStringTypeMetadata -> JSONObject().put("type", "string")
                .apply { resolved.enumValues?.let { put("enum", JSONArray(it)) } }

            is AppFunctionIntTypeMetadata -> JSONObject().put("type", "integer")
                .apply { resolved.enumValues?.let { put("enum", JSONArray(it)) } }

            is AppFunctionLongTypeMetadata -> JSONObject().put("type", "integer")
            is AppFunctionDoubleTypeMetadata, is AppFunctionFloatTypeMetadata -> JSONObject().put("type", "number")
            is AppFunctionBooleanTypeMetadata -> JSONObject().put("type", "boolean")
            is AppFunctionArrayTypeMetadata -> JSONObject().put("type", "array")
                .put("items", schemaOf(resolved.itemType, components))

            is AppFunctionObjectTypeMetadata -> {
                val properties = JSONObject()
                resolved.properties.forEach { (name, propertyType) ->
                    properties.put(name, schemaOf(propertyType, components))
                }
                JSONObject().put("type", "object")
                    .put("properties", properties)
                    .put("required", JSONArray(resolved.required))
            }
            // ponytail: allOf/oneOf, bytes, and Parcelable types are not described. Add them when a target app uses them.
            else -> JSONObject().put("type", "unsupported")
        }
        return schema.apply {
            val text = description.ifBlank { resolved.description }
            if (text.isNotBlank()) put("description", text)
        }
    }
}

/** Returns the type that a reference points to, or this type. */
internal fun AppFunctionDataTypeMetadata.resolve(
    components: AppFunctionComponentsMetadata,
): AppFunctionDataTypeMetadata = if (this is AppFunctionReferenceTypeMetadata) {
    requireNotNull(components.dataTypes[referenceDataType]) {
        "Unknown type reference $referenceDataType."
    }.resolve(components)
} else {
    this
}
