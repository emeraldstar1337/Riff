package dev.lad

import android.appwidget.AppWidgetManager
import android.graphics.Paint
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class WidgetsAndFontIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun fontAndBothWidgetLayoutsLoadWithCyrillic() {
        instrumentation.runOnMainSync {
            val paint =
                Paint().apply { typeface = ResourcesCompat.getFont(context, R.font.google_sans) }
            assertTrue(paint.hasGlyph("Я"))
            assertTrue(paint.hasGlyph("R"))
            assertTrue(
                context.assets
                    .open("licenses/GoogleSans-OFL.txt")
                    .bufferedReader()
                    .use { it.readText() }
                    .contains("SIL OPEN FONT LICENSE")
            )
            listOf(R.layout.widget_compact, R.layout.widget_artwork).forEach { layout ->
                val view =
                    RemoteViews(context.packageName, layout).apply(context, FrameLayout(context))
                assertNotNull(view.findViewById<android.view.View>(R.id.widget_play))
                assertNotNull(view.findViewById<android.view.View>(R.id.widget_cover))
            }
        }
        val providers =
            AppWidgetManager.getInstance(context).installedProviders.filter {
                it.provider.packageName == context.packageName
            }
        assertEquals(2, providers.size)
        assertTrue(providers.all { it.updatePeriodMillis == 0 })
    }
}
