package edu.bnbu.student.mvp

import android.app.Application
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import edu.bnbu.student.mvp.core.designsystem.AppleTextButton as TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import edu.bnbu.student.mvp.core.data.ApiStudentRepository
import edu.bnbu.student.mvp.core.designsystem.BNBUStudentTheme
import edu.bnbu.student.mvp.core.local.AndroidAppLocalStore
import edu.bnbu.student.mvp.core.local.AppLanguagePreferences
import edu.bnbu.student.mvp.core.network.MinimumAppVersionResponse
import edu.bnbu.student.mvp.core.network.SharedHttpClient
import edu.bnbu.student.mvp.core.network.StudentApiClient
import edu.bnbu.student.mvp.core.network.StudentEndpoint
import edu.bnbu.student.mvp.core.network.SystemHealthResponse
import edu.bnbu.student.mvp.core.model.SystemMode
import edu.bnbu.student.mvp.core.model.SystemModeStatus
import edu.bnbu.student.mvp.core.state.StudentAppState
import edu.bnbu.student.mvp.feature.shell.AppRootScreen
import edu.bnbu.student.mvp.feature.checkin.session.ExerciseSessionController
import edu.bnbu.student.mvp.feature.checkin.session.SessionMediaUploadCoordinator
import edu.bnbu.student.mvp.feature.checkin.session.SessionVideoCompressor
import edu.bnbu.student.mvp.core.network.v1.PrivateExerciseMediaObjectUploader
import edu.bnbu.student.mvp.core.network.v1.V1AuthorizedApiClient
import edu.bnbu.student.mvp.core.network.v1.V1ExerciseMediaUploadGateway
import edu.bnbu.student.mvp.core.network.v1.createV1ExerciseGateway
import edu.bnbu.student.mvp.R
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import java.io.File
import kotlinx.coroutines.CancellationException

class MainActivity : ComponentActivity() {
    private val appStateViewModel: StudentAppStateViewModel by viewModels()
    private val appUpdateManager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(this) }
    private var isInitialTargetReady = false
    private var isPlayUpdateReady by mutableStateOf(false)
    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            isPlayUpdateReady = true
        }
    }
    private val playUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { /* The Play dialog already handles cancellation and errors for flexible updates. */ }
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Denial leaves the in-app notification center fully usable. */ }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguagePreferences.localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            shouldKeepSystemSplash(
                sessionRestoreComplete = !appStateViewModel.isRestoringSession,
                privacyConsentChecked = appStateViewModel.isPrivacyConsentChecked,
                initialTargetReady = isInitialTargetReady
            )
        }
        super.onCreate(savedInstanceState)
        val appState = appStateViewModel.appState
        appUpdateManager.registerListener(installStateUpdatedListener)
        checkForPlayUpdate()

        setContent {
            var updateRequirement by remember { mutableStateOf<UpdateRequirement?>(null) }
            LaunchedEffect(Unit) {
                updateRequirement = checkMinimumVersion()
            }

            LaunchedEffect(Unit) {
                appState.updateSystemMode(checkSystemMode())
            }

            val startupInputsReady =
                !appStateViewModel.isRestoringSession &&
                    appStateViewModel.isPrivacyConsentChecked
            if (startupInputsReady) {
                BNBUStudentTheme(themeMode = appState.themeMode) {
                    AppRootScreen(
                        appState = appState,
                        exerciseSessionController = appStateViewModel.exerciseSessionController,
                        localStore = appStateViewModel.localStore,
                        initialPrivacyConsentRequired =
                            appStateViewModel.isPrivacyConsentRequired,
                        onPrivacyConsentAccepted =
                            appStateViewModel::markPrivacyConsentAccepted,
                        onInitialTargetReady = { isInitialTargetReady = true },
                        onRequestNotificationPermission = ::requestNotificationPermissionIfNeeded
                    )

                    updateRequirement?.let { requirement ->
                        UpdateRequiredDialog(
                            requirement = requirement,
                            onUpdate = { openUpdateUrl(requirement.downloadUrl) }
                        )
                    }

                    if (updateRequirement == null && isPlayUpdateReady) {
                        PlayUpdateReadyDialog(onRestart = ::completePlayUpdate)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // A flexible update may finish while the app is in the background.
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                isPlayUpdateReady = true
            }
        }
    }

    override fun onDestroy() {
        appUpdateManager.unregisterListener(installStateUpdatedListener)
        super.onDestroy()
    }

    /**
     * Starts Google Play's optional, in-app update prompt when this install belongs to a
     * Play track with a newer version. Sideloaded/debug installs simply receive no prompt.
     */
    private fun checkForPlayUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    playUpdateLauncher,
                    AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE)
                )
            }
        }
    }

    private fun completePlayUpdate() {
        isPlayUpdateReady = false
        appUpdateManager.completeUpdate()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * Checks the public minimum-version configuration. Failures intentionally leave the
     * application usable: a transient configuration/network failure must not lock out users.
     */
    private suspend fun checkMinimumVersion(): UpdateRequirement? {
        return try {
            val apiClient = StudentApiClient()
            val response = apiClient.executeAndParseCancellable(
                apiClient.request(StudentEndpoint.MinimumAppVersion),
                MinimumAppVersionResponse::class.java
            )
            val minimumVersion = response.minimumVersion.trim()
            if (minimumVersion.isNotEmpty() &&
                compareVersions(BuildConfig.VERSION_NAME, minimumVersion) < 0
            ) {
                UpdateRequirement(
                    minimumVersion = minimumVersion,
                    downloadUrl = response.downloadUrl.trim(),
                    updateMessage = response.updateMessage.trim()
                )
            } else {
                null
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    /** A missing health-mode field keeps the app in NORMAL during staged backend rollout. */
    private suspend fun checkSystemMode(): SystemModeStatus {
        return try {
            val apiClient = StudentApiClient()
            val response = apiClient.executeAndParseCancellable(
                apiClient.request(StudentEndpoint.Health),
                SystemHealthResponse::class.java
            )
            SystemModeStatus(
                mode = SystemMode.from(response.systemMode),
                message = response.maintenanceMessage.trim(),
                estimatedRecoveryTime = response.estimatedRecoveryTime?.trim()?.takeIf { it.isNotEmpty() },
                plannedMaintenanceAt = response.plannedMaintenanceAt?.trim()?.takeIf { it.isNotEmpty() }
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            SystemModeStatus()
        }
    }

    private fun openUpdateUrl(downloadUrl: String) {
        if (downloadUrl.isBlank()) return
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
        }
    }
}

private data class UpdateRequirement(
    val minimumVersion: String,
    val downloadUrl: String,
    val updateMessage: String
)

@androidx.compose.runtime.Composable
private fun UpdateRequiredDialog(
    requirement: UpdateRequirement,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text(stringResource(R.string.update_required_title)) },
        text = {
            Text(
                stringResource(
                    R.string.update_required_message,
                    BuildConfig.VERSION_NAME,
                    requirement.minimumVersion,
                    requirement.updateMessage.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate, enabled = requirement.downloadUrl.isNotBlank()) {
                Text(stringResource(R.string.update_now))
            }
        }
    )
}

@androidx.compose.runtime.Composable
private fun PlayUpdateReadyDialog(onRestart: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text(stringResource(R.string.play_update_ready_title)) },
        text = { Text(stringResource(R.string.play_update_ready_message)) },
        confirmButton = {
            TextButton(onClick = onRestart) {
                Text(stringResource(R.string.play_update_restart))
            }
        }
    )
}

