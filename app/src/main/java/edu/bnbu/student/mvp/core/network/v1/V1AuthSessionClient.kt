package edu.bnbu.student.mvp.core.network.v1

import com.google.gson.Gson
import edu.bnbu.student.mvp.BuildConfig
import edu.bnbu.student.mvp.core.local.AuthSessionCredentialStore
import edu.bnbu.student.mvp.core.local.AuthSessionCredentials
import edu.bnbu.student.mvp.core.network.SharedHttpClient
import edu.bnbu.student.mvp.core.network.v1.generated.AuthSession
import edu.bnbu.student.mvp.core.network.v1.generated.LogoutRequest
import edu.bnbu.student.mvp.core.network.v1.generated.RefreshRequest
import java.time.Instant
import java.util.UUID
import okhttp3.OkHttpClient

class V1SessionInvalidatedException(
    val requestId: String?,
    cause: Throwable? = null
) : V1TransportException(
    "Authenticated session is no longer usable " +
        "(requestId=${requestId ?: "unavailable"})"
) {
    init {
        if (cause != null) initCause(cause)
    }
}

data class V1LogoutOutcome(
    val serverRevoked: Boolean,
    val requestId: String?
)

/**
 * Serializes refresh-token rotation. Concurrent 401 responses that rejected
 * the same access token wait for one rotation and then reuse its result.
 */
class V1AuthSessionCoordinator(
    private val credentialStore: AuthSessionCredentialStore,
    private val refreshSession: (String, IdempotencyKey) -> AuthSessionCredentials,
    private val clock: () -> Instant = Instant::now,
    private val idempotencyKeyProvider: () -> IdempotencyKey = {
        IdempotencyKey.fromGenerated("android-refresh-${UUID.randomUUID()}")
    }
) {
    private val refreshLock = Any()

    @Volatile
    private var session: AuthSessionCredentials? = credentialStore.loadAuthSession()

    fun currentAccessToken(): String? = session?.accessToken

    fun currentSession(): AuthSessionCredentials? = session

    fun currentAccountScope(): String? = session?.principalUserId ?: session?.sessionId

    fun install(session: AuthSessionCredentials): Boolean {
        synchronized(refreshLock) {
            if (!credentialStore.saveAuthSession(session)) {
                this.session = null
                runCatching(credentialStore::clearAuth)
                return false
            }
            this.session = session
            return true
        }
    }

    fun refreshAfterExpiredAccessToken(rejectedAccessToken: String): String {
        synchronized(refreshLock) {
            val latest = session
                ?: throw invalidated(requestId = null)

            // A concurrent request already rotated this token family.
            if (latest.accessToken != rejectedAccessToken) return latest.accessToken

            val refreshToken = latest.usableRefreshToken(clock())
                ?: throw invalidateAndBuildException(requestId = null)
            val rotated = try {
                refreshSession(refreshToken, idempotencyKeyProvider())
            } catch (error: Exception) {
                throw invalidateAndBuildException(error.requestIdOrNull(), error)
            }
            if (!credentialStore.saveAuthSession(rotated)) {
                throw invalidateAndBuildException(requestId = null)
            }
            session = rotated
            return rotated.accessToken
        }
    }

    fun invalidate(requestId: String?, cause: Throwable? = null): V1SessionInvalidatedException {
        synchronized(refreshLock) {
            session = null
            runCatching(credentialStore::clearAuth)
        }
        return invalidated(requestId, cause)
    }

    private fun invalidateAndBuildException(
        requestId: String?,
        cause: Throwable? = null
    ): V1SessionInvalidatedException = invalidate(requestId, cause)

    private fun invalidated(
        requestId: String?,
        cause: Throwable? = null
    ): V1SessionInvalidatedException = V1SessionInvalidatedException(requestId, cause)

    private fun Throwable.requestIdOrNull(): String? =
        (this as? V1HttpException)?.error?.requestId
            ?: (this as? V1ProtocolException)?.requestId
}

