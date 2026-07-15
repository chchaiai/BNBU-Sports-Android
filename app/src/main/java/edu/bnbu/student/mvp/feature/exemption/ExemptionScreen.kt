package edu.bnbu.student.mvp.feature.exemption

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.data.ApiStudentRepository
import edu.bnbu.student.mvp.core.network.ApiHttpException
import edu.bnbu.student.mvp.core.designsystem.ActionButton
import edu.bnbu.student.mvp.core.designsystem.BNBUMotion
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.PrimaryActionButton
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.SegmentedControl
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.StatusMessagePanel
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.ValidationPanel
import edu.bnbu.student.mvp.core.designsystem.bnbuClickable
import edu.bnbu.student.mvp.core.model.Exemption
import edu.bnbu.student.mvp.core.model.ExemptionApplication
import edu.bnbu.student.mvp.core.model.ExemptionType
import edu.bnbu.student.mvp.core.model.ProofAttachment
import edu.bnbu.student.mvp.core.model.ProofMediaType
import edu.bnbu.student.mvp.core.state.StudentAppState
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ExemptionTab(val label: String) {
    MyApplications("我的申请"),
    NewApplication("提交申请")
}

private const val MaxExemptionReasonLength = 2_000

@Composable
fun ExemptionScreen(
    appState: StudentAppState,
    repository: ApiStudentRepository,
    initialApplicationId: String? = null,
    onUnauthorized: () -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(ExemptionTab.MyApplications) }
    var exemptions by remember { mutableStateOf<List<Exemption>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var selectedExemptionId by rememberSaveable { mutableStateOf(initialApplicationId) }
    var resubmittingExemption by remember { mutableStateOf<Exemption?>(null) }
    var isFormSubmitting by remember { mutableStateOf(false) }
    val loadJob = remember { mutableStateOf<Job?>(null) }
    val applicationsListState = rememberLazyListState()
    val formListState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val cs = MaterialTheme.colorScheme
    val handleBack = {
        focusManager.clearFocus(force = true)
        if (isFormSubmitting) {
            errorMessage = "申请正在提交，请等待完成后再返回"
        } else if (selectedExemptionId != null) {
            selectedExemptionId = null
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleBack)

    DisposableEffect(Unit) {
        onDispose { loadJob.value?.cancel() }
    }

    fun loadExemptions() {
        if (isLoading) return
        isLoading = true
        errorMessage = null
        val request = appState.launchAuthenticatedRequest {
            try {
                val response = repository.listExemptions()
                exemptions = response.map { r ->
                    Exemption(
                        id = r.id,
                        studentId = r.studentId,
                        studentName = r.studentName.orEmpty(),
                        type = r.type,
                        category = r.category,
                        organization = r.organization.orEmpty(),
                        reason = r.reason ?: "",
                        status = r.status.exemptionStatusLabel(),
                        proofFiles = r.proofFiles.map { it.cosKey.ifBlank { it.url } },
                        reviewComment = r.reviewComment ?: "",
                        reviewerId = r.reviewerId ?: "",
                        reviewerName = r.reviewerName ?: "",
                        createdAt = r.createdAt,
                        updatedAt = r.updatedAt ?: ""
                    )
                }
                if (selectedExemptionId != null && exemptions.none { it.id == selectedExemptionId }) {
                    selectedExemptionId = null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiHttpException && e.statusCode == 401) {
                    onUnauthorized()
                    return@launchAuthenticatedRequest
                }
                errorMessage = "加载失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
        loadJob.value = request
        if (request == null) {
            isLoading = false
            onUnauthorized()
        }
    }

    // Load on first composition — safely managed by LaunchedEffect lifecycle
    LaunchedEffect(Unit) {
        if (exemptions.isEmpty() && !isLoading) {
            loadExemptions()
        }
    }

    AnimatedContent(
        targetState = selectedExemptionId,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(tween(BNBUMotion.Standard)) +
                    slideInHorizontally(tween(BNBUMotion.Standard)) { it / 10 }) togetherWith
                    (fadeOut(tween(BNBUMotion.Quick)) +
                        slideOutHorizontally(tween(BNBUMotion.Quick)) { -it / 14 })
            } else {
                (fadeIn(tween(BNBUMotion.Standard)) +
                    slideInHorizontally(tween(BNBUMotion.Standard)) { -it / 10 }) togetherWith
                    (fadeOut(tween(BNBUMotion.Quick)) +
                        slideOutHorizontally(tween(BNBUMotion.Quick)) { it / 14 })
            }
        },
        label = "exemption-detail-transition"
    ) { targetId ->
        val selectedExemption = targetId?.let { id -> exemptions.firstOrNull { it.id == id } }
        if (selectedExemption != null) {
            ExemptionDetail(
                exemption = selectedExemption,
                onBack = { selectedExemptionId = null },
                onSupplement = {
                    resubmittingExemption = selectedExemption
                    selectedExemptionId = null
                    selectedTab = ExemptionTab.NewApplication
                }
            )
        } else {
            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier.fillMaxWidth(),
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
                label = "exemption-tab-transition"
            ) { animatedTab ->
                LazyColumn(
                    state = if (animatedTab == ExemptionTab.MyApplications) {
                        applicationsListState
                    } else {
                        formListState
                    },
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .bnbuClickable(enabled = !isFormSubmitting, onClick = handleBack),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = cs.onSurface
                )
                Text(
                    text = "返回",
                    color = cs.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            SectionTitle(
                eyebrow = "Exemption",
                title = "体育免测与免打卡申请"
            )
        }

        item {
            SegmentedControl(
                values = ExemptionTab.entries,
                selected = animatedTab,
                label = { it.label },
                onSelected = { if (!isFormSubmitting) selectedTab = it }
            )
        }

        successMessage?.let { message ->
            item {
                StatusMessagePanel(
                    message = message,
                    onDismiss = { successMessage = null }
                )
            }
        }
        errorMessage?.let { message ->
            item {
                ValidationPanel(message = message)
            }
        }

        if (isLoading && exemptions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        when (animatedTab) {
            ExemptionTab.MyApplications -> {
                if (exemptions.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            title = "暂无申请",
                            message = "你还没有提交过免测或免打卡申请。"
                        )
                    }
                } else {
                    items(items = exemptions, key = { it.id }) { exemption ->
                        ExemptionCard(exemption = exemption, onClick = { selectedExemptionId = exemption.id })
                    }
                }
            }

            ExemptionTab.NewApplication -> {
                item {
                    NewExemptionForm(
                        appState = appState,
                        repository = repository,
                        initialExemption = resubmittingExemption,
                        isSubmitting = isFormSubmitting,
                        onSubmittingChanged = { isFormSubmitting = it },
                        onUnauthorized = onUnauthorized,
                        onSuccess = { msg ->
                            successMessage = msg
                            resubmittingExemption = null
                            selectedTab = ExemptionTab.MyApplications
                            loadExemptions()
                        },
                        onError = { errorMessage = it }
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
private fun ExemptionCard(exemption: Exemption, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val statusColor = when (exemption.status) {
        "已通过" -> cs.primary
        "已驳回" -> cs.error
        else -> cs.secondary
    }

    SwissPanel(modifier = Modifier.bnbuClickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = exemption.typeLabel,
                        color = cs.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(text = exemption.status, filled = exemption.status == "已通过")
                }

                if (exemption.reason.isNotBlank()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = exemption.reason,
                            color = cs.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                if (exemption.organization.isNotBlank()) {
                    Text(
                        text = "所属组织：${exemption.organization}",
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (exemption.proofFiles.isNotEmpty()) {
                    Text(
                        text = "已上传 ${exemption.proofFiles.size} 个证明文件",
                        color = cs.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (exemption.reviewComment.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(cs.surfaceVariant, MaterialTheme.shapes.small)
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "审核意见",
                                color = cs.onSurface,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = exemption.reviewComment,
                                color = cs.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Text(
                    text = "提交时间: ${exemption.createdAt} · 点击查看详情",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ExemptionDetail(
    exemption: Exemption,
    onBack: () -> Unit,
    onSupplement: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    BackHandler(onBack = onBack)
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp).bnbuClickable(onClick = onBack),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = cs.onSurface)
                Text("返回我的申请", color = cs.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { SectionTitle(eyebrow = "Application", title = exemption.typeLabel) }
        item {
            SwissPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("申请状态", color = cs.onSurface, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StatusBadge(text = exemption.status, filled = exemption.status == "已通过")
                }
                Spacer(Modifier.height(14.dp))
                if (exemption.organization.isNotBlank()) {
                    Text("所属组织：${exemption.organization}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                }
                Text("申请理由：${exemption.reason}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text("提交时间：${exemption.createdAt}", color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
        }
        item {
            SwissPanel {
                Text("证明材料", color = cs.onSurface, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                if (exemption.proofFiles.isEmpty()) {
                    Text("尚未上传证明材料", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                } else {
                    exemption.proofFiles.forEachIndexed { index, proof ->
                        Text(
                            text = "${index + 1}. ${proof.substringAfterLast('/').ifBlank { "证明文件" }}",
                            color = cs.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        if (exemption.reviewComment.isNotBlank()) {
            item {
                SwissPanel {
                    Text("处理意见", color = cs.onSurface, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(exemption.reviewComment, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (exemption.status == "需补材料" || exemption.status == "已驳回") {
            item {
                ActionButton(
                    title = "补交证明材料",
                    icon = Icons.Filled.FileUpload,
                    filled = true,
                    onClick = onSupplement
                )
            }
        }
    }
}

@Composable
private fun ExemptionTypeSelector(
    selected: ExemptionType,
    enabled: Boolean,
    onSelected: (ExemptionType) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    ExemptionType.entries.chunked(2).forEachIndexed { rowIndex, options ->
        if (rowIndex > 0) Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            options.forEach { option ->
                val isSelected = selected == option
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) cs.primaryContainer else cs.surfaceVariant,
                    animationSpec = BNBUMotion.colorSpec,
                    label = "exemption-type-background"
                )
                val contentColor by animateColorAsState(
                    targetValue = if (isSelected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                    animationSpec = BNBUMotion.colorSpec,
                    label = "exemption-type-content"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(
                            backgroundColor,
                            MaterialTheme.shapes.small
                        )
                        .bnbuClickable(enabled = enabled) { onSelected(option) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun NewExemptionForm(
    appState: StudentAppState,
    repository: ApiStudentRepository,
    initialExemption: Exemption? = null,
    isSubmitting: Boolean,
    onSubmittingChanged: (Boolean) -> Unit,
    onUnauthorized: () -> Unit,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    var selectedType by remember(initialExemption?.id) { mutableStateOf(initialExemption?.type.toExemptionType()) }
    var organization by remember(initialExemption?.id) { mutableStateOf(initialExemption?.organization.orEmpty()) }
    var reason by remember(initialExemption?.id) { mutableStateOf("") }
    var proofAttachments by remember { mutableStateOf<List<ProofAttachment>>(emptyList()) }
    var attachmentNotice by remember { mutableStateOf<String?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    var cameraTempFile by remember { mutableStateOf<File?>(null) }
    val submissionJob = remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestProofAttachments by rememberUpdatedState(proofAttachments)
    val latestCameraTempFile by rememberUpdatedState(cameraTempFile)
    val latestIsSubmitting by rememberUpdatedState(isSubmitting)

    DisposableEffect(Unit) {
        onDispose {
            submissionJob.value?.cancel()
            latestProofAttachments.forEach {
                it.deleteOwnedCameraFile(context, "exemption_")
                it.releasePersistableReadPermissionIfPossible(context)
            }
            latestCameraTempFile?.delete()
        }
    }

    val maxAttachments = 5

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraTempUri
        val file = cameraTempFile
        if (latestIsSubmitting) {
            file?.delete()
        } else if (success && uri != null && file != null) {
            val attachment = file.toProofAttachmentFromCamera(uri)
            if (attachment != null && attachment.isValidForUpload) {
                proofAttachments = proofAttachments + attachment
                attachmentNotice = "已拍摄 1 张凭证照片。"
            } else {
                file.delete()
                attachmentNotice = "拍摄失败，请重试或从相册选择。"
            }
        } else {
            file?.delete()
        }
        cameraTempUri = null
        cameraTempFile = null
    }

    // Gallery picker launcher
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (isSubmitting) {
            attachmentNotice = "正在提交，暂时不能修改证明材料。"
            return@rememberLauncherForActivityResult
        }
        val remaining = maxAttachments - proofAttachments.size
        if (remaining <= 0) {
            attachmentNotice = "已达到 $maxAttachments 个凭证上限。"
            return@rememberLauncherForActivityResult
        }
        val selectedUris = uris.take(remaining)
        val startIndex = proofAttachments.size
        scope.launch {
            val pickedAttachments = try {
                withContext(Dispatchers.IO) {
                    selectedUris.mapIndexed { offset, uri ->
                        uri.toProofAttachment(context = context, index = startIndex + offset)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                attachmentNotice = "无法读取所选文件，请重新选择。"
                return@launch
            }
            if (latestIsSubmitting) return@launch
            val existingSources = latestProofAttachments.mapTo(mutableSetOf()) { it.source }
            val newAttachments = pickedAttachments
                .distinctBy { it.source }
                .filterNot { it.source in existingSources }
                .filter { attachment ->
                    val uri = runCatching { Uri.parse(attachment.source) }.getOrNull()
                    uri != null && context.takePersistableReadPermissionIfPossible(uri)
                }
            if (newAttachments.isNotEmpty()) {
                proofAttachments = latestProofAttachments + newAttachments
            }
            attachmentNotice = when {
                uris.isEmpty() -> null
                newAttachments.isEmpty() -> "未添加文件：请避免重复选择，并使用支持长期授权的相册文件。"
                newAttachments.size < uris.size ->
                    "已添加 ${newAttachments.size} 个凭证；重复、超限或无法长期授权的文件已跳过。"
                else -> "已添加 ${newAttachments.size} 个凭证。"
            }
        }
    }

    val cs = MaterialTheme.colorScheme

    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (initialExemption != null) {
                Text(
                    text = "正在为 ${initialExemption.typeLabel} 补交证明，请上传新的有效材料。",
                    color = cs.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (initialExemption == null) {
                Text(
                    text = "选择申请类型",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                ExemptionTypeSelector(
                    selected = selectedType,
                    enabled = !isSubmitting,
                    onSelected = {
                        selectedType = it
                        if (!it.isCheckInExemption) organization = ""
                    }
                )
            }

            AnimatedVisibility(
                visible = selectedType.isCheckInExemption,
                enter = expandVertically(tween(BNBUMotion.Standard)) + fadeIn(tween(BNBUMotion.Standard)),
                exit = shrinkVertically(tween(BNBUMotion.Standard)) + fadeOut(tween(BNBUMotion.Quick))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "组织名称",
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    OutlinedTextField(
                        value = organization,
                        onValueChange = { organization = it.take(128) },
                        enabled = !isSubmitting,
                        placeholder = { Text("填写校队或社团名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (initialExemption == null) "申请理由" else "补充说明",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(MaxExemptionReasonLength) },
                    enabled = !isSubmitting,
                    placeholder = {
                        Text(
                            if (initialExemption != null) "请说明本次补充材料的内容..."
                            else if (selectedType.isCheckInExemption) "请说明组织身份及申请原因..."
                            else "请说明申请免测的原因..."
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            }

            // ── Proof file section with camera/gallery ─────────────
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "证明材料",
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${proofAttachments.size} / $maxAttachments 个文件",
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Camera + Gallery buttons
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionButton(
                        title = "拍照",
                        icon = Icons.Filled.CameraAlt,
                        filled = proofAttachments.size < maxAttachments,
                        modifier = Modifier.weight(1f),
                        enabled = !isSubmitting && proofAttachments.size < maxAttachments,
                        onClick = {
                            if (isSubmitting) return@ActionButton
                            if (proofAttachments.size >= maxAttachments) {
                                attachmentNotice = "已达到 $maxAttachments 个凭证上限。"
                                return@ActionButton
                            }
                            var photoFile: File? = null
                            try {
                                val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                                    ?: context.cacheDir
                                photoFile = File(
                                    picturesDir,
                                    "exemption_${System.currentTimeMillis()}.jpg"
                                )
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
                                attachmentNotice = "相机不可用，请从相册选择证明材料。"
                            }
                        }
                    )

                    ActionButton(
                        title = "选择照片",
                        icon = Icons.Filled.UploadFile,
                        filled = proofAttachments.size < maxAttachments,
                        modifier = Modifier.weight(1f),
                        enabled = !isSubmitting && proofAttachments.size < maxAttachments,
                        onClick = {
                            if (proofAttachments.size < maxAttachments) {
                                mediaPicker.launch(arrayOf("image/*"))
                            } else {
                                attachmentNotice = "已达到 $maxAttachments 个凭证上限。"
                            }
                        }
                    )
                }

                AnimatedVisibility(
                    visible = attachmentNotice != null,
                    enter = expandVertically(tween(BNBUMotion.Standard)) + fadeIn(tween(BNBUMotion.Standard)),
                    exit = shrinkVertically(tween(BNBUMotion.Standard)) + fadeOut(tween(BNBUMotion.Quick))
                ) {
                    attachmentNotice?.let { notice ->
                        Text(
                            text = notice,
                            color = cs.primary,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Display current attachments
                Column(
                    modifier = Modifier.animateContentSize(tween(BNBUMotion.Standard)),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (proofAttachments.isEmpty()) {
                        Text(
                            text = if (selectedType.isCheckInExemption) {
                                "请上传能够证明校队或社团身份的材料。"
                            } else {
                                "请至少上传 1 份医院证明或诊断材料。"
                            },
                            color = cs.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        proofAttachments.forEach { attachment ->
                            ExemptionProofAttachmentRow(
                                attachment = attachment,
                                enabled = !isSubmitting,
                                onRemove = {
                                    val remainingAttachments = proofAttachments.filterNot {
                                        it.id == attachment.id
                                    }
                                    attachment.deleteOwnedCameraFile(context, "exemption_")
                                    if (remainingAttachments.none { it.source == attachment.source }) {
                                        attachment.releasePersistableReadPermissionIfPossible(context)
                                    }
                                    proofAttachments = remainingAttachments
                                }
                            )
                        }
                        }
                    }
                }

            PrimaryActionButton(
                title = if (isSubmitting) "提交中..." else if (initialExemption != null) "提交补充材料" else "提交申请",
                icon = Icons.Filled.Add,
                enabled = !isSubmitting && submissionJob.value?.isActive != true,
                loading = isSubmitting,
                onClick = {
                    if (isSubmitting || submissionJob.value?.isActive == true) return@PrimaryActionButton
                    val normalizedReason = reason.trim()
                    if (normalizedReason.length < 2) {
                        onError("申请理由或补充说明至少需要 2 个字符")
                        return@PrimaryActionButton
                    }
                    if (selectedType.isCheckInExemption && organization.isBlank()) {
                        onError("请填写校队或社团名称")
                        return@PrimaryActionButton
                    }
                    if (proofAttachments.isEmpty()) {
                        onError("请至少上传 1 个申请证明")
                        return@PrimaryActionButton
                    }
                    val selectedTypeSnapshot = selectedType
                    val organizationSnapshot = organization.trim().takeIf {
                        selectedTypeSnapshot.isCheckInExemption && it.isNotBlank()
                    }
                    val proofSnapshot = proofAttachments.toList()
                    onSubmittingChanged(true)
                    val request = appState.launchAuthenticatedRequest {
                        try {
                            // Upload proof files first (if any)
                            var uploadedCosKeys: List<String> = emptyList()
                            if (proofSnapshot.isNotEmpty()) {
                                val cacheDir = context.cacheDir
                                val uploadResult = repository.uploadProofFiles(
                                    proofAttachments = proofSnapshot,
                                    cacheDir = cacheDir
                                )
                                uploadedCosKeys = uploadResult.getOrThrow().map { it.cosKey }
                            }

                            val application = ExemptionApplication(
                                type = selectedTypeSnapshot.apiValue,
                                reason = normalizedReason,
                                proofFiles = uploadedCosKeys,
                                organization = organizationSnapshot
                            )
                            val response = initialExemption?.let {
                                repository.supplementExemption(it, application)
                            } ?: repository.submitExemption(application)
                            proofSnapshot.forEach {
                                it.deleteOwnedCameraFile(context, "exemption_")
                                it.releasePersistableReadPermissionIfPossible(context)
                            }
                            val submittedIds = proofSnapshot.mapTo(mutableSetOf()) { it.id }
                            proofAttachments = proofAttachments.filterNot { it.id in submittedIds }
                            onSuccess(
                                if (initialExemption != null) "补充材料已提交 (${response.id})"
                                else "申请已提交 (${response.id})"
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (e is ApiHttpException && e.statusCode == 401) {
                                onUnauthorized()
                                return@launchAuthenticatedRequest
                            }
                            onError("提交失败: ${e.message}")
                        } finally {
                            onSubmittingChanged(false)
                        }
                    }
                    submissionJob.value = request
                    if (request == null) {
                        onSubmittingChanged(false)
                        onUnauthorized()
                    }
                }
            )
        }
    }
}

@Composable
private fun ExemptionProofAttachmentRow(
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
            imageVector = Icons.Filled.Photo,
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
                    attachment.validationMessage?.let { add(it) }
                }.joinToString(" · "),
                color = if (attachment.isValidForUpload) cs.onSurfaceVariant else cs.primary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .bnbuClickable(enabled = enabled, onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "移除",
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── URI helper extensions (same pattern as CheckInScreen) ──────────

private fun Uri.toProofAttachment(context: Context, index: Int): ProofAttachment {
    val fileName = context.displayNameFor(this, index)
    return ProofAttachment(
        id = UUID.randomUUID().toString(),
        type = ProofMediaType.Image,
        fileName = fileName,
        byteCount = context.byteCountFor(this),
        source = toString()
    )
}

private fun String.exemptionStatusLabel(): String = when (this) {
    "pending" -> "待审核"
    "reviewing" -> "审核中"
    "supplement_required" -> "需补材料"
    "approved" -> "已通过"
    "rejected" -> "已驳回"
    "expired" -> "已过期"
    else -> this
}

private fun String?.toExemptionType(): ExemptionType = when (this) {
    "1000m" -> ExemptionType.Run1000
    "team" -> ExemptionType.Team
    "club" -> ExemptionType.Club
    else -> ExemptionType.Run800
}

private fun File.toProofAttachmentFromCamera(sourceUri: Uri): ProofAttachment? {
    if (!exists() || length() <= 0L) return null
    return ProofAttachment(
        id = UUID.randomUUID().toString(),
        type = ProofMediaType.Image,
        fileName = name,
        byteCount = length(),
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

private fun Context.displayNameFor(uri: Uri, index: Int): String {
    return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "proof-${index + 1}"
}

private fun Context.byteCountFor(uri: Uri): Long? {
    return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst() && sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
            cursor.getLong(sizeIndex)
        } else {
            null
        }
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
