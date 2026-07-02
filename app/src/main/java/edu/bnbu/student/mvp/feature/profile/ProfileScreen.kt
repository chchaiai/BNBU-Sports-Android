package edu.bnbu.student.mvp.feature.profile

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.designsystem.ActionButton
import edu.bnbu.student.mvp.core.designsystem.BNBUColors
import edu.bnbu.student.mvp.core.designsystem.BrandMark
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.model.Membership
import edu.bnbu.student.mvp.core.model.NoticeCategory
import edu.bnbu.student.mvp.core.model.StudentNotice
import edu.bnbu.student.mvp.core.state.StudentAppState

private enum class NoticeFilter(val label: String) {
    All("全部"),
    Unread("未读"),
    Deadline("截止"),
    Review("审核")
}

@Composable
private fun QuickActionsPanel(
    onOpenScoring: () -> Unit,
    onOpenExemption: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(eyebrow = "Tools", title = "体测工具")

        SwissPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    title = "耐力跑成绩换算",
                    icon = Icons.Filled.Timer,
                    filled = false,
                    onClick = onOpenScoring
                )
                ActionButton(
                    title = "免测申请 (800m / 1000m)",
                    icon = Icons.Filled.FitnessCenter,
                    filled = false,
                    onClick = onOpenExemption
                )
            }
        }
    }
}

