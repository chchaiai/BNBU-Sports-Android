package edu.bnbu.student.mvp.feature.login

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import edu.bnbu.student.mvp.core.designsystem.AppleButton as Button
import edu.bnbu.student.mvp.core.designsystem.AppleIconButton as IconButton
import edu.bnbu.student.mvp.core.designsystem.AppleTextButton as TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.R
import edu.bnbu.student.mvp.core.designsystem.BNBUMotion
import edu.bnbu.student.mvp.core.designsystem.BrandMark
import edu.bnbu.student.mvp.core.network.ApiHttpException
import edu.bnbu.student.mvp.core.network.EmailLoginRequest
import edu.bnbu.student.mvp.core.network.PhoneLoginRequest
import edu.bnbu.student.mvp.core.network.SendEmailCodeRequest
import edu.bnbu.student.mvp.core.network.SendPhoneCodeRequest
import edu.bnbu.student.mvp.core.network.StudentApiClient
import edu.bnbu.student.mvp.core.network.StudentEndpoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val verificationCodeLength = 6
private const val resendCooldownSeconds = 60

internal enum class VerificationLoginMethod {
    Email,
    Phone,
}

/**
 * Shared presentation for the two code-based sign-in methods.
 *
 * Each method keeps its own entered contact and code in this screen's saved UI state, so changing
 * the method is a presentation transition instead of a navigation reset. Request details and
 * completion callbacks remain exactly owned by the existing authentication endpoints.
 */
