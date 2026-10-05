package app.boardwalk.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private fun backup(): BackupData {
        val prefs = JSONObject().put("theme", "Dark").put("allBoards", false).put("replyLayout", "Nested")
            .put("favorites", JSONArray()).put("saved", JSONArray("""[{"board":"news","id":1,"title":"A discussion"}]"""))
            .put("proxyEnabled", false).put("proxyHost", "").put("proxyPort", "8080").put("pos:news:1", 3)
        val posts = ApiParser.thread("""{"posts":[{"no":1,"com":"Text &amp; quote","tim":42,"ext":".png","spoiler":1,"time":123,"last_modified":456}]}""")
        return BackupData(prefs, listOf(OfflineThread("news", 1, "A discussion", 1234, posts)))
    }
    @Test fun roundTripPreservesOfflineTextMetadataAndEmptyFavorites() {
        val original = backup(); val decoded = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original.threads, decoded.threads)
        assertEquals(0, decoded.preferences.getJSONArray("favorites").length())
        assertEquals(3, decoded.preferences.getInt("pos:news:1"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnknownVersion() {
        val root = JSONObject(BackupCodec.encode(backup())).put("version", 2)
        BackupCodec.decode(root.toString())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsCredentialsInBackup() {
        val data = backup(); data.preferences.put("proxyCredentials", "secret")
        BackupCodec.decode(BackupCodec.encode(data))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsPathTraversal() {
        val root = JSONObject(BackupCodec.encode(backup()))
        root.getJSONArray("threads").getJSONObject(0).put("board", "../escape")
        BackupCodec.decode(root.toString())
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsUnbookmarkedOfflineThreads() {
        val data = backup(); data.preferences.put("saved", JSONArray())
        BackupCodec.decode(BackupCodec.encode(data))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsInvalidProxyInsteadOfCrashingAfterRestore() {
        val data = backup(); data.preferences.put("proxyEnabled", true).put("proxyHost", "")
        BackupCodec.decode(BackupCodec.encode(data))
    }
}
