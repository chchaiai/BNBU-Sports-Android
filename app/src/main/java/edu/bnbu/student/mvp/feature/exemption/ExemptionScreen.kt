package edu.bnbu.student.mvp.feature.exemption

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.data.ApiStudentRepository
import edu.bnbu.student.mvp.core.designsystem.ActionButton
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.SegmentedControl
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.StatusMessagePanel
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.ValidationPanel
import edu.bnbu.student.mvp.core.model.Exemption
import edu.bnbu.student.mvp.core.model.ExemptionApplication
import edu.bnbu.student.mvp.core.model.ExemptionType
import edu.bnbu.student.mvp.core.model.ProofAttachment
import edu.bnbu.student.mvp.core.model.ProofMediaType
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
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
import kotlinx.coroutines.launch

private enum class ExemptionTab(val label: String) {
    MyApplications("我的申请"),
    NewApplication("提交申请")
}

@Composable
fun ExemptionScreen(
    repository: ApiStudentRepository,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(ExemptionTab.MyApplications) }
    var exemptions by remember { mutableStateOf<List<Exemption>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val cs = MaterialTheme.colorScheme
    val handleBack = {
        focusManager.clearFocus(force = true)
        onBack()
    }

    BackHandler(onBack = handleBack)

    fun loadExemptions() {
        isLoading = true
        scope.launch {
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
            } catch (e: Exception) {
                errorMessage = "加载失败: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // Load on first composition — safely managed by LaunchedEffect lifecycle
    LaunchedEffect(Unit) {
        if (exemptions.isEmpty() && !isLoading) {
            loadExemptions()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clickable(onClick = handleBack),
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
                selected = selectedTab,
                label = { it.label },
                onSelected = { selectedTab = it }
            )
        }

        if (successMessage != null) {
            item {
                val msg = successMessage!!
                StatusMessagePanel(
                    message = msg,
                    onDismiss = { successMessage = null }
                )
            }
        }

        if (errorMessage != null) {
            item {
                val msg = errorMessage!!
                ValidationPanel(message = msg)
            }
        }

        when (selectedTab) {
            ExemptionTab.MyApplications -> {
                if (exemptions.isEmpty()) {
                    item {
                        EmptyPlaceholder(
                            title = "暂无申请",
                            message = "你还没有提交过免测或免打卡申请。"
                        )
                    }
                } else {
                    exemptions.forEach { exemption ->
                        item {
                            ExemptionCard(exemption)
                        }
                    }
                }
            }

            ExemptionTab.NewApplication -> {
                item {
                    NewExemptionForm(
                        repository = repository,
                        onSuccess = { msg ->
                            successMessage = msg
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

@Composable
private fun ExemptionCard(exemption: Exemption) {
    val cs = MaterialTheme.colorScheme
    val statusColor = when (exemption.status) {
        "已通过" -> cs.primary
        "已驳回" -> cs.error
        else -> cs.secondary
    }

    SwissPanel {
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
                    text = "提交时间: ${exemption.createdAt}",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ExemptionTypeSelector(
    selected: ExemptionType,
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(
                            if (isSelected) cs.primaryContainer else cs.surfaceVariant,
                            MaterialTheme.shapes.small
                        )
                        .clickable { onSelected(option) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label,
                        color = cs.onSurface,
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
    repository: ApiStudentRepository,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    var selectedType by remember { mutableStateOf(ExemptionType.Run800) }
    var organization by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var proofAttachments by remember { mutableStateOf<List<ProofAttachment>>(emptyList()) }
    var isSubmitting by remember { mutableStateOf(false) }
    var attachmentNotice by remember { mutableStateOf<String?>(null) }
    var cameraTempUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val maxAttachments = 5

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraTempUri
        if (success && uri != null) {
            val attachment = uri.toProofAttachmentFromCamera(context, proofAttachments.size)
            if (attachment != null && attachment.isValidForUpload) {
                proofAttachments = proofAttachments + attachment
                attachmentNotice = "已拍摄 1 张凭证照片。"
            } else {
                attachmentNotice = "拍摄失败，请重试或从相册选择。"
            }
        }
        cameraTempUri = null
    }

    // Gallery picker launcher
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        val remaining = maxAttachments - proofAttachments.size
        if (remaining <= 0) {
            attachmentNotice = "已达到 $maxAttachments 个凭证上限。"
            return@rememberLauncherForActivityResult
        }
        val selectedUris = uris.take(remaining)
        val newAttachments = selectedUris.mapIndexed { offset, uri ->
            context.takePersistableReadPermissionIfPossible(uri)
            uri.toProofAttachment(context = context, index = proofAttachments.size + offset)
        }
        if (newAttachments.isNotEmpty()) {
            proofAttachments = proofAttachments + newAttachments
        }
        attachmentNotice = when {
            uris.isEmpty() -> null
            uris.size > remaining -> "已添加 $remaining 个凭证，已达到上限。"
            else -> "已添加 ${newAttachments.size} 个凭证。"
        }
    }

    val cs = MaterialTheme.colorScheme

    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "选择申请类型",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
            ExemptionTypeSelector(
                selected = selectedType,
                onSelected = {
                    selectedType = it
                    if (!it.isCheckInExemption) organization = ""
                }
            )

            if (selectedType.isCheckInExemption) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "组织名称",
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium
                    )
                    OutlinedTextField(
                        value = organization,
                        onValueChange = { organization = it.take(128) },
                        placeholder = { Text("填写校队或社团名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "申请理由",
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = {
                        Text(
                            if (selectedType.isCheckInExemption) "请说明组织身份及申请原因..."
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
                        onClick = {
                            if (proofAttachments.size >= maxAttachments) {
                                attachmentNotice = "已达到 $maxAttachments 个凭证上限。"
                                return@ActionButton
                            }
                            val photoFile = File(
                                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                                "exemption_${System.currentTimeMillis()}.jpg"
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
                        title = "选择照片",
                        icon = Icons.Filled.UploadFile,
                        filled = proofAttachments.size < maxAttachments,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (proofAttachments.size < maxAttachments) {
                                mediaPicker.launch(arrayOf("image/*"))
                            } else {
                                attachmentNotice = "已达到 $maxAttachments 个凭证上限。"
                            }
                        }
                    )
                }

                attachmentNotice?.let { notice ->
                    Text(
                        text = notice,
                        color = cs.primary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                // Display current attachments
                if (proofAttachments.isEmpty()) {
                    Text(
                        text = if (selectedType.isCheckInExemption) {
                            "请上传能够证明校队或社团身份的材料。"
                        } else {
                            "证明材料为可选，可上传医院证明或诊断书。"
                        },
                        color = cs.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        proofAttachments.forEach { attachment ->
                            ExemptionProofAttachmentRow(
                                attachment = attachment,
                                onRemove = {
                                    proofAttachments = proofAttachments.filterNot { it.id == attachment.id }
                                }
                            )
                        }
                    }
                }
            }

            ActionButton(
                title = if (isSubmitting) "提交中..." else "提交申请",
                icon = Icons.Filled.Add,
                filled = true,
                onClick = {
                    if (reason.isBlank()) {
                        onError("请填写申请理由")
                        return@ActionButton
                    }
                    if (selectedType.isCheckInExemption && organization.isBlank()) {
                        onError("请填写校队或社团名称")
                        return@ActionButton
                    }
                    if (proofAttachments.isEmpty()) {
                        onError("请至少上传 1 个申请证明")
                        return@ActionButton
                    }
                    isSubmitting = true
                    scope.launch {
                        try {
                            // Upload proof files first (if any)
                            var uploadedCosKeys: List<String> = emptyList()
                            if (proofAttachments.isNotEmpty()) {
                                val cacheDir = context.cacheDir
                                val uploadResult = repository.uploadProofFiles(
                                    proofAttachments = proofAttachments,
                                    cacheDir = cacheDir
                                )
                                uploadedCosKeys = uploadResult.getOrThrow().map { it.cosKey }
                            }

                            val response = repository.submitExemption(
                                ExemptionApplication(
                                    type = selectedType.apiValue,
                                    reason = reason,
                                    proofFiles = uploadedCosKeys,
                                    organization = organization.takeIf { it.isNotBlank() }
                                )
                            )
                            onSuccess("申请已提交 (${response.id})")
                        } catch (e: Exception) {
                            onError("提交失败: ${e.message}")
                        } finally {
                            isSubmitting = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ExemptionProofAttachmentRow(
    attachment: ProofAttachment,
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
                .clickable(onClick = onRemove),
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

private fun Uri.toProofAttachmentFromCamera(context: Context, index: Int): ProofAttachment? {
    val fileName = "camera_${System.currentTimeMillis()}.jpg"
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName)
    val byteCount = if (file.exists()) file.length() else null
    return ProofAttachment(
        id = UUID.randomUUID().toString(),
        type = ProofMediaType.Image,
        fileName = fileName,
        byteCount = byteCount,
        source = toString()
    )
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

private fun Context.takePersistableReadPermissionIfPossible(uri: Uri) {
    runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
