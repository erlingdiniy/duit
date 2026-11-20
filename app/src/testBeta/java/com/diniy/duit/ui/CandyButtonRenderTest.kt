package com.diniy.duit.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.diniy.duit.core.ui.components.SpatialGlassButton
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

/** Visual check only: writes build/ui-renders/candy-buttons.png (idle Primary on the sheet surface) when DUIT_RENDER=1. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w480dp-h160dp-xxhdpi")
class CandyButtonRenderTest {
    @Test
    fun renderButtons() {
        assumeTrue(System.getenv("DUIT_RENDER") == "1")
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            Row {
                for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                    DuitTheme(themeMode = mode) {
                        Column(Modifier.width(240.dp).background(SpatialTheme.colors.bottomSheetSurface).padding(16.dp)) {
                            SpatialGlassButton(text = "Add New Wallet", onClick = {}, modifier = Modifier.fillMaxWidth())
                            Text("Active Wallets", color = SpatialTheme.colors.primaryText, modifier = Modifier.padding(top = 20.dp))
                        }
                    }
                }
            }
        }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        File("build/ui-renders").apply { mkdirs() }.resolve("candy-buttons.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
