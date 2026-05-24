package com.example.presentation.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

@Composable
fun DuplicatesEmptyState(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "ghost_flicker")
    
    // Animate subtle movement to denote "identical items floating"
    val offset1 by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset1"
    )

    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha1"
    )

    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Canvas(modifier = Modifier.size(140.dp)) {
            val width = size.width
            val height = size.height

            // 1st Ghost File (Left)
            drawRoundRect(
                color = colorScheme.primary.copy(alpha = alpha1),
                topLeft = Offset(width * 0.2f + offset1, height * 0.2f),
                size = Size(width * 0.4f, height * 0.6f),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
            // 2nd Ghost File (Right, identical but overlapping)
            drawRoundRect(
                color = colorScheme.tertiary.copy(alpha = 1f - alpha1),
                topLeft = Offset(width * 0.4f - offset1, height * 0.25f),
                size = Size(width * 0.4f, height * 0.6f),
                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // Inner horizontal lines on the files
            drawLine(
                color = colorScheme.primary.copy(alpha = 0.3f),
                start = Offset(width * 0.25f + offset1, height * 0.35f),
                end = Offset(width * 0.5f + offset1, height * 0.35f),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = colorScheme.primary.copy(alpha = 0.3f),
                start = Offset(width * 0.25f + offset1, height * 0.45f),
                end = Offset(width * 0.5f + offset1, height * 0.45f),
                strokeWidth = 2.dp.toPx()
            )

            drawLine(
                color = colorScheme.tertiary.copy(alpha = 0.3f),
                start = Offset(width * 0.45f - offset1, height * 0.4f),
                end = Offset(width * 0.72f - offset1, height * 0.4f),
                strokeWidth = 2.dp.toPx()
            )
            drawLine(
                color = colorScheme.tertiary.copy(alpha = 0.3f),
                start = Offset(width * 0.45f - offset1, height * 0.5f),
                end = Offset(width * 0.72f - offset1, height * 0.5f),
                strokeWidth = 2.dp.toPx()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No duplicate files found",
            color = colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "All files are unique. Your storage is clean!",
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun ShredderEmptyState(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_flame")

    // Animate concentric rings expanding and fading
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseProgress"
    )

    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val colorScheme = MaterialTheme.colorScheme
        Canvas(modifier = Modifier.size(140.dp)) {
            val width = size.width
            val height = size.height
            val centerX = width / 2
            val centerY = height / 2

            // Concentric pulse circle
            val maxRadius = width * 0.45f
            val radius = maxRadius * pulseProgress
            val alpha = 1f - pulseProgress
            drawCircle(
                color = colorScheme.tertiary.copy(alpha = alpha * 0.3f),
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 3.dp.toPx())
            )

            // Dynamic Flame representation drawn as paths
            val flamePath = Path().apply {
                moveTo(centerX, centerY + 35)
                // Left curve
                cubicTo(
                    centerX - 45, centerY + 30,
                    centerX - 40, centerY - 25,
                    centerX - 10, centerY - 45
                )
                // Tear down and double tip
                cubicTo(
                    centerX - 15, centerY - 15,
                    centerX - 2, centerY - 12,
                    centerX + 5, centerY - 38
                )
                // Peak right tip
                cubicTo(
                    centerX + 5, centerY - 15,
                    centerX + 40, centerY + 10,
                    centerX, centerY + 35
                )
                close()
            }

            drawPath(
                path = flamePath,
                color = colorScheme.tertiary.copy(alpha = 0.85f)
            )

            // Internal flame core
            val innerFlame = Path().apply {
                moveTo(centerX, centerY + 28)
                cubicTo(
                    centerX - 25, centerY + 25,
                    centerX - 20, centerY - 10,
                    centerX - 5, centerY - 20
                )
                cubicTo(
                    centerX - 8, centerY - 5,
                    centerX + 2, centerY - 8,
                    centerX + 5, centerY - 15
                )
                cubicTo(
                    centerX + 5, centerY,
                    centerX + 20, centerY + 15,
                    centerX, centerY + 28
                )
                close()
            }
            drawPath(
                path = innerFlame,
                color = Color(0xFFFFA726) // Vivid Orange inner core
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Shredder Queue is Empty",
            color = colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Select files from the local storage to shred permanently.",
            color = colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 18.sp
        )
    }
}
