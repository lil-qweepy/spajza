package sk.spajza.app.data

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Vyzivove hodnoty rozpoznane z fotky etikety (na 100 g / 100 ml). */
data class OcrNutrition(
    val kcal: Double?,
    val protein: Double?,
    val fat: Double?,
    val satFat: Double?,
    val carbs: Double?,
    val sugar: Double?,
    val fiber: Double?,
    val salt: Double?,
    val rawText: String
) {
    val any: Boolean
        get() = kcal != null || protein != null || fat != null || satFat != null ||
            carbs != null || sugar != null || fiber != null || salt != null
}

object Ocr {

    /** Precita text z obrazka a vyberie z neho vyzivove hodnoty. */
    suspend fun recognize(context: Context, uri: Uri): Result<OcrNutrition> {
        val rows = rows(context, uri)
        return rows.map { parse(it) }
    }

    /** Precita text z obrazka po riadkoch (texty v rovnakej vyske su spojene). */
    suspend fun rows(context: Context, uri: Uri): Result<List<String>> {
        return try {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val rows = suspendCancellableCoroutine<List<String>> { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { text ->
                        val lines = text.textBlocks.flatMap { it.lines }
                        cont.resume(buildRows(lines.map { Triple(it.text, it.boundingBox, 0) }))
                    }
                    .addOnFailureListener { cont.resume(emptyList()) }
            }
            try {
                recognizer.close()
            } catch (e: Exception) {
                // nic
            }
            Result.success(rows)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Zlozi riadky tabulky: texty v rovnakej vyske sa spoja zlava doprava (nazov a hodnota). */
    private fun buildRows(lines: List<Triple<String, android.graphics.Rect?, Int>>): List<String> {
        val withBox = lines.filter { it.second != null }
        if (withBox.isEmpty()) return lines.map { it.first }
        val sorted = withBox.sortedBy { it.second!!.centerY() }
        val rows = ArrayList<MutableList<Triple<String, android.graphics.Rect?, Int>>>()
        var rowCy = -1000000
        var rowH = 1
        for (l in sorted) {
            val r = l.second!!
            if (rows.isNotEmpty() && Math.abs(r.centerY() - rowCy) <= Math.max(rowH, r.height()) * 0.6) {
                rows.last().add(l)
            } else {
                rows.add(mutableListOf(l))
                rowCy = r.centerY()
                rowH = r.height()
            }
        }
        return rows.map { row -> row.sortedBy { it.second!!.left }.joinToString("  ") { it.first } }
    }

    private val NUM = Regex("([<]?)\\s*(\\d+(?:[.,]\\d+)?)\\s*(mg|g|kcal|kj)?")

    private fun after(row: String, keyIdx: Int, wantUnits: Set<String>): Double? {
        val tail = row.substring(keyIdx)
        for (m in NUM.findAll(tail)) {
            val unit = m.groupValues[3]
            if (unit == "kj" || unit == "kcal") continue
            if (wantUnits.isNotEmpty() && unit.isNotEmpty() && unit !in wantUnits) continue
            val v = m.groupValues[2].replace(',', '.').toDoubleOrNull() ?: continue
            return v
        }
        return null
    }

    fun parse(rawRows: List<String>): OcrNutrition {
        val rows = rawRows.map { Calc.norm(it) }
        var kcal: Double? = null
        var protein: Double? = null
        var fat: Double? = null
        var sat: Double? = null
        var carbs: Double? = null
        var sugar: Double? = null
        var fiber: Double? = null
        var salt: Double? = null

        val kcalRe = Regex("(\\d+(?:[.,]\\d+)?)\\s*kcal")
        val kjRe = Regex("(\\d+(?:[.,]\\d+)?)\\s*kj")
        for (r in rows) {
            if (kcal == null) {
                val k = kcalRe.find(r)
                if (k != null) kcal = k.groupValues[1].replace(',', '.').toDoubleOrNull()
            }
        }
        if (kcal == null) {
            for (r in rows) {
                if (kcal != null) break
                val kj = kjRe.find(r)
                if (kj != null && (r.contains("energi") || r.contains("energy"))) {
                    val v = kj.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (v != null) kcal = Math.round(v / 4.184).toDouble()
                }
            }
        }
        val g = setOf("g")
        for (r in rows) {
            // nasytene tuky a cukry su vedlajsie riadky, preto sa kontroluju prve
            val iSat = firstIndex(r, listOf("nasyten", "saturat"))
            if (iSat >= 0 && sat == null) sat = after(r, iSat, g)
            val iSug = firstIndex(r, listOf("cukr", "sugar"))
            if (iSug >= 0 && sugar == null) sugar = after(r, iSug, g)
            val iFib = firstIndex(r, listOf("vlaknin", "fibre", "fiber"))
            if (iFib >= 0 && fiber == null) fiber = after(r, iFib, g)
            val iPro = firstIndex(r, listOf("bielkovin", "protein"))
            if (iPro >= 0 && protein == null) protein = after(r, iPro, g)
            val iCar = firstIndex(r, listOf("sacharid", "carbohydr", "uhlohydr"))
            if (iCar >= 0 && carbs == null) carbs = after(r, iCar, g)
            val iSalt = firstIndex(r, listOf("sol ", "sol:", "salt"))
            if (iSalt >= 0 && salt == null) salt = after(r, iSalt, g)
            val iFat = firstIndex(r, listOf("tuky", "tuk ", "fat"))
            if (iFat >= 0 && (iSat < 0 || iFat < iSat) && fat == null) fat = after(r, iFat, g)
        }
        fun ok(v: Double?, max: Double): Double? = if (v != null && v >= 0 && v <= max) v else null
        return OcrNutrition(
            kcal = ok(kcal, 950.0), protein = ok(protein, 100.0), fat = ok(fat, 100.0), satFat = ok(sat, 100.0),
            carbs = ok(carbs, 100.0), sugar = ok(sugar, 100.0), fiber = ok(fiber, 100.0), salt = ok(salt, 100.0),
            rawText = rawRows.joinToString("\n")
        )
    }

    private fun firstIndex(row: String, keys: List<String>): Int {
        var best = -1
        for (k in keys) {
            val i = row.indexOf(k)
            if (i >= 0 && (best < 0 || i < best)) best = i
        }
        return best
    }
}
