package app.boardwalk.data

import org.json.JSONArray
import org.json.JSONObject

private fun validBoard(value: String) = value.matches(Regex("[a-z0-9]{1,12}"))
data class OfflineThread(val board: String, val id: Long, val title: String, val savedAt: Long, val posts: List<Post>) {
    val key get() = "$board:$id"
    fun json() = JSONObject().put("board", board).put("id", id).put("title", title).put("savedAt", savedAt)
        .put("thread", JSONObject(ApiParser.encodeThread(posts)))
    companion object {
        fun parse(o: JSONObject): OfflineThread {
            val board = o.getString("board"); val id = o.getLong("id")
            require(validBoard(board) && id > 0) { "Invalid saved thread" }
            val posts = ApiParser.thread(o.getJSONObject("thread").toString())
            require(posts.isNotEmpty() && posts.first().id == id && posts.all { it.id > 0 } && posts.distinctBy { it.id }.size == posts.size)
            return OfflineThread(board, id, o.getString("title").take(500), o.getLong("savedAt"), posts)
        }
    }
}

data class BackupData(val preferences: JSONObject, val threads: List<OfflineThread>)
object BackupCodec {
    const val MAX_BYTES = 64 * 1024 * 1024
    private val simple = setOf("theme", "allBoards", "replyLayout", "favorites", "saved", "proxyEnabled", "proxyHost", "proxyPort")
    private val position = Regex("^(pos|off):[a-z0-9]{1,12}:[1-9][0-9]*$")
    fun encode(data: BackupData): String = JSONObject().put("format", "boardwalk-backup").put("version", 1)
        .put("preferences", data.preferences).put("threads", JSONArray().apply { data.threads.forEach { put(it.json()) } }).toString()
    fun decode(text: String): BackupData {
        require(text.toByteArray().size <= MAX_BYTES) { "Backup exceeds 64 MB" }
        val root = JSONObject(text)
        require(root.getString("format") == "boardwalk-backup" && root.getInt("version") == 1) { "Unsupported backup format" }
        val prefs = root.getJSONObject("preferences")
        require(prefs.keys().asSequence().all { it in simple || position.matches(it) }) { "Unexpected backup settings" }
        require(prefs.getString("theme") in setOf("System", "Light", "Dark"))
        require(prefs.getString("replyLayout") in setOf("Nested", "Chronological"))
        prefs.getBoolean("allBoards"); prefs.getBoolean("proxyEnabled")
        require(prefs.getString("proxyHost").length <= 253 && prefs.getString("proxyPort").length <= 5)
        require(ProxySettings(prefs.getBoolean("proxyEnabled"), prefs.getString("proxyHost"), prefs.getString("proxyPort")).validationError() == null)
        val favorites = prefs.getJSONArray("favorites")
        require(favorites.length() <= 500)
        for (i in 0 until favorites.length()) require(validBoard(favorites.getString(i)))
        val bookmarks = prefs.getJSONArray("saved")
        require(bookmarks.length() <= 10000)
        val keys = mutableSetOf<String>()
        for (i in 0 until bookmarks.length()) {
            val item = bookmarks.getJSONObject(i); val board = item.getString("board"); val id = item.getLong("id")
            require(validBoard(board) && id > 0 && item.getString("title").length <= 500)
            require(keys.add("$board:$id"))
        }
        prefs.keys().asSequence().filter { position.matches(it) }.forEach { require(prefs.getInt(it) >= 0) }
        val array = root.getJSONArray("threads")
        require(array.length() <= bookmarks.length())
        val threads = (0 until array.length()).map { OfflineThread.parse(array.getJSONObject(it)) }
        require(threads.all { it.key in keys } && threads.distinctBy { it.key }.size == threads.size)
        return BackupData(prefs, threads)
    }
}
