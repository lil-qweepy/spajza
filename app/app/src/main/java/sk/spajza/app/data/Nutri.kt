package sk.spajza.app.data

/** Priemerne vyzivove hodnoty zakladnej potraviny na 100 g (alebo 100 ml) - len odhad, nie udaje z obalu. */
data class Estimate(
    val title: String,
    /** Druh potraviny (z Kinds); moze byt aj vlastny, napr. "Voda". */
    val kind: String,
    val hint: String,
    val kcal: Double,
    val protein: Double,
    val fat: Double,
    val satFat: Double,
    val carbs: Double,
    val sugar: Double,
    val fiber: Double,
    val salt: Double,
    /** Najbeznejsia jednotka pri kupe. */
    val uom: Uom = Uom.KG,
    /** Typicka hmotnost (ml) jedneho kusa/balenia, 0 = neznama. */
    val pieceG: Double = 0.0,
    val liquid: Boolean = false,
    val water: Boolean = false,
    /** Typicka trvanlivost v dnoch (0 = neznama). */
    val shelfDays: Int = 0,
    val place: String = "",
    val cat: String = ""
) {
    val category: String get() = cat.ifBlank { Kinds.categoryFor(kind) ?: "Iné" }

    fun summary(): String =
        Calc.num(kcal, 0) + " kcal · B " + Calc.num(protein, 1) + " · T " + Calc.num(fat, 1) +
            " · S " + Calc.num(carbs, 1) + " na 100 " + (if (liquid) "ml" else "g") + " · " + hint
}

object Nutri {
    private fun n(
        title: String, kind: String, hint: String,
        kcal: Double, p: Double, f: Double, sf: Double, c: Double, s: Double, fib: Double, salt: Double,
        uom: Uom = Uom.KG, piece: Double = 0.0, shelf: Int = 0, place: String = "",
        liquid: Boolean = false, water: Boolean = false, cat: String = ""
    ) = Estimate(
        title = title, kind = kind, hint = hint, kcal = kcal, protein = p, fat = f, satFat = sf,
        carbs = c, sugar = s, fiber = fib, salt = salt, uom = uom, pieceG = piece, liquid = liquid,
        water = water, shelfDays = shelf, place = place, cat = cat
    )

