package com.devsecops.tracker.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Shared building blocks. Every screen is made of these, so the same thing
// always looks the same: cards, status pills, tabs, empty and error states.

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    padding: Dp = Sp.l,
    content: @Composable ColumnScope.() -> Unit
) {
    // V3: 16dp corners and a hairline border, same as GlowCard, so every card
    // in the app (Progress, Mastery, Settings, Planner, Gates...) matches.
    val shape = RoundedCornerShape(16.dp)
    val base = modifier.fillMaxWidth().clip(shape).background(container)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    Column(
        (if (onClick != null) base.clickableScale(haptic = false, onClick = onClick) else base).padding(padding),
        content = content
    )
}

// Small tinted label: status, level, overdue.
@Composable
fun Pill(text: String, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = Sp.s, vertical = Sp.xs)
    )
}

// Segmented control: 2-5 short options, one always selected.
@Composable
fun SegTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .selectableGroup()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(Sp.xs),
        horizontalArrangement = Arrangement.spacedBy(Sp.xs)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val tabBg by animateColorAsState(
                if (on) MaterialTheme.colorScheme.primary else Color.Transparent,
                tween(MOTION_FAST), label = "segTab"
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(tabBg)
                    .heightIn(min = 48.dp)
                    .selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) })
                    .padding(horizontal = Sp.s),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.onSurface) {
    AppCard(modifier = modifier, padding = Sp.m) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = accent, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Track for bars/heatmap empty cells: onSurfaceVariant blended over the card so
// the graphic reaches roughly 3:1 against surfaceContainer (dark ~3.3, light ~3.3)
// while still reading as a quiet track.
@Composable
fun barTrackColor(): Color {
    val cs = MaterialTheme.colorScheme
    val light = cs.surface.luminance() > 0.5f
    return cs.onSurfaceVariant.copy(alpha = if (light) 0.75f else 0.6f).compositeOver(cs.surfaceContainer)
}

@Composable
fun ThinBar(fraction: Float, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    val animated = animatedProgress(fraction.coerceIn(0f, 1f))
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(barTrackColor())
    ) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(color))
    }
}

@Composable
fun StatusDot(done: Boolean, size: Dp = 26.dp) {
    if (done) {
        androidx.compose.material3.Icon(
            Icons.Filled.CheckCircle, contentDescription = "done",
            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(size)
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
    }
}

@Composable
fun EmptyBlock(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun ErrorBlock(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            .padding(horizontal = Sp.l, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Sp.m)
    ) {
        androidx.compose.material3.Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Column(Modifier.weight(1f)) {
            Text("Couldn't load", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (message.isBlank()) "Check your connection and try again." else message,
                style = MaterialTheme.typography.bodySmall, maxLines = 2
            )
        }
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
        ) { Text("Retry") }
    }
}

// Loading placeholder: grey blocks that breathe, instead of a lone spinner.
@Composable
fun SkeletonList(count: Int = 4, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val a by transition.animateFloat(
        0.35f, 0.9f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "skeletonAlpha"
    )
    Column(modifier.fillMaxWidth().padding(Sp.l), verticalArrangement = Arrangement.spacedBy(Sp.m)) {
        repeat(count) {
            Box(
                Modifier.fillMaxWidth().height(if (it == 0) 120.dp else 76.dp)
                    .alpha(a).clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            )
        }
    }
}

// Screens that hold a list use this so header text always looks the same.
@Composable
fun GroupHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
