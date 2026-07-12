package edu.bnbu.student.mvp.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

// ═══════════════════════════════════════════════════════════════
//  BrandMark — BNBU brand identity (softened for M3)
// ═══════════════════════════════════════════════════════════════

@Composable
fun BrandMark(modifier: Modifier = Modifier, compact: Boolean = false) {
    val size = if (compact) 44.dp else 64.dp
    val barWidth = if (compact) 7.dp else 10.dp
    val barHeight = if (compact) 20.dp else 28.dp
    val labelSize = if (compact) 9.sp else 12.sp
    val cs = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .size(size)
            .background(cs.surface, RoundedCornerShape(8.dp))
            .border(1.5.dp, cs.outline, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(barWidth).height(barHeight).background(cs.onSurface))
                Box(Modifier.width(barWidth).height(barHeight).background(cs.primary))
                Box(Modifier.width(barWidth).height(barHeight).background(cs.onSurface))
            }
            Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
            Text(
                text = "BNBU",
                color = cs.onSurface,
                fontSize = labelSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
//  SectionTitle — M3-flavored two-line heading
// ═══════════════════════════════════════════════════════════════

@Composable
fun SectionTitle(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = eyebrow.uppercase(),
            color = cs.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
        Text(
            text = title,
            color = cs.onSurface,
            style = MaterialTheme.typography.headlineSmall
        )
    }
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
    val bg = if (filled) cs.primaryContainer else cs.surfaceVariant
    val fg = if (filled) cs.onPrimaryContainer else cs.onSurfaceVariant

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(cs.surfaceVariant, MaterialTheme.shapes.small)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.toFloat())
                .height(12.dp)
                .background(cs.primary, MaterialTheme.shapes.small)
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
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(20.dp))
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
            .background(cs.surfaceVariant, MaterialTheme.shapes.small)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        values.forEach { value ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) cs.surface else Color.Transparent,
                        MaterialTheme.shapes.small
                    )
                    .clickable { onSelected(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(value),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) cs.onSurface else cs.onSurfaceVariant,
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
    if (filled) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = title, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large
        ) {
            Icon(imageVector = icon, contentDescription = title, modifier = Modifier.size(18.dp))
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
