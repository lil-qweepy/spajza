package sk.spajza.app.data

import kotlin.math.ceil

/** Zakladna surovina do nudzovych zasob; mnozstvo je na osobu a den pri 2000 kcal (g alebo ml). */
data class BasicNeed(
    val name: String,
    val kind: String,
    val liquid: Boolean,
    val perPersonDay: Double,
    /** Slova (bez diakritiky), podla ktorych sa produkt rozpozna, ak nema druh. */
    val words: List<String> = emptyList()
)

/** Jeden riadok planu: kolko treba, kolko mate a kolko dokupit. */
data class BasicLine(
    val need: BasicNeed,
    /** Potreba v g alebo ml. */
    val required: Double,
    val have: Double,
    val buy: Double,
    val onList: Boolean
) {
    val uom: Uom get() = if (need.liquid) Uom.L else Uom.KG

    /** Mnozstvo na kupu v kg / l, zaokruhlene nahor. */
    val buyQty: Double get() {
        val step = if (need.kind == "Voda") 500.0 else 100.0
        return ceil(buy / step - 1e-9) * step / 1000.0
    }
}

object EmergencyShop {
    private const val WATER = "Voda"

    val basics: List<BasicNeed> = listOf(
        BasicNeed("Pitná voda", WATER, true, 0.0),
        BasicNeed("Cestoviny", "Cestoviny", false, 100.0),
        BasicNeed("Ryža", "Ryža", false, 80.0),
        BasicNeed("Ovsené vločky", "Ovsené vločky", false, 50.0),
        BasicNeed("Múka", "Múka", false, 50.0),
        BasicNeed("Fazuľa", "Fazuľa", false, 100.0),
        BasicNeed("Tuniak", "Tuniak", false, 50.0),
        BasicNeed("Cukor", "Cukor", false, 30.0),
        BasicNeed("Orechy", "Orechy", false, 30.0),
        BasicNeed("Olej", "Olej", true, 30.0),
        BasicNeed("Trvanlivé mlieko", "Mlieko", true, 200.0),
        BasicNeed("Soľ", "", false, 5.0, listOf("sol"))
    )

    private fun matches(i: Item, n: BasicNeed): Boolean {
        if (n.kind.isNotEmpty() && n.kind in Kinds.forItem(i)) return true
        if (n.words.isEmpty()) return false
        val ws = Calc.norm(i.name + " " + i.brand).split(Regex("[^a-z0-9]+"))
        return n.words.any { it in ws }
    }

    /** Plan nakupu zakladnych surovin na [days] dni pre osoby podla nastaveni aplikacie. */
    fun plan(db: Db, days: Int): List<BasicLine> {
        val s = db.settings
        val persons = s.persons.coerceAtLeast(1)
        val scale = (s.kcalPerDay / 2000.0).coerceAtLeast(0.1)
        val stock = db.stock().filter { it.qty > 0 && it.item.emergency }
        val waterHave = Emergency.report(db).waterMl
        val out = ArrayList<BasicLine>()
        for (n in basics) {
            val required: Double
            val have: Double
            if (n.kind == WATER) {
                required = persons * days * s.waterPerDayL * 1000.0
                have = waterHave
            } else {
                required = persons * days * n.perPersonDay * scale
                have = stock.filter { matches(it.item, n) }.sumOf { st ->
                    (if (n.liquid) Calc.ml(st.item, st.qty) else Calc.grams(st.item, st.qty)) ?: 0.0
                }
            }
            val buy = (required - have).coerceAtLeast(0.0)
            val nc = Pairing.core(n.name)
            val onList = db.shop.any { x -> Pairing.related(Pairing.core(x.name, "", db.stores), nc) }
            out.add(BasicLine(n, required, have, buy, onList))
        }
        return out
    }

    /** Polozka do nakupneho zoznamu pre dany riadok planu. */
    fun toShop(line: BasicLine, persons: Int, days: Int): ShopItem =
        ShopItem(
            name = line.need.name, qty = line.buyQty, uom = line.uom,
            note = "Núdzové zásoby: " + persons + " os. × " + days + " dní"
        )
}
