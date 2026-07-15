package edu.bnbu.student.mvp.core.network

import android.content.Context
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import edu.bnbu.student.mvp.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.lang.reflect.Type
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class StudentApiClient(
    val baseUrl: String = DefaultBaseUrl,
    val bearerToken: String? = null,
    // Android app Context required for ContentResolver-based uploads
    val appContext: Context? = null,
    val httpClient: OkHttpClient = SharedHttpClient.instance,
    val idempotencyKeyProvider: () -> String = { UUID.randomUUID().toString() }
) {
    init {
        val parsedBaseUrl = baseUrl.toHttpUrlOrNull()
        require(parsedBaseUrl != null) { "BNBU_API_BASE_URL must be a valid HTTP(S) URL" }
        require(BuildConfig.DEBUG || parsedBaseUrl.isHttps) {
            "Release builds require an HTTPS BNBU_API_BASE_URL"
        }
        require(parsedBaseUrl.username.isEmpty() && parsedBaseUrl.password.isEmpty()) {
            "BNBU_API_BASE_URL must not contain credentials"
        }
        require(parsedBaseUrl.query == null && parsedBaseUrl.fragment == null) {
            "BNBU_API_BASE_URL must not contain a query or fragment"
        }
        require(parsedBaseUrl.encodedPath.trimEnd('/').endsWith("/api")) {
            "BNBU_API_BASE_URL must end with /api"
        }
    }

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
            if (endpoint.method != HttpMethod.GET && endpoint != StudentEndpoint.Login) {
                val idempotencyKey = idempotencyKeyProvider().trim()
                require(idempotencyKey.isNotEmpty()) { "Idempotency-Key must not be blank" }
                put("Idempotency-Key", idempotencyKey)
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
        return httpClient.newCall(buildHttpRequest(request)).execute().use { response ->
            response.bodyOrThrow()
        }
    }

    suspend fun executeCancellable(request: StudentApiRequest): String {
        return httpClient.newCall(buildHttpRequest(request)).awaitBody()
    }

    /**
     * Convenience: execute and deserialize to the expected type in one call.
     *
     * @throws IOException if Gson returns null (empty or mismatched JSON).
     */
    fun <T> executeAndParse(request: StudentApiRequest, clazz: Class<T>): T {
        val json = execute(request)
        return parse(json, clazz)
    }

    suspend fun <T> executeAndParseCancellable(request: StudentApiRequest, clazz: Class<T>): T {
        val json = executeCancellable(request)
        return parse(json, clazz)
    }

    private fun <T> parse(json: String, clazz: Class<T>): T {
        val result = gson.fromJson(json, clazz)
            ?: throw IOException("Gson returned null for response: ${json.take(200)}")
        return result
    }

    private fun buildHttpRequest(request: StudentApiRequest): Request {
        val builder = Request.Builder().url(request.url)
        request.headers.forEach { (key, value) -> builder.addHeader(key, value) }
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
        return builder.build()
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
        val result = httpClient.newCall(buildUploadRequest(files)).execute().use { response ->
            response.bodyOrThrow()
        }
        return parse(result, UploadProofResponse::class.java)
    }

    suspend fun uploadProofFilesCancellable(files: List<File>): UploadProofResponse {
        val result = httpClient.newCall(buildUploadRequest(files)).awaitBody()
        return parse(result, UploadProofResponse::class.java)
    }

    private fun buildUploadRequest(files: List<File>): Request {
        val uploadReq = request(StudentEndpoint.UploadProof)
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)

        for (file in files) {
            if (!file.exists() || !file.canRead()) {
                throw IOException("Upload file not readable: ${file.name}")
            }
            val mimeType = when (file.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "heic" -> "image/heic"
                "heif" -> "image/heif"
                "mp4" -> "video/mp4"
                "mov" -> "video/quicktime"
                else -> "application/octet-stream"
            }
            builder.addFormDataPart(
                "files",
                file.name,
                file.asRequestBody(mimeType.toMediaType())
            )
        }

        return Request.Builder()
            .url(uploadReq.url)
            .apply {
                // Skip Content-Type for upload requests — multipart sets its own
                uploadReq.headers.filterKeys { it != "Content-Type" }
                    .forEach { (k, v) -> addHeader(k, v) }
            }
            .post(builder.build())
            .build()
    }

    private fun Response.bodyOrThrow(): String {
        if (!isSuccessful) {
            val errorBody = body?.string() ?: ""
            throw ApiHttpException(code, errorBody)
        }
        return body?.string() ?: throw IOException("Empty response body")
    }

    private val gson = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(ProofFileResponse::class.java, ProofFileResponseJsonDeserializer)
        .create()

    private fun resolve(path: String): String {
        return "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"
    }

    companion object {
        @JvmField
        val DefaultBaseUrl: String = BuildConfig.BNBU_API_BASE_URL
    }
}