@Composable
internal fun VerificationLoginScreen(
    initialMethod: VerificationLoginMethod,
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    var selectedMethodName by rememberSaveable { mutableStateOf(initialMethod.name) }
    val selectedMethod = VerificationLoginMethod.valueOf(selectedMethodName)

    var phone by rememberSaveable { mutableStateOf("") }
    var phoneVerificationCode by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var emailVerificationCode by rememberSaveable { mutableStateOf("") }
    var phoneCooldownSeconds by rememberSaveable { mutableStateOf(0) }
    var emailCooldownSeconds by rememberSaveable { mutableStateOf(0) }
    var isSendingPhoneCode by remember { mutableStateOf(false) }
    var isSendingEmailCode by remember { mutableStateOf(false) }
    var isLoggingInWithPhone by remember { mutableStateOf(false) }
    var isLoggingInWithEmail by remember { mutableStateOf(false) }
    var phoneErrorMessage by remember { mutableStateOf<String?>(null) }
    var phoneInfoMessage by remember { mutableStateOf<String?>(null) }
    var emailErrorMessage by remember { mutableStateOf<String?>(null) }
    var emailInfoMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val apiClient = remember { StudentApiClient() }
    val phoneCodeFocusRequester = remember { FocusRequester() }
    val emailCodeFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    val normalizedPhone = phone.normalizedPhone()
    val normalizedEmail = email.trim()
    val isPhoneValid = normalizedPhone.isValidPhoneNumber()
    val isEmailValid = normalizedEmail.isValidEmail()
    val isPhoneInvalid = phone.isNotBlank() && !isPhoneValid
    val isEmailInvalid = email.isNotBlank() && !isEmailValid
    val isPhoneCodeInvalid = phoneVerificationCode.isNotBlank() &&
        phoneVerificationCode.length != verificationCodeLength
    val isEmailCodeInvalid = emailVerificationCode.isNotBlank() &&
        emailVerificationCode.length != verificationCodeLength

    val canSendPhoneCode = isPhoneValid &&
        phoneCooldownSeconds == 0 &&
        !isSendingPhoneCode &&
        !isLoggingInWithPhone
    val canSendEmailCode = isEmailValid &&
        emailCooldownSeconds == 0 &&
        !isSendingEmailCode &&
        !isLoggingInWithEmail
    val canLogInWithPhone = isPhoneValid &&
        phoneVerificationCode.length == verificationCodeLength &&
        !isSendingPhoneCode &&
        !isLoggingInWithPhone
    val canLogInWithEmail = isEmailValid &&
        emailVerificationCode.length == verificationCodeLength &&
        !isSendingEmailCode &&
        !isLoggingInWithEmail

    val rateLimitMessage = stringResource(R.string.login_verification_error_rate_limited)
    val expiredCodeMessage = stringResource(R.string.login_verification_error_expired)
    val invalidCodeMessage = stringResource(R.string.login_verification_error_invalid)
    val requestFailedMessage = stringResource(R.string.login_verification_error_request_failed)
    val phoneUnavailableMessage = stringResource(R.string.login_verification_phone_unavailable)
    val emailUnavailableMessage = stringResource(R.string.login_verification_email_unavailable)
    val phoneCodeSentMessage = stringResource(R.string.login_verification_phone_code_sent)
    val emailCodeSentMessage = stringResource(R.string.login_verification_email_code_sent)

    LaunchedEffect(phoneCooldownSeconds) {
        if (phoneCooldownSeconds > 0) {
            delay(1_000)
            phoneCooldownSeconds -= 1
        }
    }
    LaunchedEffect(emailCooldownSeconds) {
        if (emailCooldownSeconds > 0) {
            delay(1_000)
            emailCooldownSeconds -= 1
        }
    }

    val sendPhoneCode = {
        phoneErrorMessage = null
        phoneInfoMessage = null
        isSendingPhoneCode = true
        coroutineScope.launch {
            try {
                apiClient.executeCancellable(
                    apiClient.request(
                        StudentEndpoint.PhoneLogin,
                        SendPhoneCodeRequest(normalizedPhone)
                    )
                )
                phoneCooldownSeconds = resendCooldownSeconds
                phoneInfoMessage = phoneCodeSentMessage
                phoneCodeFocusRequester.requestFocus()
            } catch (error: Exception) {
                phoneErrorMessage = error.toPhoneLoginMessage(
                    rateLimitMessage = rateLimitMessage,
                    expiredCodeMessage = expiredCodeMessage,
                    invalidCodeMessage = invalidCodeMessage,
                    unavailableMessage = phoneUnavailableMessage,
                    requestFailedMessage = requestFailedMessage
                )
            } finally {
                isSendingPhoneCode = false
            }
        }
        Unit
    }
    val sendEmailCode = {
        emailErrorMessage = null
        emailInfoMessage = null
        isSendingEmailCode = true
        coroutineScope.launch {
            try {
                apiClient.executeCancellable(
                    apiClient.request(
                        StudentEndpoint.EmailLogin,
                        SendEmailCodeRequest(normalizedEmail)
                    )
                )
                emailCooldownSeconds = resendCooldownSeconds
                emailInfoMessage = emailCodeSentMessage
                emailCodeFocusRequester.requestFocus()
            } catch (error: Exception) {
                emailErrorMessage = error.toEmailLoginMessage(
                    rateLimitMessage = rateLimitMessage,
                    expiredCodeMessage = expiredCodeMessage,
                    invalidCodeMessage = invalidCodeMessage,
                    unavailableMessage = emailUnavailableMessage,
                    requestFailedMessage = requestFailedMessage
                )
            } finally {
                isSendingEmailCode = false
            }
        }
        Unit
    }
    val submitPhoneLogin = {
        focusManager.clearFocus()
        phoneErrorMessage = null
        phoneInfoMessage = null
        isLoggingInWithPhone = true
        coroutineScope.launch {
            try {
                apiClient.executeCancellable(
                    apiClient.request(
                        StudentEndpoint.PhoneLogin,
                        PhoneLoginRequest(normalizedPhone, phoneVerificationCode)
                    )
                )
                onLoginSuccess()
            } catch (error: Exception) {
                phoneErrorMessage = error.toPhoneLoginMessage(
                    rateLimitMessage = rateLimitMessage,
                    expiredCodeMessage = expiredCodeMessage,
                    invalidCodeMessage = invalidCodeMessage,
                    unavailableMessage = phoneUnavailableMessage,
                    requestFailedMessage = requestFailedMessage
                )
            } finally {
                isLoggingInWithPhone = false
            }
        }
        Unit
    }
    val submitEmailLogin = {
        focusManager.clearFocus()
        emailErrorMessage = null
        emailInfoMessage = null
        isLoggingInWithEmail = true
        coroutineScope.launch {
            try {
                apiClient.executeCancellable(
                    apiClient.request(
                        StudentEndpoint.EmailLogin,
                        EmailLoginRequest(normalizedEmail, emailVerificationCode)
                    )
                )
                onLoginSuccess()
            } catch (error: Exception) {
                emailErrorMessage = error.toEmailLoginMessage(
                    rateLimitMessage = rateLimitMessage,
                    expiredCodeMessage = expiredCodeMessage,
                    invalidCodeMessage = invalidCodeMessage,
                    unavailableMessage = emailUnavailableMessage,
                    requestFailedMessage = requestFailedMessage
                )
            } finally {
                isLoggingInWithEmail = false
            }
        }
        Unit
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .testTag(
                if (selectedMethod == VerificationLoginMethod.Phone) {
                    "screen.login.phone"
                } else {
                    "screen.login.email"
                }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            VerificationLoginTopBar(
                method = selectedMethod,
                onBack = onBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp)
                    .padding(top = 30.dp, bottom = 28.dp)
            ) {
                BrandMark(compact = true)

                Spacer(Modifier.height(22.dp))
                AnimatedContent(
                    targetState = selectedMethod,
                    transitionSpec = {
                        val movesForward = targetState.ordinal > initialState.ordinal
                        (
                            fadeIn(tween(BNBUMotion.StateChange, delayMillis = 30)) +
                                slideInHorizontally(
                                    animationSpec = tween(
                                        BNBUMotion.Standard,
                                        easing = FastOutSlowInEasing
                                    ),
                                    initialOffsetX = { width ->
                                        if (movesForward) width / 12 else -width / 12
                                    }
                                )
                            ) togetherWith
                            (
                                fadeOut(tween(BNBUMotion.Quick)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(
                                            BNBUMotion.Standard,
                                            easing = FastOutSlowInEasing
                                        ),
                                        targetOffsetX = { width ->
                                            if (movesForward) -width / 16 else width / 16
                                        }
                                    )
                                )
                    },
                    label = "verification-login-method"
                ) { method ->
                    val isPhoneMethod = method == VerificationLoginMethod.Phone
                    val isSendingCode = if (isPhoneMethod) isSendingPhoneCode else isSendingEmailCode
                    val isLoggingIn = if (isPhoneMethod) isLoggingInWithPhone else isLoggingInWithEmail

                    VerificationLoginForm(
                        method = method,
                        contact = if (isPhoneMethod) phone else email,
                        onContactChange = { value ->
                            if (isPhoneMethod) {
                                phone = value
                                phoneErrorMessage = null
                                phoneInfoMessage = null
                            } else {
                                email = value
                                emailErrorMessage = null
                                emailInfoMessage = null
                            }
                        },
                        contactInvalid = if (isPhoneMethod) isPhoneInvalid else isEmailInvalid,
                        verificationCode = if (isPhoneMethod) phoneVerificationCode else emailVerificationCode,
                        onVerificationCodeChange = { value ->
                            val formattedValue = value.filter(Char::isDigit).take(verificationCodeLength)
                            if (isPhoneMethod) {
                                phoneVerificationCode = formattedValue
                                phoneErrorMessage = null
                            } else {
                                emailVerificationCode = formattedValue
                                emailErrorMessage = null
                            }
                        },
                        verificationCodeInvalid = if (isPhoneMethod) {
                            isPhoneCodeInvalid
                        } else {
                            isEmailCodeInvalid
                        },
                        isSendingCode = isSendingCode,
                        isLoggingIn = isLoggingIn,
                        cooldownSeconds = if (isPhoneMethod) {
                            phoneCooldownSeconds
                        } else {
                            emailCooldownSeconds
                        },
                        canSendCode = if (isPhoneMethod) canSendPhoneCode else canSendEmailCode,
                        canLogIn = if (isPhoneMethod) canLogInWithPhone else canLogInWithEmail,
                        statusMessage = if (isPhoneMethod) phoneInfoMessage else emailInfoMessage,
                        errorMessage = if (isPhoneMethod) phoneErrorMessage else emailErrorMessage,
                        codeFocusRequester = if (isPhoneMethod) {
                            phoneCodeFocusRequester
                        } else {
                            emailCodeFocusRequester
                        },
                        onSendCode = if (isPhoneMethod) sendPhoneCode else sendEmailCode,
                        onSubmit = if (isPhoneMethod) submitPhoneLogin else submitEmailLogin,
                        onSwitchMethod = {
                            selectedMethodName = method.other().name
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VerificationLoginTopBar(
    method: VerificationLoginMethod,
    onBack: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 12.dp)
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .testTag(
                    if (method == VerificationLoginMethod.Phone) {
                        "phoneLogin.back"
                    } else {
                        "emailLogin.back"
                    }
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun VerificationLoginForm(
    method: VerificationLoginMethod,
    contact: String,
    onContactChange: (String) -> Unit,
    contactInvalid: Boolean,
    verificationCode: String,
    onVerificationCodeChange: (String) -> Unit,
    verificationCodeInvalid: Boolean,
    isSendingCode: Boolean,
    isLoggingIn: Boolean,
    cooldownSeconds: Int,
    canSendCode: Boolean,
    canLogIn: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    codeFocusRequester: FocusRequester,
    onSendCode: () -> Unit,
    onSubmit: () -> Unit,
    onSwitchMethod: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val isPhoneMethod = method == VerificationLoginMethod.Phone
    val title = stringResource(
        if (isPhoneMethod) R.string.login_verification_phone_title else R.string.login_verification_email_title
    )
    val subtitle = stringResource(
        if (isPhoneMethod) {
            R.string.login_verification_phone_subtitle
        } else {
            R.string.login_verification_email_subtitle
        }
    )
    val contactLabel = stringResource(
        if (isPhoneMethod) R.string.login_verification_phone_label else R.string.login_verification_email_label
    )
    val contactPlaceholder = stringResource(
        if (isPhoneMethod) {
            R.string.login_verification_phone_placeholder
        } else {
            R.string.login_verification_email_placeholder
        }
    )
    val contactError = stringResource(
        if (isPhoneMethod) {
            R.string.login_verification_phone_invalid
        } else {
            R.string.login_verification_email_invalid
        }
    )
    val switchLabel = stringResource(
        if (isPhoneMethod) {
            R.string.login_verification_switch_to_email
        } else {
            R.string.login_verification_switch_to_phone
        }
    )
    val resendLabel = if (cooldownSeconds > 0) {
        stringResource(R.string.login_verification_resend_countdown, cooldownSeconds)
    } else {
        stringResource(R.string.login_verification_send_code)
    }
    val formEnabled = !isSendingCode && !isLoggingIn

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = colors.onBackground,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = subtitle,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(28.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(BNBUMotion.Standard, easing = FastOutSlowInEasing)
                ),
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.surface,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                LoginInputField(
                    label = contactLabel,
                    value = contact,
                    onValueChange = onContactChange,
                    placeholder = contactPlaceholder,
                    leadingIcon = if (isPhoneMethod) Icons.Rounded.Smartphone else Icons.Outlined.Email,
                    prefix = if (isPhoneMethod) {
                        {
                            PhonePrefix()
                        }
                    } else {
                        null
                    },
                    isError = contactInvalid,
                    errorText = contactError,
                    enabled = formEnabled,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPhoneMethod) KeyboardType.Phone else KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { codeFocusRequester.requestFocus() }
                    ),
                    testTag = if (isPhoneMethod) "phoneLogin.phone" else "emailLogin.email"
                )

                Spacer(Modifier.height(20.dp))
                LoginInputField(
                    label = stringResource(R.string.login_verification_code_label),
                    value = verificationCode,
                    onValueChange = onVerificationCodeChange,
                    placeholder = stringResource(R.string.login_verification_code_placeholder),
                    leadingIcon = Icons.Rounded.Lock,
                    isError = verificationCodeInvalid,
                    errorText = stringResource(R.string.login_verification_code_invalid),
                    enabled = formEnabled,
                    modifier = Modifier.focusRequester(codeFocusRequester),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (canLogIn) {
                                onSubmit()
                            }
                        }
                    ),
                    trailingAction = {
                        TextButton(
                            onClick = onSendCode,
                            enabled = canSendCode,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = colors.primary,
                                disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.55f)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag(
                                    if (isPhoneMethod) {
                                        "phoneLogin.sendCode"
                                    } else {
                                        "emailLogin.sendCode"
                                    }
                                )
                        ) {
                            if (isSendingCode) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = colors.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = resendLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    verificationCode = true,
                    testTag = if (isPhoneMethod) "phoneLogin.code" else "emailLogin.code"
                )

                Spacer(Modifier.height(14.dp))
                VerificationCodeNotice()

                AnimatedVisibility(
                    visible = statusMessage != null || errorMessage != null,
                    enter = fadeIn(tween(BNBUMotion.StateChange)) +
                        expandVertically(
                            animationSpec = tween(BNBUMotion.Standard, easing = FastOutSlowInEasing)
                        ),
                    exit = fadeOut(tween(BNBUMotion.Quick)) +
                        shrinkVertically(
                            animationSpec = tween(BNBUMotion.Standard, easing = FastOutSlowInEasing)
                        )
                ) {
                    Column(modifier = Modifier.padding(top = 18.dp)) {
                        statusMessage?.let { message ->
                            LoginStatusBanner(
                                message = message,
                                isError = false,
                                modifier = Modifier.testTag(
                                    if (isPhoneMethod) "phoneLogin.info" else "emailLogin.info"
                                )
                            )
                        }
                        errorMessage?.let { message ->
                            LoginStatusBanner(
                                message = message,
                                isError = true,
                                modifier = Modifier.testTag(
                                    if (isPhoneMethod) "phoneLogin.error" else "emailLogin.error"
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                VerificationSubmitButton(
                    enabled = canLogIn,
                    loading = isLoggingIn,
                    onClick = onSubmit,
                    modifier = Modifier.testTag(
                        if (isPhoneMethod) "phoneLogin.submit" else "emailLogin.submit"
                    )
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        TextButton(
            onClick = onSwitchMethod,
            enabled = formEnabled,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(
                imageVector = if (isPhoneMethod) Icons.Outlined.Email else Icons.Rounded.Smartphone,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = switchLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = stringResource(R.string.login_verification_privacy_notice),
            modifier = Modifier.fillMaxWidth(),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PhonePrefix() {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.login_verification_phone_prefix),
            color = colors.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(22.dp)
                .background(colors.outlineVariant.copy(alpha = 0.7f))
        )
        Spacer(Modifier.width(10.dp))
    }
}

@Composable
private fun LoginInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    isError: Boolean,
    errorText: String,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    testTag: String,
    modifier: Modifier = Modifier,
    prefix: (@Composable () -> Unit)? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    verificationCode: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    var isFocused by remember { mutableStateOf(false) }
    val borderWidth by animateDpAsState(
        targetValue = when {
            isError -> 1.dp
            isFocused -> 1.5.dp
            else -> 0.dp
        },
        animationSpec = tween(BNBUMotion.StateChange, easing = FastOutSlowInEasing),
        label = "verification-field-border-width"
    )
    val borderColor = when {
        isError -> colors.error
        isFocused -> colors.primary
        else -> Color.Transparent
    }
    val iconColor = when {
        isError -> colors.error
        isFocused -> colors.primary
        else -> colors.onSurfaceVariant
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = colors.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .onFocusChanged { isFocused = it.hasFocus }
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = MaterialTheme.shapes.large
                )
                .testTag(testTag),
            enabled = enabled,
            placeholder = {
                Text(
                    text = placeholder,
                    color = colors.onSurfaceVariant.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconColor
                )
            },
            prefix = prefix,
            trailingIcon = trailingAction,
            singleLine = true,
            isError = isError,
            shape = MaterialTheme.shapes.large,
            textStyle = if (verificationCode) {
                MaterialTheme.typography.titleMedium.copy(letterSpacing = 2.sp)
            } else {
                MaterialTheme.typography.bodyLarge
            },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            colors = verificationTextFieldColors()
        )
        AnimatedVisibility(
            visible = isError,
            enter = fadeIn(tween(BNBUMotion.StateChange)) +
                expandVertically(
                    animationSpec = tween(BNBUMotion.StateChange, easing = FastOutSlowInEasing)
                ),
            exit = fadeOut(tween(BNBUMotion.Quick)) +
                shrinkVertically(
                    animationSpec = tween(BNBUMotion.Quick, easing = FastOutSlowInEasing)
                )
        ) {
            Row(
                modifier = Modifier.padding(top = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = colors.error
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = errorText,
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun verificationTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.62f),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun VerificationCodeNotice() {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = colors.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.login_verification_code_expiry),
            modifier = Modifier.weight(1f),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun LoginStatusBanner(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isError) {
        colors.errorContainer.copy(alpha = 0.72f)
    } else {
        colors.primaryContainer.copy(alpha = 0.72f)
    }
    val contentColor = if (isError) colors.onErrorContainer else colors.onPrimaryContainer

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = contentColor,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun VerificationSubmitButton(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = colors.surfaceContainerHigh,
            disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = stringResource(R.string.login_verification_submit),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun VerificationLoginMethod.other(): VerificationLoginMethod = when (this) {
    VerificationLoginMethod.Email -> VerificationLoginMethod.Phone
    VerificationLoginMethod.Phone -> VerificationLoginMethod.Email
}

private fun String.normalizedPhone(): String = trim()
    .removePrefix("+86")
    .removePrefix("86")

private fun String.isValidPhoneNumber(): Boolean = matches(Regex("^1[3-9]\\d{9}$"))

private fun String.isValidEmail(): Boolean = matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))

private fun Exception.toPhoneLoginMessage(
    rateLimitMessage: String,
    expiredCodeMessage: String,
    invalidCodeMessage: String,
    unavailableMessage: String,
    requestFailedMessage: String,
): String {
    val response = (this as? ApiHttpException)?.responseBody.orEmpty().uppercase()
    return when {
        this is ApiHttpException && (statusCode == 429 || response.contains("RATE_LIMIT")) -> {
            rateLimitMessage
        }
        response.contains("EXPIRED") -> expiredCodeMessage
        response.contains("INVALID") || response.contains("CODE") ||
            (this is ApiHttpException && statusCode == 401) -> invalidCodeMessage
        this is ApiHttpException && statusCode == 404 -> unavailableMessage
        else -> requestFailedMessage
    }
}

private fun Exception.toEmailLoginMessage(
    rateLimitMessage: String,
    expiredCodeMessage: String,
    invalidCodeMessage: String,
    unavailableMessage: String,
    requestFailedMessage: String,
): String {
    val response = (this as? ApiHttpException)?.responseBody.orEmpty().uppercase()
    return when {
        this is ApiHttpException && (
            statusCode == 429 ||
                response.contains("RATE_LIMIT") ||
                response.contains("发送频繁") ||
                response.contains("操作频繁")
            ) -> rateLimitMessage
        response.contains("EXPIRED") || response.contains("验证码过期") -> expiredCodeMessage
        response.contains("INVALID") || response.contains("CODE") || response.contains("验证码错误") ||
            (this is ApiHttpException && statusCode == 401) -> invalidCodeMessage
        this is ApiHttpException && statusCode == 404 -> unavailableMessage
        else -> requestFailedMessage
    }
}
