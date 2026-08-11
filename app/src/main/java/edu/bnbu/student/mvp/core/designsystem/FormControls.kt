package edu.bnbu.student.mvp.core.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Shared mobile form field used by focused account and enrollment flows.
 * Labels, supporting copy and errors live outside the control so the field
 * remains calm and legible instead of behaving like a dense web form.
 */
@Composable
fun BNBUFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    errorText: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    counter: Pair<Int, Int>? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    inputModifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val counterVisible = counter?.let { (current, maximum) ->
        focused || current >= (maximum - 16).coerceAtLeast(0)
    } == true

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BNBULayout.Space8)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BNBULayout.Space8)
        ) {
            Text(
                text = label,
                color = colors.onSurface,
                style = MaterialTheme.typography.labelLarge
            )
            if (required) {
                Text(
                    text = interfaceText("必填", "Required"),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = inputModifier
                .fillMaxWidth()
                .heightIn(min = BNBULayout.PrimaryControlHeight)
                .testTag(testTag),
            enabled = enabled,
            singleLine = singleLine,
            placeholder = placeholder?.let {
                {
                    Text(
                        text = it,
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            },
            isError = errorText != null,
            interactionSource = interactionSource,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            shape = MaterialTheme.shapes.medium,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerLow,
                unfocusedContainerColor = colors.surfaceContainerLow,
                disabledContainerColor = colors.surfaceVariant,
                errorContainerColor = colors.errorContainer.copy(alpha = 0.18f),
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outlineVariant,
                disabledBorderColor = Color.Transparent,
                errorBorderColor = colors.error,
                cursorColor = colors.primary,
            )
        )

        if (errorText != null || supportingText != null || counterVisible) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(BNBULayout.Space8)
            ) {
                Text(
                    text = errorText ?: supportingText.orEmpty(),
                    modifier = Modifier.weight(1f),
                    color = if (errorText != null) colors.error else colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                if (counterVisible) {
                    val (current, maximum) = requireNotNull(counter)
                    Text(
                        text = "$current/$maximum",
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/** A quiet text-only primary action with explicit loading and disabled states. */
@Composable
fun BNBUPrimaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    AppleButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = BNBULayout.PrimaryControlHeight),
        enabled = enabled && !loading,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = if (loading) {
                colors.primary.copy(alpha = 0.58f)
            } else {
                colors.surfaceVariant
            },
            disabledContentColor = if (loading) colors.onPrimary else colors.onSurfaceVariant,
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(BNBULayout.Space8))
        }
        Text(text = title, style = MaterialTheme.typography.labelLarge)
    }
}
