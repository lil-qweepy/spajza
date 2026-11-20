package sk.spajza.app.data

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri

/** Vytvori PDF s prehladom nudzovych zasob a kontrolnym zoznamom (na vytlacenie). */
object EmergencyPdf {

    /** Kontrolny zoznam pre krizove situacie (tlaci sa do PDF). */
    private val checklist = listOf(
        "Pitná voda (2 l na osobu a deň)",
        "Trvanlivé potraviny a konzervy",
        "Otvárač konzerv",
        "Varič a palivo (používať len vonku)",
        "Baterky a náhradné batérie",
        "Powerbanka a nabíjačky",
        "Rádio na batérie alebo kľučku",
        "Lekárnička a osobné lieky",
        "Vlhčené utierky, toaletný papier, mydlo",
        "Odpadkové vrecia",
        "Zápalky alebo zapaľovač",
        "Spacie vrecia a deky",
        "Hotovosť v menších bankovkách",
        "Kópie dokladov a zoznam kontaktov",
        "Dezinfekčné tablety na vodu",
        "Multifunkčný nôž a píšťalka",
        "Detektor oxidu uhoľnatého",
        "Pohotovostný batoh"
    )

    private class Writer(val doc: PdfDocument) {
        val w = 595
        val h = 842
        val margin = 40f
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        val normal = Paint().apply { textSize = 11f; isAntiAlias = true; color = 0xFF222222.toInt() }
        val bold = Paint().apply { textSize = 13f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD; color = 0xFF111111.toInt() }
        val title = Paint().apply { textSize = 22f; isAntiAlias = true; typeface = Typeface.DEFAULT_BOLD; color = 0xFF111111.toInt() }
        val muted = Paint().apply { textSize = 10f; isAntiAlias = true; color = 0xFF666666.toInt() }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            y = margin + 10f
        }

        fun ensure(height: Float) {
            if (page == null || y + height > h - margin) newPage()
        }

        fun line(text: String, paint: Paint = normal, gap: Float = 4f) {
            val maxW = w - 2 * margin
            var rest = text
            while (true) {
                val n = paint.breakText(rest, true, maxW, null)
                val cut = if (n >= rest.length) rest.length else {
                    val sp = rest.lastIndexOf(' ', n)
                    if (sp > 0) sp else n
                }
                val part = rest.substring(0, cut)
                ensure(paint.textSize + gap)
                y += paint.textSize
                page?.canvas?.drawText(part, margin, y, paint)
                y += gap
                rest = rest.substring(cut).trimStart()
                if (rest.isEmpty()) break
            }
        }

        fun row(cols: List<String>, xs: List<Float>, paint: Paint = normal) {
            ensure(paint.textSize + 5f)
            y += paint.textSize
            for (i in cols.indices) {
                var t = cols[i]
                val maxW = (if (i + 1 < xs.size) xs[i + 1] else w - margin) - xs[i] - 6f
                while (t.length > 1 && paint.measureText(t) > maxW) t = t.dropLast(1)
                page?.canvas?.drawText(t, xs[i], y, paint)
            }
            y += 5f
        }

        fun space(v: Float) {
            y += v
        }

        fun finish() {
            page?.let { doc.finishPage(it) }
            page = null
        }
    }

    fun write(context: Context, uri: Uri, db: Db, r: EmergencyReport): Result<Unit> = try {
        val doc = PdfDocument()
        val wr = Writer(doc)
        wr.newPage()
        wr.line("Núdzové zásoby – prehľad", wr.title, 8f)
        wr.line("Vytvorené " + Calc.date(Calc.today()) + " v aplikácii Špajza", wr.muted, 10f)

        val s = db.settings
        wr.line("Domácnosť", wr.bold, 4f)
        wr.line("Počet osôb: " + r.persons + ", cieľ: " + r.targetDays + " dní")
        wr.line("Potreba na osobu a deň: " + Calc.num(s.kcalPerDay) + " kcal a " + Calc.num(s.waterPerDayL, 1) + " l vody", wr.normal, 10f)

        wr.line("Výsledok", wr.bold, 4f)
        wr.line("Zásoby vystačia na " + Calc.num(r.days, 1) + " dní (jedlo " + Calc.num(r.foodDays, 1) + " dní, voda " + Calc.num(r.waterDays, 1) + " dní).")
        wr.line("Spolu máte " + Calc.kcalText(r.kcalTotal) + " a " + Calc.waterText(r.waterMl) + " vody.")
        if (r.missingKcal > 0) wr.line("Do cieľa chýba ešte " + Calc.kcalText(r.missingKcal) + ".")
        if (r.missingWaterMl > 0) wr.line("Do cieľa chýba ešte " + Calc.waterText(r.missingWaterMl) + " vody.")
        wr.space(8f)

        wr.line("Započítané zásoby", wr.bold, 6f)
        val xs = listOf(40f, 300f, 400f, 490f)
        wr.row(listOf("Produkt", "Množstvo", "Energia", "Voda"), xs, wr.bold)
        val sorted = r.counted.sortedByDescending { it.kcal + it.waterMl / 10.0 }
        for (c in sorted) {
            wr.row(
                listOf(
                    c.item.name, Calc.qty(c.qty, c.item.uom),
                    if (c.kcal > 0) Calc.kcalText(c.kcal) else "-",
                    if (c.waterMl > 0) Calc.waterText(c.waterMl) else "-"
                ), xs
            )
        }
        if (sorted.isEmpty()) wr.line("Zatiaľ nemáte žiadne započítané zásoby.")
        wr.space(10f)

        wr.line("Kontrolný zoznam", wr.bold, 6f)
        for (c in checklist) wr.line((if (c in s.checked) "[x] " else "[  ] ") + c)
        wr.space(10f)

        wr.line("Dôležité čísla", wr.bold, 4f)
        wr.line("Jednotné európske číslo 112, záchranná služba 155, hasiči 150, polícia 158.")
        wr.finish()

        val out = context.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Súbor sa nepodarilo otvoriť")
        out.use { doc.writeTo(it) }
        doc.close()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
