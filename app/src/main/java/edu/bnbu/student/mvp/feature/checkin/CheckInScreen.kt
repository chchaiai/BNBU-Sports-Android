package edu.bnbu.student.mvp.feature.checkin

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import edu.bnbu.student.mvp.core.designsystem.ActionButton
import edu.bnbu.student.mvp.core.designsystem.BNBUColors
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.SegmentedControl
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.StatusMessagePanel
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.ValidationPanel
import edu.bnbu.student.mvp.core.model.CheckInRecord
import edu.bnbu.student.mvp.core.model.Course
import edu.bnbu.student.mvp.core.model.CourseTask
import edu.bnbu.student.mvp.core.model.CreditType
import edu.bnbu.student.mvp.core.model.ProofAttachment
import edu.bnbu.student.mvp.core.model.ProofMediaType
import edu.bnbu.student.mvp.core.model.ProofUploadRule
import edu.bnbu.student.mvp.core.model.ReviewStatus
import edu.bnbu.student.mvp.core.model.TaskStatus
import edu.bnbu.student.mvp.core.model.hourText
import edu.bnbu.student.mvp.core.state.StudentAppState
import java.io.File
import java.util.UUID

private enum class CheckInSegment(val label: String) {
    Tasks("任务"),
    Submit("提交"),
    Records("记录")
}

private enum class TaskScopeFilter(val label: String) {
    All("全部"),
    Course("课程相关"),
    General("其他运动")
}

private enum class RecordScopeFilter(val label: String) {
    All("全部"),
    Pending("待审核"),
    Approved("已通过"),
    Rejected("被驳回"),
    Supplement("需补材料"),
    Offset("系统抵扣")
}

