package edu.bnbu.student.mvp.feature.checkin.session

import edu.bnbu.student.mvp.core.model.ProofMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionMediaDraftStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val clock = FakeExerciseClock(10_000L)

    @Test
    fun capturedPhotoIsStoredLocallyAndRestoredByANewStoreInstance() {
        val root = temporaryFolder.newFolder("drafts")
        val store = SessionMediaDraftStore(root, clock)
        val key = SessionDraftKey("student-1", "session-1")
        val target = store.prepareCapture(key, ProofMediaType.Image).getOrThrow()
        target.file.writeBytes(byteArrayOf(1, 2, 3))

        val completed = store.completeCapture(target, success = true).getOrThrow()
        val restored = SessionMediaDraftStore(root, clock).list(key)

        assertEquals(completed, restored.single())
        assertTrue(target.file.isFile)
        assertFalse(completed.selected)
    }

    @Test
    fun pendingCaptureWithWrittenBytesRecoversAfterProcessRestart() {
        val root = temporaryFolder.newFolder("drafts")
        val key = SessionDraftKey("student-1", "session-1")
        val firstStore = SessionMediaDraftStore(root, clock)
        val target = firstStore.prepareCapture(key, ProofMediaType.Video).getOrThrow()
        target.file.writeBytes(byteArrayOf(4, 5, 6, 7))

        val recovered = SessionMediaDraftStore(root, clock).list(key)

        assertEquals(1, recovered.size)
        assertEquals(ProofMediaType.Video, recovered.single().type)
        assertEquals(SessionMediaDraftStatus.Ready, recovered.single().status)
    }

    @Test
    fun enforcesSixPhotoAndOneVideoDraftLimit() {
        val store = SessionMediaDraftStore(temporaryFolder.newFolder("drafts"), clock)
        val key = SessionDraftKey("student-1", "session-1")

        repeat(6) { capture(store, key, ProofMediaType.Image) }
        capture(store, key, ProofMediaType.Video)

        assertTrue(store.prepareCapture(key, ProofMediaType.Image).isFailure)
        assertTrue(store.prepareCapture(key, ProofMediaType.Video).isFailure)
        assertEquals(7, store.list(key).size)
    }

    @Test
    fun selectedSubmissionAcceptsOnePhotoOrOneVideo() {
        val store = SessionMediaDraftStore(temporaryFolder.newFolder("drafts"), clock)
        val key = SessionDraftKey("student-1", "session-1")
        val photo = capture(store, key, ProofMediaType.Image)
        val video = capture(store, key, ProofMediaType.Video)

        assertTrue(store.selectedForSubmission(key).isFailure)
        assertTrue(store.setSelected(key, video.id, true))
        assertEquals(listOf(video.copy(selected = true)), store.selectedForSubmission(key).getOrThrow())

        assertTrue(store.setSelected(key, video.id, false))
        assertTrue(store.setSelected(key, photo.id, true))
        assertEquals(ProofMediaType.Image, store.selectedForSubmission(key).getOrThrow().single().type)
    }

    @Test
    fun accountsAndSessionsUseDifferentPrivateDirectories() {
        val store = SessionMediaDraftStore(temporaryFolder.newFolder("drafts"), clock)
        val first = store.prepareCapture(
            SessionDraftKey("student-1", "session-1"),
            ProofMediaType.Image
        ).getOrThrow()
        val second = store.prepareCapture(
            SessionDraftKey("student-2", "session-1"),
            ProofMediaType.Image
        ).getOrThrow()
        val third = store.prepareCapture(
            SessionDraftKey("student-1", "session-2"),
            ProofMediaType.Image
        ).getOrThrow()

        assertNotEquals(first.file.parentFile, second.file.parentFile)
        assertNotEquals(first.file.parentFile, third.file.parentFile)
        assertFalse(first.file.absolutePath.contains("student-1"))
        assertFalse(first.file.absolutePath.contains("session-1"))
    }

    @Test
    fun cancelledOrEmptyCaptureIsRemoved() {
        val store = SessionMediaDraftStore(temporaryFolder.newFolder("drafts"), clock)
        val key = SessionDraftKey("student-1", "session-1")
        val cancelled = store.prepareCapture(key, ProofMediaType.Image).getOrThrow()

        assertTrue(store.completeCapture(cancelled, success = false).isFailure)
        assertFalse(cancelled.file.exists())
        assertTrue(store.list(key).isEmpty())

        val empty = store.prepareCapture(key, ProofMediaType.Image).getOrThrow()
        assertTrue(store.completeCapture(empty, success = true).isFailure)
        assertFalse(empty.file.exists())
    }

    @Test
    fun clearSessionDeletesOnlyTheRequestedSession() {
        val store = SessionMediaDraftStore(temporaryFolder.newFolder("drafts"), clock)
        val firstKey = SessionDraftKey("student-1", "session-1")
        val secondKey = SessionDraftKey("student-1", "session-2")
        capture(store, firstKey, ProofMediaType.Image)
        capture(store, secondKey, ProofMediaType.Image)

        assertTrue(store.clearSession(firstKey))

        assertTrue(store.list(firstKey).isEmpty())
        assertEquals(1, store.list(secondKey).size)
    }

    private fun capture(
        store: SessionMediaDraftStore,
        key: SessionDraftKey,
        type: ProofMediaType
    ): SessionMediaDraft {
        val target = store.prepareCapture(key, type).getOrThrow()
        target.file.writeBytes(byteArrayOf(1, 2, 3))
        return store.completeCapture(target, success = true).getOrThrow()
    }

    private class FakeExerciseClock(var now: Long) : ExerciseClock {
        override fun nowEpochMillis(): Long = now
    }
}
