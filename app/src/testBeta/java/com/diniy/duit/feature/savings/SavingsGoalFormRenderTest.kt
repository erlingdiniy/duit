package com.diniy.duit.feature.savings

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.diniy.duit.core.ui.theme.DuitTheme
import com.diniy.duit.domain.model.ThemeMode
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Visual check only: writes PNGs to build/ui-renders when DUIT_RENDER=1. Not part of normal runs. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class SavingsGoalFormRenderTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun renderLightAndDark() {
        assumeTrue(System.getenv("DUIT_RENDER") == "1")
        val out = File("build/ui-renders").apply { mkdirs() }
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            controller.get().setContent { DuitTheme(themeMode = mode) { SavingsGoalEditorSheet(null, { _, _ -> }, {}) } }
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            compose.mainClock.advanceTimeBy(2_000)
            org.robolectric.shadows.ShadowLooper.idleMainLooper()
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
            val w = 411 * 3; val h = 891 * 3
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(out, "goal-form-${mode.name.lowercase()}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            controller.pause().stop().destroy()
        }
    }
}
