package edu.bnbu.student.mvp.feature.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.HourProgressBar
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.model.CheckInRecord
import edu.bnbu.student.mvp.core.model.Course
import edu.bnbu.student.mvp.core.model.CourseTask
import edu.bnbu.student.mvp.core.model.CreditType
import edu.bnbu.student.mvp.core.model.TaskStatus
import edu.bnbu.student.mvp.core.model.hourText
import edu.bnbu.student.mvp.core.state.StudentAppState

@Composable
fun CoursesScreen(appState: StudentAppState) {
    var selectedCourseId by remember { mutableStateOf<String?>(null) }
    val selectedCourse = selectedCourseId?.let { id -> appState.workspace.courses.firstOrNull { it.id == id } }

    if (selectedCourse == null) {
        CourseList(
            appState = appState,
            onCourseSelected = { selectedCourseId = it.id }
        )
    } else {
        CourseDetail(
            appState = appState,
            course = selectedCourse,
            onBack = { selectedCourseId = null }
        )
    }
}

@Composable
private fun CourseList(
    appState: StudentAppState,
    onCourseSelected: (Course) -> Unit
) {
    var historyExpanded by remember { mutableStateOf(false) }
    val courses = appState.workspace.courses
    val historyCourses = courses.filter {
        it.semesterStatus == "archived" || it.enrollmentStatus in setOf("completed", "withdrawn")
    }
    val currentCourses = courses.filterNot { it in historyCourses }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionTitle(eyebrow = "My Courses", title = "我的课程")
        }
        item {
            Text(
                text = "教学班以课程代码 + Section 区分；同一课程代码的不同 Section 会作为不同教学班展示。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (courses.isEmpty()) {
            item {
                EmptyPlaceholder(
                    title = "暂无课程",
                    message = "当前账号还没有可展示的体育教学班；课程同步后会按课程代码和 Section 显示。"
                )
            }
        } else {
            item { SectionTitle(eyebrow = "Current", title = "当前学期课程") }
            if (currentCourses.isEmpty()) {
                item {
                    EmptyPlaceholder(
                        title = "当前学期暂无课程",
                        message = "历史课程仍可在下方展开查看。"
                    )
                }
            } else {
                items(currentCourses, key = { "current-${it.id}" }) { course ->
                    CourseCard(course = course, onClick = { onCourseSelected(course) })
                }
            }

            if (historyCourses.isNotEmpty()) {
                item {
                    HistoryCourseHeader(
                        count = historyCourses.size,
                        expanded = historyExpanded,
                        onClick = { historyExpanded = !historyExpanded }
                    )
                }
            }
            if (historyExpanded) {
                items(historyCourses, key = { "history-${it.id}" }) { course ->
                    CourseCard(course = course, onClick = { onCourseSelected(course) })
                }
            }
        }
    }
}

@Composable
private fun HistoryCourseHeader(
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceVariant, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "历史课程（$count）",
            color = cs.onSurface,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "收起历史课程" else "展开历史课程",
            tint = cs.onSurfaceVariant
        )
    }
}

