package edu.bnbu.student.mvp.feature.grades

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.designsystem.HourProgressBar
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.model.GradeRow
import edu.bnbu.student.mvp.core.state.StudentAppState

@Composable
fun GradesScreen(appState: StudentAppState) {
    val grades = appState.workspace.grades
    val components = grades.gradeComponents()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionTitle(eyebrow = "Grade Progress", title = "成绩进度") }
        item { TotalPanel(grades) }
        item { ComponentGrid(components) }
        item { FormulaPanel(grades = grades, components = components) }
        item { MissingPanel(grades) }
        item { TracePanel(grades) }
    }
}

@Composable
private fun TotalPanel(grades: GradeRow) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "总分预估",
                    color = cs.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "基于当前已录入四块成绩与权重规则，待审核打卡暂不计入。",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = grades.total.toString(),
                color = cs.onSurface,
                fontSize = 54.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 54.sp
            )
        }
    }
}

@Composable
private fun ComponentGrid(components: List<GradeComponentSummary>) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(512.dp),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(components) { component ->
            GradeComponentCard(component)
        }
    }
}

@Composable
private fun GradeComponentCard(component: GradeComponentSummary) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = component.icon,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.weight(1f))
                StatusBadge(text = component.weightText)
            }

            Text(
                text = component.title,
                color = cs.onSurface,
                style = MaterialTheme.typography.titleSmall
            )

            Text(
                text = component.score.toString(),
                color = cs.onSurface,
                fontSize = 40.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 40.sp
            )

            HourProgressBar(value = component.score.toDouble(), total = 100.0)

            Text(
                text = component.note,
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun FormulaPanel(
    grades: GradeRow,
    components: List<GradeComponentSummary>
) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "总分计算",
                color = cs.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            StatusBadge(text = "透明预估")
        }

        Spacer(Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            components.forEach { component ->
                GradeContributionRow(component)
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(cs.outline)
        )
        Spacer(Modifier.height(12.dp))

        DetailFactRow(label = "加权合计", value = "%.1f".format(components.sumOf { it.contribution }))
        DetailFactRow(label = "四舍五入", value = grades.total.toString())
    }
}

@Composable
private fun GradeContributionRow(component: GradeComponentSummary) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = component.title,
                color = cs.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${component.score} x ${component.weightText} = ${component.contributionText}",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        HourProgressBar(value = component.contribution, total = 30.0)
    }
}

@Composable
private fun MissingPanel(grades: GradeRow) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "缺失项 / 风险",
                color = cs.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            StatusBadge(
                text = if (grades.missingItems.isEmpty()) "无缺失" else "${grades.missingItems.size} 项",
                filled = grades.missingItems.isEmpty()
            )
        }

        Spacer(Modifier.height(12.dp))

        if (grades.missingItems.isEmpty()) {
            Text(
                text = "当前没有阻塞项。",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                grades.missingItems.forEach { item ->
                    MissingItemRow(item)
                }
            }
        }
    }
}

@Composable
private fun MissingItemRow(item: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.errorContainer, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = null,
            tint = cs.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = item,
            color = cs.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TracePanel(grades: GradeRow) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.TrackChanges,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "来源追溯",
                color = cs.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = grades.sourceTrace,
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun DetailFactRow(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = cs.onSurface,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

private data class GradeComponentSummary(
    val title: String,
    val score: Int,
    val weight: Double,
    val icon: ImageVector,
    val note: String
) {
    val weightText: String
        get() = "${(weight * 100).toInt()}%"

    val contribution: Double
        get() = score * weight

    val contributionText: String
        get() = "%.1f".format(contribution)
}

private fun GradeRow.gradeComponents(): List<GradeComponentSummary> {
    return listOf(
        GradeComponentSummary(
            title = "体育打卡",
            score = checkinScore,
            weight = 0.25,
            icon = Icons.Filled.AssignmentTurnedIn,
            note = "仅统计已通过和系统抵扣小时"
        ),
        GradeComponentSummary(
            title = "专项考试",
            score = exam,
            weight = 0.30,
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            note = "由任课老师录入专项成绩"
        ),
        GradeComponentSummary(
            title = "平时表现 / 签到",
            score = attendance,
            weight = 0.20,
            icon = Icons.Filled.CheckCircle,
            note = "课堂签到与平时表现"
        ),
        GradeComponentSummary(
            title = "体测",
            score = physical,
            weight = 0.25,
            icon = Icons.Filled.BarChart,
            note = "体测数据录入后参与计算"
        )
    )
}
