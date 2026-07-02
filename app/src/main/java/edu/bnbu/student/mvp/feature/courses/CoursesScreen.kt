package edu.bnbu.student.mvp.feature.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.designsystem.BNBUColors
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.HourProgressBar
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.model.CheckInRecord
import edu.bnbu.student.mvp.core.model.Course
import edu.bnbu.student.mvp.core.model.CourseTask
import edu.bnbu.student.mvp.core.model.CreditType
import edu.bnbu.student.mvp.core.model.ReviewStatus
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
                color = BNBUColors.Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp
            )
        }

        if (appState.workspace.courses.isEmpty()) {
            item {
                EmptyPlaceholder(
                    title = "暂无课程",
                    message = "当前账号还没有可展示的体育教学班；课程同步后会按课程代码和 Section 显示。"
                )
            }
        } else {
            items(appState.workspace.courses) { course ->
                CourseCard(
                    course = course,
                    onClick = { onCourseSelected(course) }
                )
            }
        }
    }
}

@Composable
private fun CourseCard(
    course: Course,
    onClick: () -> Unit
) {
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
                        color = BNBUColors.Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = course.name,
                        color = BNBUColors.Muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusBadge(text = course.semester)
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
                        CourseFact("任课老师", course.teacher),
                        CourseFact("课程学生", course.students.toString()),
                        CourseFact("待审核", course.pending.toString()),
                        CourseFact("未完成", course.missing.toString())
                    )
                ) { fact ->
                    CourseFactCell(fact)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "班级完成率",
                        color = BNBUColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${course.completion}%",
                        color = BNBUColors.Ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                HourProgressBar(value = course.completion.toDouble(), total = 100.0)
            }

            Text(
                text = "下一截止：${course.deadline}",
                color = BNBUColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "查看教学班详情",
                    color = BNBUColors.Blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = BNBUColors.Blue,
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
                    tint = BNBUColors.Ink
                )
                Text(
                    text = "返回我的课程",
                    color = BNBUColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
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
                    color = BNBUColors.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BNBUColors.BlueSoft)
            .border(1.dp, BNBUColors.Line, RectangleShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = fact.label.uppercase(),
            color = BNBUColors.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = fact.value,
            color = BNBUColors.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun DetailFactRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = BNBUColors.Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            color = BNBUColors.Muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TaskRow(task: CourseTask, course: Course?) {
    SwissPanel {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = task.creditType.courseIcon,
                contentDescription = null,
                tint = BNBUColors.Blue,
                modifier = Modifier.size(32.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        color = BNBUColors.Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(text = task.status.label, filled = task.status == TaskStatus.Active)
                }
                Text(
                    text = "${task.creditType.label} · ${task.hours.hourText()} · 截止 ${task.deadline}",
                    color = BNBUColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "证明要求：${task.proof}",
                    color = BNBUColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp
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
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = record.taskTitle,
                        color = BNBUColors.Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = record.submittedAt,
                        color = BNBUColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                StatusBadge(
                    text = record.status.label,
                    filled = record.status == ReviewStatus.Approved || record.status == ReviewStatus.Offset
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(text = record.creditType.label)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = record.hours.hourText(),
                    color = BNBUColors.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Text(
                text = "凭证：${record.proofSummary}",
                color = BNBUColors.Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "老师反馈：${record.teacherFeedback}",
                color = BNBUColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 21.sp
            )
        }
    }
}

private data class CourseFact(
    val label: String,
    val value: String
)

private val CreditType.courseIcon: ImageVector
    get() = when (this) {
        CreditType.CourseRelated -> Icons.Filled.AssignmentTurnedIn
        CreditType.General -> Icons.AutoMirrored.Filled.DirectionsRun
        CreditType.OrganizationOffset -> Icons.Filled.CheckCircle
    }
