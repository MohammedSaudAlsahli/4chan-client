package app.boardwalk.data

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest

class HomeFeedTest {
    private val g = Board("g", "Technology", true)
    private val po = Board("po", "Papercraft", true)
    private val nsfw = Board("x", "Hidden board", false)
    private fun post(id: Long, created: Long, modified: Long = created) = ApiParser.catalog(
        """[{"threads":[{"no":$id,"time":$created,"last_modified":$modified}]}]"""
    ).single()

    @Test fun filtersByBothFavoritesAndVisibility() {
        val boards = listOf(g, po, nsfw)
        assertEquals(listOf(g), HomeFeed.boards(boards, setOf("g", "x"), false))
        assertEquals(listOf(g, nsfw), HomeFeed.boards(boards, setOf("g", "x"), true))
        assertTrue(HomeFeed.boards(boards, emptySet(), true).isEmpty())
    }
    @Test fun sortsAcrossBoardsByTimeNotBoardLocalPostNumber() {
        val entries = listOf(FeedThread(g, post(90000, 100, 500)), FeedThread(po, post(4, 200, 300)))
        assertEquals(listOf("po:4", "g:90000"), HomeFeed.sorted(entries, FeedOrder.NEWEST).map { it.key })
        assertEquals(listOf("g:90000", "po:4"), HomeFeed.sorted(entries, FeedOrder.LATEST_REPLY).map { it.key })
    }
    @Test fun equalPostNumbersOnDifferentBoardsAreDistinct() {
        val entries = listOf(FeedThread(po, post(1, 100)), FeedThread(g, post(1, 100)))
        assertEquals(listOf("g:1", "po:1"), HomeFeed.sorted(entries + entries, FeedOrder.LATEST_REPLY).map { it.key })
    }
    @Test fun boardRefreshRemovesExpiredThreadsAndPreservesOtherBoards() {
        val entries = listOf(FeedThread(g, post(1, 100)), FeedThread(po, post(1, 200)))
        val refreshed = HomeFeed.replaceBoard(entries, g, listOf(post(2, 300)))
        assertEquals(setOf("g:2", "po:1"), refreshed.map { it.key }.toSet())
        assertEquals(listOf("po:1"), HomeFeed.replaceBoard(refreshed, g, emptyList()).map { it.key })
    }
    @Test fun failurePreservesOldBoardWhileOtherBoardsRefresh() = runTest {
        val old = listOf(FeedThread(g, post(1, 100)), FeedThread(nsfw, post(2, 100)))
        val states = mutableListOf<FeedProgress>()
        HomeFeed.refresh(old, listOf(g, po), { board ->
            if (board == g) throw java.io.IOException("offline")
            listOf(post(3, 200))
        }, states::add)
        assertEquals(listOf("g"), states.last().failures)
        assertEquals(setOf("g:1", "po:3"), states.last().entries.map { it.key }.toSet())
        assertEquals(2, states.last().completed)
        assertEquals(0, states.first().completed)
    }
    @Test fun navigationCancellationStopsRemainingRequests() = runTest {
        var requests = 0
        try {
            HomeFeed.refresh(emptyList(), listOf(g, po), { requests++; throw CancellationException() }, {})
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { assertEquals(1, requests) }
    }
    @Test fun missingLastModifiedFallsBackToCreationTime() {
        val post = ApiParser.catalog("""[{"threads":[{"no":1,"time":123}]}]""").single()
        assertEquals(123L, post.lastModified)
    }
    @Test fun latestReplyUsesCatalogReplyAndHotWeightsActivityAgainstAge() {
        val catalog = ApiParser.catalog("""[{"threads":[
            {"no":1,"time":1000,"replies":2,"last_modified":9000,"last_replies":[{"no":11,"time":3000,"com":"older"},{"no":12,"time":4000,"com":"newer"}]},
            {"no":2,"time":3500,"replies":50,"last_modified":3600,"last_replies":[{"no":21,"time":3600}]}
        ]}]""")
        assertEquals(12L, catalog.first().lastReply?.id)
        assertEquals("newer", catalog.first().lastReply?.comment)
        assertEquals(listOf(1L, 2L), HomeFeed.sortedBoard(catalog, FeedOrder.LATEST_REPLY).map { it.id })
        assertEquals(listOf(2L, 1L), HomeFeed.sortedBoard(catalog, FeedOrder.HOT, now = 5000).map { it.id })
    }
}
