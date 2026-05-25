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
class PlayStoreScreenshotTest {

    private val app: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun phone_01_dashboard() {
        val viewModel = createPlayStoreViewModel(app)
        capturePlayStoreImage("phone/01_dashboard.png") {
            PlayStoreScreenshotFrame(PlayStoreScene.Dashboard, viewModel)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun phone_02_duplicates() {
        val viewModel = createPlayStoreViewModel(app)
        capturePlayStoreImage("phone/02_duplicates.png") {
            PlayStoreScreenshotFrame(PlayStoreScene.Duplicates, viewModel)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun phone_03_heatmap() {
        val viewModel = createPlayStoreViewModel(app)
        capturePlayStoreImage("phone/03_heatmap.png") {
            PlayStoreScreenshotFrame(PlayStoreScene.Heatmap, viewModel)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xxhdpi")
    fun phone_04_shredder() {
        val viewModel = createPlayStoreViewModel(app)
        capturePlayStoreImage("phone/04_shredder.png") {
            PlayStoreScreenshotFrame(PlayStoreScene.Shredder, viewModel)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tablet_01_dashboard() {
        val viewModel = createPlayStoreViewModel(app)
        capturePlayStoreImage("tablet/01_dashboard.png") {
            PlayStoreScreenshotFrame(PlayStoreScene.Dashboard, viewModel)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tablet_02_duplicates() {
        val viewModel = createPlayStoreViewModel(app)
        capturePlayStoreImage("tablet/02_duplicates.png") {
            PlayStoreScreenshotFrame(PlayStoreScene.Duplicates, viewModel)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tablet_03_heatmap() {
        val viewModel = createPlayStoreViewModel(app)
        capturePlayStoreImage("tablet/03_heatmap.png") {
            PlayStoreScreenshotFrame(PlayStoreScene.Heatmap, viewModel)
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-xhdpi")
    fun tablet_04_shredder() {
        val viewModel = createPlayStoreViewModel(app)
        capturePlayStoreImage("tablet/04_shredder.png") {
            PlayStoreScreenshotFrame(PlayStoreScene.Shredder, viewModel)
        }
    }
}