    val all: List<Estimate> = listOf(
        // zelenina a ovocie
        n("Cibuľa", "Cibuľa", "čerstvá", 40.0, 1.1, 0.1, 0.0, 9.3, 4.2, 1.7, 0.01, Uom.KG, 100.0, 30, "Pivnica"),
        n("Cesnak", "Cesnak", "čerstvý", 149.0, 6.4, 0.5, 0.1, 33.0, 1.0, 2.1, 0.02, Uom.PCS, 50.0, 60, "Špajza"),
        n("Zemiaky", "Zemiaky", "surové", 77.0, 2.0, 0.1, 0.0, 17.0, 0.8, 2.2, 0.01, Uom.KG, 150.0, 30, "Pivnica"),
        n("Mrkva", "Mrkva", "čerstvá", 41.0, 0.9, 0.2, 0.0, 9.6, 4.7, 2.8, 0.18, Uom.KG, 80.0, 21, "Chladnička"),
        n("Paradajky", "Paradajky", "čerstvé", 18.0, 0.9, 0.2, 0.0, 3.9, 2.6, 1.2, 0.01, Uom.KG, 120.0, 7, "Chladnička"),
        n("Paprika", "Paprika", "čerstvá", 27.0, 1.0, 0.3, 0.1, 6.0, 4.0, 2.0, 0.01, Uom.KG, 150.0, 7, "Chladnička"),
        n("Uhorka", "Uhorka", "čerstvá", 15.0, 0.7, 0.1, 0.0, 3.6, 1.7, 0.5, 0.01, Uom.PCS, 300.0, 7, "Chladnička"),
        n("Cuketa", "Cuketa", "čerstvá", 17.0, 1.2, 0.3, 0.1, 3.1, 2.5, 1.0, 0.02, Uom.PCS, 250.0, 7, "Chladnička"),
        n("Kapusta", "Kapusta", "čerstvá", 25.0, 1.3, 0.1, 0.0, 5.8, 3.2, 2.5, 0.02, Uom.PCS, 1000.0, 21, "Chladnička"),
        n("Špenát", "Špenát", "čerstvý", 23.0, 2.9, 0.4, 0.1, 3.6, 0.4, 2.2, 0.2, Uom.KG, 0.0, 3, "Chladnička"),
        n("Huby", "Huby", "šampiňóny", 22.0, 3.1, 0.3, 0.1, 3.3, 2.0, 1.0, 0.01, Uom.KG, 0.0, 5, "Chladnička"),
        n("Jablká", "Jablká", "čerstvé", 52.0, 0.3, 0.2, 0.0, 14.0, 10.0, 2.4, 0.0, Uom.KG, 180.0, 21, "Chladnička"),
        n("Banány", "Banány", "čerstvé", 89.0, 1.1, 0.3, 0.1, 23.0, 12.0, 2.6, 0.0, Uom.KG, 120.0, 5, "Kuchynská skrinka"),
        n("Citrón", "Citrón", "čerstvý", 29.0, 1.1, 0.3, 0.0, 9.3, 2.5, 2.8, 0.01, Uom.PCS, 100.0, 14, "Chladnička"),
        n("Zelenina", "Zelenina", "priemer (brokolica, karfiol…)", 35.0, 2.5, 0.3, 0.1, 6.0, 2.5, 3.0, 0.05, Uom.KG, 0.0, 5, "Chladnička"),
        n("Hrach", "Hrach", "zelený", 81.0, 5.4, 0.4, 0.1, 14.0, 6.0, 5.0, 0.01, Uom.KG),
        n("Kukurica", "Kukurica", "sladká, konzervovaná", 82.0, 2.5, 1.2, 0.2, 15.0, 5.0, 2.0, 0.4, Uom.PACK, 300.0),
        n("Fazuľa", "Fazuľa", "varená, konzervovaná", 90.0, 6.0, 0.4, 0.1, 14.0, 1.0, 5.0, 0.6, Uom.PACK, 400.0),
        n("Šošovica", "Šošovica", "suchá", 352.0, 24.6, 1.1, 0.2, 60.0, 2.0, 11.0, 0.02, Uom.KG, 0.0, 540, "Špajza"),
        // obilniny a sypke
        n("Múka", "Múka", "pšeničná", 364.0, 10.0, 1.0, 0.2, 76.0, 0.3, 2.7, 0.01, Uom.KG, 1000.0, 270, "Špajza"),
        n("Cestoviny", "Cestoviny", "suché", 355.0, 12.5, 1.5, 0.3, 71.0, 3.0, 3.0, 0.02, Uom.KG, 500.0, 540, "Špajza"),
        n("Ryža", "Ryža", "suchá", 350.0, 7.0, 0.6, 0.2, 78.0, 0.1, 1.3, 0.01, Uom.KG, 1000.0, 540, "Špajza"),
        n("Ovsené vločky", "Ovsené vločky", "suché", 372.0, 13.0, 7.0, 1.3, 58.0, 1.0, 10.0, 0.02, Uom.KG, 500.0, 270, "Špajza"),
        n("Müsli a cereálie", "Müsli a cereálie", "priemer", 375.0, 9.0, 6.0, 1.5, 65.0, 15.0, 8.0, 0.1, Uom.KG, 500.0, 270, "Špajza"),
        n("Cukor", "Cukor", "kryštálový", 400.0, 0.0, 0.0, 0.0, 100.0, 100.0, 0.0, 0.0, Uom.KG, 1000.0, 0, "Špajza"),
        // mliecne
        n("Vajcia", "Vajcia", "slepačie", 143.0, 12.6, 9.5, 3.1, 0.7, 0.4, 0.0, 0.35, Uom.PCS, 55.0, 21, "Chladnička"),
        n("Mlieko", "Mlieko", "polotučné 1,5 %", 46.0, 3.4, 1.5, 1.0, 4.8, 4.8, 0.0, 0.1, Uom.L, 1000.0, 7, "Chladnička", liquid = true),
        n("Smotana", "Smotana", "na varenie 12 %", 135.0, 2.9, 12.0, 8.0, 3.8, 3.8, 0.0, 0.1, Uom.PCS, 200.0, 7, "Chladnička", liquid = true),
        n("Syr", "Syr", "eidam 45 %", 330.0, 26.0, 25.0, 16.0, 0.5, 0.5, 0.0, 1.8, Uom.KG, 0.0, 21, "Chladnička"),
        n("Bryndza", "Bryndza", "ovčia", 250.0, 15.0, 20.0, 13.0, 1.0, 1.0, 0.0, 3.0, Uom.PCS, 125.0, 21, "Chladnička"),
        n("Maslo", "Maslo", "82 % tuku", 740.0, 0.7, 82.0, 52.0, 0.6, 0.6, 0.0, 0.03, Uom.PCS, 250.0, 60, "Chladnička"),
        n("Jogurt", "Jogurt", "biely", 60.0, 4.0, 3.2, 2.0, 4.5, 4.5, 0.0, 0.1, Uom.PCS, 150.0, 14, "Chladnička"),
        n("Tvaroh", "Tvaroh", "polotučný", 100.0, 12.0, 4.0, 2.5, 3.5, 3.5, 0.0, 0.1, Uom.PCS, 250.0, 7, "Chladnička"),
        // maso
        n("Kuracie mäso", "Kuracie mäso", "prsia, surové", 120.0, 22.5, 2.6, 0.6, 0.0, 0.0, 0.0, 0.15, Uom.KG, 0.0, 2, "Chladnička"),
        n("Hovädzie mäso", "Hovädzie mäso", "priemer, surové", 190.0, 20.0, 12.0, 5.0, 0.0, 0.0, 0.0, 0.15, Uom.KG, 0.0, 3, "Chladnička"),
        n("Bravčové mäso", "Bravčové mäso", "krkovička, surové", 230.0, 18.0, 17.0, 6.5, 0.0, 0.0, 0.0, 0.15, Uom.KG, 0.0, 3, "Chladnička"),
        n("Ryby", "Ryby", "priemer, surové", 150.0, 20.0, 7.0, 1.5, 0.0, 0.0, 0.0, 0.2, Uom.KG, 0.0, 2, "Chladnička"),
        n("Tuniak", "Tuniak", "konzerva vo vlastnej šťave", 115.0, 25.0, 1.0, 0.3, 0.0, 0.0, 0.0, 1.0, Uom.PACK, 140.0, 0, "Špajza"),
        n("Klobása", "Klobása", "klobása, saláma", 330.0, 16.0, 29.0, 11.0, 1.0, 0.5, 0.0, 3.0, Uom.KG, 0.0, 14, "Chladnička"),
        n("Slanina", "Slanina", "údená", 540.0, 12.0, 53.0, 19.0, 0.5, 0.0, 0.0, 3.0, Uom.KG, 0.0, 14, "Chladnička"),
        n("Šunka", "Šunka", "výberová", 110.0, 18.0, 3.5, 1.2, 1.0, 0.8, 0.0, 2.2, Uom.KG, 0.0, 7, "Chladnička"),
        // ostatne
        n("Chlieb a pečivo", "Chlieb a pečivo", "pšenično-ražný chlieb", 250.0, 8.0, 1.5, 0.3, 48.0, 3.0, 4.0, 1.2, Uom.PCS, 500.0, 4, "Kuchynská skrinka"),
        n("Med", "Med", "kvetový", 304.0, 0.3, 0.0, 0.0, 82.0, 82.0, 0.0, 0.0, Uom.PCS, 500.0, 0, "Špajza"),
        n("Džem", "Džem", "priemer", 250.0, 0.4, 0.1, 0.0, 60.0, 55.0, 1.0, 0.01, Uom.PCS, 300.0, 0, "Špajza"),
        n("Orechy", "Orechy", "zmes, priemer", 610.0, 18.0, 53.0, 7.0, 18.0, 4.0, 7.0, 0.02, Uom.KG, 0.0, 0, "Špajza"),
        n("Olej", "Olej", "repkový / slnečnicový", 900.0, 0.0, 100.0, 9.0, 0.0, 0.0, 0.0, 0.0, Uom.L, 1000.0, 365, "Špajza", liquid = true),
        n("Majonéza", "Majonéza", "klasická", 680.0, 1.2, 74.0, 6.0, 2.0, 1.5, 0.0, 1.3, Uom.PCS, 250.0, 0, "Chladnička"),
        n("Kečup", "Kečup", "klasický", 100.0, 1.2, 0.1, 0.0, 23.0, 21.0, 0.5, 2.0, Uom.PCS, 500.0, 0, "Špajza"),
        n("Horčica", "Horčica", "klasická", 100.0, 6.0, 5.0, 0.4, 8.0, 5.0, 3.0, 3.5, Uom.PCS, 200.0, 0, "Špajza"),
        n("Ocot", "Ocot", "kvasný", 20.0, 0.0, 0.0, 0.0, 0.5, 0.4, 0.0, 0.01, Uom.L, 500.0, 0, "Špajza", liquid = true),
        n("Rastlinný nápoj", "Rastlinný nápoj", "sójový", 40.0, 3.3, 1.8, 0.3, 2.5, 2.0, 0.5, 0.1, Uom.L, 1000.0, 0, "Špajza", liquid = true),
        n("Voda", "Voda", "pitná voda", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Uom.L, 1500.0, 0, "Špajza", liquid = true, water = true, cat = "Nápoje")
    )

    /** Odhad pre produkt: najprv podla zadaneho druhu, potom podla nazvu. */
    fun estimate(kind: String, text: String): Estimate? {
        if (kind.isNotBlank()) {
            val byKind = all.firstOrNull { it.kind == kind.trim() }
            if (byKind != null) return byKind
        }
        val t = Calc.norm(text)
        if (t.isBlank()) return null
        for (k in Kinds.of(text)) {
            val e = all.firstOrNull { it.kind == k }
            if (e != null) return e
        }
        if (t.split(Regex("[^a-z]+")).any { it == "voda" || it.startsWith("mineraln") }) {
            return all.firstOrNull { it.kind == "Voda" }
        }
        return null
    }

    /** Hladanie medzi zakladnymi potravinami (aj skloňované tvary, napr. "cibulu"). */
    fun search(q: String): List<Estimate> {
        val t = Calc.norm(q.trim())
        if (t.length < 2) return emptyList()
        val kinds = Kinds.of(q)
        return all.filter { e ->
            Calc.norm(e.title).contains(t) || Calc.norm(e.kind).contains(t) ||
                e.kind in kinds || Calc.norm(e.hint).contains(t)
        }
    }
}
