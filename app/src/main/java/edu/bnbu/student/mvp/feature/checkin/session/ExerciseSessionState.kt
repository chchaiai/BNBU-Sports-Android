package edu.bnbu.student.mvp.feature.checkin.session

import edu.bnbu.student.mvp.core.model.CreditType

internal const val MinimumValidExerciseMillis = 60L * 60L * 1_000L
internal const val MaximumExerciseMillis = 2L * 60L * 60L * 1_000L
internal const val ExerciseTooShortMessage = "运动时长未满 1 小时，本次不计入有效打卡时长。"

internal data class ExerciseSessionDetails(
    val creditType: CreditType,
    val sportType: String,
    val customSportName: String? = null,
    val description: String = ""
) {
    val isValid: Boolean
        get() = creditType in setOf(CreditType.CourseRelated, CreditType.General) &&
            sportType in SupportedSportTypes &&
            if (sportType == OtherSportType) {
                !customSportName.isNullOrBlank() && customSportName.length <= 32
            } else {
                customSportName.isNullOrBlank()
            }

    companion object {
        const val OtherSportType = "other"
        val SupportedSportTypes = setOf(
            "running",
            "basketball",
            "football",
            "badminton",
            "swimming",
            "fitness",
            "cycling",
            OtherSportType
        )
    }
}

internal sealed interface ExerciseSessionState {
    data object Idle : ExerciseSessionState

    data class Active(
        val sessionId: String,
        val details: ExerciseSessionDetails,
        val startedAtEpochMillis: Long,
        val activeSegmentStartedAtEpochMillis: Long,
        val accumulatedActiveMillis: Long = 0L
    ) : ExerciseSessionState

    data class Paused(
        val sessionId: String,
        val details: ExerciseSessionDetails,
        val startedAtEpochMillis: Long,
        val pausedAtEpochMillis: Long,
        val accumulatedActiveMillis: Long
    ) : ExerciseSessionState

    data class Finished(
        val sessionId: String,
        val details: ExerciseSessionDetails,
        val startedAtEpochMillis: Long,
        val endedAtEpochMillis: Long,
        val activeDurationMillis: Long,
        val creditedHours: Int
    ) : ExerciseSessionState
}

internal sealed interface ExerciseSessionTransition {
    val state: ExerciseSessionState

    data class Changed(
        override val state: ExerciseSessionState
    ) : ExerciseSessionTransition

    data class TooShort(
        override val state: ExerciseSessionState.Paused,
        val message: String = ExerciseTooShortMessage
    ) : ExerciseSessionTransition

    data class Rejected(
        override val state: ExerciseSessionState,
        val reason: String
    ) : ExerciseSessionTransition
}

internal class ExerciseSessionMachine(
    private val clock: ExerciseClock = SystemExerciseClock
) {
    fun start(
        state: ExerciseSessionState,
        sessionId: String,
        details: ExerciseSessionDetails
    ): ExerciseSessionTransition {
        if (state != ExerciseSessionState.Idle) {
            return ExerciseSessionTransition.Rejected(state, "已有进行中的运动会话")
        }
        if (sessionId.isBlank()) {
            return ExerciseSessionTransition.Rejected(state, "运动会话编号不能为空")
        }
        if (!details.isValid) {
            return ExerciseSessionTransition.Rejected(state, "打卡类别或运动项目无效")
        }
        val now = clock.nowEpochMillis()
        return ExerciseSessionTransition.Changed(
            ExerciseSessionState.Active(
                sessionId = sessionId,
                details = details,
                startedAtEpochMillis = now,
                activeSegmentStartedAtEpochMillis = now
            )
        )
    }

    fun pause(state: ExerciseSessionState): ExerciseSessionTransition {
        if (state !is ExerciseSessionState.Active) {
            return ExerciseSessionTransition.Rejected(state, "只有运动中的会话可以暂停")
        }
        val now = clock.nowEpochMillis()
        if (state.effectiveDurationMillis(now) >= MaximumExerciseMillis) {
            return ExerciseSessionTransition.Changed(state.pausedAtLimit())
        }
        return ExerciseSessionTransition.Changed(
            ExerciseSessionState.Paused(
                sessionId = state.sessionId,
                details = state.details,
                startedAtEpochMillis = state.startedAtEpochMillis,
                pausedAtEpochMillis = now,
                accumulatedActiveMillis = state.effectiveDurationMillis(now)
            )
        )
    }

    fun resume(state: ExerciseSessionState): ExerciseSessionTransition {
        if (state !is ExerciseSessionState.Paused) {
            return ExerciseSessionTransition.Rejected(state, "只有已暂停的会话可以继续")
        }
        if (state.accumulatedActiveMillis >= MaximumExerciseMillis) {
            return ExerciseSessionTransition.Rejected(state, "已达到 2 小时运动上限，请确认结束本次运动")
        }
        val now = clock.nowEpochMillis()
        return ExerciseSessionTransition.Changed(
            ExerciseSessionState.Active(
                sessionId = state.sessionId,
                details = state.details,
                startedAtEpochMillis = state.startedAtEpochMillis,
                activeSegmentStartedAtEpochMillis = now,
                accumulatedActiveMillis = state.accumulatedActiveMillis
            )
        )
    }

    fun requestFinish(state: ExerciseSessionState): ExerciseSessionTransition {
        val now = clock.nowEpochMillis()
        val duration = state.effectiveDurationMillis(now)
        return when (state) {
            ExerciseSessionState.Idle,
            is ExerciseSessionState.Finished -> {
                ExerciseSessionTransition.Rejected(state, "当前没有可以结束的运动会话")
            }

            is ExerciseSessionState.Active -> {
                when {
                    duration >= MaximumExerciseMillis -> {
                        ExerciseSessionTransition.Changed(state.finishedAtLimit())
                    }

                    duration < MinimumValidExerciseMillis -> {
                        ExerciseSessionTransition.TooShort(
                            ExerciseSessionState.Paused(
                                sessionId = state.sessionId,
                                details = state.details,
                                startedAtEpochMillis = state.startedAtEpochMillis,
                                pausedAtEpochMillis = now,
                                accumulatedActiveMillis = duration
                            )
                        )
                    }

                    else -> ExerciseSessionTransition.Changed(
                        state.finished(now, duration)
                    )
                }
            }

            is ExerciseSessionState.Paused -> {
                if (duration < MinimumValidExerciseMillis) {
                    ExerciseSessionTransition.TooShort(state)
                } else {
                    ExerciseSessionTransition.Changed(state.finished(now, duration))
                }
            }
        }
    }

    fun autoFinishIfNeeded(state: ExerciseSessionState): ExerciseSessionTransition {
        if (state !is ExerciseSessionState.Active) {
            return ExerciseSessionTransition.Changed(state)
        }
        return if (state.effectiveDurationMillis(clock.nowEpochMillis()) >= MaximumExerciseMillis) {
            ExerciseSessionTransition.Changed(state.pausedAtLimit())
        } else {
            ExerciseSessionTransition.Changed(state)
        }
    }
}

