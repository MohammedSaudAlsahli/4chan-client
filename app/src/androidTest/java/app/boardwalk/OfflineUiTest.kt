package app.boardwalk

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.boardwalk.data.*
import app.boardwalk.ui.BoardwalkApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class OfflineUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun nestedRepliesAndOfflineSavedCopyWorkWithoutNetwork() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BoardwalkApplication
        val original = app.store.backup()
        val originalProxy = app.store.proxy()
        val posts = ApiParser.thread("""{"posts":[
            {"no":1,"sub":"Offline reply test","com":"Original discussion","time":1700000000},
            {"no":2,"com":"<a href=\"#p1\">&gt;&gt;1</a> First response","time":1700000001},
            {"no":3,"com":"<a href=\"#p2\">&gt;&gt;2</a> Nested response","time":1700000002},
            {"no":4,"com":"Separate conversation","time":1700000003}
        ]}""")
        app.store.saved = listOf(SavedThread("news", 1, "Offline reply test"))
        app.store.saveOffline(OfflineThread("news", 1, "Offline reply test", 1700000010000, posts))
        app.store.saveProxy(ProxySettings(true, "127.0.0.1", "1"))
        app.store.replyLayout = "Nested"
        lateinit var model: ReaderModel
        compose.runOnIdle { model = ReaderModel(app); model.switchSection("Saved") }
        compose.setContent { BoardwalkApp(model) }
        compose.onNodeWithText("Available offline · discussion only").assertExists()
        compose.onNodeWithText("Offline reply test").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Hide 2 replies").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Hide 2 replies").performClick()
        compose.onNodeWithText("Show 2 replies").assertExists()
        compose.onNodeWithText("Reply to #1").assertDoesNotExist()
        compose.onNodeWithText("Show 2 replies").performClick()
        compose.onNodeWithText("Reply to #1").assertExists()
        val dir = File(app.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "nested-replies.png").outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("Chronological").performClick()
        compose.onNodeWithText("Hide 2 replies").assertDoesNotExist()
        compose.onNodeWithContentDescription("Go back").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Choose local backup folder").performScrollTo().assertExists()
        File(dir, "local-backup.png").outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        runBlocking(Dispatchers.IO) { app.store.restore(original); app.store.saveProxy(originalProxy) }
        compose.runOnIdle { model.network.close() }
    }
}
