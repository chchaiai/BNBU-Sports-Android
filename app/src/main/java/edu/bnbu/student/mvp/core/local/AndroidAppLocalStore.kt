package edu.bnbu.student.mvp.core.local

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import edu.bnbu.student.mvp.core.model.CheckInDraft
import edu.bnbu.student.mvp.core.model.AppThemeMode
import edu.bnbu.student.mvp.core.model.StudentTaskList
import edu.bnbu.student.mvp.core.model.StudentWorkspace
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidAppLocalStore(
    context: Context,
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        StoreName,
        Context.MODE_PRIVATE
    )

    // ── Encrypted token store (AND-005) ─────────────────────────
    // Tokens are encrypted at rest using Android Keystore-backed AES/GCM.
    // Fallback: if encryption fails, store in SharedPreferences only as
    // a transient session-cache (cleared on logout).
    private val encryptedPrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            "bnbu.student.secure.v1",
            Context.MODE_PRIVATE
        )

    fun loadWorkspace(): StudentWorkspace? = readWorkspace().value

    fun readWorkspace(): LocalStoreReadResult<StudentWorkspace> {
        val raw = read(WorkspaceStorageKey, StudentWorkspace::class.java)
        if (raw.value == null) return raw
        val sanitized = ensureWorkspaceDefaults(raw.value)
        return raw.copy(value = sanitized)
    }

    fun saveWorkspace(workspace: StudentWorkspace): Boolean {
        return save(WorkspaceStorageKey, workspace)
    }

    fun loadDraft(): CheckInDraft? = readDraft().value

    fun readDraft(): LocalStoreReadResult<CheckInDraft> {
        return read(DraftStorageKey, CheckInDraft::class.java)
    }

    fun saveDraft(draft: CheckInDraft): Boolean {
        return save(DraftStorageKey, draft)
    }

    fun saveAuthToken(token: String): Boolean {
        return try {
            val encrypted = encrypt(token)
            if (encrypted != null) {
                // Store encrypted token + IV
                encryptedPrefs.edit()
                    .putString(AuthTokenEncryptedKey, encrypted.value)
                    .putString(AuthTokenIvKey, encrypted.iv)
                    .commit()
            } else {
                // Fallback: plain SharedPreferences (cleared on logout)
                preferences.edit().putString(AuthTokenKey, token).commit()
            }
        } catch (_: RuntimeException) { false }
    }

    fun loadAuthToken(): String? {
        return try {
            val encryptedValue = encryptedPrefs.getString(AuthTokenEncryptedKey, null)
            val iv = encryptedPrefs.getString(AuthTokenIvKey, null)
            if (encryptedValue != null && iv != null) {
                decrypt(encryptedValue, iv)
            } else {
                // Fallback to plain storage for legacy data
                preferences.getString(AuthTokenKey, null)
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    fun saveUserProfile(userProfileJson: String): Boolean {
        return try {
            preferences.edit().putString(UserProfileKey, userProfileJson).commit()
        } catch (_: RuntimeException) { false }
    }

    fun loadUserProfileJson(): String? {
        return preferences.getString(UserProfileKey, null)
    }

    fun saveLastSyncTime(timestamp: String): Boolean {
        return try {
            preferences.edit().putString(LastSyncKey, timestamp).commit()
        } catch (_: RuntimeException) { false }
    }

    fun loadLastSyncTime(): String? {
        return preferences.getString(LastSyncKey, null)
    }

    fun loadThemeMode(): AppThemeMode {
        return AppThemeMode.fromStorage(preferences.getString(ThemeModeKey, null))
    }

    fun saveThemeMode(mode: AppThemeMode): Boolean {
        return try {
            preferences.edit().putString(ThemeModeKey, mode.storageValue).commit()
        } catch (_: RuntimeException) { false }
    }

    fun clearAuth() {
        preferences.edit()
            .remove(AuthTokenKey)
            .remove(UserProfileKey)
            .apply()
        encryptedPrefs.edit()
            .remove(AuthTokenEncryptedKey)
            .remove(AuthTokenIvKey)
            .apply()
    }

    fun clearDraft() {
        preferences.edit().remove(DraftStorageKey).apply()
    }

    fun clearAll() {
        preferences.edit()
            .remove(WorkspaceStorageKey)
            .remove(DraftStorageKey)
            .remove(AuthTokenKey)
            .remove(UserProfileKey)
            .remove(LastSyncKey)
            .apply()
        encryptedPrefs.edit()
            .remove(AuthTokenEncryptedKey)
            .remove(AuthTokenIvKey)
            .apply()
    }

    // ── AES/GCM encryption backed by Android Keystore ─────────────
    private fun encrypt(plaintext: String): EncryptedValue? {
        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv  // 12-byte random IV
            val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            EncryptedValue(
                value = android.util.Base64.encodeToString(encrypted, android.util.Base64.DEFAULT),
                iv = android.util.Base64.encodeToString(iv, android.util.Base64.DEFAULT)
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun decrypt(encryptedValue: String, iv: String): String? {
        return try {
            val secretKey = getOrCreateKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val ivBytes = android.util.Base64.decode(iv, android.util.Base64.DEFAULT)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, ivBytes))
            val decrypted = cipher.doFinal(android.util.Base64.decode(encryptedValue, android.util.Base64.DEFAULT))
            String(decrypted, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        val existing = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    // ── Schema-evolution guard ──────────────────────────────────────
    // Gson bypasses Kotlin data-class constructors via UnsafeAllocator,
    // so `= emptyList()` defaults are never applied for fields added
    // AFTER the app was last launched. Old cached JSON leaves them null.
    private fun ensureWorkspaceDefaults(ws: StudentWorkspace): StudentWorkspace {
        val teachersNull = try {
            val f = StudentWorkspace::class.java.getDeclaredField("teachers")
            f.isAccessible = true
            f.get(ws) == null
        } catch (_: NoSuchFieldException) { false }

        val syncOpsNull = try {
            val f = StudentWorkspace::class.java.getDeclaredField("syncOperations")
            f.isAccessible = true
            f.get(ws) == null
        } catch (_: NoSuchFieldException) { false }

        val exemptionsNull = try {
            val f = StudentWorkspace::class.java.getDeclaredField("exemptions")
            f.isAccessible = true
            f.get(ws) == null
        } catch (_: NoSuchFieldException) { false }

        val studentTasksNull = try {
            val f = StudentWorkspace::class.java.getDeclaredField("studentTasks")
            f.isAccessible = true
            f.get(ws) == null
        } catch (_: NoSuchFieldException) { false }

        if (!teachersNull && !syncOpsNull && !exemptionsNull && !studentTasksNull) return ws

        return ws.copy(
            teachers = if (teachersNull) emptyList() else ws.teachers,
            syncOperations = if (syncOpsNull) emptyList() else ws.syncOperations,
            exemptions = if (exemptionsNull) emptyList() else ws.exemptions,
            studentTasks = if (studentTasksNull) StudentTaskList(emptyList(), emptyList()) else ws.studentTasks
        )
    }

    private fun <T> read(key: String, clazz: Class<T>): LocalStoreReadResult<T> {
        val json = preferences.getString(key, null)
            ?: return LocalStoreReadResult(value = null, status = LocalStoreReadStatus.Missing)

        return try {
            val value = gson.fromJson(json, clazz)
            if (value == null) {
                LocalStoreReadResult(value = null, status = LocalStoreReadStatus.DecodeFailed)
            } else {
                LocalStoreReadResult(value = value, status = LocalStoreReadStatus.Loaded)
            }
        } catch (_: RuntimeException) {
            LocalStoreReadResult(value = null, status = LocalStoreReadStatus.DecodeFailed)
        }
    }

    private fun save(key: String, value: Any): Boolean {
        return try {
            preferences.edit().putString(key, gson.toJson(value)).commit()
        } catch (_: RuntimeException) {
            false
        }
    }

    private data class EncryptedValue(val value: String, val iv: String)

    companion object {
        const val StoreName = "bnbu.student.local.v1"
        const val WorkspaceStorageKey = "bnbu.student.workspace.v1"
        const val DraftStorageKey = "bnbu.student.checkin.draft.v1"
        const val AuthTokenKey = "bnbu.student.auth.token.v1"
        const val UserProfileKey = "bnbu.student.auth.profile.v1"
        const val LastSyncKey = "bnbu.student.last_sync.v1"
        const val ThemeModeKey = "bnbu.student.theme.mode.v1"

        // Encrypted token storage keys
        private const val AuthTokenEncryptedKey = "bnbu.student.auth.token.encrypted"
        private const val AuthTokenIvKey = "bnbu.student.auth.token.iv"

        private const val KEY_ALIAS = "bnbu_student_auth_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