/** Contract-specific refresh and logout calls. */
private class V1AuthEndpoints(
    private val publicTransport: V1ApiTransport,
    private val authenticatedTransport: V1ApiTransport
) {
    fun refresh(refreshToken: String, idempotencyKey: IdempotencyKey): AuthSessionCredentials {
        val response = publicTransport.execute<AuthSession>(
            request = V1ApiRequest(
                operationId = "refreshSession",
                method = V1HttpMethod.POST,
                relativePath = "auth/refresh",
                headers = mapOf("Idempotency-Key" to idempotencyKey.wireValue),
                body = RefreshRequest(refreshToken)
            ),
            responseType = AuthSession::class.java
        )
        if (response.statusCode != 200) {
            throw V1ProtocolException(
                operationId = "refreshSession",
                statusCode = response.statusCode,
                requestId = response.meta.requestId,
                reason = "refresh returned unexpected success status"
            )
        }
        val session = response.data
            ?: throw V1ProtocolException(
                operationId = "refreshSession",
                statusCode = response.statusCode,
                requestId = response.meta.requestId,
                reason = "refresh response data is null"
            )
        return session.toCredentials()
    }

    fun logout(accessToken: String, refreshToken: String, idempotencyKey: IdempotencyKey): V1ApiSuccess<Any> {
        val response = authenticatedTransport.executeWithAccessToken<Any>(
            request = V1ApiRequest(
                operationId = "logoutSession",
                method = V1HttpMethod.POST,
                relativePath = "auth/logout",
                headers = mapOf("Idempotency-Key" to idempotencyKey.wireValue),
                body = LogoutRequest(refreshToken)
            ),
            responseType = Any::class.java,
            accessToken = accessToken
        )
        if (response.statusCode != 200 || response.data != null) {
            throw V1ProtocolException(
                operationId = "logoutSession",
                statusCode = response.statusCode,
                requestId = response.meta.requestId,
                reason = "logout response does not match EmptySuccess"
            )
        }
        return response
    }

}

internal fun AuthSession.toCredentials(): AuthSessionCredentials =
    AuthSessionCredentials.fromContract(
        sessionId = sessionId,
        enrollmentId = enrollmentId,
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenType = tokenType.value,
        accessTokenExpiresAt = accessTokenExpiresAt.toString(),
        refreshTokenExpiresAt = refreshTokenExpiresAt.toString(),
        principalUserId = user.id
    )

/**
 * Authenticated facade over [V1ApiTransport]. Only AUTH_TOKEN_EXPIRED is
 * refreshable. Invalid/revoked sessions and a second 401 fail closed.
 */
