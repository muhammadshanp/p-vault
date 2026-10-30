package com.dudeapp.vault

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale

enum class ItemType { IMAGE, PDF, TEXT }

data class VaultItem(
    val file: File,
    val title: String,
    val type: ItemType,
    /** Creation order, encoded in the filename prefix. */
    val stamp: Long,
)

data class Reel(
    val file: File,
    val title: String,
    val url: String,
    val category: String,
    val stamp: Long,
)

data class ReelCategory(val dir: File, val name: String)

data class ChatThread(
    val dir: File,
    val name: String,
    val lastMessage: String,
    val lastStamp: Long,
    val messageCount: Int,
    val avatar: File? = null,
)

data class ChatMessage(
    val file: File,
    val text: String,
    val stamp: Long,
)

/**
 * A folder of ordinary files plus the filename as metadata.
 * No database: `<stamp>_<title>.<ext>` is the whole schema.
 */
class VaultStore(private val context: Context) {

    private val dir = File(context.filesDir, "vault").apply { mkdirs() }
    private val reelsRoot = File(context.filesDir, "reels").apply { mkdirs() }
    private val chatsRoot = File(context.filesDir, "chats").apply { mkdirs() }
    private val chatPrefs = context.getSharedPreferences("dude_chats", Context.MODE_PRIVATE)

    init {
        ensureDefaultCategories()
        ensurePendingCategory()
        ensureDefaultChats()
    }

    fun list(): List<VaultItem> =
        dir.listFiles()
            ?.mapNotNull { file ->
                if (!file.isFile) return@mapNotNull null
                val type = typeOf(file.name) ?: return@mapNotNull null
                VaultItem(file, titleOf(file.name), type, stampOf(file.name))
            }
            ?.sortedByDescending { it.stamp }
            .orEmpty()

    /** Copies a picked document into the vault. Returns null if it is not an image or PDF. */
    fun addFile(uri: Uri, fallbackName: String): VaultItem? {
        val raw = displayName(uri) ?: fallbackName
        val ext = raw.substringAfterLast('.', "").lowercase()
            .ifEmpty { fallbackName.substringAfterLast('.', "").lowercase() }
        if (ext !in IMAGE_EXT && ext != "pdf") return null
        val base = raw.substringBeforeLast('.', raw).ifBlank { fallbackName.substringBeforeLast('.') }

        val target = newFile(base, ext)
        val copied = runCatching {
            val input = context.contentResolver.openInputStream(uri) ?: return@runCatching false
            input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
            true
        }.getOrDefault(false)

        if (!copied || target.length() == 0L) {
            target.delete()
            return null
        }
        val type = typeOf(target.name)
        if (type == null) {
            target.delete()
            return null
        }
        return VaultItem(target, titleOf(target.name), type, stampOf(target.name))
    }

    fun addText(title: String, body: String): VaultItem {
        val cleanTitle = title.ifBlank {
            body.lineSequence().firstOrNull()?.take(40)?.ifBlank { null } ?: "Note"
        }
        val target = newFile(cleanTitle, "txt")
        target.writeText(body)
        return VaultItem(target, titleOf(target.name), ItemType.TEXT, stampOf(target.name))
    }

    fun readText(item: VaultItem): String = item.file.readText()

    fun saveText(item: VaultItem, text: String) = item.file.writeText(text)

    fun rename(item: VaultItem, newTitle: String): VaultItem? {
        val target = File(dir, "${stampOf(item.file.name)}_${sanitize(newTitle)}.${item.file.extension}")
        if (target == item.file) return item
        if (target.exists()) return null
        if (!item.file.renameTo(target)) return null
        return VaultItem(target, titleOf(target.name), item.type, stampOf(target.name))
    }

    fun delete(item: VaultItem) {
        item.file.delete()
    }

    /** Removes all user content and restores the first-run folders, without touching the PIN. */
    fun clearAll(): Boolean = runCatching {
        dir.deleteRecursively()
        reelsRoot.deleteRecursively()
        chatsRoot.deleteRecursively()
        dir.mkdirs()
        reelsRoot.mkdirs()
        chatsRoot.mkdirs()
        chatPrefs.edit().remove("seeded").apply()
        ensureDefaultCategories()
        ensurePendingCategory()
        ensureDefaultChats()
    }.isSuccess