private suspend fun Call.awaitBody(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }

        override fun onResponse(call: Call, response: Response) {
            val result = runCatching {
                response.use {
                    if (!it.isSuccessful) {
                        val errorBody = it.body?.string() ?: ""
                        throw ApiHttpException(it.code, errorBody)
                    }
                    it.body?.string() ?: throw IOException("Empty response body")
                }
            }
            if (!continuation.isActive) return
            result.fold(
                onSuccess = { continuation.resume(it) },
                onFailure = { continuation.resumeWithException(it) }
            )
        }
    })
}

private object ProofFileResponseJsonDeserializer : JsonDeserializer<ProofFileResponse> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): ProofFileResponse {
        if (json.isJsonPrimitive && json.asJsonPrimitive.isString) {
            val legacyValue = json.asString
            val cosKey = legacyValue.takeIf { it.startsWith("proofs/") }.orEmpty()
            val metadata = proofMetadata(cosKey.ifBlank { legacyValue })
            return ProofFileResponse(
                url = legacyValue.takeUnless { cosKey.isNotEmpty() }.orEmpty(),
                cosKey = cosKey,
                mediaType = metadata.first,
                mimeType = metadata.second
            )
        }
        if (!json.isJsonObject) throw JsonParseException("Invalid proof file response")
        val value = json.asJsonObject
        return ProofFileResponse(
            url = value.get("url")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
            cosKey = value.get("cosKey")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
            mediaType = value.get("mediaType")?.takeUnless { it.isJsonNull }?.asString ?: "image",
            mimeType = value.get("mimeType")?.takeUnless { it.isJsonNull }?.asString.orEmpty(),
            size = value.get("size")?.takeUnless { it.isJsonNull }?.asLong ?: 0
        )
    }

    private fun proofMetadata(value: String): Pair<String, String> {
        return when (value.substringBefore('?').substringAfterLast('.').lowercase()) {
            "mp4" -> "video" to "video/mp4"
            "mov" -> "video" to "video/quicktime"
            "png" -> "image" to "image/png"
            "webp" -> "image" to "image/webp"
            "heic" -> "image" to "image/heic"
            "heif" -> "image" to "image/heif"
            else -> "image" to "image/jpeg"
        }
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
        // Disable OkHttp's transparent recovery so write requests are never
        // replayed after the server may already have committed them.
        .retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            val request = chain.request()
            if (!isRetryableHttpMethod(request.method)) {
                return@addInterceptor chain.proceed(request)
            }

            // Read-only requests may be replayed after transport failures.
            var attempt = 0
            val maxRetries = 2
            var lastException: IOException? = null
            while (attempt <= maxRetries) {
                try {
                    return@addInterceptor chain.proceed(request)
                } catch (e: IOException) {
                    lastException = e
                    attempt++
                    if (attempt > maxRetries) throw e
                    try {
                        Thread.sleep(250L * attempt)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw e
                    }
                }
            }
            throw lastException ?: IOException("Retry exhausted")
        }
        .build()

    internal fun isRetryableHttpMethod(method: String): Boolean {
        return method.equals("GET", ignoreCase = true) ||
            method.equals("HEAD", ignoreCase = true)
    }
}

data class StudentApiRequest(
    val method: HttpMethod,
    val path: String,
    val url: String,
    val headers: Map<String, String>,
    val body: Any? = null
)

class ApiHttpException(
    val statusCode: Int,
    val responseBody: String
) : IOException("HTTP $statusCode: $responseBody")

/** Response from POST /api/upload/proof */
data class UploadedProofFile(
    val url: String,
    val cosKey: String,
    val mediaType: String,
    val mimeType: String,
    val size: Long
)

data class UploadProofResponse(
    val files: List<UploadedProofFile> = emptyList(),
    val count: Int = 0
)
