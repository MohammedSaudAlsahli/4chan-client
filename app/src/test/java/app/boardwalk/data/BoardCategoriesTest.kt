package app.boardwalk.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BoardCategoriesTest {
    @Test fun followsHomepageSectionsAndKeepsNewBoardsVisible() {
        val boards = listOf(Board("news", "Current News", true), Board("a", "Anime & Manga", true),
            Board("vt", "Virtual YouTubers", true), Board("b", "Random", false), Board("newboard", "New", true))
        val groups = BoardCategories.group(boards)
        assertEquals(listOf("Japanese Culture", "Other", "Misc.", "New boards"), groups.map { it.name })
        assertEquals(listOf("a", "vt"), groups.first().boards.map { it.id })
        assertEquals(listOf("newboard"), groups.last().boards.map { it.id })
    }
}
