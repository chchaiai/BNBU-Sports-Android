package edu.bnbu.student.mvp.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.designsystem.BNBUColors
import edu.bnbu.student.mvp.core.designsystem.BrandMark
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.HourProgressBar
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.model.CourseTask
import edu.bnbu.student.mvp.core.model.CreditType
import edu.bnbu.student.mvp.core.model.NoticeCategory
import edu.bnbu.student.mvp.core.model.ReviewStatus
import edu.bnbu.student.mvp.core.model.StudentNotice
import edu.bnbu.student.mvp.core.model.hourText
import edu.bnbu.student.mvp.core.state.StudentAppState

@Composable
fun DashboardScreen(
    appState: StudentAppState,
    openCheckIn: () -> Unit = {},
    openGrades: () -> Unit = {},
    openProfile: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { DashboardHeader(appState) }
        item { ProgressPanel(appState) }
        item { MetricsGrid(appState) }
        item { RiskPanel(appState) }
        item { ActionPanel(appState, openCheckIn, openGrades, openProfile) }
        item { FocusPlan(appState) }
        item { NextTasks(appState) }
        item { Notices(appState) }
    }
}

@Composable
private fun DashboardHeader(appState: StudentAppState) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BrandMark(compact = true)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "你好，${appState.workspace.student.name}",
                color = BNBUColors.Ink,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "${appState.workspace.student.college} · ${appState.workspace.student.id}",
                color = BNBUColors.Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        StatusBadge(text = appState.workspace.progress.status, filled = true)
    }
}

@Composable
private fun ProgressPanel(appState: StudentAppState) {
    SwissPanel {
        SectionTitle(eyebrow = "Sports Credit", title = "体育学时进度")

        Spacer(Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = appState.totalCompleted.hourText(),
                color = BNBUColors.Ink,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 46.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "/ ${appState.hourRule.total.hourText()}",
                color = BNBUColors.Muted,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(appState.completionRatio * 100).toInt()}%",
                color = BNBUColors.Blue,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.height(14.dp))
        HourProgressBar(value = appState.totalCompleted, total = appState.hourRule.total)

        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            ProgressLine(
                title = "课程相关",
                value = appState.workspace.progress.course,
                total = appState.hourRule.courseRequired,
                detail = "还差 ${appState.courseRemaining.hourText()}"
            )
            ProgressLine(
                title = "其他运动",
                value = appState.workspace.progress.general,
                total = appState.hourRule.generalRequired,
                detail = if (appState.generalRemaining == 0.0) "已完成" else "还差 ${appState.generalRemaining.hourText()}"
            )
        }
    }
}

@Composable
private fun MetricsGrid(appState: StudentAppState) {
    val metrics = listOf(
        Metric("Total", "20h", "本学期总要求"),
        Metric("Course", "10h", "老师任务与课程相关"),
        Metric("General", "10h", "自主运动 / 组织抵扣"),
        Metric("Pending", appState.pendingCount.toString(), "当前待审核记录")
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .height(252.dp),
        userScrollEnabled = false,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(metrics) { metric ->
            MetricCell(metric)
        }
    }
}

@Composable
private fun RiskPanel(appState: StudentAppState) {
    val hasHourRisk = appState.hasHourRisk
    SwissPanel {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (hasHourRisk) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (hasHourRisk) BNBUColors.Blue else BNBUColors.Ink,
                modifier = Modifier.size(28.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (hasHourRisk) "当前风险提示" else "当前状态稳定",
                    color = BNBUColors.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = appState.riskText,
                    color = BNBUColors.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 21.sp
                )
            }
        }
    }
}

