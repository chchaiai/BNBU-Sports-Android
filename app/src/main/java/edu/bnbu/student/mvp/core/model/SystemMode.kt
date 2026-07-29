package edu.bnbu.student.mvp.core.model

import java.util.Locale

/**
 * Server-controlled availability policy for the student application.
 *
 * The health endpoint is intentionally allowed to omit these fields while the
 * backend rollout is incomplete; in that case the app remains in [NORMAL].
 */
enum class SystemMode {
    NORMAL,
    READ_ONLY,
    MAINTENANCE;

    val blocksWrites: Boolean
        get() = this != NORMAL

    companion object {
        fun from(value: String?): SystemMode = when (value?.trim()?.uppercase(Locale.ROOT)) {
            "READ_ONLY", "READONLY" -> READ_ONLY
            "MAINTENANCE" -> MAINTENANCE
            else -> NORMAL
        }
    }
}

data class SystemModeStatus(
    val mode: SystemMode = SystemMode.NORMAL,
    val message: String = "",
    val estimatedRecoveryTime: String? = null,
    /** A planned-maintenance notice is supplied by the backend at least 48 hours ahead. */
    val plannedMaintenanceAt: String? = null
)
