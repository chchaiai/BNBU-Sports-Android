package edu.bnbu.student.mvp.core.network.v1

import java.security.MessageDigest

data class CursorScope private constructor(
    val accountScope: String,
    val operationId: String,
    private val queryFingerprint: String
) {
    companion object {
        fun forQuery(
            accountScope: String,
            operationId: String,
            canonicalQuery: String
        ): CursorScope {
            require(accountScope.isNotBlank()) { "accountScope must not be blank" }
            require(operationId.isNotBlank()) { "operationId must not be blank" }
            val material = "$operationId\n$canonicalQuery".toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(material)
                .joinToString("") { byte ->
                    (byte.toInt() and 0xff).toString(16).padStart(2, '0')
                }
            return CursorScope(accountScope, operationId, digest)
        }
    }
}

class ScopedCursor private constructor(
    private val scope: CursorScope,
    private val wireValue: String
) {
    fun queryValueFor(currentScope: CursorScope): String {
        require(currentScope == scope) {
            "Cursor cannot be reused across accounts, operations, filters, or sort order"
        }
        return wireValue
    }

    override fun equals(other: Any?): Boolean =
        other is ScopedCursor && scope == other.scope && wireValue == other.wireValue

    override fun hashCode(): Int = 31 * scope.hashCode() + wireValue.hashCode()

    override fun toString(): String = "[opaque cursor]"

    companion object {
        fun fromServer(scope: CursorScope, value: String): ScopedCursor {
            require(value.length in 1..2048) { "Cursor must contain 1..2048 characters" }
            return ScopedCursor(scope, value)
        }
    }
}

fun V1ApiRequest.withCursor(cursor: ScopedCursor?, scope: CursorScope): V1ApiRequest {
    require(method.isReadOnly) { "Cursor is only valid for read-only list requests" }
    require(operationId == scope.operationId) { "Cursor scope operationId does not match request" }
    if (cursor == null) return this
    require("cursor" !in query) { "Cursor query must be supplied through ScopedCursor" }
    return copy(query = query + ("cursor" to cursor.queryValueFor(scope)))
}
