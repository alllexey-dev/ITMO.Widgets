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

/**
 * Chip order: the pinned link, the LMS page, own links by category, links added to own,
 * flow links of others, then the best-scored ALL link of every category not shown yet.
 * Chats never become chips: the subject screen lists them separately.
 */
fun subjectLinkChips(snapshot: SubjectLinksSnapshot, lmsUrl: String?, limit: Int = 4): SubjectLinkChips {
    val all = (snapshot.mine + snapshot.shared + snapshot.previous).filter { it.category != LinkCategory.CHAT }
    val links = LinkedHashMap<String, SubjectLink>()
    fun addAll(candidates: List<SubjectLink>) = candidates.forEach { links.putIfAbsent(it.id, it) }

    val pinned = all.firstOrNull { it.id == snapshot.pinnedId }
    val others = snapshot.shared.filter { it.category != LinkCategory.CHAT }
    addAll(listOfNotNull(pinned))
    addAll(snapshot.mine.filter { it.category != LinkCategory.CHAT }.sortedBy { it.category.ordinal })
    addAll(all.filter { !it.isMine && it.isSaved })
    addAll(others.filter { it.visibility == LinkVisibility.FLOW })
    val lms = lmsUrl?.let(SubjectLinkChip::Lms)
    val shown = links.values.map { it.category }.toSet() + listOfNotNull(lms?.category)
    addAll(others.filter { it.visibility == LinkVisibility.ALL && it.category !in shown }
        .groupBy { it.category }.values.map { group -> group.maxBy { it.score } }.sortedBy { it.category.ordinal })

    val ordered = links.values.map(SubjectLinkChip::Link).toMutableList<SubjectLinkChip>()
    if (lms != null) ordered.add(if (pinned == null) 0 else 1, lms)
    val visible = ordered.take(limit)
    val visibleIds = visible.filterIsInstance<SubjectLinkChip.Link>().map { it.link.id }.toSet()
    val moreCount = (snapshot.mine + snapshot.shared).asSequence()
        .filter { it.category != LinkCategory.CHAT }
        .map { it.id }.distinct()
        .count { it !in visibleIds }
    return SubjectLinkChips(visible, moreCount)
}
