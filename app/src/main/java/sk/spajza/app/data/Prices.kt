package sk.spajza.app.data

import java.time.LocalDate
import java.time.YearMonth

/** Riadok s hodnotenim ceny; level: 0 informacia, 1 dobre, 2 draho. */
data class PriceNote(val text: String, val level: Int)

object Prices {

    /** Cena za jednotku prepocitana na kg / l / ks. */
    fun base(item: Item?, uom: Uom, price: Double): Pair<Double, String> = when {
        uom == Uom.KG -> price to "kg"
        uom == Uom.G -> (price * 1000) to "kg"
        uom == Uom.L -> price to "l"
        uom == Uom.ML -> (price * 1000) to "l"
        uom.isCountable && item != null && item.net > 0 ->
            (price / (item.net / 1000.0)) to (if (item.netLiquid) "l" else "kg")
        else -> price to "ks"
    }

    /** Zaznamy o cenach produktu (podla id alebo nazvu), najnovsie prve. */
    fun forItem(db: Db, itemId: Long?, name: String): List<PricePoint> {
        val key = Calc.norm(name.trim())
        return db.prices
            .filter { (itemId != null && it.itemId == itemId) || Calc.norm(it.name.trim()) == key }
            .sortedByDescending { it.day }
    }

    /** Posledna cena v kazdom obchode, od najlacnejsieho. */
    fun latestByStore(list: List<PricePoint>): List<PricePoint> =
        list.filter { it.store.isNotBlank() }
            .groupBy { it.store }
            .map { e -> e.value.maxByOrNull { it.day }!! }
            .sortedBy { it.base }

    /** Kolko sa tento mesiac minulo na nakupy zo zoznamu. */
    fun monthSpent(db: Db, today: Long = Calc.today()): Double {
        val ym = YearMonth.from(LocalDate.ofEpochDay(today))
        return db.prices.filter { YearMonth.from(LocalDate.ofEpochDay(it.day)) == ym }.sumOf { it.price * it.qty }
    }

    fun perUnit(v: Double, unit: String): String = Calc.money(v) + " / " + unit

    /** Cena za jednotku [from] prepocitana na jednotku [to] (g/kg, ml/l, ks/bal.); null ak sa neda. */
    fun convertUnitPrice(p: Double, from: Uom, to: Uom): Double? = when {
        from == to -> p
        from == Uom.KG && to == Uom.G -> p / 1000
        from == Uom.G && to == Uom.KG -> p * 1000
        from == Uom.L && to == Uom.ML -> p / 1000
        from == Uom.ML && to == Uom.L -> p * 1000
        from.isCountable && to.isCountable -> p
        else -> null
    }

    /** Cena produktu [m] (za jeho jednotku) vyjadrena za jednotku [target], aj cez obsah 1 ks (napr. ks -> l). */
    private fun priceIn(m: Item, up: Double, target: Uom): Double? {
        convertUnitPrice(up, m.uom, target)?.let { return it }
        if (m.uom.isCountable && m.net > 0) {
            val perBase = up / m.net   // za g alebo ml
            return when {
                !m.netLiquid && target == Uom.G -> perBase
                !m.netLiquid && target == Uom.KG -> perBase * 1000
                m.netLiquid && target == Uom.ML -> perBase
                m.netLiquid && target == Uom.L -> perBase * 1000
                else -> null
            }
        }
        return null
    }

    /**
     * Priemerna cena produktu, ktory uz mate v zasobach (vazena zasobou; pri sparovanych produktoch spolu),
     * vyjadrena za jednotku polozky nakupneho zoznamu. null, ak sa neda urcit.
     */
    fun stockPrice(db: Db, s: ShopItem): Double? {
        if (s.effPrice() <= 0) return null
        val shopItem = s.itemId?.let { id -> db.items.firstOrNull { it.id == id } }
        val members: List<Item> = if (shopItem != null) {
            Pairing.members(db, shopItem)
        } else {
            val sc = Pairing.core(s.name, "", db.stores)
            if (sc.isEmpty()) emptyList() else db.items.filter { Pairing.core(it, db.stores).containsAll(sc) }
        }
        var wSum = 0.0
        var pSum = 0.0
        var plain = 0.0
        var n = 0
        for (m in members) {
            val up = Calc.unitPrice(m)
            if (up <= 0) continue
            val p = priceIn(m, up, s.uom) ?: continue
            val q = db.batches.filter { it.itemId == m.id }.sumOf { it.qty }
            val w = if (q > 0) (Calc.ml(m, q) ?: Calc.grams(m, q) ?: q) else 0.0
            wSum += w
            pSum += w * p
            plain += p
            n++
        }
        if (n == 0) return null
        return if (wSum > 0) pSum / wSum else plain / n
    }

