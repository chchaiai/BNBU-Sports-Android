package edu.bnbu.student.mvp.core.exercise

import edu.bnbu.student.mvp.core.model.CreditType
import edu.bnbu.student.mvp.core.model.ProofMediaType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseRecordCoordinatorTest {
    @Test
    fun completedSessionCreatesUpdatesAndSubmitsARecordDraft() = runBlocking {
        val gateway = FakeExerciseGateway()
        val session = completedSession()
        val created = ExerciseRecordDraft("record-1", session.sessionId, version = 1L)
        val updated = created.copy(version = 2L)
        val submitted = ExerciseRecord(
            recordId = created.recordId,
            sessionId = session.sessionId,
            version = 3L,
            submittedAtEpochMillis = 9_000L
        )
        gateway.onCreateRecordDraft = { sessionId ->
            assertEquals(session.sessionId, sessionId)
            created
        }
        var updateCommand: UpdateExerciseRecordDraftCommand? = null
        gateway.onUpdateRecordDraft = { command ->
            updateCommand = command
            updated
        }
        var submitExpectedVersion = -1L
        gateway.onSubmitRecord = { recordId, expectedVersion ->
            assertEquals(created.recordId, recordId)
            submitExpectedVersion = expectedVersion
            submitted
        }
        val coordinator = ExerciseRecordCoordinator(gateway)

        coordinator.begin(session)
        coordinator.edit(validForm(description = "  morning run  "))
        coordinator.updateDraft()
        val result = coordinator.submit()

        assertTrue(result is ExerciseRecordOperationResult.Success)
        assertEquals(1L, updateCommand?.expectedVersion)
        assertEquals("morning run", updateCommand?.form?.description)
        assertEquals(2L, submitExpectedVersion)
        assertEquals(submitted, coordinator.state.submittedRecord)
    }

    @Test
    fun allRecordDescriptionsMustContainOneToTwoHundredCharacters() = runBlocking {
        val gateway = gatewayWithCreatedDraft()
        val coordinator = ExerciseRecordCoordinator(gateway)
        coordinator.begin(completedSession())

        coordinator.edit(validForm(description = " "))
        val blankResult = coordinator.updateDraft()
        coordinator.edit(validForm(description = "a".repeat(201)))
        val longResult = coordinator.updateDraft()

        assertRejectedForm(blankResult)
        assertRejectedForm(longResult)
    }

    @Test
    fun otherSportNameMustContainOneToOneHundredCharacters() = runBlocking {
        val gateway = gatewayWithCreatedDraft()
        val coordinator = ExerciseRecordCoordinator(gateway)
        coordinator.begin(completedSession())

        coordinator.edit(validForm().copy(
            sportType = ExerciseRecordForm.OtherSportType,
            otherSportName = ""
        ))
        val blankResult = coordinator.updateDraft()
        coordinator.edit(validForm().copy(
            sportType = ExerciseRecordForm.OtherSportType,
            otherSportName = "a".repeat(101)
        ))
        val longResult = coordinator.updateDraft()

        assertRejectedForm(blankResult)
        assertRejectedForm(longResult)
    }

    @Test
    fun recordCanOnlyReferenceAvailableMedia() = runBlocking {
        val gateway = gatewayWithCreatedDraft()
        val coordinator = ExerciseRecordCoordinator(gateway)
        coordinator.begin(completedSession())
        coordinator.edit(validForm().copy(
            media = listOf(
                ExerciseMediaReference(
                    mediaId = "media-1",
                    sessionId = "session-1",
                    type = ProofMediaType.Image,
                    availability = ExerciseMediaAvailability.PROCESSING
                )
            )
        ))

        val result = coordinator.updateDraft()

        assertRejectedForm(result)
    }

    @Test
    fun recordCannotBindMediaFromAnotherSession() = runBlocking {
        val gateway = gatewayWithCreatedDraft()
        val coordinator = ExerciseRecordCoordinator(gateway)
        coordinator.begin(completedSession())
        coordinator.edit(validForm().copy(
            media = listOf(
                ExerciseMediaReference(
                    mediaId = "media-1",
                    sessionId = "session-2",
                    type = ProofMediaType.Image,
                    availability = ExerciseMediaAvailability.AVAILABLE
                )
            )
        ))

        val result = coordinator.updateDraft()

        assertRejectedForm(result)
    }

    @Test
    fun failedSubmissionRetainsDraftFormAndMediaThenAllowsRetry() = runBlocking {
        val gateway = gatewayWithCreatedDraft()
        gateway.onUpdateRecordDraft = { command ->
            ExerciseRecordDraft(command.recordId, "session-1", version = 2L)
        }
        val failure = IllegalStateException("offline")
        var submitCalls = 0
        gateway.onSubmitRecord = { recordId, _ ->
            submitCalls += 1
            if (submitCalls == 1) throw failure
            ExerciseRecord(recordId, "session-1", version = 3L, submittedAtEpochMillis = 9_000L)
        }
        val coordinator = ExerciseRecordCoordinator(gateway)
        val form = validForm()
        coordinator.begin(completedSession())
        coordinator.edit(form)
        coordinator.updateDraft()

        val failed = coordinator.submit()

        assertTrue(failed is ExerciseRecordOperationResult.Failed)
        assertEquals("record-1", coordinator.state.remoteDraft?.recordId)
        assertEquals(form.media, coordinator.state.form.media)
        assertTrue(coordinator.state.isFormSynced)
        assertSame(failure, coordinator.state.recoverableFailure?.cause)

        val retried = coordinator.submit()

        assertTrue(retried is ExerciseRecordOperationResult.Success)
        assertEquals(2, submitCalls)
        assertEquals("record-1", coordinator.state.submittedRecord?.recordId)
    }

    @Test
    fun concurrentSubmitIsRejectedInsteadOfCreatingADuplicateRequest() = runBlocking {
        val gateway = gatewayWithCreatedDraft()
        gateway.onUpdateRecordDraft = { command ->
            ExerciseRecordDraft(command.recordId, "session-1", version = 2L)
        }
        val enteredSubmit = CompletableDeferred<Unit>()
        val releaseSubmit = CompletableDeferred<Unit>()
        var submitCalls = 0
        gateway.onSubmitRecord = { recordId, _ ->
            submitCalls += 1
            enteredSubmit.complete(Unit)
            releaseSubmit.await()
            ExerciseRecord(recordId, "session-1", version = 3L, submittedAtEpochMillis = 9_000L)
        }
        val coordinator = ExerciseRecordCoordinator(gateway)
        coordinator.begin(completedSession())
        coordinator.edit(validForm())
        coordinator.updateDraft()

        val first = async { coordinator.submit() }
        enteredSubmit.await()
        val duplicate = coordinator.submit()
        releaseSubmit.complete(Unit)

        assertTrue(first.await() is ExerciseRecordOperationResult.Success)
        assertTrue(duplicate is ExerciseRecordOperationResult.Rejected)
        duplicate as ExerciseRecordOperationResult.Rejected
        assertEquals(ExerciseRecordRejection.OPERATION_IN_PROGRESS, duplicate.reason)
        assertEquals(1, submitCalls)
    }

    @Test
    fun sessionShorterThanOneHourCannotCreateARecordDraft() = runBlocking {
        val gateway = FakeExerciseGateway()
        var createCalls = 0
        gateway.onCreateRecordDraft = {
            createCalls += 1
            ExerciseRecordDraft("record-1", it, version = 1L)
        }
        val coordinator = ExerciseRecordCoordinator(gateway)

        val result = coordinator.begin(
            completedSession().copy(activeDurationSeconds = MinimumValidExerciseDurationSeconds - 1L)
        )

        assertTrue(result is ExerciseRecordOperationResult.Rejected)
        assertEquals(0, createCalls)
    }

    private fun gatewayWithCreatedDraft(): FakeExerciseGateway {
        return FakeExerciseGateway().apply {
            onCreateRecordDraft = { sessionId ->
                ExerciseRecordDraft("record-1", sessionId, version = 1L)
            }
        }
    }

    private fun completedSession() = ExerciseSessionRecord(
        sessionId = "session-1",
        phase = ExerciseSessionPhase.COMPLETED,
        version = 4L,
        creditType = CreditType.General,
        sportType = "running",
        startedAtEpochMillis = 1_000L,
        activeDurationSeconds = MinimumValidExerciseDurationSeconds,
        endedAtEpochMillis = 3_601_000L
    )

    private fun validForm(
        description: String = "morning run"
    ) = ExerciseRecordForm(
        description = description,
        remark = "felt good",
        sportType = "running",
        media = listOf(
            ExerciseMediaReference(
                mediaId = "media-1",
                sessionId = "session-1",
                type = ProofMediaType.Image,
                availability = ExerciseMediaAvailability.AVAILABLE
            )
        )
    )

    private fun assertRejectedForm(result: ExerciseRecordOperationResult) {
        assertTrue(result is ExerciseRecordOperationResult.Rejected)
        result as ExerciseRecordOperationResult.Rejected
        assertEquals(ExerciseRecordRejection.INVALID_FORM, result.reason)
    }
}
