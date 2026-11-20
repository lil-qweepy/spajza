package sk.spajza.app.data

/** Druhy potravin: podla nich recepty rozpoznaju, ze napr. "Polohrubá" je múka. */
object Kinds {
    private class K(val name: String, val keys: List<String>, val not: List<String> = emptyList())

    private val all = listOf(
        K("Múka", listOf("muk", "polohrub", "hrubozrnn")),
        K("Cestoviny", listOf("cestovin", "spagety", "spaghetti", "penne", "fusilli", "makaron", "farfalle", "tagliatelle", "rezanc", "vretien", "kolienk")),
        K("Ryža", listOf("ryz", "basmati", "jazmin")),
        K("Zemiaky", listOf("zemiak")),
        K("Cibuľa", listOf("cibul")),
        K("Cesnak", listOf("cesnak")),
        K("Vajcia", listOf("vajc", "vajic")),
        K("Mlieko", listOf("mliek"), listOf("cokolad", "kakao", "susienk", "oblatk", "tycink", "karamel", "rezy")),
        K("Smotana", listOf("smotan")),
        K("Syr", listOf("syr", "eidam", "edam", "gouda", "mozzarel", "parmezan", "niva", "encian"), listOf("syrov")),
        K("Bryndza", listOf("bryndz")),
        K("Maslo", listOf("maslo", "margarin"), listOf("arašid", "arasid")),
        K("Olej", listOf("olej", "olivov")),
        K("Cukor", listOf("cukor"), listOf("bez cukru")),
        K("Paradajky", listOf("paradajk", "pretlak", "passata", "rajcin")),
        K("Paprika", listOf("paprik")),
        K("Mrkva", listOf("mrkv")),
        K("Zelenina", listOf("zelenin", "brokolic", "karfiol", "hrasok")),
        K("Hrach", listOf("hrach", "hrasok")),
        K("Šošovica", listOf("sosovic")),
        K("Fazuľa", listOf("fazul")),
        K("Kukurica", listOf("kukuric")),
        K("Tuniak", listOf("tuniak")),
        K("Kuracie mäso", listOf("kurac", "kuraci", "kurcat")),
        K("Klobása", listOf("klobas", "salam", "parok")),
        K("Slanina", listOf("slanin")),
        K("Chlieb a pečivo", listOf("chlieb", "chleb", "pecivo", "toast", "rozok", "baget")),
        K("Ovsené vločky", listOf("vlock", "ovsen")),
        K("Jogurt", listOf("jogurt", "yogurt")),
        K("Tvaroh", listOf("tvaroh")),
        K("Šunka", listOf("sunk")),
        K("Hovädzie mäso", listOf("hovadz", "mlete", "mleta")),
        K("Bravčové mäso", listOf("bravcov", "krkovic", "kotlet")),
        K("Ryby", listOf("losos", "ryba", "ryby", "sardink", "makrel", "treska")),
        K("Cuketa", listOf("cuket")),
        K("Uhorka", listOf("uhork")),
        K("Kapusta", listOf("kapust")),
        K("Špenát", listOf("spenat")),
        K("Huby", listOf("hrib", "sampin", "huby", "hliv")),
        K("Jablká", listOf("jabl")),
        K("Banány", listOf("banan")),
        K("Citrón", listOf("citron", "limet")),
        K("Med", listOf("med ", "medik", "medov"), listOf("medved", "medzi")),
        K("Džem", listOf("dzem", "marmelad", "lekvar")),
        K("Kakao a čokoláda", listOf("kakao", "cokolad")),
        K("Orechy", listOf("orech", "mandl", "lieskov", "kesu", "arasid")),
        K("Droždie", listOf("drozdi")),
        K("Prášok do pečiva", listOf("prasok do pecen", "sodu", "vanilk")),
        K("Majonéza", listOf("majonez")),
        K("Kečup", listOf("kecup", "ketchup")),
        K("Horčica", listOf("horcic")),
        K("Ocot", listOf("ocot")),
        K("Korenie", listOf("korenie", "paprika mlet", "kmin", "majoran", "oregano", "bazalk", "rasca")),
        K("Vývar", listOf("vyvar", "bujon", "kocka")),
        K("Rastlinný nápoj", listOf("sojov", "ovsen napoj", "mandlov napoj")),
        K("Müsli a cereálie", listOf("musli", "cerealie", "kukuricne platky"))
    )

