package edu.bnbu.student.mvp.feature.login

import androidx.compose.runtime.Composable

@Composable
fun PhoneLoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    VerificationLoginScreen(
        initialMethod = VerificationLoginMethod.Phone,
        onLoginSuccess = onLoginSuccess,
        onBack = onBack
    )
}
