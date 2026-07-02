package edu.bnbu.student.mvp.feature.exemption

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.data.ApiStudentRepository
import edu.bnbu.student.mvp.core.designsystem.ActionButton
import edu.bnbu.student.mvp.core.designsystem.BNBUColors
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

    fun loadExemptions() {
        isLoading = true
        scope.launch {
            try {
                val response = repository.listExemptions()
                exemptions = response.map { r ->
                    Exemption(
                        id = r.id,
                        studentId = r.studentId,
                        studentName = r.studentName,
                        type = r.type,
                        reason = r.reason ?: "",
                        status = r.status,
                        proofFiles = r.proofFiles,
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
                    .clickable(onClick = onBack)
                    .padding(vertical = 8.dp),
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
            SectionTitle(
                eyebrow = "Exemption",
                title = "800m / 1000m 免测申请"
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
                            title = "暂无免测申请",
                            message = "你还没有提交过免测申请。请切换到「提交申请」标签页提交新的申请。"
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
    val statusColor = when (exemption.status) {
        "已通过" -> BNBUColors.Blue
        "已驳回" -> Color(0xFFF44336)
        else -> Color(0xFFFF9800)
    }

    SwissPanel {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = BNBUColors.Blue,
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
                        color = BNBUColors.Ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f)
                    )
                    StatusBadge(text = exemption.status, filled = exemption.status == "已通过")
                }

                if (exemption.reason.isNotBlank()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = BNBUColors.Muted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = exemption.reason,
                            color = BNBUColors.Muted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 20.sp
                        )
                    }
                }

                if (exemption.proofFiles.isNotEmpty()) {
                    Text(
                        text = "已上传 ${exemption.proofFiles.size} 个证明文件",
                        color = BNBUColors.Blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (exemption.reviewComment.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BNBUColors.BlueSoft)
                            .border(1.dp, BNBUColors.Line, RectangleShape)
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = BNBUColors.Blue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "审核意见",
                                color = BNBUColors.Ink,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = exemption.reviewComment,
                                color = BNBUColors.Muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    text = "提交时间: ${exemption.createdAt}",
                    color = BNBUColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
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

    SwissPanel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "选择免测项目",
                color = BNBUColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            SegmentedControl(
                values = ExemptionType.entries,
                selected = selectedType,
                label = { it.label },
                onSelected = { selectedType = it }
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "申请理由",
                    color = BNBUColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = { Text("请说明申请免测的原因...") },
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
                        color = BNBUColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${proofAttachments.size} / $maxAttachments 个文件",
                        color = BNBUColors.Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
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
                        color = BNBUColors.Blue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 17.sp
                    )
                }

                // Display current attachments
                if (proofAttachments.isEmpty()) {
                    Text(
                        text = "证明材料为可选。请使用拍照或相册功能上传医院证明、诊断书等文件。",
                        color = BNBUColors.Muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
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
                title = if (isSubmitting) "提交中..." else "提交免测申请",
                icon = Icons.Filled.Add,
                filled = true,
                onClick = {
                    if (reason.isBlank()) {
                        onError("请填写申请理由")
                        return@ActionButton
                    }
                    isSubmitting = true
                    scope.launch {
                        try {
                            // Upload proof files first (if any)
                            var uploadedUrls: List<String> = emptyList()
                            if (proofAttachments.isNotEmpty()) {
                                val cacheDir = context.cacheDir
                                val uploadResult = repository.uploadProofFiles(
                                    proofAttachments = proofAttachments,
                                    cacheDir = cacheDir
                                )
                                uploadedUrls = uploadResult.getOrDefault(emptyList())
                            }

                            val response = repository.submitExemption(
                                ExemptionApplication(
                                    type = selectedType.label,
                                    reason = reason,
                                    proofFiles = uploadedUrls
                                )
                            )
                            onSuccess("免测申请已提交 (${response.id})")
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BNBUColors.BlueSoft)
            .border(1.dp, BNBUColors.Line, RectangleShape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Photo,
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
                    attachment.validationMessage?.let { add(it) }
                }.joinToString(" · "),
                color = if (attachment.isValidForUpload) BNBUColors.Muted else BNBUColors.Blue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
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
                tint = BNBUColors.Muted,
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
