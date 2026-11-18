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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.remember
import java.io.File
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One spacing scale, on a 4dp grid. Before this there were ten different horizontal insets
 * (8, 10, 12, 14, 16, 20, 22, 24) and nothing lined up across screens. The three margins
 * below are the only screen gutters: use them instead of a literal.
 */
object Spacing {
    /** Side margin of a full screen: header, list, section. */
    val Screen = 20.dp

    /** Gap between two cards, chips or tiles. */
    val Card = 12.dp

    /** Gap between two blocks of content. */
    val Section = 24.dp
}

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
 * [picture] and [file] are a picture behind the card's content: a card image from the app,
 * or a photo this phone chose. They are a separate layer from the screen backdrop, so a look
 * can use both. On a transparent card the picture is the card; on any other it sits behind
 * a wash, and the card keeps its own colour and its own size.
 */
@Composable
fun Modifier.vaultCard(
    shape: Shape,
    marked: Boolean = false,
    @DrawableRes picture: Int? = null,
    file: File? = null,
    /** True when the picture is the whole card, with the labels on top of it. */
    pictureIsCard: Boolean = false,
): Modifier =
    this
        .clip(shape)
        // A photo of the user's own is the card, so the surface steps aside for it; the wash
        // that keeps the labels readable is drawn over the photo, in cardPicture().
        .background(
            MaterialTheme.colorScheme.surface.copy(alpha = if (pictureIsCard) 0f else cardAlpha()),
        )
        .cardPicture(shape, picture, file, wash = pictureIsCard)
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
 * The picture behind a card. It is drawn like a background: it covers the card, cropped to
 * fill (never stretched), and everything the card already draws — the name, the count, the
 * icons — stays on top of it. So the card is the same card as on every other theme, with a
 * picture behind its content.
 *
 * [picture] is a picture built into the app; [file] is a photo this phone chose.
 */
@Composable
fun Modifier.cardPicture(
    shape: Shape,
    @DrawableRes picture: Int? = null,
    file: File? = null,
    /** True when the picture is the card: a dark wash is drawn over it, under the labels. */
    wash: Boolean = false,
): Modifier {
    if (picture == null && file == null) return this
    val res = LocalContext.current.resources
    val loaded = remember(picture, file) { CardPictures.load(res, picture, file) }
    val cached = loaded ?: return this
    return this.drawWithCache {
        onDrawWithContent {
            // The picture first, then whatever the card draws, so no label is ever covered.
            val scale = maxOf(size.width / cached.width, size.height / cached.height)
            val width = cached.width * scale
            val height = cached.height * scale
            drawImage(
                image = cached,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(cached.width, cached.height),
                dstOffset = IntOffset(((size.width - width) / 2f).toInt(), ((size.height - height) / 2f).toInt()),
                dstSize = IntSize(width.toInt() + 1, height.toInt() + 1),
                filterQuality = FilterQuality.Medium,
            )
            if (wash) {
                // Over the photo, under the labels: dark at the top and bottom, where the
                // name and the count sit, and lighter in the middle so the photo shows.
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.55f to Color.Black.copy(alpha = 0.12f),
                        1f to Color.Black.copy(alpha = 0.5f),
                    ),
                )
            }
            drawContent()
        }
    }
}

/** Reads a card picture once and keeps it while the card is on screen. */
internal object CardPictures {
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?) = size > 12
    }

    fun load(res: android.content.res.Resources, @DrawableRes picture: Int?, file: File?): ImageBitmap? {
        val key = when {
            file != null -> "f:" + file.absolutePath + ":" + file.length()
            picture != null -> "r:" + picture
            else -> return null
        }
        cache[key]?.let { return it }
        val bitmap = runCatching {
            if (file != null) {
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = 2 })
            } else {
                BitmapFactory.decodeResource(res, picture!!, BitmapFactory.Options().apply { inSampleSize = 2 })
            }?.asImageBitmap()
        }.getOrNull()
        cache[key] = bitmap
        return bitmap
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
