package sk.spajza.app.data

import kotlinx.serialization.Serializable

enum class Uom(val label: String, val longName: String) {
    PCS("ks", "kus"),
    KG("kg", "kilogram"),
    G("g", "gram"),
    L("l", "liter"),
    ML("ml", "mililiter"),
    PACK("bal.", "balenie");

    val isMass: Boolean get() = this == KG || this == G
    val isVolume: Boolean get() = this == L || this == ML
    val isCountable: Boolean get() = this == PCS || this == PACK
}

enum class PriceBasis(val label: String) {
    PER_PIECE("za kus"),
    PER_PACK("za balenie"),
    PER_KG("za kilogram"),
    PER_L("za liter")
}

enum class MoveType(val label: String) {
    ADD("Pridanie"),
    REMOVE("Odobratie"),
    CONSUME("Spotreba"),
    DISCARD("Vyhodenie"),
    ADJUST("Úprava množstva"),
    MOVE("Presun"),
    OPEN("Otvorenie")
}

@Serializable
data class Place(val id: Long, val name: String)

@Serializable
data class Item(
    val id: Long = 0,
    val name: String,
    val brand: String = "",
    val manufacturer: String = "",
    val ean: String = "",
    val category: String = "Iné",
    /** Druh potraviny (napr. Múka) - podla neho recepty najdu surovinu. */
    val kind: String = "",
    /** Nazov skupiny rovnakych produktov (napr. mlieko z Tesca a z Lidla); prazdne = nesparovany. */
    val group: String = "",
    val image: String? = null,
    val uom: Uom = Uom.PCS,
    /** Obsah jedneho kusa/balenia v g alebo ml (0 = neznamy). */
    val net: Double = 0.0,
    /** true = net je v ml, false = v g. */
    val netLiquid: Boolean = false,
    val price: Double = 0.0,
    val basis: PriceBasis = PriceBasis.PER_PIECE,
    val kcal: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val satFat: Double? = null,
    val carbs: Double? = null,
    val sugar: Double? = null,
    val fiber: Double? = null,
    val salt: Double? = null,
    /** Minimalne mnozstvo v jednotke produktu; 0 = nesledovat. */
    val minQty: Double = 0.0,
    val placeId: Long? = null,
    val water: Boolean = false,
    val emergency: Boolean = true,
    val lowNotified: Boolean = false,
    /** Trvanlivost po otvoreni v dnoch; 0 = odhad podla druhu potraviny. */
    val openDays: Int = 0,
    /** Vyzivove hodnoty su len priemerny odhad (nie z obalu). */
    val nutriEstimate: Boolean = false,
    /** Produkt vznikol len z fotky pri skenovani; udaje treba doplnit neskor. */
    val draft: Boolean = false,
    val note: String = "",
    val created: Long = 0
)

@Serializable
data class Batch(
    val id: Long = 0,
    val itemId: Long,
    val qty: Double,
    /** Dni od epochy (LocalDate.toEpochDay). */
    val bought: Long? = null,
    val expiry: Long? = null,
    val placeId: Long? = null,
    /** 0 nic, 1 = do 30 dni, 2 = do 7 dni, 3 = do 3 dni, 4 = po expiracii. */
    val notified: Int = 0,
    /** Den (epoch), kedy bolo balenie otvorene; null = neotvorene. */
    val opened: Long? = null,
    /** Povodna expiracia pred otvorenim (len informativne). */
    val origExpiry: Long? = null
)

@Serializable
data class Move(
    val id: Long = 0,
    val itemId: Long? = null,
    val name: String,
    val type: MoveType,
    /** Zmena mnozstva (+ prirastok, - ubytok). */
    val qty: Double,
    val uom: Uom,
    val value: Double = 0.0,
    val kcal: Double = 0.0,
    val waterMl: Double = 0.0,
    val time: Long = 0,
    val note: String = "",
    val from: String = "",
    val to: String = ""
)

@Serializable
data class ShopItem(
    val id: Long = 0,
    val itemId: Long? = null,
    val name: String,
    val qty: Double = 1.0,
    val uom: Uom = Uom.PCS,
    /** Cena za jednu jednotku (podla uom). */
    val price: Double = 0.0,
    val checked: Boolean = false,
    /** Obchod, kde je polozka v akcii / kde ju chcete kupit (prazdne = lubovolny). */
    val store: String = "",
    /** Bezna cena za jednotku (na vypocet usetrenia pri akcii); 0 = neznama. */
    val regularPrice: Double = 0.0,
    /** Do kedy plati akcia (den od epochy); null = bez obmedzenia. */
    val dealUntil: Long? = null,
    val note: String = "",
    /** Mnozstvova akcia "kup X, zaplat Y" (napr. 2 a 1); 0 = bez nej. */
    val buyQty: Double = 0.0,
    val payQty: Double = 0.0,
    /** Velkost jedneho balenia v g alebo ml (napr. 150 alebo 250); 0 = nezadane (nepovinne). */
    val packSize: Double = 0.0,
    val packLiquid: Boolean = false
) {
    /** Velkost balenia ako text (napr. "250 g" alebo "1,5 l"); prazdny, ak nie je zadana. */
    fun packText(): String =
        if (packSize <= 0) "" else if (packLiquid) Calc.volumeText(packSize) else Calc.massText(packSize)

    /** Skutocna cena za kg alebo l (podla balenia alebo jednotky) a jednotka; null, ak sa neda urcit. */
    fun perKgL(): Pair<Double, String>? {
        val e = effPrice()
        if (e <= 0) return null
        return when {
            uom == Uom.KG -> e to "kg"
            uom == Uom.G -> (e * 1000) to "kg"
            uom == Uom.L -> e to "l"
            uom == Uom.ML -> (e * 1000) to "l"
            uom.isCountable && packSize > 0 -> (e / (packSize / 1000.0)) to (if (packLiquid) "l" else "kg")
            else -> null
        }
    }

    val multiBuy: Boolean get() = buyQty > 1.0 && payQty > 0 && payQty < buyQty

    /** Cena za cele mnozstvo s ohladom na akciu "kup X, zaplat Y". */
    fun cost(): Double {
        if (!multiBuy) return qty * price
        val sets = Math.floor(qty / buyQty + 1e-9)
        val rem = qty - sets * buyQty
        return (sets * payQty + rem) * price
    }

    /** Skutocna cena za jednu jednotku po zapocitani akcie. */
    fun effPrice(): Double = if (qty > 0) cost() / qty else price

    /** Kolko sa usetri oproti bezne cene (alebo oproti cene bez mnozstvovej akcie). */
    fun saving(): Double {
        val full = (if (regularPrice > price) regularPrice else price) * qty
        return Math.max(0.0, full - cost())
    }
}

