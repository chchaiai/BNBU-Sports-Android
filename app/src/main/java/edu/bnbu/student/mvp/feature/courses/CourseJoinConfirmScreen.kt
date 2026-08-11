package edu.bnbu.student.mvp.feature.courses

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import edu.bnbu.student.mvp.core.designsystem.AppleTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import edu.bnbu.student.mvp.core.designsystem.BNBUFormField
import edu.bnbu.student.mvp.core.designsystem.BNBULayout
import edu.bnbu.student.mvp.core.designsystem.BNBUPrimaryButton
import edu.bnbu.student.mvp.core.designsystem.GridBackground
import edu.bnbu.student.mvp.core.designsystem.SegmentedControl
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.ValidationPanel
import edu.bnbu.student.mvp.core.designsystem.interfaceText
import edu.bnbu.student.mvp.core.local.AppLanguagePreferences
import edu.bnbu.student.mvp.core.network.ApiHttpException
import edu.bnbu.student.mvp.core.network.CourseJoinCourseResponse
import edu.bnbu.student.mvp.core.network.CourseJoinMembershipResponse
import edu.bnbu.student.mvp.core.network.CourseJoinRequestBody
import edu.bnbu.student.mvp.core.network.CourseJoinResponse
import edu.bnbu.student.mvp.core.network.CourseJoinStudentResponse
import edu.bnbu.student.mvp.core.network.v1.V1HttpException
import edu.bnbu.student.mvp.core.network.v1.generated.CourseInvitePreview
import edu.bnbu.student.mvp.core.network.v1.generated.CurrentUserData
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val MaxNameLength = 64
private const val MaxStudentNumberLength = 32
private const val MaxGradeLength = 4
private val StudentNumberPattern = Regex("^[A-Za-z0-9][A-Za-z0-9-]{4,31}$")
private val GradeYearPattern = Regex("^\\d{4}$")

/** Course information resolved and validated from a teacher-issued invitation. */
data class CourseJoinInfo(
    val id: String,
    val name: String,
    val courseNumber: String,
    val section: String,
    val teacher: String,
    val semester: String,
    /** True only for the explicitly labelled local direct-join demonstration. */
    val isDemoScanResult: Boolean = false
)

internal fun CourseInvitePreview.toCourseJoinInfo(): CourseJoinInfo = CourseJoinInfo(
    id = classSectionId,
    name = courseName,
    courseNumber = courseCode,
    section = displayName,
    teacher = teacherDisplayName,
    semester = semesterDisplayName
)

sealed interface CourseJoinCompletion {
    val alreadyJoined: Boolean

    data class Authoritative(
        val currentUser: CurrentUserData,
        override val alreadyJoined: Boolean = false
    ) : CourseJoinCompletion

    data class Demo(
        val response: CourseJoinResponse
    ) : CourseJoinCompletion {
        override val alreadyJoined: Boolean
            get() = response.isAlreadyJoined()
    }
}

private enum class JoinGender(val apiValue: String) {
    Male("male"),
    Female("female");

    @Composable
    fun label(): String = when (this) {
        Male -> interfaceText("男", "Male")
        Female -> interfaceText("女", "Female")
    }

    companion object {
        fun from(value: String): JoinGender? = entries.firstOrNull {
            it.apiValue.equals(value.trim(), ignoreCase = true)
        }
    }
}

/**
 * Confirms identity details and atomically creates an active course membership.
 * A successful response is handed to [onJoined] before this screen navigates away.
 */
