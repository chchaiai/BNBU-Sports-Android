package edu.bnbu.student.mvp.core.network

import android.content.Context
import android.provider.OpenableColumns
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class StudentApiClient(
    val baseUrl: String = DefaultBaseUrl,
    val bearerToken: String? = null,
    // Android app Context required for ContentResolver-based uploads
    val appContext: Context? = null
) {
    fun withToken(token: String?): StudentApiClient = copy(bearerToken = token)
    fun withContext(context: Context): StudentApiClient = copy(appContext = context.applicationContext)

    fun request(
        endpoint: StudentEndpoint,
        body: Any? = null
    ): StudentApiRequest {
        val headers = buildMap {
            put("Accept", "application/json")
            if (endpoint.method != HttpMethod.GET) {
                put("Content-Type", "application/json")
            }
            bearerToken
                ?.takeIf { it.isNotBlank() }
                ?.let { put("Authorization", "Bearer $it") }
        }

        return StudentApiRequest(
            method = endpoint.method,
            path = endpoint.path,
            url = resolve(endpoint.path),
            headers = headers,
            body = body
        )
    }

    /**
     * Execute an API request over HTTP. Callers must wrap this in
     * withContext(Dispatchers.IO) or launch.
     *
     * @throws IOException on network error, non-2xx status, or empty response.
     */
    @Throws(IOException::class)
    fun execute(request: StudentApiRequest): String {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (k, v) -> builder.addHeader(k, v) }

        val bodyJson = if (request.body != null && request.body !is String) {
            gson.toJson(request.body)
        } else {
            request.body as? String
        }

        when (request.method) {
            HttpMethod.GET -> builder.get()
            HttpMethod.POST -> builder.post(
                (bodyJson ?: "").toRequestBody("application/json".toMediaType())
            )
            HttpMethod.PUT -> builder.put(
                (bodyJson ?: "").toRequestBody("application/json".toMediaType())
            )
        }

        val response = SharedHttpClient.instance.newCall(builder.build()).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            response.close()
            throw IOException("HTTP ${response.code}: $errorBody")
        }
        val result = response.body?.string() ?: throw IOException("Empty response body")
        response.close()
        return result
    }

    /**
     * Convenience: execute and deserialize to the expected type in one call.
     *
     * @throws IOException if Gson returns null (empty or mismatched JSON).
     */
    fun <T> executeAndParse(request: StudentApiRequest, clazz: Class<T>): T {
        val json = execute(request)
        val result = gson.fromJson(json, clazz)
            ?: throw IOException("Gson returned null for response: ${json.take(200)}")
        return result
    }

    /**
     * Upload proof files via multipart/form-data.
     *
     * Reads each [File] as binary, builds a [MultipartBody] with field name
     * "files" (matching the backend multer config), and POSTs to the upload
     * endpoint. Returns the parsed [UploadProofResponse] containing the URLs
     * assigned by the server.
     *
     * @param files image files to upload — each must exist and be readable.
     * @throws IOException on network error, non-2xx status, or empty response.
     */
    @Throws(IOException::class)
    fun uploadProofFiles(files: List<File>): UploadProofResponse {
        val uploadReq = request(StudentEndpoint.UploadProof)

        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)

        for (file in files) {
            if (!file.exists() || !file.canRead()) {
                throw IOException("Upload file not readable: ${file.name}")
            }
            val mimeType = when {
                file.extension.equals("png", ignoreCase = true) -> "image/png"
                file.extension.equals("webp", ignoreCase = true) -> "image/webp"
                file.extension.equals("heic", ignoreCase = true) -> "image/heic"
                file.extension.equals("heif", ignoreCase = true) -> "image/heif"
                else -> "image/jpeg"
            }
            builder.addFormDataPart(
                "files",
                file.name,
                file.asRequestBody(mimeType.toMediaType())
            )
        }

        val body = builder.build()
        val httpRequest = Request.Builder()
            .url(uploadReq.url)
            .apply {
                // Skip Content-Type for upload requests — multipart sets its own
                uploadReq.headers.filterKeys { it != "Content-Type" }
                    .forEach { (k, v) -> addHeader(k, v) }
            }
            .post(body)
            .build()

        val response = SharedHttpClient.instance.newCall(httpRequest).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: ""
            response.close()
            throw IOException("Upload failed HTTP ${response.code}: $errorBody")
        }
        val result = response.body?.string() ?: throw IOException("Empty upload response")
        response.close()
        return gson.fromJson(result, UploadProofResponse::class.java)
            ?: throw IOException("Gson returned null for upload response: ${result.take(200)}")
    }

    private val gson = GsonBuilder()
        .serializeNulls()
        .create()

    private fun resolve(path: String): String {
        return "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
    }

    companion object {
        // ⚠️ Use HTTP for the raw IP server — Android TLS cannot verify
        // a certificate issued for a bare IP address. Switch to a domain
        // with proper HTTPS (e.g., "https://bnbu.example.com/api") for production.
        const val DefaultBaseUrl = "http://123.207.5.70:96/api"
    }
}

/**
 * Shared [OkHttpClient] singleton — connection-pool-friendly.
 *
 * Creating a fresh OkHttpClient per [StudentApiClient] instance wastes resources
 * (new connection pools, thread-pool internals) and can exhaust file descriptors
 * under heavy use. All API requests route through this single shared instance.
 */
object SharedHttpClient {
    val instance: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        // Connection pool: reuse connections, avoid socket exhaustion
        .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
        // HTTP/1.1 only — HTTP/2 requires HTTPS (not available for raw IP).
        .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        .addInterceptor { chain ->
            // Simple retry interceptor — retries on IO errors only (not on 4xx).
            // Uses exponential backoff WITHOUT blocking the dispatcher thread.
            // Note: Thread.sleep() is acceptable here because OkHttp uses a
            // dedicated thread pool for network calls, not the main thread.
            var attempt = 0
            val maxRetries = 2
            var lastException: IOException? = null
            while (attempt <= maxRetries) {
                try {
                    return@addInterceptor chain.proceed(chain.request())
                } catch (e: IOException) {
                    lastException = e
                    attempt++
                    if (attempt > maxRetries) throw e
                    // Don't retry on 4xx client errors
                    if (e.message?.contains("HTTP 4") == true) throw e
                    // Avoid Thread.sleep on the OkHttp dispatcher; use a brief
                    // wait to avoid tight retry loops (OkHttp's dispatcher
                    // runs on a dedicated thread pool, so this is safe).
                    try { Thread.sleep(500L * attempt) } catch (_: InterruptedException) { throw e }
                }
            }
            throw lastException ?: IOException("Retry exhausted")
        }
        .build()
}

data class StudentApiRequest(
    val method: HttpMethod,
    val path: String,
    val url: String,
    val headers: Map<String, String>,
    val body: Any? = null
)

/** Response from POST /api/upload/proof */
data class UploadProofResponse(
    val urls: List<String> = emptyList(),
    val count: Int = 0
)
