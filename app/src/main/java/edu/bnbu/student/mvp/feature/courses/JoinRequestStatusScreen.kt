package edu.bnbu.student.mvp.feature.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import edu.bnbu.student.mvp.core.designsystem.AppleIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import edu.bnbu.student.mvp.core.designsystem.ActionButton
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.bnbuClickable
import edu.bnbu.student.mvp.core.model.CourseJoinRequest
import edu.bnbu.student.mvp.core.model.JoinRequestStatus
import edu.bnbu.student.mvp.core.designsystem.interfaceText

/**
 * Shows the outcome of an invitation-based course join request.
 *
 * [inviteUnavailable] is set by the status-query owner when the server reports that the
 * invitation has expired or has been revoked. It intentionally has its own state instead of
 * overloading a missing request, which can also mean that a query is still loading.
 */
@Composable
fun JoinRequestStatusScreen(
    request: CourseJoinRequest?,
    inviteUnavailable: Boolean = false,
    onBack: () -> Unit,
    onContactTeacher: () -> Unit = {},
    onEditAndResubmit: (CourseJoinRequest) -> Unit = {},
    onUseNewInvite: () -> Unit = {},
    onApproved: () -> Unit = {}
) {
    if (request?.status == JoinRequestStatus.ACTIVE) {
        // Approved students belong in the normal course experience, never in this status page.
        LaunchedEffect(request.id) { onApproved() }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusScreenHeader(onBack = onBack)
        when {
            inviteUnavailable -> InviteUnavailablePanel(onContactTeacher = onContactTeacher)
            request == null -> JoinRequestUnavailablePanel(onBack = onBack)
            request.status == JoinRequestStatus.PENDING -> PendingPanel(
                request = request,
                onContactTeacher = onContactTeacher
            )
            request.status == JoinRequestStatus.NEEDS_CORRECTION -> CorrectionPanel(
                request = request,
                onEditAndResubmit = onEditAndResubmit
            )
            request.status == JoinRequestStatus.REJECTED -> RejectedPanel(
                request = request,
                onContactTeacher = onContactTeacher,
                onUseNewInvite = onUseNewInvite
            )
            request.status == JoinRequestStatus.ACTIVE -> Unit
        }
    }
}

/** Compact entry card shared by Dashboard and Courses. */
@Composable
fun JoinRequestEntryPanel(
    request: CourseJoinRequest,
    onOpen: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    SwissPanel(modifier = Modifier.bnbuClickable(onClick = onOpen)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = interfaceText("加入申请", "Join request"),
                    color = cs.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${request.courseCode} / Section ${request.section} · ${request.status.localizedLabel()}",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            StatusBadge(text = request.status.localizedLabel(), filled = request.status == JoinRequestStatus.PENDING)
        }
    }
}

@Composable
private fun StatusScreenHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = interfaceText("返回", "Back")
            )
        }
        Spacer(Modifier.width(4.dp))
        SectionTitle(
            eyebrow = interfaceText("课程加入", "Course Join"),
            title = interfaceText("加入申请", "Join request")
        )
    }
}

@Composable
private fun PendingPanel(
    request: CourseJoinRequest,
    onContactTeacher: () -> Unit
) {
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusHeading(
                title = interfaceText("申请状态：待教师审核", "Status: under teacher review"),
                icon = Icons.Filled.ErrorOutline
            )
            FactList(
                interfaceText("课程", "Course") to "${request.courseCode} / Section ${request.section}",
                interfaceText("班级", "Class") to request.courseName,
                interfaceText("教师", "Teacher") to request.teacherName,
                interfaceText("学期", "Term") to request.semester,
                interfaceText("提交时间", "Submitted") to request.submittedAt
            )
            ActionButton(
                title = interfaceText("联系教师", "Contact teacher"),
                icon = Icons.Filled.Email,
                filled = false,
                onClick = onContactTeacher
            )
        }
    }
}

@Composable
private fun CorrectionPanel(
    request: CourseJoinRequest,
    onEditAndResubmit: (CourseJoinRequest) -> Unit
) {
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusHeading(
                title = interfaceText("申请状态：需补正资料", "Status: information needed"),
                icon = Icons.Filled.Edit
            )
            ReviewComment(label = interfaceText("教师原因", "Teacher's note"), comment = request.reviewComment)
            ActionButton(
                title = interfaceText("修改并重新提交", "Edit and resubmit"),
                icon = Icons.Filled.Edit,
                filled = true,
                onClick = { onEditAndResubmit(request) }
            )
        }
    }
}

@Composable
private fun RejectedPanel(
    request: CourseJoinRequest,
    onContactTeacher: () -> Unit,
    onUseNewInvite: () -> Unit
) {
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusHeading(
                title = interfaceText("申请状态：已拒绝", "Status: rejected"),
                icon = Icons.Filled.ErrorOutline
            )
            ReviewComment(label = interfaceText("拒绝原因", "Reason for rejection"), comment = request.reviewComment)
            ActionButton(
                title = interfaceText("联系教师", "Contact teacher"),
                icon = Icons.Filled.Email,
                filled = false,
                onClick = onContactTeacher
            )
            ActionButton(
                title = interfaceText("使用新邀请码重新申请", "Apply again with a new invitation code"),
                icon = Icons.Filled.Refresh,
                filled = true,
                onClick = onUseNewInvite
            )
        }
    }
}

@Composable
private fun InviteUnavailablePanel(onContactTeacher: () -> Unit) {
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusHeading(
                title = interfaceText("该邀请已过期或已被教师撤销", "This invitation expired or was revoked by the teacher"),
                icon = Icons.Filled.ErrorOutline
            )
            Text(
                text = interfaceText("请联系教师获取新的二维码或邀请码", "Contact the teacher for a new QR code or invitation code."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
            ActionButton(
                title = interfaceText("联系教师", "Contact teacher"),
                icon = Icons.Filled.Email,
                filled = true,
                onClick = onContactTeacher
            )
        }
    }
}

@Composable
private fun JoinRequestUnavailablePanel(onBack: () -> Unit) {
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusHeading(
                title = interfaceText("暂时无法获取申请状态", "Unable to load request status"),
                icon = Icons.Filled.ErrorOutline
            )
            Text(
                text = interfaceText("请返回后刷新页面，或联系教师确认申请情况。", "Go back and refresh the page, or contact the teacher to confirm the request."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
            ActionButton(
                title = interfaceText("返回", "Back"),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                filled = false,
                onClick = onBack
            )
        }
    }
}

@Composable
private fun StatusHeading(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FactList(vararg facts: Pair<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        facts.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "$label：",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(76.dp)
                )
                Text(
                    text = value.ifBlank { interfaceText("待公布", "To be announced") },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReviewComment(label: String, comment: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$label：",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = comment.ifBlank { interfaceText("教师暂未填写说明，请联系教师确认。", "The teacher has not provided a note. Please contact the teacher.") },
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** Maps UI display values without changing the stable application status. */
private fun JoinRequestStatus.localizedLabel(): String = when (this) {
    JoinRequestStatus.PENDING -> interfaceText("待审核", "Under review")
    JoinRequestStatus.ACTIVE -> interfaceText("已通过", "Approved")
    JoinRequestStatus.REJECTED -> interfaceText("已拒绝", "Rejected")
    JoinRequestStatus.NEEDS_CORRECTION -> interfaceText("需补正", "Information needed")
}
