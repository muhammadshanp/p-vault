package com.dudeapp.vault

import java.io.File
import java.util.Locale

/**
 * Adds an unpacked backup to the files already on the phone.
 *
 * Nothing that is already there is deleted or overwritten:
 *  - a file the phone already has (same name and content) is skipped,
 *  - a vault item or reel with the same name but different content is added as a second copy,
 *  - reel categories and chats are matched by name, so "Work" in the backup lands in the
 *    existing "Work" instead of creating a second one,
 *  - inside a chat, a message the phone already has keeps the phone's version,
 *  - the profile picture is only taken from the backup when the phone has none.
 */
internal object BackupMerge {
    /** Returns how many files were added. */
    fun merge(staging: File, target: File): Int {
        var added = 0
        added += mergeItems(File(staging, "vault"), File(target, "vault").apply { mkdirs() })
        added += mergeReels(File(staging, "reels"), File(target, "reels").apply { mkdirs() })
        added += mergeChats(File(staging, "chats"), File(target, "chats").apply { mkdirs() })

        val avatar = File(staging, "profile_avatar")
        val localAvatar = File(target, "profile_avatar")
        if (avatar.isFile && avatar.length() > 0 && !(localAvatar.isFile && localAvatar.length() > 0)) {
            avatar.copyTo(localAvatar, overwrite = true)
        }
        return added
    }

    /** Files named `<stamp>_<title>.<ext>`; a clash with different content keeps both. */
    private fun mergeItems(from: File, into: File, alreadyHave: (File) -> Boolean = { false }): Int {
        var added = 0
        from.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach { incoming ->
            if (alreadyHave(incoming)) return@forEach
            val rest = incoming.name.substringAfter('_', "")
            // The same item may already be here under another stamp (an earlier restore).
            val duplicate = into.listFiles().orEmpty().any { local ->
                local.isFile &&
                    (local.name == incoming.name || (rest.isNotEmpty() && local.name.substringAfter('_', "") == rest)) &&
                    sameContent(local, incoming)
            }
            if (duplicate) return@forEach
            incoming.copyTo(freeName(into, incoming.name))
            added += 1
        }
        return added
    }

    private fun mergeReels(from: File, into: File): Int {
        var added = 0
        // A reel that was moved to another category on the phone is still the same reel.
        val alreadyHave: (File) -> Boolean = { incoming ->
            into.listFiles().orEmpty().any { category ->
                val local = File(category, incoming.name)
                category.isDirectory && local.isFile && sameContent(local, incoming)
            }
        }
        from.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.forEach { folder ->
            added += mergeItems(folder, matchingFolder(into, folder.name), alreadyHave)
        }
        return added
    }

    private fun mergeChats(from: File, into: File): Int {
        var added = 0
        from.listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }.forEach { folder ->
            val local = matchingFolder(into, folder.name)
            val hasAvatar = local.listFiles().orEmpty().any { it.isFile && it.name.startsWith("avatar_") }
            folder.listFiles().orEmpty().filter { it.isFile }.forEach { incoming ->
                if (incoming.name.endsWith(".tmp")) return@forEach
                if (incoming.name.startsWith("avatar_") && hasAvatar) return@forEach
                val target = File(local, incoming.name)
                // Message files point at their attachments by name, so names are kept as
                // they are and the phone's copy wins when both sides have the same one.
                if (target.exists()) return@forEach
                incoming.copyTo(target)
                added += 1
            }
        }
        return added
    }

    /** The folder `<nn>_<name>` with the same name (ignoring case), created if missing. */
    private fun matchingFolder(root: File, folderName: String): File {
        val name = folderName.substringAfter('_')
        val folders = root.listFiles().orEmpty().filter { it.isDirectory }
        folders.firstOrNull { it.name.substringAfter('_').equals(name, ignoreCase = true) }?.let { return it }
        val next = (folders.mapNotNull { it.name.substringBefore('_').toIntOrNull() }.maxOrNull() ?: 0) + 1
        return File(root, "%02d_%s".format(Locale.US, next, name)).apply { mkdirs() }
    }

    /** [name] if unused, otherwise the same name with the leading stamp raised until free. */
    private fun freeName(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val rest = name.substringAfter('_', "")
        var stamp = name.substringBefore('_').toLongOrNull()
        if (stamp == null || rest.isEmpty()) {
            var n = 2
            do {
                file = File(dir, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.', "")}")
                n += 1
            } while (file.exists())
            return file
        }
        while (file.exists()) {
            stamp += 1
            file = File(dir, "${stamp}_$rest")
        }
        return file
    }

    private fun sameContent(a: File, b: File): Boolean {
        if (a.length() != b.length()) return false
        a.inputStream().buffered().use { first ->
            b.inputStream().buffered().use { second ->
                while (true) {
                    val x = first.read()
                    if (x != second.read()) return false
                    if (x < 0) return true
                }
            }
        }
    }
}
