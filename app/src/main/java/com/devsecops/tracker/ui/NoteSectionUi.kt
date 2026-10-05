package com.devsecops.tracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// One "## section" of a note as a collapsible card: number badge, heading, chevron, body.
@Composable
fun NoteSectionCard(
    number: Int,
    heading: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(cs.surfaceContainerLow)
            .then(Modifier.semantics { contentDescription = "Section $number $heading" })
    ) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = if (open) "Band karo" else "Kholo", onClick = onToggle)
                .padding(horizontal = Sp.m, vertical = Sp.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Sp.m)
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(cs.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "%02d".format(number), color = cs.onPrimaryContainer,
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
            Text(
                heading, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(if (open) "▴" else "▾", color = cs.onSurfaceVariant, fontSize = 16.sp)
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.fillMaxWidth().padding(start = Sp.m, end = Sp.m, bottom = Sp.m)) { body() }
        }
    }
}
