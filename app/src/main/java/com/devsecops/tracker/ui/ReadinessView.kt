package com.devsecops.tracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ReadinessRadarCard(
    coveragePct: Int,
    masteryPct: Int,
    accuracyPct: Int,
    revisionPct: Int,
    streakDays: Int,
    calibrationGap: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EXAM READINESS RADAR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
                Text(
                    text = "Calibration Gap: +${calibrationGap}%",
                    fontSize = 11.sp,
                    color = Color(0xFFF59E0B),
                    fontWeight = FontWeight.SemiBold
                )
            }

            ReadinessBarItem("Syllabus Coverage", coveragePct, Color(0xFF3B82F6))
            ReadinessBarItem("Topic Mastery (L3+)", masteryPct, Color(0xFF10B981))
            ReadinessBarItem("Practice Accuracy", accuracyPct, Color(0xFF06B6D4))
            ReadinessBarItem("Revision Coverage", revisionPct, Color(0xFF8B5CF6))
            ReadinessBarItem("Study Consistency", (streakDays * 7).coerceAtMost(100), Color(0xFFF59E0B))
        }
    }
}

@Composable
private fun ReadinessBarItem(label: String, pct: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 11.sp, color = Color(0xFF94A3B8))
            Text(text = "$pct%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color(0xFF1E293B), shape = RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct / 100f)
                    .background(color, shape = RoundedCornerShape(3.dp))
            )
        }
    }
}
