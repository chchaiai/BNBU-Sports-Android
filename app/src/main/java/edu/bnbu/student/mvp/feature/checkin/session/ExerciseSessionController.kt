package edu.bnbu.student.mvp.feature.checkin.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.bnbu.student.mvp.core.local.ExerciseSessionSnapshotStorage
import edu.bnbu.student.mvp.core.local.LocalStoreReadStatus
import edu.bnbu.student.mvp.core.model.ProofMediaType
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ExerciseSessionController(
    snapshotStorage: ExerciseSessionSnapshotStorage,
    mediaRootDirectory: File,
    private val clock: ExerciseClock = SystemExerciseClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val machine = ExerciseSessionMachine(clock)
    private val sessionStore = ExerciseSessionStore(snapshotStorage)
    private val mediaStore = SessionMediaDraftStore(mediaRootDirectory, clock)
    private var boundAccountId: String? = null
    private var bindingGeneration = 0L
    private var persistenceJob: Job? = null

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

    fun bindAccount(accountId: String) {
        val normalized = accountId.trim()
        if (normalized.isEmpty()) {
            val previousAccountId = boundAccountId
            bindingGeneration += 1
            boundAccountId = null
            state = ExerciseSessionState.Idle
            drafts = emptyList()
            isRestoring = false
            if (previousAccountId != null) {
                scope.launch(ioDispatcher) {
                    mediaStore.clearAccount(previousAccountId)
                }
            }
            return
        }
        if (boundAccountId == normalized && !isRestoring) return
        val previousAccountId = boundAccountId
        boundAccountId = normalized
        bindingGeneration += 1
        val generation = bindingGeneration
        state = ExerciseSessionState.Idle
        drafts = emptyList()
        isRestoring = true
        scope.launch {
            if (previousAccountId != null && previousAccountId != normalized) {
                withContext(ioDispatcher) {
                    mediaStore.clearAccount(previousAccountId)
                }
            }
            val restored = withContext(ioDispatcher) { sessionStore.restore(normalized) }
            if (generation != bindingGeneration || boundAccountId != normalized) return@launch
            val normalizedTransition = machine.autoFinishIfNeeded(restored.state)
            state = normalizedTransition.state
            drafts = withContext(ioDispatcher) {
                currentDraftKey(normalized, state)?.let(mediaStore::list).orEmpty()
            }
            if (normalizedTransition.state != restored.state) persistCurrentState()
            if (restored.status == LocalStoreReadStatus.Discarded) {
                message = "无法恢复旧运动会话，已安全清理本地状态。"
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
            onPrepared(Result.failure(IllegalStateException("当前没有可拍摄凭证的运动会话")))
            return
        }
        if (isMediaBusy) {
            onPrepared(Result.failure(IllegalStateException("正在处理上一项媒体文件")))
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
                mediaStore.completeCapture(target, success, durationSeconds)
            }
            refreshDrafts()
            isMediaBusy = false
            message = result.fold(
                onSuccess = {
                    if (it.type == ProofMediaType.Image) "现场照片已保存为本地草稿。" else "现场视频已保存为本地草稿。"
                },
                onFailure = { if (success) it.message ?: "媒体草稿保存失败" else null }
            )
        }
    }

    fun setDraftSelected(draftId: String, selected: Boolean) {
        val key = boundAccountId?.let { currentDraftKey(it, state) } ?: return
        scope.launch {
            val updated = withContext(ioDispatcher) {
                mediaStore.setSelected(key, draftId, selected)
            }
            if (updated) refreshDrafts()
        }
    }

    fun removeDraft(draftId: String) {
        val key = boundAccountId?.let { currentDraftKey(it, state) } ?: return
        scope.launch {
            withContext(ioDispatcher) { mediaStore.remove(key, draftId) }
            refreshDrafts()
        }
    }

    fun updateDescription(value: String) {
        val current = state as? ExerciseSessionState.Finished ?: return
        if (current.details.description == value) return
        state = current.copy(details = current.details.copy(description = value))
        persistCurrentState()
    }

    fun validateSelectedProofs(): Result<List<SessionMediaDraft>> {
        val finished = state as? ExerciseSessionState.Finished
            ?: return Result.failure(IllegalStateException("当前运动尚未结束"))
        if (finished.details.description.isBlank()) {
            return Result.failure(IllegalArgumentException("请填写运动说明"))
        }
        val key = boundAccountId?.let { currentDraftKey(it, state) }
            ?: return Result.failure(IllegalStateException("当前没有待提交的运动会话"))
        return mediaStore.selectedForSubmission(key)
    }

    fun resolveDraftFile(draft: SessionMediaDraft): File? {
        val key = boundAccountId?.let { currentDraftKey(it, state) } ?: return null
        return runCatching { mediaStore.resolveFile(key, draft) }.getOrNull()
    }

    fun abandon() {
        val accountId = boundAccountId ?: return
        val key = currentDraftKey(accountId, state)
        state = ExerciseSessionState.Idle
        drafts = emptyList()
        queuePersistence(accountId, ExerciseSessionState.Idle)
        if (key != null) {
            scope.launch { withContext(ioDispatcher) { mediaStore.clearSession(key) } }
        }
        message = "本次运动会话及本地草稿已清理。"
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

    fun destroy() {
        scope.cancel()
    }

    private fun applyTransition(transition: ExerciseSessionTransition) {
        when (transition) {
            is ExerciseSessionTransition.Rejected -> {
                message = transition.reason
                return
            }

            is ExerciseSessionTransition.TooShort -> message = transition.message
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
                message = "运动会话本地保存失败，请勿关闭应用并稍后重试。"
            }
        }
    }

    private fun refreshDraftsAsync() {
        scope.launch { refreshDrafts() }
    }

    private suspend fun refreshDrafts() {
        val accountId = boundAccountId ?: return
        val key = currentDraftKey(accountId, state)
        drafts = if (key == null) {
            emptyList()
        } else {
            withContext(ioDispatcher) { mediaStore.list(key) }
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
        } ?: return null
        return SessionDraftKey(accountId = accountId, sessionId = sessionId)
    }
}
