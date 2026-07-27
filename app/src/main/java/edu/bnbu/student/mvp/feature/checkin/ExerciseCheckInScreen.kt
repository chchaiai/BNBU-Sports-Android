package edu.bnbu.student.mvp.feature.checkin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.video.VideoFrameDecoder
import edu.bnbu.student.mvp.BuildConfig
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.SegmentedControl
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.ValidationPanel
import edu.bnbu.student.mvp.core.designsystem.bnbuClickable
import edu.bnbu.student.mvp.core.model.CreditType
import edu.bnbu.student.mvp.core.model.ProofMediaType
import edu.bnbu.student.mvp.core.model.ProofUploadRule
import edu.bnbu.student.mvp.core.state.StudentAppState
import edu.bnbu.student.mvp.feature.checkin.session.ExerciseSessionController
import edu.bnbu.student.mvp.feature.checkin.session.ExerciseSessionDetails
import edu.bnbu.student.mvp.feature.checkin.session.ExerciseSessionState
import edu.bnbu.student.mvp.feature.checkin.session.MaximumExerciseMillis
import edu.bnbu.student.mvp.feature.checkin.session.SessionCaptureTarget
import edu.bnbu.student.mvp.feature.checkin.session.SessionMediaDraft
import edu.bnbu.student.mvp.feature.checkin.session.creditedExerciseHours
import edu.bnbu.student.mvp.feature.checkin.session.effectiveDurationMillis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private enum class ExerciseCheckInTab(val label: String) {
    Exercise("运动"),
    Records("记录")
}

private data class ExerciseSportOption(
    val value: String,
    val label: String
)

private val ExerciseSportOptions = listOf(
    ExerciseSportOption("running", "跑步"),
    ExerciseSportOption("basketball", "篮球"),
    ExerciseSportOption("football", "足球"),
    ExerciseSportOption("badminton", "羽毛球"),
    ExerciseSportOption("swimming", "游泳"),
    ExerciseSportOption("fitness", "健身"),
    ExerciseSportOption("cycling", "骑行"),
    ExerciseSportOption(ExerciseSessionDetails.OtherSportType, "其他")
)

