package com.hrithikvish.nanobot.domain.appfunction

import android.util.Log
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.metadata.AppFunctionMetadata
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart

/** Finds the AppFunctions of all installed apps, and finds them again when an app adds or removes one. */
class GetAppFunctionsUseCase @Inject constructor(
    private val appFunctionManager: AppFunctionManager?,
) {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<List<AppFunctionMetadata>> {
        val manager = appFunctionManager ?: return flowOf(emptyList())
        return manager.observeAppFunctions()
            .debounce(500.milliseconds)
            .mapLatest { search() }
            .onStart { emit(search()) }
    }

    /** Returns the AppFunctions that are on the device now. */
    suspend fun search(): List<AppFunctionMetadata> =
        appFunctionManager?.searchAppFunctions(AppFunctionSearchSpec()).orEmpty()
            .also { Log.d("NanoBot search()", it.toString()) }
}
