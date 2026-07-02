package edu.bnbu.student.mvp.feature.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.core.designsystem.BNBUColors
import edu.bnbu.student.mvp.core.designsystem.BrandMark
import edu.bnbu.student.mvp.core.designsystem.GridBackground
import edu.bnbu.student.mvp.core.designsystem.PrimaryActionButton
import edu.bnbu.student.mvp.core.designsystem.SectionTitle
import edu.bnbu.student.mvp.core.designsystem.SwissPanel

@Composable
fun LoginScreen(
    onLogin: (account: String, password: String) -> Unit,
    isLoading: Boolean = false,
    errorMessage: String? = null
) {
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                BrandMark()
                Spacer(Modifier.weight(1f))
                Text(
                    text = "STUDENT APP",
                    color = BNBUColors.Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "BNBU",
                    color = BNBUColors.Ink,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 58.sp
                )
                Text(
                    text = "体育打卡与成绩进度",
                    color = BNBUColors.Ink,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 38.sp
                )
                Text(
                    text = "课程相关 10 小时 + 其他运动 10 小时，进度、缺口、审核反馈一次看清。",
                    color = BNBUColors.Muted,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp
                )
            }

            SwissPanel {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    SectionTitle(eyebrow = "SIGN IN", title = "学生登录")

                    // Error message
                    if (errorMessage != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BNBUColors.Paper)
                                .border(1.5.dp, BNBUColors.Line, RectangleShape)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                color = BNBUColors.Ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // Account field
                    BnbuTextField(
                        value = account,
                        onValueChange = { account = it },
                        label = "学号 / 邮箱",
                        placeholder = "22301142 或 s1@bnbu.edu.cn",
                        enabled = !isLoading,
                        keyboardType = KeyboardType.Email
                    )

                    // Password field
                    BnbuTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "密码",
                        placeholder = "请输入密码",
                        enabled = !isLoading,
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                    tint = BNBUColors.Muted
                                )
                            }
                        }
                    )

                    // Login button
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (isLoading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BNBUColors.Ink)
                                    .padding(vertical = 14.dp, horizontal = 16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = BNBUColors.Surface,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "登录中…",
                                    color = BNBUColors.Surface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        } else {
                            val canLogin = account.isNotBlank() && password.isNotBlank()
                            PrimaryActionButton(
                                title = "进入学生端",
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                modifier = Modifier.testTag("login.submit"),
                                onClick = { if (canLogin) onLogin(account.trim(), password) }
                            )
                        }
                    }

                    // Demo fallback
                }
            }

            Text(
                text = "第一阶段仅包含学生端体育打卡与成绩透明化；老师端和管理端由 Web 承担。",
                color = BNBUColors.Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 19.sp
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
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label.uppercase(),
            color = BNBUColors.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BNBUColors.Surface)
                .border(1.5.dp, BNBUColors.Line, RectangleShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = BNBUColors.Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = visualTransformation,
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = BNBUColors.Muted.copy(alpha = 0.5f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}