@Composable
fun ProfileScreen(
    appState: StudentAppState,
    onOpenScoring: () -> Unit = {},
    onOpenExemption: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {}
) {
    var selectedNoticeFilter by remember { mutableStateOf(NoticeFilter.All) }
    var selectedNoticeId by remember { mutableStateOf<String?>(null) }
    val selectedNotice = selectedNoticeId?.let { id ->
        appState.workspace.notices.firstOrNull { it.id == id }
    }

    if (selectedNotice != null) {
        NoticeDetailScreen(
            appState = appState,
            notice = selectedNotice,
            onBack = { selectedNoticeId = null }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { ProfileHeader(appState) }

        item { QuickActionsPanel(onOpenScoring, onOpenExemption) }
        item { TeacherPanel(appState) }
        item { IdentityPanel(appState) }
        item {
            NotificationPanel(
                appState = appState,
                selectedFilter = selectedNoticeFilter,
                onFilterSelected = { selectedNoticeFilter = it },
                onNoticeSelected = { selectedNoticeId = it.id }
            )
        }
        item {
            SettingsPanel(appState = appState, onOpenPrivacy = onOpenPrivacy)
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun ProfileHeader(appState: StudentAppState) {
    val student = appState.workspace.student

    SwissPanel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            BrandMark(compact = true)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = student.name,
                    color = BNBUColors.Ink,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${student.id} · ${student.college}",
                    color = BNBUColors.Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${student.className} · ${student.genderLabel} · ${student.gradeLabel}",
                        color = BNBUColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            StatusBadge(text = student.status, filled = true)
        }
    }
}

@Composable
private fun TeacherPanel(appState: StudentAppState) {
    val teachers = appState.workspace.teachers
    if (teachers.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(eyebrow = "My Teacher", title = "我的老师")
        teachers.forEach { teacher ->
            SwissPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = BNBUColors.Blue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = teacher.teacherName,
                            color = BNBUColors.Ink,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "教师ID: ${teacher.teacherId}",
                            color = BNBUColors.Muted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityPanel(appState: StudentAppState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(eyebrow = "Identity", title = "校队 / 社团抵扣状态")

        if (appState.workspace.memberships.isEmpty()) {
            EmptyPlaceholder(
                title = "暂无认证记录",
                message = "当前没有校队或社团抵扣认证。认证生效后，只能抵扣其他运动小时，不能替代课程相关小时。"
            )
        } else {
            appState.workspace.memberships.forEach { membership ->
                MembershipCard(membership)
            }
        }
    }
}

@Composable
private fun MembershipCard(membership: Membership) {
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${membership.typeTitle} · ${membership.organization}",
                        color = BNBUColors.Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "有效期至 ${membership.validUntil}",
                        color = BNBUColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(text = membership.status, filled = membership.status == "认证有效")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "抵扣: ${membership.offset}",
                            color = BNBUColors.Blue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (membership.comment.isNotBlank() && membership.comment != "offset") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BNBUColors.BlueSoft)
                        .border(1.dp, BNBUColors.Line, RectangleShape)
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = BNBUColors.Blue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = membership.comment,
                        color = BNBUColors.Muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 21.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationPanel(
    appState: StudentAppState,
    selectedFilter: NoticeFilter,
    onFilterSelected: (NoticeFilter) -> Unit,
    onNoticeSelected: (StudentNotice) -> Unit
) {
    val filteredNotices = appState.workspace.notices.filter { notice ->
        when (selectedFilter) {
            NoticeFilter.All -> true
            NoticeFilter.Unread -> notice.isUnread
            NoticeFilter.Deadline -> notice.category == NoticeCategory.Deadline
            NoticeFilter.Review -> notice.category == NoticeCategory.Review
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(
                eyebrow = "Notifications",
                title = "通知 (${filteredNotices.size})",
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NoticeFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                Text(
                    text = filter.label,
                    color = if (isSelected) BNBUColors.Surface else BNBUColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .background(
                            if (isSelected) BNBUColors.Blue else BNBUColors.Surface,
                            RectangleShape
                        )
                        .border(1.5.dp, if (isSelected) BNBUColors.Blue else BNBUColors.Line, RectangleShape)
                        .clickable { onFilterSelected(filter) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (filteredNotices.isEmpty()) {
            EmptyPlaceholder(
                title = "暂无通知",
                message = "当前筛选条件下没有匹配的通知。"
            )
        } else {
            filteredNotices.forEach { notice ->
                NoticeRow(
                    notice = notice,
                    onClick = { onNoticeSelected(notice) }
                )
            }
        }
    }
}

@Composable
private fun NoticeRow(
    notice: StudentNotice,
    onClick: () -> Unit
) {
    SwissPanel(
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (notice.isUnread) Icons.Filled.Notifications else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (notice.isUnread) BNBUColors.Blue else BNBUColors.Muted,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = notice.title,
                    color = BNBUColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = notice.time,
                    color = BNBUColors.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = notice.message,
                color = BNBUColors.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun NoticeDetailScreen(
    appState: StudentAppState,
    notice: StudentNotice,
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
                    text = "返回",
                    color = BNBUColors.Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        item {
            SwissPanel {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = notice.title,
                        color = BNBUColors.Ink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = notice.time,
                        color = BNBUColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = notice.message,
                        color = BNBUColors.Muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp
                    )
                }
            }
        }

        if (notice.isUnread) {
            item {
                ActionButton(
                    title = "标记为已读",
                    icon = Icons.Filled.CheckCircle,
                    filled = true,
                    onClick = {
                        if (notice.isUnread) {
                            appState.markNoticeRead(notice.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    appState: StudentAppState,
    onOpenPrivacy: () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(eyebrow = "Settings", title = "设置")

        SwissPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingLine(label = "学生姓名", value = appState.workspace.student.name)
                SettingLine(label = "学号", value = appState.workspace.student.id)
                SettingLine(label = "学院", value = appState.workspace.student.college)
                SettingLine(label = "班级", value = appState.workspace.student.className)
                SettingLine(label = "App 版本", value = "BNBU Student MVP 1.0")
            }
        }

        SwissPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    title = "隐私政策",
                    icon = Icons.Filled.FitnessCenter,
                    filled = false,
                    onClick = onOpenPrivacy
                )
                ActionButton(
                    title = "退出登录",
                    icon = Icons.Filled.Clear,
                    filled = true,
                    onClick = appState::logout
                )
            }
        }
    }
}

@Composable
private fun SettingLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InlineAction(
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .background(BNBUColors.Surface)
            .border(1.5.dp, BNBUColors.Line, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) BNBUColors.Blue else BNBUColors.Muted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = if (enabled) BNBUColors.Ink else BNBUColors.Muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1f)
        )
        if (enabled) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = BNBUColors.Muted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
