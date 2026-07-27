package edu.bnbu.student.mvp.feature.checkin.session

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import edu.bnbu.student.mvp.core.model.ProofMediaType
import edu.bnbu.student.mvp.core.model.ProofUploadRule
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

internal data class SessionDraftKey(
    val accountId: String,
    val sessionId: String
)

internal enum class SessionMediaDraftStatus {
    PendingCapture,
    Ready
}

internal data class SessionMediaDraft(
    val id: String,
    val type: ProofMediaType,
    val fileName: String,
    val capturedAtEpochMillis: Long,
    val byteCount: Long,
    val durationSeconds: Double? = null,
    val selected: Boolean = false,
    val status: SessionMediaDraftStatus = SessionMediaDraftStatus.Ready
)

internal data class SessionCaptureTarget(
    val key: SessionDraftKey,
    val draftId: String,
    val type: ProofMediaType,
    val file: File
)

internal class SessionMediaDraftStore(
    private val rootDirectory: File,
    private val clock: ExerciseClock = SystemExerciseClock,
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
) {
    init {
        require(rootDirectory.path.isNotBlank()) { "草稿根目录不能为空" }
    }

    @Synchronized
    fun prepareCapture(
        key: SessionDraftKey,
        type: ProofMediaType
    ): Result<SessionCaptureTarget> = runCatching {
        validateKey(key)
        val index = readAndRecoverIndex(key)
        enforceAvailableSlot(index.drafts, type)
        val id = UUID.randomUUID().toString()
        val extension = if (type == ProofMediaType.Image) "jpg" else "mp4"
        val prefix = if (type == ProofMediaType.Image) "photo" else "video"
        val fileName = "${prefix}_${id}.$extension"
        val directory = sessionDirectory(key).also { directory ->
            check(directory.mkdirs() || directory.isDirectory) { "无法创建媒体草稿目录" }
        }
        val file = File(directory, fileName)
        check(file.createNewFile()) { "无法创建媒体草稿文件" }
        val pending = SessionMediaDraft(
            id = id,
            type = type,
            fileName = fileName,
            capturedAtEpochMillis = clock.nowEpochMillis(),
            byteCount = 0L,
            status = SessionMediaDraftStatus.PendingCapture
        )
        if (!writeIndex(key, index.copy(drafts = index.drafts + pending))) {
            file.delete()
            error("无法保存媒体草稿索引")
        }
        SessionCaptureTarget(key = key, draftId = id, type = type, file = file)
    }

    @Synchronized
    fun completeCapture(
        target: SessionCaptureTarget,
        success: Boolean,
        durationSeconds: Double? = null
    ): Result<SessionMediaDraft> = runCatching {
        validateKey(target.key)
        val expectedDirectory = sessionDirectory(target.key).canonicalFile
        check(target.file.canonicalFile.parentFile == expectedDirectory) {
            "媒体文件不属于当前运动会话"
        }
        val index = readIndex(target.key)
        val pending = index.drafts.firstOrNull {
            it.id == target.draftId && it.status == SessionMediaDraftStatus.PendingCapture
        } ?: error("找不到待完成的媒体草稿")
        check(pending.type == target.type && pending.fileName == target.file.name) {
            "媒体草稿信息不一致"
        }
        if (!success) {
            removeDraftInternal(target.key, index, pending)
            error("拍摄已取消")
        }
        val actualBytes = target.file.length()
        validateCapturedFile(target.type, actualBytes)
        val ready = pending.copy(
            byteCount = actualBytes,
            durationSeconds = durationSeconds?.takeIf { it >= 0.0 },
            status = SessionMediaDraftStatus.Ready
        )
        check(writeIndex(target.key, index.copy(
            drafts = index.drafts.map { if (it.id == ready.id) ready else it }
        ))) { "无法更新媒体草稿索引" }
        ready
    }.onFailure {
        if (!success || target.file.length() <= 0L || target.file.length() > maxBytesFor(target.type)) {
            cancelCapture(target)
        }
    }

    @Synchronized
    fun list(key: SessionDraftKey): List<SessionMediaDraft> {
        validateKey(key)
        return readAndRecoverIndex(key).drafts.filter {
            it.status == SessionMediaDraftStatus.Ready
        }
    }

    @Synchronized
    fun setSelected(
        key: SessionDraftKey,
        draftId: String,
        selected: Boolean
    ): Boolean {
        validateKey(key)
        val index = readAndRecoverIndex(key)
        val draft = index.drafts.firstOrNull {
            it.id == draftId && it.status == SessionMediaDraftStatus.Ready
        } ?: return false
        val updated = index.copy(drafts = index.drafts.map {
            if (it.id == draft.id) it.copy(selected = selected) else it
        })
        return writeIndex(key, updated)
    }

    @Synchronized
    fun selectedForSubmission(key: SessionDraftKey): Result<List<SessionMediaDraft>> = runCatching {
        val selected = list(key).filter { it.selected }
        check(selected.isNotEmpty()) { "请至少选择 1 张照片或 1 个视频作为打卡凭证" }
        val imageCount = selected.count { it.type == ProofMediaType.Image }
        val videoCount = selected.count { it.type == ProofMediaType.Video }
        check(imageCount <= ProofUploadRule.maxImageCount) {
            "最多选择 ${ProofUploadRule.maxImageCount} 张照片"
        }
        check(videoCount <= ProofUploadRule.maxVideoCount) {
            "最多选择 ${ProofUploadRule.maxVideoCount} 个视频"
        }
        selected.forEach { draft ->
            val file = resolveFile(key, draft)
            check(file.isFile && file.length() == draft.byteCount) { "凭证文件不存在或已发生变化" }
            validateCapturedFile(draft.type, file.length())
        }
        selected
    }

    @Synchronized
    fun remove(key: SessionDraftKey, draftId: String): Boolean {
        validateKey(key)
        val index = readIndex(key)
        val draft = index.drafts.firstOrNull { it.id == draftId } ?: return false
        return removeDraftInternal(key, index, draft)
    }

    @Synchronized
    fun cancelCapture(target: SessionCaptureTarget): Boolean {
        validateKey(target.key)
        val index = readIndex(target.key)
        val draft = index.drafts.firstOrNull { it.id == target.draftId }
        return if (draft == null) {
            safeDelete(target.file, sessionDirectory(target.key))
        } else {
            removeDraftInternal(target.key, index, draft)
        }
    }

    @Synchronized
    fun clearSession(key: SessionDraftKey): Boolean {
        validateKey(key)
        val directory = sessionDirectory(key)
        return !directory.exists() || directory.deleteRecursively()
    }

    @Synchronized
    fun clearAccount(accountId: String): Boolean {
        require(accountId.isNotBlank()) { "账号不能为空" }
        val directory = File(rootDirectory, stableHash(accountId.trim()))
        check(isWithinRoot(directory)) { "账号草稿目录越界" }
        return !directory.exists() || directory.deleteRecursively()
    }

    @Synchronized
    fun cleanupExpiredOrphans(
        activeKeys: Set<SessionDraftKey>,
        retentionMillis: Long = DefaultOrphanRetentionMillis
    ): Int {
        require(retentionMillis >= 0L) { "保留时间不能为负数" }
        if (!rootDirectory.isDirectory) return 0
        val activePaths = activeKeys.map { sessionDirectory(it).canonicalPath }.toSet()
        val cutoff = clock.nowEpochMillis() - retentionMillis
        var removed = 0
        rootDirectory.listFiles()?.filter { it.isDirectory }?.forEach { accountDirectory ->
            accountDirectory.listFiles()?.filter { it.isDirectory }?.forEach { sessionDirectory ->
                val canonical = sessionDirectory.canonicalPath
                if (
                    canonical !in activePaths &&
                    isWithinRoot(sessionDirectory) &&
                    sessionDirectory.lastModified() <= cutoff &&
                    sessionDirectory.deleteRecursively()
                ) {
                    removed += 1
                }
            }
            if (accountDirectory.listFiles().isNullOrEmpty()) accountDirectory.delete()
        }
        return removed
    }

    fun resolveFile(key: SessionDraftKey, draft: SessionMediaDraft): File {
        validateKey(key)
        require(isSafeFileName(draft.fileName)) { "媒体文件名无效" }
        val directory = sessionDirectory(key)
        return File(directory, draft.fileName).also { file ->
            check(file.canonicalFile.parentFile == directory.canonicalFile) { "媒体文件路径越界" }
        }
    }

    private fun readAndRecoverIndex(key: SessionDraftKey): SessionMediaDraftIndex {
        val index = readIndex(key)
        val now = clock.nowEpochMillis()
        var changed = false
        val recovered = index.drafts.mapNotNull { draft ->
            val file = runCatching { resolveFile(key, draft) }.getOrNull()
            if (file == null) {
                changed = true
                return@mapNotNull null
            }
            when (draft.status) {
                SessionMediaDraftStatus.Ready -> {
                    if (!file.isFile || file.length() <= 0L || file.length() > maxBytesFor(draft.type)) {
                        safeDelete(file, sessionDirectory(key))
                        changed = true
                        null
                    } else if (draft.byteCount != file.length()) {
                        changed = true
                        draft.copy(byteCount = file.length())
                    } else {
                        draft
                    }
                }

                SessionMediaDraftStatus.PendingCapture -> {
                    when {
                        file.isFile && file.length() > 0L && file.length() <= maxBytesFor(draft.type) -> {
                            changed = true
                            draft.copy(
                                byteCount = file.length(),
                                status = SessionMediaDraftStatus.Ready
                            )
                        }

                        now - draft.capturedAtEpochMillis >= PendingCaptureRetentionMillis -> {
                            safeDelete(file, sessionDirectory(key))
                            changed = true
                            null
                        }

                        else -> draft
                    }
                }
            }
        }.distinctBy { it.id }
        if (recovered.size != index.drafts.size) changed = true
        val result = index.copy(drafts = recovered)
        if (changed) writeIndex(key, result)
        return result
    }

    private fun readIndex(key: SessionDraftKey): SessionMediaDraftIndex {
        val file = indexFile(key)
        if (!file.isFile) return SessionMediaDraftIndex()
        return try {
            val index = gson.fromJson(file.readText(Charsets.UTF_8), SessionMediaDraftIndex::class.java)
            if (index == null || index.schemaVersion != SessionMediaDraftIndex.CurrentSchemaVersion) {
                SessionMediaDraftIndex()
            } else {
                index.copy(drafts = index.drafts.orEmpty())
            }
        } catch (_: RuntimeException) {
            SessionMediaDraftIndex()
        }
    }

    private fun writeIndex(key: SessionDraftKey, index: SessionMediaDraftIndex): Boolean {
        val directory = sessionDirectory(key)
        if (!directory.mkdirs() && !directory.isDirectory) return false
        val destination = indexFile(key)
        val temporary = File(directory, "$IndexFileName.tmp")
        return try {
            temporary.writeText(gson.toJson(index), Charsets.UTF_8)
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
            directory.setLastModified(clock.nowEpochMillis())
            true
        } catch (_: Exception) {
            temporary.delete()
            false
        }
    }

    private fun removeDraftInternal(
        key: SessionDraftKey,
        index: SessionMediaDraftIndex,
        draft: SessionMediaDraft
    ): Boolean {
        val fileDeleted = runCatching {
            safeDelete(resolveFile(key, draft), sessionDirectory(key))
        }.getOrDefault(false)
        val remaining = index.drafts.filterNot { it.id == draft.id }
        val indexUpdated = writeIndex(key, index.copy(drafts = remaining))
        if (remaining.isEmpty()) {
            indexFile(key).delete()
            sessionDirectory(key).delete()
        }
        return fileDeleted && indexUpdated
    }

    private fun enforceAvailableSlot(
        drafts: List<SessionMediaDraft>,
        type: ProofMediaType
    ) {
        val count = drafts.count { it.type == type }
        val limit = if (type == ProofMediaType.Image) {
            ProofUploadRule.maxImageCount
        } else {
            ProofUploadRule.maxVideoCount
        }
        check(count < limit) {
            if (type == ProofMediaType.Image) {
                "运动过程中最多拍摄 $limit 张照片草稿"
            } else {
                "每次运动最多拍摄 $limit 个视频草稿"
            }
        }
    }

    private fun validateCapturedFile(type: ProofMediaType, byteCount: Long) {
        check(byteCount > 0L) { "拍摄文件为空" }
        check(byteCount <= maxBytesFor(type)) {
            if (type == ProofMediaType.Image) "照片超过 8MB" else "视频超过 100MB"
        }
    }

    private fun maxBytesFor(type: ProofMediaType): Long {
        return if (type == ProofMediaType.Image) {
            ProofUploadRule.maxImageBytes.toLong()
        } else {
            ProofUploadRule.maxVideoBytes.toLong()
        }
    }

    private fun validateKey(key: SessionDraftKey) {
        require(key.accountId.isNotBlank()) { "账号不能为空" }
        require(key.sessionId.isNotBlank()) { "运动会话编号不能为空" }
    }

    private fun sessionDirectory(key: SessionDraftKey): File {
        validateKey(key)
        val accountDirectory = File(rootDirectory, stableHash(key.accountId.trim()))
        val directory = File(accountDirectory, stableHash(key.sessionId.trim()))
        check(isWithinRoot(directory)) { "媒体草稿目录越界" }
        return directory
    }

    private fun indexFile(key: SessionDraftKey): File = File(sessionDirectory(key), IndexFileName)

    private fun isWithinRoot(file: File): Boolean {
        val rootPath = rootDirectory.canonicalFile.toPath()
        return file.canonicalFile.toPath().startsWith(rootPath)
    }

    private fun safeDelete(file: File, expectedDirectory: File): Boolean {
        if (file.canonicalFile.parentFile != expectedDirectory.canonicalFile) return false
        return !file.exists() || file.delete()
    }

    private fun isSafeFileName(fileName: String): Boolean {
        return fileName.isNotBlank() &&
            fileName == File(fileName).name &&
            !fileName.contains("..")
    }

    private fun stableHash(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private data class SessionMediaDraftIndex(
        val schemaVersion: Int = CurrentSchemaVersion,
        val drafts: List<SessionMediaDraft> = emptyList()
    ) {
        companion object {
            const val CurrentSchemaVersion = 1
        }
    }

    companion object {
        const val DefaultOrphanRetentionMillis = 7L * 24L * 60L * 60L * 1_000L
        private const val PendingCaptureRetentionMillis = 60L * 60L * 1_000L
        private const val IndexFileName = "drafts.v1.json"
    }
}
