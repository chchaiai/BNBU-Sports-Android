package edu.bnbu.student.mvp.feature.checkin

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import edu.bnbu.student.mvp.core.designsystem.ActionButton
import edu.bnbu.student.mvp.core.designsystem.BNBUMotion
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.SegmentedControl
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.StatusMessagePanel
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.ValidationPanel
import edu.bnbu.student.mvp.core.designsystem.bnbuClickable
import edu.bnbu.student.mvp.core.designsystem.pressScale
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
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class CheckInSegment(val label: String) {
    Submit("提交"),
    Records("记录")
}

private data class SportTypeOption(
    val value: String,
    val label: String,
    val icon: ImageVector
)

private const val OtherSportType = "other"
private const val MaxCheckInNoteLength = 2_000

private val SportTypeOptions = listOf(
    SportTypeOption("running", "跑步", Icons.AutoMirrored.Filled.DirectionsRun),
    SportTypeOption("basketball", "篮球", Icons.Filled.SportsBasketball),
    SportTypeOption("football", "足球", Icons.Filled.SportsSoccer),
    SportTypeOption("badminton", "羽毛球", Icons.Filled.SportsTennis),
    SportTypeOption("swimming", "游泳", Icons.Filled.Pool),
    SportTypeOption("fitness", "健身", Icons.Filled.FitnessCenter),
    SportTypeOption("cycling", "骑行", Icons.AutoMirrored.Filled.DirectionsBike),
    SportTypeOption(OtherSportType, "其他", Icons.Filled.MoreHoriz)
)

