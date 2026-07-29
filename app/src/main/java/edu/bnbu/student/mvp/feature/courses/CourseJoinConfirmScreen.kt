package edu.bnbu.student.mvp.feature.courses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import edu.bnbu.student.mvp.core.designsystem.AppleTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.bnbu.student.mvp.core.designsystem.ActionButton
import edu.bnbu.student.mvp.core.designsystem.GridBackground
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.ValidationPanel
import edu.bnbu.student.mvp.core.designsystem.interfaceText
import edu.bnbu.student.mvp.core.local.AppLanguagePreferences
import edu.bnbu.student.mvp.core.network.ApiHttpException
import edu.bnbu.student.mvp.core.network.CourseInviteJoinRequestBody
import edu.bnbu.student.mvp.core.network.StudentApiClient
import edu.bnbu.student.mvp.core.network.StudentEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val MaxNameLength = 64
private const val MaxStudentNumberLength = 32

/** Course information resolved from an invite before the student applies to join. */
data class CourseJoinInfo(
    val name: String = "篮球基础 B 班",
    val courseNumber: String = "GEPE101",
    val section: String = "1004",
    val teacher: String = "张老师",
    val semester: String = "2026 Spring"
)

/**
 * Course-invite confirmation page (B3).
 *
 * [onSubmitted] is the navigation seam for B4's PendingStatusScreen.  The screen
 * itself deliberately does not create that destination because the B4 feature is
 * not yet part of this module.
 */
