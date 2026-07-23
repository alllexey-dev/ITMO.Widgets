package dev.alllexey.itmowidgets.domain.repository

import dev.alllexey.itmowidgets.core.result.AppResult

interface SportActionRepository {

    fun areCommunityServicesEnabled(): Boolean

    suspend fun signIn(lessonId: Long): AppResult<Unit>

    suspend fun signOut(lessonId: Long): AppResult<Unit>

    suspend fun createFreeSignEntry(lessonId: Long, forceSign: Boolean): AppResult<Unit>

    suspend fun cancelFreeSignEntry(entryId: Long): AppResult<Unit>

    suspend fun createAutoSignEntry(prototypeLessonId: Long): AppResult<Unit>

    suspend fun cancelAutoSignEntry(entryId: Long): AppResult<Unit>
}
