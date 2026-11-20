package sk.spajza.app.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Zaloha: jeden ZIP subor (data.json + obrazky). Ziadny cloud, subor si ulozi pouzivatel. */
object Backup {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun export(context: Context, store: Store, uri: Uri): Result<Int> = try {
        var count = 0
        context.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(json.encodeToString(Db.serializer(), store.db.value).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                val files = store.imagesDir.listFiles()
                if (files != null) {
                    for (f in files) {
                        if (!f.isFile) continue
                        zip.putNextEntry(ZipEntry("images/" + f.name))
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        count++
                    }
                }
            }
        } ?: throw IllegalStateException("Súbor sa nepodarilo otvoriť")
        Result.success(count)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Import: nahradi vsetky aktualne data. */
    fun import(context: Context, store: Store, uri: Uri): Result<Int> = try {
        var text: String? = null
        val images = ArrayList<Pair<String, ByteArray>>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    val name = e.name
                    if (!e.isDirectory) {
                        if (name == "data.json") {
                            text = String(zip.readBytes(), Charsets.UTF_8)
                        } else if (name.startsWith("images/") && !name.contains("..")) {
                            images.add(File(name).name to zip.readBytes())
                        }
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("Súbor sa nepodarilo otvoriť")
        val t = text ?: throw IllegalStateException("V súbore chýba data.json – nie je to záloha Špajze")
        val db = json.decodeFromString(Db.serializer(), t)
        for ((n, bytes) in images) File(store.imagesDir, n).writeBytes(bytes)
        store.replaceAll(db)
        Result.success(db.items.size)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
