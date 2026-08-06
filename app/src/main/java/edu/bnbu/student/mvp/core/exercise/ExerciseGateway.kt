package edu.bnbu.student.mvp.core.exercise

import edu.bnbu.student.mvp.core.model.CreditType

/** Business boundary implemented by the backend integration owner. */
internal interface ExerciseGateway {
    suspend fun start(command: StartExerciseCommand): ExerciseSessionRecord

    suspend fun getActive(): ExerciseSessionRecord?

    suspend fun pause(sessionId: String, expectedVersion: Long): ExerciseSessionRecord

    suspend fun resume(sessionId: String, expectedVersion: Long): ExerciseSessionRecord

    suspend fun finish(sessionId: String, expectedVersion: Long): ExerciseSessionRecord

    suspend fun createRecordDraft(sessionId: String): ExerciseRecordDraft

    suspend fun updateRecordDraft(
        command: UpdateExerciseRecordDraftCommand
    ): ExerciseRecordDraft

    suspend fun submitRecord(recordId: String, expectedVersion: Long): ExerciseRecord
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
        require(creditType == CreditType.CourseRelated || creditType == CreditType.General) {
            "Exercise session credit type is invalid."
        }
        require(sportType.isNotBlank()) { "Sport type cannot be empty." }
        if (sportType.equals(ExerciseRecordForm.OtherSportType, ignoreCase = true)) {
            val normalizedCustomSportName = customSportName?.trim()
            require(
                normalizedCustomSportName != null &&
                    normalizedCustomSportName.length in 1..MaxOtherSportNameLength
            ) {
                "Other sport name must contain 1 to $MaxOtherSportNameLength characters."
            }
        } else {
            require(customSportName.isNullOrBlank()) {
                "Custom sport name is only valid when sport type is OTHER."
            }
        }
    }
}

internal data class ExerciseSessionRecord(
    val sessionId: String,
    val phase: ExerciseSessionPhase,
    val version: Long,
    val creditType: CreditType,
    val sportType: String,
    val customSportName: String? = null,
    val startedAtEpochMillis: Long,
    val activeDurationSeconds: Long,
    val endedAtEpochMillis: Long? = null
) {
    init {
        require(sessionId.isNotBlank()) { "Session ID cannot be empty." }
        require(version >= 0L) { "Session version cannot be negative." }
        require(creditType == CreditType.CourseRelated || creditType == CreditType.General) {
            "Exercise session credit type is invalid."
        }
        require(sportType.isNotBlank()) { "Sport type cannot be empty." }
        require(startedAtEpochMillis >= 0L) { "Start time cannot be negative." }
        require(activeDurationSeconds in 0L..MaximumExerciseDurationSeconds) {
            "Active duration must be between 0 and $MaximumExerciseDurationSeconds seconds."
        }
        require(
            activeDurationSeconds < MaximumExerciseDurationSeconds ||
                phase == ExerciseSessionPhase.COMPLETED
        ) { "A session at the two-hour limit must be COMPLETED." }
        require(endedAtEpochMillis == null || endedAtEpochMillis >= startedAtEpochMillis) {
            "End time cannot be earlier than start time."
        }
        require(phase != ExerciseSessionPhase.COMPLETED || endedAtEpochMillis != null) {
            "Completed sessions require an end time."
        }
    }
}

internal const val MinimumValidExerciseDurationSeconds = 60L * 60L
internal const val MaximumExerciseDurationSeconds = 2L * 60L * 60L
