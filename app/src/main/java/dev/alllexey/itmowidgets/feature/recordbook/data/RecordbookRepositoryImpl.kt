package dev.alllexey.itmowidgets.feature.recordbook.data

import api.myitmo.MyItmo
import api.myitmo.model.recordbook.ControlEntry
import api.myitmo.model.recordbook.RecordBookEntry
import api.myitmo.model.recordbook.RecordBookTeacher
import api.myitmo.model.recordbook.Semester
import api.myitmo.model.recordbook.Specialization
import dev.alllexey.itmowidgets.core.network.requireResult
import dev.alllexey.itmowidgets.core.network.toAppError
import dev.alllexey.itmowidgets.core.result.AppResult
import dev.alllexey.itmowidgets.feature.recordbook.domain.RecordbookRepository
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookPeriod
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookProgram
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookSubject
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecordbookRepositoryImpl @Inject constructor(
    private val myItmo: MyItmo
) : RecordbookRepository {

    private val api by lazy { myItmo.api }

    override suspend fun getPrograms(): AppResult<List<RecordbookProgram>> = request(
        call = { api.getSpecializations() },
        transform = { programs -> programs.map { it.toModel() } }
    )

    override suspend fun getSubjects(
        programId: Long,
        semester: Int
    ): AppResult<List<RecordbookSubject>> = request(
        call = { api.getRecordBook(programId, semester) },
        transform = { subjects -> subjects.map { it.toModel() } }
    )

    override suspend fun getControls(entryId: Long): AppResult<List<RecordbookControl>> = request(
        call = { api.getControlEntries(entryId) },
        transform = { controls -> controls.map { it.toModel() } }
    )

    private suspend fun <T, R> request(
        call: () -> retrofit2.Call<api.myitmo.model.ResultResponse<T>>,
        transform: (T) -> R
    ): AppResult<R> {
        return try {
            val result = withContext(Dispatchers.IO) {
                myItmo.execute(call()).requireResult()
            }
            AppResult.Success(transform(result))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    private fun Specialization.toModel() = RecordbookProgram(
        id = mainPlan,
        name = specializationName.trim(),
        periods = semesters.map { it.toModel() }
    )

    private fun Semester.toModel() = RecordbookPeriod(
        studyYear = studyYear.trim(),
        semester = semester,
        course = course,
        actual = isActual
    )

    private fun RecordBookEntry.toModel() = RecordbookSubject(
        name = name.trim(),
        disciplineId = disciplineId,
        entryId = estId,
        controlType = controlType.trim(),
        score = currentScore,
        rate = rate?.trim(),
        attempt = attempt,
        examDate = examDate,
        hasDetails = isHaveTree,
        teacherName = teacher?.displayName()
    )

    private fun ControlEntry.toModel() = RecordbookControl(
        id = id,
        name = controlName.trim(),
        score = rate,
        minimum = minValue,
        maximum = maxValue,
        required = isRequired,
        date = date,
        teacherName = teacher?.displayName(),
        parentId = parentId
    )

    private fun RecordBookTeacher.displayName(): String? {
        return listOfNotNull(surname, name, patronymic)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .takeIf(String::isNotBlank)
    }
}
