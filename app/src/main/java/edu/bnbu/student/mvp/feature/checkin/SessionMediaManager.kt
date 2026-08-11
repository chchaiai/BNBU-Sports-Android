package edu.bnbu.student.mvp.feature.checkin

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Close
import edu.bnbu.student.mvp.core.designsystem.AppleButton as Button
import edu.bnbu.student.mvp.core.designsystem.AppleIconButton as IconButton
import edu.bnbu.student.mvp.core.designsystem.AppleOutlinedButton as OutlinedButton
import edu.bnbu.student.mvp.core.designsystem.AppleTextButton as TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import coil3.video.videoFrameMillis
import edu.bnbu.student.mvp.core.designsystem.bnbuClickable
import edu.bnbu.student.mvp.core.designsystem.interfaceText
import edu.bnbu.student.mvp.core.model.ProofMediaType
import edu.bnbu.student.mvp.core.model.ProofUploadRule
import edu.bnbu.student.mvp.feature.checkin.session.ExerciseSessionController
import edu.bnbu.student.mvp.feature.checkin.session.SessionMediaDraft
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.delay

private val MediaManagerBlue = Color(0xFF007AFF)
private val MediaManagerDanger = Color(0xFFFF3B30)

/**
 * The single visual manager for the session-media draft source. It intentionally
 * reads [ExerciseSessionController.drafts] directly, so thumbnails, edits,
 * ordering, local draft persistence, and final submission always share one list.
 */
