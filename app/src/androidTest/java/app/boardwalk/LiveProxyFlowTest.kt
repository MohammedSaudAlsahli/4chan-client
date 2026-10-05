package app.boardwalk

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in live test: pass -e proxyPort PORT for an authenticated local test proxy. */
class LiveProxyFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String, substring: Boolean = false) {
        compose.waitUntil(60_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val dir = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun liveProxyMediaGesturesAndReturnPosition() {
        val port = InstrumentationRegistry.getArguments().getString("proxyPort")
        assumeTrue("Requires the local test proxy", port != null)
        compose.onNodeWithText("Settings").performClick()
        val proxySwitch = compose.onNodeWithContentDescription("Use a proxy")
        if (proxySwitch.fetchSemanticsNode().config[SemanticsProperties.ToggleableState] == androidx.compose.ui.state.ToggleableState.Off) proxySwitch.performClick()
        compose.onNodeWithText("Server address").performScrollTo().performTextReplacement("10.0.2.2")
        compose.onNodeWithText("Port").performScrollTo().performTextReplacement(port!!)
        compose.onNodeWithText("Username (optional)").performScrollTo().performTextReplacement("reader")
        compose.onNodeWithText("Password (optional)").performScrollTo().performTextReplacement("test-only")
        compose.onNodeWithText("Test connection").performScrollTo().performClick()
        waitFor("Connected through proxy.", substring = true)
        compose.onNodeWithText("Save").performScrollTo().performClick()
        waitFor("Proxy saved.", substring = true)
        screenshot("proxy")
        val app = compose.activity.application as BoardwalkApplication
        assertTrue(app.store.proxy().enabled)
        assertEquals("test-only", app.store.proxy().password)
        val prefs = compose.activity.getSharedPreferences("boardwalk", 0)
        assertFalse(prefs.getString("proxyCredentials", "")!!.contains("test-only"))

        compose.onNodeWithText("Boards").performClick()
        waitFor("Find a board")
        compose.onNodeWithText("Find a board").performTextInput("po")
        waitFor("Papercraft & Origami")
        compose.onNodeWithText("Papercraft & Origami").performClick()
        waitFor("Pizza Cube")
        screenshot("catalog")
        compose.onNodeWithContentDescription("Open image for post 627353").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("1 of 4").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(60_000) {
            compose.onAllNodes(hasContentDescription("full-size image", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        screenshot("viewer")
        compose.onNodeWithTag("zoomable-image").performTouchInput { doubleClick() }
        compose.onNodeWithText("Zoomed").assertExists()
        screenshot("zoom")
        compose.onNodeWithTag("zoomable-image").performTouchInput { doubleClick() }
        compose.onNodeWithText("Zoomed").assertDoesNotExist()
        compose.onNodeWithTag("zoomable-image").performTouchInput {
            pinch(start0 = center - Offset(50f, 0f), start1 = center + Offset(50f, 0f),
                end0 = center - Offset(200f, 0f), end1 = center + Offset(200f, 0f))
        }
        compose.onNodeWithText("Zoomed").assertExists()
        compose.onNodeWithTag("zoomable-image").performTouchInput { doubleClick() }
        compose.onNodeWithTag("zoomable-image").performTouchInput { swipeLeft() }
        waitFor("2 of 4")
        compose.onNodeWithContentDescription("Close image viewer").performClick()
        compose.onNodeWithTag("catalog-list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("catalog-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(before > 0)
        val thumbnails = compose.onAllNodes(hasContentDescription("Open image for post", substring = true))
        thumbnails[1].performClick()
        compose.onNodeWithContentDescription("Full-screen media viewer").assertExists()
        compose.onNodeWithContentDescription("Close image viewer").performClick()
        val after = compose.onNodeWithTag("catalog-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 0f)

        compose.runOnIdle {
            val model = androidx.lifecycle.ViewModelProvider(compose.activity)[ReaderModel::class.java]
            model.favorites.toList().forEach(model::favorite)
            listOf("g", "po").forEach(model::favorite)
        }
        // Home mixes board-local catalogs and returns to the same scrolled position.
        compose.onNodeWithText("Home").performClick()
        waitFor("Threads from 2 favorite boards")
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("/po/ ·", substring = true))
        val homeBefore = compose.onNodeWithTag("home-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val homeImages = compose.onAllNodes(hasContentDescription("Open image for post", substring = true))
        homeImages[0].performClick()
        compose.onNodeWithContentDescription("Full-screen media viewer").assertExists()
        compose.onNodeWithContentDescription("Close image viewer").performClick()
        val homeAfter = compose.onNodeWithTag("home-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(homeBefore, homeAfter, 0f)
        compose.onNodeWithText("Newest").performClick()
        compose.onNodeWithTag("home-list").performScrollToIndex(0)
        screenshot("home")
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("/po/ ·", substring = true))
        compose.onAllNodes(hasText("/po/ ·", substring = true))[0].performClick()
        compose.onNodeWithText("Thread", substring = false).assertExists()
        compose.onNodeWithContentDescription("Go back").performClick()
        compose.onNodeWithTag("home-list").assertExists()

    }
}
