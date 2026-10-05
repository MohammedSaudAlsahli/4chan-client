package app.boardwalk

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.boardwalk.data.*
import app.boardwalk.ui.BoardwalkTheme
import app.boardwalk.ui.ThreadRow
import coil.ImageLoader
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FeedInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun imageTapAndDiscussionTapAreIndependent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val post = ApiParser.thread("""{"posts":[{"no":100,"sub":"Papercraft ideas","com":"Share your current project","tim":22,"ext":".jpg","spoiler":1}]}""").first()
        var imagesOpened = 0
        var threadsOpened = 0
        compose.setContent {
            BoardwalkTheme {
                ThreadRow(post, "po", ImageLoader(context), false,
                    openThread = { threadsOpened++ }, openMedia = { imagesOpened++ }, bookmark = {})
            }
        }
        compose.onNodeWithContentDescription("Open image for post 100").performClick()
        compose.runOnIdle { assertEquals(1, imagesOpened); assertEquals(0, threadsOpened) }
        compose.onNodeWithText("Papercraft ideas").performClick()
        compose.runOnIdle { assertEquals(1, imagesOpened); assertEquals(1, threadsOpened) }
    }
}