@Composable
fun CheckInScreen(appState: StudentAppState) {
    var selectedSegment by remember { mutableStateOf(CheckInSegment.Tasks) }
    var selectedTaskFilter by remember { mutableStateOf(TaskScopeFilter.All) }
    var selectedRecordFilter by remember { mutableStateOf(RecordScopeFilter.All) }
    var selectedTaskId by remember { mutableStateOf(appState.activeTasks.firstOrNull()?.id.orEmpty()) }
    var hours by remember { mutableStateOf(1.0) }
    var note by remember { mutableStateOf("") }
    var proofAttachments by remember { mutableStateOf<List<ProofAttachment>>(emptyList()) }
    var supplementingRecordId by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val supplementingRecord = supplementingRecordId?.let { id ->
        appState.workspace.records.firstOrNull { it.id == id }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SegmentedControl(
                values = CheckInSegment.entries,
                selected = selectedSegment,
                label = { it.label },
                onSelected = { selectedSegment = it }
            )
        }

        statusMessage?.let { message ->
            item {
                StatusMessagePanel(
                    message = message,
                    onDismiss = { statusMessage = null }
                )
            }
        }

        when (selectedSegment) {
            CheckInSegment.Tasks -> {
                item {
                    TaskListIntro(
                        selectedTaskFilter = selectedTaskFilter,
                        onFilterSelected = { selectedTaskFilter = it }
                    )
                }

                val tasks = appState.workspace.tasks.filter { task ->
                    when (selectedTaskFilter) {
                        TaskScopeFilter.All -> true
                        TaskScopeFilter.Course -> task.creditType == CreditType.CourseRelated
                        TaskScopeFilter.General -> task.creditType == CreditType.General
                    }
                }

                if (tasks.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            title = if (selectedTaskFilter == TaskScopeFilter.All) "暂无打卡任务" else "暂无${selectedTaskFilter.label}任务",
                            message = "当前没有可展示的任务；若老师后续发布新任务，会出现在这里。"
                        )
                    }
                } else {
                    items(tasks) { task ->
                        TaskActionCard(
                            task = task,
                            course = appState.workspace.courses.firstOrNull { it.id == task.courseId },
                            onStart = {
                                if (task.status == TaskStatus.Active) {
                                    selectedTaskId = task.id
                                    hours = appState.hourLimitFor(task)
                                    note = ""
                                    proofAttachments = emptyList()
                                    supplementingRecordId = null
                                    statusMessage = null
                                    selectedSegment = CheckInSegment.Submit
                                }
                            }
                        )
                    }
                }
            }

            CheckInSegment.Submit -> {
                item {
                    SubmitShell(
                        appState = appState,
                        selectedTaskId = selectedTaskId,
                        hours = hours,
                        note = note,
                        proofAttachments = proofAttachments,
                        supplementingRecord = supplementingRecord,
                        onTaskSelected = { taskId ->
                            selectedTaskId = taskId
                            supplementingRecordId = null
                            appState.activeTasks.firstOrNull { it.id == taskId }?.let { task ->
                                hours = appState.normalizedHours(hours, task)
                            }
                        },
                        onHoursChanged = { hours = it },
                        onNoteChanged = { note = it },
                        onProofAttachmentsChanged = { proofAttachments = it },
                        onClearSupplement = {
                            supplementingRecordId = null
                            note = ""
                            proofAttachments = emptyList()
                        },
                        onSubmitComplete = { message ->
                            statusMessage = message
                            selectedRecordFilter = RecordScopeFilter.Pending
                            selectedSegment = CheckInSegment.Records
                            supplementingRecordId = null
                            note = ""
                            proofAttachments = emptyList()
                        }
                    )
                }
            }

            CheckInSegment.Records -> {
                item {
                    RecordListIntro(
                        selectedRecordFilter = selectedRecordFilter,
                        onFilterSelected = { selectedRecordFilter = it }
                    )
                }

                val records = appState.workspace.records.filter { record ->
                    when (selectedRecordFilter) {
                        RecordScopeFilter.All -> true
                        RecordScopeFilter.Pending -> record.status == ReviewStatus.Pending
                        RecordScopeFilter.Approved -> record.status == ReviewStatus.Approved
                        RecordScopeFilter.Rejected -> record.status == ReviewStatus.Rejected
                        RecordScopeFilter.Supplement -> record.status == ReviewStatus.Supplement
                        RecordScopeFilter.Offset -> record.status == ReviewStatus.Offset
                    }
                }

                if (records.isEmpty()) {
                    item {
                        EmptyPlaceholder(title = "暂无记录", message = "当前筛选条件下没有打卡记录。")
                    }
                } else {
                    items(records) { record ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            RecordCard(record)
                            if (record.status == ReviewStatus.Supplement || record.status == ReviewStatus.Rejected) {
                                ActionButton(
                                    title = if (record.status == ReviewStatus.Supplement) "补交材料" else "重新提交材料",
                                    icon = Icons.Filled.UploadFile,
                                    filled = true,
                                    onClick = {
                                        supplementingRecordId = record.id
                                        selectedTaskId = ""
                                        hours = record.hours.coerceIn(0.5, appState.hourRule.dailyLimit)
                                        note = "按老师反馈补充材料：${record.teacherFeedback}"
                                        proofAttachments = emptyList()
                                        statusMessage = null
                                        selectedSegment = CheckInSegment.Submit
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskListIntro(
    selectedTaskFilter: TaskScopeFilter,
    onFilterSelected: (TaskScopeFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(eyebrow = "Check-In Tasks", title = "打卡任务列表")
        Text(
            text = "课程相关任务由老师发布；其他运动任务用于自主运动或组织活动。审核通过后才计入有效学时。",
            color = BNBUColors.Muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 21.sp
        )
        SegmentedControl(
            values = TaskScopeFilter.entries,
            selected = selectedTaskFilter,
            label = { it.label },
            onSelected = onFilterSelected
        )
    }
}

@Composable
private fun RecordListIntro(
    selectedRecordFilter: RecordScopeFilter,
    onFilterSelected: (RecordScopeFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(eyebrow = "Records", title = "打卡记录")
        SegmentedControl(
            values = RecordScopeFilter.entries,
            selected = selectedRecordFilter,
            label = { it.label },
            onSelected = onFilterSelected
        )
    }
}

@Composable
private fun SubmitShell(
    appState: StudentAppState,
    selectedTaskId: String,
    hours: Double,
    note: String,
    proofAttachments: List<ProofAttachment>,
    supplementingRecord: CheckInRecord?,
    onTaskSelected: (String) -> Unit,
    onHoursChanged: (Double) -> Unit,
    onNoteChanged: (String) -> Unit,
    onProofAttachmentsChanged: (List<ProofAttachment>) -> Unit,
    onClearSupplement: () -> Unit,
    onSubmitComplete: (String) -> Unit
) {
    val activeTasks = appState.activeTasks
    val selectedTask = activeTasks.firstOrNull { it.id == selectedTaskId } ?: activeTasks.firstOrNull()
    val maxHours = selectedTask?.let { appState.hourLimitFor(it) } ?: appState.hourRule.dailyLimit
    val totalProofCount = proofAttachments.size + (supplementingRecord?.proofFiles?.size ?: 0)
    val validationMessage = submitValidationMessage(
        selectedTask = selectedTask,
        supplementingRecord = supplementingRecord,
        hours = hours,
        maxHours = maxHours,
        newProofs = proofAttachments,
        totalProofCount = totalProofCount
    )
    var showConfirm by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle(
            eyebrow = if (supplementingRecord == null) "Submit" else "Supplement",
            title = if (supplementingRecord == null) "提交打卡" else "补交材料"
        )

        if (activeTasks.isEmpty() && supplementingRecord == null) {
            EmptyPlaceholder(
                title = "暂无可提交任务",
                message = "当前没有进行中的打卡任务。已关闭任务只能查看，不能提交；请等待老师发布新任务或查看已有记录。"
            )
        } else {
            if (appState.draft != null && supplementingRecord == null) {
                DraftPanel(
                    appState = appState,
                    onRestore = {
                        val draft = appState.draft
                        if (draft != null) {
                            val draftTask = activeTasks.firstOrNull { it.id == draft.taskId }
                            if (draftTask != null) {
                                onTaskSelected(draft.taskId)
                                onHoursChanged(draft.hours)
                                onNoteChanged(draft.note)
                                onProofAttachmentsChanged(draft.proofAttachments)
                            }
                        }
                    }
                )
            }

            supplementingRecord?.let { record ->
                SupplementPanel(record = record, onCancel = onClearSupplement)
            }

            if (supplementingRecord == null) {
                TaskSelectionPanel(
                    activeTasks = activeTasks,
                    selectedTask = selectedTask,
                    appState = appState,
                    onTaskSelected = onTaskSelected
                )
            }

            SubmitDetailPanel(
                selectedTask = selectedTask,
                supplementingRecord = supplementingRecord,
                appState = appState,
                hours = hours,
                maxHours = maxHours,
                note = note,
                proofAttachments = proofAttachments,
                totalProofCount = totalProofCount,
                onHoursChanged = onHoursChanged,
                onNoteChanged = onNoteChanged,
                onProofAttachmentsChanged = onProofAttachmentsChanged
            )

            validationMessage?.let {
                ValidationPanel(message = it)
            }

            if (showConfirm) {
                ConfirmSubmitPanel(
                    supplementingRecord = supplementingRecord,
                    selectedTask = selectedTask,
                    hours = hours,
                    proofCount = proofAttachments.size,
                    onCancel = { showConfirm = false },
                    onConfirm = {
                        showConfirm = false
                        if (supplementingRecord != null) {
                            appState.submitSupplement(
                                record = supplementingRecord,
                                hours = hours,
                                note = note,
                                proofAttachments = proofAttachments
                            )
                            onSubmitComplete("补充材料已提交，记录已回到待审核状态。")
                        } else if (selectedTask != null) {
                            appState.submitCheckIn(
                                task = selectedTask,
                                hours = hours,
                                note = note,
                                proofAttachments = proofAttachments
                            )
                            onSubmitComplete("打卡已提交，审核通过后将计入有效学时。")
                        }
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (supplementingRecord == null && selectedTask != null) {
                    ActionButton(
                        title = "保存草稿",
                        icon = Icons.Filled.Save,
                        filled = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            appState.saveDraft(
                                taskId = selectedTask.id,
                                hours = hours,
                                note = note,
                                proofAttachments = proofAttachments
                            )
                        }
                    )
                }
                ActionButton(
                    title = if (supplementingRecord == null) "提交审核" else "提交补交",
                    icon = Icons.Filled.UploadFile,
                    filled = validationMessage == null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (validationMessage == null) {
                            showConfirm = true
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DraftPanel(
    appState: StudentAppState,
    onRestore: () -> Unit
) {
    val draft = appState.draft ?: return
    val task = appState.workspace.tasks.firstOrNull { it.id == draft.taskId }

    SwissPanel {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "本地草稿",
                    color = BNBUColors.Ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "${task?.title ?: "任务已失效"} · ${draft.hours.hourText()} · ${draft.proofAttachments.size} 个凭证 · ${draft.updatedAt}",
                    color = BNBUColors.Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 19.sp
                )
            }
            StatusBadge(text = "Local", filled = true)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                title = "恢复草稿",
                icon = Icons.Filled.UploadFile,
                filled = true,
                modifier = Modifier.weight(1f),
                onClick = onRestore
            )
            ActionButton(
                title = "清除",
                icon = Icons.Filled.Clear,
                filled = false,
                modifier = Modifier.weight(1f),
                onClick = appState::clearDraft
            )
        }
    }
}

@Composable
private fun SupplementPanel(record: CheckInRecord, onCancel: () -> Unit) {
    SwissPanel {
        Text(
            text = "正在补交",
            color = BNBUColors.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "${record.taskTitle} · 原凭证 ${record.proofFiles.size} 个 · 老师反馈：${record.teacherFeedback}",
            color = BNBUColors.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(12.dp))
        ActionButton(
            title = "取消补交",
            icon = Icons.Filled.Clear,
            filled = false,
            onClick = onCancel
        )
    }
}

@Composable
private fun TaskSelectionPanel(
    activeTasks: List<CourseTask>,
    selectedTask: CourseTask?,
    appState: StudentAppState,
    onTaskSelected: (String) -> Unit
) {
    SwissPanel {
        Text(
            text = "选择任务",
            color = BNBUColors.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            activeTasks.forEach { task ->
                TaskPickerRow(
                    task = task,
                    selected = task.id == selectedTask?.id,
                    onClick = { onTaskSelected(task.id) }
                )
            }
        }

        if (selectedTask != null) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(text = selectedTask.creditType.label, filled = true)
                Spacer(Modifier.width(8.dp))
                StatusBadge(text = "最多 ${appState.hourLimitFor(selectedTask).hourText()}")
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (selectedTask.creditType == CreditType.CourseRelated) {
                    "本次记录将计入课程相关体育学时。"
                } else {
                    "本次记录将计入其他运动学时，不能替代课程相关学时。"
                },
                color = BNBUColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun SubmitDetailPanel(
    selectedTask: CourseTask?,
    supplementingRecord: CheckInRecord?,
    appState: StudentAppState,
    hours: Double,
    maxHours: Double,
    note: String,
    proofAttachments: List<ProofAttachment>,
    totalProofCount: Int,
    onHoursChanged: (Double) -> Unit,
    onNoteChanged: (String) -> Unit,
    onProofAttachmentsChanged: (List<ProofAttachment>) -> Unit
) {
    SwissPanel {
        Text(
            text = "本次学时",
            color = BNBUColors.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(12.dp))
        HoursControl(
            value = hours,
            maxHours = maxHours,
            onChange = { value ->
                val normalized = if (selectedTask != null) {
                    appState.normalizedHours(value, selectedTask)
                } else {
                    value.coerceIn(0.5, appState.hourRule.dailyLimit)
                }
                onHoursChanged(normalized)
            }
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = "补充说明",
            color = BNBUColors.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(10.dp))
        NoteEditor(
            value = note,
            placeholder = if (supplementingRecord == null) {
                "例如：今天在南区操场完成 5km 慢跑，上传运动轨迹截图和现场照片。"
            } else {
                "说明本次补交了哪些材料，回应老师反馈。"
            },
            onValueChange = onNoteChanged
        )

        Spacer(Modifier.height(18.dp))
        ProofAttachmentPanel(
            proofAttachments = proofAttachments,
            totalProofCount = totalProofCount,
            onProofAttachmentsChanged = onProofAttachmentsChanged
        )
    }
}

@Composable
private fun HoursControl(
    value: Double,
    maxHours: Double,
    onChange: (Double) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, BNBUColors.Line, RectangleShape)
            .background(BNBUColors.Surface)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SquareIconButton(
            icon = Icons.Filled.RemoveCircle,
            enabled = value > 0.5,
            onClick = { onChange(value - 0.5) }
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value.hourText(),
                color = BNBUColors.Ink,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "单次最多 ${maxHours.hourText()}",
                color = BNBUColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        SquareIconButton(
            icon = Icons.Filled.AddCircle,
            enabled = value < maxHours,
            onClick = { onChange(value + 0.5) }
        )
    }
}

@Composable
private fun NoteEditor(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .background(BNBUColors.Surface)
            .border(1.5.dp, BNBUColors.Line, RectangleShape)
            .padding(12.dp)
    ) {
        if (value.isBlank()) {
            Text(
                text = placeholder,
                color = BNBUColors.Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(
                color = BNBUColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp
            ),
            cursorBrush = SolidColor(BNBUColors.Blue)
        )
    }
}

@Composable
private fun ProofAttachmentPanel(
    proofAttachments: List<ProofAttachment>,
    totalProofCount: Int,
    onProofAttachmentsChanged: (List<ProofAttachment>) -> Unit
) {
    val context = LocalContext.current
    var attachmentNotice by remember { mutableStateOf<String?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }

    // Camera launcher (AND-003)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraTempUri
        if (success && uri != null) {
            val attachment = uri.toProofAttachmentFromCamera(context, proofAttachments.size)
            if (attachment != null && attachment.isValidForUpload) {
                onProofAttachmentsChanged(proofAttachments + attachment)
                attachmentNotice = "已拍摄 1 张现场凭证。"
            } else {
                attachmentNotice = "拍摄失败，请重试或从相册选择。"
            }
        }
        cameraTempUri = null
    }

    // Document picker launcher (existing gallery selector)
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val remaining = ProofUploadRule.maxAttachmentCount - totalProofCount
        if (remaining <= 0) {
            attachmentNotice = "已达到 ${ProofUploadRule.maxAttachmentCount} 个凭证上限。"
            return@rememberLauncherForActivityResult
        }

        val selectedUris = uris.take(remaining)
        val newAttachments = selectedUris.toPickedProofAttachments(
            context = context,
            startIndex = proofAttachments.size
        )
        if (newAttachments.isNotEmpty()) {
            onProofAttachmentsChanged(proofAttachments + newAttachments)
        }
        attachmentNotice = when {
            uris.isEmpty() -> null
            uris.size > remaining -> "已添加 $remaining 个凭证，已达到上限。"
            else -> "已添加 ${newAttachments.size} 个本地凭证。"
        }
    }

    Text(
        text = "图片 / 视频凭证",
        color = BNBUColors.Ink,
        fontSize = 18.sp,
        fontWeight = FontWeight.Black
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = ProofUploadRule.summaryText,
        color = BNBUColors.Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 19.sp
    )
    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton(
            title = "拍照",
            icon = Icons.Filled.CameraAlt,
            filled = totalProofCount < ProofUploadRule.maxAttachmentCount,
            modifier = Modifier.weight(1f),
            onClick = {
                if (totalProofCount >= ProofUploadRule.maxAttachmentCount) {
                    attachmentNotice = "已达到 ${ProofUploadRule.maxAttachmentCount} 个凭证上限。"
                    return@ActionButton
                }
                val photoFile = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                    "proof_${System.currentTimeMillis()}.jpg"
                )
                photoFile.parentFile?.mkdirs()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    photoFile
                )
                cameraTempUri = uri
                cameraLauncher.launch(uri)
            }
        )

        ActionButton(
            title = "选择照片/视频",
            icon = Icons.Filled.UploadFile,
            filled = totalProofCount < ProofUploadRule.maxAttachmentCount,
            modifier = Modifier.weight(1f),
            onClick = {
                if (totalProofCount < ProofUploadRule.maxAttachmentCount) {
                    mediaPicker.launch(arrayOf("image/*", "video/*"))
                } else {
                    attachmentNotice = "已达到 ${ProofUploadRule.maxAttachmentCount} 个凭证上限。"
                }
            }
        )
    }

    Spacer(Modifier.height(10.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton(
            title = "清空",
            icon = Icons.Filled.Delete,
            filled = false,
            modifier = Modifier.weight(1f),
            onClick = {
                onProofAttachmentsChanged(emptyList())
                attachmentNotice = "凭证已清空。"
            }
        )
    }

    attachmentNotice?.let { notice ->
        Spacer(Modifier.height(10.dp))
        Text(
            text = notice,
            color = BNBUColors.Blue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 17.sp
        )
    }

    Spacer(Modifier.height(12.dp))

    if (proofAttachments.isEmpty()) {
        Text(
            text = "尚未添加凭证。请选择能证明本次运动过程的图片或视频。",
            color = BNBUColors.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 19.sp
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            proofAttachments.forEach { attachment ->
                ProofAttachmentRow(
                    attachment = attachment,
                    onRemove = {
                        onProofAttachmentsChanged(proofAttachments.filterNot { it.id == attachment.id })
                    }
                )
            }
        }
    }
}

@Composable
private fun ProofAttachmentRow(
    attachment: ProofAttachment,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BNBUColors.BlueSoft)
            .border(1.dp, BNBUColors.Line, RectangleShape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (attachment.type == ProofMediaType.Video) Icons.Filled.Videocam else Icons.Filled.Photo,
            contentDescription = null,
            tint = BNBUColors.Ink,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = attachment.fileName,
                color = BNBUColors.Ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = buildList {
                    add(attachment.type.label)
                    add(attachment.displaySize)
                    attachment.displayDuration?.let { add(it) }
                    attachment.validationMessage?.let { add(it) }
                }.joinToString(" · "),
                color = if (attachment.isValidForUpload) BNBUColors.Muted else BNBUColors.Blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        SquareIconButton(icon = Icons.Filled.Delete, enabled = true, onClick = onRemove)
    }
}

@Composable
private fun ConfirmSubmitPanel(
    supplementingRecord: CheckInRecord?,
    selectedTask: CourseTask?,
    hours: Double,
    proofCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val title = supplementingRecord?.taskTitle ?: selectedTask?.title ?: "当前任务"

    SwissPanel {
        Text(
            text = "确认提交",
            color = BNBUColors.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$title · ${hours.hourText()} · 新增 $proofCount 个凭证。提交后会先进入待审核，并在后续 API 接入后进入同步队列。",
            color = BNBUColors.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                title = "取消",
                icon = Icons.Filled.Clear,
                filled = false,
                modifier = Modifier.weight(1f),
                onClick = onCancel
            )
            ActionButton(
                title = "确认提交",
                icon = Icons.Filled.CheckCircle,
                filled = true,
                modifier = Modifier.weight(1f),
                onClick = onConfirm
            )
        }
    }
}

@Composable
private fun TaskActionCard(
    task: CourseTask,
    course: Course?,
    onStart: () -> Unit
) {
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = task.creditType.checkInIcon,
                    contentDescription = null,
                    tint = BNBUColors.Blue,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
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

            ActionButton(
                title = if (task.status == TaskStatus.Active) "去提交" else "任务已关闭",
                icon = if (task.status == TaskStatus.Active) Icons.Filled.UploadFile else Icons.Filled.RadioButtonUnchecked,
                filled = task.status == TaskStatus.Active,
                onClick = onStart
            )
        }
    }
}

@Composable
private fun TaskPickerRow(
    task: CourseTask,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) BNBUColors.BlueSoft else BNBUColors.Surface)
            .border(1.dp, BNBUColors.Line, RectangleShape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else task.creditType.checkInIcon,
            contentDescription = null,
            tint = if (selected) BNBUColors.Blue else BNBUColors.Ink,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = task.title,
                color = BNBUColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "${task.creditType.label} · 最多 ${task.hours.hourText()}",
                color = BNBUColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
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

@Composable
private fun SquareIconButton(
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(if (enabled) BNBUColors.Ink else BNBUColors.BlueSoft)
            .border(1.5.dp, BNBUColors.Line, RectangleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) BNBUColors.Surface else BNBUColors.Muted,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun submitValidationMessage(
    selectedTask: CourseTask?,
    supplementingRecord: CheckInRecord?,
    hours: Double,
    maxHours: Double,
    newProofs: List<ProofAttachment>,
    totalProofCount: Int
): String? {
    if (selectedTask == null && supplementingRecord == null) return "请选择一个可提交的任务。"
    if (hours < 0.5) return "单次打卡至少 0.5h。"
    if (hours > maxHours) return "本次学时不能超过 ${maxHours.hourText()}。"
    if (newProofs.isEmpty()) return "至少需要添加 1 个图片或视频凭证。"
    if (totalProofCount > ProofUploadRule.maxAttachmentCount) {
        return "同一条记录最多保留 ${ProofUploadRule.maxAttachmentCount} 个凭证。"
    }
    val invalidProof = newProofs.firstOrNull { !it.isValidForUpload }
    if (invalidProof != null) return "${invalidProof.fileName}：${invalidProof.validationMessage}"
    return null
}

private fun List<Uri>.toPickedProofAttachments(
    context: Context,
    startIndex: Int
): List<ProofAttachment> {
    return mapIndexed { offset, uri ->
        context.takePersistableReadPermissionIfPossible(uri)
        uri.toProofAttachment(context = context, index = startIndex + offset)
    }
}

private fun Uri.toProofAttachment(context: Context, index: Int): ProofAttachment {
    val fileName = context.displayNameFor(this, index)
    val mediaType = context.mediaTypeFor(this, fileName)
    return ProofAttachment(
        id = UUID.randomUUID().toString(),
        type = mediaType,
        fileName = fileName,
        byteCount = context.byteCountFor(this),
        durationSeconds = if (mediaType == ProofMediaType.Video) context.videoDurationSecondsFor(this) else null,
        source = toString()
    )
}

private fun Context.displayNameFor(uri: Uri, index: Int): String {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "android-proof-${index + 1}"
}

private fun Context.byteCountFor(uri: Uri): Long? {
    return querySize(uri)
}

private fun Context.querySize(uri: Uri): Long? {
    return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst() && sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
            cursor.getLong(sizeIndex)
        } else {
            null
        }
    }
}

