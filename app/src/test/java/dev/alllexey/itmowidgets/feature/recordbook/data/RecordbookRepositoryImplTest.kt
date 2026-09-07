package dev.alllexey.itmowidgets.feature.recordbook.data

import dev.alllexey.itmowidgets.core.result.AppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.core.testing.myItmoStub
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RecordbookRepositoryImplTest {
    @Test fun `uses main plan and semester path and preserves absent scores`() = runTest {
        val repository = RecordbookRepositoryImpl(myItmoStub { request ->
            assertEquals("/api/record_book/123/2", request.url.encodedPath)
            """{"error_code":0,"result":[{"name":"  Тестовый предмет  ","discipline_id":1,"est_id":2,"control_type":" Зачет ","current_score":null,"rate":null,"attempt":null,"have_tree":false,"teacher":{"name":null,"surname":null,"patronymic":null}}]}"""
        })
        val subject = (repository.getSubjects(123, 2) as AppResult.Success).value.single()
        assertEquals("Тестовый предмет", subject.name)
        assertNull(subject.score)
        assertNull(subject.teacherName)
        assertFalse(subject.hasDetails)
    }

    @Test fun `preserves nullable control bounds and tree parent rather than coercing them to zero`() = runTest {
        val repository = RecordbookRepositoryImpl(myItmoStub { request ->
            assertEquals("/api/record_book/2", request.url.encodedPath)
            """{"error_code":0,"result":[{"id":3,"parent_id":1,"control_name":" Работа ","min_value":null,"max_value":null,"rate":null,"required":true,"teacher":{"surname":" Тестовый ","name":" Преподаватель ","patronymic":null}}]}"""
        })
        val control = (repository.getControls(2) as AppResult.Success).value.single()
        assertEquals(1L, control.parentId)
        assertNull(control.minimum)
        assertNull(control.maximum)
        assertNull(control.score)
        assertEquals("Тестовый Преподаватель", control.teacherName)
    }

    @Test fun `HTTP 200 authorization error is not an empty result`() = runTest {
        val repository = RecordbookRepositoryImpl(myItmoStub { """{"error_code":403,"result":null}""" })
        assertEquals(AppResult.Failure(AppError.Forbidden), repository.getPrograms())
    }
}