@Composable
fun CourseJoinConfirmScreen(
    inviteCode: String,
    course: CourseJoinInfo,
    initialName: String = "",
    initialStudentNumber: String = "",
    initialGender: String = "",
    initialGrade: String = "",
    writeEnabled: Boolean = true,
    activeCourseId: String? = null,
    onBack: () -> Unit = {},
    onEnterExistingCourse: () -> Unit = {},
    onJoined: suspend (CourseJoinCompletion) -> Unit = {},
    submitCourseJoin: suspend (CourseJoinRequestBody) -> CourseJoinCompletion
) {
    val appLanguage = AppLanguagePreferences.currentLanguage
    var name by rememberSaveable { mutableStateOf(initialName) }
    var studentNumber by rememberSaveable { mutableStateOf(initialStudentNumber) }
    var genderValue by rememberSaveable { mutableStateOf(JoinGender.from(initialGender)?.apiValue.orEmpty()) }
    var grade by rememberSaveable { mutableStateOf(initialGrade) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var hasSubmitted by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable(appLanguage) { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val nameFocusRequester = remember { FocusRequester() }
    val studentNumberFocusRequester = remember { FocusRequester() }
    val gradeFocusRequester = remember { FocusRequester() }
    val alreadyInThisCourse = activeCourseId != null && activeCourseId == course.id
    val alreadyInAnotherCourse = activeCourseId != null && activeCourseId != course.id
    val normalizedName = name.trim().replace(Regex("\\s+"), " ")
    val normalizedStudentNumber = studentNumber.trim().uppercase()
    val normalizedGrade = grade.trim()
    val nameFieldError = when {
        name.isNotEmpty() && normalizedName.isBlank() -> interfaceText("请输入姓名。", "Enter your name.")
        else -> null
    }
    val studentNumberFieldError = when {
        studentNumber.isNotBlank() && !StudentNumberPattern.matches(normalizedStudentNumber) -> interfaceText(
            "请输入 5–32 位字母、数字或连字符。",
            "Enter 5–32 letters, numbers, or hyphens."
        )
        else -> null
    }
    val gradeFieldError = when {
        grade.isNotBlank() && !GradeYearPattern.matches(normalizedGrade) -> interfaceText(
            "请输入四位数字年份。",
            "Enter a four-digit year."
        )
        else -> null
    }
    val formValid = validateDirectCourseJoin(
        name = normalizedName,
        studentNumber = normalizedStudentNumber,
        gender = genderValue,
        grade = normalizedGrade
    ) == null

    fun submit() {
        if (!writeEnabled || isSubmitting || hasSubmitted || alreadyInThisCourse || alreadyInAnotherCourse) return

        val trimmedName = name.trim().replace(Regex("\\s+"), " ")
        val normalizedStudentNumber = studentNumber.trim().uppercase()
        val trimmedGrade = grade.trim().replace(Regex("\\s+"), " ")
        errorMessage = validateDirectCourseJoin(
            name = trimmedName,
            studentNumber = normalizedStudentNumber,
            gender = genderValue,
            grade = trimmedGrade
        )
        if (errorMessage != null) return

        focusManager.clearFocus(force = true)
        isSubmitting = true
        scope.launch {
            try {
                val body = CourseJoinRequestBody(
                    studentName = trimmedName,
                    studentNumber = normalizedStudentNumber,
                    gender = genderValue,
                    grade = trimmedGrade,
                    inviteCode = inviteCode
                )
                val response = submitCourseJoin(body)
                hasSubmitted = true
                onJoined(response)
                val message = if (response.alreadyJoined) {
                    interfaceText("你已经加入该课程", "You have already joined this course.")
                } else {
                    interfaceText("你已成功加入《${course.name}》", "You have successfully joined ${course.name}.")
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                hasSubmitted = false
                errorMessage = directJoinErrorMessage(error)
            } finally {
                isSubmitting = false
            }
        }
    }

    BackHandler(enabled = !isSubmitting, onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        GridBackground(modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BNBULayout.ScreenHorizontal, vertical = BNBULayout.Space12)
                .testTag("screen.courseJoinConfirm"),
            verticalArrangement = Arrangement.spacedBy(BNBULayout.Space16)
        ) {
            TextButton(
                onClick = onBack,
                enabled = !isSubmitting,
                modifier = Modifier.testTag("courseJoinConfirm.back")
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                Text(interfaceText("返回", "Back"))
            }

            Column(verticalArrangement = Arrangement.spacedBy(BNBULayout.Space8)) {
                Text(
                    text = interfaceText("加入课程", "Join course"),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = interfaceText(
                        "课程已识别，请补充个人信息完成加入。",
                        "Course identified. Add your details to finish joining."
                    ),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (course.isDemoScanResult) {
                DemoDirectJoinPanel()
            }

            CompactCourseSummary(course)

            Column(verticalArrangement = Arrangement.spacedBy(BNBULayout.Space8)) {
                Text(
                    text = interfaceText("填写个人信息", "Enter your details"),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = interfaceText(
                        "用于课程名单核对，所有项目均需填写。",
                        "Used to match the course roster. Complete every field."
                    ),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            when {
                alreadyInThisCourse -> {
                    ValidationPanel(interfaceText("你已经加入该课程。", "You have already joined this course."))
                    BNBUPrimaryButton(
                        title = interfaceText("进入课程", "Open course"),
                        modifier = Modifier.testTag("courseJoinConfirm.openExisting"),
                        onClick = onEnterExistingCourse
                    )
                }
                alreadyInAnotherCourse -> ValidationPanel(
                    interfaceText(
                        "你本学期已加入其他体育课程，不能重复加入第二门课程。",
                        "You already belong to another PE course this term and cannot join a second course."
                    )
                )
                else -> {
                    errorMessage?.let { ValidationPanel(it) }
                    BNBUFormField(
                        value = name,
                        onValueChange = {
                            name = it.take(MaxNameLength)
                            errorMessage = null
                        },
                        label = interfaceText("姓名", "Name"),
                        placeholder = interfaceText("请输入姓名", "Enter your name"),
                        errorText = nameFieldError,
                        required = true,
                        counter = name.length to MaxNameLength,
                        enabled = !isSubmitting && writeEnabled,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { studentNumberFocusRequester.requestFocus() }
                        ),
                        inputModifier = Modifier.focusRequester(nameFocusRequester),
                        testTag = "courseJoinConfirm.name"
                    )
                    BNBUFormField(
                        value = studentNumber,
                        onValueChange = {
                            studentNumber = it.take(MaxStudentNumberLength)
                            errorMessage = null
                        },
                        label = interfaceText("学号", "Student ID"),
                        placeholder = interfaceText("请输入学号", "Enter your student ID"),
                        supportingText = interfaceText(
                            "5–32 位字母、数字或连字符",
                            "5–32 letters, numbers, or hyphens"
                        ),
                        errorText = studentNumberFieldError,
                        required = true,
                        enabled = !isSubmitting && writeEnabled,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { gradeFocusRequester.requestFocus() }
                        ),
                        inputModifier = Modifier.focusRequester(studentNumberFocusRequester),
                        testTag = "courseJoinConfirm.studentNumber"
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(BNBULayout.Space8)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(BNBULayout.Space8)
                        ) {
                            Text(
                                text = interfaceText("性别", "Gender"),
                                color = colors.onSurface,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = interfaceText("必填", "Required"),
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        SegmentedControl(
                            values = JoinGender.entries,
                            selected = JoinGender.from(genderValue),
                            label = { it.label() },
                            onSelected = {
                                genderValue = it.apiValue
                                errorMessage = null
                            },
                            enabled = !isSubmitting && writeEnabled,
                            optionTestTag = { "courseJoinConfirm.gender.${it.apiValue}" }
                        )
                    }
                    BNBUFormField(
                        value = grade,
                        onValueChange = {
                            grade = it.filter(Char::isDigit).take(MaxGradeLength)
                            errorMessage = null
                        },
                        label = interfaceText("年级", "Cohort year"),
                        placeholder = "2024",
                        supportingText = interfaceText("例如：2024 级", "For example: 2024 cohort"),
                        errorText = gradeFieldError,
                        required = true,
                        enabled = !isSubmitting && writeEnabled,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus(force = true) }
                        ),
                        inputModifier = Modifier.focusRequester(gradeFocusRequester),
                        testTag = "courseJoinConfirm.grade"
                    )
                    Spacer(Modifier.height(BNBULayout.Space4))
                    BNBUPrimaryButton(
                        title = if (isSubmitting) {
                            interfaceText("正在加入…", "Joining…")
                        } else {
                            interfaceText("确认加入", "Confirm join")
                        },
                        enabled = formValid && !hasSubmitted && writeEnabled,
                        loading = isSubmitting,
                        modifier = Modifier.testTag("courseJoinConfirm.submit"),
                        onClick = ::submit
                    )
                }
            }
        }
    }
}

@Composable
private fun DemoDirectJoinPanel() {
    ValidationPanel(
        interfaceText(
            "本地演示：不会访问真实课程、账号或服务端。",
            "Local demo: no real course, account, or server is accessed."
        )
    )
}

@Composable
private fun CompactCourseSummary(course: CourseJoinInfo) {
    val colors = MaterialTheme.colorScheme
    SwissPanel(contentPadding = BNBULayout.Space16) {
        Column(verticalArrangement = Arrangement.spacedBy(BNBULayout.Space8)) {
            Text(
                text = course.name,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${course.courseNumber} · ${course.section}",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${course.teacher} · ${course.semester}",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun validateDirectCourseJoin(
    name: String,
    studentNumber: String,
    gender: String,
    grade: String
): String? = when {
    name.isBlank() -> interfaceText("请填写姓名。", "Enter your name.")
    name.length > MaxNameLength -> interfaceText("姓名不能超过 %1\$d 个字符。", "Name cannot exceed %1\$d characters.").format(MaxNameLength)
    studentNumber.isBlank() -> interfaceText("请填写学号。", "Enter your student ID.")
    !StudentNumberPattern.matches(studentNumber) -> interfaceText("学号格式不正确，请输入 5–32 位字母、数字或连字符。", "The student ID format is invalid. Enter 5–32 letters, numbers, or hyphens.")
    JoinGender.from(gender) == null -> interfaceText("请选择性别。", "Select a gender.")
    grade.isBlank() -> interfaceText("请填写入学年份。", "Enter your enrollment year.")
    !GradeYearPattern.matches(grade) || grade.toIntOrNull() !in 1000..9999 -> interfaceText(
        "请输入四位数字入学年份。",
        "Enter a four-digit enrollment year."
    )
    else -> null
}

internal fun directJoinErrorMessage(error: Throwable): String {
    val errorCode = (error as? V1HttpException)?.error?.code?.value
        ?: (error as? ApiHttpException)?.responseBody?.let(::extractServerErrorCode)
        ?: error.message.orEmpty().uppercase()
    val httpStatus = when (error) {
        is V1HttpException -> error.statusCode
        is ApiHttpException -> error.statusCode
        else -> null
    }
    return when {
        errorCode.contains("INVITE_EXPIRED") || errorCode.contains("QR_EXPIRED") ||
            httpStatus == 410 -> interfaceText(
            "课程二维码或邀请码已过期，请向教师获取新的加入凭证。",
            "The course QR code or invitation code has expired. Ask the teacher for a new credential."
        )
        errorCode.contains("INVITE_REVOKED") || errorCode.contains("QR_REVOKED") -> interfaceText(
            "课程二维码或邀请码已被停用，请向教师获取新的加入凭证。",
            "The course QR code or invitation code has been disabled. Ask the teacher for a new credential."
        )
        errorCode.contains("INVALID_INVITE") || errorCode.contains("INVITE_INVALID") ||
            errorCode.contains("INVALID_QR") -> interfaceText(
            "课程二维码无效，请确认扫描的是教师当前提供的二维码。",
            "The course QR code is invalid. Scan the current code provided by the teacher."
        )
        errorCode.contains("COURSE_NOT_FOUND") ||
            httpStatus == 404 -> interfaceText(
            "课程不存在或已被删除，请联系教师确认课程。",
            "The course does not exist or was removed. Contact the teacher to confirm it."
        )
        errorCode.contains("JOIN_CLOSED") || errorCode.contains("COURSE_CLOSED") ||
            errorCode.contains("ENROLLMENT_CLOSED") -> interfaceText(
            "该课程已关闭加入，请联系教师。",
            "This course is closed to new members. Contact the teacher."
        )
        errorCode.contains("READ_ONLY") || errorCode.contains("MAINTENANCE") -> interfaceText(
            "系统当前暂停写入，暂时不能加入课程，请稍后重试。",
            "The system is not accepting changes right now. Try joining again later."
        )
        errorCode.contains("ACTIVE_MEMBERSHIP_EXISTS") || errorCode.contains("ACTIVE_COURSE_EXISTS") ||
            errorCode.contains("OTHER_COURSE") || errorCode.contains("COURSE_CONFLICT") -> interfaceText(
            "你本学期已加入其他体育课程，不能重复加入第二门课程。",
            "You already belong to another PE course this term and cannot join a second course."
        )
        errorCode.contains("ALREADY_JOINED") || errorCode.contains("DUPLICATE_MEMBERSHIP") -> interfaceText(
            "你已经加入该课程，不会创建重复课程关系。",
            "You have already joined this course. No duplicate membership was created."
        )
        errorCode.contains("STUDENT_NUMBER_CONFLICT") || errorCode.contains("ACCOUNT_CONFLICT") ||
            errorCode.contains("IDENTITY_CONFLICT") -> interfaceText(
            "该学号已关联其他学生身份，请核对学号或联系管理员处理。",
            "This student ID is linked to another identity. Check the ID or contact an administrator."
        )
        errorCode.contains("COURSE_MISMATCH") || errorCode.contains("MEMBERSHIP_MISMATCH") ||
            errorCode.contains("STUDENT_MISMATCH") -> interfaceText(
            "服务端返回的课程或学生信息与本次扫码不一致，本地未接受该结果，请联系管理员核查。",
            "The returned course or student did not match this scan. The app did not accept the result; contact an administrator."
        )
        errorCode.contains("STUDENT_NUMBER_INVALID") -> interfaceText(
            "学号格式不正确，请核对后重试。",
            "The student ID format is invalid. Check it and try again."
        )
        errorCode.contains("SESSION_MISSING") -> interfaceText(
            "课程已加入，但服务端未返回登录会话；请使用验证码登录后进入课程。",
            "The course was joined, but the server did not return a session. Sign in with a verification code to open it."
        )
        errorCode.contains("STUDENT_MISSING") || errorCode.contains("COURSE_MISSING") ||
            errorCode.contains("MEMBERSHIP_MISSING") || errorCode.contains("STUDENT_INVALID") ||
            errorCode.contains("MEMBERSHIP_NOT_ACTIVE") -> interfaceText(
            "服务端返回的加入结果不完整，未保存本地登录状态，请稍后重试。",
            "The enrollment response was incomplete, so no local session was saved. Try again later."
        )
        httpStatus == 409 -> interfaceText(
            "请求已重复提交，或该学号已加入本课程；请稍候后重新进入课程。",
            "The request was duplicated, or this student ID is already enrolled. Wait briefly, then reopen the course."
        )
        httpStatus == 422 -> interfaceText(
            "姓名、学号、性别或年级未通过校验，请核对后重试。",
            "The name, student ID, gender, or grade did not pass validation. Check the details and try again."
        )
        httpStatus == 429 -> interfaceText(
            "提交过于频繁，请稍候再试；不要重复点击确认加入。",
            "Too many requests were submitted. Wait before trying again, and do not tap Confirm repeatedly."
        )
        httpStatus != null && httpStatus >= 500 -> interfaceText(
            "课程加入服务暂时不可用，请稍后重试。",
            "The course enrollment service is temporarily unavailable. Try again later."
        )
        httpStatus == 403 -> interfaceText(
            "服务端拒绝加入课程，请确认二维码仍有效且课程允许加入。",
            "The server rejected enrollment. Confirm that the QR code is still valid and the course is accepting members."
        )
        error is IOException -> interfaceText(
            "网络连接异常，暂时无法确认加入结果；请检查网络后重试，系统不会创建重复课程关系。",
            "The network failed, so the enrollment result could not be confirmed. Retry safely; the server must not create a duplicate membership."
        )
        else -> interfaceText(
            "服务端未能完成课程加入，请核对课程与个人资料后重试。",
            "The server could not complete enrollment. Check the course and your details, then try again."
        )
    }
}

/** Local-only direct-enrollment result used by the clearly labelled scan demo. */
internal fun buildDemoCourseJoinResponse(
    course: CourseJoinInfo,
    body: CourseJoinRequestBody
): CourseJoinResponse {
    val studentId = "demo-${body.studentNumber.lowercase()}"
    return CourseJoinResponse(
        student = CourseJoinStudentResponse(
            id = studentId,
            name = body.studentName,
            studentNumber = body.studentNumber,
            email = body.email.orEmpty(),
            className = body.grade,
            gender = body.gender,
            grade = body.grade,
            accountStatus = "PENDING_CONTACT_BINDING"
        ),
        course = CourseJoinCourseResponse(
            id = course.id,
            code = course.courseNumber,
            section = course.section,
            name = course.name,
            teacherName = course.teacher,
            semester = course.semester,
            status = "active"
        ),
        membership = CourseJoinMembershipResponse(
            id = "demo-membership-${course.id}-${body.studentNumber.lowercase()}",
            courseId = course.id,
            studentId = studentId,
            status = "active",
            joinedAt = Instant.now().toString(),
            joinMethod = "qr"
        ),
        result = "joined",
        alreadyJoined = false
    )
}

private fun extractServerErrorCode(responseBody: String): String? =
    Regex("\\\"(?:code|errorCode|error_code)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        .find(responseBody)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.uppercase()
        ?.takeIf(String::isNotBlank)
