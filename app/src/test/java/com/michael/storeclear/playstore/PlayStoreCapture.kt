package com.michael.storeclear.playstore

import androidx.compose.runtime.Composable
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.activityTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.michael.storeclear.R

private val playStoreCaptureOptions = RoborazziOptions(
    captureType = RoborazziOptions.CaptureType.Screenshot(),
)

@OptIn(ExperimentalRoborazziApi::class)
fun capturePlayStoreImage(
    outputPath: String,
    content: @Composable () -> Unit,
) {
    captureRoboImage(
        filePath = "../play-store/$outputPath",
        roborazziOptions = playStoreCaptureOptions,
        roborazziComposeOptions = RoborazziComposeOptions {
            activityTheme(R.style.Theme_MyApplication)
        },
        content = content,
    )
}
