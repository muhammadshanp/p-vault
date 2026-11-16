package com.dudeapp.vault

import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
 * The shared card look: a solid surface with a hairline border, or an accent ring when
 * selected. Cards are opaque on every theme, including the picture ones: the picture sits
 * behind the screen and inside the cards, never through them.
 *
 * [picture] is a card image painted inside the card, on its right side, fading into the
 * card colour. It is a separate layer from the screen backdrop, so a look can use both.
 */
@Composable
fun Modifier.vaultCard(
    shape: Shape,
    marked: Boolean = false,
    @DrawableRes picture: Int? = null,
): Modifier =
    this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha()))
        .cardPicture(shape, picture)
        .border(
            width = if (marked) 2.dp else 1.dp,
            color = if (marked) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
            },
            shape = shape,
        )

/**
 * The picture inside a card: it covers the whole card, cropped to fill it, so any picture
 * can be used whatever its size. The card keeps its shape, its size and its border, and a
 * soft dark wash keeps the label readable over the picture.
 *
 * The picture is scaled like ContentScale.Crop: it fills the card without stretching, and
 * the extra is trimmed equally on both sides.
 */
@Composable
private fun Modifier.cardPicture(shape: Shape, picture: Int?): Modifier {
    if (picture == null) return this
    val res = LocalContext.current.resources
    val isDark = LocalThemePreset.current.dark
    // Over a picture the label needs help: dark text on light pictures and the reverse.
    val wash = if (isDark) Color.Black else Color.White
    return this.drawWithCache {
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val image = BitmapFactory.decodeResource(res, picture, options)?.asImageBitmap()
        onDrawWithContent {
            drawContent()
            if (image == null || image.width == 0 || image.height == 0) return@onDrawWithContent
            val scale = maxOf(size.width / image.width, size.height / image.height)
            val width = image.width * scale
            val height = image.height * scale
            drawImage(
                image = image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(((size.width - width) / 2f).toInt(), ((size.height - height) / 2f).toInt()),
                dstSize = IntSize(width.toInt() + 1, height.toInt() + 1),
                filterQuality = FilterQuality.Medium,
            )
            // A gentle gradient at the bottom and behind the text keeps every label legible,
            // whichever part of the picture happens to be light.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to wash.copy(alpha = if (isDark) 0.30f else 0.25f),
                    0.55f to Color.Transparent,
                    1f to wash.copy(alpha = if (isDark) 0.62f else 0.55f),
                ),
            )
        }
    }
}

/**
 * How solid a card is. Only the glassy looks (Moonlit) let the backdrop through; every
 * other theme keeps fully opaque cards.
 */
@Composable
fun cardAlpha(): Float = if (LocalThemePreset.current.glass) 0.72f else 1f

/** A wash of one of the section colours, for the tinted look; nothing on other themes. */
@Composable
fun Modifier.tileTint(tint: Color, shape: Shape): Modifier {
    if (!LocalThemePreset.current.tileTints) return this
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                0f to tint.copy(alpha = 0.34f),
                0.6f to tint.copy(alpha = 0.12f),
                1f to tint.copy(alpha = 0.04f),
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
