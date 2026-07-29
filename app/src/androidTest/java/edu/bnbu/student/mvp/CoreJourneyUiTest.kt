package edu.bnbu.student.mvp

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.bnbu.student.mvp.core.designsystem.BNBUStudentTheme
import edu.bnbu.student.mvp.core.local.AndroidAppLocalStore
import edu.bnbu.student.mvp.core.mock.MockStudentWorkspace
import edu.bnbu.student.mvp.core.state.StudentAppState
import edu.bnbu.student.mvp.feature.checkin.session.ExerciseSessionController
import edu.bnbu.student.mvp.feature.checkin.session.ExerciseSessionState
import edu.bnbu.student.mvp.feature.shell.AppRootScreen
import edu.bnbu.student.mvp.feature.login.ContactBindingActions
import edu.bnbu.student.mvp.feature.login.ContactBindingMode
import edu.bnbu.student.mvp.feature.login.ContactBindingScreen
import edu.bnbu.student.mvp.feature.login.LocalContactBindingActions
import java.io.File
import java.time.Instant
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue

/**
 * Device/emulator regression coverage for the critical student journey.
 *
 * It uses the explicit local Mock-user entry point, so it neither needs a live
 * backend nor submits a real check-in. A complete submission intentionally
 * remains outside this test because production requires a camera-created proof.
 */
@RunWith(AndroidJUnit4::class)
class CoreJourneyUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var localStore: AndroidAppLocalStore
    private lateinit var exerciseController: ExerciseSessionController

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        localStore = AndroidAppLocalStore(context)
        localStore.clearAll()
        localStore.agreePrivacyPolicy(BuildConfig.PRIVACY_POLICY_VERSION, Instant.now().toString())
        localStore.markPreLoginCourseGuideCompleted()
        localStore.markOnboardingCompleted(MockStudentWorkspace.studentId)
        localStore.markHealthReminderShown(MockStudentWorkspace.studentId)

        val appState = StudentAppState(localStore = localStore, cacheDir = context.cacheDir)
        exerciseController = ExerciseSessionController(
            localStore = localStore,
            mediaRootDirectory = File(context.cacheDir, "core-journey-ui-test")
        )

        composeRule.setContent {
            BNBUStudentTheme {
                AppRootScreen(
                    appState = appState,
                    exerciseSessionController = exerciseController,
                    localStore = localStore
                )
            }
        }
    }

    @After
    fun tearDown() {
        exerciseController.destroy()
        localStore.clearAll()
    }

    @Test
    fun mockLogin_canStartCheckIn_andOpenGrades() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("login.mockUser").assertIsEnabled()
            }.isSuccess
        }
        composeRule.onNodeWithTag("login.mockUser").assertIsEnabled().performClick()

        val checkInLabel = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.navigation_checkin)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(checkInLabel) and hasClickAction())
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasText(checkInLabel) and hasClickAction()).performClick()
        composeRule.onNodeWithTag("screen.checkIn").assertIsDisplayed()

        val gradesLabel = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.navigation_grades)
        composeRule.onNode(hasText(gradesLabel) and hasClickAction()).performClick()
        composeRule.onNodeWithTag("screen.grades").assertExists().assertIsDisplayed()

        composeRule.onNode(hasText(checkInLabel) and hasClickAction()).performClick()
        composeRule.onNodeWithTag("checkIn.startExercise").assertIsEnabled().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            exerciseController.state is ExerciseSessionState.Active
        }
    }

    @Test
    fun bottomNavigation_switchesRepeatedlyAcrossAllTabs() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("login.mockUser").assertIsEnabled()
            }.isSuccess
        }
        composeRule.onNodeWithTag("login.mockUser").performClick()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tabLabels = listOf(
            context.getString(R.string.navigation_dashboard),
            context.getString(R.string.navigation_courses),
            context.getString(R.string.navigation_checkin),
            context.getString(R.string.navigation_grades),
            context.getString(R.string.navigation_profile)
        )
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(hasText(tabLabels.first()) and hasClickAction())
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Exercise quick, repeated hand-offs while selection animations are active.
        repeat(3) {
            tabLabels.forEach { label ->
                composeRule.onNode(hasText(label) and hasClickAction()).performClick()
            }
        }

        composeRule.onNode(hasText(tabLabels.last()) and hasClickAction()).assertIsSelected()
    }

    @Test
    fun requiredActivation_isFocusedAndDoesNotOfferWorkspaceNavigation() {
        composeRule.setContent {
            BNBUStudentTheme {
                CompositionLocalProvider(
                    LocalContactBindingActions provides ContactBindingActions(
                        sendEmailCode = { _, callback -> callback(Result.success(Unit)) },
                        verifyEmailCode = { _, _, callback -> callback(Result.failure(IllegalStateException())) },
                        sendPhoneCode = { _, callback -> callback(Result.success(Unit)) },
                        verifyPhoneCode = { _, _, callback -> callback(Result.failure(IllegalStateException())) }
                    )
                ) {
                    ContactBindingScreen(
                        mode = ContactBindingMode.RequiredActivation,
                        onBindingComplete = {},
                        onLogout = {},
                        onOpenPrivacy = {},
                        onOpenHelp = {}
                    )
                }
            }
        }

        composeRule.onNodeWithTag("screen.contactActivation").assertIsDisplayed()
        composeRule.onNodeWithTag("contactActivation.logout").assertIsDisplayed()
        composeRule.onNodeWithTag("contactActivation.privacy").assertIsDisplayed()
        composeRule.onNodeWithTag("contactActivation.help").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("contactBinding.back").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("screen.checkIn").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun managedContacts_canReplaceAnAlreadyVerifiedMethod() {
        composeRule.setContent {
            BNBUStudentTheme {
                CompositionLocalProvider(
                    LocalContactBindingActions provides ContactBindingActions(
                        sendEmailCode = { _, callback -> callback(Result.success(Unit)) },
                        verifyEmailCode = { _, _, callback -> callback(Result.success(Unit)) },
                        sendPhoneCode = { _, callback -> callback(Result.success(Unit)) },
                        verifyPhoneCode = { _, _, callback -> callback(Result.success(Unit)) }
                    )
                ) {
                    ContactBindingScreen(
                        mode = ContactBindingMode.ManageContacts,
                        onBindingComplete = {},
                        onBack = {},
                        initialEmail = "s***@example.edu.cn",
                        initialEmailVerified = true
                    )
                }
            }
        }

        composeRule.onNodeWithTag("contactBinding.email.change").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("contactBinding.email.value").assertIsDisplayed()
    }
}
