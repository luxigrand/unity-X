package com.nexusneuro.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusneuro.app.domain.ChartPoint
import com.nexusneuro.app.ui.theme.NexusPalette
import com.nexusneuro.app.ui.theme.MonoStyle

@Composable
fun WaveformChart(
    title: String,
    points: List<ChartPoint>,
    modifier: Modifier = Modifier,
    emptyLabel: String = "Bekleniyor…",
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(1.dp, NexusPalette.White)
            .background(NexusPalette.Black)
            .padding(8.dp),
    ) {
        Text(
            text = title,
            style = MonoStyle.copy(fontSize = 11.sp, color = NexusPalette.White.copy(alpha = 0.7f)),
            modifier = Modifier.align(Alignment.TopStart),
        )
        if (points.size < 2) {
            Text(
                text = emptyLabel,
                style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.4f)),
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize().padding(top = 18.dp)) {
                val minY = points.minOf { it.value }
                val maxY = points.maxOf { it.value }
                val rangeY = (maxY - minY).takeIf { it > 1e-6f } ?: 1f
                val minX = points.first().time
                val maxX = points.last().time
                val rangeX = (maxX - minX).takeIf { it > 1e-6f } ?: 1f

                val path = Path()
                points.forEachIndexed { index, p ->
                    val x = ((p.time - minX) / rangeX) * size.width
                    val y = size.height - ((p.value - minY) / rangeY) * size.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = NexusPalette.White,
                    style = Stroke(width = 2f, cap = StrokeCap.Round),
                )
                // baseline grid
                drawLine(
                    color = NexusPalette.Dim,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 1f,
                )
            }
        }
    }
}
