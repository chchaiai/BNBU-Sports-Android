package edu.bnbu.student.mvp.feature.checkin.session

import edu.bnbu.student.mvp.core.exercise.ExerciseSessionPhase
import edu.bnbu.student.mvp.core.exercise.ExerciseSessionRecord

internal fun ExerciseSessionState.toContractMirrorOrNull(
    version: Long,
    nowEpochMillis: Long
): ExerciseSessionRecord? {
    val details = when (this) {
        ExerciseSessionState.Idle,
        is ExerciseSessionState.Submitted -> return null
        is ExerciseSessionState.Active -> details
        is ExerciseSessionState.Paused -> details
        is ExerciseSessionState.Finished -> details
    }
    val startedAt = when (this) {
        is ExerciseSessionState.Active -> startedAtEpochMillis
        is ExerciseSessionState.Paused -> startedAtEpochMillis
        is ExerciseSessionState.Finished -> startedAtEpochMillis
        else -> error("State does not contain a session.")
    }
    val endedAt = (this as? ExerciseSessionState.Finished)?.endedAtEpochMillis
    return ExerciseSessionRecord(
        sessionId = sessionIdOrNull() ?: return null,
        phase = toExerciseSessionPhaseOrNull() ?: return null,
        version = version,
        creditType = details.creditType,
        sportType = details.sportType,
        customSportName = details.customSportName,
        startedAtEpochMillis = startedAt,
        activeDurationSeconds = effectiveDurationMillis(nowEpochMillis) / 1_000L,
        endedAtEpochMillis = endedAt
    )
}

internal fun ExerciseSessionRecord.toLocalState(
    nowEpochMillis: Long
): ExerciseSessionState {
    val details = ExerciseSessionDetails(
        creditType = creditType,
        sportType = sportType,
        customSportName = customSportName
    )
    require(details.isValid) { "Server returned invalid exercise details." }
    val durationMillis = activeDurationSeconds * 1_000L
    return when (phase) {
        ExerciseSessionPhase.ACTIVE -> ExerciseSessionState.Active(
            sessionId = sessionId,
            details = details,
            startedAtEpochMillis = startedAtEpochMillis,
            activeSegmentStartedAtEpochMillis = nowEpochMillis,
            accumulatedActiveMillis = durationMillis
        )

        ExerciseSessionPhase.PAUSED -> ExerciseSessionState.Paused(
            sessionId = sessionId,
            details = details,
            startedAtEpochMillis = startedAtEpochMillis,
            pausedAtEpochMillis = nowEpochMillis,
            accumulatedActiveMillis = durationMillis
        )

        ExerciseSessionPhase.COMPLETED -> ExerciseSessionState.Finished(
            sessionId = sessionId,
            details = details,
            startedAtEpochMillis = startedAtEpochMillis,
            endedAtEpochMillis = requireNotNull(endedAtEpochMillis),
            activeDurationMillis = durationMillis,
            creditedHours = creditedExerciseHours(durationMillis)
        )
    }
}

private fun ExerciseSessionState.sessionIdOrNull(): String? = when (this) {
    ExerciseSessionState.Idle,
    is ExerciseSessionState.Submitted -> null
    is ExerciseSessionState.Active -> sessionId
    is ExerciseSessionState.Paused -> sessionId
    is ExerciseSessionState.Finished -> sessionId
}