    val names: List<String> get() = all.map { it.name }

    private val categoryOf: Map<String, String> = mapOf(
        "Múka" to "Cestoviny, ryža a obilniny", "Cestoviny" to "Cestoviny, ryža a obilniny",
        "Ryža" to "Cestoviny, ryža a obilniny", "Šošovica" to "Cestoviny, ryža a obilniny",
        "Fazuľa" to "Cestoviny, ryža a obilniny", "Ovsené vločky" to "Cestoviny, ryža a obilniny",
        "Müsli a cereálie" to "Cestoviny, ryža a obilniny",
        "Zemiaky" to "Ovocie a zelenina", "Cibuľa" to "Ovocie a zelenina", "Cesnak" to "Ovocie a zelenina",
        "Paradajky" to "Ovocie a zelenina", "Paprika" to "Ovocie a zelenina", "Mrkva" to "Ovocie a zelenina",
        "Zelenina" to "Ovocie a zelenina", "Cuketa" to "Ovocie a zelenina", "Uhorka" to "Ovocie a zelenina",
        "Kapusta" to "Ovocie a zelenina", "Špenát" to "Ovocie a zelenina", "Huby" to "Ovocie a zelenina",
        "Jablká" to "Ovocie a zelenina", "Banány" to "Ovocie a zelenina", "Citrón" to "Ovocie a zelenina",
        "Hrach" to "Ovocie a zelenina", "Kukurica" to "Ovocie a zelenina",
        "Vajcia" to "Mliečne výrobky", "Mlieko" to "Mliečne výrobky", "Smotana" to "Mliečne výrobky",
        "Syr" to "Mliečne výrobky", "Bryndza" to "Mliečne výrobky", "Maslo" to "Mliečne výrobky",
        "Jogurt" to "Mliečne výrobky", "Tvaroh" to "Mliečne výrobky",
        "Tuniak" to "Mäso a ryby", "Kuracie mäso" to "Mäso a ryby", "Klobása" to "Mäso a ryby",
        "Slanina" to "Mäso a ryby", "Šunka" to "Mäso a ryby", "Hovädzie mäso" to "Mäso a ryby",
        "Bravčové mäso" to "Mäso a ryby", "Ryby" to "Mäso a ryby",
        "Chlieb a pečivo" to "Pečivo",
        "Cukor" to "Sladkosti a snacky", "Med" to "Sladkosti a snacky", "Džem" to "Sladkosti a snacky",
        "Kakao a čokoláda" to "Sladkosti a snacky", "Orechy" to "Sladkosti a snacky",
        "Olej" to "Omáčky a korenie", "Majonéza" to "Omáčky a korenie", "Kečup" to "Omáčky a korenie",
        "Horčica" to "Omáčky a korenie", "Ocot" to "Omáčky a korenie", "Korenie" to "Omáčky a korenie",
        "Vývar" to "Omáčky a korenie", "Droždie" to "Omáčky a korenie", "Prášok do pečiva" to "Omáčky a korenie",
        "Rastlinný nápoj" to "Nápoje"
    )

    private val liquidKinds = setOf("Mlieko", "Smotana", "Olej", "Ocot", "Rastlinný nápoj")
    private val liquidWords = listOf(
        "voda", "vody", "vodu", "napoj", "dzus", "juice", "pivo", "vino", "limonad", "mineraln",
        "sirup", "kofola", "cola", "tonic", "smoothie", "nektar", "energy drink"
    )

