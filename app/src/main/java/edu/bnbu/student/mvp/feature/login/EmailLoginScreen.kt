package edu.bnbu.student.mvp.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import edu.bnbu.student.mvp.BuildConfig
import edu.bnbu.student.mvp.core.designsystem.AppleButton as Button
import edu.bnbu.student.mvp.core.designsystem.AppleTextButton as TextButton
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.interfaceText
import edu.bnbu.student.mvp.core.local.AndroidAppLocalStore
import edu.bnbu.student.mvp.core.local.AppLanguagePreferences
import edu.bnbu.student.mvp.core.model.AppLanguage
import edu.bnbu.student.mvp.core.network.v1.IntentFingerprint
import edu.bnbu.student.mvp.core.network.v1.MutationIntentRegistry
import edu.bnbu.student.mvp.core.network.v1.MutationIntentScope
import edu.bnbu.student.mvp.core.network.v1.V1HttpException
import edu.bnbu.student.mvp.core.network.v1.V1StudentApi
import edu.bnbu.student.mvp.core.network.v1.generated.CurrentUserData
import edu.bnbu.student.mvp.core.network.v1.generated.StudentSignInCodeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun EmailLoginScreen(
    localStore: AndroidAppLocalStore,
    onLoginSuccess: (CurrentUserData) -> Unit,
    onBack: () -> Unit,
) {
    val api = remember(localStore) { V1StudentApi.create(localStore) }
    val intentRegistry = remember { MutationIntentRegistry() }
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var challengeId by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    val normalizedEmail = email.trim().lowercase()
    val emailValid = normalizedEmail.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
    val codeValid = code.matches(Regex("^\\d{4,10}$"))
    val locale = if (AppLanguagePreferences.currentLanguage == AppLanguage.Chinese) {
        StudentSignInCodeRequest.Locale.zhMinusCN
    } else {
        StudentSignInCodeRequest.Locale.en
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("screen.emailLogin")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("emailLogin.back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
            Text(
                text = interfaceText("邮箱验证码登录", "Email verification sign-in"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(20.dp))
        SwissPanel(contentPadding = 20.dp) {
            Text(
                text = interfaceText(
                    "使用学校登记的邮箱接收一次性验证码。",
                    "Use the email registered with your school to receive a one-time code."
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it.take(254)
                    challengeId = null
                    code = ""
                    message = null
                },
                modifier = Modifier.fillMaxWidth().testTag("emailLogin.email"),
                enabled = !isLoading,
                singleLine = true,
                label = { Text(interfaceText("邮箱", "Email")) },
                placeholder = { Text("student@example.edu") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = email.isNotBlank() && !emailValid
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    enabled = emailValid && !isLoading,
                    onClick = {
                        isLoading = true
                        message = null
                        scope.launch {
                            val intent = intentRegistry.acquire(
                                MutationIntentScope(
                                    accountScope = "${BuildConfig.BNBU_ORGANIZATION_CODE}:$normalizedEmail",
                                    operationId = "requestStudentSignInCode",
                                    actionSlot = "email-login-code"
                                ),
                                IntentFingerprint.fromCanonicalInput(
                                    "requestStudentSignInCode",
                                    "${BuildConfig.BNBU_ORGANIZATION_CODE}\n$normalizedEmail\n${locale.value}"
                                )
                            )
                            try {
                                val challenge = api.requestSignInCode(
                                    organizationCode = BuildConfig.BNBU_ORGANIZATION_CODE,
                                    account = normalizedEmail,
                                    locale = locale,
                                    intent = intent
                                )
                                challengeId = challenge.challengeId
                                code = ""
                                message = interfaceText(
                                    "验证码已发送到邮箱，请在 10 分钟内输入。",
                                    "The code was sent to your email. Enter it within 10 minutes."
                                )
                                intentRegistry.complete(intent)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                message = error.emailOnlyMessage()
                                intentRegistry.abandon(intent)
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("emailLogin.sendCode")
                ) {
                    Text(
                        if (challengeId == null) {
                            interfaceText("发送验证码", "Send code")
                        } else {
                            interfaceText("重新发送", "Send again")
                        }
                    )
                }
            }
            if (challengeId != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(10) },
                    modifier = Modifier.fillMaxWidth().testTag("emailLogin.code"),
                    enabled = !isLoading,
                    singleLine = true,
                    label = { Text(interfaceText("邮箱验证码", "Email code")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("emailLogin.message")
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val activeChallengeId = challengeId ?: return@Button
                    isLoading = true
                    message = null
                    scope.launch {
                        val intent = intentRegistry.acquire(
                            MutationIntentScope(
                                accountScope = "${BuildConfig.BNBU_ORGANIZATION_CODE}:$normalizedEmail",
                                operationId = "verifyStudentSignInCode",
                                actionSlot = activeChallengeId
                            ),
                            IntentFingerprint.fromCanonicalInput(
                                "verifyStudentSignInCode",
                                "$activeChallengeId\n${code.trim()}"
                            )
                        )
                        try {
                            api.verifySignInCode(
                                challengeId = activeChallengeId,
                                code = code.trim(),
                                deviceId = localStore.getOrCreateInstallationId(),
                                intent = intent
                            )
                            val current = api.getCurrentUser().data
                                ?: error("CURRENT_USER_DATA_MISSING")
                            intentRegistry.complete(intent)
                            onLoginSuccess(current)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            message = error.emailOnlyMessage()
                            intentRegistry.abandon(intent)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = challengeId != null && codeValid && !isLoading,
                modifier = Modifier.fillMaxWidth().testTag("emailLogin.submit")
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(interfaceText("登录", "Sign in"))
                }
            }
        }
    }
}

private fun Exception.emailOnlyMessage(): String {
    val code = (this as? V1HttpException)?.error?.code?.value
    return when (code) {
        "AUTH_VERIFICATION_CODE_INVALID" -> interfaceText(
            "验证码错误、过期或已使用，请重新获取。",
            "The code is invalid, expired, or already used. Request a new one."
        )
        "AUTH_RATE_LIMITED" -> interfaceText(
            "请求过于频繁，请稍后再试。",
            "Too many attempts. Try again later."
        )
        "SYSTEM_SERVICE_UNAVAILABLE" -> interfaceText(
            "邮件服务暂时不可用，请稍后重试。",
            "Email delivery is temporarily unavailable. Try again later."
        )
        else -> interfaceText(
            "登录失败，请检查邮箱和网络后重试。",
            "Sign-in failed. Check the email and network, then try again."
        )
    }
}
