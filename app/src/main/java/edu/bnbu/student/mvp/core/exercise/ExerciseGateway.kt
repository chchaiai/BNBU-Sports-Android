package edu.bnbu.student.mvp.core.exercise

import edu.bnbu.student.mvp.core.model.CreditType

/** Business boundary implemented by the backend integration owner. */
internal interface ExerciseGateway {
    suspend fun start(command: StartExerciseCommand): ExerciseSessionRecord

    suspend fun getActive(): ExerciseSessionRecord?

    suspend fun pause(sessionId: String): ExerciseSessionRecord

    suspend fun resume(sessionId: String): ExerciseSessionRecord

    suspend fun finish(sessionId: String): ExerciseSessionRecord

    suspend fun createRecordDraft(
        command: CreateExerciseRecordDraftCommand
    ): ExerciseRecordDraft

    suspend fun submitRecord(recordId: String): ExerciseRecord
}

internal enum class ExerciseSessionPhase {
    ACTIVE,
    PAUSED,
    COMPLETED
}

internal data class StartExerciseCommand(
    val creditType: CreditType,
    val sportType: String,
    val customSportName: String? = null
) {
    init {
        require(sportType.isNotBlank()) { "Sport type cannot be empty." }
    }
}

internal data class ExerciseSessionRecord(
    val sessionId: String,
    val phase: ExerciseSessionPhase,
    val startedAtEpochMillis: Long,
    val activeDurationSeconds: Long
) {
    init {
        require(sessionId.isNotBlank()) { "Session ID cannot be empty." }
        require(startedAtEpochMillis >= 0L) { "Start time cannot be negative." }
        require(activeDurationSeconds in 0L..MaximumExerciseDurationSeconds) {
            "Active duration must be between 0 and $MaximumExerciseDurationSeconds seconds."
        }
    }
}

internal data class CreateExerciseRecordDraftCommand(
    val sessionId: String,
    val description: String,
    val remark: String = "",
    /** References only; media creation and upload belong to a separate owner. */
    val availableMediaIds: List<String> = emptyList()
) {
    init {
        require(sessionId.isNotBlank()) { "Session ID cannot be empty." }
        require(availableMediaIds.all { it.isNotBlank() }) { "Media IDs cannot be empty." }
    }
}

internal data class ExerciseRecordDraft(
    val recordId: String,
    val sessionId: String
) {
    init {
        require(recordId.isNotBlank()) { "Record ID cannot be empty." }
        require(sessionId.isNotBlank()) { "Session ID cannot be empty." }
    }
}

internal data class ExerciseRecord(
    val recordId: String,
    val sessionId: String,
    val submitted: Boolean
) {
    init {
        require(recordId.isNotBlank()) { "Record ID cannot be empty." }
        require(sessionId.isNotBlank()) { "Session ID cannot be empty." }
    }
}

internal const val MaximumExerciseDurationSeconds = 2L * 60L * 60L
