package dev.alllexey.itmowidgets.feature.sport.data.mapper

import dev.alllexey.itmoapi.myitmo.sport.ChosenSportSection
import dev.alllexey.itmowidgets.client.sport.model.QueueEntryStatus as QueueEntryStatusDto
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignEntry as SportAutoSignEntryDto
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignLimits as SportAutoSignLimitsDto
import dev.alllexey.itmowidgets.client.sport.model.SportAutoSignQueue as SportAutoSignQueueDto
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignEntry as SportFreeSignEntryDto
import dev.alllexey.itmowidgets.client.sport.model.SportFreeSignQueue as SportFreeSignQueueDto
import dev.alllexey.itmowidgets.client.sport.model.SportLessonDto
import dev.alllexey.itmowidgets.client.sport.model.SportQueue as SportQueueDto
import dev.alllexey.itmowidgets.client.sport.model.SportQueueEntry as SportQueueEntryDto
import dev.alllexey.itmowidgets.feature.sport.domain.model.SectionName
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttempts
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAttendance
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignLimits
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportAutoSignQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportBooking
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterCatalog
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFilterOption
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportFreeSignQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueue
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntry
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueEntryStatus
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportQueueLesson
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportScore
import dev.alllexey.itmowidgets.feature.sport.domain.model.SportTimeSlot
import dev.alllexey.itmowidgets.feature.sport.domain.model.UnavailableReason
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

fun dev.alllexey.itmoapi.myitmo.sport.SportScore.toModel(): SportScore {
    return SportScore(
        attendances = sum.attendances.toInt(),
        other = sum.other.toInt(),
        attendancesData = attendances.orEmpty().map { it.toModel() }
    )
}

/** An absent name or evaluation decodes as blank in 2.x; the domain keeps them absent, as 1.x did. */
fun dev.alllexey.itmoapi.myitmo.sport.SportAttendance.toModel(): SportAttendance {
    return SportAttendance(
        type = type.trim(),
        name = name.trim().takeIf { it.isNotEmpty() }?.let(::SectionName),
        evaluationId = evaluationId,
        evaluationName = evaluationName.trim().takeIf { it.isNotEmpty() },
        sectionLevel = sectionLevel,
        score = score,
        dateTime = date,
        isCompetition = isCompetition
    )
}

fun dev.alllexey.itmoapi.myitmo.sport.SportAttempts.toModel(): SportAttempts {
    return SportAttempts(
        total = totalAttempts,
        used = usedAttempts,
        free = freeAttempts,
        canSignIn = canSignIn
    )
}

fun dev.alllexey.itmoapi.myitmo.sport.SportLesson.toModel(now: Instant): SportLesson {
    val unavailableReasons = UnavailableReason.getSortedUnavailableReasons(
        signed = signed,
        startsAt = date,
        available = available.toInt(),
        serverReasons = canSignIn.unavailableReasons,
        now = now
    )
    return SportLesson(
        isLessonReal = true,
        lessonId = id,
        start = date,
        end = dateEnd,
        sectionId = sectionId,
        sectionName = SectionName(sectionName.trim()),
        sectionLevel = sectionLevel.toInt(),
        lessonGroupId = lessonGroupId,
        lessonLevel = lessonLevel.toInt(),
        typeId = typeId.toInt(),
        buildingId = buildingId,
        roomId = roomId,
        roomName = roomName.trim(),
        limit = limit.toInt(),
        available = available.toInt(),
        comment = comment?.trim(),
        timeSlotId = timeSlotId,
        timeSlotStart = timeSlotStart.trim(),
        timeSlotEnd = timeSlotEnd.trim(),
        intersection = intersection,
        canSignIn = canSignIn.canSignIn,
        unavailableReasons = unavailableReasons,
        signed = signed,
        teacherIsu = teacherIsu.toInt(),
        teacherFio = teacherFio.trim(),
        signEntry = null,
        signQueue = null,
        friendsBookings = emptyList(),
    )
}


/** A lesson without dates cannot be placed on a day; 1.x failed the whole list on one, 2.x skips it. */
fun ChosenSportSection.toBookings(): List<SportBooking> {
    return lessonGroups.flatMap { group ->
        group.lessons.mapNotNull { lesson ->
            val start = lesson.dateStart ?: return@mapNotNull null
            val end = lesson.dateEnd ?: return@mapNotNull null
            SportBooking(
                isLessonReal = true,
                lessonId = lesson.id,
                sectionName = SectionName(sectionName.trim()),
                start = start,
                end = end,
                roomName = lesson.roomName.trim(),
                teacherFio = lesson.teacherFio.trim(),
                teacherIsu = lesson.teacherIsu.toInt(),
                sectionLevel = level,
                lessonLevel = group.level,
                signed = true,
                signEntry = null,
                friendsBookings = emptyList()
            )
        }
    }
}

