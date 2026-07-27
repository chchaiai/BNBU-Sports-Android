package edu.bnbu.student.mvp.feature.checkin

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import edu.bnbu.student.mvp.core.designsystem.ActionButton
import edu.bnbu.student.mvp.core.designsystem.EmptyPlaceholder
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.StatusBadge
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.ValidationPanel
import edu.bnbu.student.mvp.core.designsystem.bnbuClickable
import edu.bnbu.student.mvp.core.model.CheckInRecord
import edu.bnbu.student.mvp.core.model.ProofAttachment
import edu.bnbu.student.mvp.core.model.ProofMediaType
import edu.bnbu.student.mvp.core.model.ReviewStatus
import edu.bnbu.student.mvp.core.model.hourText
import edu.bnbu.student.mvp.core.state.StudentAppState

@Composable
internal fun RecordListIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(eyebrow = "Records", title = "打卡记录")
    }
}

@Composable
internal fun RecordCard(
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
internal fun CheckInRecordDetail(
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
