package com.hrithikvish.nanobot.ui.appfunctions

import android.graphics.drawable.Drawable
import androidx.appfunctions.metadata.AppFunctionComponentsMetadata
import androidx.appfunctions.metadata.AppFunctionMetadata
import androidx.appfunctions.metadata.AppFunctionParameterMetadata
import androidx.appfunctions.metadata.AppFunctionResponseMetadata
import androidx.appfunctions.metadata.AppFunctionStringTypeMetadata
import com.hrithikvish.nanobot.domain.appfunction.GetAppFunctionsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppFunctionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val parser = AppFunctionMetadataParser()

    private val sampleFunction1 = AppFunctionMetadata(
        id = "com.google.android.deskclock.ClockService#setAlarm",
        packageName = "com.google.android.deskclock",
        isEnabled = true,
        schema = null,
        parameters = listOf(
            AppFunctionParameterMetadata(
                name = "time",
                isRequired = true,
                dataType = AppFunctionStringTypeMetadata(isNullable = false, description = "Alarm time HH:mm"),
                description = "The time to set the alarm for.",
            ),
        ),
        response = AppFunctionResponseMetadata(
            valueType = AppFunctionStringTypeMetadata(isNullable = false),
        ),
        components = AppFunctionComponentsMetadata(emptyMap()),
        description = "Sets an alarm at the given time.",
    )

    private val sampleFunction2 = AppFunctionMetadata(
        id = "com.google.android.calendar.CalendarService#createEvent",
        packageName = "com.google.android.calendar",
        isEnabled = true,
        schema = null,
        parameters = listOf(
            AppFunctionParameterMetadata(
                name = "title",
                isRequired = true,
                dataType = AppFunctionStringTypeMetadata(isNullable = false, description = "Event title"),
                description = "The title of the calendar event.",
            ),
        ),
        response = AppFunctionResponseMetadata(
            valueType = AppFunctionStringTypeMetadata(isNullable = false),
        ),
        components = AppFunctionComponentsMetadata(emptyMap()),
        description = "Creates an event in the calendar.",
    )

    private class FakeGetAppFunctionsUseCase(
        private val functions: List<AppFunctionMetadata>,
    ) : GetAppFunctionsUseCase(null) {
        override fun invoke(): Flow<List<AppFunctionMetadata>> = flowOf(functions)
        override suspend fun search(): List<AppFunctionMetadata> = functions
    }

    private class FakeAppDetailsProvider : AppDetailsProvider(context = null) {
        override fun getAppInfo(packageName: String): Pair<String, Drawable?> {
            return when (packageName) {
                "com.google.android.deskclock" -> Pair("Clock", null)
                "com.google.android.calendar" -> Pair("Calendar", null)
                else -> Pair(packageName, null)
            }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialState_loadsAppsAndToolsGroupedCorrectly() = runTest {
        val fakeGetAppFunctions = FakeGetAppFunctionsUseCase(listOf(sampleFunction1, sampleFunction2))
        val fakeAppDetailsProvider = FakeAppDetailsProvider()

        val viewModel = AppFunctionsViewModel(
            getAppFunctions = fakeGetAppFunctions,
            parser = parser,
            appDetailsProvider = fakeAppDetailsProvider,
            appFunctionManager = null,
        )

        advanceUntilIdle()

        val state = viewModel.state.first { !it.isLoading }
        assertEquals(2, state.totalAppsCount)
        assertEquals(2, state.totalToolsCount)
        assertEquals(2, state.apps.size)

        val clockApp = state.apps.first { it.packageName == "com.google.android.deskclock" }
        assertEquals("Clock", clockApp.appName)
        assertEquals(1, clockApp.tools.size)
        assertEquals("setAlarm", clockApp.tools[0].name)
    }

    @Test
    fun searchFiltering_filtersByToolNameAndAppName() = runTest {
        val fakeGetAppFunctions = FakeGetAppFunctionsUseCase(listOf(sampleFunction1, sampleFunction2))
        val fakeAppDetailsProvider = FakeAppDetailsProvider()

        val viewModel = AppFunctionsViewModel(
            getAppFunctions = fakeGetAppFunctions,
            parser = parser,
            appDetailsProvider = fakeAppDetailsProvider,
            appFunctionManager = null,
        )

        advanceUntilIdle()

        viewModel.onSearchQueryChanged("alarm")
        advanceUntilIdle()

        val state = viewModel.state.first { it.searchQuery == "alarm" }
        assertEquals(1, state.apps.size)
        assertEquals("Clock", state.apps[0].appName)
        assertEquals("setAlarm", state.apps[0].tools[0].name)
    }

    @Test
    fun toggleAppExpanded_togglesState() = runTest {
        val fakeGetAppFunctions = FakeGetAppFunctionsUseCase(listOf(sampleFunction1, sampleFunction2))
        val fakeAppDetailsProvider = FakeAppDetailsProvider()

        val viewModel = AppFunctionsViewModel(
            getAppFunctions = fakeGetAppFunctions,
            parser = parser,
            appDetailsProvider = fakeAppDetailsProvider,
            appFunctionManager = null,
        )

        advanceUntilIdle()

        val initialState = viewModel.state.first { !it.isLoading }
        val deskclock = initialState.apps.first { it.packageName == "com.google.android.deskclock" }
        assertTrue(deskclock.isExpanded)

        viewModel.toggleAppExpanded("com.google.android.deskclock")
        advanceUntilIdle()

        val updatedState = viewModel.state.value
        val deskclockUpdated = updatedState.apps.first { it.packageName == "com.google.android.deskclock" }
        assertFalse(deskclockUpdated.isExpanded)
    }
}