fun SportQueueEntry.toBooking(): SportBooking {
    val (lesson, isReal) = when (this) {
        is SportFreeSignEntry -> this.targetLesson to true
        is SportAutoSignEntry -> (this.realLesson?.to(true) ?: (this.targetLesson to false))
    }

    return SportBooking(
        isLessonReal = isReal,
        lessonId = if (isReal) lesson.id else -lesson.id,
        sectionName = SectionName(lesson.sectionName),
        start = lesson.start + (if (!isReal) 14 else 0).days,
        end = lesson.end + (if (!isReal) 14 else 0).days,
        roomName = lesson.roomName,
        teacherFio = lesson.teacherFio,
        teacherIsu = lesson.teacherIsu.toInt(),
        sectionLevel = lesson.sectionLevel.toInt(),
        lessonLevel = lesson.level.toInt(),
        signed = false,
        signEntry = this,
        friendsBookings = emptyList()
    )
}

fun dev.alllexey.itmoapi.myitmo.sport.SportFilters.toModel(): SportFilterCatalog {
    return SportFilterCatalog(
        buildings = buildingId.map { SportFilterOption(it.id, it.value.trim()) },
        sections = sectionId.map { SportFilterOption(it.id, it.value.trim()) },
        sportTypes = sportTypeId.map { SportFilterOption(it.id, it.value.trim()) },
        teachers = teacherIsu.map { SportFilterOption(it.id, it.value.trim()) }
    )
}

fun dev.alllexey.itmoapi.myitmo.sport.TimeSlot.toModel(): SportTimeSlot {
    return SportTimeSlot(
        id = id,
        start = timeStart.trim(),
        end = timeEnd.trim()
    )
}

fun SportAutoSignLimitsDto.toModel(): SportAutoSignLimits {
    return SportAutoSignLimits(
        limit = limit,
        available = available,
        nextAvailableAt = nextAvailableAt
    )
}

fun SportQueueEntryDto.toModel(): SportQueueEntry {
    return when (this) {
        is SportFreeSignEntryDto -> SportFreeSignEntry(
            id = id,
            lessonId = lessonId,
            position = position,
            total = total,
            isCancelled = isCancelled,
            status = status.toModel(),
            createdAt = createdAt,
            firstNotifiedAt = firstNotifiedAt,
            lastNotifiedAt = lastNotifiedAt,
            cancelledAt = cancelledAt,
            satisfiedAt = satisfiedAt,
            expiredAt = expiredAt,
            notificationAttempts = notificationAttempts,
            maxNotificationAttempts = maxNotificationAttempts,
            targetLesson = targetLesson.toModel(),
            forceSign = forceSign
        )

        is SportAutoSignEntryDto -> SportAutoSignEntry(
            id = id,
            prototypeLessonId = prototypeLessonId,
            realLessonId = realLessonId,
            position = position,
            total = total,
            isCancelled = isCancelled,
            status = status.toModel(),
            createdAt = createdAt,
            firstNotifiedAt = firstNotifiedAt,
            lastNotifiedAt = lastNotifiedAt,
            cancelledAt = cancelledAt,
            satisfiedAt = satisfiedAt,
            expiredAt = expiredAt,
            notificationAttempts = notificationAttempts,
            maxNotificationAttempts = maxNotificationAttempts,
            targetLesson = targetLesson.toModel(),
            realLesson = realLesson?.toModel()
        )
    }
}

fun SportQueueDto.toModel(): SportQueue {
    return when (this) {
        is SportFreeSignQueueDto -> SportFreeSignQueue(
            lessonId = lessonId,
            total = total
        )

        is SportAutoSignQueueDto -> SportAutoSignQueue(
            lessonId = lessonId,
            total = total,
            realLessonId = realLessonId
        )
    }
}

/**
 * A status this client does not know fails the whole load, as 1.x did (its Gson left the status null and this
 * mapping threw). Backend sends no new status while `app.minimum` is 2.2 or lower.
 */
private fun QueueEntryStatusDto.toModel(): SportQueueEntryStatus = when (this) {
    QueueEntryStatusDto.WAITING -> SportQueueEntryStatus.WAITING
    QueueEntryStatusDto.NOTIFIED -> SportQueueEntryStatus.NOTIFIED
    QueueEntryStatusDto.GAVE_UP_NOTIFYING -> SportQueueEntryStatus.GAVE_UP_NOTIFYING
    QueueEntryStatusDto.SATISFIED -> SportQueueEntryStatus.SATISFIED
    QueueEntryStatusDto.EXPIRED -> SportQueueEntryStatus.EXPIRED
    QueueEntryStatusDto.UNKNOWN -> throw IllegalStateException("Unknown sport queue entry status")
}

private fun SportLessonDto.toModel(): SportQueueLesson {
    return SportQueueLesson(
        id = id,
        sectionId = sectionId,
        sectionName = sectionName.trim(),
        sectionLevel = sectionLevel,
        level = level,
        typeId = typeId,
        buildingId = buildingId,
        roomName = roomName.trim(),
        start = start,
        end = end,
        timeSlotId = timeSlotId,
        teacherIsu = teacherIsu,
        teacherFio = teacherFio.trim()
    )
}