@Composable
private fun ActionPanel(
    appState: StudentAppState,
    openCheckIn: () -> Unit,
    openGrades: () -> Unit,
    openProfile: () -> Unit
) {
    SwissPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (appState.supplementRecordCount > 0) Icons.Filled.Error else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = BNBUColors.Ink
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "待处理",
                color = BNBUColors.Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            StatusBadge(text = "${appState.actionableRecordCount + appState.unreadNoticeCount}")
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionMiniMetric(label = "需补材料", value = appState.supplementRecordCount.toString(), modifier = Modifier.weight(1f))
            ActionMiniMetric(label = "待审核", value = appState.pendingRecordCount.toString(), modifier = Modifier.weight(1f))
            ActionMiniMetric(label = "未读通知", value = appState.unreadNoticeCount.toString(), modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = appState.actionText,
            color = BNBUColors.Muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 21.sp
        )

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DashboardShortcutButton("处理打卡", Icons.Filled.AddBox, Modifier.weight(1f), openCheckIn)
            DashboardShortcutButton("看通知", Icons.Filled.Notifications, Modifier.weight(1f), openProfile)
            DashboardShortcutButton("看成绩", Icons.Filled.BarChart, Modifier.weight(1f), openGrades)
        }
    }
}

@Composable
private fun FocusPlan(appState: StudentAppState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(eyebrow = "Plan", title = "本周行动计划")

        SwissPanel {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                appState.focusPlanItems.forEach { item ->
                    FocusPlanRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun NextTasks(appState: StudentAppState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(eyebrow = "Deadline", title = "近期任务")

        if (appState.activeTasks.isEmpty()) {
            EmptyPlaceholder(
                title = "暂无近期任务",
                message = "当前没有进行中的打卡任务；新任务发布后会在这里显示。"
            )
        } else {
            appState.activeTasks.take(2).forEach { task ->
                TaskRow(task = task)
            }
        }
    }
}

@Composable
private fun Notices(appState: StudentAppState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(eyebrow = "Notice", title = "通知 / 截止提醒")

        if (appState.workspace.notices.isEmpty()) {
            EmptyPlaceholder(title = "暂无通知", message = "当前没有截止提醒或审核反馈。")
        } else {
            appState.workspace.notices.take(2).forEach { notice ->
                NoticeRow(notice = notice)
            }
        }
    }
}

@Composable
private fun ProgressLine(
    title: String,
    value: Double,
    total: Double,
    detail: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = BNBUColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${value.hourText()} / ${total.hourText()}",
                color = BNBUColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.width(8.dp))
            StatusBadge(text = detail)
        }
        HourProgressBar(value = value, total = total)
    }
}

