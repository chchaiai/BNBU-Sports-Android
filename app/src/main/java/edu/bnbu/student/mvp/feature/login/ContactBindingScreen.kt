package edu.bnbu.student.mvp.feature.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.bnbu.student.mvp.core.designsystem.AppleButton as Button
import edu.bnbu.student.mvp.core.designsystem.AppleIconButton as IconButton
import edu.bnbu.student.mvp.core.designsystem.BNBULayout
import edu.bnbu.student.mvp.core.designsystem.interfaceText
import edu.bnbu.student.mvp.core.local.AppLanguagePreferences
import edu.bnbu.student.mvp.core.designsystem.pressScale
import kotlinx.coroutines.delay

enum class ContactBindingMode {
    RequiredActivation,
    ManageContacts
}

/** Operations are provided by AppRootScreen so this screen stays token-free. */
internal data class ContactBindingActions(
    val sendEmailCode: (String, (Result<Unit>) -> Unit) -> Unit,
    val verifyEmailCode: (String, String, (Result<Unit>) -> Unit) -> Unit,
    val sendPhoneCode: (String, (Result<Unit>) -> Unit) -> Unit,
    val verifyPhoneCode: (String, String, (Result<Unit>) -> Unit) -> Unit
)

internal val LocalContactBindingActions = staticCompositionLocalOf<ContactBindingActions> {
    error("ContactBindingScreen must be hosted by AppRootScreen")
}

private enum class ContactKind { Email, Phone }

/**
 * Lets a student link a new login contact without owning navigation or account
 * data. The visual hierarchy is deliberately quiet: status first, then the
 * single contact method currently being verified.
 */
