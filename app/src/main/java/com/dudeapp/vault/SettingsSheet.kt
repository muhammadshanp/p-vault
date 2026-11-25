package com.dudeapp.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The settings look: one white sheet raised over the dark header, rows separated by space
 * rather than lines, and a quiet arrow on everything that opens something.
 *
 * Borrowed from the reference screens for the shape of the page. Colours, type and radius
 * all come from the app's theme, so every look still styles it.
 */

/** A raised white group of rows. Consecutive groups are 14dp apart. */
@Composable
fun SettingsSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 6.dp),
        content = content,
    )
}

/** A quiet label above a group. */
@Composable
fun SettingsSheetLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(start = 6.dp, bottom = 6.dp, top = 2.dp),
    )
}

/**
 * One row of a sheet: icon, title, optional value on the right, then the arrow when the
 * row opens something. [trailing] replaces the arrow, for switches and other controls.
 */
@Composable
fun SettingsSheetRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            modifier = Modifier.size(19.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text(
                text = value,
                color = if (valueColor != Color.Unspecified) valueColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                fontSize = 13.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(6.dp))
        }
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** A hairline between rows, inset past the icons. */
@Composable
fun SettingsSheetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 49.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
    )
}

/** The tick shown beside a name, at the sizes the app uses. */
@Composable
fun TickBadge(tick: AccessTick, size: Dp = 14.dp) {
    when (tick) {
        AccessTick.Blue -> Icon(
            Icons.Filled.Verified,
            contentDescription = "Admin",
            tint = PreReVerifiedBlue,
            modifier = Modifier.size(size),
        )

        AccessTick.Black -> Icon(
            Icons.Filled.Verified,
            contentDescription = "Partner",
            tint = PreRePartnerBlack,
            modifier = Modifier.size(size),
        )

        AccessTick.None -> Unit
    }
}

/**
 * The three tick choices the owner picks from: none, blue (admin role), black (partner).
 * A row of pills, because there are only ever three and the current one has to be obvious.
 */
@Composable
fun TickChooser(
    selected: AccessTick,
    enabled: Boolean,
    onSelect: (AccessTick) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        AccessTick.None to "No tick",
        AccessTick.Blue to "Blue · admin",
        AccessTick.Black to "Black · partner",
    )
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (tick, label) ->
            val active = tick == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    .border(
                        1.dp,
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        RoundedCornerShape(10.dp),
                    )
                    .clickable(enabled = enabled) { onSelect(tick) }
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (tick != AccessTick.None) {
                    TickBadge(
                        tick = if (active) AccessTick.None else tick,
                        size = 13.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = label,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A small header used above the sheet: back arrow, title, and a trailing action. */
@Composable
fun SettingsHeader(
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/** Spacer between two sheets. */
@Composable
fun SheetGap(height: Dp = 14.dp) {
    Spacer(Modifier.height(height))
}