class V1AuthorizedApiClient private constructor(
    private val transport: V1ApiTransport,
    private val authEndpoints: V1AuthEndpoints,
    private val coordinator: V1AuthSessionCoordinator,
    private val idempotencyKeyProvider: () -> IdempotencyKey
) {
    fun installSession(session: AuthSessionCredentials): Boolean = coordinator.install(session)

    fun currentAccountScope(): String? = coordinator.currentAccountScope()
    fun <T> execute(request: V1ApiRequest, responseType: java.lang.reflect.Type): V1ApiSuccess<T> {
        val rejectedToken = coordinator.currentAccessToken()
        try {
            return transport.executeWithAccessToken(request, responseType, rejectedToken)
        } catch (error: V1HttpException) {
            if (error.shouldInvalidateImmediately()) throw coordinator.invalidate(error.error.requestId, error)
            if (!error.isRefreshable(rejectedToken)) throw error
        }

        val refreshedToken = coordinator.refreshAfterExpiredAccessToken(rejectedToken!!)
        return try {
            transport.executeWithAccessToken(request, responseType, refreshedToken)
        } catch (error: V1HttpException) {
            if (error.statusCode == 401) throw coordinator.invalidate(error.error.requestId, error)
            throw error
        }
    }

    suspend fun <T> executeCancellable(
        request: V1ApiRequest,
        responseType: java.lang.reflect.Type
    ): V1ApiSuccess<T> {
        val rejectedToken = coordinator.currentAccessToken()
        try {
            return transport.executeCancellableWithAccessToken(request, responseType, rejectedToken)
        } catch (error: V1HttpException) {
            if (error.shouldInvalidateImmediately()) throw coordinator.invalidate(error.error.requestId, error)
            if (!error.isRefreshable(rejectedToken)) throw error
        }

        val refreshedToken = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            coordinator.refreshAfterExpiredAccessToken(rejectedToken!!)
        }
        return try {
            transport.executeCancellableWithAccessToken(request, responseType, refreshedToken)
        } catch (error: V1HttpException) {
            if (error.statusCode == 401) throw coordinator.invalidate(error.error.requestId, error)
            throw error
        }
    }

    /** Always clears local secrets, even if remote revocation cannot be confirmed. */
    fun logoutSafely(): V1LogoutOutcome {
        val session = coordinator.currentSession()
        if (session == null) {
            coordinator.invalidate(requestId = null)
            return V1LogoutOutcome(serverRevoked = false, requestId = null)
        }
        var outcome = V1LogoutOutcome(serverRevoked = false, requestId = null)
        try {
            val refreshToken = session.refreshToken ?: return outcome
            val response = authEndpoints.logout(
                accessToken = session.accessToken,
                refreshToken = refreshToken,
                idempotencyKey = idempotencyKeyProvider()
            )
            outcome = V1LogoutOutcome(
                serverRevoked = true,
                requestId = response.meta.requestId
            )
        } catch (error: V1HttpException) {
            outcome = V1LogoutOutcome(false, error.error.requestId)
        } catch (error: V1ProtocolException) {
            outcome = V1LogoutOutcome(false, error.requestId)
        } catch (_: V1TransportException) {
            outcome = V1LogoutOutcome(false, null)
        } catch (_: RuntimeException) {
            outcome = V1LogoutOutcome(false, null)
        } finally {
            coordinator.invalidate(outcome.requestId)
        }
        return outcome
    }

    private fun V1HttpException.isRefreshable(rejectedToken: String?): Boolean =
        statusCode == 401 &&
            error.code.value == "AUTH_TOKEN_EXPIRED" &&
            !rejectedToken.isNullOrBlank()

    private fun V1HttpException.shouldInvalidateImmediately(): Boolean =
        error.code.value in ImmediateInvalidationCodes

    companion object {
        private val ImmediateInvalidationCodes = setOf(
            "AUTH_TOKEN_INVALID",
            "AUTH_SESSION_REVOKED",
            "AUTH_ACCOUNT_DISABLED"
        )

        fun create(
            credentialStore: AuthSessionCredentialStore,
            baseUrl: String = BuildConfig.BNBU_API_BASE_URL,
            httpClient: OkHttpClient = SharedHttpClient.instance,
            gson: Gson = V1Json.gson,
            clock: () -> Instant = Instant::now,
            requestIdProvider: () -> String = { "android-${UUID.randomUUID()}" },
            idempotencyKeyProvider: () -> IdempotencyKey = {
                IdempotencyKey.fromGenerated("android-auth-${UUID.randomUUID()}")
            }
        ): V1AuthorizedApiClient {
            lateinit var coordinator: V1AuthSessionCoordinator
            val authenticatedTransport = V1ApiTransport(
                baseUrl = baseUrl,
                httpClient = httpClient,
                gson = gson,
                accessTokenProvider = { coordinator.currentAccessToken() },
                requestIdProvider = requestIdProvider
            )
            val publicTransport = V1ApiTransport(
                baseUrl = baseUrl,
                httpClient = httpClient,
                gson = gson,
                requestIdProvider = requestIdProvider
            )
            val endpoints = V1AuthEndpoints(publicTransport, authenticatedTransport)
            coordinator = V1AuthSessionCoordinator(
                credentialStore = credentialStore,
                refreshSession = endpoints::refresh,
                clock = clock,
                idempotencyKeyProvider = idempotencyKeyProvider
            )
            return V1AuthorizedApiClient(
                transport = authenticatedTransport,
                authEndpoints = endpoints,
                coordinator = coordinator,
                idempotencyKeyProvider = idempotencyKeyProvider
            )
        }
    }
}
