package dev.alllexey.itmowidgets.data.repository

import api.myitmo.MyItmo
import api.myitmo.model.recordbook.ControlEntry
import api.myitmo.model.recordbook.RecordBookEntry
import api.myitmo.model.recordbook.RecordBookTeacher
import api.myitmo.model.recordbook.Semester
import api.myitmo.model.recordbook.Specialization
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookControl
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookPeriod
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookProgram
import dev.alllexey.itmowidgets.domain.model.recordbook.RecordbookSubject
import dev.alllexey.itmowidgets.domain.repository.RecordbookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class RecordbookRepositoryImpl @Inject constructor(
    private val myItmo: MyItmo
) : RecordbookRepository {

    private val api by lazy { myItmo.api }

    override suspend fun getPrograms(): List<RecordbookProgram> = request {
        api.getSpecializations()
    }.map { it.toModel() }

    override suspend fun getSubjects(
        programId: Long,
        semester: Int
    ): List<RecordbookSubject> = request {
        api.getRecordBook(programId, semester)
    }.map { it.toModel() }

    override suspend fun getControls(entryId: Long): List<RecordbookControl> = request {
        api.getControlEntries(entryId)
    }.map { it.toModel() }

    private suspend fun <T> request(call: () -> retrofit2.Call<api.myitmo.model.ResultResponse<T>>): T {
        return withContext(Dispatchers.IO) {
            val response = myItmo.execute(call())
            if (response.errorCode != 0) {
                error(response.errorMessage ?: "My ITMO returned error ${response.errorCode}")
            }
            response.result ?: error("My ITMO returned an empty response")
        }
    }

    private fun Specialization.toModel() = RecordbookProgram(
        id = mainPlan,
        name = specializationName.trim(),
        periods = semesters.map { it.toModel() }
    )

    private fun Semester.toModel() = RecordbookPeriod(
        studyYear = studyYear,
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
        rate = rate,
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
        teacherName = teacher?.displayName()
    )

    private fun RecordBookTeacher.displayName(): String? {
        return listOfNotNull(surname, name, patronymic)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .takeIf(String::isNotBlank)
    }
}
