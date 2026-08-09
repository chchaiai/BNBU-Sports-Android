package edu.bnbu.student.mvp.core.exercise

import java.util.concurrent.CancellationException
import kotlinx.coroutines.sync.Mutex

internal enum class ExerciseSessionAction {
    RESTORE,
    START,
    PAUSE,
    RESUME,
    FINISH
}

internal enum class ExerciseOperationRejection {
    OPERATION_IN_PROGRESS,
    INVALID_STATE
}

internal data class ExerciseRecoverableFailure(
    val action: ExerciseSessionAction,
    val cause: Throwable
)

internal data class ExerciseSessionCoordinatorState(
    val session: ExerciseSessionRecord? = null,
    val inFlightAction: ExerciseSessionAction? = null,
    val recoverableFailure: ExerciseRecoverableFailure? = null
)

internal sealed interface ExerciseSessionOperationResult {
    data class Success(val session: ExerciseSessionRecord?) : ExerciseSessionOperationResult

    data class Rejected(
        val reason: ExerciseOperationRejection
    ) : ExerciseSessionOperationResult

    data class Failed(
        val retainedSession: ExerciseSessionRecord?,
        val cause: Throwable
    ) : ExerciseSessionOperationResult
}

internal class ExerciseVersionConflictException(
    message: String = "Exercise session version conflict."
) : IllegalStateException(message)

/**
 * Coordinates the server-authoritative session mirror without depending on HTTP.
 * Failed mutations retain the last confirmed state and are always safe to retry.
 */
internal class ExerciseSessionCoordinator(
    private val gateway: ExerciseGateway
) {
    private val operationMutex = Mutex()

    var state: ExerciseSessionCoordinatorState = ExerciseSessionCoordinatorState()
        private set

    suspend fun restore(
        localMirror: ExerciseSessionRecord?
    ): ExerciseSessionOperationResult = execute(ExerciseSessionAction.RESTORE) {
        state = state.copy(session = localMirror)
        val serverActive = gateway.getActive(localMirror)
        val authoritative = serverActive ?: localMirror?.takeIf {
            it.phase == ExerciseSessionPhase.COMPLETED
        }
        complete(authoritative)
    }

    suspend fun start(command: StartExerciseCommand): ExerciseSessionOperationResult {
        if (state.session != null) return invalidState()
        return execute(ExerciseSessionAction.START) {
            val started = gateway.start(command)
            require(started.phase == ExerciseSessionPhase.ACTIVE) {
                "Start must return an ACTIVE session."
            }
            complete(started)
        }
    }

    suspend fun pause(): ExerciseSessionOperationResult {
        val current = state.session?.takeIf { it.phase == ExerciseSessionPhase.ACTIVE }
            ?: return invalidState()
        return mutate(ExerciseSessionAction.PAUSE, current) {
            gateway.pause(current)
        }
    }

    suspend fun resume(): ExerciseSessionOperationResult {
        val current = state.session?.takeIf { it.phase == ExerciseSessionPhase.PAUSED }
            ?: return invalidState()
        return mutate(ExerciseSessionAction.RESUME, current) {
            gateway.resume(current)
        }
    }

    suspend fun finish(): ExerciseSessionOperationResult {
        val current = state.session?.takeIf {
            it.phase == ExerciseSessionPhase.ACTIVE || it.phase == ExerciseSessionPhase.PAUSED
        } ?: return invalidState()
        return mutate(ExerciseSessionAction.FINISH, current) {
            gateway.finish(current)
        }
    }

    fun clearCompletedSession(): Boolean {
        if (state.inFlightAction != null || state.session?.phase != ExerciseSessionPhase.COMPLETED) {
            return false
        }
        state = ExerciseSessionCoordinatorState()
        return true
    }

    private suspend fun mutate(
        action: ExerciseSessionAction,
        previous: ExerciseSessionRecord,
        request: suspend () -> ExerciseSessionRecord
    ): ExerciseSessionOperationResult = execute(action) {
        val updated = request()
        require(updated.sessionId == previous.sessionId) {
            "Server returned a different exercise session."
        }
        val expectedPhase = when (action) {
            ExerciseSessionAction.PAUSE -> ExerciseSessionPhase.PAUSED
            ExerciseSessionAction.RESUME -> ExerciseSessionPhase.ACTIVE
            ExerciseSessionAction.FINISH -> ExerciseSessionPhase.COMPLETED
            else -> error("Unsupported mutation action: $action")
        }
        require(updated.phase == expectedPhase) {
            "$action returned ${updated.phase}, expected $expectedPhase."
        }
        require(updated.version > previous.version) {
            "$action did not advance the server version."
        }
        complete(updated)
    }

    private suspend fun execute(
        action: ExerciseSessionAction,
        operation: suspend () -> ExerciseSessionOperationResult
    ): ExerciseSessionOperationResult {
        if (!operationMutex.tryLock()) {
            return ExerciseSessionOperationResult.Rejected(
                ExerciseOperationRejection.OPERATION_IN_PROGRESS
            )
        }
        val previous = state.session
        state = state.copy(inFlightAction = action, recoverableFailure = null)
        return try {
            operation()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val fallback = state.session ?: previous
            val retained = if (error is ExerciseVersionConflictException) {
                val refreshed = fallback?.let { current ->
                    runCatching { gateway.get(current.sessionId, current) }
                }
                if (refreshed?.isSuccess == true) refreshed.getOrNull() else fallback
            } else {
                fallback
            }
            state = ExerciseSessionCoordinatorState(
                session = retained,
                recoverableFailure = ExerciseRecoverableFailure(action, error)
            )
            ExerciseSessionOperationResult.Failed(retained, error)
        } finally {
            if (state.inFlightAction != null) {
                state = state.copy(inFlightAction = null)
            }
            operationMutex.unlock()
        }
    }

    private fun complete(
        session: ExerciseSessionRecord?
    ): ExerciseSessionOperationResult.Success {
        state = ExerciseSessionCoordinatorState(session = session)
        return ExerciseSessionOperationResult.Success(session)
    }

    private fun invalidState() = ExerciseSessionOperationResult.Rejected(
        ExerciseOperationRejection.INVALID_STATE
    )
}
