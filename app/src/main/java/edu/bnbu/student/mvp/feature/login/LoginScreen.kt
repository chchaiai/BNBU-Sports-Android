package edu.bnbu.student.mvp.feature.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.designsystem.GridBackground
import edu.bnbu.student.mvp.core.designsystem.BNBUMotion
import edu.bnbu.student.mvp.core.designsystem.PrimaryActionButton
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.SwissPanel
import edu.bnbu.student.mvp.core.designsystem.UniversityBrandLockup

@Composable
fun LoginScreen(
    onLogin: (account: String, password: String) -> Unit,
    onOpenPrivacy: () -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var account by rememberSaveable { mutableStateOf("") }
    // Passwords must not enter Android's saved-instance-state bundle.
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val canLogin = account.isNotBlank() && password.isNotBlank() && !isLoading
    val submitLogin = {
        if (canLogin) {
            keyboardController?.hide()
            onLogin(account.trim(), password)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GridBackground(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .testTag("screen.login"),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            UniversityBrandLockup(modifier = Modifier.fillMaxWidth())

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "BNBU",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.displayLarge
                )
                Text(
                    text = "体育打卡与成绩进度",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 38.sp
                )
                Text(
                    text = "课程相关 10 小时 + 其他运动 10 小时，进度、缺口与打卡记录一次看清。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            SwissPanel {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    SectionTitle(eyebrow = "SIGN IN", title = "学生登录")

                    // Error message
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = expandVertically(tween(BNBUMotion.Standard)) +
                            fadeIn(tween(BNBUMotion.Standard)),
                        exit = shrinkVertically(tween(BNBUMotion.Standard)) +
                            fadeOut(tween(BNBUMotion.Quick))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.shapes.small
                                )
                                .semantics { liveRegion = LiveRegionMode.Assertive }
                                .padding(12.dp)
                        ) {
                            errorMessage?.let { message ->
                                Text(
                                    text = message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Account field
                    BnbuTextField(
                        value = account,
                        onValueChange = { account = it },
                        label = "学号 / 邮箱",
                        placeholder = "22301142 或 s1@bnbu.edu.cn",
                        enabled = !isLoading,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        keyboardActions = KeyboardActions(
                            onNext = { passwordFocusRequester.requestFocus() }
                        )
                    )

                    // Password field
                    BnbuTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "密码",
                        placeholder = "请输入密码",
                        enabled = !isLoading,
                        modifier = Modifier.focusRequester(passwordFocusRequester),
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                        keyboardActions = KeyboardActions(onDone = { submitLogin() }),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                enabled = !isLoading
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    )

                    // Login button
                    PrimaryActionButton(
                        title = if (isLoading) "登录中…" else "进入学生端",
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        modifier = Modifier.testTag("login.submit"),
                        enabled = canLogin,
                        loading = isLoading,
                        onClick = submitLogin
                    )

                    TextButton(
                        onClick = onOpenPrivacy,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("登录前请阅读《隐私政策》")
                    }
                }
            }

            Text(
                text = "第一阶段仅包含学生端体育打卡与成绩透明化；老师端和管理端由 Web 承担。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun BnbuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label.uppercase(),
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction
            ),
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            trailingIcon = trailingIcon
        )
    }
}
