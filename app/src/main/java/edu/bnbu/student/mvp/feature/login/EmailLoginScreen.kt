package edu.bnbu.student.mvp.feature.login

import androidx.compose.runtime.Composable

@Composable
fun EmailLoginScreen(
    onLoginSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    VerificationLoginScreen(
        initialMethod = VerificationLoginMethod.Email,
        onLoginSuccess = onLoginSuccess,
        onBack = onBack
    )
}
