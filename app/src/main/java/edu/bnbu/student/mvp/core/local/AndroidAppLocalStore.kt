package edu.bnbu.student.mvp.core.local

import android.annotation.SuppressLint
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

// Sensitive writes and destructive auth cleanup must be durable before the
// operation reports success. Callers perform normal writes on Dispatchers.IO;
// logout intentionally commits its small preference deletion synchronously.
@SuppressLint("ApplySharedPref")
class AndroidAppLocalStore(
    context: Context,
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        StoreName,
        Context.MODE_PRIVATE
    )

    // ── Encrypted private-data store ─────────────────────────────
    // Authentication, profile, workspace and draft data are encrypted at rest
    // using an Android Keystore-backed AES/GCM key. Sensitive values are never
    // written in plaintext when the Keystore is unavailable.
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
            val encrypted = encrypt(token) ?: return false
            val committed = encryptedPrefs.edit()
                .putString(AuthTokenEncryptedKey, encrypted.value)
                .putString(AuthTokenIvKey, encrypted.iv)
                .commit()
            if (committed) preferences.edit().remove(AuthTokenKey).commit()
            committed
        } catch (_: RuntimeException) { false }
    }

    fun loadAuthToken(): String? {
        return try {
            val encryptedValue = encryptedPrefs.getString(AuthTokenEncryptedKey, null)
            val iv = encryptedPrefs.getString(AuthTokenIvKey, null)
            if (encryptedValue != null && iv != null) {
                decrypt(encryptedValue, iv).also { decrypted ->
                    if (decrypted == null) clearEncryptedAuthToken()
                }
            } else {
                // One-time migration from legacy plaintext storage. If secure
                // migration is impossible, discard the token rather than expose it.
                val legacyToken = preferences.getString(AuthTokenKey, null)
                if (legacyToken != null && saveAuthToken(legacyToken)) legacyToken else {
                    preferences.edit().remove(AuthTokenKey).commit()
                    null
                }
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    fun saveUserProfile(userProfileJson: String): Boolean {
        return saveSensitiveString(UserProfileKey, userProfileJson)
    }

    fun loadUserProfileJson(): String? {
        return readSensitiveString(UserProfileKey)
    }

    fun saveLastSyncTime(timestamp: String): Boolean {
        return try {
            preferences.edit().putString(LastSyncKey, timestamp).apply()
            true
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
            preferences.edit().putString(ThemeModeKey, mode.storageValue).apply()
            true
        } catch (_: RuntimeException) { false }
    }

    fun clearAuth() {
        preferences.edit()
            .remove(AuthTokenKey)
            .remove(UserProfileKey)
            .commit()
        encryptedPrefs.edit()
            .remove(AuthTokenEncryptedKey)
            .remove(AuthTokenIvKey)
            .remove(encryptedValueKey(UserProfileKey))
            .remove(encryptedIvKey(UserProfileKey))
            .commit()
    }

    fun clearDraft() {
        preferences.edit().remove(DraftStorageKey).apply()
        clearEncryptedValue(DraftStorageKey)
    }

    fun clearAll() {
        preferences.edit()
            .remove(WorkspaceStorageKey)
            .remove(DraftStorageKey)
            .remove(AuthTokenKey)
            .remove(UserProfileKey)
            .remove(LastSyncKey)
            .commit()
        encryptedPrefs.edit().clear().commit()
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
                value = android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP),
                iv = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP)
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
        val json = readSensitiveString(key)
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
        return saveSensitiveString(key, gson.toJson(value))
    }

    private fun saveSensitiveString(key: String, value: String): Boolean {
        return try {
            val encrypted = encrypt(value) ?: return false
            val committed = encryptedPrefs.edit()
                .putString(encryptedValueKey(key), encrypted.value)
                .putString(encryptedIvKey(key), encrypted.iv)
                .commit()
            if (committed) preferences.edit().remove(key).commit()
            committed
        } catch (_: RuntimeException) {
            false
        }
    }

    private fun readSensitiveString(key: String): String? {
        return try {
            val encryptedValue = encryptedPrefs.getString(encryptedValueKey(key), null)
            val iv = encryptedPrefs.getString(encryptedIvKey(key), null)
            if (encryptedValue != null && iv != null) {
                decrypt(encryptedValue, iv).also { decrypted ->
                    if (decrypted == null) clearEncryptedValue(key)
                }
            } else {
                // Migrate older plaintext app data once. Never continue using a
                // plaintext value if it cannot be protected by the Keystore.
                val legacyValue = preferences.getString(key, null) ?: return null
                if (saveSensitiveString(key, legacyValue)) legacyValue else {
                    preferences.edit().remove(key).commit()
                    null
                }
            }
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun clearEncryptedAuthToken() {
        encryptedPrefs.edit()
            .remove(AuthTokenEncryptedKey)
            .remove(AuthTokenIvKey)
            .commit()
    }

    private fun clearEncryptedValue(key: String) {
        encryptedPrefs.edit()
            .remove(encryptedValueKey(key))
            .remove(encryptedIvKey(key))
            .commit()
    }

    private fun encryptedValueKey(key: String): String = "$key.encrypted"

    private fun encryptedIvKey(key: String): String = "$key.iv"

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