@Composable
private fun CourseCard(
    course: Course,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    SwissPanel(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = course.displayTitle,
                        color = cs.onSurface,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = course.name,
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                StatusBadge(text = course.semester.ifBlank { "学期待定" })
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(118.dp),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    listOf(
                        CourseFact("任课老师", course.teacher.ifBlank { "待公布" }),
                        CourseFact("学年", course.academicYear.ifBlank { "待设置" }),
                        CourseFact("学期", course.term.ifBlank { "待设置" }),
                        CourseFact("选课状态", course.enrollmentStatus.enrollmentStatusLabel())
                    )
                ) { fact ->
                    CourseFactCell(fact)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (course.isCurrent) "当前教学班" else course.semesterStatus.semesterStatusLabel(),
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "查看课程详情",
                    color = cs.primary,
                    style = MaterialTheme.typography.labelMedium
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun CourseDetail(
    appState: StudentAppState,
    course: Course,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = cs.onSurface
                )
                Text(
                    text = "返回我的课程",
                    color = cs.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SectionTitle(eyebrow = course.semester, title = course.displayTitle)
        }

        item {
            SwissPanel {
                DetailFactRow(label = "课程名称", value = course.name)
                DetailFactRow(label = "Section", value = "Section ${course.section}")
                DetailFactRow(label = "任课老师", value = course.teacher)
                DetailFactRow(label = "下一截止", value = course.deadline)
            }
        }

        item {
            SwissPanel {
                Text(
                    text = "我的课程相关进度",
                    color = cs.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(14.dp))
                HourProgressBar(
                    value = appState.workspace.progress.course,
                    total = appState.hourRule.courseRequired
                )
                Spacer(Modifier.height(14.dp))
                DetailFactRow(label = "已完成", value = appState.workspace.progress.course.hourText())
                DetailFactRow(label = "仍缺口", value = appState.courseRemaining.hourText())
            }
        }

        item {
            SectionTitle(eyebrow = "Class Tasks", title = "本教学班任务")
        }

        val tasks = appState.tasksFor(course)
        if (tasks.isEmpty()) {
            item {
                EmptyPlaceholder(
                    title = "暂无教学班任务",
                    message = "当前教学班还没有可展示任务。老师发布后会在这里同步。"
                )
            }
        } else {
            items(tasks) { task ->
                TaskRow(task = task, course = course)
            }
        }

        item {
            SectionTitle(eyebrow = "Trace", title = "相关记录")
        }

        val records = appState.recordsFor(course)
        if (records.isEmpty()) {
            item {
                EmptyPlaceholder(
                    title = "暂无相关记录",
                    message = "当前教学班还没有课程相关打卡记录。"
                )
            }
        } else {
            items(records) { record ->
                RecordCard(record)
            }
        }
    }
}

@Composable
private fun CourseFactCell(fact: CourseFact) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceVariant, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = fact.label.uppercase(),
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = fact.value,
            color = cs.onSurface,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
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
        verticalAlignment = Alignment.Top
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

@Composable
private fun TaskRow(task: CourseTask, course: Course?) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = task.creditType.courseIcon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        color = cs.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(text = task.status.label, filled = task.status == TaskStatus.Active)
                }
                Text(
                    text = "${task.creditType.label} · ${task.hours.hourText()} · 截止 ${task.deadline}",
                    color = cs.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "证明要求：${task.proof}",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                if (course != null) {
                    StatusBadge(text = course.displayTitle)
                }
            }
        }
    }
}

@Composable
private fun RecordCard(record: CheckInRecord) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = record.taskTitle,
                        color = cs.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = record.submittedAt,
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(text = record.creditType.label)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = record.hours.hourText(),
                    color = cs.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            record.sportType?.takeIf { it.isNotBlank() }?.let { sportType ->
                Text(
                    text = "运动项目：$sportType",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "凭证：${record.proofSummary}",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            if (record.note.isNotBlank()) {
                Text(
                    text = "备注：${record.note}",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private data class CourseFact(
    val label: String,
    val value: String
)

private fun String.enrollmentStatusLabel(): String = when (this) {
    "enrolled" -> "修读中"
    "completed" -> "已完成"
    "withdrawn" -> "已退课"
    else -> ifBlank { "待确认" }
}

private fun String.semesterStatusLabel(): String = when (this) {
    "upcoming" -> "即将开始"
    "current" -> "当前学期"
    "archived" -> "历史学期"
    else -> ifBlank { "学期待定" }
}

private val CreditType.courseIcon: ImageVector
    get() = when (this) {
        CreditType.CourseRelated -> Icons.Filled.AssignmentTurnedIn
        CreditType.General -> Icons.AutoMirrored.Filled.DirectionsRun
        CreditType.OrganizationOffset -> Icons.Filled.CheckCircle
    }
