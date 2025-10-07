package com.example.myandroidapp.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.myandroidapp.shared.EquityPoint
import kotlin.math.max

@Composable
fun EquityCurveChart(
    points: List<EquityPoint>,
    modifier: Modifier = Modifier,
    strokeColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (points.isEmpty()) {
        Box(modifier.height(160.dp), contentAlignment = Alignment.Center) {
            Text("No equity data available", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    val minEquity = points.minOf { it.equityUsd }
    val maxEquity = points.maxOf { it.equityUsd }
    val range = max(1.0, maxEquity - minEquity)

    val normalized = remember(points) {
        points.sortedBy { it.timestamp }.mapIndexed { index, point ->
            val xRatio = index.toFloat() / max(1, points.lastIndex)
            val yRatio = (point.equityUsd - minEquity) / range
            Offset(xRatio, 1f - yRatio.toFloat())
        }
    }

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        if (normalized.size == 1) {
            val point = normalized.first()
            drawCircle(
                color = strokeColor,
                radius = 6.dp.toPx(),
                center = Offset(point.x * size.width, point.y * size.height),
            )
            return@Canvas
        }

        val path = Path().apply {
            normalized.forEachIndexed { index, offset ->
                val point = Offset(offset.x * size.width, offset.y * size.height)
                if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
            }
        }

        drawPath(
            path = path,
            color = strokeColor,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        normalized.forEachIndexed { index, offset ->
            if (index == 0 || index == normalized.lastIndex || index % max(1, normalized.size / 6) == 0) {
                drawCircle(
                    color = strokeColor,
                    radius = 4.dp.toPx(),
                    center = Offset(offset.x * size.width, offset.y * size.height),
                )
            }
        }
    }
}

