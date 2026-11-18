package sk.spajza.app.data

/** Polozka rozpoznana z blocku: nazov, pocet a cena za jeden kus. */
data class ReceiptLine(val name: String, val qty: Double, val unitPrice: Double)

object Receipt {
    private val SKIP = listOf(
        "spolu", "celkom", "suma", "dph", "hotovost", "karta", "platba", "vrate", "zlava", "dic", "ico",
        "pokladn", "blok", "datum", "zaokruh", "dakujeme", "bonus", "kupon", "body", "preplatok", "telefon",
        "www", "ulica", "predavajuc", "zakaznik", "rekapit", "k platbe", "spat", "doklad", "uctenk", "eurach", "obchod"
    )
    private val EXACT = setOf("karta", "dic", "ico", "body", "blok", "suma", "spat", "dph", "www")
    private val SKIP_RE: List<Regex> = SKIP.map { w ->
        if (w in EXACT) Regex("\\b" + w + "\\b") else Regex("\\b" + w)
    }
    private val QTY = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:ks)?\\s*[x×*]\\s*(\\d+[.,]\\d{2})")
    private val PRICE_END = Regex("(\\d{1,4}[.,]\\d{2})\\s*(?:eur|€)?\\s*[A-Za-z]?\\s*$", RegexOption.IGNORE_CASE)
    private val PRICE_ANY = Regex("\\d+[.,]\\d{2}")

    private fun price(s: String): Double? = s.replace(',', '.').toDoubleOrNull()

    private fun cleanName(raw: String): String {
        var t = QTY.replace(raw, " ")
        t = PRICE_ANY.replace(t, " ")
        t = t.replace("€", " ").replace(Regex("^\\s*\\d{3,}\\s+"), " ")
        t = t.replace(Regex("\\s+[A-Za-z]\\s*$"), " ")
        t = t.replace(Regex("\\s+"), " ").trim().trim('.', ',', '-', '*', ':')
        if (t.isNotEmpty() && t == t.uppercase()) t = t.lowercase().replaceFirstChar { it.uppercase() }
        return t
    }

    fun parse(rows: List<String>): List<ReceiptLine> {
        val out = ArrayList<ReceiptLine>()
        for (raw in rows) {
            val r = raw.trim()
            if (r.length < 3) continue
            val n = Calc.norm(r)
            if (SKIP_RE.any { it.containsMatchIn(n) }) continue
            val qm = QTY.find(r)
            val pe = PRICE_END.find(r)
            val name = cleanName(r)
            val letters = name.count { it.isLetter() }
            if (letters < 2 && qm != null && out.isNotEmpty()) {
                val last = out.removeAt(out.size - 1)
                val q = price(qm.groupValues[1]) ?: 1.0
                val u = price(qm.groupValues[2]) ?: last.unitPrice
                out.add(ReceiptLine(last.name, q, u))
            } else if (letters >= 2 && pe != null) {
                val total = price(pe.groupValues[1]) ?: continue
                if (total <= 0) continue
                if (qm != null && !n.contains("kg")) {
                    out.add(ReceiptLine(name, price(qm.groupValues[1]) ?: 1.0, price(qm.groupValues[2]) ?: total))
                } else {
                    out.add(ReceiptLine(name, 1.0, total))
                }
            }
        }
        return out
    }
}
