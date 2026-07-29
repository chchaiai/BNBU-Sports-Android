package edu.bnbu.student.mvp.core.push

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import edu.bnbu.student.mvp.BuildConfig
import edu.bnbu.student.mvp.core.network.PushDeviceRegistrationRequest
import edu.bnbu.student.mvp.core.network.StudentApiClient
import edu.bnbu.student.mvp.core.network.StudentEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Synchronizes only the opaque FCM registration token, never student data. */
object FcmPushRegistrar {
    suspend fun registerCurrentDevice(context: Context, apiClient: StudentApiClient): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                check(FirebaseApp.initializeApp(context.applicationContext) != null) {
                    "Firebase is not configured"
                }
                val token = Tasks.await(FirebaseMessaging.getInstance().token).trim()
                check(token.isNotEmpty()) { "FCM returned an empty token" }
                apiClient.executeCancellable(
                    apiClient.request(
                        StudentEndpoint.RegisterPushDevice,
                        PushDeviceRegistrationRequest(token = token, appVersion = BuildConfig.VERSION_NAME)
                    )
                )
                Unit
            }
        }

    suspend fun unregisterCurrentDevice(context: Context, apiClient: StudentApiClient): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (FirebaseApp.initializeApp(context.applicationContext) == null) return@runCatching
                val token = Tasks.await(FirebaseMessaging.getInstance().token).trim()
                if (token.isEmpty()) return@runCatching
                apiClient.executeCancellable(
                    apiClient.request(
                        StudentEndpoint.UnregisterPushDevice,
                        PushDeviceRegistrationRequest(token = token, appVersion = BuildConfig.VERSION_NAME)
                    )
                )
                Unit
            }
        }
}
