package edu.bnbu.student.mvp.feature.checkin.session

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import edu.bnbu.student.mvp.core.local.AndroidAppLocalStore
import edu.bnbu.student.mvp.core.local.LocalStoreReadStatus
import edu.bnbu.student.mvp.core.designsystem.interfaceText
import edu.bnbu.student.mvp.core.model.CreditType
import edu.bnbu.student.mvp.core.model.ProofMediaType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface LocationStatus {
    data object Unknown : LocationStatus
    data object Acquiring : LocationStatus
    data class Acquired(val latitude: Double, val longitude: Double) : LocationStatus
    data object Unavailable : LocationStatus
}

internal class ExerciseSessionController(
    private val localStore: AndroidAppLocalStore,
    mediaRootDirectory: File,
    private val clock: ExerciseClock = SystemExerciseClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val machine = ExerciseSessionMachine(clock)
    private val sessionStore = ExerciseSessionStore(localStore)
    private val mediaStore = SessionMediaDraftStore(mediaRootDirectory, clock)
    private var boundAccountId: String? = null
    private var bindingGeneration = 0L
    private var persistenceJob: Job? = null
    private var locationRequestGeneration = 0L
    private var locationCancellationSource: CancellationTokenSource? = null
    private val _locationStatus = MutableStateFlow<LocationStatus>(LocationStatus.Unknown)

    val locationStatus: StateFlow<LocationStatus> = _locationStatus.asStateFlow()
    var state: ExerciseSessionState by mutableStateOf(ExerciseSessionState.Idle)
        private set

    var drafts: List<SessionMediaDraft> by mutableStateOf(emptyList())
        private set

    var isRestoring: Boolean by mutableStateOf(false)
        private set

    var isMediaBusy: Boolean by mutableStateOf(false)
        private set

    var message: String? by mutableStateOf(null)
        private set

    var shouldShowHealthReminder: Boolean by mutableStateOf(false)
        private set

    /**
     * Binds the controller to an active account.  A pending contact-activation
     * session passes an empty account with [preserveExistingDrafts] so its
     * locally saved exercise work stays private and can be restored only after
     * the server confirms the account is active again.
     */
    fun bindAccount(accountId: String, preserveExistingDrafts: Boolean = false) {
        val normalized = accountId.trim()
        if (normalized.isEmpty()) {
            val previousAccountId = boundAccountId
            bindingGeneration += 1
            boundAccountId = null
            state = ExerciseSessionState.Idle
            drafts = emptyList()
            isRestoring = false
            shouldShowHealthReminder = false
            resetLocationStatus()
            if (previousAccountId != null && !preserveExistingDrafts) {
                scope.launch(ioDispatcher) {
                    runCatching { mediaStore.clearAccount(previousAccountId) }
                }
            }
            return
        }
        if (boundAccountId == normalized && !isRestoring) return
        val previousAccountId = boundAccountId
        boundAccountId = normalized
        shouldShowHealthReminder = !localStore.hasShownHealthReminder(normalized)
        bindingGeneration += 1
        val generation = bindingGeneration
        state = ExerciseSessionState.Idle
        drafts = emptyList()
        isRestoring = true
        resetLocationStatus()
        scope.launch {
            if (previousAccountId != null && previousAccountId != normalized) {
                withContext(ioDispatcher) {
                    runCatching { mediaStore.clearAccount(previousAccountId) }
                }
            }
            val restored = withContext(ioDispatcher) { sessionStore.restore(normalized) }
            if (generation != bindingGeneration || boundAccountId != normalized) return@launch
            val normalizedTransition = machine.autoFinishIfNeeded(restored.state)
            state = normalizedTransition.state
            val restoredDrafts = withContext(ioDispatcher) {
                runCatching {
                    currentDraftKey(normalized, state)?.let(mediaStore::list).orEmpty()
                }
            }
            drafts = restoredDrafts.getOrDefault(emptyList())
            if (restoredDrafts.isFailure && restored.status != LocalStoreReadStatus.Discarded) {
                message = interfaceText("媒体草稿恢复失败，原始文件未被修改。", "Could not restore media drafts. Original files were not changed.")
            }
            if (normalizedTransition.state != restored.state) persistCurrentState()
            if (restored.status == LocalStoreReadStatus.Discarded) {
                message = interfaceText("无法恢复旧运动会话，已安全清理本地状态。", "Could not restore the previous exercise session. Local state was safely cleared.")
            }
            isRestoring = false
        }
    }

    fun start(details: ExerciseSessionDetails) {
        applyTransition(
            machine.start(
                state = state,
                sessionId = UUID.randomUUID().toString(),
                details = details
            )
        )
    }

    fun pause() {
        applyTransition(machine.pause(state))
    }

    /** Attempts to obtain a one-off location for the active exercise session. */
    fun requestLocation(context: Context) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation && !hasCoarseLocation) {
            _locationStatus.value = LocationStatus.Unavailable
            return
        }

        locationCancellationSource?.cancel()
        val requestGeneration = ++locationRequestGeneration
        val cancellationSource = CancellationTokenSource()
        locationCancellationSource = cancellationSource
        _locationStatus.value = LocationStatus.Acquiring

        runCatching {
            LocationServices.getFusedLocationProviderClient(context.applicationContext)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationSource.token)
                .addOnSuccessListener { location ->
                    if (requestGeneration != locationRequestGeneration) return@addOnSuccessListener
                    _locationStatus.value = location?.let {
                        LocationStatus.Acquired(it.latitude, it.longitude)
                    } ?: LocationStatus.Unavailable
                }
                .addOnFailureListener {
                    if (requestGeneration == locationRequestGeneration) {
                        _locationStatus.value = LocationStatus.Unavailable
                    }
                }
                .addOnCanceledListener {
                    if (requestGeneration == locationRequestGeneration) {
                        _locationStatus.value = LocationStatus.Unavailable
                    }
                }
        }.onFailure {
            if (requestGeneration == locationRequestGeneration) {
                _locationStatus.value = LocationStatus.Unavailable
            }
        }
    }

    fun resume() {
        applyTransition(machine.resume(state))
    }

    fun requestFinish() {
        applyTransition(machine.requestFinish(state))
    }

    fun autoFinishIfNeeded() {
        val transition = machine.autoFinishIfNeeded(state)
        if (transition.state != state) {
            applyTransition(transition)
        }
    }

    fun prepareCapture(
        type: ProofMediaType,
        onPrepared: (Result<SessionCaptureTarget>) -> Unit
    ) {
        val accountId = boundAccountId
        val key = accountId?.let { currentDraftKey(it, state) }
        if (key == null) {
            onPrepared(Result.failure(IllegalStateException(interfaceText("当前没有可拍摄凭证的运动会话", "There is no exercise session available for capturing proof."))))
            return
        }
        if (isMediaBusy) {
            onPrepared(Result.failure(IllegalStateException(interfaceText("正在处理上一项媒体文件", "The previous media file is still being processed."))))
            return
        }
        isMediaBusy = true
        scope.launch {
            val result = withContext(ioDispatcher) { mediaStore.prepareCapture(key, type) }
            isMediaBusy = false
            onPrepared(result)
        }
    }

    fun completeCapture(
        target: SessionCaptureTarget,
        success: Boolean,
        durationSeconds: Double? = null
    ) {
        if (isMediaBusy) return
        isMediaBusy = true
        scope.launch {
            val result = withContext(ioDispatcher) {
                val resolvedDuration = durationSeconds ?: if (success && target.type == ProofMediaType.Video) {
                    SessionMediaEditor.readVideoDurationSeconds(target.file)
                } else {
                    null
                }
                mediaStore.completeCapture(target, success, resolvedDuration)
            }
            refreshDrafts()
            isMediaBusy = false
            message = result.fold(
                onSuccess = {
                    if (it.type == ProofMediaType.Image) interfaceText("现场照片已保存为本地草稿。", "On-site photo saved as a local draft.") else interfaceText("现场视频已保存为本地草稿。", "On-site video saved as a local draft.")
                },
                onFailure = {
                    if (!success) {
                        null
                    } else if (target.type == ProofMediaType.Image) {
                        interfaceText("现场照片保存失败，请重试。", "Could not save the on-site photo. Try again.")
                    } else {
                        interfaceText("现场视频保存失败，请重试。", "Could not save the on-site video. Try again.")
                    }
                }
            )
        }
    }

    fun setDraftSelected(draftId: String, selected: Boolean) {
        val key = boundAccountId?.let { currentDraftKey(it, state) } ?: return
        scope.launch {
            val updated = withContext(ioDispatcher) {
                runCatching { mediaStore.setSelected(key, draftId, selected) }.getOrDefault(false)
            }
            if (updated) {
                refreshDrafts()
            } else {
                message = interfaceText("更新凭证选择失败，请稍后重试。", "Could not update the proof selection. Try again later.")
            }
        }
    }

    fun removeDraft(draftId: String) {
        val key = boundAccountId?.let { currentDraftKey(it, state) } ?: return
        if (isMediaBusy) {
            message = interfaceText("正在处理上一项媒体文件", "The previous media file is still being processed.")
            return
        }
        isMediaBusy = true
        scope.launch {
            val removed = withContext(ioDispatcher) {
                runCatching { mediaStore.remove(key, draftId) }.getOrDefault(false)
            }
            refreshDrafts()
            isMediaBusy = false
            if (!removed) {
                message = interfaceText("删除媒体草稿失败，请稍后重试。", "Could not delete the media draft. Try again later.")
            }
        }
    }

    /** Opens a safe staging target for a replacement camera capture. */
    fun prepareReplacementCapture(
        draftId: String,
        onPrepared: (Result<SessionMediaFileUpdateTarget>) -> Unit
    ) {
        val key = boundAccountId?.let { currentDraftKey(it, state) }
        if (key == null) {
            onPrepared(Result.failure(IllegalStateException(interfaceText("当前没有可替换的媒体草稿", "There is no media draft available to replace."))))
            return
        }
        if (isMediaBusy) {
            onPrepared(Result.failure(IllegalStateException(interfaceText("正在处理上一项媒体文件", "The previous media file is still being processed."))))
            return
        }
        isMediaBusy = true
        scope.launch {
            val result = withContext(ioDispatcher) { mediaStore.prepareReplacement(key, draftId) }
            isMediaBusy = false
            onPrepared(result)
        }
    }

    /** Completes a replacement only after the system camera has produced a valid new file. */
    fun completeReplacementCapture(
        target: SessionMediaFileUpdateTarget,
        success: Boolean
    ) {
        if (isMediaBusy) return
        isMediaBusy = true
        scope.launch {
            val result = withContext(ioDispatcher) {
                if (!success) {
                    runCatching { mediaStore.cancelFileUpdate(target) }
                    Result.failure<SessionMediaDraft>(IllegalStateException("Capture cancelled"))
                } else {
                    val duration = if (target.type == ProofMediaType.Video) {
                        SessionMediaEditor.readVideoDurationSeconds(target.file)
                    } else {
                        null
                    }
                    mediaStore.commitFileUpdate(target, duration)
                }
            }
            refreshDrafts()
            isMediaBusy = false
            message = result.fold(
                onSuccess = {
                    if (it.type == ProofMediaType.Image) {
                        interfaceText("现场照片已替换。", "On-site photo replaced.")
                    } else {
                        interfaceText("现场视频已替换。", "On-site video replaced.")
                    }
                },
                onFailure = {
                    if (success) interfaceText("替换媒体失败，已保留原内容。", "Could not replace the media. The original was kept.") else null
                }
            )
        }
    }

    /** Saves a photo edit to a staging file and commits it only after processing succeeds. */
    fun savePhotoEdit(
        draftId: String,
        cropAspectRatio: Float?,
        rotationDegrees: Int,
        onCompleted: (Result<SessionMediaDraft>) -> Unit = {}
    ) {
        val key = boundAccountId?.let { currentDraftKey(it, state) }
        if (key == null) {
            onCompleted(Result.failure(IllegalStateException(interfaceText("当前没有可编辑的媒体草稿", "There is no media draft available to edit."))))
            return
        }
        if (isMediaBusy) {
            onCompleted(Result.failure(IllegalStateException(interfaceText("正在处理上一项媒体文件", "The previous media file is still being processed."))))
            return
        }
        isMediaBusy = true
        scope.launch {
            val result = withContext(ioDispatcher) {
                mediaStore.prepareEdit(key, draftId).fold(
                    onSuccess = { target ->
                        SessionMediaEditor.saveEditedPhoto(
                            source = target.sourceFile,
                            destination = target.file,
                            cropAspectRatio = cropAspectRatio,
                            rotationDegrees = rotationDegrees
                        ).fold(
                            onSuccess = { mediaStore.commitFileUpdate(target) },
                            onFailure = { error ->
                                runCatching { mediaStore.cancelFileUpdate(target) }
                                Result.failure(error)
                            }
                        )
                    },
                    onFailure = { Result.failure(it) }
                )
            }
            refreshDrafts()
            isMediaBusy = false
            message = result.fold(
                onSuccess = { interfaceText("照片编辑已保存。", "Photo edit saved.") },
                onFailure = { interfaceText("照片编辑失败，已保留原图。", "Photo edit failed. The original was kept.") }
            )
            onCompleted(result)
        }
    }

    /** Saves a trimmed MP4 to a staging file and retains the original on any failure. */
    fun trimVideo(
        draftId: String,
        startMillis: Long,
        endMillis: Long,
        onCompleted: (Result<SessionMediaDraft>) -> Unit = {}
    ) {
        val key = boundAccountId?.let { currentDraftKey(it, state) }
        if (key == null) {
            onCompleted(Result.failure(IllegalStateException(interfaceText("当前没有可编辑的媒体草稿", "There is no media draft available to edit."))))
            return
        }
        if (isMediaBusy) {
            onCompleted(Result.failure(IllegalStateException(interfaceText("正在处理上一项媒体文件", "The previous media file is still being processed."))))
            return
        }
        isMediaBusy = true
        scope.launch {
            val result = withContext(ioDispatcher) {
                mediaStore.prepareEdit(key, draftId).fold(
                    onSuccess = { target ->
                        if (target.type != ProofMediaType.Video) {
                            mediaStore.cancelFileUpdate(target)
                            Result.failure(
                                IllegalArgumentException(
                                    interfaceText("所选素材不是视频。", "The selected media is not a video.")
                                )
                            )
                        } else {
                            SessionMediaEditor.trimVideo(
                                source = target.sourceFile,
                                destination = target.file,
                                startMillis = startMillis,
                                endMillis = endMillis
                            ).fold(
                                onSuccess = {
                                    mediaStore.commitFileUpdate(
                                        target,
                                        SessionMediaEditor.readVideoDurationSeconds(target.file)
                                    )
                                },
                                onFailure = { error ->
                                    runCatching { mediaStore.cancelFileUpdate(target) }
                                    Result.failure(error)
                                }
                            )
                        }
                    },
                    onFailure = { Result.failure(it) }
                )
            }
            refreshDrafts()
            isMediaBusy = false
            message = result.fold(
                onSuccess = { interfaceText("视频裁剪已保存。", "Video trim saved.") },
                onFailure = { interfaceText("视频裁剪失败，已保留原视频。", "Video trim failed. The original was kept.") }
            )
            onCompleted(result)
        }
    }

    fun setVideoCover(
        draftId: String,
        timestampMillis: Long,
        onCompleted: (Boolean) -> Unit = {}
    ) {
        val key = boundAccountId?.let { currentDraftKey(it, state) } ?: run {
            onCompleted(false)
            return
        }
        if (isMediaBusy) {
            onCompleted(false)
            return
        }
        isMediaBusy = true
        scope.launch {
            val updated = withContext(ioDispatcher) {
                runCatching { mediaStore.setVideoCover(key, draftId, timestampMillis) }.getOrDefault(false)
            }
            if (updated) refreshDrafts()
            isMediaBusy = false
            if (!updated) {
                message = interfaceText("保存视频封面失败，请稍后重试。", "Could not save the video cover. Try again later.")
            }
            onCompleted(updated)
        }
    }

    fun reorderPhotos(orderedDraftIds: List<String>) {
        val key = boundAccountId?.let { currentDraftKey(it, state) } ?: return
        if (isMediaBusy) return
        isMediaBusy = true
        scope.launch {
            val updated = withContext(ioDispatcher) {
                runCatching { mediaStore.reorderImages(key, orderedDraftIds) }.getOrDefault(false)
            }
            if (updated) refreshDrafts()
            isMediaBusy = false
            if (!updated) {
                message = interfaceText("调整照片顺序失败，请稍后重试。", "Could not reorder photos. Try again later.")
            }
        }
    }

    fun updateDescription(value: String) {
        val current = state as? ExerciseSessionState.Finished ?: return
        val truncatedValue = truncateExerciseDescription(value)
        if (current.details.description == truncatedValue) return
        state = current.copy(details = current.details.copy(description = truncatedValue))
        persistCurrentState()
    }

    fun updateRemark(value: String) {
        val current = state as? ExerciseSessionState.Finished ?: return
        val truncatedValue = truncateExerciseRemark(value)
        if (current.details.remark == truncatedValue) return
        state = current.copy(details = current.details.copy(remark = truncatedValue))
        persistCurrentState()
    }

    fun validateSelectedProofs(): Result<List<SessionMediaDraft>> {
        if (isMediaBusy) {
            return Result.failure(IllegalStateException(interfaceText("媒体仍在处理中，请稍后再提交。", "Media is still being processed. Try submitting again shortly.")))
        }
        val finished = state as? ExerciseSessionState.Finished
            ?: return Result.failure(IllegalStateException(interfaceText("当前运动尚未结束", "The current exercise has not ended.")))
        val requiresDescription = finished.details.creditType == CreditType.General
        if (requiresDescription && finished.details.description.isBlank()) {
            return Result.failure(IllegalArgumentException(interfaceText("请填写运动说明", "Enter exercise details.")))
        }
        if (
            requiresDescription &&
            finished.details.description.length > MaxExerciseDescriptionLength
        ) {
            return Result.failure(
                IllegalArgumentException(interfaceText("运动说明不能超过 $MaxExerciseDescriptionLength 个字符", "Exercise details cannot exceed $MaxExerciseDescriptionLength characters."))
            )
        }
        if (finished.details.remark.length > MaxExerciseRemarkLength) {
            return Result.failure(
                IllegalArgumentException(interfaceText("备注不能超过 $MaxExerciseRemarkLength 个字符", "Notes cannot exceed $MaxExerciseRemarkLength characters."))
            )
        }
        val key = boundAccountId?.let { currentDraftKey(it, state) }
            ?: return Result.failure(IllegalStateException(interfaceText("当前没有待提交的运动会话", "There is no exercise session ready to submit.")))
        return mediaStore.selectedForSubmission(key)
    }

    fun resolveDraftFile(draft: SessionMediaDraft): File? {
        val key = boundAccountId?.let { currentDraftKey(it, state) } ?: return null
        return runCatching { mediaStore.resolveFile(key, draft) }.getOrNull()
    }

    /** Clears durable state and local media after a successful server submission. */
    fun markSubmitted(summary: SubmissionSummary) {
        val finished = state as? ExerciseSessionState.Finished ?: return
        val accountId = boundAccountId ?: return
        val key = currentDraftKey(accountId, finished)
        state = ExerciseSessionState.Submitted(
            creditedHours = finished.creditedHours,
            summary = summary
        )
        drafts = emptyList()
        queuePersistence(accountId, ExerciseSessionState.Idle)
        if (key != null) {
            scope.launch { withContext(ioDispatcher) { runCatching { mediaStore.clearSession(key) } } }
        }
    }

    fun resetAfterSubmission() {
        if (state !is ExerciseSessionState.Submitted) return
        state = ExerciseSessionState.Idle
        message = null
    }

    fun abandon() {
        if (isMediaBusy) {
            message = interfaceText("媒体仍在处理中，请稍后再放弃本次运动。", "Media is still being processed. Try discarding this exercise again shortly.")
            return
        }
        clearCurrentSession(interfaceText("本次运动会话及本地草稿已清理。", "This exercise session and its local drafts were cleared."))
    }

    private fun clearCurrentSession(completionMessage: String) {
        val accountId = boundAccountId
        val key = accountId?.let { currentDraftKey(it, state) }
        state = ExerciseSessionState.Idle
        drafts = emptyList()
        resetLocationStatus()
        if (accountId != null) {
            queuePersistence(accountId, ExerciseSessionState.Idle)
        }
        if (key != null) {
            scope.launch { withContext(ioDispatcher) { runCatching { mediaStore.clearSession(key) } } }
        }
        message = completionMessage
    }

    fun debugAddActiveDuration(durationMillis: Long) {
        if (durationMillis <= 0L) return
        state = when (val current = state) {
            is ExerciseSessionState.Active -> current.copy(
                startedAtEpochMillis = current.startedAtEpochMillis - durationMillis,
                activeSegmentStartedAtEpochMillis = current.activeSegmentStartedAtEpochMillis -
                    durationMillis
            )

            is ExerciseSessionState.Paused -> current.copy(
                startedAtEpochMillis = current.startedAtEpochMillis - durationMillis,
                accumulatedActiveMillis = (current.accumulatedActiveMillis + durationMillis)
                    .coerceAtMost(MaximumExerciseMillis - 1L)
            )

            else -> current
        }
        persistCurrentState()
        autoFinishIfNeeded()
    }

    fun consumeMessage() {
        message = null
    }

    fun dismissHealthReminder() {
        val accountId = boundAccountId ?: return
        localStore.markHealthReminderShown(accountId)
        shouldShowHealthReminder = false
    }

    fun destroy() {
        locationCancellationSource?.cancel()
        scope.cancel()
    }

    private fun applyTransition(transition: ExerciseSessionTransition) {
        when (transition) {
            is ExerciseSessionTransition.Rejected -> {
                message = transition.reason
                return
            }

            is ExerciseSessionTransition.Discarded -> {
                clearCurrentSession(transition.message)
                return
            }
            is ExerciseSessionTransition.Changed -> Unit
        }
        state = transition.state
        persistCurrentState()
        refreshDraftsAsync()
    }

    private fun persistCurrentState() {
        val accountId = boundAccountId ?: return
        queuePersistence(accountId, state)
    }

    private fun queuePersistence(accountId: String, stateSnapshot: ExerciseSessionState) {
        val previous = persistenceJob
        persistenceJob = scope.launch {
            previous?.join()
            val saved = withContext(ioDispatcher) {
                sessionStore.save(accountId, stateSnapshot)
            }
            if (!saved && boundAccountId == accountId) {
                message = interfaceText("运动会话本地保存失败，请勿关闭应用并稍后重试。", "Could not save the exercise session locally. Keep the app open and try again later.")
            }
        }
    }

    private fun refreshDraftsAsync() {
        scope.launch { refreshDrafts() }
    }

    private fun resetLocationStatus() {
        locationRequestGeneration += 1
        locationCancellationSource?.cancel()
        locationCancellationSource = null
        _locationStatus.value = LocationStatus.Unknown
    }

    private suspend fun refreshDrafts() {
        val accountId = boundAccountId ?: return
        val key = currentDraftKey(accountId, state)
        if (key == null) {
            drafts = emptyList()
            return
        }
        val loaded = withContext(ioDispatcher) { runCatching { mediaStore.list(key) } }
        loaded.onSuccess { drafts = it }
            .onFailure {
                message = interfaceText("读取媒体草稿失败，请稍后重试。", "Could not read media drafts. Try again later.")
            }
    }

    private fun currentDraftKey(
        accountId: String,
        state: ExerciseSessionState
    ): SessionDraftKey? {
        val sessionId = when (state) {
            ExerciseSessionState.Idle -> null
            is ExerciseSessionState.Active -> state.sessionId
            is ExerciseSessionState.Paused -> state.sessionId
            is ExerciseSessionState.Finished -> state.sessionId
            is ExerciseSessionState.Submitted -> null
        } ?: return null
        return SessionDraftKey(accountId = accountId, sessionId = sessionId)
    }
}