    // ---------- porovnanie akcii ----------

    /** Dve akcie na ten isty produkt (z roznych obchodov); [cheaper] je lacnejsia, rozdiel je za jednotku polozky [cheaper]. */
    data class DealCompare(
        val cheaper: ShopItem, val dearer: ShopItem, val diff: Double, val pct: Long,
        /** Jednotka, v ktorej je rozdiel (kg, l, alebo jednotka polozky). */
        val unit: String
    )

    private fun sameProduct(db: Db, a: ShopItem, b: ShopItem): Boolean {
        val ia = a.itemId?.let { id -> db.items.firstOrNull { it.id == id } }
        val ib = b.itemId?.let { id -> db.items.firstOrNull { it.id == id } }
        if (ia != null && ib != null && Pairing.groupKey(ia) == Pairing.groupKey(ib)) return true
        val ca = if (ia != null) Pairing.core(ia, db.stores) else Pairing.core(a.name, "", db.stores)
        val cb = if (ib != null) Pairing.core(ib, db.stores) else Pairing.core(b.name, "", db.stores)
        return Pairing.identical(ca, cb)
    }

    /** Porovna novu / upravenu polozku [s] s inymi polozkami zoznamu pre ten isty produkt; najlacnejsiu ponuku proti nej vrati. */
    fun compareDeals(db: Db, s: ShopItem): DealCompare? {
        if (s.effPrice() <= 0) return null
        var best: DealCompare? = null
        for (o in db.shop) {
            if (o.id == s.id || o.effPrice() <= 0 || !sameProduct(db, s, o)) continue
            // Ak je zadana velkost balenia pri oboch, porovnava sa cena za kg / l (150 g vs 250 g).
            val ks = s.perKgL()
            val ko = o.perKgL()
            val ps: Double
            val po: Double
            val unit: String
            if (ks != null && ko != null && ks.second == ko.second && (s.packSize > 0 || o.packSize > 0)) {
                ps = ks.first
                po = ko.first
                unit = ks.second
            } else {
                ps = s.effPrice()
                po = convertUnitPrice(o.effPrice(), o.uom, s.uom) ?: continue
                unit = s.uom.label
            }
            val diff = Math.abs(ps - po)
            if (diff < 0.004) continue
            val cmp = if (ps < po) DealCompare(s, o, diff, Math.round(diff / po * 100), unit)
            else DealCompare(o, s, diff, Math.round(diff / ps * 100), unit)
            val cur = best
            if (cur == null || cmp.pct > cur.pct) best = cmp
        }
        return best
    }

    /** Usetrenie polozky: oproti bezne cene / mnozstvovej akcii, inak oproti priemernej cene v zasobach. */
    fun saving(db: Db, s: ShopItem): Double {
        if (s.regularPrice > s.price || s.multiBuy) return s.saving()
        val ref = stockPrice(db, s) ?: return 0.0
        return Math.max(0.0, (ref - s.effPrice()) * s.qty)
    }

    /** Hodnotenie ceny polozky nakupneho zoznamu podla historie. */
    fun notes(db: Db, s: ShopItem): List<PriceNote> {
        if (s.price <= 0) return emptyList()
        val item = s.itemId?.let { id -> db.items.firstOrNull { it.id == id } }
        val b = base(item, s.uom, s.effPrice())
        val cur = b.first
        val unit = b.second
        val pts = forItem(db, s.itemId, s.name).filter { it.baseUnit == unit }
        if (pts.isEmpty()) return emptyList()
        val out = ArrayList<PriceNote>()
        val min = pts.minOf { it.base }
        val avg = pts.map { it.base }.average()
        if (pts.size >= 2) {
            if (cur <= min * 1.02) out.add(PriceNote("Najlepšia cena doteraz (bežne ø " + perUnit(avg, unit) + ")", 1))
            else if (cur > avg * 1.10) out.add(PriceNote("Drahšie ako bežne (ø " + perUnit(avg, unit) + ")", 2))
        }
        val best = latestByStore(pts).firstOrNull()
        if (best != null && !best.store.equals(s.store, ignoreCase = true) && best.base < cur * 0.92) {
            out.add(PriceNote("Lacnejšie býva v " + best.store + ": " + perUnit(best.base, unit), 0))
        }
        return out
    }
}