@Composable
private fun MetricCell(metric: Metric) {
    SwissPanel {
        Text(
            text = metric.label.uppercase(),
            color = BNBUColors.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = metric.value,
            color = BNBUColors.Ink,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = metric.footnote,
            color = BNBUColors.Muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun ActionMiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(BNBUColors.BlueSoft)
            .border(1.dp, BNBUColors.Line, RectangleShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            color = BNBUColors.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = value,
            color = BNBUColors.Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun DashboardShortcutButton(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    action: () -> Unit
) {
    Row(
        modifier = modifier
            .background(BNBUColors.Ink)
            .clickable(onClick = action)
            .padding(vertical = 11.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = BNBUColors.Surface,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = title,
            color = BNBUColors.Surface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun FocusPlanRow(item: FocusPlanItem) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = BNBUColors.Blue,
            modifier = Modifier.size(28.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    color = BNBUColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(text = item.status)
            }
            Text(
                text = item.detail,
                color = BNBUColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun TaskRow(task: CourseTask) {
    SwissPanel {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = task.creditType.dashboardIcon,
                contentDescription = null,
                tint = BNBUColors.Blue,
                modifier = Modifier.size(32.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        color = BNBUColors.Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(text = task.creditType.label)
                }
                Text(
                    text = "截止：${task.deadline}",
                    color = BNBUColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "证明：${task.proof}",
                    color = BNBUColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun NoticeRow(notice: StudentNotice) {
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = notice.category.dashboardIcon,
                    contentDescription = null,
                    tint = BNBUColors.Blue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = notice.category.label,
                    color = BNBUColors.Blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.weight(1f))
                if (notice.isUnread) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(BNBUColors.Blue)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notice.title,
                    color = BNBUColors.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(text = notice.time)
            }

            Text(
                text = notice.message,
                color = BNBUColors.Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp
            )
        }
    }
}

private data class Metric(
    val label: String,
    val value: String,
    val footnote: String
)

private data class FocusPlanItem(
    val title: String,
    val detail: String,
    val icon: ImageVector,
    val status: String
)

private val StudentAppState.pendingCount: Int
    get() = workspace.records.count { it.status == ReviewStatus.Pending || it.status == ReviewStatus.Supplement }

private val StudentAppState.hasHourRisk: Boolean
    get() = courseRemaining > 0.0 || generalRemaining > 0.0

private val StudentAppState.riskText: String
    get() {
        if (courseRemaining > 0.0 && generalRemaining > 0.0) {
            return "课程相关还差 ${courseRemaining.hourText()}，其他运动还差 ${generalRemaining.hourText()}。请优先关注课程任务和可计入的自主运动。"
        }
        if (courseRemaining > 0.0) {
            return "课程相关还差 ${courseRemaining.hourText()}。其他运动已由组织认证完成，但不能替代课程相关学时。"
        }
        if (generalRemaining > 0.0) {
            return "其他运动还差 ${generalRemaining.hourText()}。可通过自主运动打卡或有效组织认证完成。"
        }
        return "课程相关与其他运动均达到本学期要求，请继续关注审核反馈和成绩缺失项。"
    }

private val StudentAppState.actionText: String
    get() {
        if (supplementRecordCount > 0) {
            return "有 ${supplementRecordCount} 条记录需要补充材料，请进入打卡记录处理。"
        }
        if (pendingRecordCount > 0) {
            return "已有记录进入审核队列，审核通过后才会计入有效小时。"
        }
        return "暂无需要补交的材料，继续关注截止提醒与课程任务。"
    }

private val StudentAppState.focusPlanItems: List<FocusPlanItem>
    get() {
        val items = mutableListOf<FocusPlanItem>()
        if (courseRemaining > 0.0) {
            items += FocusPlanItem(
                title = "优先补齐课程相关 ${courseRemaining.hourText()}",
                detail = if (activeTasks.isEmpty()) {
                    "课程相关不能被组织抵扣替代；当前暂无可提交任务，请等待老师发布。"
                } else {
                    "课程相关不能被组织抵扣替代，建议先完成 GEPE101 相关任务。"
                },
                icon = Icons.Filled.TrackChanges,
                status = "高优先级"
            )
        }
        if (supplementRecordCount > 0) {
            items += FocusPlanItem(
                title = "处理 ${supplementRecordCount} 条补材料记录",
                detail = "按老师反馈补充图片或视频后，会重新进入待审核队列。",
                icon = Icons.Filled.UploadFile,
                status = "需动作"
            )
        }
        if (pendingRecordCount > 0) {
            items += FocusPlanItem(
                title = "等待 ${pendingRecordCount} 条审核结果",
                detail = "待审核记录暂不计入有效小时，请留意审核反馈。",
                icon = Icons.Filled.HourglassEmpty,
                status = "跟进"
            )
        }
        if (unreadNoticeCount > 0) {
            items += FocusPlanItem(
                title = "查看 ${unreadNoticeCount} 条未读提醒",
                detail = "优先确认截止时间和补材料通知。",
                icon = Icons.Filled.NotificationsActive,
                status = "提醒"
            )
        }
        if (items.isEmpty()) {
            items += FocusPlanItem(
                title = "当前没有阻塞事项",
                detail = "保持运动记录连续性，关注下一次课程任务发布。",
                icon = Icons.Filled.AssignmentTurnedIn,
                status = "稳定"
            )
        }
        return items.take(4)
    }

private val CreditType.dashboardIcon: ImageVector
    get() = when (this) {
        CreditType.CourseRelated -> Icons.Filled.AssignmentTurnedIn
        CreditType.General -> Icons.AutoMirrored.Filled.DirectionsRun
        CreditType.OrganizationOffset -> Icons.Filled.CheckCircle
    }

private val NoticeCategory.dashboardIcon: ImageVector
    get() = when (this) {
        NoticeCategory.Deadline -> Icons.Filled.NotificationsActive
        NoticeCategory.Review -> Icons.Filled.AssignmentTurnedIn
        NoticeCategory.Organization -> Icons.Filled.CheckCircle
        NoticeCategory.System -> Icons.Filled.RadioButtonUnchecked
    }
