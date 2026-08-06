package edu.bnbu.student.mvp.core.exercise

internal class FakeExerciseGateway : ExerciseGateway {
    var onStart: suspend (StartExerciseCommand) -> ExerciseSessionRecord = {
        error("start was not expected")
    }
    var onGetActive: suspend () -> ExerciseSessionRecord? = {
        error("getActive was not expected")
    }
    var onPause: suspend (String, Long) -> ExerciseSessionRecord = { _, _ ->
        error("pause was not expected")
    }
    var onResume: suspend (String, Long) -> ExerciseSessionRecord = { _, _ ->
        error("resume was not expected")
    }
    var onFinish: suspend (String, Long) -> ExerciseSessionRecord = { _, _ ->
        error("finish was not expected")
    }
    var onCreateRecordDraft: suspend (String) -> ExerciseRecordDraft = {
        error("createRecordDraft was not expected")
    }
    var onUpdateRecordDraft: suspend (
        UpdateExerciseRecordDraftCommand
    ) -> ExerciseRecordDraft = {
        error("updateRecordDraft was not expected")
    }
    var onSubmitRecord: suspend (String, Long) -> ExerciseRecord = { _, _ ->
        error("submitRecord was not expected")
    }

    override suspend fun start(command: StartExerciseCommand): ExerciseSessionRecord =
        onStart(command)

    override suspend fun getActive(): ExerciseSessionRecord? = onGetActive()

    override suspend fun pause(
        sessionId: String,
        expectedVersion: Long
    ): ExerciseSessionRecord = onPause(sessionId, expectedVersion)

    override suspend fun resume(
        sessionId: String,
        expectedVersion: Long
    ): ExerciseSessionRecord = onResume(sessionId, expectedVersion)

    override suspend fun finish(
        sessionId: String,
        expectedVersion: Long
    ): ExerciseSessionRecord = onFinish(sessionId, expectedVersion)

    override suspend fun createRecordDraft(sessionId: String): ExerciseRecordDraft =
        onCreateRecordDraft(sessionId)

    override suspend fun updateRecordDraft(
        command: UpdateExerciseRecordDraftCommand
    ): ExerciseRecordDraft = onUpdateRecordDraft(command)

    override suspend fun submitRecord(
        recordId: String,
        expectedVersion: Long
    ): ExerciseRecord = onSubmitRecord(recordId, expectedVersion)
}
