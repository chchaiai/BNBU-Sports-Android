package edu.bnbu.student.mvp

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import edu.bnbu.student.mvp.core.data.ApiStudentRepository
import edu.bnbu.student.mvp.core.designsystem.BNBUStudentTheme
import edu.bnbu.student.mvp.core.local.AndroidAppLocalStore
import edu.bnbu.student.mvp.core.state.StudentAppState
import edu.bnbu.student.mvp.feature.shell.AppRootScreen

class MainActivity : ComponentActivity() {
    private val appStateViewModel: StudentAppStateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appState = appStateViewModel.appState

        setContent {
            BNBUStudentTheme(themeMode = appState.themeMode) {
                AppRootScreen(
                    appState = appState,
                    isRestoringSession = appStateViewModel.isRestoringSession
                )
            }
        }
    }
}

class StudentAppStateViewModel(application: Application) : AndroidViewModel(application) {
    val appState = StudentAppState(
        localStore = AndroidAppLocalStore(application),
        cacheDir = application.cacheDir
    )

    var isRestoringSession by mutableStateOf(true)
        private set

    init {
        ApiStudentRepository.initContext(application)
        appState.tryRestoreSession {
            isRestoringSession = false
        }
    }

    override fun onCleared() {
        appState.destroy()
        super.onCleared()
    }
}
