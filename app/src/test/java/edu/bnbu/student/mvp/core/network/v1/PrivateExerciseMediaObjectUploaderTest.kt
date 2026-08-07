package edu.bnbu.student.mvp.core.network.v1

import edu.bnbu.student.mvp.core.exercise.ExerciseMediaUploadMethod
import edu.bnbu.student.mvp.core.exercise.ExerciseMediaUploadSession
import edu.bnbu.student.mvp.core.exercise.UploadExerciseMediaObjectCommand
import java.io.File
import java.time.Instant
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlinx.coroutines.runBlocking

class PrivateExerciseMediaObjectUploaderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var uploader: PrivateExerciseMediaObjectUploader
    private lateinit var sourceFile: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        uploader = PrivateExerciseMediaObjectUploader(
            httpClient = OkHttpClient(),
            clock = { FixedNow }
        )
        sourceFile = temporaryFolder.newFile("photo.jpg").apply {
            writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun uploadsOnlyToSignedStorageUrlWithoutBackendAuthenticationHeaders() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setHeader("ETag", "\"etag-1\""))

        val receipt = uploader.upload(command())

        assertEquals("\"etag-1\"", receipt.entityTag)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("image/jpeg", request.getHeader("Content-Type"))
        assertEquals("required-value", request.getHeader("x-required-header"))
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("X-Request-ID"))
        assertNull(request.getHeader("Idempotency-Key"))
        assertArrayEquals(sourceFile.readBytes(), request.body.readByteArray())
    }

    @Test
    fun refusesRedirectInsteadOfForwardingSignedHeadersToAnotherLocation() {
        server.enqueue(
            MockResponse()
                .setResponseCode(307)
                .setHeader("Location", server.url("/redirected"))
        )

        assertThrows(ExerciseMediaObjectUploadException::class.java) {
            runBlocking { uploader.upload(command()) }
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun rejectsExpiredUploadSessionBeforeReadingTheNetwork() {
        val expired = command().copy(
            uploadSession = session().copy(expiresAtEpochMillis = FixedNow.toEpochMilli())
        )

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { uploader.upload(expired) }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun missingEntityTagIsNotReportedAsSuccessfulUpload() {
        server.enqueue(MockResponse().setResponseCode(200))

        assertThrows(ExerciseMediaObjectUploadException::class.java) {
            runBlocking { uploader.upload(command()) }
        }
    }

    private fun command() = UploadExerciseMediaObjectCommand(
        uploadSession = session(),
        sourceFile = sourceFile,
        mimeType = "image/jpeg",
        expectedFileSizeBytes = sourceFile.length()
    )

    private fun session() = ExerciseMediaUploadSession(
        uploadSessionId = "upload-1",
        mediaId = "media-1",
        uploadUrl = server.url("/private-object").toUri(),
        uploadMethod = ExerciseMediaUploadMethod.PUT,
        requiredHeaders = mapOf(
            "Content-Type" to "image/jpeg",
            "x-required-header" to "required-value"
        ),
        expiresAtEpochMillis = FixedNow.plusSeconds(300).toEpochMilli()
    )

    private companion object {
        val FixedNow: Instant = Instant.parse("2026-08-07T12:00:00Z")
    }
}