private fun Context.mediaTypeFor(uri: Uri, fileName: String): ProofMediaType {
    val mimeType = contentResolver.getType(uri).orEmpty().lowercase()
    val lowerName = fileName.lowercase()
    return if (
        mimeType.startsWith("video/") ||
        lowerName.endsWith(".mp4") ||
        lowerName.endsWith(".mov") ||
        lowerName.endsWith(".m4v") ||
        lowerName.endsWith(".webm")
    ) {
        ProofMediaType.Video
    } else {
        ProofMediaType.Image
    }
}

private fun Context.videoDurationSecondsFor(uri: Uri): Double? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(this, uri)
        retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toDoubleOrNull()
            ?.div(1000.0)
    } catch (_: RuntimeException) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

private fun Context.takePersistableReadPermissionIfPossible(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private val CreditType.checkInIcon: ImageVector
    get() = when (this) {
        CreditType.CourseRelated -> Icons.Filled.AssignmentTurnedIn
        CreditType.General -> Icons.AutoMirrored.Filled.DirectionsRun
        CreditType.OrganizationOffset -> Icons.Filled.CheckCircle
    }

/**
 * Create a ProofAttachment from a camera-captured photo URI (AND-003).
 */
private fun Uri.toProofAttachmentFromCamera(context: Context, index: Int): ProofAttachment? {
    val fileName = "camera_${System.currentTimeMillis()}.jpg"
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)
    val byteCount = if (file.exists()) file.length() else null
    return ProofAttachment(
        id = UUID.randomUUID().toString(),
        type = ProofMediaType.Image,
        fileName = fileName,
        byteCount = byteCount,
        durationSeconds = null,
        source = toString()
    )
}