@Composable
fun CheckInScreen(appState: StudentAppState) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
    val submitListState = rememberLazyListState()
    val recordsListState = rememberLazyListState()
    val initialDraft = appState.draft?.takeIf { it.taskId == appState.selfCheckInTask.id }
    var selectedSegment by rememberSaveable { mutableStateOf(CheckInSegment.Submit) }
    var selectedRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    var hours by rememberSaveable { mutableDoubleStateOf(initialDraft?.hours ?: 1.0) }
    var note by rememberSaveable {
        mutableStateOf(initialDraft?.note.orEmpty().take(MaxCheckInNoteLength))
    }
    var selectedSportType by rememberSaveable { mutableStateOf(initialDraft?.sportType) }
    var customSportType by rememberSaveable { mutableStateOf(initialDraft?.customSportType.orEmpty()) }
    var proofAttachments by remember {
        mutableStateOf(initialDraft?.proofAttachments.orEmpty())
    }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var submitRequestVersion by remember { mutableIntStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }
    val submitInteractionSource = remember { MutableInteractionSource() }

    val selectedTask = appState.selfCheckInTask
    val maxHours = appState.hourLimitFor(selectedTask)
    val today = LocalDate.now()
    val hasSubmittedToday = remember(appState.workspace.records, today) {
        appState.hasSubmittedCheckInToday(today)
    }
    val validationMessage = remember(
        selectedTask,
        hasSubmittedToday,
        hours,
        maxHours,
        proofAttachments,
        selectedSportType,
        customSportType
    ) {
        submitValidationMessage(
            selectedTask = selectedTask,
            hasSubmittedToday = hasSubmittedToday,
            hours = hours,
            maxHours = maxHours,
            existingProofs = emptyList(),
            newProofs = proofAttachments,
            totalProofCount = proofAttachments.size,
            customSportTypeMissing = selectedSportType == OtherSportType && customSportType.isBlank()
        )
    }

    val latestHours by rememberUpdatedState(hours)
    val latestNote by rememberUpdatedState(note)
    val latestSportType by rememberUpdatedState(selectedSportType)
    val latestCustomSportType by rememberUpdatedState(customSportType)
    val latestProofs by rememberUpdatedState(proofAttachments)
    DisposableEffect(appState) {
        onDispose {
            val hasUserInput = latestHours != 1.0 || latestNote.isNotBlank() ||
                latestSportType != null || latestCustomSportType.isNotBlank() || latestProofs.isNotEmpty()
            if (appState.isAuthenticated && hasUserInput) {
                appState.saveDraft(
                    taskId = selectedTask.id,
                    hours = latestHours,
                    note = latestNote,
                    sportType = latestSportType,
                    customSportType = latestCustomSportType,
                    proofAttachments = latestProofs
                )
            } else if (!appState.isAuthenticated) {
                latestProofs.forEach {
                    it.deleteOwnedCameraFile(context, "proof_")
                    it.releasePersistableReadPermissionIfPossible(context)
                }
            }
        }
    }

    val selectedRecord = remember(selectedRecordId, appState.workspace.records) {
        selectedRecordId?.let { id ->
            appState.workspace.records.firstOrNull { it.id == id }
        }
    }
    AnimatedContent(
        targetState = selectedRecord,
        modifier = Modifier.fillMaxSize(),
        contentKey = { record -> record?.id ?: "record-list" },
        transitionSpec = {
            val animationSpec = tween<IntOffset>(
                durationMillis = BNBUMotion.Standard,
                easing = FastOutSlowInEasing
            )
            if (targetState != null) {
                (slideInHorizontally(animationSpec) { width -> width / 8 } +
                    fadeIn(tween(BNBUMotion.Standard))) togetherWith
                    (slideOutHorizontally(animationSpec) { width -> -width / 12 } +
                        fadeOut(tween(BNBUMotion.Quick)))
            } else {
                (slideInHorizontally(animationSpec) { width -> -width / 8 } +
                    fadeIn(tween(BNBUMotion.Standard))) togetherWith
                    (slideOutHorizontally(animationSpec) { width -> width / 12 } +
                        fadeOut(tween(BNBUMotion.Quick)))
            }
        },
        label = "checkInRecordDetail"
    ) { animatedRecord ->
        if (animatedRecord != null) {
            CheckInRecordDetail(
                appState = appState,
                record = animatedRecord,
                imageLoader = imageLoader,
                onBack = { selectedRecordId = null }
            )
        } else {
            val bottomContentPadding by animateDpAsState(
                targetValue = if (selectedSegment == CheckInSegment.Submit) 104.dp else 0.dp,
                animationSpec = tween(
                    durationMillis = BNBUMotion.Standard,
                    easing = FastOutSlowInEasing
                ),
                label = "checkInBottomPadding"
            )
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = selectedSegment,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (fadeIn(tween(BNBUMotion.Standard)) +
                            slideInHorizontally(tween(BNBUMotion.Standard)) {
                                direction * (it / 14)
                            }) togetherWith
                            (fadeOut(tween(BNBUMotion.Quick)) +
                                slideOutHorizontally(tween(BNBUMotion.Standard)) {
                                    -direction * (it / 16)
                                })
                    },
                    label = "checkInSegment"
                ) { animatedSegment ->
                    LazyColumn(
                        state = if (animatedSegment == CheckInSegment.Submit) {
                            submitListState
                        } else {
                            recordsListState
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            bottom = if (animatedSegment == CheckInSegment.Submit) {
                                bottomContentPadding
                            } else {
                                0.dp
                            }
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
        item {
            SegmentedControl(
                values = CheckInSegment.entries,
                selected = animatedSegment,
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

        when (animatedSegment) {
            CheckInSegment.Submit -> {
                item {
                    SubmitShell(
                        appState = appState,
                        hours = hours,
                        note = note,
                        selectedSportType = selectedSportType,
                        customSportType = customSportType,
                        proofAttachments = proofAttachments,
                        onHoursChanged = { if (!isSubmitting) hours = it },
                        onNoteChanged = {
                            if (!isSubmitting) note = it.take(MaxCheckInNoteLength)
                        },
                        onSportTypeSelected = {
                            if (!isSubmitting) {
                                selectedSportType = it
                                if (it != OtherSportType) customSportType = ""
                            }
                        },
                        onCustomSportTypeChanged = {
                            if (!isSubmitting) customSportType = it
                        },
                        onProofAttachmentsChanged = {
                            if (!isSubmitting) proofAttachments = it
                        },
                        validationMessage = validationMessage,
                        submitRequestVersion = submitRequestVersion,
                        onSubmitRequestHandled = { submitRequestVersion = 0 },
                        isSubmitting = isSubmitting,
                        onSubmittingChanged = { isSubmitting = it },
                        onSubmitComplete = { message, submittedProofs ->
                            statusMessage = message
                            selectedSegment = CheckInSegment.Records
                            hours = 1.0
                            note = ""
                            selectedSportType = null
                            customSportType = ""
                            submittedProofs.forEach {
                                it.deleteOwnedCameraFile(context, "proof_")
                                it.releasePersistableReadPermissionIfPossible(context)
                            }
                            proofAttachments = emptyList()
                        }
                    )
                }
            }

            CheckInSegment.Records -> {
                item {
                    RecordListIntro()
                }

                val records = appState.workspace.records.filter {
                    it.creditType != CreditType.OrganizationOffset
                }

                if (records.isEmpty()) {
                    item {
                        EmptyPlaceholder(title = "暂无记录", message = "当前筛选条件下没有打卡记录。")
                    }
                } else {
                    items(records, key = { it.id }) { record ->
                        RecordCard(
                            record = record,
                            imageLoader = imageLoader,
                            onOpenDetail = { selectedRecordId = record.id }
                        )
                    }
                }
            }
        }
                    }
                }

        AnimatedVisibility(
            visible = selectedSegment == CheckInSegment.Submit,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(
                animationSpec = tween(
                    durationMillis = BNBUMotion.Standard,
                    easing = FastOutSlowInEasing
                ),
                initialOffsetY = { height -> height / 2 }
            ) + fadeIn(tween(BNBUMotion.Standard)),
            exit = slideOutVertically(
                animationSpec = tween(
                    durationMillis = BNBUMotion.Standard,
                    easing = FastOutSlowInEasing
                ),
                targetOffsetY = { height -> height / 2 }
            ) + fadeOut(tween(BNBUMotion.Quick))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (validationMessage == null) {
                            "信息已完整，可以提交"
                        } else {
                            validationMessage
                        },
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2
                    )
                    Button(
                        onClick = { submitRequestVersion += 1 },
                        enabled = validationMessage == null && !isSubmitting,
                        interactionSource = submitInteractionSource,
                        modifier = Modifier.pressScale(
                            interactionSource = submitInteractionSource,
                            enabled = validationMessage == null && !isSubmitting
                        )
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isSubmitting) "提交中" else "提交打卡")
                    }
                }
            }
        }
            }
        }
    }
}

@Composable
private fun RecordListIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(eyebrow = "Records", title = "打卡记录")
    }
}

