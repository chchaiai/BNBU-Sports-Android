package edu.bnbu.student.mvp.core.network

import edu.bnbu.student.mvp.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StudentApiClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun defaultBaseUrlComesFromBuildConfig() {
        assertEquals(BuildConfig.BNBU_API_BASE_URL, StudentApiClient.DefaultBaseUrl)
    }

    @Test
    fun rejectsAmbiguousOrCredentialedBaseUrls() {
        assertThrows(IllegalArgumentException::class.java) {
            StudentApiClient(baseUrl = "https://api.example.test")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudentApiClient(baseUrl = "https://api.example.test/api?tenant=other")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StudentApiClient(baseUrl = "https://user:secret@api.example.test/api")
        }
    }

    @Test
    fun requestAddsJsonAndBearerHeaders() {
        val client = client(bearerToken = "token-123")

        val get = client.request(StudentEndpoint.SportSummary)
        val post = client.request(
            StudentEndpoint.Login,
            StudentLoginRequest(account = "student", password = "secret")
        )

        assertEquals("application/json", get.headers["Accept"])
        assertEquals("Bearer token-123", get.headers["Authorization"])
        assertFalse(get.headers.containsKey("Content-Type"))
        assertEquals("application/json", post.headers["Content-Type"])
        assertFalse(post.headers.containsKey("Idempotency-Key"))
        assertEquals("${server.url("/api").toString().trimEnd('/')}/auth/login", post.url)
    }

    @Test
    fun mutationsGetOneStableIdempotencyKeyPerLogicalRequest() {
        var generatedKeys = 0
        val client = client(
            idempotencyKeyProvider = {
                generatedKeys += 1
                "request-key-$generatedKeys"
            }
        )

        val submit = client.request(
            StudentEndpoint.SportRecords,
            SubmitSportRecordRequest(
                creditType = "其他运动",
                courseId = null,
                taskId = null,
                hours = 1.0,
                description = "run",
                proofFiles = emptyList()
            )
        )
        val markRead = client.request(StudentEndpoint.MarkNotificationRead("notice-1"))
        val get = client.request(StudentEndpoint.SportSummary)
        val login = client.request(
            StudentEndpoint.Login,
            StudentLoginRequest(account = "student", password = "secret")
        )

        assertEquals("request-key-1", submit.headers["Idempotency-Key"])
        assertEquals("request-key-1", submit.headers["Idempotency-Key"])
        assertEquals("request-key-2", markRead.headers["Idempotency-Key"])
        assertFalse(get.headers.containsKey("Idempotency-Key"))
        assertFalse(login.headers.containsKey("Idempotency-Key"))
        assertEquals(2, generatedKeys)
    }

    @Test
    fun executeParsesDtoAndClosesErrorResponsesForReuse() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("{\"message\":\"failed\"}"))
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id":"student-remote",
                  "name":"Remote Student",
                  "email":"remote@bnbu.edu.cn",
                  "role":"student",
                  "college":"Science",
                  "className":"Class 2"
                }
                """.trimIndent()
            )
        )
        val client = client()

        assertThrows(IOException::class.java) {
            client.execute(client.request(StudentEndpoint.StudentProfile))
        }
        val profile = client.executeAndParse(
            client.request(StudentEndpoint.StudentProfile),
            StudentProfileResponse::class.java
        )

        val first = server.takeRequest()
        val second = server.takeRequest()
        assertEquals("/api/student/profile", first.path)
        assertEquals("/api/student/profile", second.path)
        assertEquals(1, second.sequenceNumber)
        assertEquals("student-remote", profile.id)
        assertEquals("Class 2", profile.className)
    }

    @Test
    fun nonIdempotentWritesAreNotRetriedAfterConnectionLoss() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(
            MockResponse().setBody(
                "{\"id\":\"record-2\",\"status\":\"待审核\",\"submittedAt\":\"2026-07-14T00:00:00Z\"}"
            )
        )
        val client = client(httpClient = SharedHttpClient.instance)
        val request = client.request(
            StudentEndpoint.SportRecords,
            SubmitSportRecordRequest(
                creditType = "其他运动",
                courseId = null,
                taskId = null,
                hours = 1.0,
                description = "run",
                proofFiles = emptyList()
            )
        )

        assertThrows(IOException::class.java) { client.execute(request) }
        assertEquals(1, server.requestCount)
        assertFalse(SharedHttpClient.isRetryableHttpMethod("POST"))
        assertFalse(SharedHttpClient.isRetryableHttpMethod("PUT"))
        assertTrue(SharedHttpClient.isRetryableHttpMethod("GET"))
    }

    @Test
    fun cancellableExecutionCancelsTheUnderlyingOkHttpCall() = runBlocking {
        server.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE)
        )
        val httpClient = okhttp3.OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .build()
        val client = client(httpClient = httpClient)

        val requestJob = async(Dispatchers.IO) {
            client.executeCancellable(client.request(StudentEndpoint.StudentProfile))
        }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        requestJob.cancelAndJoin()

        repeat(20) {
            if (httpClient.dispatcher.runningCallsCount() == 0) return@repeat
            delay(25)
        }
        assertEquals(0, httpClient.dispatcher.runningCallsCount())
    }

    private fun client(
        bearerToken: String? = null,
        httpClient: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .build(),
        idempotencyKeyProvider: () -> String = { "test-idempotency-key" }
    ): StudentApiClient {
        return StudentApiClient(
            baseUrl = server.url("/api").toString(),
            bearerToken = bearerToken,
            httpClient = httpClient,
            idempotencyKeyProvider = idempotencyKeyProvider
        )
    }
}
