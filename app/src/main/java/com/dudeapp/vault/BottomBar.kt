package com.dudeapp.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

private val BarHeight = 68.dp
private val BarBottomGap = 12.dp
private val PillWidth = 58.dp

/**
 * How much of the bottom of the screen the floating bar covers, system navigation
 * included. Lists add this to their bottom padding so the last item can scroll clear.
 */
@Composable
fun floatingBarSpace(): Dp =
    BarHeight + BarBottomGap + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home),
    REELS("Reels", Icons.Outlined.PlayCircle, Icons.Filled.PlayCircle),
    IMAGES("Images", Icons.Outlined.Image, Icons.Filled.Image),
    PDFS("PDFs", Icons.Outlined.PictureAsPdf, Icons.Filled.PictureAsPdf),
    NOTES("Notes", Icons.AutoMirrored.Outlined.StickyNote2, Icons.AutoMirrored.Filled.StickyNote2),
}

/**
 * The bar for the tab pager. The highlight follows the pager itself (taps and swipes),
 * so it can never disagree with the page on screen. The pill's position is read while
 * drawing, not while composing, so a swipe moves it without rebuilding the bar.
 */
@Composable
fun PagerBottomBar(
    pagerState: PagerState,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected by remember(pagerState) {
        derivedStateOf { Tab.entries[pagerState.targetPage.coerceIn(0, Tab.entries.lastIndex)] }
    }
    // Where the pill is, in tabs: 0 = first tab, 1.5 = halfway between the 2nd and 3rd.
    // It is the pager's own position, so the pill moves in the same frame as the page,
    // with nothing chasing behind it.
    val last = Tab.entries.lastIndex.toFloat()
    BottomBar(
        selected = selected,
        position = { (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, last) },
        onSelect = onSelect,
        modifier = modifier,
    )
}

/**
 * The bottom navigation: a soft white capsule that floats over the content, so lists
 * scroll behind it. One glowing pill slides to the current tab; the other tabs are quiet
 * grey icons with their names underneath.
 */
@Composable
fun BottomBar(
    selected: Tab,
    position: () -> Float,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val systemInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val capsule = CircleShape
    val accent = MaterialTheme.colorScheme.primary
    var rowWidth by remember { mutableIntStateOf(0) }
    Box(
        modifier
            .padding(start = 20.dp, end = 20.dp, bottom = systemInset + BarBottomGap)
            .widthIn(max = 460.dp)
            .fillMaxWidth()
            .height(BarHeight)
            // A wide, faint shadow rather than a hard edge.
            .shadow(
                elevation = 18.dp,
                shape = capsule,
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Black.copy(alpha = 0.35f),
            )
            .background(MaterialTheme.colorScheme.surface, capsule)
            .padding(horizontal = 8.dp),
    ) {
        // The glowing pill, behind the icons. Its shadow is tinted with its own colour.
        if (rowWidth > 0) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        val slot = rowWidth.toFloat() / Tab.entries.size
                        IntOffset((slot * position() + (slot - PillWidth.toPx()) / 2f).roundToInt(), 0)
                    }
                    .size(width = PillWidth, height = 44.dp)
                    .shadow(elevation = 12.dp, shape = CircleShape, ambientColor = accent, spotColor = accent)
                    .background(accent, CircleShape),
            )
        }
        Row(
            Modifier
                .fillMaxSize()
                .onSizeChanged { rowWidth = it.width }
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEachIndexed { index, tab ->
                BarItem(
                    tab = tab,
                    selected = tab == selected,
                    // 1 when the pill is exactly on this tab, 0 when it is a tab away.
                    highlight = { (1f - abs(position() - index)).coerceIn(0f, 1f) },
                    onClick = { if (tab != selected) onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BarItem(
    tab: Tab,
    selected: Boolean,
    highlight: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxHeight()
            // No ripple: the pill sliding in is the feedback, and a ripple would spill
            // outside the capsule's rounded ends.
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            )
            .semantics { contentDescription = tab.label },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            // As the pill arrives the name fades out and the icon settles in the middle.
            Modifier.graphicsLayer { translationY = highlight() * 8.5.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(23.dp)
                        .graphicsLayer { alpha = 1f - highlight() },
                )
                Icon(
                    imageVector = tab.selectedIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { alpha = highlight() },
                )
            }
            Text(
                text = tab.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .padding(top = 3.dp)
                    .graphicsLayer { alpha = 1f - highlight() },
            )
        }
    }
}
