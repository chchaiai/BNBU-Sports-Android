package edu.bnbu.student.mvp.core.exercise

import edu.bnbu.student.mvp.core.model.ProofMediaType

internal enum class ExerciseMediaSource {
    CAMERA,
    GALLERY
}

internal data class ExerciseMediaCandidate(
    val type: ProofMediaType,
    val byteCount: Long,
    val durationSeconds: Double? = null,
    val source: ExerciseMediaSource
)

internal object ExerciseMediaPolicy {
    const val MaxImageCount = 6
    const val MaxVideoCount = 1
    const val MaxImageBytes = 10L * 1_024L * 1_024L
    const val MaxVideoDurationSeconds = 15.0

    fun validateCandidate(candidate: ExerciseMediaCandidate): Result<Unit> = runCatching {
        require(candidate.source == ExerciseMediaSource.CAMERA) {
            "Check-in evidence must be captured with the camera."
        }
        require(candidate.byteCount > 0L) { "Captured media cannot be empty." }
        if (candidate.type == ProofMediaType.Image) {
            require(candidate.byteCount <= MaxImageBytes) {
                "Captured image exceeds its size limit."
            }
        }
        when (candidate.type) {
            ProofMediaType.Image -> require(candidate.durationSeconds == null) {
                "Images cannot have a video duration."
            }

            ProofMediaType.Video -> {
                val duration = requireNotNull(candidate.durationSeconds) {
                    "Video duration is required."
                }
                require(duration.isFinite() && duration > 0.0) {
                    "Video duration must be positive."
                }
                require(duration <= MaxVideoDurationSeconds) {
                    "Video duration exceeds $MaxVideoDurationSeconds seconds."
                }
            }
        }
    }

    fun validateSelection(candidates: List<ExerciseMediaCandidate>): Result<Unit> = runCatching {
        require(candidates.isNotEmpty()) { "At least one evidence file is required." }
        require(candidates.count { it.type == ProofMediaType.Image } <= MaxImageCount) {
            "At most $MaxImageCount images can be selected."
        }
        require(candidates.count { it.type == ProofMediaType.Video } <= MaxVideoCount) {
            "At most $MaxVideoCount video can be selected."
        }
        candidates.forEach { validateCandidate(it).getOrThrow() }
    }
}
