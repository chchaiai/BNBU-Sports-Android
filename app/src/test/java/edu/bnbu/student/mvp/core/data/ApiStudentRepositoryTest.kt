package edu.bnbu.student.mvp.core.data

import edu.bnbu.student.mvp.core.model.ProofAttachment
import edu.bnbu.student.mvp.core.model.ProofMediaType
import edu.bnbu.student.mvp.core.model.Exemption
import edu.bnbu.student.mvp.core.model.ExemptionApplication
import edu.bnbu.student.mvp.core.network.StudentApiClient
import edu.bnbu.student.mvp.core.network.SubmitSportRecordRequest
import edu.bnbu.student.mvp.core.network.SupplementSportRecordRequest
import edu.bnbu.student.mvp.core.network.UserDto
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApiStudentRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
    fun mutationCallsRunBlockingHttpOnIoDispatcher() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                "{\"id\":\"record-1\",\"status\":\"待审核\",\"submittedAt\":\"2026-07-14T00:00:00Z\"}"
            )
        )
        server.enqueue(MockResponse().setBody("{\"id\":\"record-1\",\"status\":\"待审核\"}"))
        server.enqueue(MockResponse().setBody("{\"id\":\"notice-1\",\"read\":true}"))

        val networkThreads = CopyOnWriteArrayList<String>()
        val httpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .addInterceptor { chain ->
                networkThreads += Thread.currentThread().name
                chain.proceed(chain.request())
            }
            .build()
        val repository = repository(httpClient = httpClient)
        val callerThread = Thread.currentThread().name

        val submit = repository.submitRecord(
            SubmitSportRecordRequest(
                creditType = "其他运动",
                courseId = null,
                taskId = null,
                hours = 1.0,
                description = "run",
                proofFiles = emptyList()
            )
        )
        val supplement = repository.supplementRecord(
            "record-1",
            SupplementSportRecordRequest(
                hours = 1.0,
                description = "more proof",
                proofFiles = emptyList()
            )
        )
        val markRead = repository.markNotificationRead("notice-1")

        assertTrue(submit.isSuccess)
        assertTrue(supplement.isSuccess)
        assertTrue(markRead.isSuccess)
        assertEquals(3, networkThreads.size)
        networkThreads.forEach { assertNotEquals(callerThread, it) }
        assertEquals("POST", server.takeRequest().method)
        assertEquals("POST", server.takeRequest().method)
        assertEquals("PUT", server.takeRequest().method)
    }

    @Test
    fun workspaceUsesRemoteProfileAndReportsGradesFailure() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id":"remote-id",
                  "name":"Remote Name",
                  "email":"remote@bnbu.edu.cn",
                  "role":"student",
                  "college":"Remote College",
                  "className":"Remote Class",
                  "status":"正常"
                }
                """.trimIndent()
            )
        )
        server.enqueue(MockResponse().setBody("{\"pending\":[],\"completed\":[]}"))
        server.enqueue(MockResponse().setBody("{\"courses\":[],\"scope\":\"all\"}"))
        server.enqueue(MockResponse().setResponseCode(503).setBody("{\"message\":\"grades unavailable\"}"))

        val staleUser = UserDto(
            id = "stale-id",
            name = "Stale Name",
            email = "stale@bnbu.edu.cn",
            role = "student",
            college = "Stale College",
            className = "Stale Class"
        )
        val workspace = repository(userProfile = staleUser).loadWorkspaceAsync()

        assertEquals("remote-id", workspace.student.id)
        assertEquals("Remote Name", workspace.student.name)
        assertEquals("remote@bnbu.edu.cn", workspace.student.email)
        assertEquals("Remote College", workspace.student.college)
        assertEquals("Remote Class", workspace.student.className)
        assertTrue(workspace.grades.sourceTrace.contains("HTTP 503"))
    }

    @Test
    fun unreadableUploadFailsWithoutCallingServer() = runBlocking {
        val missing = File(temporaryFolder.root, "missing.jpg")
        val attachment = imageAttachment(missing)

        val result = repository().uploadProofFiles(
            proofAttachments = listOf(attachment),
            cacheDir = temporaryFolder.newFolder("cache-unreadable")
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("not readable"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun uploadCountMismatchIsAnExplicitFailure() = runBlocking {
        val source = temporaryFolder.newFile("proof.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        server.enqueue(MockResponse().setBody("{\"files\":[],\"count\":0}"))

        val result = repository().uploadProofFiles(
            proofAttachments = listOf(imageAttachment(source)),
            cacheDir = temporaryFolder.newFolder("cache-mismatch")
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("accepted 0 of 1"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun exemptionSupplementUsesDedicatedCategoryRouteAndPayload() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                "{\"id\":\"exemption-1\",\"status\":\"reviewing\",\"createdAt\":\"2026-07-14T00:00:00Z\"}"
            )
        )
        val exemption = Exemption(
            id = "exemption-1",
            studentId = "student-1",
            type = "team",
            category = "checkin",
            organization = "Track Team",
            reason = "original reason",
            status = "supplement_required",
            createdAt = "2026-07-13T00:00:00Z"
        )

        val response = repository().supplementExemption(
            exemption = exemption,
            payload = ExemptionApplication(
                type = "team",
                reason = "new supporting document",
                proofFiles = listOf("proofs/student-1/new.jpg"),
                organization = "Track Team"
            )
        )

        assertEquals("exemption-1", response.id)
        val request = server.takeRequest()
        assertEquals(
            "/api/student/checkin-exemptions/exemption-1/supplements",
            request.path
        )
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"reason\":\"new supporting document\""))
        assertFalse(body.contains("\"type\""))
    }

    private fun imageAttachment(file: File): ProofAttachment {
        return ProofAttachment(
            id = file.name,
            type = ProofMediaType.Image,
            fileName = file.name,
            byteCount = file.length().takeIf { it > 0 } ?: 1,
            source = file.toURI().toString()
        )
    }

    private fun repository(
        httpClient: OkHttpClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .build(),
        userProfile: UserDto? = null
    ): ApiStudentRepository {
        val apiClient = StudentApiClient(
            baseUrl = server.url("/api").toString(),
            bearerToken = "test-token",
            httpClient = httpClient
        )
        return ApiStudentRepository(apiClient = apiClient, userProfile = userProfile)
    }
}