@Composable
internal fun ExerciseCheckInRoot(
    appState: StudentAppState,
    controller: ExerciseSessionController
) {
    val accountId = appState.workspace.student.id
    var selectedTab by rememberSaveable { mutableStateOf(ExerciseCheckInTab.Exercise) }
    var selectedRecordId by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }

    LaunchedEffect(accountId, appState.isAuthenticated) {
        controller.bindAccount(if (appState.isAuthenticated) accountId else "")
    }

    val selectedRecord = selectedRecordId?.let { id ->
        appState.workspace.records.firstOrNull { it.id == id }
    }
    if (selectedRecord != null) {
        CheckInRecordDetail(
            appState = appState,
            record = selectedRecord,
            imageLoader = imageLoader,
            onBack = { selectedRecordId = null }
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SegmentedControl(
            values = ExerciseCheckInTab.entries,
            selected = selectedTab,
            label = { it.label },
            onSelected = { selectedTab = it }
        )
        when (selectedTab) {
            ExerciseCheckInTab.Exercise -> ExerciseFlowContent(controller)
            ExerciseCheckInTab.Records -> {
                val records = appState.workspace.records.filter {
                    it.creditType != CreditType.OrganizationOffset
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item { RecordListIntro() }
                    if (records.isEmpty()) {
                        item {
                            EmptyPlaceholder(
                                title = "暂无记录",
                                message = "当前账号还没有可展示的打卡记录。"
                            )
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
}

@Composable
private fun ExerciseFlowContent(controller: ExerciseSessionController) {
    val message = controller.message
    if (message != null) {
        AlertDialog(
            onDismissRequest = controller::consumeMessage,
            confirmButton = {
                TextButton(onClick = controller::consumeMessage) { Text("我知道了") }
            },
            title = { Text("运动提示") },
            text = { Text(message) }
        )
    }

    if (controller.isRestoring) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在恢复运动会话…")
            }
        }
        return
    }

    when (val state = controller.state) {
        ExerciseSessionState.Idle -> ExercisePreparationContent(controller)
        is ExerciseSessionState.Active -> ExerciseRunningContent(controller, state, paused = false)
        is ExerciseSessionState.Paused -> ExerciseRunningContent(controller, state, paused = true)
        is ExerciseSessionState.Finished -> ExerciseFinishedContent(controller, state)
    }
}

@Composable
private fun ExercisePreparationContent(controller: ExerciseSessionController) {
    var creditTypeName by rememberSaveable { mutableStateOf(CreditType.General.name) }
    var sportType by rememberSaveable { mutableStateOf("running") }
    var customSportName by rememberSaveable { mutableStateOf("") }
    val selectedCreditType = CreditType.entries.firstOrNull { it.name == creditTypeName }
        ?: CreditType.General
    val details = ExerciseSessionDetails(
        creditType = selectedCreditType,
        sportType = sportType,
        customSportName = customSportName.trim().takeIf {
            sportType == ExerciseSessionDetails.OtherSportType
        }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionTitle(eyebrow = "Exercise Session", title = "开始运动")
        }
        item {
            SwissPanel {
                Text("打卡类别", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CategoryButton(
                        label = "课程相关运动",
                        selected = selectedCreditType == CreditType.CourseRelated,
                        modifier = Modifier.weight(1f),
                        onClick = { creditTypeName = CreditType.CourseRelated.name }
                    )
                    CategoryButton(
                        label = "自主其他运动",
                        selected = selectedCreditType == CreditType.General,
                        modifier = Modifier.weight(1f),
                        onClick = { creditTypeName = CreditType.General.name }
                    )
                }
            }
        }
        item {
            SwissPanel {
                Text("运动项目", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                ExerciseSportOptions.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowOptions.forEach { option ->
                            CategoryButton(
                                label = option.label,
                                selected = sportType == option.value,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    sportType = option.value
                                    if (option.value != ExerciseSessionDetails.OtherSportType) {
                                        customSportName = ""
                                    }
                                }
                            )
                        }
                        if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (sportType == ExerciseSessionDetails.OtherSportType) {
                    OutlinedTextField(
                        value = customSportName,
                        onValueChange = { customSportName = it.take(32) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("具体运动名称") },
                        supportingText = { Text("${customSportName.length}/32") },
                        singleLine = true
                    )
                }
            }
        }
        item {
            SwissPanel {
                Text(
                    "运动开始后可现场拍照或录像。媒体只保存为本地草稿，结束运动后再选择最终打卡凭证；本页面不提供相册选择。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { controller.start(details) },
                    enabled = details.isValid,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("开始运动")
                }
            }
        }
    }
}

@Composable
private fun CategoryButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun ExerciseRunningContent(
    controller: ExerciseSessionController,
    state: ExerciseSessionState,
    paused: Boolean
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    var showFinishConfirm by remember { mutableStateOf(false) }
    val duration = state.effectiveDurationMillis(now)
    val limitReached = duration >= MaximumExerciseMillis
    val details = state.detailsOrNull() ?: return
    val draftCount = controller.drafts.size

    LaunchedEffect(state) {
        while (state is ExerciseSessionState.Active) {
            now = System.currentTimeMillis()
            controller.autoFinishIfNeeded()
            delay(1_000L)
        }
    }

    if (showFinishConfirm || limitReached) {
        AlertDialog(
            onDismissRequest = {
                if (!limitReached) showFinishConfirm = false
            },
            confirmButton = {
                TextButton(onClick = {
                    showFinishConfirm = false
                    controller.requestFinish()
                }) { Text("确认结束") }
            },
            dismissButton = if (limitReached) {
                null
            } else {
                {
                    TextButton(onClick = { showFinishConfirm = false }) {
                        Text("取消")
                    }
                }
            },
            title = { Text("你确定要结束本次运动吗？") },
            text = if (limitReached) {
                { Text("已达到 2 小时运动上限，运动时长已停止累计。") }
            } else {
                null
            }
        )
    }

    if (showAbandonConfirm) {
        AlertDialog(
            onDismissRequest = { showAbandonConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showAbandonConfirm = false
                    controller.abandon()
                }) { Text("确认放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonConfirm = false }) { Text("继续保留") }
            },
            title = { Text("放弃本次运动？") },
            text = { Text("放弃后会清理本次运动会话以及所有现场照片和视频草稿。") }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF5B5262),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sportLabel(details),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (paused) "已暂停" else "运动中",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                StatusBadge(text = "未获取位置")
            }

            Spacer(Modifier.height(50.dp))
            Text(
                text = formatDuration(duration),
                color = Color.White,
                fontSize = 58.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "有效运动时长",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(42.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SessionMetric(
                    label = "开始时间",
                    value = formatStartTime(state),
                    modifier = Modifier.weight(1f)
                )
                SessionMetric(
                    label = "预计学时",
                    value = "${creditedExerciseHours(duration)}h",
                    modifier = Modifier.weight(1f)
                )
                SessionMetric(
                    label = "本地草稿",
                    value = "$draftCount",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(30.dp))
            MediaCaptureActions(
                controller = controller,
                allowVideo = true,
                lightContent = true
            )

            if (controller.drafts.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "已现场拍摄：${draftSummary(controller.drafts)}，运动结束前不会上传。",
                    color = Color.White.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(32.dp))
            if (paused) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = controller::resume,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("继续")
                    }
                    Button(
                        onClick = { showFinishConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("结束")
                    }
                }
            } else {
                Button(
                    onClick = controller::pause,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Pause, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("暂停运动")
                }
                TextButton(onClick = { showFinishConfirm = true }) {
                    Text("结束运动", color = Color.White)
                }
            }

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    controller.debugAddActiveDuration(60L * 60L * 1_000L)
                }) {
                    Text("开发测试：增加60分钟", color = Color.White)
                }
            }

            TextButton(onClick = { showAbandonConfirm = true }) {
                Text("放弃并清理本次运动", color = Color.White.copy(alpha = 0.75f))
            }
        }
    }
}