internal fun ExerciseSessionState.effectiveDurationMillis(nowEpochMillis: Long): Long {
    return when (this) {
        ExerciseSessionState.Idle -> 0L
        is ExerciseSessionState.Active -> {
            val currentSegment = (nowEpochMillis - activeSegmentStartedAtEpochMillis)
                .coerceAtLeast(0L)
            (accumulatedActiveMillis + currentSegment).coerceIn(0L, MaximumExerciseMillis)
        }

        is ExerciseSessionState.Paused -> accumulatedActiveMillis.coerceIn(0L, MaximumExerciseMillis)
        is ExerciseSessionState.Finished -> activeDurationMillis.coerceIn(0L, MaximumExerciseMillis)
    }
}

internal fun creditedExerciseHours(activeDurationMillis: Long): Int {
    return when {
        activeDurationMillis >= MaximumExerciseMillis -> 2
        activeDurationMillis >= MinimumValidExerciseMillis -> 1
        else -> 0
    }
}

private fun ExerciseSessionState.Active.pausedAtLimit(): ExerciseSessionState.Paused {
    val remainingActiveMillis = (MaximumExerciseMillis - accumulatedActiveMillis).coerceAtLeast(0L)
    val exactLimitEpochMillis = activeSegmentStartedAtEpochMillis + remainingActiveMillis
    return ExerciseSessionState.Paused(
        sessionId = sessionId,
        details = details,
        startedAtEpochMillis = startedAtEpochMillis,
        pausedAtEpochMillis = exactLimitEpochMillis,
        accumulatedActiveMillis = MaximumExerciseMillis
    )
}

private fun ExerciseSessionState.Active.finishedAtLimit(): ExerciseSessionState.Finished {
    val remainingActiveMillis = (MaximumExerciseMillis - accumulatedActiveMillis).coerceAtLeast(0L)
    val exactEndEpochMillis = activeSegmentStartedAtEpochMillis + remainingActiveMillis
    return ExerciseSessionState.Finished(
        sessionId = sessionId,
        details = details,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = exactEndEpochMillis,
        activeDurationMillis = MaximumExerciseMillis,
        creditedHours = 2
    )
}

private fun ExerciseSessionState.Active.finished(
    endedAtEpochMillis: Long,
    durationMillis: Long
): ExerciseSessionState.Finished {
    return ExerciseSessionState.Finished(
        sessionId = sessionId,
        details = details,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        activeDurationMillis = durationMillis,
        creditedHours = creditedExerciseHours(durationMillis)
    )
}

private fun ExerciseSessionState.Paused.finished(
    endedAtEpochMillis: Long,
    durationMillis: Long
): ExerciseSessionState.Finished {
    return ExerciseSessionState.Finished(
        sessionId = sessionId,
        details = details,
        startedAtEpochMillis = startedAtEpochMillis,
        endedAtEpochMillis = endedAtEpochMillis,
        activeDurationMillis = durationMillis,
        creditedHours = creditedExerciseHours(durationMillis)
    )
}
