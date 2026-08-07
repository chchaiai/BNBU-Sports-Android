package edu.bnbu.student.mvp.core.network.v1

import edu.bnbu.student.mvp.core.exercise.ExerciseMediaObjectUploader
import edu.bnbu.student.mvp.core.exercise.ExerciseMediaUploadMethod
import edu.bnbu.student.mvp.core.exercise.ExerciseMediaUploadReceipt
import edu.bnbu.student.mvp.core.exercise.UploadExerciseMediaObjectCommand
import java.io.IOException
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response

internal class PrivateExerciseMediaObjectUploader(
    httpClient: OkHttpClient,
    private val clock: () -> Instant = Instant::now
) : ExerciseMediaObjectUploader {
    private val storageClient = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun upload(
        command: UploadExerciseMediaObjectCommand
    ): ExerciseMediaUploadReceipt {
        require(command.uploadSession.expiresAtEpochMillis > clock().toEpochMilli()) {
            "Media upload session is expired."
        }
        val requestBody = command.sourceFile.asRequestBody(command.mimeType.trim().lowercase().toMediaType())
        val request = Request.Builder()
            .url(command.uploadSession.uploadUrl.toURL())
            .apply {
                command.uploadSession.requiredHeaders.forEach { (name, value) -> header(name, value) }
            }
            .method(command.uploadSession.uploadMethod.name, requestBody)
            .build()
        return execute(request)
    }

    private suspend fun execute(request: Request): ExerciseMediaUploadReceipt =
        suspendCancellableCoroutine { continuation ->
            val call = storageClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            ExerciseMediaObjectUploadException("Private media upload failed.", error)
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            if (it.code !in 200..299) {
                                throw ExerciseMediaObjectUploadException(
                                    "Private media upload returned HTTP ${it.code}."
                                )
                            }
                            val entityTag = it.header("ETag")?.trim().orEmpty()
                            if (entityTag.isEmpty()) {
                                throw ExerciseMediaObjectUploadException(
                                    "Private media upload response is missing ETag."
                                )
                            }
                            ExerciseMediaUploadReceipt(entityTag)
                        }
                    }
                    if (!continuation.isActive) return
                    result.fold(
                        onSuccess = continuation::resume,
                        onFailure = continuation::resumeWithException
                    )
                }
            })
        }
}

internal class ExerciseMediaObjectUploadException(
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)
