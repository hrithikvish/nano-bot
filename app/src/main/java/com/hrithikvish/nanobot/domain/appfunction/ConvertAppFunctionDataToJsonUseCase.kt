package com.hrithikvish.nanobot.domain.appfunction

import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.ExecuteAppFunctionResponse.Success.Companion.PROPERTY_RETURN_VALUE
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

/** Converts the return value of an AppFunction to a JSON string, for Gemini Nano and the UI. */
class ConvertAppFunctionDataToJsonUseCase @Inject constructor() {

    operator fun invoke(function: AppFunctionMetadata, returnValue: AppFunctionData): String =
        valueOf(
            data = returnValue,
            name = PROPERTY_RETURN_VALUE,
            type = function.response.valueType,
            components = function.components,
        )?.toString() ?: "null"

    private fun valueOf(
        data: AppFunctionData,
        name: String,
        type: AppFunctionDataTypeMetadata,
        components: AppFunctionComponentsMetadata,
    ): Any? {
        if (!data.containsKey(name)) return null
        return when (val resolved = type.resolve(components)) {
            is AppFunctionStringTypeMetadata -> data.getString(name)
            is AppFunctionIntTypeMetadata -> data.getInt(name)
            is AppFunctionLongTypeMetadata -> data.getLong(name)
            is AppFunctionDoubleTypeMetadata -> data.getDouble(name)
            is AppFunctionFloatTypeMetadata -> data.getFloat(name)
            is AppFunctionBooleanTypeMetadata -> data.getBoolean(name)
            is AppFunctionObjectTypeMetadata -> data.getAppFunctionData(name)?.let { objectOf(it, resolved, components) }
            is AppFunctionArrayTypeMetadata -> when (val itemType = resolved.itemType.resolve(components)) {
                is AppFunctionStringTypeMetadata -> data.getStringList(name)?.let(::JSONArray)
                is AppFunctionIntTypeMetadata -> data.getIntArray(name)?.let { JSONArray(it.toList()) }
                is AppFunctionLongTypeMetadata -> data.getLongArray(name)?.let { JSONArray(it.toList()) }
                is AppFunctionDoubleTypeMetadata -> data.getDoubleArray(name)?.let { JSONArray(it.toList()) }
                is AppFunctionFloatTypeMetadata -> data.getFloatArray(name)?.let { JSONArray(it.toList()) }
                is AppFunctionBooleanTypeMetadata -> data.getBooleanArray(name)?.let { JSONArray(it.toList()) }
                is AppFunctionObjectTypeMetadata -> data.getAppFunctionDataList(name)?.let { list -> JSONArray(list.map { objectOf(it, itemType, components) }) }
                else -> null
            }
            else -> null
        }
    }

    private fun objectOf(
        data: AppFunctionData,
        type: AppFunctionObjectTypeMetadata,
        components: AppFunctionComponentsMetadata,
    ): JSONObject = JSONObject().apply {
        type.properties.forEach { (name, propertyType) ->
            valueOf(data, name, propertyType, components)?.let { put(name, it) }
        }
    }
}
