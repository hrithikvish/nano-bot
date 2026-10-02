package com.hrithikvish.nanobot.ui.appfunctions

import androidx.appfunctions.metadata.AppFunctionArrayTypeMetadata
import androidx.appfunctions.metadata.AppFunctionBooleanTypeMetadata
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionIntTypeMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionObjectTypeMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionReferenceTypeMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFunctionMetadataParserTest {

    private val parser = AppFunctionMetadataParser()

    private val sampleObjectType = "com.example.notes.model.Note"
    private val components = AppFunctionComponentsMetadata(
        mapOf(
            sampleObjectType to AppFunctionObjectTypeMetadata(
                properties = mapOf(
                    "title" to AppFunctionStringTypeMetadata(
                        isNullable = false,
                        description = "The note title.",
                    ),
                    "id" to AppFunctionIntTypeMetadata(isNullable = false),
                    "tags" to AppFunctionArrayTypeMetadata(
                        itemType = AppFunctionStringTypeMetadata(isNullable = false),
                        isNullable = true,
                    ),
                ),
                required = listOf("title", "id"),
                qualifiedName = sampleObjectType,
                isNullable = false,
            ),
        ),
    )

    @Test
    fun parse_extractsParametersAndNestedProperties() {
        val function = AppFunctionMetadata(
            id = "com.example.notes.NotesService#createNote",
            packageName = "com.example.notes",
            isEnabled = true,
            schema = null,
            parameters = listOf(
                AppFunctionParameterMetadata(
                    name = "note",
                    isRequired = true,
                    dataType = AppFunctionReferenceTypeMetadata(sampleObjectType, isNullable = false),
                    description = "The note to create.",
                ),
                AppFunctionParameterMetadata(
                    name = "pinned",
                    isRequired = false,
                    dataType = AppFunctionBooleanTypeMetadata(isNullable = false),
                    description = "Whether to pin the note.",
                ),
            ),
            response = AppFunctionResponseMetadata(
                valueType = AppFunctionStringTypeMetadata(
                    enumValues = setOf("SUCCESS", "FAILED"),
                    isNullable = false,
                ),
            ),
            components = components,
            description = "Creates a new note with the given details.",
        )

        val tool = parser.parse(function)

        assertEquals("createNote", tool.name)
        assertEquals("com.example.notes.NotesService#createNote", tool.id)
        assertEquals("com.example.notes", tool.packageName)
        assertEquals("Creates a new note with the given details.", tool.description)
        assertTrue(tool.isEnabled)

        // Parameters
        assertEquals(2, tool.parameters.size)
        val param0 = tool.parameters[0]
        assertEquals("note", param0.name)
        assertEquals("Object (Note)", param0.typeDisplayName)
        assertTrue(param0.isRequired)
        assertEquals("The note to create.", param0.description)
        assertEquals(3, param0.nestedProperties.size)

        val titleProp = param0.nestedProperties.first { it.name == "title" }
        assertEquals("String", titleProp.typeDisplayName)
        assertTrue(titleProp.isRequired)
        assertEquals("The note title.", titleProp.description)

        val tagsProp = param0.nestedProperties.first { it.name == "tags" }
        assertEquals("List<String>", tagsProp.typeDisplayName)
        assertFalse(tagsProp.isRequired)

        val param1 = tool.parameters[1]
        assertEquals("pinned", param1.name)
        assertEquals("Boolean", param1.typeDisplayName)
        assertFalse(param1.isRequired)

        // Response
        assertEquals("String (\"SUCCESS\", \"FAILED\")", tool.response.typeDisplayName)
    }
}