@Composable
internal fun SessionMediaManager(
    controller: ExerciseSessionController,
    submissionRequired: Boolean,
    modifier: Modifier = Modifier,
    onRetakeRequested: (SessionMediaDraft) -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
    val drafts = controller.drafts
    val photos = drafts.filter { it.type == ProofMediaType.Image }
    val videos = drafts.filter { it.type == ProofMediaType.Video }
    val photoOrderKey = drafts.joinToString(separator = "|") { draft ->
        "${draft.id}:${draft.fileName}:${draft.type}"
    }
    var visualPhotoOrder by remember(photoOrderKey) { mutableStateOf(photos.map { it.id }) }
    var previewDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var draggingPhotoId by remember { mutableStateOf<String?>(null) }

    val photosById = photos.associateBy { it.id }
    val orderedPhotos = visualPhotoOrder.mapNotNull(photosById::get)
    val orderedPhotoIterator = orderedPhotos.iterator()
    val displayDrafts = drafts.map { draft ->
        if (draft.type == ProofMediaType.Image && orderedPhotoIterator.hasNext()) {
            orderedPhotoIterator.next()
        } else {
            draft
        }
    }

    fun movePhoto(photoId: String, direction: Int) {
        val from = visualPhotoOrder.indexOf(photoId)
        val to = (from + direction).coerceIn(0, visualPhotoOrder.lastIndex)
        if (from < 0 || from == to) return
        visualPhotoOrder = visualPhotoOrder.toMutableList().apply {
            val item = removeAt(from)
            add(to, item)
        }
    }

    fun persistPhotoOrder() {
        val currentOrder = photos.map { it.id }
        if (visualPhotoOrder != currentOrder) controller.reorderPhotos(visualPhotoOrder)
    }

    val deleteTarget = deleteDraftId?.let { id -> drafts.firstOrNull { it.id == id } }
    if (deleteTarget != null) {
        DeleteMediaDraftDialog(
            isBusy = controller.isMediaBusy,
            onDismiss = { deleteDraftId = null },
            onConfirm = {
                deleteDraftId = null
                previewDraftId = null
                controller.removeDraft(deleteTarget.id)
            }
        )
    }

    val previewDraft = previewDraftId?.let { id -> drafts.firstOrNull { it.id == id } }
    when (previewDraft?.type) {
        ProofMediaType.Image -> PhotoDraftPreviewDialog(
            photos = photos,
            initialDraftId = previewDraft.id,
            controller = controller,
            imageLoader = imageLoader,
            onDismiss = { previewDraftId = null },
            onDelete = { draft -> deleteDraftId = draft.id },
            onRetake = { draft ->
                previewDraftId = null
                onRetakeRequested(draft)
            }
        )

        ProofMediaType.Video -> VideoDraftPreviewDialog(
            draft = previewDraft,
            file = controller.resolveDraftFile(previewDraft),
            controller = controller,
            onDismiss = { previewDraftId = null },
            onDelete = { draft -> deleteDraftId = draft.id },
            onRetake = { draft ->
                previewDraftId = null
                onRetakeRequested(draft)
            }
        )

        null -> Unit
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = interfaceText("已拍摄素材", "Captured media"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            MediaCountPill(
                text = interfaceText(
                    "照片 ${photos.size}/${ProofUploadRule.maxImageCount}",
                    "Photos ${photos.size}/${ProofUploadRule.maxImageCount}"
                )
            )
            Spacer(Modifier.width(6.dp))
            MediaCountPill(
                text = interfaceText(
                    "视频 ${videos.size}/${ProofUploadRule.maxVideoCount}",
                    "Video ${videos.size}/${ProofUploadRule.maxVideoCount}"
                )
            )
        }
        Spacer(Modifier.height(10.dp))
        if (displayDrafts.isEmpty()) {
            MediaEmptyState(submissionRequired = submissionRequired)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(displayDrafts, key = { it.id }) { draft ->
                    val file = controller.resolveDraftFile(draft)
                    MediaDraftThumbnail(
                        draft = draft,
                        file = file,
                        imageLoader = imageLoader,
                        isDragging = draggingPhotoId == draft.id,
                        allowPhotoReorder = photos.size > 1 && !controller.isMediaBusy,
                        onOpen = { previewDraftId = draft.id },
                        onPhotoMove = { direction -> movePhoto(draft.id, direction) },
                        onPhotoDragStateChange = { dragging ->
                            draggingPhotoId = if (dragging) draft.id else null
                        },
                        onPhotoDragFinished = ::persistPhotoOrder
                    )
                }
            }
            if (photos.size > 1) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = interfaceText("长按照片并左右拖动可调整提交顺序。", "Press and hold a photo, then drag left or right to reorder it."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (submissionRequired) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = interfaceText("当前保留的照片和视频会全部作为本次打卡凭证提交。", "All retained photos and videos will be submitted as proof for this check-in."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MediaCountPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun MediaEmptyState(submissionRequired: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = null,
                tint = MediaManagerBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (submissionRequired) {
                    interfaceText("请先现场拍摄至少 1 张照片或 1 个视频。", "Capture at least one on-site photo or video first.")
                } else {
                    interfaceText("拍摄完成后，照片和视频会立即显示在这里。", "Captured photos and videos will appear here immediately.")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun MediaDraftThumbnail(
    draft: SessionMediaDraft,
    file: File?,
    imageLoader: ImageLoader,
    isDragging: Boolean,
    allowPhotoReorder: Boolean,
    onOpen: () -> Unit,
    onPhotoMove: (Int) -> Unit,
    onPhotoDragStateChange: (Boolean) -> Unit,
    onPhotoDragFinished: () -> Unit
) {
    val density = LocalDensity.current
    val dragThreshold = with(density) { 58.dp.toPx() }
    var accumulatedDrag by remember(draft.id) { mutableFloatStateOf(0f) }
    val shape = MaterialTheme.shapes.medium
    val canOpen = file?.isFile == true
    val dragModifier = if (allowPhotoReorder && draft.type == ProofMediaType.Image) {
        Modifier.pointerInput(draft.id, allowPhotoReorder) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    accumulatedDrag = 0f
                    onPhotoDragStateChange(true)
                },
                onDragCancel = {
                    accumulatedDrag = 0f
                    onPhotoDragStateChange(false)
                },
                onDragEnd = {
                    accumulatedDrag = 0f
                    onPhotoDragStateChange(false)
                    onPhotoDragFinished()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    accumulatedDrag += dragAmount.x
                    if (abs(accumulatedDrag) >= dragThreshold) {
                        onPhotoMove(if (accumulatedDrag > 0f) 1 else -1)
                        accumulatedDrag = 0f
                    }
                }
            )
        }
    } else {
        Modifier
    }
    Surface(
        modifier = dragModifier
            .width(112.dp)
            .height(122.dp)
            .graphicsLayer {
                scaleX = if (isDragging) 1.04f else 1f
                scaleY = if (isDragging) 1.04f else 1f
                alpha = if (isDragging) 0.92f else 1f
            }
            .bnbuClickable(enabled = canOpen, onClick = onOpen),
        color = MaterialTheme.colorScheme.surface,
        shape = shape,
        shadowElevation = if (isDragging) 6.dp else 2.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                DraftThumbnailImage(
                    draft = draft,
                    file = file,
                    imageLoader = imageLoader
                )
                if (draft.type == ProofMediaType.Video) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.56f),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = interfaceText("播放视频", "Play video"),
                            tint = Color.White,
                            modifier = Modifier.padding(5.dp).size(22.dp)
                        )
                    }
                    Text(
                        text = draft.durationSeconds?.let(::formatMediaDuration)
                            ?: interfaceText("时长未知", "Unknown duration"),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .background(Color.Black.copy(alpha = 0.66f), MaterialTheme.shapes.extraSmall)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                text = if (draft.type == ProofMediaType.Image) {
                    interfaceText("现场照片", "On-site photo")
                } else {
                    if (draft.compressedForUpload) {
                        interfaceText("现场视频", "On-site video")
                    } else {
                        interfaceText("视频待压缩", "Video needs compression")
                    }
                },
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
            Text(
                text = if (draft.type == ProofMediaType.Image) {
                    formatByteCount(draft.byteCount)
                } else {
                    draft.durationSeconds?.let(::formatMediaDuration) ?: formatByteCount(draft.byteCount)
                },
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DraftThumbnailImage(
    draft: SessionMediaDraft,
    file: File?,
    imageLoader: ImageLoader
) {
    val context = LocalContext.current
    if (file?.isFile != true) {
        MediaLoadFallback(draft.type)
        return
    }
    val request = remember(file.absolutePath, draft.type, draft.coverTimestampMillis) {
        ImageRequest.Builder(context)
            .data(file)
            .apply {
                if (draft.type == ProofMediaType.Video) {
                    decoderFactory(VideoFrameDecoder.Factory())
                    videoFrameMillis(draft.coverTimestampMillis ?: 0L)
                }
            }
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        imageLoader = imageLoader,
        contentDescription = if (draft.type == ProofMediaType.Image) {
            interfaceText("现场照片缩略图", "On-site photo thumbnail")
        } else {
            interfaceText("现场视频封面", "On-site video cover")
        },
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        loading = {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        },
        error = { MediaLoadFallback(draft.type) }
    )
}

@Composable
private fun MediaLoadFallback(type: ProofMediaType) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = if (type == ProofMediaType.Image) Icons.Filled.Image else Icons.Filled.Videocam,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = interfaceText("无法加载", "Unavailable"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun DeleteMediaDraftDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(interfaceText("删除这项内容？", "Delete this item?")) },
        text = { Text(interfaceText("删除后无法恢复，需要重新拍摄。", "Deleted media cannot be recovered. You will need to capture it again.")) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isBusy) {
                Text(interfaceText("删除", "Delete"), color = MediaManagerDanger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(interfaceText("取消", "Cancel"))
            }
        }
    )
}

private enum class PhotoCropOption(val aspectRatio: Float?) {
    Original(null),
    Square(1f),
    FourThree(4f / 3f),
    SixteenNine(16f / 9f);

    fun label(): String = when (this) {
        Original -> interfaceText("原始", "Original")
        Square -> "1:1"
        FourThree -> "4:3"
        SixteenNine -> "16:9"
    }
}

@Composable
private fun PhotoDraftPreviewDialog(
    photos: List<SessionMediaDraft>,
    initialDraftId: String,
    controller: ExerciseSessionController,
    imageLoader: ImageLoader,
    onDismiss: () -> Unit,
    onDelete: (SessionMediaDraft) -> Unit,
    onRetake: (SessionMediaDraft) -> Unit
) {
    if (photos.isEmpty()) {
        onDismiss()
        return
    }
    val initialPage = photos.indexOfFirst { it.id == initialDraftId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { photos.size })
    LaunchedEffect(initialDraftId, photos.map { it.id }) {
        photos.indexOfFirst { it.id == initialDraftId }
            .takeIf { it >= 0 }
            ?.let { pagerState.scrollToPage(it) }
    }
    val activeDraft = photos.getOrNull(pagerState.currentPage) ?: return
    var isEditing by remember(activeDraft.id) { mutableStateOf(false) }
    var cropOption by remember(activeDraft.id) { mutableStateOf(PhotoCropOption.Original) }
    var rotationDegrees by remember(activeDraft.id) { mutableLongStateOf(0L) }
    var editError by remember(activeDraft.id) { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = { if (!controller.isMediaBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, enabled = !controller.isMediaBusy) {
                        Icon(Icons.Filled.Close, interfaceText("关闭预览", "Close preview"), tint = Color.White)
                    }
                    Text(
                        text = "${pagerState.currentPage + 1}/${photos.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { onDelete(activeDraft) }, enabled = !controller.isMediaBusy) {
                        Icon(Icons.Filled.Delete, interfaceText("删除照片", "Delete photo"), tint = Color.White)
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    val draft = photos[page]
                    val file = controller.resolveDraftFile(draft)
                    if (isEditing && page == pagerState.currentPage) {
                        PhotoEditPreview(
                            file = file,
                            imageLoader = imageLoader,
                            cropAspectRatio = cropOption.aspectRatio,
                            rotationDegrees = rotationDegrees.toFloat()
                        )
                    } else {
                        ZoomablePhoto(file = file, imageLoader = imageLoader)
                    }
                }
                Surface(color = Color(0xFF1C1C1E)) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        editError?.let {
                            Text(
                                text = it,
                                color = Color(0xFFFF6961),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        if (isEditing) {
                            Text(
                                text = interfaceText("裁剪与旋转", "Crop and rotate"),
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                PhotoCropOption.entries.forEach { option ->
                                    OutlinedButton(
                                        onClick = { cropOption = option },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (cropOption == option) MediaManagerBlue else Color.White
                                        )
                                    ) {
                                        Text(option.label(), maxLines = 1)
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            TextButton(
                                onClick = { rotationDegrees = (rotationDegrees + 90L) % 360L },
                                enabled = !controller.isMediaBusy
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, tint = Color.White)
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = interfaceText("向右旋转", "Rotate right"),
                                    color = Color.White
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = {
                                        isEditing = false
                                        cropOption = PhotoCropOption.Original
                                        rotationDegrees = 0L
                                        editError = null
                                    },
                                    enabled = !controller.isMediaBusy,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(interfaceText("取消编辑", "Cancel edit"), color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        editError = null
                                        controller.savePhotoEdit(
                                            draftId = activeDraft.id,
                                            cropAspectRatio = cropOption.aspectRatio,
                                            rotationDegrees = rotationDegrees.toInt()
                                        ) { result ->
                                            result.onSuccess {
                                                isEditing = false
                                                cropOption = PhotoCropOption.Original
                                                rotationDegrees = 0L
                                            }.onFailure {
                                                editError = interfaceText("照片编辑失败，已保留原图。", "Photo edit failed. The original was kept.")
                                            }
                                        }
                                    },
                                    enabled = !controller.isMediaBusy,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MediaManagerBlue)
                                ) {
                                    if (controller.isMediaBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                    } else {
                                        Text(interfaceText("保存编辑", "Save edit"))
                                    }
                                }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = {
                                        isEditing = true
                                        editError = null
                                    },
                                    enabled = !controller.isMediaBusy,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, tint = Color.White)
                                    Spacer(Modifier.width(5.dp))
                                    Text(interfaceText("编辑照片", "Edit photo"), color = Color.White)
                                }
                                TextButton(
                                    onClick = { onRetake(activeDraft) },
                                    enabled = !controller.isMediaBusy,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = Color.White)
                                    Spacer(Modifier.width(5.dp))
                                    Text(interfaceText("重新拍摄", "Retake"), color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(file: File?, imageLoader: ImageLoader) {
    var scale by remember(file?.absolutePath) { mutableFloatStateOf(1f) }
    var offset by remember(file?.absolutePath) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(file?.absolutePath) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale <= 1.01f) Offset.Zero else offset + pan
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (file?.isFile != true) {
            MediaPreviewUnavailable(ProofMediaType.Image)
        } else {
            SubcomposeAsyncImage(
                model = file,
                imageLoader = imageLoader,
                contentDescription = interfaceText("原始现场照片", "Original on-site photo"),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                loading = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                },
                error = { MediaPreviewUnavailable(ProofMediaType.Image) }
            )
        }
    }
}

@Composable
private fun PhotoEditPreview(
    file: File?,
    imageLoader: ImageLoader,
    cropAspectRatio: Float?,
    rotationDegrees: Float
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (file?.isFile != true) {
            MediaPreviewUnavailable(ProofMediaType.Image)
        } else {
            val frameModifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .then(if (cropAspectRatio != null) Modifier.aspectRatio(cropAspectRatio) else Modifier)
                .clipToBounds()
            Box(frameModifier, contentAlignment = Alignment.Center) {
                SubcomposeAsyncImage(
                    model = file,
                    imageLoader = imageLoader,
                    contentDescription = interfaceText("照片编辑预览", "Photo edit preview"),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = rotationDegrees },
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    },
                    error = { MediaPreviewUnavailable(ProofMediaType.Image) }
                )
            }
        }
    }
}

@Composable
private fun VideoDraftPreviewDialog(
    draft: SessionMediaDraft,
    file: File?,
    controller: ExerciseSessionController,
    onDismiss: () -> Unit,
    onDelete: (SessionMediaDraft) -> Unit,
    onRetake: (SessionMediaDraft) -> Unit
) {
    var videoView by remember(file?.absolutePath) { mutableStateOf<VideoView?>(null) }
    var mediaPlayer by remember(file?.absolutePath) { mutableStateOf<MediaPlayer?>(null) }
    var durationMillis by remember(file?.absolutePath) {
        mutableLongStateOf(((draft.durationSeconds ?: 0.0) * 1_000.0).toLong())
    }
    var progressMillis by remember(file?.absolutePath) { mutableLongStateOf(0L) }
    var isPlaying by remember(file?.absolutePath) { mutableStateOf(false) }
    var isMuted by remember(file?.absolutePath) { mutableStateOf(false) }
    var isTrimming by remember(file?.absolutePath) { mutableStateOf(false) }
    var trimStart by remember(file?.absolutePath) { mutableFloatStateOf(0f) }
    var trimEnd by remember(file?.absolutePath) { mutableFloatStateOf(1f) }
    var isSelectingCover by remember(file?.absolutePath) { mutableStateOf(false) }
    var coverFraction by remember(file?.absolutePath) { mutableFloatStateOf(0f) }
    var playbackError by remember(file?.absolutePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(draft.coverTimestampMillis, durationMillis, isSelectingCover) {
        if (!isSelectingCover && durationMillis > 0L) {
            coverFraction = ((draft.coverTimestampMillis ?: 0L).toFloat() / durationMillis)
                .coerceIn(0f, 1f)
        }
    }
    LaunchedEffect(isPlaying, durationMillis, videoView) {
        while (isPlaying && durationMillis > 0L) {
            progressMillis = videoView?.currentPosition?.toLong()?.coerceAtLeast(0L) ?: progressMillis
            delay(250L)
        }
    }
    DisposableEffect(file?.absolutePath) {
        onDispose {
            runCatching { videoView?.stopPlayback() }
            mediaPlayer = null
            videoView = null
        }
    }

    Dialog(
        onDismissRequest = { if (!controller.isMediaBusy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, enabled = !controller.isMediaBusy) {
                        Icon(Icons.Filled.Close, interfaceText("关闭视频预览", "Close video preview"), tint = Color.White)
                    }
                    Text(
                        text = interfaceText("现场视频", "On-site video"),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    IconButton(onClick = { onDelete(draft) }, enabled = !controller.isMediaBusy) {
                        Icon(Icons.Filled.Delete, interfaceText("删除视频", "Delete video"), tint = Color.White)
                    }
                }
                if (file?.isFile == true) {
                    key(file.absolutePath) {
                        AndroidView(
                            factory = { androidContext ->
                                VideoView(androidContext).also { view ->
                                    videoView = view
                                    view.setVideoURI(Uri.fromFile(file))
                                    view.setOnPreparedListener { player ->
                                        mediaPlayer = player
                                        durationMillis = player.duration.toLong().coerceAtLeast(0L)
                                        player.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
                                    }
                                    view.setOnCompletionListener {
                                        isPlaying = false
                                        progressMillis = durationMillis
                                    }
                                    view.setOnErrorListener { _, _, _ ->
                                        isPlaying = false
                                        playbackError = interfaceText("视频无法播放，文件可能已损坏。", "The video cannot be played and may be damaged.")
                                        true
                                    }
                                }
                            },
                            update = { view ->
                                mediaPlayer?.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
                                if (isPlaying && !view.isPlaying) {
                                    view.start()
                                } else if (!isPlaying && view.isPlaying) {
                                    view.pause()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) { MediaPreviewUnavailable(ProofMediaType.Video) }
                }
                Surface(color = Color(0xFF1C1C1E)) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (!draft.compressedForUpload) {
                            Surface(
                                color = Color(0xFF3A2D00),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        interfaceText(
                                            "原视频已安全保留，但压缩尚未完成，当前不能上传。",
                                            "The original video is retained, but compression has not completed and it cannot be uploaded."
                                        ),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = { controller.retryVideoCompression(draft.id) },
                                        enabled = !controller.isMediaBusy,
                                        colors = ButtonDefaults.buttonColors(containerColor = MediaManagerBlue)
                                    ) {
                                        if (controller.isMediaBusy) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(interfaceText("重试压缩", "Retry compression"))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        playbackError?.let {
                            Text(it, color = Color(0xFFFF6961), style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                        }
                        if (durationMillis > 0L) {
                            Slider(
                                value = (progressMillis.toFloat() / durationMillis).coerceIn(0f, 1f),
                                onValueChange = { fraction ->
                                    val position = (fraction * durationMillis).toLong()
                                    progressMillis = position
                                    videoView?.seekTo(position.toInt())
                                },
                                enabled = file?.isFile == true && !controller.isMediaBusy
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { isPlaying = !isPlaying },
                                enabled = file?.isFile == true && !controller.isMediaBusy
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) interfaceText("暂停", "Pause") else interfaceText("播放", "Play"),
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = { isMuted = !isMuted },
                                enabled = file?.isFile == true && !controller.isMediaBusy
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Filled.MusicOff else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = if (isMuted) interfaceText("恢复声音", "Unmute") else interfaceText("静音", "Mute"),
                                    tint = Color.White
                                )
                            }
                            Text(
                                text = "${formatMediaDuration(progressMillis / 1_000.0)} / ${formatMediaDuration(durationMillis / 1_000.0)}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                        if (isTrimming) {
                            val minimumRange = if (durationMillis <= 1_000L) 0.05f else {
                                (1_000f / durationMillis).coerceIn(0.01f, 0.5f)
                            }
                            Text(
                                text = interfaceText("裁剪视频首尾", "Trim video start and end"),
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall
                            )
                            RangeSlider(
                                value = trimStart..trimEnd,
                                onValueChange = { range ->
                                    if (range.endInclusive - range.start >= minimumRange) {
                                        trimStart = range.start
                                        trimEnd = range.endInclusive
                                    }
                                },
                                enabled = durationMillis > 0L && !controller.isMediaBusy
                            )
                            Text(
                                text = "${formatMediaDuration(trimStart * durationMillis / 1_000.0)} – ${formatMediaDuration(trimEnd * durationMillis / 1_000.0)}",
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = {
                                        isTrimming = false
                                        trimStart = 0f
                                        trimEnd = 1f
                                    },
                                    enabled = !controller.isMediaBusy,
                                    modifier = Modifier.weight(1f)
                                ) { Text(interfaceText("取消编辑", "Cancel edit"), color = Color.White) }
                                Button(
                                    onClick = {
                                        isPlaying = false
                                        controller.trimVideo(
                                            draftId = draft.id,
                                            startMillis = (trimStart * durationMillis).toLong(),
                                            endMillis = (trimEnd * durationMillis).toLong()
                                        ) { result ->
                                            result.onSuccess {
                                                isTrimming = false
                                                trimStart = 0f
                                                trimEnd = 1f
                                                progressMillis = 0L
                                            }.onFailure {
                                                playbackError = interfaceText("视频裁剪失败，已保留原视频。", "Video trim failed. The original was kept.")
                                            }
                                        }
                                    },
                                    enabled = durationMillis > 0L && !controller.isMediaBusy,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MediaManagerBlue)
                                ) {
                                    if (controller.isMediaBusy) {
                                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                                    } else {
                                        Text(interfaceText("保存编辑", "Save edit"))
                                    }
                                }
                            }
                        } else if (isSelectingCover) {
                            Text(
                                text = interfaceText("选择视频封面", "Choose video cover"),
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Slider(
                                value = coverFraction,
                                onValueChange = { coverFraction = it },
                                enabled = durationMillis > 0L && !controller.isMediaBusy
                            )
                            Text(
                                text = formatMediaDuration(coverFraction * durationMillis / 1_000.0),
                                color = Color.White.copy(alpha = 0.72f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = { isSelectingCover = false },
                                    enabled = !controller.isMediaBusy,
                                    modifier = Modifier.weight(1f)
                                ) { Text(interfaceText("取消", "Cancel"), color = Color.White) }
                                Button(
                                    onClick = {
                                        controller.setVideoCover(
                                            draft.id,
                                            (coverFraction * durationMillis).toLong()
                                        ) { saved ->
                                            if (saved) {
                                                isSelectingCover = false
                                            } else {
                                                playbackError = interfaceText("保存视频封面失败，请稍后重试。", "Could not save the video cover. Try again later.")
                                            }
                                        }
                                    },
                                    enabled = durationMillis > 0L && !controller.isMediaBusy,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MediaManagerBlue)
                                ) { Text(interfaceText("保存封面", "Save cover")) }
                            }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                TextButton(
                                    onClick = { isTrimming = true },
                                    enabled = durationMillis > 0L && !controller.isMediaBusy,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Crop, contentDescription = null, tint = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Text(interfaceText("裁剪", "Trim"), color = Color.White)
                                }
                                TextButton(
                                    onClick = { isSelectingCover = true },
                                    enabled = durationMillis > 0L && !controller.isMediaBusy,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Image, contentDescription = null, tint = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Text(interfaceText("选封面", "Cover"), color = Color.White)
                                }
                                TextButton(
                                    onClick = { onRetake(draft) },
                                    enabled = !controller.isMediaBusy,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White)
                                    Spacer(Modifier.width(4.dp))
                                    Text(interfaceText("重新录制", "Re-record"), color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPreviewUnavailable(type: ProofMediaType) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = if (type == ProofMediaType.Image) Icons.Filled.Image else Icons.Filled.Videocam,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(42.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = interfaceText("文件不存在、损坏或暂时无法加载。", "The file is missing, damaged, or temporarily unavailable."),
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp)
        )
    }
}

private fun formatMediaDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong().coerceAtLeast(0L)
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private fun formatByteCount(byteCount: Long): String {
    return if (byteCount >= 1_000_000L) {
        "%.1f MB".format(byteCount / 1_000_000.0)
    } else {
        "${(byteCount / 1_000L).coerceAtLeast(1L)} KB"
    }
}