@Composable
private fun SubmitShell(
    appState: StudentAppState,
    hours: Double,
    note: String,
    selectedSportType: String?,
    customSportType: String,
    proofAttachments: List<ProofAttachment>,
    onHoursChanged: (Double) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSportTypeSelected: (String?) -> Unit,
    onCustomSportTypeChanged: (String) -> Unit,
    onProofAttachmentsChanged: (List<ProofAttachment>) -> Unit,
    validationMessage: String?,
    submitRequestVersion: Int,
    onSubmitRequestHandled: () -> Unit,
    isSubmitting: Boolean,
    onSubmittingChanged: (Boolean) -> Unit,
    onSubmitComplete: (String, List<ProofAttachment>) -> Unit
) {
    val selectedTask = appState.selfCheckInTask
    val maxHours = appState.hourLimitFor(selectedTask)
    val existingProofs = emptyList<ProofAttachment>()
    val totalProofCount = proofAttachments.size
    val resolvedSportType = when (selectedSportType) {
        OtherSportType -> customSportType.trim().takeIf { it.isNotEmpty() }
        else -> selectedSportType
    }
    var showConfirm by remember { mutableStateOf(false) }
    var submitError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(submitRequestVersion) {
        if (submitRequestVersion > 0) {
            if (validationMessage == null && !isSubmitting) {
                showConfirm = true
            }
            onSubmitRequestHandled()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTitle(eyebrow = "Submit", title = "提交打卡")

        run {
            if (appState.draft != null) {
                DraftPanel(
                    appState = appState,
                    onRestore = {
                        val draft = appState.draft
                        if (draft != null) {
                            if (draft.taskId == selectedTask.id) {
                                onHoursChanged(draft.hours)
                                onNoteChanged(draft.note)
                                onSportTypeSelected(draft.sportType)
                                onCustomSportTypeChanged(draft.customSportType.orEmpty())
                                onProofAttachmentsChanged(draft.proofAttachments)
                            }
                        }
                    }
                )
            }

            SubmitDetailPanel(
                selectedTask = selectedTask,
                appState = appState,
                hours = hours,
                maxHours = maxHours,
                note = note,
                selectedSportType = selectedSportType,
                customSportType = customSportType,
                proofAttachments = proofAttachments,
                existingProofs = existingProofs,
                totalProofCount = totalProofCount,
                onHoursChanged = onHoursChanged,
                onNoteChanged = onNoteChanged,
                onSportTypeSelected = onSportTypeSelected,
                onCustomSportTypeChanged = onCustomSportTypeChanged,
                onProofAttachmentsChanged = onProofAttachmentsChanged,
                enabled = !isSubmitting
            )

            submitError?.let {
                ValidationPanel(message = it)
            }

            if (showConfirm) {
                ConfirmSubmitPanel(
                    selectedTask = selectedTask,
                    hours = hours,
                    proofCount = proofAttachments.size,
                    onCancel = { showConfirm = false },
                    onConfirm = {
                        showConfirm = false
                        onSubmittingChanged(true)
                        submitError = null
                        appState.submitCheckIn(
                                task = selectedTask,
                                hours = hours,
                                note = note,
                                sportType = resolvedSportType,
                                proofAttachments = proofAttachments,
                                onResult = { result ->
                                    onSubmittingChanged(false)
                                    result.fold(
                                        onSuccess = {
                                            onSubmitComplete(
                                                "打卡已记录，可在打卡记录中查看。",
                                                proofAttachments
                                            )
                                        },
                                        onFailure = {
                                            submitError = it.message ?: "打卡提交失败，请重试"
                                        }
                                    )
                                }
                            )
                    }
                )
            }

            ActionButton(
                title = "保存草稿",
                icon = Icons.Filled.Save,
                filled = false,
                enabled = !isSubmitting,
                onClick = {
                    appState.saveDraft(
                        taskId = selectedTask.id,
                        hours = hours,
                        note = note,
                        sportType = selectedSportType,
                        customSportType = customSportType,
                        proofAttachments = proofAttachments
                    )
                }
            )
        }
    }
}

@Composable
private fun DraftPanel(
    appState: StudentAppState,
    onRestore: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val draft = appState.draft ?: return
    val task = if (draft.taskId == appState.selfCheckInTask.id) {
        appState.selfCheckInTask
    } else {
        appState.workspace.tasks.firstOrNull { it.id == draft.taskId }
    }

    SwissPanel {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "本地草稿",
                    color = cs.onSurface,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${task?.title ?: "任务已失效"} · ${draft.hours.hourText()} · ${draft.proofAttachments.size} 个凭证 · ${draft.updatedAt}",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
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
private fun TaskSelectionPanel(
    activeTasks: List<CourseTask>,
    selectedTask: CourseTask?,
    appState: StudentAppState,
    onTaskSelected: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Text(
            text = "选择任务",
            color = cs.onSurface,
            style = MaterialTheme.typography.titleMedium
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
                color = cs.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SubmitDetailPanel(
    selectedTask: CourseTask?,
    appState: StudentAppState,
    hours: Double,
    maxHours: Double,
    note: String,
    selectedSportType: String?,
    customSportType: String,
    proofAttachments: List<ProofAttachment>,
    existingProofs: List<ProofAttachment>,
    totalProofCount: Int,
    onHoursChanged: (Double) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSportTypeSelected: (String?) -> Unit,
    onCustomSportTypeChanged: (String) -> Unit,
    onProofAttachmentsChanged: (List<ProofAttachment>) -> Unit,
    enabled: Boolean
) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Text(
            text = "本次学时",
            color = cs.onSurface,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))
        HoursControl(
            value = hours,
            maxHours = maxHours,
            enabled = enabled,
            onChange = { value ->
                val normalized = if (selectedTask != null) {
                    appState.normalizedHours(value, selectedTask)
                } else {
                    if (value >= 2.0 && appState.hourRule.dailyLimit >= 2.0) 2.0 else 1.0
                }
                onHoursChanged(normalized)
            }
        )

        Spacer(Modifier.height(18.dp))
        SportTypeSelector(
            selectedValue = selectedSportType,
            customValue = customSportType,
            enabled = enabled,
            onSelected = onSportTypeSelected,
            onCustomValueChanged = onCustomSportTypeChanged
        )

        Spacer(Modifier.height(18.dp))
        Text(
            text = "补充说明",
            color = cs.onSurface,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(10.dp))
        NoteEditor(
            value = note,
            placeholder = "例如：今天在南区操场完成 5km 慢跑，上传运动轨迹截图和现场照片。",
            enabled = enabled,
            onValueChange = onNoteChanged
        )

        Spacer(Modifier.height(18.dp))
        ProofAttachmentPanel(
            proofAttachments = proofAttachments,
            existingProofs = existingProofs,
            totalProofCount = totalProofCount,
            enabled = enabled,
            onProofAttachmentsChanged = onProofAttachmentsChanged
        )
    }
}

@Composable
private fun SportTypeSelector(
    selectedValue: String?,
    customValue: String,
    enabled: Boolean,
    onSelected: (String?) -> Unit,
    onCustomValueChanged: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var showAll by rememberSaveable {
        mutableStateOf(selectedValue in SportTypeOptions.drop(4).map { it.value })
    }
    Text(
        text = "运动项目（可选）",
        color = cs.onSurface,
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = "请选择本次运动；再次点击已选项目可取消。",
        color = cs.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(10.dp))

    val keepExpandedForSelection = selectedValue in SportTypeOptions.drop(4).map { it.value }
    SportTypeOptionRows(
        options = SportTypeOptions.take(4),
        selectedValue = selectedValue,
        enabled = enabled,
        onSelected = onSelected
    )

    AnimatedVisibility(
        visible = showAll || keepExpandedForSelection,
        enter = expandVertically(
            animationSpec = tween(
                durationMillis = BNBUMotion.Standard,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(tween(BNBUMotion.Standard)),
        exit = shrinkVertically(
            animationSpec = tween(
                durationMillis = BNBUMotion.Standard,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(tween(BNBUMotion.Quick))
    ) {
        Column(modifier = Modifier.padding(top = 10.dp)) {
            SportTypeOptionRows(
                options = SportTypeOptions.drop(4),
                selectedValue = selectedValue,
                enabled = enabled,
                onSelected = onSelected
            )
        }
    }

    AnimatedVisibility(visible = !keepExpandedForSelection) {
        TextButton(
            onClick = { showAll = !showAll },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (showAll) "收起运动项目" else "查看更多运动项目")
        }
    }

    AnimatedVisibility(
        visible = selectedValue == OtherSportType,
        enter = expandVertically(
            animationSpec = tween(
                durationMillis = BNBUMotion.Standard,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(tween(BNBUMotion.Standard)),
        exit = shrinkVertically(
            animationSpec = tween(
                durationMillis = BNBUMotion.Standard,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(tween(BNBUMotion.Quick))
    ) {
        Column {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(cs.surfaceVariant, MaterialTheme.shapes.small)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (customValue.isBlank()) {
                    Text(
                        text = "填写其他运动项目",
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                BasicTextField(
                    value = customValue,
                    onValueChange = { onCustomValueChanged(it.take(32)) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(
                        color = cs.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(cs.primary)
                )
            }
        }
    }
}

@Composable
private fun SportTypeOptionRows(
    options: List<SportTypeOption>,
    selectedValue: String?,
    enabled: Boolean,
    onSelected: (String?) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowOptions.forEach { option ->
                    val selected = selectedValue == option.value
                    val backgroundColor by animateColorAsState(
                        targetValue = if (selected) cs.primaryContainer else cs.surfaceVariant,
                        animationSpec = BNBUMotion.colorSpec,
                        label = "sportTypeBackground"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (selected) cs.primary else cs.onSurfaceVariant,
                        animationSpec = BNBUMotion.colorSpec,
                        label = "sportTypeIcon"
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .background(
                                color = backgroundColor,
                                shape = MaterialTheme.shapes.small
                            )
                            .bnbuClickable(
                                enabled = enabled,
                                onClickLabel = "选择${option.label}"
                            ) {
                                onSelected(if (selected) null else option.value)
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = option.label,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = option.label,
                            color = cs.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HoursControl(
    value: Double,
    maxHours: Double,
    enabled: Boolean,
    onChange: (Double) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceVariant, MaterialTheme.shapes.small)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SquareIconButton(
            icon = Icons.Filled.RemoveCircle,
            contentDescription = "减少学时",
            enabled = enabled && value > 1.0,
            onClick = { onChange(value - 1.0) }
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInVertically(
                            animationSpec = tween(BNBUMotion.Standard),
                            initialOffsetY = { height -> height / 2 }
                        ) + fadeIn(tween(BNBUMotion.Standard))) togetherWith
                            (slideOutVertically(
                                animationSpec = tween(BNBUMotion.Quick),
                                targetOffsetY = { height -> -height / 2 }
                            ) + fadeOut(tween(BNBUMotion.Quick)))
                    } else {
                        (slideInVertically(
                            animationSpec = tween(BNBUMotion.Standard),
                            initialOffsetY = { height -> -height / 2 }
                        ) + fadeIn(tween(BNBUMotion.Standard))) togetherWith
                            (slideOutVertically(
                                animationSpec = tween(BNBUMotion.Quick),
                                targetOffsetY = { height -> height / 2 }
                            ) + fadeOut(tween(BNBUMotion.Quick)))
                    }
                },
                label = "checkInHours"
            ) { animatedValue ->
                Text(
                    text = animatedValue.hourText(),
                    color = cs.onSurface,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = "单次最多 ${maxHours.hourText()}",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
        SquareIconButton(
            icon = Icons.Filled.AddCircle,
            contentDescription = "增加学时",
            enabled = enabled && value < maxHours,
            onClick = { onChange(value + 1.0) }
        )
    }
}

@Composable
private fun NoteEditor(
    value: String,
    placeholder: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
            .background(cs.surfaceVariant, MaterialTheme.shapes.small)
            .padding(12.dp)
    ) {
        if (value.isBlank()) {
            Text(
                text = placeholder,
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier.fillMaxSize(),
            textStyle = TextStyle(
                color = cs.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                lineHeight = 20.sp
            ),
            cursorBrush = SolidColor(cs.primary)
        )
    }
}

@Composable
private fun ProofAttachmentPanel(
    proofAttachments: List<ProofAttachment>,
    existingProofs: List<ProofAttachment>,
    totalProofCount: Int,
    enabled: Boolean,
    onProofAttachmentsChanged: (List<ProofAttachment>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var attachmentNotice by remember { mutableStateOf<String?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var cameraTempFile by remember { mutableStateOf<File?>(null) }
    val latestCameraTempFile by rememberUpdatedState(cameraTempFile)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestProofAttachments by rememberUpdatedState(proofAttachments)
    val latestExistingProofs by rememberUpdatedState(existingProofs)
    val currentProofs = existingProofs + proofAttachments
    val imageSlots = (ProofUploadRule.maxImageCount - currentProofs.count { it.type == ProofMediaType.Image })
        .coerceAtLeast(0)
    val videoSlots = (ProofUploadRule.maxVideoCount - currentProofs.count { it.type == ProofMediaType.Video })
        .coerceAtLeast(0)
    val hasAvailableProofSlot = imageSlots > 0 || videoSlots > 0

    DisposableEffect(Unit) {
        onDispose { latestCameraTempFile?.delete() }
    }

    // Camera launcher (AND-003)
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraTempUri
        val file = cameraTempFile
        if (!latestEnabled) {
            file?.delete()
        } else if (success && uri != null && file != null) {
            val attachment = file.toProofAttachmentFromCamera(uri)
            if (
                attachment != null &&
                attachment.isValidForUpload &&
                ProofUploadRule.limitMessage(currentProofs + attachment) == null
            ) {
                onProofAttachmentsChanged(proofAttachments + attachment)
                attachmentNotice = "已拍摄 1 张现场凭证。"
            } else {
                file?.delete()
                attachmentNotice = "拍摄失败或已达到 ${ProofUploadRule.maxImageCount} 张照片上限。"
            }
        } else {
            file?.delete()
        }
        cameraTempUri = null
        cameraTempFile = null
    }

    // Document picker launcher (existing gallery selector)
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!enabled) {
            attachmentNotice = "正在提交，暂时不能修改凭证。"
            return@rememberLauncherForActivityResult
        }
        if (!hasAvailableProofSlot) {
            attachmentNotice = "已达到上传上限：最多 ${ProofUploadRule.maxImageCount} 张照片和 ${ProofUploadRule.maxVideoCount} 个视频。"
            return@rememberLauncherForActivityResult
        }

        if (uris.isEmpty()) {
            attachmentNotice = null
            return@rememberLauncherForActivityResult
        }

        attachmentNotice = "正在读取所选凭证…"
        scope.launch {
            val pickedAttachments = try {
                withContext(Dispatchers.IO) {
                    uris.toPickedProofAttachments(
                        context = context,
                        startIndex = proofAttachments.size
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                attachmentNotice = "无法读取所选文件，请重新选择。"
                return@launch
            }
            val proofsAtCompletion = latestExistingProofs + latestProofAttachments
            if (!latestEnabled) {
                return@launch
            }
            val existingSources = proofsAtCompletion.mapTo(mutableSetOf()) { it.source }
            val allowedAttachments = pickedAttachments
                .distinctBy { it.source }
                .filterNot { it.source in existingSources }
                .takeAllowedProofAttachments(proofsAtCompletion)
            val newAttachments = allowedAttachments.filter { attachment ->
                val uri = runCatching { Uri.parse(attachment.source) }.getOrNull()
                uri != null && context.takePersistableReadPermissionIfPossible(uri)
            }
            if (newAttachments.isNotEmpty()) {
                onProofAttachmentsChanged(latestProofAttachments + newAttachments)
            }
            attachmentNotice = when {
                newAttachments.isEmpty() -> "未添加文件：请避免重复选择，并使用支持长期授权的相册文件。"
                newAttachments.size < pickedAttachments.size ->
                    "已添加 ${newAttachments.size} 个凭证；重复、超限或无法长期授权的文件已跳过。"
                else -> "已添加 ${newAttachments.size} 个本地凭证。"
            }
        }
    }

    Text(
        text = "图片 / 视频凭证",
        color = cs.onSurface,
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = ProofUploadRule.summaryText,
        color = cs.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton(
            title = "拍照",
            icon = Icons.Filled.CameraAlt,
            filled = imageSlots > 0,
            modifier = Modifier.weight(1f),
            enabled = enabled && imageSlots > 0,
            onClick = {
                if (!enabled) return@ActionButton
                if (imageSlots <= 0) {
                    attachmentNotice = "已达到 ${ProofUploadRule.maxImageCount} 张照片上限。"
                    return@ActionButton
                }
                var photoFile: File? = null
                try {
                    val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                        ?: context.cacheDir
                    photoFile = File(picturesDir, "proof_${System.currentTimeMillis()}.jpg")
                    photoFile.parentFile?.mkdirs()
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )
                    cameraTempUri = uri
                    cameraTempFile = photoFile
                    cameraLauncher.launch(uri)
                } catch (_: Exception) {
                    photoFile?.delete()
                    cameraTempUri = null
                    cameraTempFile = null
                    attachmentNotice = "Camera is unavailable on this emulator. Please use photo/video picker."
                }
            }
        )

        ActionButton(
            title = "选择照片/视频",
            icon = Icons.Filled.UploadFile,
            filled = hasAvailableProofSlot,
            modifier = Modifier.weight(1f),
            enabled = enabled && hasAvailableProofSlot,
            onClick = {
                if (hasAvailableProofSlot) {
                    mediaPicker.launch(arrayOf("image/*", "video/*"))
                } else {
                    attachmentNotice = "已达到上传上限：最多 ${ProofUploadRule.maxImageCount} 张照片和 ${ProofUploadRule.maxVideoCount} 个视频。"
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
            enabled = enabled && proofAttachments.isNotEmpty(),
            onClick = {
                proofAttachments.forEach {
                    it.deleteOwnedCameraFile(context, "proof_")
                    it.releasePersistableReadPermissionIfPossible(context)
                }
                onProofAttachmentsChanged(emptyList())
                attachmentNotice = "凭证已清空。"
            }
        )
    }

    attachmentNotice?.let { notice ->
        Spacer(Modifier.height(10.dp))
        Text(
            text = notice,
            color = cs.primary,
            style = MaterialTheme.typography.labelMedium
        )
    }

    Spacer(Modifier.height(12.dp))

    if (proofAttachments.isEmpty()) {
        Text(
            text = "尚未添加凭证。请选择能证明本次运动过程的图片或视频。",
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            proofAttachments.forEach { attachment ->
                ProofAttachmentRow(
                    attachment = attachment,
                    enabled = enabled,
                    onRemove = {
                        val remainingAttachments = proofAttachments.filterNot {
                            it.id == attachment.id
                        }
                        attachment.deleteOwnedCameraFile(context, "proof_")
                        if (remainingAttachments.none { it.source == attachment.source }) {
                            attachment.releasePersistableReadPermissionIfPossible(context)
                        }
                        onProofAttachmentsChanged(remainingAttachments)
                    }
                )
            }
        }
    }
}

@Composable
private fun ProofAttachmentRow(
    attachment: ProofAttachment,
    enabled: Boolean,
    onRemove: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.surfaceVariant, MaterialTheme.shapes.small)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (attachment.type == ProofMediaType.Video) Icons.Filled.Videocam else Icons.Filled.Photo,
            contentDescription = null,
            tint = cs.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = attachment.fileName,
                color = cs.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = buildList {
                    add(attachment.type.label)
                    add(attachment.displaySize)
                    attachment.displayDuration?.let { add(it) }
                    attachment.validationMessage?.let { add(it) }
                }.joinToString(" · "),
                color = if (attachment.isValidForUpload) cs.onSurfaceVariant else cs.primary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        SquareIconButton(
            icon = Icons.Filled.Delete,
            contentDescription = "删除凭证",
            enabled = enabled,
            onClick = onRemove
        )
    }
}

@Composable
private fun ConfirmSubmitPanel(
    selectedTask: CourseTask?,
    hours: Double,
    proofCount: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    val title = selectedTask?.title ?: "当前任务"

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("确认提交") },
        text = {
            Text("$title · ${hours.hourText()} · 新增 $proofCount 个凭证。确认后将提交并保存到打卡记录。")
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("确认提交")
            }
        }
    )
}

@Composable
private fun TaskActionCard(
    task: CourseTask,
    course: Course?,
    onStart: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = task.creditType.checkInIcon,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
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
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) cs.primaryContainer else cs.surfaceVariant,
                MaterialTheme.shapes.small
            )
            .bnbuClickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else task.creditType.checkInIcon,
            contentDescription = null,
            tint = if (selected) cs.primary else cs.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = task.title,
                color = cs.onSurface,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${task.creditType.label} · 最多 ${task.hours.hourText()}",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RecordCard(
    record: CheckInRecord,
    imageLoader: ImageLoader,
    onOpenDetail: () -> Unit
) {
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
                    text = "运动项目：${sportType.displaySportType()}",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text(
                text = "打卡照片 / 视频",
                color = cs.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            RecordMediaGrid(
                proofs = record.proofFiles,
                imageLoader = imageLoader,
                onClick = onOpenDetail
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

@Composable
private fun RecordMediaGrid(
    proofs: List<ProofAttachment>,
    imageLoader: ImageLoader,
    onClick: () -> Unit
) {
    when {
        proofs.isEmpty() -> {
            MediaPlaceholder(
                mediaType = ProofMediaType.Image,
                message = "暂无打卡照片或视频",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .bnbuClickable(onClick = onClick)
            )
        }
        proofs.size == 1 -> {
            ProofThumbnail(
                proof = proofs[0],
                imageLoader = imageLoader,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                onClick = onClick
            )
        }
        proofs.size == 2 -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                proofs.forEach { proof ->
                    ProofThumbnail(
                        proof = proof,
                        imageLoader = imageLoader,
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        onClick = onClick
                    )
                }
            }
        }
        else -> {
            Row(
                modifier = Modifier.fillMaxWidth().height(190.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProofThumbnail(
                    proof = proofs[0],
                    imageLoader = imageLoader,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = onClick
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProofThumbnail(
                        proof = proofs[1],
                        imageLoader = imageLoader,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        onClick = onClick
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ProofThumbnail(
                            proof = proofs[2],
                            imageLoader = imageLoader,
                            modifier = Modifier.fillMaxSize(),
                            onClick = onClick
                        )
                        val remaining = proofs.size - 3
                        if (remaining > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.48f))
                                    .bnbuClickable(onClick = onClick),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+$remaining",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
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
private fun ProofThumbnail(
    proof: ProofAttachment,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val sourceAvailable = proof.source.isDisplayableMediaSource()
    val imageRequest = remember(proof.source, proof.type) {
        ImageRequest.Builder(context)
            .data(proof.source)
            .apply {
                if (proof.type == ProofMediaType.Video) {
                    decoderFactory(VideoFrameDecoder.Factory())
                }
            }
            .build()
    }
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(cs.surfaceVariant)
            .bnbuClickable(onClick = onClick)
    ) {
        if (sourceAvailable) {
            SubcomposeAsyncImage(
                model = imageRequest,
                imageLoader = imageLoader,
                contentDescription = "${proof.type.label}：${proof.fileName}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                },
                error = {
                    MediaPlaceholder(
                        mediaType = proof.type,
                        message = "暂时无法加载",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            )
        } else {
            MediaPlaceholder(
                mediaType = proof.type,
                message = proof.fileName.ifBlank { "媒体文件" },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (proof.type == ProofMediaType.Video) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.45f), MaterialTheme.shapes.large)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircle,
                    contentDescription = "视频",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = "视频",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.62f), MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun MediaPlaceholder(
    mediaType: ProofMediaType,
    message: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .background(cs.surfaceVariant)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (mediaType == ProofMediaType.Video) Icons.Filled.Videocam else Icons.Filled.Photo,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2
        )
    }
}

@Composable
private fun CheckInRecordDetail(
    appState: StudentAppState,
    record: CheckInRecord,
    imageLoader: ImageLoader,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var openError by remember { mutableStateOf<String?>(null) }
    var supplementHours by remember(record.id) {
        mutableDoubleStateOf(if (record.hours >= 2.0) 2.0 else 1.0)
    }
    var supplementNote by remember(record.id) { mutableStateOf("") }
    var supplementProofs by remember(record.id) {
        mutableStateOf<List<ProofAttachment>>(emptyList())
    }
    var isSupplementSubmitting by remember(record.id) { mutableStateOf(false) }
    val latestSupplementProofs by rememberUpdatedState(supplementProofs)
    val canSupplement = record.status == ReviewStatus.Supplement ||
        record.status == ReviewStatus.Rejected

    DisposableEffect(record.id) {
        onDispose {
            latestSupplementProofs.forEach {
                it.deleteOwnedCameraFile(context, "proof_")
                it.releasePersistableReadPermissionIfPossible(context)
            }
        }
    }

    BackHandler {
        if (isSupplementSubmitting) {
            openError = "补充材料正在提交，请等待完成后再返回"
        } else {
            onBack()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .bnbuClickable(
                        enabled = !isSupplementSubmitting,
                        onClickLabel = "返回打卡记录",
                        onClick = onBack
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "返回打卡记录",
                    tint = cs.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Text("返回打卡记录", color = cs.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { SectionTitle(eyebrow = "Check-In Detail", title = "打卡记录详情") }
        item {
            SwissPanel {
                Text(record.taskTitle, color = cs.onSurface, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(text = record.creditType.label)
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(text = record.status.label, filled = canSupplement)
                    Spacer(Modifier.width(8.dp))
                    Text(record.hours.hourText(), color = cs.onSurface, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(10.dp))
                Text("提交时间：${record.submittedAt}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                record.sportType?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("运动项目：${it.displaySportType()}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                if (record.note.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("备注：${record.note}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        openError?.let { message ->
            item { ValidationPanel(message = message) }
        }
        item {
            SectionTitle(
                eyebrow = "Media",
                title = "打卡照片 / 视频 (${record.proofFiles.size})"
            )
        }
        if (record.proofFiles.isEmpty()) {
            item {
                EmptyPlaceholder(title = "暂无照片或视频", message = "这条记录没有可展示的媒体文件。")
            }
        } else {
            items(record.proofFiles, key = { it.id }) { proof ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProofThumbnail(
                        proof = proof,
                        imageLoader = imageLoader,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        onClick = {
                            openError = context.openProofInSystemApp(proof)
                        }
                    )
                    Text(
                        text = buildList {
                            add(proof.type.label)
                            add(proof.fileName)
                            proof.displayDuration?.let { add(it) }
                        }.joinToString(" · "),
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
        if (canSupplement) {
            item {
                SectionTitle(eyebrow = "Supplement", title = "补交打卡材料")
            }
            item {
                SwissPanel {
                    record.teacherFeedback.takeIf { it.isNotBlank() }?.let { feedback ->
                        Text(
                            text = "审核反馈：$feedback",
                            color = cs.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    Text(
                        text = "补交学时",
                        color = cs.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    HoursControl(
                        value = supplementHours,
                        maxHours = minOf(record.hours, appState.hourRule.dailyLimit),
                        enabled = !isSupplementSubmitting,
                        onChange = {
                            if (!isSupplementSubmitting) supplementHours = it
                        }
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = "补充说明",
                        color = cs.onSurface,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    NoteEditor(
                        value = supplementNote,
                        placeholder = "请说明本次新增材料以及对审核反馈的补充。",
                        enabled = !isSupplementSubmitting,
                        onValueChange = {
                            if (!isSupplementSubmitting) {
                                supplementNote = it.take(MaxCheckInNoteLength)
                            }
                        }
                    )
                    Spacer(Modifier.height(18.dp))
                    ProofAttachmentPanel(
                        proofAttachments = supplementProofs,
                        existingProofs = record.proofFiles,
                        totalProofCount = record.proofFiles.size + supplementProofs.size,
                        enabled = !isSupplementSubmitting,
                        onProofAttachmentsChanged = {
                            if (!isSupplementSubmitting) supplementProofs = it
                        }
                    )
                    Spacer(Modifier.height(16.dp))
                    ActionButton(
                        title = if (isSupplementSubmitting) "提交中..." else "提交补充材料",
                        icon = Icons.Filled.UploadFile,
                        filled = true,
                        enabled = !isSupplementSubmitting && supplementProofs.isNotEmpty(),
                        onClick = {
                            if (isSupplementSubmitting) return@ActionButton
                            val proofSnapshot = supplementProofs.toList()
                            if (proofSnapshot.isEmpty()) {
                                openError = "请至少添加 1 个新的图片或视频凭证"
                                return@ActionButton
                            }
                            isSupplementSubmitting = true
                            openError = null
                            appState.submitSupplement(
                                record = record,
                                hours = supplementHours,
                                note = supplementNote.trim(),
                                proofAttachments = proofSnapshot,
                                onResult = { result ->
                                    result.fold(
                                        onSuccess = {
                                            proofSnapshot.forEach {
                                                it.deleteOwnedCameraFile(context, "proof_")
                                                it.releasePersistableReadPermissionIfPossible(context)
                                            }
                                            supplementProofs = emptyList()
                                            supplementNote = ""
                                        },
                                        onFailure = {
                                            openError = it.message ?: "补充材料提交失败，请重试"
                                        }
                                    )
                                    isSupplementSubmitting = false
                                }
                            )
                        }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

private fun String.isDisplayableMediaSource(): Boolean {
    return startsWith("https://", ignoreCase = true) ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("content://", ignoreCase = true) ||
        startsWith("file://", ignoreCase = true) ||
        startsWith("/")
}

private fun Context.openProofInSystemApp(proof: ProofAttachment): String? {
    if (!proof.source.isDisplayableMediaSource()) return "该媒体文件没有可用的预览地址。"
    val mimeType = if (proof.type == ProofMediaType.Video) "video/*" else "image/*"
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(proof.source), mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try {
        startActivity(Intent.createChooser(intent, "打开${proof.type.label}"))
        null
    } catch (_: ActivityNotFoundException) {
        "设备上没有可以打开该${proof.type.label}的应用。"
    } catch (_: Exception) {
        "暂时无法打开该${proof.type.label}，请稍后重试。"
    }
}

@Composable
private fun SquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(
                if (enabled) cs.primaryContainer else cs.surfaceVariant,
                MaterialTheme.shapes.small
            )
            .bnbuClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) cs.onPrimaryContainer else cs.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun submitValidationMessage(
    selectedTask: CourseTask?,
    hasSubmittedToday: Boolean,
    hours: Double,
    maxHours: Double,
    existingProofs: List<ProofAttachment>,
    newProofs: List<ProofAttachment>,
    totalProofCount: Int,
    customSportTypeMissing: Boolean
): String? {
    if (selectedTask == null) return "请选择一个可提交的任务。"
    if (hasSubmittedToday) return "今日已打卡，每天只能提交一次。"
    if (hours != 1.0 && hours != 2.0) return "本次打卡只能选择 1h 或 2h。"
    if (hours > maxHours) return "本次学时不能超过 ${maxHours.hourText()}。"
    if (customSportTypeMissing) return "请填写其他运动项目。"
    if (newProofs.isEmpty()) return "至少需要添加 1 个图片或视频凭证。"
    if (totalProofCount > ProofUploadRule.maxAttachmentCount) {
        return "同一条记录最多保留 ${ProofUploadRule.maxAttachmentCount} 个凭证。"
    }
    ProofUploadRule.limitMessage(existingProofs + newProofs)?.let { return it }
    val invalidProof = newProofs.firstOrNull { !it.isValidForUpload }
    if (invalidProof != null) return "${invalidProof.fileName}：${invalidProof.validationMessage}"
    return null
}

private fun String.displaySportType(): String = when (this) {
    "running" -> "跑步"
    "basketball" -> "篮球"
    "football" -> "足球"
    "badminton" -> "羽毛球"
    "swimming" -> "游泳"
    "fitness" -> "健身"
    "cycling" -> "骑行"
    else -> this
}

private fun List<Uri>.toPickedProofAttachments(
    context: Context,
    startIndex: Int
): List<ProofAttachment> {
    return mapIndexed { offset, uri ->
        uri.toProofAttachment(context = context, index = startIndex + offset)
    }
}

private fun List<ProofAttachment>.takeAllowedProofAttachments(
    existingProofs: List<ProofAttachment>
): List<ProofAttachment> {
    var imageCount = existingProofs.count { it.type == ProofMediaType.Image }
    var videoCount = existingProofs.count { it.type == ProofMediaType.Video }
    val accepted = mutableListOf<ProofAttachment>()

    for (attachment in this) {
        when (attachment.type) {
            ProofMediaType.Image -> {
                if (imageCount < ProofUploadRule.maxImageCount) {
                    accepted += attachment
                    imageCount += 1
                }
            }
            ProofMediaType.Video -> {
                if (videoCount < ProofUploadRule.maxVideoCount) {
                    accepted += attachment
                    videoCount += 1
                }
            }
        }
    }

    return accepted
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

private fun Context.takePersistableReadPermissionIfPossible(uri: Uri): Boolean {
    return runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)
}

private fun Context.releasePersistableReadPermissionIfPossible(uri: Uri) {
    runCatching {
        contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

private fun ProofAttachment.releasePersistableReadPermissionIfPossible(context: Context) {
    val uri = runCatching { Uri.parse(source) }.getOrNull() ?: return
    if (uri.scheme == "content") context.releasePersistableReadPermissionIfPossible(uri)
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
private fun File.toProofAttachmentFromCamera(sourceUri: Uri): ProofAttachment? {
    if (!exists() || length() <= 0L) return null
    return ProofAttachment(
        id = UUID.randomUUID().toString(),
        type = ProofMediaType.Image,
        fileName = name,
        byteCount = length(),
        durationSeconds = null,
        source = sourceUri.toString()
    )
}

private fun ProofAttachment.deleteOwnedCameraFile(context: Context, requiredPrefix: String) {
    if (!fileName.startsWith(requiredPrefix) || !fileName.endsWith(".jpg", ignoreCase = true)) return
    val ownedDirectories = listOfNotNull(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
        context.cacheDir
    )
    ownedDirectories.forEach { directory ->
        runCatching {
            val candidate = File(directory, fileName).canonicalFile
            val parent = directory.canonicalFile
            if (candidate.parentFile == parent && candidate.isFile) candidate.delete()
        }
    }
}
