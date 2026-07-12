package edu.bnbu.student.mvp.feature.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.designsystem.BrandMark
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.HourProgressBar
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.model.CourseTask
import edu.bnbu.student.mvp.core.model.CreditType
import edu.bnbu.student.mvp.core.model.NoticeCategory
import edu.bnbu.student.mvp.core.model.StudentNotice
import edu.bnbu.student.mvp.core.model.hourText
import edu.bnbu.student.mvp.core.state.StudentAppState

@Composable
fun DashboardScreen(
    appState: StudentAppState,
    onOpenNotificationSheet: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { DashboardHeader(appState, onOpenNotificationSheet) }
        item { ProgressPanel(appState) }
        item { MetricsGrid(appState) }
        item { RiskPanel(appState) }
        item { FocusPlan(appState) }
        item { NextTasks(appState) }
    }
}

@Composable
private fun DashboardHeader(appState: StudentAppState, onOpenNotificationSheet: () -> Unit) {
    val cs = MaterialTheme.colorScheme
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
                color = cs.onSurface,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "${appState.workspace.student.college} · ${appState.workspace.student.id}",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            NotificationBell(
                unreadCount = appState.unreadNoticeCount,
                onClick = onOpenNotificationSheet
            )
            Spacer(Modifier.height(6.dp))
            StatusBadge(text = appState.workspace.progress.status, filled = true)
        }
    }
}

@Composable
private fun NotificationBell(unreadCount: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .background(cs.surfaceVariant, MaterialTheme.shapes.small)
        ) {
            Icon(
                imageVector = if (unreadCount > 0) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                contentDescription = "打开通知",
                tint = cs.onSurface
            )
        }
        if (unreadCount > 0) {
            Text(
                text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                color = cs.onPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .background(cs.primary, RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun ProgressPanel(appState: StudentAppState) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        SectionTitle(eyebrow = "Sports Credit", title = "体育学时进度")

        Spacer(Modifier.height(18.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = appState.totalCompleted.hourText(),
                color = cs.onSurface,
                style = MaterialTheme.typography.displaySmall
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "/ ${appState.hourRule.total.hourText()}",
                color = cs.onSurfaceVariant,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(appState.completionRatio * 100).toInt()}%",
                color = cs.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium
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
        Metric("Records", appState.workspace.records.count { it.creditType != CreditType.OrganizationOffset }.toString(), "本学期打卡记录")
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
    val cs = MaterialTheme.colorScheme
    val hasHourRisk = appState.hasHourRisk
    SwissPanel {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (hasHourRisk) Icons.Filled.Warning else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (hasHourRisk) cs.secondary else cs.primary,
                modifier = Modifier.size(28.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (hasHourRisk) "当前风险提示" else "当前状态稳定",
                    color = cs.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = appState.riskText,
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
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
private fun ProgressLine(
    title: String,
    value: Double,
    total: Double,
    detail: String
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = cs.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${value.hourText()} / ${total.hourText()}",
                color = cs.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.width(8.dp))
            StatusBadge(text = detail)
        }
        HourProgressBar(value = value, total = total)
    }
}

@Composable
private fun MetricCell(metric: Metric) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Text(
            text = metric.label.uppercase(),
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = metric.value,
            color = cs.onSurface,
            style = MaterialTheme.typography.headlineLarge
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = metric.footnote,
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ActionMiniMetric(label: String, value: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(cs.surfaceVariant, MaterialTheme.shapes.small)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = label,
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = value,
            color = cs.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium
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
    val cs = MaterialTheme.colorScheme
    FilledTonalButton(
        onClick = action,
        modifier = modifier,
        shape = MaterialTheme.shapes.large
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = title,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun FocusPlanRow(item: FocusPlanItem) {
    val cs = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(28.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    color = cs.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(text = item.status)
            }
            Text(
                text = item.detail,
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TaskRow(task: CourseTask) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = task.creditType.dashboardIcon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(32.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = task.title,
                        color = cs.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(text = task.creditType.label)
                }
                Text(
                    text = "截止：${task.deadline}",
                    color = cs.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "证明：${task.proof}",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun NoticeRow(notice: StudentNotice) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = notice.category.dashboardIcon,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = notice.category.label,
                    color = cs.primary,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.weight(1f))
                if (notice.isUnread) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(cs.primary, RoundedCornerShape(4.dp))
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notice.title,
                    color = cs.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(text = notice.time)
            }

            Text(
                text = notice.message,
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
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
        return "课程相关与其他运动均达到本学期要求，请继续保持运动并关注课程通知。"
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
