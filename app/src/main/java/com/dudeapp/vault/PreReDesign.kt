package com.dudeapp.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The P Vault design language, taken from the Figma file (Sign In + Chat).
 *
 * Everything here is a token or a small component, so the screens read like the design
 * without hard-coding a palette into each one. The design is monochrome — black ink on
 * white, a 1dp #E0E0E0 hairline on fields, 8dp control corners, 18dp bubbles — and the
 * dark twin simply swaps the two ends of that pair, so it sits inside the app's existing
 * themes instead of fighting them.
 */
internal data class PreReColors(
    val page: Color,
    val ink: Color,
    val muted: Color,
    val border: Color,
    val divider: Color,
    val field: Color,
    val primaryFill: Color,
    val onPrimaryFill: Color,
    val bubbleMine: Color,
    val onBubbleMine: Color,
    val bubbleOther: Color,
    val onBubbleOther: Color,
)

private val LightPreRe = PreReColors(
    page = Color.White,
    ink = Color.Black,
    muted = Color(0xFF828282),
    border = Color(0xFFE0E0E0),
    divider = Color(0xFFE6E6E6),
    field = Color.White,
    primaryFill = Color.Black,
    onPrimaryFill = Color.White,
    bubbleMine = Color.Black,
    onBubbleMine = Color.White,
    bubbleOther = Color(0xFFE9E9EB),
    onBubbleOther = Color.Black,
)

private val DarkPreRe = PreReColors(
    page = Color(0xFF0C0D10),
    ink = Color(0xFFF5F5F7),
    muted = Color(0xFF9A9AA0),
    border = Color(0xFF2E2E33),
    divider = Color(0xFF26262B),
    field = Color(0xFF16161A),
    primaryFill = Color(0xFFF5F5F7),
    onPrimaryFill = Color(0xFF0C0D10),
    bubbleMine = Color(0xFFF5F5F7),
    onBubbleMine = Color(0xFF0C0D10),
    bubbleOther = Color(0xFF2C2C2F),
    onBubbleOther = Color(0xFFF5F5F7),
)

/** Picks the twin that suits the theme in use, so downloaded themes keep working. */
@Composable
internal fun preReColors(): PreReColors =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkPreRe else LightPreRe

/**
 * The chat footer field from the design: 40dp tall, 8dp corners, a 1dp #E0E0E0 hairline,
 * 14sp text, and it grows to four lines without the border changing weight. Built on
 * BasicTextField so the composer isn't boxed in by Material's minimum height and padding.
 */
@Composable
internal fun PreReChatField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = preReColors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        maxLines = 4,
        textStyle = TextStyle(color = colors.ink, fontSize = 14.sp, lineHeight = 20.sp),
        cursorBrush = SolidColor(colors.ink),
        modifier = modifier,
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(PreReControlShape)
                    .background(colors.field)
                    .border(1.dp, colors.border, PreReControlShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = colors.muted, fontSize = 14.sp)
                    }
                    field()
                }
            }
        },
    )
}

/**
 * The one deliberate piece of colour in a monochrome design: the owner's tick.
 * 14dp, #1D9BF0, and only ever next to a name the server marks verified.
 */
internal val PreReVerifiedBlue = Color(0xFF1D9BF0)

@Composable
internal fun VerifiedTick(size: androidx.compose.ui.unit.Dp = 14.dp) {
    Icon(
        imageVector = androidx.compose.material.icons.Icons.Filled.Verified,
        contentDescription = "Verified",
        tint = PreReVerifiedBlue,
        modifier = Modifier.size(size),
    )
}

/** 8dp everywhere in the design's controls: fields, buttons, cards. */
internal val PreReControlShape = RoundedCornerShape(8.dp)

/** The design's app name: 24sp semi-bold, −0.24 tracking, 150% line height. */
@Composable
internal fun PreReAppName(text: String = "P Vault", color: Color = preReColors().ink) {
    Text(
        text = text,
        color = color,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.24).sp,
        lineHeight = 36.sp,
        textAlign = TextAlign.Center,
    )
}

/** "Create an account" — 16sp semi-bold. */
@Composable
internal fun PreReHeading(text: String, color: Color = preReColors().ink) {
    Text(
        text = text,
        color = color,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 24.sp,
        textAlign = TextAlign.Center,
    )
}

/** "Enter your email to sign up for this app" — 14sp regular. */
@Composable
internal fun PreReBody(text: String, color: Color = preReColors().ink, align: TextAlign = TextAlign.Center) {
    Text(
        text = text,
        color = color,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        textAlign = align,
    )
}

/** The design's text field: white, 1dp #E0E0E0, 8dp corners, 16dp side padding. */
@Composable
internal fun PreReField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = preReColors()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(
            color = colors.ink,
            fontSize = 14.sp,
            lineHeight = 19.6.sp,
        ),
        cursorBrush = SolidColor(colors.ink),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        decorationBox = { field ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp)
                    .clip(PreReControlShape)
                    .background(colors.field)
                    .border(1.dp, colors.border, PreReControlShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(placeholder, color = colors.muted, fontSize = 14.sp)
                }
                field()
            }
        },
    )
}

/** The design's Continue button: solid ink, 8dp corners, 40dp tall, 14sp medium. */
@Composable
internal fun PreRePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    busyLabel: String = "Signing in…",
) {
    val colors = preReColors()
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = PreReControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryFill,
            contentColor = colors.onPrimaryFill,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = colors.onPrimaryFill,
            )
            Spacer(Modifier.width(8.dp))
            Text(busyLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        } else {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** The design's second route: outlined, same 8dp corners and 40dp height. */
@Composable
internal fun PreReOutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = preReColors()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = PreReControlShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.height(40.dp),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** A hairline with "or" sitting in the middle, exactly as the design draws it. */
@Composable
internal fun PreReDividerLabel(label: String) {
    val colors = preReColors()
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.divider),
        )
        Text(
            text = label,
            color = colors.muted,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.divider),
        )
    }
}

/** The small Google glyph the design puts beside "Google". */
@Composable
internal fun PreReGoogleGlyph() {
    Text(
        text = "G",
        color = Color(0xFF4285F4),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * Bubble corners from the design: 18dp, with the 4dp tail on the first or last bubble of a
 * run so a stack reads as one shape. `mine` mirrors the geometry to the right side.
 */
@Composable
internal fun preReBubbleShape(mine: Boolean, first: Boolean, last: Boolean): RoundedCornerShape {
    val big = 18.dp
    val tail = 4.dp
    return if (mine) {
        RoundedCornerShape(
            topStart = big,
            topEnd = if (first) big else tail,
            bottomEnd = if (last) tail else big,
            bottomStart = big,
        )
    } else {
        RoundedCornerShape(
            topStart = if (first) tail else big,
            topEnd = big,
            bottomEnd = big,
            bottomStart = if (last) big else tail,
        )
    }
}