@Composable
private fun SessionMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ExerciseFinishedContent(
    controller: ExerciseSessionController,
    state: ExerciseSessionState.Finished
) {
    var localMessage by remember { mutableStateOf<String?>(null) }
    var showAbandonConfirm by remember { mutableStateOf(false) }

    localMessage?.let { text ->
        AlertDialog(
            onDismissRequest = { localMessage = null },
            confirmButton = { TextButton(onClick = { localMessage = null }) { Text("确定") } },
            title = { Text("凭证检查") },
            text = { Text(text) }
        )
    }
    if (showAbandonConfirm) {
        AlertDialog(
            onDismissRequest = { showAbandonConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showAbandonConfirm = false
                    controller.abandon()
                }) { Text("确认放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showAbandonConfirm = false }) { Text("取消") }
            },
            title = { Text("放弃待提交记录？") },
            text = { Text("本次运动时长和所有本地媒体草稿都会被删除。") }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { SectionTitle(eyebrow = "Finish", title = "运动完成") }
        item {
            SwissPanel {
                Text(
                    formatDuration(state.activeDurationMillis),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text("有效运动时长 · 计入 ${state.creditedHours} 小时")
                Spacer(Modifier.height(6.dp))
                Text(
                    "${state.details.creditType.label} · ${sportLabel(state.details)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            SwissPanel {
                Text("运动说明", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.details.description,
                    onValueChange = controller::updateDescription,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("请填写本次运动内容") },
                    supportingText = { Text("课程相关运动和其他运动均需填写") },
                    minLines = 3
                )
            }
        }
        item {
            SwissPanel {
                Text("现场补拍", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "运动结束后仍可现场拍照；根据当前确认规则，此处不再新增录像，也不提供相册入口。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                MediaCaptureActions(
                    controller = controller,
                    allowVideo = false,
                    lightContent = false
                )
            }
        }
        item {
            SectionTitle(eyebrow = "Local Drafts", title = "选择打卡凭证")
        }
        if (controller.drafts.isEmpty()) {
            item {
                EmptyPlaceholder(
                    title = "尚无现场凭证",
                    message = "请至少现场拍摄 1 张照片，或返回运动阶段拍摄 1 个视频。"
                )
            }
        } else {
            items(controller.drafts, key = { it.id }) { draft ->
                DraftSelectionRow(
                    draft = draft,
                    file = controller.resolveDraftFile(draft),
                    onSelectedChange = { controller.setDraftSelected(draft.id, it) },
                    onDelete = { controller.removeDraft(draft.id) }
                )
            }
        }
        item {
            ValidationPanel(
                message = "最终至少选择 1 张照片或 1 个视频，最多 ${ProofUploadRule.maxImageCount} 张照片加 ${ProofUploadRule.maxVideoCount} 个视频。"
            )
        }
        item {
            Button(
                onClick = {
                    localMessage = controller.validateSelectedProofs().fold(
                        onSuccess = { "凭证选择已完成。真实提交接口将在下一开发步骤接入。" },
                        onFailure = { it.message ?: "凭证选择不完整" }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("确认凭证（本地）")
            }
            TextButton(
                onClick = { showAbandonConfirm = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("放弃本次记录")
            }
        }
    }
}

@Composable
private fun MediaCaptureActions(
    controller: ExerciseSessionController,
    allowVideo: Boolean,
    lightContent: Boolean
) {
    val context = LocalContext.current
    var pendingPhoto by remember { mutableStateOf<SessionCaptureTarget?>(null) }
    var pendingVideo by remember { mutableStateOf<SessionCaptureTarget?>(null) }
    var launchError by remember { mutableStateOf<String?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        pendingPhoto?.let { controller.completeCapture(it, success) }
        pendingPhoto = null
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        pendingVideo?.let { controller.completeCapture(it, success) }
        pendingVideo = null
    }

    launchError?.let { ValidationPanel(message = it) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CaptureButton(
            label = "现场拍照",
            icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null) },
            enabled = !controller.isMediaBusy && controller.drafts.count {
                it.type == ProofMediaType.Image
            } < ProofUploadRule.maxImageCount,
            lightContent = lightContent,
            modifier = Modifier.weight(1f),
            onClick = {
                controller.prepareCapture(ProofMediaType.Image) { result ->
                    result.fold(
                        onSuccess = { target ->
                            runCatching {
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    target.file
                                )
                            }.fold(
                                onSuccess = { uri: Uri ->
                                    pendingPhoto = target
                                    photoLauncher.launch(uri)
                                },
                                onFailure = {
                                    controller.completeCapture(target, false)
                                    launchError = "无法打开系统相机"
                                }
                            )
                        },
                        onFailure = { launchError = it.message }
                    )
                }
            }
        )
        if (allowVideo) {
            CaptureButton(
                label = "现场录像",
                icon = { Icon(Icons.Filled.Videocam, contentDescription = null) },
                enabled = !controller.isMediaBusy && controller.drafts.none {
                    it.type == ProofMediaType.Video
                },
                lightContent = lightContent,
                modifier = Modifier.weight(1f),
                onClick = {
                    controller.prepareCapture(ProofMediaType.Video) { result ->
                        result.fold(
                            onSuccess = { target ->
                                runCatching {
                                    FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        target.file
                                    )
                                }.fold(
                                    onSuccess = { uri: Uri ->
                                        pendingVideo = target
                                        videoLauncher.launch(uri)
                                    },
                                    onFailure = {
                                        controller.completeCapture(target, false)
                                        launchError = "无法打开系统录像"
                                    }
                                )
                            },
                            onFailure = { launchError = it.message }
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun CaptureButton(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    lightContent: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (lightContent) {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            icon()
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f))
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            icon()
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    }
}

@Composable
private fun DraftSelectionRow(
    draft: SessionMediaDraft,
    file: java.io.File?,
    onSelectedChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    SwissPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center
            ) {
                if (file != null && file.isFile) {
                    AsyncImage(
                        model = file,
                        contentDescription = draft.fileName,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (draft.type == ProofMediaType.Video) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "视频草稿",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                } else {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (draft.type == ProofMediaType.Image) "现场照片" else "现场视频",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    formatByteCount(draft.byteCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Checkbox(checked = draft.selected, onCheckedChange = onSelectedChange)
            Icon(
                Icons.Filled.Delete,
                contentDescription = "删除草稿",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .size(36.dp)
                    .padding(7.dp)
                    .bnbuClickable(onClick = onDelete)
            )
        }
    }
}

private fun ExerciseSessionState.detailsOrNull(): ExerciseSessionDetails? {
    return when (this) {
        ExerciseSessionState.Idle -> null
        is ExerciseSessionState.Active -> details
        is ExerciseSessionState.Paused -> details
        is ExerciseSessionState.Finished -> details
    }
}

private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = (durationMillis / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

private fun formatStartTime(state: ExerciseSessionState): String {
    val timestamp = when (state) {
        ExerciseSessionState.Idle -> return "--:--"
        is ExerciseSessionState.Active -> state.startedAtEpochMillis
        is ExerciseSessionState.Paused -> state.startedAtEpochMillis
        is ExerciseSessionState.Finished -> state.startedAtEpochMillis
    }
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
}

private fun sportLabel(details: ExerciseSessionDetails): String {
    return if (details.sportType == ExerciseSessionDetails.OtherSportType) {
        details.customSportName.orEmpty()
    } else {
        ExerciseSportOptions.firstOrNull { it.value == details.sportType }?.label
            ?: details.sportType
    }
}

private fun draftSummary(drafts: List<SessionMediaDraft>): String {
    val images = drafts.count { it.type == ProofMediaType.Image }
    val videos = drafts.count { it.type == ProofMediaType.Video }
    return buildList {
        if (images > 0) add("$images 张照片")
        if (videos > 0) add("$videos 个视频")
    }.joinToString("、")
}

private fun formatByteCount(byteCount: Long): String {
    return if (byteCount >= 1_000_000L) {
        "%.1f MB".format(byteCount / 1_000_000.0)
    } else {
        "${(byteCount / 1_000L).coerceAtLeast(1L)} KB"
    }
}