internal fun shouldKeepSystemSplash(
    sessionRestoreComplete: Boolean,
    privacyConsentChecked: Boolean,
    initialTargetReady: Boolean
): Boolean = !sessionRestoreComplete || !privacyConsentChecked || !initialTargetReady

/** Compares numeric dot-separated version components, ignoring build suffixes such as -debug. */
internal fun compareVersions(currentVersion: String, minimumVersion: String): Int {
    val current = versionComponents(currentVersion)
    val minimum = versionComponents(minimumVersion)
    val length = maxOf(current.size, minimum.size)
    for (index in 0 until length) {
        val comparison = (current.getOrElse(index) { 0 }).compareTo(minimum.getOrElse(index) { 0 })
        if (comparison != 0) return comparison
    }
    return 0
}

private fun versionComponents(version: String): List<Int> = version
    .trim()
    .removePrefix("v")
    .removePrefix("V")
    .substringBefore('-')
    .split('.')
    .map { component -> component.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

class StudentAppStateViewModel(application: Application) : AndroidViewModel(application) {
    internal val localStore = AndroidAppLocalStore(application)

    val appState = StudentAppState(
        localStore = localStore,
        cacheDir = application.cacheDir
    )

    internal val exerciseSessionController = ExerciseSessionController(
        localStore = localStore,
        mediaRootDirectory = File(application.filesDir, "exercise_session_drafts"),
        exerciseGatewayProvider = { createV1ExerciseGateway(localStore) },
        mediaUploadCoordinatorProvider = {
            val authorizedClient = V1AuthorizedApiClient.create(localStore)
            SessionMediaUploadCoordinator(
                gateway = V1ExerciseMediaUploadGateway(authorizedClient),
                objectUploader = PrivateExerciseMediaObjectUploader(SharedHttpClient.instance)
            )
        },
        videoCompressor = SessionVideoCompressor(application)
    )

    var isRestoringSession by mutableStateOf(true)
        private set

    var isPrivacyConsentChecked by mutableStateOf(false)
        private set

    var isPrivacyConsentRequired by mutableStateOf(false)
        private set

    init {
        ApiStudentRepository.initContext(application)
        isPrivacyConsentRequired =
            !localStore.hasAgreedPrivacyPolicy(BuildConfig.PRIVACY_POLICY_VERSION)
        isPrivacyConsentChecked = true
        appState.tryRestoreSession {
            isRestoringSession = false
        }
    }

    internal fun markPrivacyConsentAccepted() {
        isPrivacyConsentRequired = false
    }

    override fun onCleared() {
        exerciseSessionController.destroy()
        appState.destroy()
        super.onCleared()
    }
}
