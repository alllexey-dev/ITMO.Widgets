package dev.alllexey.itmowidgets.feature.recordbook.ui.subject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.alllexey.itmowidgets.core.navigation.UserScreenArgs
import dev.alllexey.itmowidgets.core.reviews.TeacherLevel
import dev.alllexey.itmowidgets.core.reviews.TeacherLevelTone
import dev.alllexey.itmowidgets.core.reviews.tone
import dev.alllexey.itmowidgets.core.schedule.lessonTypeName
import dev.alllexey.itmowidgets.core.text.asString
import dev.alllexey.itmowidgets.designsystem.components.avatar.Avatar
import dev.alllexey.itmowidgets.designsystem.components.buttons.ToneDot
import dev.alllexey.itmowidgets.designsystem.components.groups.GroupPosition
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeading
import dev.alllexey.itmowidgets.designsystem.components.groups.SectionHeadingSpacing
import dev.alllexey.itmowidgets.designsystem.components.groups.connectedGroupItem
import dev.alllexey.itmowidgets.designsystem.preview.ItmoPreview
import dev.alllexey.itmowidgets.designsystem.theme.ItmoTheme
import dev.alllexey.itmowidgets.feature.recordbook.presentation.SubjectTeacher
import dev.alllexey.itmowidgets.shared.core.teacher_open_profile
import dev.alllexey.itmowidgets.shared.designsystem.ic_chevron_right
import dev.alllexey.itmowidgets.shared.feature.recordbook.Res
import dev.alllexey.itmowidgets.shared.feature.recordbook.subject_teachers_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import dev.alllexey.itmowidgets.shared.core.Res as CoreRes
import dev.alllexey.itmowidgets.shared.designsystem.Res as KitRes

/**
 * A teacher of the subject (`item_subject_teacher.xml` as `TeacherHolder` binds it): the avatar with initials, the
 * name and the lesson types they run, the review tone's dot and, with an ISU that a profile accepts
 * ([UserScreenArgs.profileIsu]), a chevron; only then the row opens the profile through [onOpenProfile]. A teacher
 * with such an ISU keeps the dot's place, so a tone arriving later does not move the chevron. TalkBack reads the name
 * with the tone.
 */
@Composable
fun SubjectTeacherRow(item: SubjectHubItem.Teacher, onOpenProfile: (Int) -> Unit) {
    val teacher = item.teacher
    val isu = UserScreenArgs.profileIsu(teacher.isu)
    val colors = ItmoTheme.colorScheme
    val interaction = if (isu == null) {
        Modifier.semantics(mergeDescendants = true) {}
    } else {
        Modifier.clickable(onClickLabel = stringResource(CoreRes.string.teacher_open_profile)) { onOpenProfile(isu) }
    }
    val nameDescription = item.level?.tone()?.let { tone ->
        "${teacher.name}$LIST_SEPARATOR${tone.description(tone.label.asString()).asString().lowercase()}"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .connectedGroupItem(item.position)
            .then(interaction)
            .heightIn(min = TeacherMinHeight)
            .padding(horizontal = ItmoTheme.spacing.cardPadding, vertical = ItmoTheme.spacing.compact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(teacher.name, pictureUrl = null, size = AvatarSize)
        Column(Modifier.weight(1f).padding(start = ItmoTheme.spacing.content)) {
            Text(
                teacher.name,
                if (nameDescription == null) Modifier else Modifier.semantics { contentDescription = nameDescription },
                color = colors.onSurface,
                style = ItmoTheme.typography.bodyLarge.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
            )
            if (teacher.roles.isNotEmpty()) {
                Text(
                    teacher.roles.map { stringResource(lessonTypeName(it)) }.joinToString(LIST_SEPARATOR),
                    Modifier.padding(top = RolesGap),
                    color = colors.onSurfaceVariant,
                    style = ItmoTheme.typography.bodyMedium,
                )
            }
        }
        if (item.level != null || isu != null) {
            ToneDot(item.level?.tone()?.color(), Modifier.padding(start = ItmoTheme.spacing.compact))
        }
        if (isu != null) {
            Icon(
                painterResource(KitRes.drawable.ic_chevron_right),
                contentDescription = null,
                modifier = Modifier.padding(start = ItmoTheme.spacing.compact).size(ChevronSize),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

/** DS-01a's `teacher_level_*` token of the tone, harmonized towards the primary colour by the theme. */
@Composable
private fun TeacherLevelTone.color(): Color {
    val colors = ItmoTheme.extendedColors
    return when (this) {
        TeacherLevelTone.VERY_NEGATIVE -> colors.teacherLevelVeryNegative
        TeacherLevelTone.NEGATIVE -> colors.teacherLevelNegative
        TeacherLevelTone.MIXED -> colors.teacherLevelMixed
        TeacherLevelTone.POSITIVE -> colors.teacherLevelPositive
        TeacherLevelTone.VERY_POSITIVE -> colors.teacherLevelVeryPositive
    }
}

private const val LIST_SEPARATOR = ", "

/** `item_subject_teacher.xml`: a connected group's 56 dp row with a 40 dp avatar. */
private val TeacherMinHeight = 56.dp
private val AvatarSize = 40.dp
private val ChevronSize = 24.dp

/** The roles' 2 dp `layout_marginTop`. */
private val RolesGap = 2.dp

/**
 * Every kind of teacher row: a profile with a positive tone, one whose tone has not arrived (the dot keeps its
 * place), a mixed tone with a long name, and a teacher without an ISU, who neither opens nor has a dot.
 */
@Preview(heightDp = 520)
@Composable
private fun SubjectTeacherRowPreview() = ItmoPreview {
    Column(
        Modifier
            .padding(horizontal = ItmoTheme.spacing.screenMargin)
            .padding(bottom = ItmoTheme.spacing.screenMargin),
    ) {
        SectionHeading(stringResource(Res.string.subject_teachers_title), spacing = SectionHeadingSpacing.First)
        val rows = listOf(
            SubjectTeacher("Иванова Мария Сергеевна", 300001, listOf(1, 3)) to TeacherLevel.POSITIVE,
            SubjectTeacher("Смирнов Алексей Петрович", 300002, listOf(2)) to null,
            SubjectTeacher("Константинопольская-Тестова Александра Владиславовна", 300003, listOf(3)) to
                TeacherLevel.MIXED,
            SubjectTeacher("Петров Пётр", null, emptyList()) to null,
        )
        rows.forEachIndexed { index, (teacher, level) ->
            SubjectTeacherRow(
                SubjectHubItem.Teacher(teacher, level, GroupPosition.of(index, rows.size)),
                onOpenProfile = {},
            )
        }
    }
}
