package com.michael.storeclear.presentation.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.michael.storeclear.domain.model.DirectoryHeatNode

data class HeatmapCell(
    val node: DirectoryHeatNode,
    val rect: Rect,
    val color: Color
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun HeatmapCanvas(
    rootNode: DirectoryHeatNode,
    onCellClick: (DirectoryHeatNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val cells = remember(rootNode) { mutableListOf<HeatmapCell>() }
    val textMeasurer = rememberTextMeasurer()

    val cool = Color(0xFF2B2930)
    val glow = Color(0xFF4A3728)
    val hot = Color(0xFFB91C1C)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(rootNode) {
                detectTapGestures { offset ->
                    val clicked = cells.find { it.rect.contains(offset) }
                    if (clicked != null && clicked.node.children.isNotEmpty()) {
                        onCellClick(clicked.node)
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            cells.clear()
            val canvasWidth = size.width
            val canvasHeight = size.height

            if (rootNode.sizeBytes <= 0) return@Canvas

            // We start layout with list of direct children of this root node
            val childrenToLayout = rootNode.children.filter { it.sizeBytes > 0 }
            if (childrenToLayout.isEmpty()) {
                // If no subfolders list root as a single large rectangle
                val rect = Rect(0f, 0f, canvasWidth, canvasHeight)
                val cellColor = hot
                cells.add(HeatmapCell(rootNode, rect, cellColor))
                
                drawRoundRect(
                    color = cellColor,
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
                // Draw name text
                drawCellText(textMeasurer, rootNode.name, formatSize(rootNode.sizeBytes), rect)
                return@Canvas
            }

            // Perform proportional tree-strip slicing
            val maxChildSize = childrenToLayout.maxOfOrNull { it.sizeBytes } ?: 1L
            layoutTreemap(
                children = childrenToLayout,
                rect = Rect(0f, 0f, canvasWidth, canvasHeight),
                horizontal = canvasWidth >= canvasHeight,
                cells = cells,
                maxSize = maxChildSize,
                cool = cool,
                glow = glow,
                hot = hot
            )

            // Render cells
            for (cell in cells) {
                drawRoundRect(
                    color = cell.color,
                    topLeft = Offset(cell.rect.left + 2f, cell.rect.top + 2f),
                    size = Size(cell.rect.width - 4f, cell.rect.height - 4f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.25f),
                    topLeft = Offset(cell.rect.left + 2f, cell.rect.top + 2f),
                    size = Size(cell.rect.width - 4f, cell.rect.height - 4f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Draw labels inside cells
                drawCellText(textMeasurer, cell.node.name, formatSize(cell.node.sizeBytes), cell.rect)
            }
        }
    }
}

private fun layoutTreemap(
    children: List<DirectoryHeatNode>,
    rect: Rect,
    horizontal: Boolean,
    cells: MutableList<HeatmapCell>,
    maxSize: Long,
    cool: Color,
    glow: Color,
    hot: Color
) {
    if (children.isEmpty()) return
    val totalSize = children.sumOf { it.sizeBytes }
    if (totalSize <= 0) return

    var currentOffset = if (horizontal) rect.left else rect.top
    val totalAvailableLength = if (horizontal) rect.width else rect.height

    for (child in children) {
        val fraction = child.sizeBytes.toFloat() / totalSize
        val sliceLength = totalAvailableLength * fraction

        val childRect = if (horizontal) {
            Rect(
                left = currentOffset,
                top = rect.top,
                right = currentOffset + sliceLength,
                bottom = rect.bottom
            )
        } else {
            Rect(
                left = rect.left,
                top = currentOffset,
                right = rect.right,
                bottom = currentOffset + sliceLength
            )
        }

        // Color coding lerp from max size in local node
        val intensityFraction = if (maxSize > 0) (child.sizeBytes.toFloat() / maxSize).coerceIn(0f, 1f) else 0f
        val color = if (intensityFraction < 0.5f) {
            lerp(cool, glow, intensityFraction * 2f)
        } else {
            lerp(glow, hot, (intensityFraction - 0.5f) * 2f)
        }

        cells.add(HeatmapCell(child, childRect, color))
        currentOffset += sliceLength
    }
}

@OptIn(ExperimentalTextApi::class)
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCellText(
    textMeasurer: TextMeasurer,
    name: String,
    sizeStr: String,
    rect: Rect
) {
    if (rect.width < 50f || rect.height < 40f) return // Too small to render text

    val fontSize = if (rect.width < 100f || rect.height < 60f) 10.sp else 12.sp
    val label = "$name\n$sizeStr"

    val textLayoutResult = textMeasurer.measure(
        text = AnnotatedString(label),
        style = TextStyle(
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = (fontSize.value + 2).sp
        ),
        overflow = TextOverflow.Ellipsis,
        constraints = androidx.compose.ui.unit.Constraints(
            maxWidth = (rect.width - 12f).toInt().coerceAtLeast(0),
            maxHeight = (rect.height - 8f).toInt().coerceAtLeast(0)
        )
    )

    drawText(
        textLayoutResult = textLayoutResult,
        topLeft = Offset(rect.left + 6f, rect.top + 6f)
    )
}
