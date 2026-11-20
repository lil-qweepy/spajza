package sk.spajza.app.data

/** Potrebne mnozstvo suroviny: unit je "g", "ml" alebo "ks". */
data class Need(val amount: Double, val unit: String)

object Amounts {
    private val RE = Regex("(\\d+(?:[.,]\\d+)?)\\s*(kg|dkg|ml|dl|cl|g|l|ks|konzerv\\w*|balen\\w*|kus\\w*)(?![a-z])")

    /** Z textu ako "200 g", "1,5 l" alebo "1 konzerva" vyrobi mnozstvo; inak null. */
    fun parse(text: String): Need? {
        val t = Calc.norm(text)
        val m = RE.find(t) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val u = m.groupValues[2]
        return when {
            u == "kg" -> Need(v * 1000, "g")
            u == "dkg" -> Need(v * 10, "g")
            u == "g" -> Need(v, "g")
            u == "l" -> Need(v * 1000, "ml")
            u == "dl" -> Need(v * 100, "ml")
            u == "cl" -> Need(v * 10, "ml")
            u == "ml" -> Need(v, "ml")
            else -> Need(v, "ks")
        }
    }

    fun scale(n: Need, f: Double): Need = Need(n.amount * f, n.unit)

    fun format(n: Need): String = when (n.unit) {
        "g" -> if (n.amount >= 1000) Calc.num(n.amount / 1000, 2) + " kg" else Calc.num(n.amount, 0) + " g"
        "ml" -> if (n.amount >= 1000) Calc.num(n.amount / 1000, 2) + " l" else Calc.num(n.amount, 0) + " ml"
        else -> Calc.num(n.amount, 1) + " ks"
    }

    private fun inUnit(i: Item, q: Double, unit: String): Double? = when (unit) {
        "g" -> Calc.grams(i, q)
        "ml" -> Calc.ml(i, q)
        else -> Calc.pieces(i, q)
    }

    /** Mnozstvo v jednotke produktu zodpovedajuce potrebe; null ak sa neda prepocitat. */
    fun itemQty(i: Item, n: Need): Double? = when (n.unit) {
        "g" -> when {
            i.uom == Uom.KG -> n.amount / 1000
            i.uom == Uom.G -> n.amount
            i.uom.isCountable && i.net > 0 && !i.netLiquid -> n.amount / i.net
            else -> null
        }
        "ml" -> when {
            i.uom == Uom.L -> n.amount / 1000
            i.uom == Uom.ML -> n.amount
            i.uom.isCountable && i.net > 0 && i.netLiquid -> n.amount / i.net
            else -> null
        }
        else -> if (i.uom.isCountable) n.amount else null
    }

    /** Ci zasoby staci. Ak sa niektory produkt neda prepocitat, predpoklada sa, ze staci. */
    fun enough(items: List<StockItem>, n: Need): Boolean {
        var sum = 0.0
        for (s in items) {
            val v = inUnit(s.item, s.qty, n.unit) ?: return true
            sum += v
        }
        return sum + 1e-6 >= n.amount
    }

    fun available(items: List<StockItem>, unit: String): Double? {
        var sum = 0.0
        for (s in items) {
            val v = inUnit(s.item, s.qty, unit) ?: return null
            sum += v
        }
        return sum
    }
}
