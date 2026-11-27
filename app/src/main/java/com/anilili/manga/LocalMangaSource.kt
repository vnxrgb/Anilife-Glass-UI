package com.anilili.manga

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

/** Imports user-selected archives to private storage. No provider download or network access is required. */
internal class LocalMangaSource(context: Context) : MangaSource {
    private val app = context.applicationContext
    private val root = File(app.filesDir, "local_manga")
    override val id = ID
    override val displayName = "Local library"
    override val chapterLanguages = listOf("und")
    private fun folder(id: String): File {
        require(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)) { "Invalid local archive" }
        return File(root, id)
    }
    override suspend fun search(query: String, limit: Int): List<MangaTitle> = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { dir ->
            val title = File(dir, "title.txt").takeIf { it.isFile }?.readText() ?: return@mapNotNull null
            MangaTitle(ID, dir.name, title, "Imported local comic archive", null)
        }.filter { it.title.contains(query, true) }.take(limit)
    }
    override suspend fun catalog(query: String, kind: MangaKind, order: MangaOrder, offset: Int, limit: Int): List<MangaTitle> =
        withContext(Dispatchers.IO) {
            require(offset >= 0 && limit in 1..100)
            if (kind != MangaKind.ALL && kind != MangaKind.OTHER) return@withContext emptyList()
            val entries = search(query, Int.MAX_VALUE)
            val sorted = if (order == MangaOrder.RECENT) entries.sortedByDescending { File(folder(it.id), "title.txt").lastModified() } else entries.sortedBy { it.title.lowercase() }
            sorted.drop(offset).take(limit)
        }
    override suspend fun chapters(mangaId: String, language: String): List<MangaChapter> = withContext(Dispatchers.IO) {
        require(File(folder(mangaId), "title.txt").isFile) { "Local archive not found" }
        listOf(MangaChapter(ID, mangaId, mangaId, "Local comic", "1", "und"))
    }
    override suspend fun pages(chapterId: String): List<MangaPage> = withContext(Dispatchers.IO) {
        folder(chapterId).listFiles().orEmpty().filter { it.name.startsWith("page-") && it.isFile }.sortedBy { it.name }
            .mapIndexed { index, file -> MangaPage(index, Uri.fromFile(file).toString()) }
    }
    suspend fun importArchive(uri: Uri): MangaTitle = withContext(Dispatchers.IO) {
        val title = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }?.substringBeforeLast('.')?.take(160)?.ifBlank { "Local comic" } ?: "Local comic"
        val id = UUID.randomUUID().toString()
        val dir = folder(id)
        require(dir.mkdirs()) { "Could not create local storage" }
        val archive = File(dir, "import.zip")
        try {
            app.contentResolver.openInputStream(uri)?.use { input ->
                archive.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= MAX_ARCHIVE) { "Archive exceeds 512 MB" }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: error("Cannot read selected archive")
            ZipFile(archive).use { zip ->
                val entries = zip.entries().asSequence().filter { !it.isDirectory && !it.name.startsWith("__MACOSX/") && it.name.substringAfterLast('.').lowercase() in IMAGE_TYPES }
                    .take(2001).sortedWith(compareBy { naturalKey(it.name) }).toList()
                require(entries.isNotEmpty() && entries.size <= 2000) { "Archive must contain 1–2000 supported images" }
                var extracted = 0L
                entries.forEachIndexed { index, entry ->
                    val suffix = entry.name.substringAfterLast('.').lowercase()
                    val target = File(dir, "page-" + index.toString().padStart(5, '0') + "." + suffix)
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var pageSize = 0L
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                pageSize += n; extracted += n
                                require(pageSize <= MAX_PAGE && extracted <= MAX_ARCHIVE) { "Expanded archive exceeds storage limit" }
                                output.write(buffer, 0, n)
                            }
                        }
                    }
                    require(target.length() > 0) { "Empty image in archive" }
                }
            }
            File(dir, "title.txt").writeText(title)
            MangaTitle(ID, id, title, "Imported local comic archive", null)
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        } finally { archive.delete() }
    }
    companion object {
        const val ID = "local"
        private const val MAX_ARCHIVE = 512L * 1024 * 1024
        private const val MAX_PAGE = 32L * 1024 * 1024
        private val IMAGE_TYPES = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
        internal fun naturalKey(name: String): String = Regex("[0-9]+").replace(name.lowercase()) { it.value.padStart(20, '0') }
    }
}
