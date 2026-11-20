package com.diniy.duit.settings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.candySwitchColors
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.core.ui.theme.SpatialTheme
import com.diniy.duit.domain.model.ThemeMode
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Visual check only: writes build/ui-renders/switches.png when DUIT_RENDER=1. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w240dp-h120dp-xxhdpi")
class CandySwitchRenderTest {
    @Test
    fun renderSwitches() {
        assumeTrue(System.getenv("DUIT_RENDER") == "1")
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            Row {
                for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                    DuitTheme(themeMode = mode) {
                        Column(Modifier.background(SpatialTheme.colors.appBackground).padding(12.dp)) {
                            Switch(checked = false, onCheckedChange = {}, colors = candySwitchColors())
                            Switch(checked = true, onCheckedChange = {}, colors = candySwitchColors())
                        }
                    }
                }
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File("build/ui-renders").apply { mkdirs() }.resolve("switches.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
