package dev.alllexey.itmowidgets.feature.resources.data.demo

import dev.alllexey.itmowidgets.core.demo.DemoPeople
import dev.alllexey.itmowidgets.core.demo.DemoPerson
import dev.alllexey.itmowidgets.core.demo.DemoStudy
import dev.alllexey.itmowidgets.core.demo.DemoSubject
import dev.alllexey.itmowidgets.core.resources.LinkAudience
import dev.alllexey.itmowidgets.core.resources.LinkCategory
import dev.alllexey.itmowidgets.core.resources.LinkVisibility
import dev.alllexey.itmowidgets.core.resources.ResourceScope
import dev.alllexey.itmowidgets.core.resources.SubjectLink
import dev.alllexey.itmowidgets.core.resources.SubjectLinkStatus
import dev.alllexey.itmowidgets.core.resources.SubjectLinksSnapshot
import java.time.OffsetDateTime

/** Links of the demo subjects: the stream's shared links, one of Anna's own and the pin of the period. */
object DemoSubjectLinks {

    fun snapshot(scope: ResourceScope, now: OffsetDateTime): SubjectLinksSnapshot {
        val subject = DemoStudy.CURRENT.firstOrNull { it.id == scope.subjectId }
        val links = subject?.let { LINKS[it] }.orEmpty().mapIndexed { index, link -> link.toModel(scope, subject!!, index, now) }
        val mine = links.filter { it.isMine }
        val shared = links.filterNot { it.isMine }
        return SubjectLinksSnapshot(
            mine = mine,
            shared = shared,
            previous = emptyList(),
            pinnedId = shared.firstOrNull { it.category == LinkCategory.SCORES }?.id,
            audiences = subject?.let(::audiences).orEmpty(),
            premoderation = false,
            servicesEnabled = true
        )
    }

    private fun audiences(subject: DemoSubject) = listOf(
        LinkAudience(subject.flowId(LECTURE), "${subject.flow} ${DemoStudy.FLOW_FACULTY} ${DemoStudy.LECTURE_STREAM}", LECTURE, 1),
        LinkAudience(subject.flowId(PRACTICE), "${subject.flow} ${DemoStudy.FLOW_FACULTY} ${DemoStudy.PRACTICE_GROUP}", PRACTICE, 2)
    )

    private data class DemoLink(
        val category: LinkCategory,
        val title: String,
        val url: String,
        val visibility: LinkVisibility,
        val author: DemoPerson?,
        val score: Int,
        val daysAgo: Long
    ) {
        val isMine: Boolean get() = author == DemoPeople.ME_PERSON

        fun toModel(scope: ResourceScope, subject: DemoSubject, index: Int, now: OffsetDateTime) = SubjectLink(
            id = "00000000-0000-4000-9000-%012d".format(subject.id * 100 + index),
            scope = scope,
            category = category,
            url = url,
            title = title,
            visibility = visibility,
            flowId = if (visibility == LinkVisibility.FLOW) subject.flowId(PRACTICE) else null,
            audienceLabel = if (visibility == LinkVisibility.FLOW) {
                "${subject.flow} ${DemoStudy.FLOW_FACULTY} ${DemoStudy.PRACTICE_GROUP}"
            } else {
                null
            },
            status = if (visibility == LinkVisibility.PRIVATE) SubjectLinkStatus.PRIVATE else SubjectLinkStatus.PUBLISHED,
            reviewNote = null,
            score = score,
            myVote = if (!isMine && score > 5) 1 else 0,
            isMine = isMine,
            reportedByMe = false,
            author = author?.summary(),
            updatedAt = now.minusDays(daysAgo)
        )
    }

    private const val LECTURE = 1
    private const val PRACTICE = 3

    private val LINKS: Map<DemoSubject, List<DemoLink>> = mapOf(
        DemoStudy.ALGORITHMS to listOf(
            DemoLink(
                LinkCategory.SCORES, "Баллы потока", DemoStudy.ALGORITHMS_SCORES_SHEET,
                LinkVisibility.FLOW, DemoPeople.IVAN, 12, 9
            ),
            DemoLink(
                LinkCategory.QUEUE, "Очередь на защиту лабораторных",
                "https://docs.google.com/spreadsheets/d/1Zr4tWq9LmKx2Vb7NcY0aHs3Df6Gj8PoUe5Ri1TyQwEz/edit",
                LinkVisibility.FLOW, DemoPeople.MARIA, 8, 4
            ),
            DemoLink(
                LinkCategory.MATERIALS, "Конспекты лекций", "https://disk.yandex.ru/d/Xr3kq8LmPa0b2Q",
                LinkVisibility.ALL, DemoPeople.DMITRY, 21, 30
            ),
            DemoLink(
                LinkCategory.CHAT, "Чат потока", "https://t.me/+R4kXo2mVb9cwYjk6",
                LinkVisibility.FLOW, DemoPeople.IVAN, 5, 40
            ),
            DemoLink(
                LinkCategory.NOTES, "Мои заметки к экзамену",
                "https://docs.google.com/document/d/1Lm7Qa2Wx5Ez8Rc0Tv3Yb6Un9Io4Pk1Js8Hd2Fg5Kl0Zx/edit",
                LinkVisibility.PRIVATE, DemoPeople.ME_PERSON, 0, 2
            )
        ),
        DemoStudy.MATH to listOf(
            DemoLink(
                LinkCategory.MATERIALS, "Задачник к практикам", "https://disk.yandex.ru/d/Qm2Lp7Vx4Kz9Ra",
                LinkVisibility.ALL, DemoPeople.POLINA, 15, 35
            ),
            DemoLink(
                LinkCategory.RECORDINGS, "Записи лекций",
                "https://www.youtube.com/playlist?list=PLx7Rk2Mv9Qa4Bz6Tw1Ny8Ue3Jd5Hs0Lc",
                LinkVisibility.FLOW, DemoPeople.MARIA, 9, 12
            )
        ),
        DemoStudy.DATABASES to listOf(
            DemoLink(
                LinkCategory.TASKS, "Задания лабораторных", "https://disk.yandex.ru/d/Bd5Nr8Kt2Wq7Lx",
                LinkVisibility.FLOW, DemoPeople.ME_PERSON, 6, 20
            ),
            DemoLink(
                LinkCategory.CHAT, "Чат по базам данных", "https://t.me/+Hq8Zt3Rk1Vw6Lp2m",
                LinkVisibility.FLOW, DemoPeople.IVAN, 4, 25
            )
        ),
        DemoStudy.DISCRETE to listOf(
            DemoLink(
                LinkCategory.SCORES, "Баллы за домашние задания",
                "https://docs.google.com/spreadsheets/d/1Wv3Ns6Rt9Ky2Mq5Lp8Xz1Bc4Df7Gh0Jk3Ui6Oa9Pe2T/edit",
                LinkVisibility.FLOW, DemoPeople.MARIA, 7, 15
            ),
            DemoLink(
                LinkCategory.EXAM, "Вопросы к экзамену", "https://disk.yandex.ru/d/Ex4Mk9Qt7Vz2Rb",
                LinkVisibility.ALL, DemoPeople.POLINA, 10, 6
            )
        ),
        DemoStudy.ENGLISH to listOf(
            DemoLink(
                LinkCategory.MATERIALS, "Тексты для эссе", "https://disk.yandex.ru/d/Es7Lq2Wn5Kx8Tz",
                LinkVisibility.FLOW, DemoPeople.MARIA, 3, 18
            )
        )
    )
}
