package edu.bnbu.student.mvp.core.network.v1

import com.google.gson.JsonParser
import edu.bnbu.student.mvp.core.exercise.ExerciseSessionPhase
import edu.bnbu.student.mvp.core.exercise.ExerciseSessionRecord
import edu.bnbu.student.mvp.core.exercise.ExerciseVersionConflictException
import edu.bnbu.student.mvp.core.exercise.StartExerciseCommand
import edu.bnbu.student.mvp.core.local.AuthSessionCredentialStore
import edu.bnbu.student.mvp.core.local.AuthSessionCredentials
import edu.bnbu.student.mvp.core.model.CreditType
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class V1ExerciseSessionGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var store: ExerciseCredentialStore
    private lateinit var gateway: V1ExerciseSessionGateway

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        store = ExerciseCredentialStore(authSession())
        val client = V1AuthorizedApiClient.create(
            credentialStore = store,
            baseUrl = server.url("/api/v1").toString().trimEnd('/'),
            httpClient = OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .build(),
            clock = { FixedNow },
            requestIdProvider = { "req-client" },
            idempotencyKeyProvider = { IdempotencyKey.fromGenerated("auth-intent") }
        )
        gateway = V1ExerciseSessionGateway(
            authorizedClient = client,
            enrollmentIdProvider = { store.session?.enrollmentId },
            clock = { FixedNow },
            mutationRegistry = MutationIntentRegistry { "exercise-intent" }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun startUsesOnlyOpenApiFieldsAndPreservesClientSelectionLocally() = runBlocking {
        server.enqueue(success(201, "req-start", sessionJson("IN_PROGRESS", 1L, 0L)))

        val result = gateway.start(
            StartExerciseCommand(
                creditType = CreditType.General,
                sportType = "OTHER",
                customSportName = "Climbing"
            )
        )

        assertEquals(ExerciseSessionPhase.ACTIVE, result.phase)
        assertEquals("enrollment-1", result.enrollmentId)
        assertEquals(CreditType.General, result.creditType)
        assertEquals("OTHER", result.sportType)
        assertEquals("Climbing", result.customSportName)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/exercise-sessions", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
        assertEquals("exercise-intent", request.getHeader("Idempotency-Key"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("enrollmentId", "clientObservedAt"), body.keySet())
        assertEquals("enrollment-1", body["enrollmentId"].asString)
        assertFalse(body.has("creditType"))
        assertFalse(body.has("sportType"))
    }

    @Test
    fun activeReadCombinesServerStateWithMatchingLocalSelection() = runBlocking {
        server.enqueue(success(200, "req-active", sessionJson("PAUSED", 4L, 901L)))
        val localMirror = localMirror(version = 0L)

        val result = gateway.getActive(localMirror)

        requireNotNull(result)
        assertEquals(ExerciseSessionPhase.PAUSED, result.phase)
        assertEquals(4L, result.version)
        assertEquals(901L, result.activeDurationSeconds)
        assertEquals(localMirror.creditType, result.creditType)
        assertEquals(localMirror.sportType, result.sportType)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals(
            "/api/v1/exercise-sessions/active?enrollmentId=enrollment-1",
            request.path
        )
    }

    @Test
    fun activeReadFailsClosedWhenServerSessionHasNoLocalSelectionContext() {
        server.enqueue(success(200, "req-active", sessionJson("IN_PROGRESS", 2L, 10L)))

        assertThrows(ExerciseSessionClientContextMissingException::class.java) {
            runBlocking { gateway.getActive(null) }
        }
    }

    @Test
    fun pauseSendsPositiveExpectedVersionAndMapsAuthoritativeDuration() = runBlocking {
        server.enqueue(success(200, "req-pause", sessionJson("PAUSED", 5L, 1_234L)))

        val result = gateway.pause(localMirror(version = 4L))

        assertEquals(ExerciseSessionPhase.PAUSED, result.phase)
        assertEquals(5L, result.version)
        assertEquals(1_234L, result.activeDurationSeconds)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/exercise-sessions/session-1/pause", request.path)
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("clientObservedAt", "expectedVersion"), body.keySet())
        assertEquals(4L, body["expectedVersion"].asLong)
    }

    @Test
    fun durationCapConflictRequestsAnAuthoritativeRefresh() {
        server.enqueue(error(409, "SESSION_DURATION_CAP_REACHED", "req-cap"))

        val thrown = assertThrows(ExerciseVersionConflictException::class.java) {
            runBlocking { gateway.pause(localMirror(version = 4L)) }
        }

        assertTrue(thrown.cause is V1HttpException)
    }

    @Test
    fun directReadMapsTerminalContractStatusesWithoutInventingAnActiveSession() = runBlocking {
        server.enqueue(success(200, "req-read", sessionJson("EXPIRED", 6L, 1_500L)))

        val result = gateway.get("session-1", localMirror(version = 5L))

        assertEquals(ExerciseSessionPhase.EXPIRED, result.phase)
        assertEquals("/api/v1/exercise-sessions/session-1", server.takeRequest().path)
    }

    private fun localMirror(version: Long): ExerciseSessionRecord = ExerciseSessionRecord(
        sessionId = "session-1",
        phase = ExerciseSessionPhase.ACTIVE,
        version = version,
        enrollmentId = "enrollment-1",
        creditType = CreditType.CourseRelated,
        sportType = "RUNNING",
        startedAtEpochMillis = FixedNow.minusSeconds(600).toEpochMilli(),
        activeDurationSeconds = 600L
    )

    private fun authSession(): AuthSessionCredentials = AuthSessionCredentials.fromContract(
        sessionId = "auth-session-1",
        enrollmentId = "enrollment-1",
        accessToken = "access-token",
        refreshToken = "refresh-token",
        tokenType = "Bearer",
        accessTokenExpiresAt = "2026-08-07T13:00:00Z",
        refreshTokenExpiresAt = "2026-08-14T12:00:00Z",
        principalUserId = "user-1"
    )

    private fun success(status: Int, requestId: String, data: String): MockResponse =
        MockResponse()
            .setResponseCode(status)
            .setHeader("X-Request-ID", requestId)
            .setBody("""{"data":$data,"meta":{"requestId":"$requestId"}}""")

    private fun error(status: Int, code: String, requestId: String): MockResponse =
        MockResponse()
            .setResponseCode(status)
            .setHeader("X-Request-ID", requestId)
            .setBody(
                """{"code":"$code","message":"safe message","details":{},"requestId":"$requestId","timestamp":"2026-08-07T12:00:00Z"}"""
            )

    private fun sessionJson(status: String, version: Long, durationSeconds: Long): String {
        val endedAt = if (status in setOf("COMPLETED", "CANCELLED", "EXPIRED")) {
            "\"2026-08-07T12:30:00Z\""
        } else {
            "null"
        }
        val endReason = when (status) {
            "COMPLETED" -> "\"USER_COMPLETED\""
            "CANCELLED" -> "\"USER_CANCELLED\""
            "EXPIRED" -> "\"SESSION_EXPIRED\""
            else -> "null"
        }
        return """{
            "id":"session-1",
            "organizationId":"org-1",
            "semesterId":"semester-1",
            "studentId":"student-1",
            "enrollmentId":"enrollment-1",
            "classSectionId":"section-1",
            "status":"$status",
            "startedAt":"2026-08-07T12:00:00Z",
            "endedAt":$endedAt,
            "actualDurationSeconds":$durationSeconds,
            "pausedDurationSeconds":0,
            "businessDate":"2026-08-07",
            "lastHeartbeatAt":"2026-08-07T12:10:00Z",
            "endReason":$endReason,
            "version":$version
        }""".trimIndent()
    }

    private class ExerciseCredentialStore(
        var session: AuthSessionCredentials?
    ) : AuthSessionCredentialStore {
        override fun saveAuthSession(session: AuthSessionCredentials): Boolean {
            this.session = session
            return true
        }

        override fun loadAuthSession(): AuthSessionCredentials? = session

        override fun clearAuth() {
            session = null
        }
    }

    private companion object {
        val FixedNow: Instant = Instant.parse("2026-08-07T12:00:00Z")
    }
}
