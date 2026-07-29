package edu.bnbu.student.mvp.feature.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import edu.bnbu.student.mvp.core.designsystem.AppleButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import edu.bnbu.student.mvp.core.designsystem.AppleIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import edu.bnbu.student.mvp.core.designsystem.ValidationPanel
import edu.bnbu.student.mvp.core.designsystem.interfaceText
import edu.bnbu.student.mvp.core.network.StudentApiClient
import edu.bnbu.student.mvp.core.network.StudentEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val MaxRecoveryDescriptionLength = 500
private val RecoveryContentMaxWidth = 680.dp

/** Request body for POST /api/v1/student/recovery-requests. */
data class RecoveryRequestBody(
    val studentId: String,
    val name: String,
    val description: String,
    val newPhone: String? = null,
    val newEmail: String? = null
)

/** A public recovery form for students who cannot use either bound contact. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoveryRequestScreen(
    onBack: () -> Unit,
    submitRequest: suspend (RecoveryRequestBody) -> Unit = { body ->
        val client = StudentApiClient()
        client.executeCancellable(client.request(StudentEndpoint.RecoveryRequests, body))
    }
) {
    var studentId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var newPhone by remember { mutableStateOf("") }
    var newEmail by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val handleBack = {
        focusManager.clearFocus(force = true)
        onBack()
    }

    BackHandler(onBack = handleBack)

    fun submit() {
        val trimmedStudentId = studentId.trim()
        val trimmedName = name.trim()
        val trimmedDescription = description.trim()
        if (isSubmitting || submitted) return
        errorMessage = when {
            trimmedStudentId.isEmpty() -> interfaceText("请填写学号", "Enter your student ID.")
            trimmedName.isEmpty() -> interfaceText("请填写姓名", "Enter your name.")
            trimmedDescription.isEmpty() -> interfaceText("请填写说明", "Enter a description.")
            else -> null
        }
        if (errorMessage != null) return

        focusManager.clearFocus(force = true)
        isSubmitting = true
        scope.launch {
            try {
                submitRequest(
                    RecoveryRequestBody(
                        studentId = trimmedStudentId,
                        name = trimmedName,
                        description = trimmedDescription,
                        newPhone = newPhone.trim().ifBlank { null },
                        newEmail = newEmail.trim().ifBlank { null }
                    )
                )
                submitted = true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                errorMessage = interfaceText("恢复申请提交失败，请稍后重试", "Recovery request failed. Try again later.")
            } finally {
                isSubmitting = false
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen.recoveryRequest"),
        containerColor = colors.background,
        topBar = {
            RecoveryTopBar(onBack = handleBack)
        },
        bottomBar = {
            if (!submitted) {
                RecoverySubmitBar(
                    isSubmitting = isSubmitting,
                    onSubmit = ::submit
                )
            }
        }
    ) { innerPadding ->
        if (submitted) {
            RecoverySuccessState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onBack = handleBack
            )
        } else {
            RecoveryForm(
                studentId = studentId,
                onStudentIdChange = { studentId = it },
                name = name,
                onNameChange = { name = it },
                description = description,
                onDescriptionChange = {
                    description = it.take(MaxRecoveryDescriptionLength)
                },
                newPhone = newPhone,
                onNewPhoneChange = { newPhone = it },
                newEmail = newEmail,
                onNewEmailChange = { newEmail = it },
                errorMessage = errorMessage,
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecoveryTopBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = interfaceText("账号恢复", "Account recovery"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = interfaceText("返回", "Back")
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

@Composable
private fun RecoveryForm(
    studentId: String,
    onStudentIdChange: (String) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    newPhone: String,
    onNewPhoneChange: (String) -> Unit,
    newEmail: String,
    onNewEmailChange: (String) -> Unit,
    errorMessage: String?,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = RecoveryContentMaxWidth)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                bottom = 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = interfaceText("换手机后无法登录？", "Can't sign in after changing phones?"),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = interfaceText("提交以下信息后，老师或管理员会核对你的身份，并协助绑定新的联系方式。", "Submit the details below so a teacher or administrator can verify your identity and help link new contact details."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            errorMessage?.let { message ->
                item {
                    ValidationPanel(message = message)
                }
            }

            item {
                RecoverySection(
                    title = interfaceText("身份信息", "Identity details"),
                    description = interfaceText("请填写与校园账号一致的信息", "Use the same details as your campus account.")
                ) {
                    RecoveryTextField(
                        value = studentId,
                        onValueChange = onStudentIdChange,
                        label = interfaceText("学号", "Student ID"),
                        placeholder = interfaceText("请输入学号", "Enter your student ID"),
                        requirement = interfaceText("必填", "Required"),
                        enabled = enabled,
                        imeAction = ImeAction.Next,
                        tag = "recovery.studentId"
                    )
                    RecoveryTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = interfaceText("姓名", "Name"),
                        placeholder = interfaceText("请输入姓名", "Enter your name"),
                        requirement = interfaceText("必填", "Required"),
                        enabled = enabled,
                        imeAction = ImeAction.Next,
                        tag = "recovery.name"
                    )
                }
            }

            item {
                RecoverySection(
                    title = interfaceText("情况说明", "What happened"),
                    description = interfaceText("简要说明原联系方式无法使用的情况", "Briefly explain why the original contact details cannot be used.")
                ) {
                    RecoveryTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        label = interfaceText("说明", "Description"),
                        placeholder = interfaceText("例如：更换手机后，原手机号和邮箱均无法接收验证码", "For example: after changing phones, neither the old mobile number nor email can receive codes."),
                        requirement = interfaceText("必填", "Required"),
                        supportingText = "${description.length} / $MaxRecoveryDescriptionLength",
                        enabled = enabled,
                        minLines = 4,
                        maxLines = 7,
                        imeAction = ImeAction.Default,
                        tag = "recovery.description"
                    )
                }
            }

            item {
                RecoverySection(
                    title = interfaceText("新的联系方式", "New contact details"),
                    description = interfaceText("选填；如方便，请填写可正常使用的联系方式", "Optional. Provide contact details you can currently use, if available.")
                ) {
                    RecoveryTextField(
                        value = newPhone,
                        onValueChange = onNewPhoneChange,
                        label = interfaceText("新手机号", "New mobile number"),
                        placeholder = interfaceText("请输入新手机号", "Enter a new mobile number"),
                        requirement = interfaceText("选填", "Optional"),
                        enabled = enabled,
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next,
                        tag = "recovery.newPhone"
                    )
                    RecoveryTextField(
                        value = newEmail,
                        onValueChange = onNewEmailChange,
                        label = interfaceText("新邮箱", "New email"),
                        placeholder = interfaceText("请输入新邮箱", "Enter a new email"),
                        requirement = interfaceText("选填", "Optional"),
                        enabled = enabled,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        tag = "recovery.newEmail"
                    )
                }
            }
        }
    }
}

@Composable
private fun RecoverySection(
    title: String,
    description: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun RecoveryTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    requirement: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    minLines: Int = 1,
    maxLines: Int = 1,
    tag: String
) {
    val colors = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = colors.onSurface,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = requirement,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            if (supportingText != null) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = supportingText,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .testTag(tag),
            placeholder = {
                Text(
                    text = placeholder,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f)
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = keyboardActions,
            minLines = minLines,
            maxLines = maxLines,
            shape = MaterialTheme.shapes.medium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceVariant.copy(alpha = 0.62f),
                unfocusedContainerColor = colors.surfaceVariant.copy(alpha = 0.62f),
                disabledContainerColor = colors.surfaceVariant.copy(alpha = 0.4f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun RecoverySubmitBar(
    isSubmitting: Boolean,
    onSubmit: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onSubmit,
                enabled = !isSubmitting,
                modifier = Modifier
                    .widthIn(max = RecoveryContentMaxWidth)
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag("recovery.submit"),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.56f),
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isSubmitting) interfaceText("正在提交…", "Submitting…") else interfaceText("提交恢复申请", "Submit recovery request"),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun RecoverySuccessState(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = interfaceText("申请已提交", "Request submitted"),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = interfaceText("恢复申请已提交，请等待老师或管理员联系你", "Your recovery request was submitted. Please wait for a teacher or administrator to contact you."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(interfaceText("返回登录", "Back to sign in"))
            }
        }
    }
}
