package com.hrithikvish.nanobot.domain.appfunction

import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionDoubleTypeMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiToolConverterTest {

    // A common shape: one parameter that references an object type.
    private val params = "com.example.finance.model.LoanDetails"
    private val components = AppFunctionComponentsMetadata(
        mapOf(
            params to AppFunctionObjectTypeMetadata(
                properties = mapOf(
                    "tenureMonths" to AppFunctionIntTypeMetadata(
                        isNullable = false,
                        description = "The loan tenure in months.",
                    ),
                    "principal" to AppFunctionDoubleTypeMetadata(isNullable = false),
                    "frequency" to AppFunctionStringTypeMetadata(isNullable = true),
                ),
                required = listOf("tenureMonths", "principal"),
                qualifiedName = params,
                isNullable = false,
            ),
        ),
    )

    private fun function(packageName: String) = AppFunctionMetadata(
        id = "com.example.finance.LoanService#calculateEmi",
        packageName = packageName,
        isEnabled = true,
        schema = null,
        parameters = listOf(
            AppFunctionParameterMetadata(
                name = "params",
                isRequired = true,
                dataType = AppFunctionReferenceTypeMetadata(params, isNullable = false),
                description = "The loan details.",
            ),
        ),
        response = AppFunctionResponseMetadata(AppFunctionStringTypeMetadata(isNullable = false)),
        components = components,
        description = "Calculates the monthly payment.\nOnly fixed-rate loans are supported.",
    )

    @Test
    fun shortNames_areUniqueAndSchemaResolvesReferences() {
        val tools = GeminiToolConverter()(
            listOf(
                function("com.example.finance"),
                function("com.example.finance.beta"),
            )
        )

        assertEquals(listOf("calculateEmi", "calculateEmi_2"), tools.map { it.name })
        assertEquals("calculateEmi_2: Calculates the monthly payment.", tools[1].summary)

        val schema = JSONObject(tools[0].schema)
        assertEquals("com.example.finance", schema.getString("app"))
        assertEquals("params", schema.getJSONArray("required").getString(0))
        val param = schema.getJSONObject("parameters").getJSONObject("params")
        assertEquals("object", param.getString("type"))
        assertEquals("The loan details.", param.getString("description"))
        val properties = param.getJSONObject("properties")
        assertEquals("integer", properties.getJSONObject("tenureMonths").getString("type"))
        assertEquals("The loan tenure in months.", properties.getJSONObject("tenureMonths").getString("description"))
        assertEquals("number", properties.getJSONObject("principal").getString("type"))
        assertEquals("string", properties.getJSONObject("frequency").getString("type"))
    }
}
