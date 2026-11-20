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

    private const val MAX_JSON = 30_000_000L
    private const val MAX_IMAGE = 12_000_000L
    private const val MAX_TOTAL = 250_000_000L
    private const val MAX_ENTRIES = 10_000

    /** Precita polozku ZIPu najviac [max] bajtov; pri vacsej vyhodi chybu (ochrana pred zip bombou). */
    private fun readLimited(zip: ZipInputStream, max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val tmp = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = zip.read(tmp)
            if (n < 0) break
            total += n
            if (total > max) throw IllegalStateException("Záloha obsahuje príliš veľký súbor")
            out.write(tmp, 0, n)
        }
        return out.toByteArray()
    }

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
                var entries = 0
                var total = 0L
                while (e != null) {
                    if (++entries > MAX_ENTRIES) throw IllegalStateException("Záloha obsahuje príliš veľa súborov")
                    val name = e.name
                    if (!e.isDirectory) {
                        if (name == "data.json") {
                            val b = readLimited(zip, MAX_JSON)
                            total += b.size
                            text = String(b, Charsets.UTF_8)
                        } else if (name.startsWith("images/") && !name.contains("..")) {
                            val n = File(name).name
                            if (isSafeImageName(n)) {
                                val b = readLimited(zip, MAX_IMAGE)
                                total += b.size
                                images.add(n to b)
                            }
                        }
                        if (total > MAX_TOTAL) throw IllegalStateException("Záloha je príliš veľká")
                    }
                    zip.closeEntry()
                    e = zip.nextEntry
                }
            }
        } ?: throw IllegalStateException("Súbor sa nepodarilo otvoriť")
        val t = text ?: throw IllegalStateException("V súbore chýba data.json – nie je to záloha Špajze")
        val parsed = json.decodeFromString(Db.serializer(), t)
        // Cesty k obrazkom zo zalohy sa neduveruju: povoleny je len bezny nazov suboru.
        val db = parsed.copy(items = parsed.items.map { if (it.image != null && !isSafeImageName(it.image)) it.copy(image = null) else it })
        for ((n, bytes) in images) File(store.imagesDir, n).writeBytes(bytes)
        store.replaceAll(db)
        Result.success(db.items.size)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
