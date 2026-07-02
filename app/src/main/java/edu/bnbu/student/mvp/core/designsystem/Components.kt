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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GridBackground(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .background(BNBUColors.Paper)
            .clipToBounds()
    ) {
        val spacing = 42.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(
                color = BNBUColors.Blue.copy(alpha = 0.10f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
            x += spacing
        }

        var y = 0f
        while (y <= size.height) {
            drawLine(
                color = BNBUColors.Blue.copy(alpha = 0.10f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
            y += spacing
        }
    }
}

@Composable
fun SwissPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BNBUColors.Surface)
            .border(width = 1.5.dp, color = BNBUColors.Line, shape = RectangleShape)
            .padding(18.dp),
        content = content
    )
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, compact: Boolean = false) {
    val size = if (compact) 44.dp else 64.dp
    val barWidth = if (compact) 7.dp else 10.dp
    val barHeight = if (compact) 20.dp else 28.dp
    val labelSize = if (compact) 9.sp else 12.sp

    Box(
        modifier = modifier
            .size(size)
            .background(BNBUColors.Surface)
            .border(2.dp, BNBUColors.Line),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(barWidth).height(barHeight).background(BNBUColors.Ink))
                Box(Modifier.width(barWidth).height(barHeight).background(BNBUColors.BlueLight))
                Box(Modifier.width(barWidth).height(barHeight).background(BNBUColors.Ink))
            }
            Spacer(Modifier.height(if (compact) 3.dp else 6.dp))
            Text(
                text = "BNBU",
                color = BNBUColors.Ink,
                fontSize = labelSize,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SectionTitle(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = eyebrow.uppercase(),
            color = BNBUColors.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Text(
            text = title,
            color = BNBUColors.Ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun StatusBadge(text: String, modifier: Modifier = Modifier, filled: Boolean = false) {
    Text(
        text = text,
        modifier = modifier
            .background(if (filled) BNBUColors.Ink else BNBUColors.BlueSoft)
            .border(1.dp, BNBUColors.Line)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        color = if (filled) BNBUColors.Surface else BNBUColors.Ink,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun HourProgressBar(value: Double, total: Double, modifier: Modifier = Modifier) {
    val ratio = if (total <= 0.0) 0f else (value / total).coerceIn(0.0, 1.0).toFloat()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(BNBUColors.Surface)
            .border(1.5.dp, BNBUColors.Line)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(ratio)
                .height(12.dp)
                .background(BNBUColors.Blue)
        )
    }
}

@Composable
fun EmptyPlaceholder(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    SwissPanel(modifier = modifier) {
        Text(
            text = title,
            color = BNBUColors.Ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            color = BNBUColors.Muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 21.sp
        )
    }
}

@Composable
fun PrimaryActionButton(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BNBUColors.Ink)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = BNBUColors.Surface,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = BNBUColors.Surface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/**
 * Horizontal segmented control bar.  Each value produces one equally-weighted
 * segment that toggles [selected].
 */
@Composable
fun <T> SegmentedControl(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, BNBUColors.Line, RectangleShape)
            .background(BNBUColors.Surface)
    ) {
        values.forEach { value ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (isSelected) BNBUColors.Ink else BNBUColors.Surface)
                    .clickable { onSelected(value) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label(value),
                    color = if (isSelected) BNBUColors.Surface else BNBUColors.Ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Primary / secondary action button used throughout check-in and profile flows.
 * When [filled] is true, the button renders in ink-on-white; otherwise it
 * renders with an outlined style.
 */
@Composable
fun ActionButton(
    title: String,
    icon: ImageVector,
    filled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (filled) BNBUColors.Ink else BNBUColors.Surface)
            .border(1.5.dp, BNBUColors.Line, RectangleShape)
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (filled) BNBUColors.Surface else BNBUColors.Ink,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = if (filled) BNBUColors.Surface else BNBUColors.Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

/**
 * A dismissible success message panel with a "我知道了" action.
 */
@Composable
fun StatusMessagePanel(
    message: String,
    onDismiss: () -> Unit
) {
    SwissPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "操作成功",
                tint = BNBUColors.Blue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                color = BNBUColors.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
                lineHeight = 20.sp
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

/**
 * A non-dismissible validation / error panel.
 */
@Composable
fun ValidationPanel(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BNBUColors.BlueSoft)
            .border(1.5.dp, BNBUColors.Line, RectangleShape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Error,
            contentDescription = "验证错误",
            tint = BNBUColors.Ink,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = message,
            color = BNBUColors.Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 18.sp
        )
    }
}
