package com.michael.storeclear.playstore

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Category(PlayStoreScreenshotTests::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PlayStoreFeatureGraphicTest {

    @Test
    @Config(qualifiers = "w1024dp-h500dp-mdpi")
    fun feature_graphic() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        capturePlayStoreImage("feature-graphic.png") {
            FeatureGraphicContent()
        }
    }
}
