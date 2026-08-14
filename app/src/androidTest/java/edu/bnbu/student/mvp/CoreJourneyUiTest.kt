package edu.bnbu.student.mvp

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.bnbu.student.mvp.core.designsystem.BNBUStudentTheme
import edu.bnbu.student.mvp.core.local.AndroidAppLocalStore
import edu.bnbu.student.mvp.core.state.StudentAppState
import edu.bnbu.student.mvp.feature.checkin.session.ExerciseSessionController
import edu.bnbu.student.mvp.feature.shell.AppRootScreen
import edu.bnbu.student.mvp.feature.login.ContactBindingMode
import edu.bnbu.student.mvp.feature.login.ContactBindingScreen
import edu.bnbu.student.mvp.feature.courses.CourseJoinConfirmScreen
import edu.bnbu.student.mvp.feature.courses.CourseJoinInfo
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
 * Authentication and enrollment remain server-owned. These device tests verify
 * the formal pre-authentication and activation surfaces without manufacturing a
 * local student session. Full submission still requires a camera and backend.
 */
@RunWith(AndroidJUnit4::class)
class CoreJourneyUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var localStore: AndroidAppLocalStore
    private lateinit var appState: StudentAppState
    private lateinit var exerciseController: ExerciseSessionController

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        localStore = AndroidAppLocalStore(context)
        localStore.clearAll()
        localStore.agreePrivacyPolicy(BuildConfig.PRIVACY_POLICY_VERSION, Instant.now().toString())
        localStore.markPreLoginCourseGuideCompleted()

        appState = StudentAppState(localStore = localStore, cacheDir = context.cacheDir)
        exerciseController = ExerciseSessionController(
            localStore = localStore,
            mediaRootDirectory = File(context.cacheDir, "core-journey-ui-test")
        )
    }

    private fun setAppRootContent() {
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
    fun formalLogin_exposesOnlyServerBackedEntryPoints() {
        setAppRootContent()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("login.email").assertIsEnabled()
            }.isSuccess
        }
        composeRule.onNodeWithTag("login.email").assertIsEnabled().assertIsDisplayed()
        composeRule.onNodeWithTag("login.scanJoin").assertIsEnabled().assertIsDisplayed()
        composeRule.onNodeWithTag("login.recoveryRequest").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("login.mockUser").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("screen.checkIn").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun scanEntry_opensTheRealQrResolverSurface() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.CAMERA
        )
        setAppRootContent()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag("login.scanJoin").assertIsEnabled()
            }.isSuccess
        }
        composeRule.onNodeWithTag("login.scanJoin").performClick()
        composeRule.onNodeWithTag("screen.courseJoin.scan").assertIsDisplayed()
        composeRule.onNodeWithTag("courseJoin.scan.camera").assertIsDisplayed()
        composeRule.onNodeWithTag("courseJoin.scan.manualInput").assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag("courseJoin.scan.simulateSuccess")
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun requiredActivation_isFocusedAndDoesNotOfferWorkspaceNavigation() {
        composeRule.setContent {
            BNBUStudentTheme {
                ContactBindingScreen(
                    mode = ContactBindingMode.RequiredActivation,
                    localStore = localStore,
                    currentEmailMasked = null,
                    currentEmailVerified = false,
                    expectedUserVersion = 1,
                    onCurrentUserUpdated = {},
                    onBindingComplete = {},
                    onLogout = {},
                    onOpenPrivacy = {},
                    onOpenHelp = {}
                )
            }
        }

        composeRule.onNodeWithTag("screen.emailSecurity").assertIsDisplayed()
        composeRule.onNodeWithTag("emailSecurity.logout").assertIsDisplayed()
        composeRule.onNodeWithTag("emailSecurity.privacy").assertIsDisplayed()
        composeRule.onNodeWithTag("emailSecurity.help").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("emailSecurity.back").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithTag("screen.checkIn").fetchSemanticsNodes().isEmpty())

        composeRule.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen.emailSecurity").assertIsDisplayed()
    }

    @Test
    fun courseJoinForm_hasOnlyMaleAndFemaleAndStartsDisabled() {
        val serverCourse = CourseJoinInfo(
            id = "section-test",
            name = "体育课程",
            courseNumber = "PE-TEST",
            section = "测试教学班",
            teacher = "测试教师",
            semester = "测试学期"
        )
        composeRule.setContent {
            BNBUStudentTheme {
                CourseJoinConfirmScreen(
                    inviteCode = "0123456789abcdef",
                    course = serverCourse,
                    submitCourseJoin = { error("Submit must remain disabled for an empty form") }
                )
            }
        }

        composeRule.onNodeWithTag("screen.courseJoinConfirm").assertIsDisplayed()
        composeRule.onNodeWithTag("courseJoinConfirm.gender.male").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("courseJoinConfirm.gender.female").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithTag("courseJoinConfirm.gender.other").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("courseJoinConfirm.submit").assertIsNotEnabled()
    }

    @Test
    fun managedContacts_canReplaceAnAlreadyVerifiedMethod() {
        composeRule.setContent {
            BNBUStudentTheme {
                ContactBindingScreen(
                    mode = ContactBindingMode.ManageContacts,
                    localStore = localStore,
                    currentEmailMasked = "s***@example.edu.cn",
                    currentEmailVerified = true,
                    expectedUserVersion = 2,
                    onCurrentUserUpdated = {},
                    onBindingComplete = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("screen.emailSecurity").assertIsDisplayed()
        composeRule.onNodeWithTag("emailSecurity.back").assertIsDisplayed()
        composeRule.onNodeWithTag("emailSecurity.newEmail").assertIsDisplayed()
    }
}
