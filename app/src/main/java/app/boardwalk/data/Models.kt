package app.boardwalk.data

import org.json.JSONArray
import org.json.JSONObject

data class Board(val id: String, val title: String, val workSafe: Boolean)
data class Post(
    val id: Long, val subject: String, val comment: String, val name: String,
    val time: Long, val replies: Int, val images: Int, val mediaId: Long?,
    val extension: String, val filename: String, val spoiler: Boolean,
    val width: Int, val height: Int, val archived: Boolean,
    val lastModified: Long = time,
) {
    val hasMedia get() = mediaId != null && extension in setOf(".jpg", ".png", ".gif", ".webm", ".pdf")
    fun thumbnail(board: String) = mediaId?.let { "https://i.4cdn.org/$board/${it}s.jpg" }
    fun media(board: String) = if (hasMedia) "https://i.4cdn.org/$board/$mediaId$extension" else null
}
data class SavedThread(val board: String, val id: Long, val title: String)
data class ReadPosition(val index: Int = 0, val offset: Int = 0)

object ApiParser {
    fun encodeThread(posts: List<Post>): String = JSONObject().put("posts", JSONArray().apply {
        posts.forEach { p -> put(JSONObject().put("no", p.id).put("sub", p.subject).put("com", p.comment).put("name", p.name)
            .put("time", p.time).put("replies", p.replies).put("images", p.images).put("tim", p.mediaId ?: 0)
            .put("ext", p.extension).put("filename", p.filename).put("spoiler", if (p.spoiler) 1 else 0)
            .put("w", p.width).put("h", p.height).put("archived", if (p.archived) 1 else 0).put("last_modified", p.lastModified)) }
    }).toString()

    fun boards(body: String): List<Board> {
        val list = JSONObject(body).getJSONArray("boards")
        return (0 until list.length()).map { i -> list.getJSONObject(i).let {
            Board(it.getString("board"), it.getString("title"), it.optInt("ws_board") == 1)
        } }
    }
    fun catalog(body: String): List<Post> {
        val pages = JSONArray(body)
        return (0 until pages.length()).flatMap { posts(pages.getJSONObject(it).getJSONArray("threads")) }
    }
    fun thread(body: String) = posts(JSONObject(body).getJSONArray("posts"))
    private fun posts(list: JSONArray) = (0 until list.length()).map { i ->
        val p = list.getJSONObject(i)
        Post(p.getLong("no"), p.optString("sub"), p.optString("com"), p.optString("name", "Anonymous"),
            p.optLong("time"), p.optInt("replies"), p.optInt("images"),
            p.optLong("tim").takeIf { it > 0 && p.optInt("filedeleted") != 1 },
            p.optString("ext"), p.optString("filename"), p.optInt("spoiler") == 1,
            p.optInt("w"), p.optInt("h"), p.optInt("archived") == 1,
            p.optLong("last_modified", p.optLong("time")))
    }
}
