package com.hrithikvish.nanobot.di

import android.content.Context
import androidx.appfunctions.AppFunctionManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Null when the device does not support AppFunctions. */
    @Provides
    fun provideAppFunctionManager(@ApplicationContext context: Context): AppFunctionManager? =
        AppFunctionManager.getInstance(context)
}
