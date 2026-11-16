package com.dudeapp.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One colour per kind of content, so each section is recognisable at a glance. */
object Accent {
    val Photo = Color(0xFF3B82F6)
    val Pdf = Color(0xFFEF4444)
    val Note = Color(0xFFF59E0B)
    val Chat = Color(0xFF10B981)
    val Reel = Color(0xFFD946EF)
}

fun accentOf(type: ItemType): Color = when (type) {
    ItemType.IMAGE -> Accent.Photo
    ItemType.PDF -> Accent.Pdf
    ItemType.TEXT -> Accent.Note
}

/**
 * The shared card look: a surface with a hairline border, or an accent ring when selected.
 * On the picture themes the surface is slightly see-through, so the background picture
 * shows through the cards instead of being hidden behind them.
 */
@Composable
fun Modifier.vaultCard(shape: Shape, marked: Boolean = false): Modifier =
    this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha() + (if (marked) 0.1f else 0f)))
        .border(
            width = if (marked) 2.dp else 1.dp,
            color = if (marked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            },
            shape = shape,
        )

/** How solid a card is: fully solid for plain themes, a little glassy on picture themes. */
@Composable
fun cardAlpha(): Float =
    if (LocalThemePreset.current.backdrop is BackdropStyle.Plain) 1f else 0.78f

/** A wash of the theme's accent over a card, strongest at the top left corner. */
@Composable
fun accentWash(accent: Color, strength: Float = 0.5f, shape: Shape): Modifier {
    if (LocalThemePreset.current.backdrop is BackdropStyle.Plain) return this
    return this.then(
        Modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    0f to accent.copy(alpha = 0.35f * strength),
                    0.55f to accent.copy(alpha = 0.10f * strength),
                    1f to Color.Transparent,
                ),
            ),
    )
}

/** A tinted rounded square holding an icon. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** The round tick shown on cards and rows while selecting. */
@Composable
fun SelectionDot(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            )
            .border(
                1.5.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** A filter pill: solid accent when selected, quiet surface otherwise. */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}
