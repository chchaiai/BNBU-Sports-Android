package edu.bnbu.student.mvp.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.bnbu.student.mvp.R

// ═══════════════════════════════════════════════════════════════
//  GridBackground — subtle dot-grid in M3 surfaceVariant tone
// ═══════════════════════════════════════════════════════════════

@Composable
fun GridBackground(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .clipToBounds()
    ) {
        val spacing = 42.dp.toPx()
        val outlineColor = Color(0xFF747775).copy(alpha = 0.06f)
        var x = 0f
        while (x <= size.width) {
            drawLine(outlineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            x += spacing
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(outlineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            y += spacing
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SwissPanel  →  Google M3 Card
//
//  Card replaces the old white-background + black-border panel.
//  It uses tonal elevation for subtle depth and the M3 medium
//  shape (12 dp corner radius) defined in BNBUShapes.
// ═══════════════════════════════════════════════════════════════

@Composable
fun SwissPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

// ═══════════════════════════════════════════════════════════════
//  BrandMark — official BNBU emblem
// ═══════════════════════════════════════════════════════════════

@Composable
fun BrandMark(modifier: Modifier = Modifier, compact: Boolean = false) {
    val size = if (compact) 44.dp else 64.dp
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = modifier
            .size(size),
        shape = MaterialTheme.shapes.medium,
        color = Color.White,
        border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.55f)),
        shadowElevation = if (compact) 0.dp else 1.dp
    ) {
        Image(
            painter = painterResource(R.drawable.bnbu_emblem),
            contentDescription = "BNBU 校徽",
            modifier = Modifier
                .fillMaxSize()
                .padding(if (compact) 5.dp else 7.dp)
        )
    }
}

@Composable
fun UniversityBrandLockup(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandMark(compact = true)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "北师香港浸会大学",
                color = cs.onSurface,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "BNBU · STUDENT SPORTS",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.6.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SectionTitle — concise, single-line section heading
// ═══════════════════════════════════════════════════════════════

@Composable
@Suppress("UNUSED_PARAMETER")
fun SectionTitle(title: String, modifier: Modifier = Modifier, eyebrow: String = "") {
    val cs = MaterialTheme.colorScheme
    // Keep the legacy eyebrow argument while call sites migrate, but avoid
    // repeating English labels above an already descriptive Chinese title.
    Text(
        text = title,
        modifier = modifier.fillMaxWidth(),
        color = cs.onSurface,
        style = MaterialTheme.typography.headlineSmall
    )
}

// ═══════════════════════════════════════════════════════════════
//  StatusBadge — low-saturation rounded chip
//
//  Google M3 style:  unchecked → surfaceVariant background,
//  checked → primaryContainer.  No more black border.
// ═══════════════════════════════════════════════════════════════

@Composable
fun StatusBadge(text: String, modifier: Modifier = Modifier, filled: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = if (filled) cs.primaryContainer else cs.surfaceVariant,
        animationSpec = BNBUMotion.colorSpec,
        label = "statusBadgeBackground"
    )
    val fg by animateColorAsState(
        targetValue = if (filled) cs.onPrimaryContainer else cs.onSurfaceVariant,
        animationSpec = BNBUMotion.colorSpec,
        label = "statusBadgeContent"
    )

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = bg
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  HourProgressBar — M3 LinearProgressIndicator wrapper
// ═══════════════════════════════════════════════════════════════

@Composable
fun HourProgressBar(value: Double, total: Double, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val progress = if (total <= 0.0) 0f else (value / total).toFloat().coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = BNBUMotion.progressSpec,
        label = "hourProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(cs.surfaceVariant, MaterialTheme.shapes.small)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(cs.primary, MaterialTheme.shapes.small)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    scaleX = animatedProgress
                }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
//  EmptyPlaceholder — Card with muted message
// ═══════════════════════════════════════════════════════════════

@Composable
fun EmptyPlaceholder(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                color = cs.onSurface,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  PrimaryActionButton  →  M3 FilledButton
// ═══════════════════════════════════════════════════════════════

@Composable
fun PrimaryActionButton(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(
                interactionSource = interactionSource,
                enabled = enabled && !loading
            ),
        enabled = enabled && !loading,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = if (loading) cs.primary.copy(alpha = 0.58f) else cs.surfaceVariant,
            disabledContentColor = if (loading) cs.onPrimary else cs.onSurfaceVariant
        )
    ) {
        if (loading) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = cs.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text = title)
    }
}

// ═══════════════════════════════════════════════════════════════
//  SegmentedControl — M3 chip-style segmented bar
// ═══════════════════════════════════════════════════════════════

@Composable
fun <T> SegmentedControl(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup()
            .background(cs.surfaceVariant, MaterialTheme.shapes.small)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        values.forEach { value ->
            val isSelected = value == selected
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) cs.primaryContainer else Color.Transparent,
                animationSpec = BNBUMotion.colorSpec,
                label = "segmentBackground"
            )
            val contentColor by animateColorAsState(
                targetValue = if (isSelected) cs.onPrimaryContainer else cs.onSurfaceVariant,
                animationSpec = BNBUMotion.colorSpec,
                label = "segmentContent"
            )
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.97f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                ),
                label = "segmentScale"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(
                        backgroundColor,
                        MaterialTheme.shapes.small
                    )
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelected(value) }
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(value),
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ActionButton
//
//  filled=true   → M3 FilledTonalButton (PrimaryContainer)
//  filled=false  → M3 OutlinedButton
// ═══════════════════════════════════════════════════════════════

@Composable
fun ActionButton(
    title: String,
    icon: ImageVector,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val animatedModifier = modifier
        .fillMaxWidth()
        .pressScale(interactionSource = interactionSource)
    if (filled) {
        FilledTonalButton(
            onClick = onClick,
            modifier = animatedModifier,
            interactionSource = interactionSource,
            shape = MaterialTheme.shapes.large
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = title, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = animatedModifier,
            interactionSource = interactionSource,
            shape = MaterialTheme.shapes.large
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = title, maxLines = 1)
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  StatusMessagePanel — success toast in a Card
// ═══════════════════════════════════════════════════════════════

@Composable
fun StatusMessagePanel(
    message: String,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = cs.primaryContainer.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "操作成功",
                    tint = cs.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = message,
                    color = cs.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(text = "完成", filled = true)
            }
            Spacer(Modifier.height(10.dp))
            ActionButton(
                title = "知道了",
                icon = Icons.Filled.Clear,
                filled = false,
                onClick = onDismiss
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  ValidationPanel — error / warning card
// ═══════════════════════════════════════════════════════════════

@Composable
fun ValidationPanel(message: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.errorContainer, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = "验证错误",
            tint = cs.error,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            color = cs.onErrorContainer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
