package com.michael.storeclear.playstore

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
class PlayStoreIconTest {

    @Test
    @Config(qualifiers = "w512dp-h512dp-mdpi")
    fun app_icon_512() {
        capturePlayStoreImage("app-icon-512.png") {
            PlayStoreIconContent()
        }
    }
}
