package dev.alllexey.itmowidgets.di

import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.R
import dev.alllexey.itmowidgets.core.location.BuildingDirectory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocationModule {

    @Provides
    @Singleton
    fun provideBuildingDirectory(@ApplicationContext context: Context, gson: Gson): BuildingDirectory {
        val json = context.resources.openRawResource(R.raw.itmo_buildings).bufferedReader().use { it.readText() }
        return BuildingDirectory.parse(json, gson)
    }
}