    // ---- Reels ----------------------------------------------------------

    fun categoryNames(): List<String> = categories().map { it.name }

    /** Shared reels are staged here until the user files them. */
    fun pendingCategoryName(): String? =
        categories().firstOrNull { it.name.equals(PENDING_NAME, ignoreCase = true) }?.name

    fun categories(): List<ReelCategory> =
        reelsRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name }
            ?.map { ReelCategory(it, it.name.substringAfter('_')) }
            .orEmpty()

    fun addCategory(name: String): String? {
        val clean = sanitize(name).take(30).trim()
        if (clean.isBlank()) return null
        if (categories().any { it.name.equals(clean, ignoreCase = true) }) return null
        val next = (categories().mapNotNull { it.dir.name.substringBefore('_').toIntOrNull() }.maxOrNull() ?: 0) + 1
        val created = File(reelsRoot, numberedFolder(next, clean)).mkdirs()
        return if (created) clean else null
    }

    fun renameCategory(oldName: String, newName: String): Boolean {
        if (oldName.equals(PENDING_NAME, ignoreCase = true)) return false
        val clean = sanitize(newName).take(30).trim()
        if (clean.isBlank()) return false
        if (clean.equals(PENDING_NAME, ignoreCase = true)) return false
        val current = categories().firstOrNull { it.name == oldName } ?: return false
        if (current.name == clean) return true
        if (categories().any { it.name != current.name && it.name.equals(clean, ignoreCase = true) }) return false
        val prefix = current.dir.name.substringBefore('_')
        return current.dir.renameTo(File(reelsRoot, "${prefix}_$clean"))
    }

    fun listReels(): List<Reel> =
        categories()
            .flatMap { category ->
                category.dir.listFiles().orEmpty().mapNotNull { file ->
                    if (!file.isFile || file.extension.lowercase() != "reel") return@mapNotNull null
                    val url = runCatching { file.readText().trim() }.getOrDefault("")
                    if (url.isBlank()) return@mapNotNull null
                    Reel(
                        file = file,
                        title = titleOf(file.name),
                        url = url,
                        category = category.name,
                        stamp = stampOf(file.name),
                    )
                }
            }
            .sortedByDescending { it.stamp }

    fun addReel(url: String, title: String, category: String): Reel? {
        val targetDir = categoryDir(category) ?: return null
        val cleanTitle = title.ifBlank { "Instagram Reel" }
        var stamp = System.currentTimeMillis()
        var file = File(targetDir, "${stamp}_${sanitize(cleanTitle)}.reel")
        while (file.exists()) {
            stamp += 1
            file = File(targetDir, "${stamp}_${sanitize(cleanTitle)}.reel")
        }
        file.writeText(url.trim())
        return Reel(
            file = file,
            title = titleOf(file.name),
            url = url.trim(),
            category = targetDir.name.substringAfter('_'),
            stamp = stampOf(file.name),
        )
    }

    fun moveReel(reel: Reel, category: String): Boolean {
        val targetDir = categoryDir(category) ?: return false
        if (targetDir.name == reel.file.parentFile?.name) return true
        return reel.file.renameTo(File(targetDir, reel.file.name))
    }

    fun deleteReel(reel: Reel) {
        reel.file.delete()
    }

    // ---- Chats ----------------------------------------------------------

    fun listChats(): List<ChatThread> =
        chatsRoot.listFiles()
            ?.filter { it.isDirectory }
            ?.map { folder ->
                val messages = folder.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase() == "txt" }
                    .orEmpty()
                val last = messages.maxByOrNull { chatStampOf(it.name) }
                ChatThread(
                    dir = folder,
                    name = folder.name.substringAfter('_'),
                    lastMessage = last?.let { file ->
                        runCatching { file.readText().replace(Regex("\\s+"), " ").trim() }.getOrDefault("")
                    }.orEmpty(),
                    lastStamp = last?.let { chatStampOf(it.name) } ?: 0L,
                    messageCount = messages.size,
                    avatar = chatAvatarOf(folder),
                )
            }
            ?.sortedWith(compareByDescending<ChatThread> { it.lastStamp }.thenBy { it.name.lowercase() })
            .orEmpty()

    fun renameChat(chat: ChatThread, newName: String): Boolean {
        val clean = sanitize(newName).take(30).trim()
        if (clean.isBlank()) return false
        val current = chatsRoot.listFiles()
            ?.firstOrNull { it.isDirectory && it.name == chat.dir.name }
            ?: return false
        if (current.name.substringAfter('_') == clean) return true
        val duplicate = chatsRoot.listFiles()?.any {
            it.isDirectory && it.name != current.name &&
                it.name.substringAfter('_').equals(clean, ignoreCase = true)
        } == true
        if (duplicate) return false
        val prefix = current.name.substringBefore('_')
        val target = File(chatsRoot, "${prefix}_$clean")
        if (target.exists() && !target.absolutePath.equals(current.absolutePath, ignoreCase = true)) {
            return false
        }
        if (current.renameTo(target)) return true
        val temp = File(chatsRoot, "${prefix}_tmp_${System.nanoTime()}")
        if (!current.renameTo(temp)) return false
        if (temp.renameTo(target)) return true
        temp.renameTo(current)
        return false
    }

    fun deleteChat(chat: ChatThread): Boolean =
        chat.dir.deleteRecursively()

    fun addChat(name: String): String? {
        val clean = sanitize(name).take(30).trim()
        if (clean.isBlank()) return null
        val exists = chatsRoot.listFiles()?.any {
            it.isDirectory && it.name.substringAfter('_').equals(clean, ignoreCase = true)
        } == true
        if (exists) return null
        val next = (chatsRoot.listFiles()?.mapNotNull { it.name.substringBefore('_').toIntOrNull() }?.maxOrNull() ?: 0) + 1
        val created = File(chatsRoot, numberedFolder(next, clean)).mkdirs()
        return if (created) clean else null
    }

    fun setChatAvatar(chat: ChatThread, uri: Uri): File? {
        val folder = chatsRoot.listFiles()?.firstOrNull {
            it.isDirectory && it.name == chat.dir.name
        } ?: return null
        val temp = File(folder, "avatar.tmp")
        var target: File? = null
        return runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@runCatching null
            input.use { source ->
                temp.outputStream().use { sink -> source.copyTo(sink) }
            }
            if (temp.length() == 0L) {
                temp.delete()
                return@runCatching null
            }
            var dest = File(folder, "avatar_${System.currentTimeMillis()}")
            while (dest.exists()) dest = File(folder, "avatar_${System.nanoTime()}")
            target = dest
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
            folder.listFiles()
                ?.filter {
                    it.isFile && it.name.startsWith("avatar_") &&
                        it.absolutePath != dest.absolutePath
                }
                ?.forEach { it.delete() }
            dest.takeIf { it.length() > 0 }
        }.onFailure {
            temp.delete()
            target?.delete()
        }.getOrNull()
    }

    fun removeChatAvatar(chat: ChatThread): Boolean =
        chat.dir.listFiles()
            ?.filter {
                it.isFile && (it.name.startsWith("avatar_") || it.name == "avatar.tmp")
            }
            ?.all { it.delete() }
            ?: false

    fun chatMessages(chat: String): List<ChatMessage> {
        val folder = chatDir(chat) ?: return emptyList()
        return folder.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() == "txt" }
            ?.mapNotNull { file ->
                val text = runCatching { file.readText().trim() }.getOrNull()
                if (text.isNullOrBlank()) return@mapNotNull null
                ChatMessage(file, text, chatStampOf(file.name))
            }
            ?.sortedBy { it.stamp }
            .orEmpty()
    }

    fun addMessage(chat: String, text: String): ChatMessage? {
        val body = text.trim()
        if (body.isEmpty()) return null
        val folder = chatDir(chat) ?: return null
        var stamp = System.currentTimeMillis()
        var file = File(folder, "$stamp.txt")
        while (file.exists()) {
            stamp += 1
            file = File(folder, "$stamp.txt")
        }
        return runCatching {
            file.writeText(body)
            ChatMessage(file, body, stamp)
        }.onFailure { file.delete() }.getOrNull()
    }

    fun editMessage(message: ChatMessage, text: String): ChatMessage? {
        val body = text.trim()
        if (body.isEmpty() || !message.file.exists()) return null
        val temp = File(message.file.parentFile, "${message.file.name}.tmp")
        return runCatching {
            temp.writeText(body)
            if (!temp.renameTo(message.file)) {
                temp.copyTo(message.file, overwrite = true)
                temp.delete()
            }
            ChatMessage(message.file, body, message.stamp)
        }.onFailure { temp.delete() }.getOrNull()
    }

    fun deleteMessage(message: ChatMessage): Boolean =
        runCatching { message.file.delete() }.getOrDefault(false)

    private fun ensureDefaultChats() {
        if (chatPrefs.getBoolean("seeded", false)) return
        if (chatsRoot.listFiles()?.any { it.isDirectory } != true) {
            listOf("Anime", "Tech", "AI", "Movies", "College").forEachIndexed { index, name ->
                File(chatsRoot, numberedFolder(index + 1, name)).mkdirs()
            }
        }
        chatPrefs.edit().putBoolean("seeded", true).apply()
    }

    private fun chatDir(name: String): File? =
        chatsRoot.listFiles()?.firstOrNull { it.isDirectory && it.name.substringAfter('_') == name }

    private fun chatAvatarOf(folder: File): File? =
        folder.listFiles()
            ?.filter { it.isFile && it.name.startsWith("avatar_") }
            ?.maxByOrNull { it.lastModified() }
            ?.takeIf { it.length() > 0 }

    private fun chatStampOf(name: String): Long =
        name.substringBefore('.').toLongOrNull() ?: 0L

    private fun ensureDefaultCategories() {
        if (reelsRoot.listFiles()?.any { it.isDirectory } == true) return
        listOf("Tech", "AI", "Anime", "Others").forEachIndexed { index, name ->
            File(reelsRoot, numberedFolder(index + 1, name)).mkdirs()
        }
    }

    /** Creates the reserved staging folder and moves an older Pending folder to the front. */
    private fun ensurePendingCategory() {
        val existing = categories().firstOrNull { it.name.equals(PENDING_NAME, ignoreCase = true) }
        if (existing != null) {
            if (existing.dir.name != PENDING_FOLDER) {
                val target = File(reelsRoot, PENDING_FOLDER)
                if (!target.exists()) existing.dir.renameTo(target)
            }
            return
        }
        File(reelsRoot, PENDING_FOLDER).mkdirs()
    }

    private fun numberedFolder(index: Int, name: String): String =
        "%02d_%s".format(Locale.US, index, name)

    private fun categoryDir(name: String): File? =
        categories().firstOrNull { it.name == name }?.dir ?: categories().firstOrNull()?.dir

    private fun newFile(title: String, ext: String): File {
        var stamp = System.currentTimeMillis()
        var file = File(dir, "${stamp}_${sanitize(title)}.$ext")
        while (file.exists()) {
            stamp += 1
            file = File(dir, "${stamp}_${sanitize(title)}.$ext")
        }
        return file
    }

    private fun typeOf(name: String): ItemType? = when (name.substringAfterLast('.', "").lowercase()) {
        in IMAGE_EXT -> ItemType.IMAGE
        "pdf" -> ItemType.PDF
        "txt" -> ItemType.TEXT
        else -> null
    }

    private fun titleOf(name: String): String =
        name.substringAfter('_').substringBeforeLast('.').ifBlank { "Untitled" }

    private fun stampOf(name: String): Long = name.substringBefore('_').toLongOrNull() ?: 0L

    private fun sanitize(raw: String): String =
        raw.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ")
            .trim()
            .take(60)
            .ifBlank { "Untitled" }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    companion object {
        private const val PENDING_NAME = "Pending"
        private const val PENDING_FOLDER = "00_Pending"
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
    }
}
