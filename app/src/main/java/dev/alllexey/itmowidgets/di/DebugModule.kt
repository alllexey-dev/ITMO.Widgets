package dev.alllexey.itmowidgets.di

import android.content.Context
import api.myitmo.MyItmo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.alllexey.itmowidgets.core.debug.DefaultSportLessonTemplateController
import dev.alllexey.itmowidgets.core.debug.DefaultSportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.debug.DebugRefreshTokenController
import dev.alllexey.itmowidgets.core.debug.DefaultDebugRefreshTokenController
import dev.alllexey.itmowidgets.core.debug.FileSportLessonTemplateStore
import dev.alllexey.itmowidgets.core.debug.FileSportScoreOverrideStore
import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateController
import dev.alllexey.itmowidgets.core.debug.SportLessonTemplateStore
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideController
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideProvider
import dev.alllexey.itmowidgets.core.debug.SportScoreOverrideStore
import dev.alllexey.itmowidgets.core.session.SessionDataCleaner
import dev.alllexey.itmowidgets.core.session.SessionTokenStore
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DebugModule {

    @Provides
    @Singleton
    fun provideSportScoreOverrideStore(
        @ApplicationContext context: Context
    ): SportScoreOverrideStore = FileSportScoreOverrideStore(
        File(context.noBackupFilesDir, SPORT_SCORE_OVERRIDE_FILE)
    )

    @Provides
    @Singleton
    fun provideDefaultSportScoreOverrideProvider(
        overrideStore: SportScoreOverrideStore
    ): DefaultSportScoreOverrideProvider = DefaultSportScoreOverrideProvider(overrideStore)

    @Provides
    fun provideSportScoreOverrideProvider(
        provider: DefaultSportScoreOverrideProvider
    ): SportScoreOverrideProvider = provider

    @Provides
    fun provideSportScoreOverrideController(
        provider: DefaultSportScoreOverrideProvider
    ): SportScoreOverrideController = provider

    @Provides
    @Singleton
    fun provideSportLessonTemplateStore(
        @ApplicationContext context: Context
    ): SportLessonTemplateStore = FileSportLessonTemplateStore(
        File(context.noBackupFilesDir, SPORT_LESSON_TEMPLATE_FILE)
    )

    @Provides
    @Singleton
    fun provideDefaultSportLessonTemplateController(
        templateStore: SportLessonTemplateStore
    ): DefaultSportLessonTemplateController =
        DefaultSportLessonTemplateController(templateStore)

    @Provides
    fun provideSportLessonTemplateController(
        provider: DefaultSportLessonTemplateController
    ): SportLessonTemplateController = provider

    @Provides
    @Singleton
    fun provideDebugRefreshTokenController(
        tokenStore: SessionTokenStore,
        myItmo: MyItmo,
        dataCleaners: Set<@JvmSuppressWildcards SessionDataCleaner>
    ): DebugRefreshTokenController {
        return DefaultDebugRefreshTokenController(
            tokenStore = tokenStore,
            myItmo = myItmo,
            dataCleaners = dataCleaners
        )
    }

    private const val SPORT_SCORE_OVERRIDE_FILE = "debug/sport_score_override"
    private const val SPORT_LESSON_TEMPLATE_FILE = "debug/sport_lesson_templates"
}
