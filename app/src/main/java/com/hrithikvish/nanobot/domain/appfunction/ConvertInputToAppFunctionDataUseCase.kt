package com.hrithikvish.nanobot.domain.appfunction

import androidx.appfunctions.AppFunctionData
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
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

/**
 * Converts the JSON arguments from Gemini Nano to type-safe [AppFunctionData] for one function.
 * Throws [IllegalArgumentException] when an argument is missing or has the wrong type.
 */
class ConvertInputToAppFunctionDataUseCase @Inject constructor() {

    operator fun invoke(function: AppFunctionMetadata, arguments: JSONObject): AppFunctionData {
        val builder = AppFunctionData.Builder(function.parameters, function.components)
        for (parameter in function.parameters) {
            if (arguments.isNull(parameter.name)) {
                require(!parameter.isRequired) { "Missing required argument '${parameter.name}'." }
                continue
            }
            builder.set(
                name = parameter.name,
                type = parameter.dataType,
                value = arguments.get(parameter.name),
                components = function.components,
            )
        }
        return builder.build()
    }

    private fun AppFunctionData.Builder.set(
        name: String,
        type: AppFunctionDataTypeMetadata,
        value: Any,
        components: AppFunctionComponentsMetadata,
    ) {
        when (val resolved = type.resolve(components)) {
            is AppFunctionStringTypeMetadata -> setString(name, value.toString())
            is AppFunctionIntTypeMetadata -> setInt(name, number(name, value).toInt())
            is AppFunctionLongTypeMetadata -> setLong(name, number(name, value).toLong())
            is AppFunctionDoubleTypeMetadata -> setDouble(name, number(name, value).toDouble())
            is AppFunctionFloatTypeMetadata -> setFloat(name, number(name, value).toFloat())
            is AppFunctionBooleanTypeMetadata -> setBoolean(name, value as? Boolean ?: value.toString().toBooleanStrict())
            is AppFunctionObjectTypeMetadata -> setAppFunctionData(name, objectOf(name, resolved, value, components))
            is AppFunctionArrayTypeMetadata -> setArray(
                name = name,
                type = resolved,
                values = value as? JSONArray ?: throw wrongType(name, "an array"),
                components = components,
            )
            else -> throw IllegalArgumentException("Argument '$name' has a type that NanoBot does not support.")
        }
    }

    private fun AppFunctionData.Builder.setArray(
        name: String,
        type: AppFunctionArrayTypeMetadata,
        values: JSONArray,
        components: AppFunctionComponentsMetadata,
    ) {
        val items = List(values.length()) { values.get(it) }
        when (val itemType = type.itemType.resolve(components)) {
            is AppFunctionStringTypeMetadata -> setStringList(
                name,
                items.map { it.toString() },
            )

            is AppFunctionIntTypeMetadata -> setIntArray(
                name,
                items.map { number(name, it).toInt() }.toIntArray(),
            )

            is AppFunctionLongTypeMetadata -> setLongArray(
                name,
                items.map { number(name, it).toLong() }.toLongArray(),
            )

            is AppFunctionDoubleTypeMetadata -> setDoubleArray(
                name,
                items.map { number(name, it).toDouble() }.toDoubleArray(),
            )

            is AppFunctionFloatTypeMetadata -> setFloatArray(
                name,
                items.map { number(name, it).toFloat() }.toFloatArray(),
            )

            is AppFunctionBooleanTypeMetadata -> setBooleanArray(
                name,
                items.map { it as? Boolean ?: it.toString().toBooleanStrict() }.toBooleanArray(),
            )

            is AppFunctionObjectTypeMetadata -> setAppFunctionDataList(
                name,
                items.map { objectOf(name, itemType, it, components) },
            )

            else -> throw IllegalArgumentException("Argument '$name' has an item type that NanoBot does not support.")
        }
    }

    private fun objectOf(
        name: String,
        type: AppFunctionObjectTypeMetadata,
        value: Any,
        components: AppFunctionComponentsMetadata,
    ): AppFunctionData {
        val json = value as? JSONObject ?: throw wrongType(name, "an object")
        val builder = AppFunctionData.Builder(type, components)
        // A missing property stays unset. The target app decides if it is required.
        for ((property, propertyType) in type.properties) {
            if (!json.isNull(property)) {
                builder.set(property, propertyType, json.get(property), components)
            }
        }
        return builder.build()
    }

    // Gemini Nano sometimes writes a number as a string, for example "30".
    private fun number(name: String, value: Any): Number =
        value as? Number ?: value.toString().toDoubleOrNull() ?: throw wrongType(name, "a number")

    private fun wrongType(name: String, expected: String) =
        IllegalArgumentException("Argument '$name' must be $expected.")
}
