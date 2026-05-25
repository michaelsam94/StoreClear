package com.michael.storeclear.playstore

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.michael.storeclear.R
import com.michael.storeclear.ui.theme.NeonPurple
import com.michael.storeclear.ui.theme.ObsidianBlack
import com.michael.storeclear.ui.theme.VividRed

@Composable
fun FeatureGraphicContent() {
    val gradient = Brush.horizontalGradient(
        colors = listOf(ObsidianBlack, Color(0xFF3B1F5C), VividRed.copy(alpha = 0.85f)),
    )

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .padding(horizontal = 48.dp, vertical = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "StoreClear",
                color = NeonPurple,
                fontSize = 52.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Text(
                text = "Find hidden space. Leave no trace.",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Duplicates · Heatmap · Secure shred · 100% on-device",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        Surface(
            modifier = Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(28.dp)),
            color = Color.Black.copy(alpha = 0.35f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                PlayStoreIconContent()
            }
        }
    }
}

@Composable
fun PlayStoreIconContent() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val iconBitmap = remember {
        checkNotNull(context.getDrawable(R.mipmap.ic_launcher))
            .toBitmap(512, 512)
            .asImageBitmap()
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = iconBitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
