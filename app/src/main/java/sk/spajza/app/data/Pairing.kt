package sk.spajza.app.data

/**
 * Skupina rovnakych produktov (napr. mlieko plnotucne z Tesca aj z Lidla - ine EAN, ale ten isty produkt).
 * Nespárovany produkt tvori skupinu sam.
 */
data class Grp(
    val key: String,
    val name: String,
    val members: List<StockItem>,
    /** Zastupca skupiny (clen s najvacsou zasobou); jeho jednotka sa pouziva pre mnozstva skupiny. */
    val primary: StockItem,
    /** Spolocny zaklad prepoctu ("ml", "g", "ks"); prazdny pre skupinu s jednym clenom. */
    val unit: String,
    /** Zasoba spolu v jednotke [primary]. */
    val qty: Double,
    /** Minimum (najvyssie z clenov) v jednotke [primary]; 0 = nesledovat. */
    val minQty: Double
) {
    val grouped: Boolean get() = members.size > 1
    val low: Boolean get() = minQty > 0 && qty <= minQty
    val ids: Set<Long> get() = members.map { it.item.id }.toSet()
}

object Pairing {
    private val STOP = setOf("bez", "pre", "bio", "cca", "the", "akcia", "akcie", "akciu", "zlava", "novy", "nova")

    private fun stem(w: String): String? {
        val n = Calc.norm(w)
        if (n.length < 3 || n.any { it.isDigit() }) return null
        if (n in STOP) return null
        return if (n.length > 5) n.take(5) else n
    }

    private fun words(s: String): List<String> = Calc.norm(s).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private fun storeStems(extra: List<String>): Set<String> =
        (Stores.DEFAULT + extra).flatMap { words(it) }.mapNotNull { stem(it) }.toSet()

    /** Podstatne slova nazvu bez znacky, obchodu, velkosti baleni a slov ako "akcia". */
    fun core(name: String, brand: String = "", extraStores: List<String> = emptyList()): Set<String> {
        val drop = storeStems(extraStores) + words(brand).mapNotNull { stem(it) }
        return words(name).mapNotNull { stem(it) }.filter { it !in drop }.toSet()
    }

    fun core(i: Item, extraStores: List<String> = emptyList()): Set<String> = core(i.name, i.brand, extraStores)

    /** Jedna mnozina slov obsahuje druhu (napr. "mlieko" ~ "mlieko plnotucne"). */
    fun related(a: Set<String>, b: Set<String>): Boolean =
        a.isNotEmpty() && b.isNotEmpty() && (a.containsAll(b) || b.containsAll(a))

    fun identical(a: Set<String>, b: Set<String>): Boolean = a.isNotEmpty() && a == b

    fun groupKey(i: Item): String = if (i.group.isBlank()) "#" + i.id else "g:" + Calc.norm(i.group.trim())

    /** Nazov skupiny odvodeny z nazvu produktu bez znacky a obchodu. */
    fun labelFor(i: Item, extraStores: List<String> = emptyList()): String {
        val keep = core(i, extraStores)
        val ws = i.name.trim().split(Regex("\\s+")).filter { w -> stem(w)?.let { it in keep } == true }
        val s = ws.joinToString(" ").ifBlank { i.name.trim() }
        return s.replaceFirstChar { it.uppercase() }
    }

    // ---------- prepocty medzi clenmi skupiny ----------

    private fun pickUnit(ms: List<Item>): String? = when {
        ms.all { Calc.ml(it, 1.0) != null } -> "ml"
        ms.all { Calc.grams(it, 1.0) != null } -> "g"
        ms.all { it.uom.isCountable } -> "ks"
        else -> null
    }

    private fun toBase(i: Item, q: Double, unit: String): Double? = when (unit) {
        "ml" -> Calc.ml(i, q)
        "g" -> Calc.grams(i, q)
        else -> Calc.pieces(i, q)
    }

    private fun fromBase(i: Item, base: Double, unit: String): Double = when (unit) {
        "ml" -> when {
            i.uom == Uom.L -> base / 1000
            i.uom == Uom.ML -> base
            i.uom.isCountable && i.net > 0 -> base / i.net
            else -> base
        }
        "g" -> when {
            i.uom == Uom.KG -> base / 1000
            i.uom == Uom.G -> base
            i.uom.isCountable && i.net > 0 -> base / i.net
            else -> base
        }
        else -> base
    }