/** Zaznam o cene z nakupu (z nakupneho zoznamu); zaklad pre historiu cien, porovnanie obchodov a rozpocet. */
@Serializable
data class PricePoint(
    val itemId: Long? = null,
    val name: String,
    val store: String = "",
    /** Cena za jednu jednotku [uom]. */
    val price: Double,
    val uom: Uom,
    val qty: Double,
    /** Den nakupu (epoch day). */
    val day: Long,
    /** Cena prepocitana na kg, l alebo ks - na porovnanie roznych balenii. */
    val base: Double,
    val baseUnit: String,
    val deal: Boolean = false
)

object Stores {
    val DEFAULT = listOf("Lidl", "Kaufland", "Tesco", "Billa", "Coop Jednota", "Terno", "Fresh", "Metro", "dm", "Teta")
    fun all(db: Db): List<String> = (DEFAULT + db.stores).distinct()
}

@Serializable
data class Ingredient(
    val name: String,
    /** Kluce (bez diakritiky, male pismena), podla ktorych sa hlada surovina v zasobach. */
    val keys: List<String>,
    val amount: String = "",
    /** Bezna surovina (sol, olej...), ktoru predpokladame doma. */
    val basic: Boolean = false
)

@Serializable
data class Recipe(
    val id: Long = 0,
    val name: String,
    val minutes: Int = 30,
    val servings: Int = 2,
    val ingredients: List<Ingredient>,
    val steps: List<String>
)

@Serializable
data class Snap(val day: Long, val value: Double)

@Serializable
data class AppSettings(
    val persons: Int = 2,
    val kcalPerDay: Double = 2000.0,
    val waterPerDayL: Double = 2.0,
    val targetDays: Int = 14,
    val notificationsEnabled: Boolean = true,
    val notify30: Boolean = true,
    val notify7: Boolean = true,
    val notify3: Boolean = true,
    val notifyLow: Boolean = true,
    val notifyDaily: Boolean = true,
    val notifyHour: Int = 8,
    val autoFetch: Boolean = true,
    val lastAutoFill: Long = 0,
    val checked: Set<String> = emptySet(),
    /** Mesacny rozpocet na nakupy v EUR; 0 = nesledovat. */
    val monthlyBudget: Double = 0.0
)

@Serializable
data class Db(
    val items: List<Item> = emptyList(),
    val batches: List<Batch> = emptyList(),
    val places: List<Place> = emptyList(),
    val categories: List<String> = emptyList(),
    /** Vlastne druhy potravin (okrem vstavanych). */
    val kinds: List<String> = emptyList(),
    val moves: List<Move> = emptyList(),
    val shop: List<ShopItem> = emptyList(),
    /** Vlastne obchody (okrem vstavanych v [Stores]). */
    val stores: List<String> = emptyList(),
    /** Historia cien z nakupov (najnovsie na konci). */
    val prices: List<PricePoint> = emptyList(),
    /** Vlastne recepty pouzivatela (vstavane su v kode). */
    val recipes: List<Recipe> = emptyList(),
    val snaps: List<Snap> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val nextId: Long = 1
)

/** Produkt spolu s jeho sarzami a vypocitanymi hodnotami. */
data class StockItem(
    val item: Item,
    val batches: List<Batch>,
    val qty: Double,
    val value: Double,
    val kcal: Double,
    val waterMl: Double,
    val nearest: Long?
) {
    val low: Boolean get() = item.minQty > 0 && qty <= item.minQty
}

fun Db.placeName(id: Long?): String = if (id == null) "" else places.firstOrNull { it.id == id }?.name.orEmpty()

fun Db.stock(): List<StockItem> {
    val byItem = batches.filter { it.qty > 0 }.groupBy { it.itemId }
    return items.map { buildStock(it, byItem[it.id].orEmpty()) }
        .sortedBy { it.item.name.lowercase() }
}

fun buildStock(item: Item, batches: List<Batch>): StockItem {
    val qty = batches.sumOf { it.qty }
    return StockItem(
        item = item,
        batches = batches.sortedBy { it.expiry ?: Long.MAX_VALUE },
        qty = qty,
        value = Calc.value(item, qty),
        kcal = Calc.kcal(item, qty),
        waterMl = Calc.waterMl(item, qty),
        nearest = batches.mapNotNull { it.expiry }.minOrNull()
    )
}
