package app.boardwalk.data

import org.junit.Assert.*
import org.junit.Test

class ApiParserTest {
    @Test fun parsesOptionalFieldsAndPreservesSpoilers() {
        val result = ApiParser.thread("""{"posts":[{"no":100,"tim":12345,"ext":".png","spoiler":1,"w":1200,"h":800},{"no":101}]}""")
        assertEquals(2, result.size)
        assertTrue(result[0].spoiler)
        assertEquals("https://i.4cdn.org/g/12345.png", result[0].media("g"))
        assertFalse(result[1].hasMedia)
        assertEquals("Anonymous", result[1].name)
    }
    @Test fun deletedAndUnsupportedAttachmentsAreNotOfferedAsMedia() {
        val posts = ApiParser.thread("""{"posts":[{"no":1,"tim":22,"ext":".jpg","filedeleted":1},{"no":2,"tim":23,"ext":".html"}]}""")
        assertTrue(posts.none { it.hasMedia })
        assertNull(posts[0].thumbnail("g"))
        assertNull(posts[1].media("g"))
    }
    @Test fun catalogsFlattenPagesWithoutIncludingReplies() {
        val posts = ApiParser.catalog("""[{"page":1,"threads":[{"no":100,"replies":8,"last_replies":[{"no":101}]}]},{"page":2,"threads":[{"no":200}]}]""")
        assertEquals(listOf(100L, 200L), posts.map { it.id })
        assertEquals(8, posts.first().replies)
    }
}
