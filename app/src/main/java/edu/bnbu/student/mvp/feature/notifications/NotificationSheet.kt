package edu.bnbu.student.mvp.feature.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.model.NoticeCategory
import edu.bnbu.student.mvp.core.model.StudentNotice

private enum class NotificationFilter(val label: String) {
    All("全部"),
    Unread("未读"),
    Deadline("截止提醒"),
    Application("申请与材料")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSheet(
    notices: List<StudentNotice>,
    unreadCount: Int,
    onDismiss: () -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenExemption: (String?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedFilter by remember { mutableStateOf(NotificationFilter.All) }
    var selectedNoticeId by remember { mutableStateOf<String?>(null) }
    val selectedNotice = selectedNoticeId?.let { id -> notices.firstOrNull { it.id == id } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .padding(horizontal = 18.dp)
        ) {
            NotificationSheetHeader(
                unreadCount = unreadCount,
                showingDetail = selectedNotice != null,
                onBack = { selectedNoticeId = null },
                onMarkAllRead = onMarkAllRead,
                onDismiss = onDismiss
            )

            if (selectedNotice != null) {
                NotificationDetail(
                    notice = selectedNotice,
                    onMarkRead = onMarkRead
                )
            } else {
                NotificationList(
                    notices = notices,
                    selectedFilter = selectedFilter,
                    onFilterSelected = { selectedFilter = it },
                    onNoticeSelected = { notice ->
                        if (notice.category == NoticeCategory.Review) {
                            if (notice.isUnread) onMarkRead(notice.id)
                            onDismiss()
                            onOpenExemption(notice.targetId)
                        } else {
                            selectedNoticeId = notice.id
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun NotificationSheetHeader(
    unreadCount: Int,
    showingDetail: Boolean,
    onBack: () -> Unit,
    onMarkAllRead: () -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showingDetail) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "返回通知列表")
            }
        } else {
            Icon(
                imageVector = if (unreadCount > 0) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = if (showingDetail) "通知详情" else "通知",
            color = cs.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (!showingDetail) {
            StatusBadge(text = "未读 $unreadCount")
            TextButton(onClick = onMarkAllRead, enabled = unreadCount > 0) {
                Text("全部已读")
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = "关闭通知")
        }
    }
}

@Composable
private fun NotificationList(
    notices: List<StudentNotice>,
    selectedFilter: NotificationFilter,
    onFilterSelected: (NotificationFilter) -> Unit,
    onNoticeSelected: (StudentNotice) -> Unit
) {
    val filtered = notices.filter { notice ->
        when (selectedFilter) {
            NotificationFilter.All -> true
            NotificationFilter.Unread -> notice.isUnread
            NotificationFilter.Deadline -> notice.category == NoticeCategory.Deadline
            NotificationFilter.Application -> notice.category == NoticeCategory.Review
        }
    }
    val cs = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NotificationFilter.entries.forEach { filter ->
            val selected = filter == selectedFilter
            Text(
                text = filter.label,
                color = if (selected) cs.onPrimary else cs.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .background(
                        if (selected) cs.primary else cs.surfaceVariant,
                        MaterialTheme.shapes.small
                    )
                    .clickable { onFilterSelected(filter) }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            )
        }
    }

    if (filtered.isEmpty()) {
        EmptyPlaceholder(
            title = "暂无通知",
            message = "当前筛选条件下没有通知。"
        )
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filtered, key = { it.id }) { notice ->
                NotificationRow(notice = notice, onClick = { onNoticeSelected(notice) })
            }
        }
    }
}

@Composable
private fun NotificationRow(notice: StudentNotice, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    SwissPanel(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (notice.isUnread) Icons.Filled.NotificationsActive else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (notice.isUnread) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notice.title,
                        color = cs.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (notice.isUnread) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    Text(notice.time, color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                Text(notice.message, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NotificationDetail(notice: StudentNotice, onMarkRead: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SwissPanel {
                Text(notice.title, color = cs.onSurface, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.padding(top = 4.dp))
                Text(notice.time, color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.padding(top = 6.dp))
                Text(notice.message, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (notice.isUnread) {
            item {
                TextButton(onClick = { onMarkRead(notice.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("标记为已读")
                }
            }
        }
    }
}
