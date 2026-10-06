package app.boardwalk.data

import org.junit.Assert.*
import org.junit.Test

class ReaderLinksTest {
    @Test fun boardAndCrossBoardThreadStayInsideReader() {
        assertEquals(ReaderLink("b"), ReaderLinks.parse("https://boards.4chan.org/b/", "a", 1))
        assertEquals(ReaderLink("b"), ReaderLinks.parse("https://www.4chan.org/b/", "a", 1))
        assertEquals(ReaderLink("b", 42, 43), ReaderLinks.parse("//boards.4channel.org/b/thread/42#p43", "a", 1))
        assertEquals(ReaderLink("b", 42), ReaderLinks.parse("/b/res/42", "a", 1))
        assertEquals(ReaderLink("a", 1, 5), ReaderLinks.parse("#p5", "a", 1))
    }
    @Test fun rejectsExternalAndUnsafeLinks() {
        assertNull(ReaderLinks.parse("https://boards.4chan.org.evil.test/b/thread/42"))
        assertNull(ReaderLinks.parse("javascript:alert(1)"))
        assertNull(ReaderLinks.parse("https://example.org/b/"))
        assertNull(ReaderLinks.parse("/b/thread/not-a-number"))
    }
    @Test fun extractsQuotedCrossBoardPosts() {
        val html = """<a href="//boards.4chan.org/b/thread/42#p43">&gt;&gt;&gt;/b/43</a>"""
        assertEquals(listOf(ReaderLink("b", 42, 43)), ReaderLinks.links(html, "a", 1))
    }
}
