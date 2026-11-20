package sk.spajza.app.data

import kotlin.math.ceil

/** Odhad, kedy produkt (alebo skupina rovnakych produktov) dojde, podla doterajsej spotreby. */
data class Forecast(
    val group: Grp,
    /** Priemerna spotreba za den v jednotke zastupcu skupiny. */
    val perDay: Double,
    /** Na kolko dni este zasoba vystaci (0 = uz nie je). */
    val daysLeft: Double,
    /** Navrhovane mnozstvo na kupu v jednotke zastupcu (pokrytie na [Forecasts.TARGET_DAYS] dni). */
    val buy: Double
) {
    val stock: StockItem get() = group.primary
}

object Forecasts {
    const val WINDOW_DAYS = 60
    const val MIN_SPAN_DAYS = 7
    const val MIN_MOVES = 2
    const val WARN_DAYS = 5
    const val TARGET_DAYS = 14

    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun toItemUnit(q: Double, from: Uom, to: Uom): Double? = when {
        from == to -> q
        from == Uom.KG && to == Uom.G -> q * 1000
        from == Uom.G && to == Uom.KG -> q / 1000
        from == Uom.L && to == Uom.ML -> q * 1000
        from == Uom.ML && to == Uom.L -> q / 1000
        else -> null
    }

    /** Skupiny produktov, ktorym podla spotreby zasoba vystaci na [WARN_DAYS] dni alebo menej. Najskor dojdu prve. */
    fun soonOut(db: Db, now: Long = System.currentTimeMillis()): List<Forecast> {
        val cutoff = now - WINDOW_DAYS * DAY_MS
        val moves = db.moves.filter {
            it.itemId != null && it.type == MoveType.CONSUME && it.qty < 0 && it.time >= cutoff
        }.groupBy { it.itemId!! }
        val out = ArrayList<Forecast>()
        for (g in Pairing.groups(db)) {
            var consumed = 0.0
            var count = 0
            var first = Long.MAX_VALUE
            for (m in g.members) {
                val ms = moves[m.item.id] ?: continue
                count += ms.size
                first = minOf(first, ms.minOf { it.time })
                val inMember = ms.sumOf { toItemUnit(-it.qty, it.uom, m.item.uom) ?: 0.0 }
                consumed += Pairing.toPrimary(g, m.item, inMember)
            }
            if (count < MIN_MOVES || consumed <= 0) continue
            val span = ((now - first).toDouble() / DAY_MS).coerceIn(MIN_SPAN_DAYS.toDouble(), WINDOW_DAYS.toDouble())
            val perDay = consumed / span
            val daysLeft = g.qty / perDay
            if (daysLeft > WARN_DAYS) continue
            var buy = perDay * TARGET_DAYS - g.qty
            buy = if (g.primary.item.uom.isCountable) ceil(buy - 1e-9).coerceAtLeast(1.0)
            else (Math.round(buy * 100) / 100.0).coerceAtLeast(0.01)
            out.add(Forecast(g, perDay, daysLeft, buy))
        }
        return out.sortedBy { it.daysLeft }
    }

    fun daysText(days: Double): String = when {
        days < 0.5 -> "už nie je"
        days < 1.5 -> "vystačí na cca 1 deň"
        else -> "vystačí na cca " + Math.round(days) + " dní"
    }
}
