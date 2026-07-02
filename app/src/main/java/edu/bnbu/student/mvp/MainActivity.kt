package edu.bnbu.student.mvp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import edu.bnbu.student.mvp.core.designsystem.BNBUStudentTheme
import edu.bnbu.student.mvp.core.local.AndroidAppLocalStore
import edu.bnbu.student.mvp.core.state.StudentAppState
import edu.bnbu.student.mvp.feature.shell.AppRootScreen

class MainActivity : ComponentActivity() {
    private lateinit var appState: StudentAppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appState = StudentAppState(
            localStore = AndroidAppLocalStore(applicationContext),
            cacheDir = cacheDir
        )

        // Initialize API context for uploads
        edu.bnbu.student.mvp.core.data.ApiStudentRepository.initContext(applicationContext)

        // Attempt session restore before showing login (AND-004)
        appState.tryRestoreSession()

        setContent {
            BNBUStudentTheme {
                AppRootScreen(appState = appState)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::appState.isInitialized) {
            appState.destroy()
        }
    }
}
