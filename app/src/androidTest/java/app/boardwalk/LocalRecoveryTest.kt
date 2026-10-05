package app.boardwalk

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import app.boardwalk.data.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalRecoveryTest {
    @Test fun interruptedRestoreRecoversPreviousArchivesAndPreferencesOnNextStart() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(n: String, mode: Int) = base.getSharedPreferences("test-journal-$n", mode)
            override fun getFilesDir() = File(base.cacheDir, "test-journal").apply { mkdirs() }
        }
        for (promoted in listOf(false, true)) {
            context.filesDir.deleteRecursively()
            val prefs = context.getSharedPreferences("boardwalk", 0)
            prefs.edit().clear().commit()
            val store = LocalStore(context)
            store.theme = "Dark"; store.saved = listOf(SavedThread("news", 1, "Original"))
            val posts = ApiParser.thread("""{"posts":[{"no":1,"com":"Previous content"}]}""")
            store.saveOffline(OfflineThread("news", 1, "Original", 1234, posts))
            val snapshot = org.json.JSONObject()
                .put("theme", org.json.JSONObject().put("type", "string").put("value", "Dark"))
                .put("saved", org.json.JSONObject().put("type", "string").put("value", prefs.getString("saved", "[]")))
            File(context.filesDir, "restore-journal.json").writeText(snapshot.toString())
            assertTrue(File(context.filesDir, "saved-threads").renameTo(File(context.filesDir, "restore-previous")))
            if (promoted) File(context.filesDir, "saved-threads").mkdirs()
            prefs.edit().clear().putString("theme", "Light").commit()
            val recovered = LocalStore(context)
            assertEquals("Dark", recovered.theme)
            assertEquals(posts, recovered.offline("news", 1)!!.posts)
            assertEquals("Original", recovered.saved.single().title)
            assertFalse(File(context.filesDir, "restore-journal.json").exists())
        }
        context.filesDir.deleteRecursively()
        context.getSharedPreferences("boardwalk", 0).edit().clear().commit()
    }

    @Test fun exportedFileRestoresIntoFreshLocalStorage() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        fun context(name: String) = object : ContextWrapper(base) {
            override fun getSharedPreferences(n: String, mode: Int) = base.getSharedPreferences("test-$name-$n", mode)
            override fun getFilesDir() = File(base.cacheDir, "test-$name").apply { mkdirs() }
        }
        val sourceContext = context("source"); val targetContext = context("target")
        sourceContext.getSharedPreferences("boardwalk", 0).edit().clear().commit()
        targetContext.getSharedPreferences("boardwalk", 0).edit().clear().commit()
        sourceContext.filesDir.deleteRecursively(); targetContext.filesDir.deleteRecursively()
        val source = LocalStore(sourceContext)
        assertEquals(setOf("news", "biz"), source.favorites)
        source.favorites = emptySet(); source.theme = "Dark"; source.replyLayout = "Chronological"
        source.saved = listOf(SavedThread("news", 1, "Offline discussion"))
        source.rememberPosition("news", 1, ReadPosition(2, 45))
        source.saveProxy(ProxySettings(true, "proxy.example.com", "8080", "reader", "private-password"))
        val posts = ApiParser.thread("""{"posts":[{"no":1,"com":"Read me offline"},{"no":2,"com":"Reply text"}]}""")
        source.saveOffline(OfflineThread("news", 1, "Offline discussion", 1234, posts))
        val text = BackupCodec.encode(source.backup())
        assertFalse(text.contains("private-password")); assertFalse(text.contains("proxyCredentials"))
        val target = LocalStore(targetContext)
        target.restore(BackupCodec.decode(text))
        assertTrue(target.favorites.isEmpty()); assertEquals("Dark", target.theme)
        assertEquals("Chronological", target.replyLayout)
        assertEquals(ReadPosition(2, 45), target.position("news", 1))
        assertEquals(posts, target.offline("news", 1)!!.posts)
        val savedFile = File(targetContext.filesDir, "saved-threads/news-1.json")
        assertTrue(savedFile.renameTo(File(savedFile.path + ".bak")))
        assertTrue("news:1" in target.offlineKeys())
        assertEquals(posts, target.offline("news", 1)!!.posts)
        assertTrue(target.proxy().enabled); assertEquals("", target.proxy().password)
        target.removeOffline("news", 1); assertNull(target.offline("news", 1))
        sourceContext.filesDir.deleteRecursively(); targetContext.filesDir.deleteRecursively()
        sourceContext.getSharedPreferences("boardwalk", 0).edit().clear().commit()
        targetContext.getSharedPreferences("boardwalk", 0).edit().clear().commit()
    }
}
