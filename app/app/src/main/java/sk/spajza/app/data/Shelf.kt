package sk.spajza.app.data

/** Druh miesta uloženia z pohľadu trvanlivosti. */
enum class PlaceType { FRIDGE, FREEZER, ROOM }

/**
 * Odhad trvanlivosti podľa miesta uloženia (čisté funkcie, bez závislosti na UI).
 * Základ (napr. 3 dni pre mäso) platí pre miesto, kde sa potravina zvyčajne skladuje;
 * pri inom mieste sa prepočíta (mraznička = mesiace, rozmrazené = pár dní).
 */
object Shelf {
    /** Ako dlho vydrží zmrazená potravina danej kategórie (dni). */
    private val freezerDaysByCategory: Map<String, Int> = mapOf(
        "Mäso a ryby" to 180,
        "Mliečne výrobky" to 90,
        "Ovocie a zelenina" to 240,
        "Pečivo" to 90,
        "Mrazené" to 270
    )
    private const val FREEZER_DEFAULT = 180

    /** Po rozmrazení (presun do chladničky) a pri izbovej teplote. */
    const val THAWED_FRIDGE_DAYS = 3
    const val THAWED_ROOM_DAYS = 1

    /** Rozmrazene mäso a ryby sa spotrebuju rychlejsie (2 dni), ostatne potraviny do 3 dni. */
    fun thawDays(category: String): Int = if (category == "Mäso a ryby") 2 else THAWED_FRIDGE_DAYS

    fun typeOf(placeName: String): PlaceType {
        val n = Calc.norm(placeName)
        return when {
            n.contains("mraz") -> PlaceType.FREEZER
            n.contains("chlad") -> PlaceType.FRIDGE
            else -> PlaceType.ROOM
        }
    }

    fun freezerDays(category: String): Int = freezerDaysByCategory[category] ?: FREEZER_DEFAULT

    /**
     * Odhad trvanlivosti (dni) na mieste [to], ak [base] dní platí na mieste [from].
     * [base] <= 0 alebo null znamená neznámu hodnotu. Vráti null, ak sa odhad nedá urobiť.
     */
    fun days(base: Int?, from: PlaceType, to: PlaceType, category: String): Int? {
        val b = base?.takeIf { it > 0 }
        return when {
            from == to -> b
            to == PlaceType.FREEZER -> maxOf(b ?: 0, freezerDays(category))
            from == PlaceType.FREEZER && to == PlaceType.FRIDGE -> thawDays(category)
            from == PlaceType.FREEZER -> THAWED_ROOM_DAYS
            from == PlaceType.FRIDGE && to == PlaceType.ROOM -> b?.let { maxOf(1, it / 3) }
            else -> b // izba -> chladnička: nesľubujeme dlhšiu trvanlivosť, ako poznáme
        }
    }

    /**
     * Nová expirácia šarže pri presune z [from] na [to]. Zmrazenie predĺži expiráciu,
     * rozmrazenie a presun do tepla ju skrátia. Po expirácii sa nič nepredlžuje.
     */
    fun moveExpiry(old: Long?, today: Long, from: PlaceType, to: PlaceType, category: String): Long? {
        if (from == to) return old
        if (old != null && old < today) return old
        val remaining = old?.let { (it - today).toInt() }
        val d = days(remaining, from, to, category) ?: return old
        val candidate = today + d
        return when {
            to == PlaceType.FREEZER -> if (old == null) candidate else maxOf(old, candidate)
            from == PlaceType.FREEZER -> if (old == null) candidate else minOf(old, candidate)
            else -> if (old == null) null else minOf(old, candidate)
        }
    }

    /** Čitateľný popis dĺžky: "5 dní", "6 mesiacov", "2 roky". */
    fun describe(days: Int): String {
        if (days <= 45) return days.toString() + (if (days == 1) " deň" else " dní")
        if (days < 365) {
            val m = Math.round(days / 30.0).toInt().coerceAtLeast(2)
            return m.toString() + (if (m in 2..4) " mesiace" else " mesiacov")
        }
        val y = Math.round(days / 365.0).toInt().coerceAtLeast(1)
        return y.toString() + (if (y == 1) " rok" else if (y in 2..4) " roky" else " rokov")
    }
}
