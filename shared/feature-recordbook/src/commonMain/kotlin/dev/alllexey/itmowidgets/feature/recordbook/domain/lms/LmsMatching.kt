package dev.alllexey.itmowidgets.feature.recordbook.domain.lms

import dev.alllexey.itmowidgets.feature.recordbook.domain.subjectNameKey
import dev.alllexey.itmowidgets.feature.recordbook.domain.model.RecordbookControl

/** Conservative automatic matching: ambiguity never writes a score into an unrelated control. */
object LmsMatching {
    fun course(courses: List<LmsCourseScores>, subject: String, year: String, semester: Int): LmsCourseScores? {
        val parts = year.split('/')
        if (parts.size != 2 || parts.any { it.length != 4 || it.toIntOrNull() == null } || semester <= 0) return null
        val first = parts[0]
        val second = parts[1]
        val yearPattern = Regex("(?<!\\d)(?:$first|${first.takeLast(2)})\\s*/\\s*(?:$second|${second.takeLast(2)})(?!\\d)")
        val suffix = Regex("\\s*[,(-]?\\s*(\\d+)\\s*(?:семестр|сем)\\s*\\)?$", RegexOption.IGNORE_CASE)
        return courses.filter { course ->
            val match = suffix.find(course.name)
            val semesterMatches = match == null || match.groupValues[1].toIntOrNull() == semester
            val name = if (match == null) course.name else course.name.substring(0, match.range.first)
            semesterMatches && subjectNameKey(name) == subjectNameKey(subject) && yearPattern.containsMatchIn(course.program)
        }.singleOrNull()
    }

    /** Only a confirmed rubric control with the same number and maximum can fill an ungraded recordbook row. */
    fun controls(controls: List<RecordbookControl>, scores: List<LmsScore>): Map<Long, Double> {
        val lmsPattern = Regex("^рк\\s*(?:№|#)?\\s*(\\d+)$", RegexOption.IGNORE_CASE)
        val controlPattern = Regex("^контрольная\\s+работа\\s*(?:№|#)?\\s*(\\d+)$", RegexOption.IGNORE_CASE)
        val candidates = scores.mapNotNull { score ->
            val number = lmsPattern.matchEntire(score.name.trim())?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val earned = score.earned?.takeIf { it.isFinite() && it >= 0 && it <= score.max } ?: return@mapNotNull null
            if (score.verificationStatus != "VERIFIED" || !score.max.isFinite() || score.max <= 0) return@mapNotNull null
            Triple(number, score.max, earned)
        }
        return controls.mapNotNull { control ->
            if (control.score != null || control.absent || control.additional) return@mapNotNull null
            val number = controlPattern.matchEntire(control.name.trim())?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val matchingControls = controls.count { controlPattern.matchEntire(it.name.trim())?.groupValues?.get(1)?.toIntOrNull() == number }
            val matching = candidates.filter { it.first == number && it.second == control.maximum }
            if (matchingControls != 1 || matching.size != 1) null else control.id to matching.single().third
        }.toMap()
    }
}