@Composable
fun ContactBindingScreen(
    mode: ContactBindingMode,
    onBindingComplete: () -> Unit,
    onBack: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    onOpenPrivacy: (() -> Unit)? = null,
    onOpenHelp: (() -> Unit)? = null,
    initialEmail: String = "",
    initialPhone: String = "",
    initialEmailVerified: Boolean = false,
    initialPhoneVerified: Boolean = false,
    activationLoading: Boolean = false,
    activationError: String? = null,
    onRetryActivation: (() -> Unit)? = null
) {
    val actions = LocalContactBindingActions.current
    val isRequiredActivation = mode == ContactBindingMode.RequiredActivation
    var email by rememberSaveable(initialEmail) { mutableStateOf(initialEmail) }
    var emailCode by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable(initialPhone) { mutableStateOf(initialPhone) }
    var phoneCode by rememberSaveable { mutableStateOf("") }
    var emailVerified by rememberSaveable(initialEmailVerified) { mutableStateOf(initialEmailVerified) }
    var phoneVerified by rememberSaveable(initialPhoneVerified) { mutableStateOf(initialPhoneVerified) }
    var completionSent by rememberSaveable { mutableStateOf(false) }
    var selectedKindName by rememberSaveable { mutableStateOf(ContactKind.Email.name) }
    val selectedKind = ContactKind.entries.firstOrNull { it.name == selectedKindName } ?: ContactKind.Email

    // A pending account cannot use Android back navigation to enter the workspace.
    BackHandler(enabled = true) {
        if (!isRequiredActivation) onBack?.invoke()
    }

    LaunchedEffect(
        emailVerified,
        phoneVerified,
        isRequiredActivation,
        activationLoading,
        activationError
    ) {
        if (
            isRequiredActivation &&
            (emailVerified || phoneVerified) &&
            !activationLoading &&
            activationError == null &&
            !completionSent
        ) {
            completionSent = true
            delay(450)
            onBindingComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(
                    start = BNBULayout.ScreenHorizontal,
                    end = BNBULayout.ScreenHorizontal,
                    top = BNBULayout.Space8,
                    bottom = BNBULayout.Space32
                )
                .testTag(if (isRequiredActivation) "screen.contactActivation" else "screen.contactBinding"),
            verticalArrangement = Arrangement.spacedBy(BNBULayout.Space24)
        ) {
            if (!isRequiredActivation && onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("contactBinding.back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = interfaceText("返回", "Back")
                    )
                }
            }

            BindingHeader(mode)
            ContactStatusGroup(
                required = isRequiredActivation,
                emailVerified = emailVerified,
                phoneVerified = phoneVerified,
                selectedKind = selectedKind,
                onSelect = { selectedKindName = it.name }
            )

            Column(verticalArrangement = Arrangement.spacedBy(BNBULayout.Space12)) {
                Text(
                    text = if (selectedKind == ContactKind.Email) {
                        interfaceText("绑定邮箱", "Link email")
                    } else {
                        interfaceText("绑定手机号", "Link mobile number")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (selectedKind == ContactKind.Email) {
                        interfaceText(
                            "用于接收登录验证和账户安全通知。",
                            "Used for sign-in verification and account security notifications."
                        )
                    } else {
                        interfaceText(
                            "用于登录验证和重要账户提醒。",
                            "Used for sign-in verification and important account alerts."
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when (selectedKind) {
                    ContactKind.Email -> ContactVerificationForm(
                        kind = ContactKind.Email,
                        value = email,
                        onValueChange = { if (!emailVerified) email = it.trim() },
                        code = emailCode,
                        onCodeChange = { if (!emailVerified) emailCode = it.trim() },
                        isVerified = emailVerified,
                        isInputValid = { it.contains('@') && it.substringAfter('@').contains('.') },
                        onSendCode = actions.sendEmailCode,
                        onVerifyCode = actions.verifyEmailCode,
                        onVerified = { emailVerified = true },
                        allowChange = !isRequiredActivation,
                        onChangeVerifiedContact = {
                            emailVerified = false
                            email = ""
                            emailCode = ""
                        },
                        modifier = Modifier.testTag("contactBinding.email")
                    )

                    ContactKind.Phone -> ContactVerificationForm(
                        kind = ContactKind.Phone,
                        value = phone,
                        onValueChange = { if (!phoneVerified) phone = it.trim() },
                        code = phoneCode,
                        onCodeChange = { if (!phoneVerified) phoneCode = it.trim() },
                        isVerified = phoneVerified,
                        isInputValid = ::isMainlandChinaPhone,
                        onSendCode = { value, onResult ->
                            actions.sendPhoneCode(normalizeMainlandChinaPhone(value), onResult)
                        },
                        onVerifyCode = { value, code, onResult ->
                            actions.verifyPhoneCode(
                                normalizeMainlandChinaPhone(value),
                                code,
                                onResult
                            )
                        },
                        onVerified = { phoneVerified = true },
                        allowChange = !isRequiredActivation,
                        onChangeVerifiedContact = {
                            phoneVerified = false
                            phone = ""
                            phoneCode = ""
                        },
                        modifier = Modifier.testTag("contactBinding.phone")
                    )
                }
            }

            if (isRequiredActivation && (emailVerified || phoneVerified)) {
                ActivationCompletionState(
                    isLoading = activationLoading,
                    error = activationError,
                    onRetry = onRetryActivation
                )
            }

            ActivationFooter(
                required = isRequiredActivation,
                onLogout = onLogout,
                onOpenPrivacy = onOpenPrivacy,
                onOpenHelp = onOpenHelp
            )
        }
    }
}

@Composable
private fun ActivationCompletionState(
    isLoading: Boolean,
    error: String?,
    onRetry: (() -> Unit)?
) {
    when {
        isLoading -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(BNBULayout.Space8))
            Text(
                text = interfaceText("正在准备你的账户…", "Preparing your account…"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        !error.isNullOrBlank() -> Column(
            verticalArrangement = Arrangement.spacedBy(BNBULayout.Space12)
        ) {
            VerificationMessage(text = error, isError = true)
            if (onRetry != null) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = BNBULayout.PrimaryControlHeight)
                        .testTag("contactActivation.retryWorkspace"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(interfaceText("重试并继续", "Retry and continue"))
                }
            }
        }
    }
}

/**
 * Lightweight, local help for the required activation flow.  It deliberately
 * does not load the ordinary help centre because a pending account is allowed
 * to use only its contact-verification endpoints.
 */
@Composable
fun ContactActivationHelpScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(
                start = BNBULayout.ScreenHorizontal,
                end = BNBULayout.ScreenHorizontal,
                top = BNBULayout.Space8,
                bottom = BNBULayout.Space32
            )
            .testTag("screen.contactActivationHelp"),
        verticalArrangement = Arrangement.spacedBy(BNBULayout.Space24)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("contactActivation.help.back")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = interfaceText("返回", "Back")
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(BNBULayout.Space8)) {
            Text(
                text = interfaceText("需要帮助？", "Need help?"),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = interfaceText(
                    "完成一次联系方式验证后，就可以继续使用课程、打卡和进度服务。",
                    "Verify one contact method to continue to courses, check-ins, and progress."
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.padding(BNBULayout.CardPadding),
                verticalArrangement = Arrangement.spacedBy(BNBULayout.Space16)
            ) {
                ActivationHelpItem(
                    title = interfaceText("没有收到验证码", "Didn't receive a code?"),
                    body = interfaceText(
                        "检查邮箱或手机号是否输入正确。验证码有效期为 10 分钟，可在 60 秒后重新发送。",
                        "Check that the email address or mobile number is correct. Codes are valid for 10 minutes and can be resent after 60 seconds."
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                ActivationHelpItem(
                    title = interfaceText("仍然无法完成验证", "Still can't verify?"),
                    body = interfaceText(
                        "请联系学校体育教学部或账户管理员，并说明你的学号与遇到的问题。",
                        "Contact the university sports office or your account administrator with your student ID and a description of the issue."
                    )
                )
            }
        }
    }
}

@Composable
private fun ActivationHelpItem(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(BNBULayout.Space4)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BindingHeader(mode: ContactBindingMode) {
    Column(verticalArrangement = Arrangement.spacedBy(BNBULayout.Space8)) {
        Text(
            text = if (mode == ContactBindingMode.RequiredActivation) {
                interfaceText("完成账户激活", "Complete account activation")
            } else {
                interfaceText("登录与安全", "Sign-in and security")
            },
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = if (mode == ContactBindingMode.RequiredActivation) {
                interfaceText(
                    "你已加入课程。验证一个手机号或邮箱后，即可开始打卡并使用课程服务。",
                    "Your course is ready. Verify either a mobile number or email address to start check-ins and use course services."
                )
            } else {
                interfaceText(
                    "添加或更换邮箱、手机号，保持登录方式随时可用。",
                    "Add or change your email and mobile number to keep your sign-in methods available."
                )
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActivationFooter(
    required: Boolean,
    onLogout: (() -> Unit)?,
    onOpenPrivacy: (() -> Unit)?,
    onOpenHelp: (() -> Unit)?
) {
    if (required) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BNBULayout.Space4)
        ) {
            TextButton(
                onClick = { onLogout?.invoke() },
                enabled = onLogout != null,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("contactActivation.logout")
            ) {
                Text(interfaceText("退出登录", "Sign out"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { onOpenPrivacy?.invoke() },
                    enabled = onOpenPrivacy != null,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("contactActivation.privacy")
                ) {
                    Text(interfaceText("隐私说明", "Privacy"))
                }
                Text(
                    text = "·",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(
                    onClick = { onOpenHelp?.invoke() },
                    enabled = onOpenHelp != null,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("contactActivation.help")
                ) {
                    Text(interfaceText("需要帮助", "Get help"))
                }
            }
        }
    } else {
        Text(
            text = interfaceText(
                "验证任一联系方式后会自动保存。",
                "Either verified contact method is saved automatically."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = BNBULayout.Space4)
        )
    }
}

private fun isMainlandChinaPhone(value: String): Boolean {
    val digits = mainlandChinaPhoneDigits(value)
    return digits.matches(Regex("^1[3-9]\\d{9}$"))
}

/** The UI accepts mainland China input while the API always receives E.164. */
private fun normalizeMainlandChinaPhone(value: String): String =
    "+86${mainlandChinaPhoneDigits(value)}"

private fun mainlandChinaPhoneDigits(value: String): String =
    value.filter(Char::isDigit).let { raw ->
        if (raw.startsWith("86")) raw.drop(2) else raw
    }

@Composable
private fun ContactStatusGroup(
    required: Boolean,
    emailVerified: Boolean,
    phoneVerified: Boolean,
    selectedKind: ContactKind,
    onSelect: (ContactKind) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(BNBULayout.Space8)) {
        Text(
            text = if (required) {
                interfaceText("选择一种验证方式", "Choose one verification method")
            } else {
                interfaceText("登录方式", "Sign-in methods")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = BNBULayout.Space4)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large
        ) {
            Column {
                ContactStatusRow(
                    icon = Icons.Filled.Email,
                    label = interfaceText("邮箱", "Email"),
                    detail = if (emailVerified) {
                        interfaceText("已验证", "Verified")
                    } else {
                        interfaceText("待验证", "Not verified")
                    },
                    verified = emailVerified,
                    selected = selectedKind == ContactKind.Email,
                    onClick = { onSelect(ContactKind.Email) }
                )
                HorizontalDivider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 56.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
                ContactStatusRow(
                    icon = Icons.Filled.Phone,
                    label = interfaceText("手机号", "Mobile number"),
                    detail = if (phoneVerified) {
                        interfaceText("已验证", "Verified")
                    } else {
                        interfaceText("待验证", "Not verified")
                    },
                    verified = phoneVerified,
                    selected = selectedKind == ContactKind.Phone,
                    onClick = { onSelect(ContactKind.Phone) }
                )
            }
        }
    }
}

@Composable
private fun ContactStatusRow(
    icon: ImageVector,
    label: String,
    detail: String,
    verified: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .pressScale(interactionSource)
            .padding(horizontal = BNBULayout.Space16, vertical = BNBULayout.Space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(BNBULayout.Space12))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (verified) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = interfaceText("已验证", "Verified"),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(BNBULayout.Space4))
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = if (verified || selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ContactVerificationForm(
    kind: ContactKind,
    value: String,
    onValueChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    isVerified: Boolean,
    isInputValid: (String) -> Boolean,
    onSendCode: (String, (Result<Unit>) -> Unit) -> Unit,
    onVerifyCode: (String, String, (Result<Unit>) -> Unit) -> Unit,
    onVerified: () -> Unit,
    allowChange: Boolean,
    onChangeVerifiedContact: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appLanguage = AppLanguagePreferences.currentLanguage
    var codeSent by rememberSaveable(kind) { mutableStateOf(false) }
    var isSending by rememberSaveable(kind) { mutableStateOf(false) }
    var isVerifying by rememberSaveable(kind) { mutableStateOf(false) }
    var message by rememberSaveable(kind, appLanguage) { mutableStateOf<String?>(null) }
    var isMessageError by rememberSaveable(kind) { mutableStateOf(false) }
    var resendSeconds by rememberSaveable(kind) { mutableStateOf(0) }
    val isBusy = isSending || isVerifying
    val contactLabel = if (kind == ContactKind.Email) {
        interfaceText("邮箱", "Email")
    } else {
        interfaceText("手机号", "Mobile number")
    }
    val codeLabel = if (kind == ContactKind.Email) {
        interfaceText("邮箱验证码", "Email verification code")
    } else {
        interfaceText("短信验证码", "SMS verification code")
    }
    val icon = if (kind == ContactKind.Email) Icons.Filled.Email else Icons.Filled.Phone

    LaunchedEffect(resendSeconds) {
        if (resendSeconds > 0) {
            delay(1_000)
            resendSeconds -= 1
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(BNBULayout.CardPadding),
            verticalArrangement = Arrangement.spacedBy(BNBULayout.Space16)
        ) {
            if (isVerified) {
                VerifiedContactState(icon = icon, label = contactLabel, value = value)
                if (allowChange) {
                    TextButton(
                        onClick = onChangeVerifiedContact,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("contactBinding.${kind.name.lowercase()}.change")
                    ) {
                        Text(
                            if (kind == ContactKind.Email) {
                                interfaceText("更换邮箱", "Use another email")
                            } else {
                                interfaceText("更换手机号", "Use another mobile number")
                            }
                        )
                    }
                }
            } else {
                ContactTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = contactLabel,
                    icon = icon,
                    keyboardType = if (kind == ContactKind.Email) KeyboardType.Email else KeyboardType.Phone,
                    enabled = !isBusy,
                    modifier = Modifier.testTag("contactBinding.${kind.name.lowercase()}.value")
                )
                Button(
                    onClick = {
                        if (!isInputValid(value)) {
                            isMessageError = true
                            message = if (kind == ContactKind.Email) {
                                interfaceText("请输入有效的邮箱", "Enter a valid email address.")
                            } else {
                                interfaceText("请输入有效的手机号", "Enter a valid mobile number.")
                            }
                        } else {
                            isSending = true
                            isMessageError = false
                            message = null
                            onSendCode(value) { result ->
                                isSending = false
                                codeSent = result.isSuccess
                                if (result.isSuccess) resendSeconds = 60
                                isMessageError = result.isFailure
                                message = result.fold(
                                    onSuccess = {
                                        interfaceText(
                                            "验证码已发送",
                                            "Verification code sent."
                                        )
                                    },
                                    onFailure = {
                                        it.message ?: interfaceText(
                                            "发送失败，请稍后重试",
                                            "Could not send the code. Try again later."
                                        )
                                    }
                                )
                            }
                        }
                    },
                    enabled = !isBusy && resendSeconds == 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = BNBULayout.PrimaryControlHeight)
                        .testTag("contactBinding.${kind.name.lowercase()}.sendCode"),
                    shape = MaterialTheme.shapes.medium
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(
                            if (resendSeconds > 0) {
                                interfaceText("${resendSeconds} 秒后可重发", "Resend in ${resendSeconds}s")
                            } else {
                                interfaceText("获取验证码", "Send verification code")
                            }
                        )
                    }
                }

                if (codeSent) {
                    ContactTextField(
                        value = code,
                        onValueChange = onCodeChange,
                        label = codeLabel,
                        icon = null,
                        keyboardType = KeyboardType.NumberPassword,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("contactBinding.${kind.name.lowercase()}.code")
                    )
                    Button(
                        onClick = {
                            isVerifying = true
                            isMessageError = false
                            message = null
                            onVerifyCode(value, code) { result ->
                                isVerifying = false
                                isMessageError = result.isFailure
                                if (result.isSuccess) onVerified()
                                message = result.fold(
                                    onSuccess = {
                                        interfaceText("验证成功", "Verification successful.")
                                    },
                                    onFailure = {
                                        it.message ?: interfaceText(
                                            "验证失败，请检查验证码后重试",
                                            "Verification failed. Check the code and try again."
                                        )
                                    }
                                )
                            }
                        },
                        enabled = code.matches(Regex("^\\d{6}$")) && !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = BNBULayout.PrimaryControlHeight)
                            .testTag("contactBinding.${kind.name.lowercase()}.verify"),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(interfaceText("确认验证", "Verify"))
                        }
                    }
                }
            }

            message?.let { VerificationMessage(text = it, isError = isMessageError) }
        }
    }
}

@Composable
private fun ContactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector?,
    keyboardType: KeyboardType,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = icon?.let { image -> { Icon(imageVector = image, contentDescription = null) } },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
private fun VerifiedContactState(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BNBULayout.Space4),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = interfaceText("已验证", "Verified"),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(BNBULayout.Space12))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = interfaceText(
                    "${label}已验证",
                    "$label verified"
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun VerificationMessage(text: String, isError: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(BNBULayout.Space12),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
