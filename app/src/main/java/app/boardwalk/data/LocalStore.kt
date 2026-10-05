package app.boardwalk.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LocalStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("boardwalk", Context.MODE_PRIVATE)
    private val archiveDir get() = java.io.File(context.filesDir, "saved-threads").apply { mkdirs() }
    private fun archiveFile(board: String, id: Long): java.io.File {
        require(board.matches(Regex("[a-z0-9]{1,12}")) && id > 0)
        return java.io.File(archiveDir, "$board-$id.json")
    }
    @Synchronized fun offline(board: String, id: Long): OfflineThread? = runCatching {
        android.util.AtomicFile(archiveFile(board, id)).openRead().use { OfflineThread.parse(JSONObject(it.readBytes().toString(Charsets.UTF_8))) }
    }.getOrNull()
    @Synchronized fun saveOffline(thread: OfflineThread) {
        val file = android.util.AtomicFile(archiveFile(thread.board, thread.id))
        val out = file.startWrite()
        try { out.write(thread.json().toString().toByteArray()); file.finishWrite(out) }
        catch (e: Exception) { file.failWrite(out); throw e }
        prefs.edit().putLong("archiveRevision", System.nanoTime()).apply()
    }
    @Synchronized fun removeOffline(board: String, id: Long) {
        android.util.AtomicFile(archiveFile(board, id)).delete()
        prefs.edit().putLong("archiveRevision", System.nanoTime()).apply()
    }
    @Synchronized fun offlineKeys(): Set<String> = saved.filter { archiveFile(it.board, it.id).let { file -> file.exists() || java.io.File(file.path + ".bak").exists() } }.map { "${it.board}:${it.id}" }.toSet()
    var backupUri: String?
        get() = prefs.getString("backupUri", null)
        set(value) { prefs.edit().putString("backupUri", value).apply() }
    fun observe(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(listener)
    fun unobserve(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(listener)
    @Synchronized fun backup(): BackupData {
        val p = JSONObject().put("theme", theme).put("allBoards", showAll).put("replyLayout", replyLayout)
            .put("favorites", JSONArray(favorites.sorted())).put("saved", JSONArray(prefs.getString("saved", "[]")))
            .put("proxyEnabled", prefs.getBoolean("proxyEnabled", false)).put("proxyHost", prefs.getString("proxyHost", ""))
            .put("proxyPort", prefs.getString("proxyPort", "8080"))
        prefs.all.filterKeys { it.startsWith("pos:") || it.startsWith("off:") }.forEach { (key, value) -> p.put(key, value) }
        return BackupData(p, saved.mapNotNull { offline(it.board, it.id) })
    }
    private val journal get() = android.util.AtomicFile(java.io.File(context.filesDir, "restore-journal.json"))
    init { recoverRestore() }
    private fun preferencesJson(): JSONObject = JSONObject().apply {
        prefs.all.forEach { (key, value) ->
            val type = when (value) { is Boolean -> "boolean"; is Int -> "int"; is Long -> "long"; is Float -> "float"; is Set<*> -> "set"; else -> "string" }
            put(key, JSONObject().put("type", type).put("value", if (value is Set<*>) JSONArray(value.toList()) else value))
        }
    }
    private fun restorePreferences(o: JSONObject) {
        val edit = prefs.edit().clear()
        o.keys().forEach { key ->
            val item = o.getJSONObject(key)
            when (item.getString("type")) {
                "boolean" -> edit.putBoolean(key, item.getBoolean("value"))
                "int" -> edit.putInt(key, item.getInt("value"))
                "long" -> edit.putLong(key, item.getLong("value"))
                "float" -> edit.putFloat(key, item.getDouble("value").toFloat())
                "set" -> { val a = item.getJSONArray("value"); edit.putStringSet(key, (0 until a.length()).map { a.getString(it) }.toSet()) }
                else -> edit.putString(key, item.getString("value"))
            }
        }
        check(edit.commit()) { "Could not recover previous settings" }
    }
    @Synchronized private fun recoverRestore() {
        val marker = java.io.File(context.filesDir, "restore-journal.json")
        if (!marker.exists() && !java.io.File(context.filesDir, "restore-journal.json.bak").exists()) return
        val prior = journal.openRead().use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        val previous = java.io.File(context.filesDir, "restore-previous")
        val current = java.io.File(context.filesDir, "saved-threads")
        if (previous.exists()) {
            current.deleteRecursively()
            check(previous.renameTo(current)) { "Could not recover previous discussions" }
        }
        restorePreferences(prior)
        journal.delete()
        java.io.File(context.filesDir, "restore-staging").deleteRecursively()
    }
    @Synchronized fun restore(data: BackupData) {
        recoverRestore()
        val staging = java.io.File(context.filesDir, "restore-staging").apply { deleteRecursively(); check(mkdirs()) }
        data.threads.forEach { java.io.File(staging, "${it.board}-${it.id}.json").writeText(it.json().toString()) }
        val current = archiveDir
        val previous = java.io.File(context.filesDir, "restore-previous").apply { deleteRecursively() }
        val output = journal.startWrite()
        try { output.write(preferencesJson().toString().toByteArray()); journal.finishWrite(output) }
        catch (e: Exception) { journal.failWrite(output); throw e }
        try {
            check(current.renameTo(previous))
            check(staging.renameTo(current))
            val p = data.preferences
            val editor = prefs.edit().clear()
                .putString("theme", p.getString("theme")).putBoolean("allBoards", p.getBoolean("allBoards"))
                .putString("replyLayout", p.getString("replyLayout"))
                .putString("saved", p.getJSONArray("saved").toString())
                .putBoolean("proxyEnabled", p.getBoolean("proxyEnabled")).putString("proxyHost", p.getString("proxyHost"))
                .putString("proxyPort", p.getString("proxyPort"))
            val favorites = p.getJSONArray("favorites")
            editor.putStringSet("favorites", (0 until favorites.length()).map { favorites.getString(it) }.toSet())
            p.keys().asSequence().filter { it.startsWith("pos:") || it.startsWith("off:") }.forEach { editor.putInt(it, p.getInt(it)) }
            check(editor.commit()) { "Could not restore preferences" }
            journal.delete()
        } catch (e: Exception) { recoverRestore(); throw e }
        previous.deleteRecursively()
    }
    fun rotateBackupFile(created: String): String? {
        val obsolete = prefs.getString("backupPreviousFile", null)
        check(prefs.edit().putString("backupPreviousFile", prefs.getString("backupLastFile", null))
            .putString("backupLastFile", created).commit())
        return obsolete
    }
    var replyLayout: String
        get() = prefs.getString("replyLayout", "Nested") ?: "Nested"
        set(value) { prefs.edit().putString("replyLayout", value).apply() }
    var theme: String
        get() = prefs.getString("theme", "System") ?: "System"
        set(value) { prefs.edit().putString("theme", value).apply() }
    var showAll: Boolean
        get() = prefs.getBoolean("allBoards", false)
        set(value) { prefs.edit().putBoolean("allBoards", value).apply() }
    var favorites: Set<String>
        get() = prefs.getStringSet("favorites", setOf("news", "biz"))!!.toSet()
        set(value) { prefs.edit().putStringSet("favorites", value).apply() }
    var saved: List<SavedThread>
        get() = runCatching {
            val a = JSONArray(prefs.getString("saved", "[]"))
            (0 until a.length()).map { a.getJSONObject(it).let { x ->
                SavedThread(x.getString("board"), x.getLong("id"), x.getString("title"))
            } }
        }.getOrDefault(emptyList())
        set(value) {
            val a = JSONArray()
            value.forEach { a.put(JSONObject().put("board", it.board).put("id", it.id).put("title", it.title)) }
            prefs.edit().putString("saved", a.toString()).apply()
        }
    fun position(board: String, id: Long) = ReadPosition(
        prefs.getInt("pos:$board:$id", 0), prefs.getInt("off:$board:$id", 0))
    fun rememberPosition(board: String, id: Long, position: ReadPosition) {
        prefs.edit().putInt("pos:$board:$id", position.index).putInt("off:$board:$id", position.offset).apply()
    }
    fun proxy(): ProxySettings {
        val enabled = prefs.getBoolean("proxyEnabled", false)
        // If decryption fails, preserve enabled mode. Never silently revert to direct access.
        val credentials = runCatching { decrypt(prefs.getString("proxyCredentials", "")!!) }.getOrDefault(JSONObject())
        return ProxySettings(enabled, prefs.getString("proxyHost", "")!!, prefs.getString("proxyPort", "8080")!!,
            credentials.optString("username"), credentials.optString("password"))
    }
    fun saveProxy(settings: ProxySettings) {
        val encrypted = encrypt(JSONObject().put("username", settings.username).put("password", settings.password).toString())
        check(prefs.edit().putBoolean("proxyEnabled", settings.enabled).putString("proxyHost", settings.host)
            .putString("proxyPort", settings.port).putString("proxyCredentials", encrypted).commit()) {
            "Could not save connection settings."
        }
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("boardwalk.proxy", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("boardwalk.proxy", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(value: String): JSONObject {
        if (value.isEmpty()) return JSONObject()
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
    }
}