@Composable
fun CourseJoinConfirmScreen(
    inviteCode: String,
    course: CourseJoinInfo = CourseJoinInfo(),
    initialName: String = "",
    initialStudentNumber: String = "",
    initialEmail: String = "",
    writeEnabled: Boolean = true,
    canSubmitNewJoinRequest: Boolean = true,
    onBack: () -> Unit = {},
    onSubmitted: () -> Unit = {},
    onSubmittedRequest: (name: String, studentNumber: String, email: String) -> Unit = { _, _, _ -> },
    submitJoinRequest: suspend (CourseInviteJoinRequestBody) -> Unit = { body ->
        val client = StudentApiClient()
        client.executeCancellable(
            client.request(StudentEndpoint.CourseInviteJoinRequest(inviteCode), body)
        )
    }
) {
    val appLanguage = AppLanguagePreferences.currentLanguage
    var name by rememberSaveable { mutableStateOf(initialName) }
    var studentNumber by rememberSaveable { mutableStateOf(initialStudentNumber) }
    var email by rememberSaveable { mutableStateOf(initialEmail) }
    var isSubmitting by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable(appLanguage) { mutableStateOf<String?>(null) }
    var isSubmitted by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    fun submit() {
        if (!writeEnabled || isSubmitting || isSubmitted) return
        if (!canSubmitNewJoinRequest) {
            errorMessage = interfaceText("本学期已选择课程或已有待处理申请，不能重复选课。", "You already have a course or a pending request this term and cannot submit another request.")
            return
        }

        val trimmedName = name.trim()
        val trimmedStudentNumber = studentNumber.trim()
        val trimmedEmail = email.trim()
        errorMessage = validateJoinRequest(trimmedName, trimmedStudentNumber, trimmedEmail)
        if (errorMessage != null) return

        focusManager.clearFocus(force = true)
        isSubmitting = true
        scope.launch {
            try {
                submitJoinRequest(
                    CourseInviteJoinRequestBody(
                        name = trimmedName,
                        studentNumber = trimmedStudentNumber,
                        email = trimmedEmail.ifBlank { null }
                    )
                )
                isSubmitted = true
                onSubmittedRequest(trimmedName, trimmedStudentNumber, trimmedEmail)
                onSubmitted()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                errorMessage = joinRequestErrorMessage(error)
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("screen.courseJoinConfirm"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TextButton(
                onClick = onBack,
                enabled = !isSubmitting,
                modifier = Modifier.testTag("courseJoinConfirm.back")
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                Text(interfaceText("返回", "Back"))
            }

            SectionTitle(title = interfaceText("确认课程信息", "Confirm course information"))

            SwissPanel {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    CourseJoinFact(label = interfaceText("课程名称", "Course name"), value = course.name)
                    CourseJoinFact(
                        label = interfaceText("课程编号 / Section", "Course number / Section"),
                        value = "${course.courseNumber} / Section ${course.section}"
                    )
                    CourseJoinFact(label = interfaceText("授课老师", "Instructor"), value = course.teacher)
                    CourseJoinFact(label = interfaceText("学期", "Term"), value = course.semester)
                    Text(
                        text = interfaceText("请确认以上课程信息无误后再提交申请", "Confirm that these course details are correct before submitting your request."),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            SectionTitle(title = interfaceText("填写身份资料", "Enter identity details"))

            SwissPanel {
                if (isSubmitted) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary
                        )
                        Text(
                            text = interfaceText("申请已提交，请等待老师确认。", "Request submitted. Please wait for the teacher's confirmation."),
                            color = colors.onSurface,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (!canSubmitNewJoinRequest) {
                            ValidationPanel(interfaceText("本学期仅可选择一门课程。你已有课程或待处理申请，不能重复提交。", "You can choose only one course per term. You already have a course or a pending request."))
                        }
                        errorMessage?.let { ValidationPanel(it) }
                        CourseJoinTextField(
                            value = name,
                            onValueChange = { name = it.take(MaxNameLength) },
                            label = interfaceText("姓名（必填）", "Name (required)"),
                            supportingText = "${name.length} / $MaxNameLength",
                            enabled = !isSubmitting && writeEnabled && canSubmitNewJoinRequest,
                            tag = "courseJoinConfirm.name"
                        )
                        CourseJoinTextField(
                            value = studentNumber,
                            onValueChange = { studentNumber = it.take(MaxStudentNumberLength) },
                            label = interfaceText("学号（必填）", "Student ID (required)"),
                            supportingText = "${studentNumber.length} / $MaxStudentNumberLength",
                            enabled = !isSubmitting && writeEnabled,
                            tag = "courseJoinConfirm.studentNumber"
                        )
                        CourseJoinTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = interfaceText("邮箱（选填）", "Email (optional)"),
                            enabled = !isSubmitting && writeEnabled,
                            keyboardType = KeyboardType.Email,
                            tag = "courseJoinConfirm.email"
                        )
                        Spacer(Modifier.height(2.dp))
                        ActionButton(
                            title = if (isSubmitting) interfaceText("提交中…", "Submitting…") else interfaceText("提交加入申请", "Submit join request"),
                            icon = Icons.AutoMirrored.Filled.Send,
                            filled = true,
                            enabled = !isSubmitting && writeEnabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("courseJoinConfirm.submit"),
                            onClick = ::submit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseJoinFact(label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = label, color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(text = value, color = colors.onSurface, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CourseJoinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    tag: String,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth().testTag(tag),
        singleLine = true
    )
}

private fun validateJoinRequest(name: String, studentNumber: String, email: String): String? = when {
    name.isBlank() -> interfaceText("请填写姓名。", "Enter your name.")
    name.length > MaxNameLength -> interfaceText("姓名不能超过 %1\$d 个字符。", "Name cannot exceed %1\$d characters.").format(MaxNameLength)
    studentNumber.isBlank() -> interfaceText("请填写学号。", "Enter your student ID.")
    studentNumber.length > MaxStudentNumberLength -> interfaceText("学号不能超过 %1\$d 个字符。", "Student ID cannot exceed %1\$d characters.").format(MaxStudentNumberLength)
    email.isNotBlank() && !EMAIL_PATTERN.matches(email) -> interfaceText("请输入有效的邮箱地址。", "Enter a valid email address.")
    else -> null
}

private fun joinRequestErrorMessage(error: Throwable): String {
    if (error is ApiHttpException) {
        val serverMessage = extractServerMessage(error.responseBody)
        if (serverMessage != null) return serverMessage
        return when (error.statusCode) {
            404, 410 -> interfaceText("邀请已过期或不存在，请向老师获取新的邀请。", "The invitation has expired or does not exist. Ask the teacher for a new invitation.")
            409 -> interfaceText("该学号已提交过申请或已加入该课程。", "This student ID has already submitted a request or joined this course.")
            else -> interfaceText("提交失败（%1\$d），请稍后重试。", "Submission failed (%1\$d). Try again later.").format(error.statusCode)
        }
    }
    return interfaceText("提交申请失败，请检查网络后重试。", "Could not submit the request. Check your connection and try again.")
}

private fun extractServerMessage(responseBody: String): String? {
    val message = Regex("\\\"(?:message|error)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        .find(responseBody)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    return message?.takeIf { it.isNotBlank() && it.length <= 160 }
}

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
