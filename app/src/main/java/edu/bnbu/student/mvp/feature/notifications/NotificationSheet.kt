package edu.bnbu.student.mvp.feature.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.bnbu.student.mvp.core.designsystem.BNBUMotion
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.bnbuClickable
import edu.bnbu.student.mvp.core.model.NoticeCategory
import edu.bnbu.student.mvp.core.model.StudentNotice
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    var selectedFilter by rememberSaveable { mutableStateOf(NotificationFilter.All) }
    var selectedNoticeId by rememberSaveable { mutableStateOf<String?>(null) }
    var isDismissing by remember { mutableStateOf(false) }
    val selectedNotice = selectedNoticeId?.let { id -> notices.firstOrNull { it.id == id } }
    val dismissSheet: ((() -> Unit)?) -> Unit = dismiss@{ afterDismiss ->
        if (isDismissing) return@dismiss
        isDismissing = true
        scope.launch {
            try {
                sheetState.hide()
                onDismiss()
                afterDismiss?.invoke()
            } finally {
                isDismissing = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismissSheet(null) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        BackHandler(enabled = selectedNotice != null) {
            selectedNoticeId = null
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            NotificationSheetHeader(
                unreadCount = unreadCount,
                showingDetail = selectedNotice != null,
                onBack = { selectedNoticeId = null },
                onMarkAllRead = onMarkAllRead,
                onDismiss = { dismissSheet(null) }
            )

            AnimatedContent(
                targetState = selectedNoticeId,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                transitionSpec = {
                    val openingDetail = targetState != null
                    val enterOffset: (Int) -> Int = { width ->
                        if (openingDetail) width / 10 else -width / 10
                    }
                    val exitOffset: (Int) -> Int = { width ->
                        if (openingDetail) -width / 14 else width / 14
                    }
                    (fadeIn(
                        animationSpec = tween(
                            durationMillis = BNBUMotion.Standard,
                            easing = FastOutSlowInEasing
                        )
                    ) + slideInHorizontally(
                        animationSpec = tween(
                            durationMillis = BNBUMotion.Standard,
                            easing = FastOutSlowInEasing
                        ),
                        initialOffsetX = enterOffset
                    )) togetherWith (fadeOut(
                        animationSpec = tween(durationMillis = BNBUMotion.Quick)
                    ) + slideOutHorizontally(
                        animationSpec = tween(durationMillis = BNBUMotion.Standard),
                        targetOffsetX = exitOffset
                    ))
                },
                label = "notificationListDetail"
            ) { activeNoticeId ->
                val activeNotice = activeNoticeId?.let { id -> notices.firstOrNull { it.id == id } }
                if (activeNotice != null) {
                    NotificationDetail(
                        notice = activeNotice,
                        onMarkRead = onMarkRead
                    )
                } else {
                    NotificationList(
                        notices = notices,
                        selectedFilter = selectedFilter,
                        onFilterSelected = { selectedFilter = it },
                        onNoticeSelected = { notice ->
                            if (notice.isUnread) onMarkRead(notice.id)
                            if (notice.category == NoticeCategory.Review) {
                                dismissSheet { onOpenExemption(notice.targetId) }
                            } else {
                                selectedNoticeId = notice.id
                            }
                        }
                    )
                }
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
    Column(modifier = Modifier.fillMaxWidth()) {
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
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "关闭通知")
            }
        }
        if (!showingDetail) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(text = if (unreadCount > 0) "$unreadCount 条未读" else "暂无未读")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onMarkAllRead, enabled = unreadCount > 0) {
                    Text("全部标为已读")
                }
            }
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
    val filtered = remember(notices, selectedFilter) {
        notices.filter { notice ->
            when (selectedFilter) {
                NotificationFilter.All -> true
                NotificationFilter.Unread -> notice.isUnread
                NotificationFilter.Deadline -> notice.category == NoticeCategory.Deadline
                NotificationFilter.Application -> notice.category == NoticeCategory.Review
            }
        }
    }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(NotificationFilter.entries, key = { it.name }) { filter ->
            val selected = filter == selectedFilter
            FilterChip(
                selected = selected,
                onClick = { onFilterSelected(filter) },
                label = {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
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
    SwissPanel(modifier = Modifier.bnbuClickable(onClick = onClick)) {
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