    /** Je potravina tekutina (merame ju v ml/l), alebo tuha (g/kg)? */
    fun isLiquid(text: String, category: String = ""): Boolean {
        if (category == "Nápoje") return true
        val t = Calc.norm(text)
        if (t.isBlank()) return false
        if (liquidWords.any { t.contains(it) }) return true
        return of(text).any { it in liquidKinds }
    }

    /** Jednotka zodpovedajuca skupenstvu: tuhe -> g/kg, tekute -> ml/l, ks a balenie ostavaju. */
    fun fitUom(u: Uom, liquid: Boolean): Uom = when {
        liquid && u == Uom.G -> Uom.ML
        liquid && u == Uom.KG -> Uom.L
        !liquid && u == Uom.ML -> Uom.G
        !liquid && u == Uom.L -> Uom.KG
        else -> u
    }

    /** Jednotky, ktore ma zmysel ponuknut pre dane skupenstvo. */
    fun uomsFor(liquid: Boolean): List<Uom> =
        Uom.entries.filter { it.isCountable || (if (liquid) it.isVolume else it.isMass) }

    /** Kategoria pre dany druh potraviny (null, ak druh nepoznáme). */
    fun categoryFor(kind: String): String? = categoryOf[kind.trim()]

    /** Kategoria odhadnuta z nazvu a znacky (null, ak sa druh nerozpozna). */
    fun categoryGuess(text: String): String? = of(text).firstNotNullOfOrNull { categoryOf[it] }

    /** Vsetky druhy, ktore zodpovedaju textu (nazov, znacka...). */
    fun of(text: String): Set<String> {
        val t = Calc.norm(text)
        if (t.isBlank()) return emptySet()
        return all.filter { k -> k.keys.any { t.contains(it) } && k.not.none { t.contains(Calc.norm(it)) } }
            .map { it.name }.toSet()
    }

    fun guess(text: String): String = of(text).firstOrNull() ?: ""

    private val openDaysByKind: Map<String, Int> = mapOf(
        "Mlieko" to 3, "Smotana" to 3, "Jogurt" to 4, "Tvaroh" to 3, "Syr" to 7, "Bryndza" to 7, "Maslo" to 30,
        "Šunka" to 3, "Klobása" to 5, "Slanina" to 7, "Kuracie mäso" to 1, "Hovädzie mäso" to 1,
        "Bravčové mäso" to 1, "Ryby" to 1, "Tuniak" to 2, "Majonéza" to 30, "Kečup" to 60, "Horčica" to 60,
        "Džem" to 30, "Med" to 365, "Vývar" to 4, "Rastlinný nápoj" to 5, "Paradajky" to 3, "Kukurica" to 3,
        "Hrach" to 3, "Fazuľa" to 3, "Olej" to 180, "Ocot" to 365, "Chlieb a pečivo" to 4, "Huby" to 3,
        "Špenát" to 2, "Müsli a cereálie" to 60, "Múka" to 180, "Cestoviny" to 180, "Ryža" to 180, "Cukor" to 365
    )

    private val openDaysByCategory: Map<String, Int> = mapOf(
        "Mliečne výrobky" to 5, "Mäso a ryby" to 2, "Konzervy" to 3, "Nápoje" to 5, "Omáčky a korenie" to 30,
        "Pečivo" to 4, "Mrazené" to 30, "Ovocie a zelenina" to 3, "Sladkosti a snacky" to 14,
        "Cestoviny, ryža a obilniny" to 90
    )

    /** Na kolko dni sa skrati trvanlivost po otvoreni (vlastna hodnota produktu ma prednost pred odhadom). */
    fun openDays(i: Item): Int {
        if (i.openDays > 0) return i.openDays
        val byKind = forItem(i).mapNotNull { openDaysByKind[it] }.minOrNull()
        if (byKind != null) return byKind
        return openDaysByCategory[i.category] ?: 5
    }

    /** Druhy produktu: zadany druh ma prednost pred odhadom z nazvu. */
    fun forItem(i: Item): Set<String> {
        if (i.kind.isNotBlank()) return of(i.kind) + i.kind.trim()
        return of(i.name + " " + i.brand)
    }
}
