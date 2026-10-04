package com.devsecops.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devsecops.tracker.data.Topic
import com.devsecops.tracker.data.MasteryRecord
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

data class GraphNode(
    val topic: Topic,
    val center: Offset,
    val radius: Float = 42f,
    val level: String = "L1"
)

@Composable
fun KnowledgeGraphCanvas(
    topics: List<Topic>,
    masteryRecords: List<MasteryRecord>,
    onSelectTopic: (Topic) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTopicId by remember { mutableStateOf<Int?>(topics.firstOrNull()?.id) }

    val masteryMap = remember(masteryRecords) {
        val map = mutableMapOf<Int, String>()
        for (i in masteryRecords.indices) {
            val rec = masteryRecords[i]
            map[rec.topic_id] = rec.level
        }
        map
    }

    val displayTopics = remember(topics) { topics.take(8) }

    val nodes = remember(displayTopics, masteryMap) {
        val list = mutableListOf<GraphNode>()
        val count = if (displayTopics.isEmpty()) 1 else displayTopics.size
        for (i in displayTopics.indices) {
            val topic = displayTopics[i]
            val angle = (i * 2.0 * PI / count).toFloat()
            val radius = 220f
            val cx = 400f + radius * cos(angle)
            val cy = 300f + radius * sin(angle)
            val lvl = masteryMap[topic.id] ?: "L1"
            list.add(GraphNode(topic = topic, center = Offset(cx, cy), level = lvl))
        }
        list
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(nodes) {
                            detectTapGestures { tapOffset ->
                                val tapped = nodes.find { node ->
                                    (tapOffset - node.center).getDistance() <= node.radius * 1.5f
                                }
                                if (tapped != null) {
                                    selectedTopicId = tapped.topic.id
                                    onSelectTopic(tapped.topic)
                                }
                            }
                        }
                ) {
                    val centerPoint = Offset(size.width / 2, size.height / 2)

                    // 1. Draw central Hub
                    drawCircle(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        radius = 80f,
                        center = centerPoint
                    )
                    drawCircle(
                        color = Color(0xFF10B981),
                        radius = 18f,
                        center = centerPoint
                    )

                    // 2. Draw dependency edges
                    for (i in nodes.indices) {
                        val node = nodes[i]
                        val color = if (node.topic.id == selectedTopicId) Color(0xFF10B981) else Color(0xFF334155)
                        drawLine(
                            color = color,
                            start = centerPoint,
                            end = node.center,
                            strokeWidth = if (node.topic.id == selectedTopicId) 4f else 2f
                        )
                    }

                    // 3. Draw nodes
                    for (i in nodes.indices) {
                        val node = nodes[i]
                        val isSelected = node.topic.id == selectedTopicId
                        val nodeColor = when (node.level) {
                            "L5" -> Color(0xFF10B981)
                            "L4" -> Color(0xFF14B8A6)
                            "L3" -> Color(0xFF06B6D4)
                            "L2" -> Color(0xFFF59E0B)
                            else -> Color(0xFF64748B)
                        }

                        // Outer Glow Ring
                        drawCircle(
                            color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.3f) else nodeColor.copy(alpha = 0.2f),
                            radius = node.radius + 12f,
                            center = node.center
                        )

                        // Main Node
                        drawCircle(
                            color = nodeColor,
                            radius = node.radius,
                            center = node.center
                        )

                        // Border
                        if (isSelected) {
                            drawCircle(
                                color = Color.White,
                                radius = node.radius + 2f,
                                center = node.center,
                                style = Stroke(width = 3f)
                            )
                        }
                    }
                }

                // Header Overlay
                Text(
                    text = "Interactive Knowledge Graph Visualizer",
                    style = TextStyle(color = Color(0xFF94A3B8), fontSize = 11.sp),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}
