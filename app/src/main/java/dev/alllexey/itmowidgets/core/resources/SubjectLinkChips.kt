package dev.alllexey.itmowidgets.core.resources

sealed interface SubjectLinkChip {
    val category: LinkCategory

    data class Link(val link: SubjectLink) : SubjectLinkChip {
        override val category: LinkCategory get() = link.category
    }

    /** The MyITMO LMS page of the subject. */
    data class Lms(val url: String) : SubjectLinkChip {
        override val category: LinkCategory get() = LinkCategory.MATERIALS
    }
}

/** [moreCount] counts the non-chat links of `mine` and `shared` that did not fit. */
data class SubjectLinkChips(val visible: List<SubjectLinkChip>, val moreCount: Int)

/** Best score first; of equal scores the newer link. Own links rank like everybody else's. */
val SubjectLinkRanking: Comparator<SubjectLink> =
    compareByDescending<SubjectLink> { it.score }.thenByDescending { it.updatedAt }

/**
 * Chip order: the pinned link, the LMS page, then own, added and shared links together by [SubjectLinkRanking].
 * Chats never become chips: the subject screen lists them separately.
 */
fun subjectLinkChips(snapshot: SubjectLinksSnapshot, lmsUrl: String?, limit: Int = 4): SubjectLinkChips {
    val current = (snapshot.mine + snapshot.shared).filter { it.category != LinkCategory.CHAT }.distinctBy { it.id }
    val pinned = (current + snapshot.previous).firstOrNull { it.id == snapshot.pinnedId && it.category != LinkCategory.CHAT }
    val visible = buildList {
        pinned?.let { add(SubjectLinkChip.Link(it)) }
        lmsUrl?.let { add(SubjectLinkChip.Lms(it)) }
        current.filter { it.id != pinned?.id }.sortedWith(SubjectLinkRanking).mapTo(this, SubjectLinkChip::Link)
    }.take(limit)
    val visibleIds = visible.filterIsInstance<SubjectLinkChip.Link>().map { it.link.id }.toSet()
    return SubjectLinkChips(visible, current.count { it.id !in visibleIds })
}
