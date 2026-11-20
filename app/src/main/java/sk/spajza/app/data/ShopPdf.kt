package sk.spajza.app.data

import android.content.Context
import android.net.Uri
import android.graphics.pdf.PdfDocument

/** PDF s nakupnym zoznamom (na vytlacenie alebo poslanie), zoskupeny podla obchodov. */
object ShopPdf {

    fun write(context: Context, uri: Uri, db: Db, items: List<ShopItem>): Result<Unit> = try {
        val doc = PdfDocument()
        val wr = EmergencyPdf.Writer(doc)
        wr.newPage()
        wr.line("Nákupný zoznam", wr.title, 8f)
        wr.line("Vytvorené " + Calc.date(Calc.today()) + " v aplikácii Špajza", wr.muted, 10f)

        val xs = listOf(40f, 64f, 300f, 380f, 485f)
        val none = "Bez obchodu"
        val groups = items.groupBy { it.store.ifBlank { none } }
            .toList()
            .sortedWith(compareBy({ it.first == none }, { it.first.lowercase() }))

        var grand = 0.0
        var saved = 0.0
        for ((store, list) in groups) {
            wr.space(6f)
            wr.line(store, wr.bold, 4f)
            wr.row(listOf("", "Položka", "Množstvo", "Cena / jedn.", "Spolu"), xs, wr.muted)
            var sub = 0.0
            val sorted = list.sortedWith(compareBy<ShopItem>({ it.checked }, { it.dealUntil ?: Long.MAX_VALUE }, { it.name.lowercase() }))
            for (s in sorted) {
                val cost = s.cost()
                sub += cost
                saved += Prices.saving(db, s)
                wr.row(
                    listOf(
                        if (s.checked) "[x]" else "[  ]", s.name, Calc.qty(s.qty, s.uom),
                        if (s.price > 0) Calc.money(s.price) + " / " + s.uom.label else "-",
                        if (s.price > 0) Calc.money(cost) else "-"
                    ), xs
                )
                val extra = ArrayList<String>()
                if (s.multiBuy) extra.add("kúp " + Calc.num(s.buyQty, 1) + ", zaplať " + Calc.num(s.payQty, 1))
                if (s.dealUntil != null) extra.add("akcia do " + Calc.date(s.dealUntil))
                if (s.regularPrice > s.price && s.price > 0) extra.add("bežne " + Calc.money(s.regularPrice))
                val sav = Prices.saving(db, s)
                if (sav > 0.005) extra.add("ušetríte " + Calc.money(sav))
                if (s.note.isNotBlank()) extra.add(s.note)
                if (extra.isNotEmpty()) wr.row(listOf("", extra.joinToString(" · ")), xs.take(2), wr.muted)
            }
            grand += sub
            wr.row(listOf("", "", "", "Spolu " + store, Calc.money(sub)), xs, wr.bold)
        }
        if (items.isEmpty()) wr.line("Zoznam je prázdny.")
        wr.space(12f)
        wr.line("Odhadovaná cena spolu: " + Calc.money(grand), wr.bold, 4f)
        if (saved > 0.005) wr.line("Ušetríte vďaka akciám: " + Calc.money(saved))
        wr.finish()

        val out = context.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("Súbor sa nepodarilo otvoriť")
        out.use { doc.writeTo(it) }
        doc.close()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