    /** Mnozstvo [q] clena [m] vyjadrene v jednotke zastupcu skupiny. */
    fun toPrimary(g: Grp, m: Item, q: Double): Double {
        if (m.id == g.primary.item.id || g.unit.isEmpty()) return q
        return fromBase(g.primary.item, toBase(m, q, g.unit) ?: 0.0, g.unit)
    }

    private fun single(m: StockItem): Grp =
        Grp(groupKey(m.item), m.item.name, listOf(m), m, "", m.qty, m.item.minQty)

    /** Vsetky skupiny (nespárované produkty ako samostatné skupiny), zoradene podla nazvu. */
    fun groups(db: Db, stock: List<StockItem> = db.stock()): List<Grp> {
        val out = ArrayList<Grp>()
        for (ms in stock.groupBy { groupKey(it.item) }.values) {
            val unit = if (ms.size > 1) pickUnit(ms.map { it.item }) else null
            if (unit == null) {
                for (m in ms) out.add(single(m))
                continue
            }
            val primary = ms.maxByOrNull { toBase(it.item, it.qty, unit) ?: 0.0 } ?: ms.first()
            val draft = Grp(groupKey(primary.item), primary.item.group.trim(), ms, primary, unit, 0.0, 0.0)
            val qty = ms.sumOf { toPrimary(draft, it.item, it.qty) }
            val min = ms.maxOf { if (it.item.minQty > 0) toPrimary(draft, it.item, it.item.minQty) else 0.0 }
            out.add(draft.copy(qty = qty, minQty = min))
        }
        return out.sortedBy { it.name.lowercase() }
    }

    /** Skupiny, ktorym dochadza zasoba (ako celok). */
    fun lowGroups(db: Db): List<Grp> = groups(db).filter { it.low }

    /** Id produktov, ktorych skupine dochadza zasoba. */
    fun lowIds(db: Db): Set<Long> = lowGroups(db).flatMap { it.ids }.toSet()

    /**
     * Je skupina uz v nakupnom zozname? Plati aj pre ineho clena skupiny a pre volny text
     * (napr. "Mlieko plnotucne" z letaku), ktory je rovnaky alebo vseobecnejsi nez nazov produktu.
     */
    fun onShopList(db: Db, g: Grp, includeChecked: Boolean = true): Boolean {
        val cores = g.members.map { core(it.item, db.stores) } + listOf(core(g.name, "", db.stores))
        return db.shop.any { s ->
            if (!includeChecked && s.checked) return@any false
            val sid = s.itemId
            if (sid != null) {
                sid in g.ids
            } else {
                val sc = core(s.name, "", db.stores)
                sc.isNotEmpty() && cores.any { it.containsAll(sc) }
            }
        }
    }

    // ---------- sparovanie ----------

    fun members(db: Db, item: Item): List<Item> = db.items.filter { groupKey(it) == groupKey(item) }

    /** Produkty, ktore vyzeraju ako ten isty produkt (rovnake slova najprv). */
    fun candidates(db: Db, item: Item): List<Item> {
        val c = core(item, db.stores)
        val mine = groupKey(item)
        return db.items.filter { it.id != item.id && groupKey(it) != mine && related(c, core(it, db.stores)) }
            .sortedBy { if (identical(c, core(it, db.stores))) 0 else 1 }
    }

    fun pair(db: Db, a: Item, b: Item): Db {
        val ka = groupKey(a)
        val kb = groupKey(b)
        val label = when {
            a.group.isNotBlank() -> a.group
            b.group.isNotBlank() -> b.group
            else -> labelFor(a, db.stores)
        }
        val ids = db.items.filter { val k = groupKey(it); k == ka || k == kb }.map { it.id }.toSet() + a.id + b.id
        return db.copy(items = db.items.map { if (it.id in ids) it.copy(group = label) else it })
    }

    fun unpair(db: Db, item: Item): Db {
        val k = groupKey(item)
        val rest = db.items.filter { groupKey(it) == k && it.id != item.id }
        val clear = if (rest.size <= 1) rest.map { it.id }.toSet() + item.id else setOf(item.id)
        return db.copy(items = db.items.map { if (it.id in clear) it.copy(group = "") else it })
    }
}
