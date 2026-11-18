package sk.spajza.app.data

/** Surovina receptu a to, co k nej mame v zasobach. */
data class IngMatch(
    val ing: Ingredient,
    val stock: StockItem?,
    val all: List<StockItem> = emptyList(),
    val need: Need? = null,
    val enough: Boolean = true
) {
    val satisfied: Boolean get() = ing.basic || (stock != null && enough)
}

data class RecipeMatch(val recipe: Recipe, val matches: List<IngMatch>, val soonUsed: Int) {
    val needed: Int get() = matches.count { !it.ing.basic }
    val have: Int get() = matches.count { !it.ing.basic && it.satisfied }
    val missing: Int get() = needed - have
}

object RecipeEngine {

    /** Z textu surovinu vytvori hladane kluce (kmen slova bez diakritiky). */
    fun keysFor(name: String): List<String> {
        val keys = ArrayList<String>()
        for (w in Calc.norm(name).split(Regex("[^a-z0-9]+"))) {
            if (w.length < 3) continue
            val stem = when {
                w.length > 5 -> w.dropLast(2)
                w.length > 3 -> w.dropLast(1)
                else -> w
            }
            keys.add(stem)
        }
        return keys
    }

    fun match(recipes: List<Recipe>, stock: List<StockItem>, portions: Int? = null): List<RecipeMatch> {
        val today = Calc.today()
        val pantry = stock.filter { it.qty > 0 }.map { s ->
            val text = Calc.norm(s.item.name + " " + s.item.brand + " " + s.item.kind + " " + s.item.category)
            text to s
        }
        val result = recipes.map { r ->
            var soon = 0
            val ms = r.ingredients.map { ing ->
                if (ing.basic || ing.keys.isEmpty()) {
                    IngMatch(ing, null)
                } else {
                    val ingKinds = Kinds.of(ing.name)
                    val foundAll = pantry.filter { p ->
                        (ingKinds.isNotEmpty() && Kinds.forItem(p.second.item).any { it in ingKinds }) ||
                            ing.keys.any { k -> p.first.contains(k) }
                    }.map { it.second }.sortedBy { it.nearest ?: Long.MAX_VALUE }
                    val found = foundAll.firstOrNull()
                    if (found != null) {
                        val n = found.nearest
                        if (n != null && n - today <= 7) soon++
                    }
                    val factor = if (portions != null && r.servings > 0) portions.toDouble() / r.servings else 1.0
                    val need = Amounts.parse(ing.amount)?.let { Amounts.scale(it, factor) }
                    val enough = if (found == null || need == null) true else Amounts.enough(foundAll, need)
                    IngMatch(ing, found, foundAll, need, enough)
                }
            }
            RecipeMatch(r, ms, soon)
        }
        return result.sortedWith(
            compareBy<RecipeMatch> { it.missing }
                .thenByDescending { it.soonUsed }
                .thenBy { it.recipe.name }
        )
    }
}

object BuiltInRecipes {
    private fun i(name: String, amount: String, vararg keys: String): Ingredient =
        Ingredient(name, keys.toList(), amount, false)

    private fun b(name: String, amount: String = ""): Ingredient =
        Ingredient(name, emptyList(), amount, true)

    private val cestoviny = arrayOf("cestovin", "spagety", "penne", "fusilli", "makaron", "farfalle")
    private val cibula = arrayOf("cibul")
    private val zemiaky = arrayOf("zemiak")
    private val vajcia = arrayOf("vajc", "vajic")
    private val mliekoKeys = arrayOf("mliek")
    private val syrKeys = arrayOf("syr", "eidam", "edam", "gouda", "mozzarel", "parmezan")
    private val ryzaKeys = arrayOf("ryz")
    private val zelenina = arrayOf("zelenin", "hrasok", "brokolic", "karfiol", "mrkv")

    val all: List<Recipe> = listOf(
        Recipe(
            -1, "Cestoviny s paradajkovou omáčkou", 25, 2,
            listOf(
                i("Cestoviny", "200 g", *cestoviny),
                i("Paradajky alebo pretlak", "400 g", "paradajk", "pretlak", "passata"),
                i("Cibuľa", "1 ks", *cibula),
                i("Cesnak", "2 strúčiky", "cesnak"),
                b("Olej", "2 PL"), b("Soľ a korenie")
            ),
            listOf(
                "Cestoviny uvarte v osolenej vode podľa návodu.",
                "Na oleji speňte nadrobno nakrájanú cibuľu a cesnak.",
                "Pridajte paradajky, osoľte, okoreňte a povarte asi 10 minút.",
                "Cestoviny zmiešajte s omáčkou a ihneď podávajte."
            )
        ),
        Recipe(
            -2, "Cestoviny aglio e olio", 15, 2,
            listOf(
                i("Špagety alebo cestoviny", "200 g", *cestoviny),
                i("Cesnak", "4 strúčiky", "cesnak"),
                b("Olivový olej", "4 PL"), b("Soľ"), b("Chilli", "podľa chuti")
            ),
            listOf(
                "Cestoviny uvarte v osolenej vode, trochu vody z varenia si odložte.",
                "Cesnak nakrájajte na plátky a pomaly ho opečte na oleji dozlatista, nesmie zhorknúť.",
                "Pridajte scedené cestoviny, trochu vody z varenia a chilli a všetko premiešajte."
            )
        ),
        Recipe(
            -3, "Zapekané cestoviny so syrom", 35, 3,
            listOf(
                i("Cestoviny", "250 g", *cestoviny),
                i("Syr", "150 g", *syrKeys),
                i("Smotana alebo mlieko", "200 ml", "smotan", "mliek"),
                b("Soľ a korenie")
            ),
            listOf(
                "Rúru rozohrejte na 190 °C.",
                "Cestoviny uvarte skôr al dente a scedte ich.",
                "Zmiešajte ich so smotanou alebo mliekom, polovicou strúhaného syra, soľou a korením.",
                "Preložte do zapekacej misy, posypte zvyšným syrom a pečte asi 20 minút do zlatista."
            )
        ),
        Recipe(
            -4, "Tuniakový cestovinový šalát", 20, 2,
            listOf(
                i("Cestoviny", "150 g", *cestoviny),
                i("Tuniak v konzerve", "1 konzerva", "tuniak"),
                i("Kukurica", "100 g", "kukuric"),
                b("Olej alebo majonéza"), b("Soľ a korenie")
            ),
            listOf(
                "Cestoviny uvarte, scedte a nechajte vychladnúť.",
                "Pridajte scedený tuniak a kukuricu.",
                "Zmiešajte s olejom alebo majonézou, dochuťte soľou a korením."
            )
        ),
        Recipe(
            -5, "Omeleta", 10, 1,
            listOf(
                i("Vajcia", "3 ks", *vajcia),
                i("Mlieko", "50 ml", *mliekoKeys),
                b("Maslo alebo olej"), b("Soľ")
            ),
            listOf(
                "Vajcia rozšľahajte s mliekom a soľou.",
                "Na panvici rozohrejte maslo a vlejte zmes.",
                "Na miernom ohni nechajte zhustnúť, preložte a podávajte."
            )
        ),
        Recipe(
            -6, "Praženica s cibuľou", 15, 2,
            listOf(
                i("Vajcia", "4 ks", *vajcia),
                i("Cibuľa", "1 ks", *cibula),
                b("Olej alebo masť"), b("Soľ a korenie")
            ),
            listOf(
                "Nadrobno nakrájanú cibuľu speňte na oleji dozlatista.",
                "Pridajte rozšľahané vajcia so soľou a korením.",
                "Za stáleho miešania zrážajte do požadovanej hustoty."
            )
        ),
        Recipe(
            -7, "Hrianky s vajíčkom a syrom", 15, 2,
            listOf(
                i("Chlieb", "4 krajce", "chlieb", "chleb", "pecivo", "toast"),
                i("Vajcia", "2 ks", *vajcia),
                i("Syr", "100 g", *syrKeys),
                b("Maslo alebo olej"), b("Soľ")
            ),
            listOf(
                "Chlieb opečte na masle z oboch strán.",
                "Na každý krajec dajte opečené vajíčko.",
                "Posypte strúhaným syrom a krátko zapečte alebo prikryte, aby sa syr roztopil."
            )
        ),
        Recipe(
            -8, "Bryndzové halušky", 50, 4,
            listOf(
                i("Zemiaky", "800 g", *zemiaky),
                i("Hladká múka", "300 g", "muka", "muky"),
                i("Bryndza", "250 g", "bryndz"),
                i("Slanina", "100 g", "slanin"),
                b("Soľ")
            ),
            listOf(
                "Očistené zemiaky nastrúhajte najjemnejšie a zmiešajte s múkou a soľou na hustejšie cesto.",
                "Cesto nešúchajte na haluškovom strúhadle alebo z dosky priamo do vriacej osolenej vody.",
                "Keď halušky vyplávajú na povrch, povarte ich ešte krátko a scedte.",
                "Zmiešajte ich s bryndzou, posypte opečenou slaninou a ihneď podávajte."
            )
        ),
        Recipe(
            -9, "Zemiaková polievka", 40, 4,
            listOf(
                i("Zemiaky", "500 g", *zemiaky),
                i("Mrkva", "1 ks", "mrkv"),
                i("Cibuľa", "1 ks", *cibula),
                b("Olej alebo maslo"), b("Soľ, korenie, majorán"), b("Voda alebo vývar", "1,2 l")
            ),
            listOf(
                "Cibuľu speňte na oleji, pridajte nakrájanú mrkvu a zemiaky.",
                "Zalejte vodou alebo vývarom, osoľte a varte asi 25 minút do mäkka.",
                "Podľa chuti rozmixujte, dochuťte korením a majoránom."
            )
        ),
        Recipe(
            -10, "Zemiaková kaša s vajíčkom", 35, 2,
            listOf(
                i("Zemiaky", "600 g", *zemiaky),
                i("Mlieko", "100 ml", *mliekoKeys),
                i("Vajcia", "2 ks", *vajcia),
                b("Maslo"), b("Soľ")
            ),
            listOf(
                "Zemiaky ošúpte, nakrájajte a uvarte v osolenej vode do mäkka.",
                "Scedte ich a roztlačte s teplým mliekom a maslom.",
                "Vajcia usmažte na masle a podávajte na kaši."
            )
        ),
        Recipe(
            -11, "Zemiakové placky", 35, 3,
            listOf(
                i("Zemiaky", "600 g", *zemiaky),
                i("Hladká múka", "3 PL", "muka", "muky"),
                i("Vajcia", "1 ks", *vajcia),
                i("Cesnak", "2 strúčiky", "cesnak"),
                b("Olej na vyprážanie"), b("Soľ, majorán")
            ),
            listOf(
                "Zemiaky nastrúhajte a zľahka vytlačte tekutinu.",
                "Pridajte vajce, múku, pretlačený cesnak, soľ a majorán a zmiešajte.",
                "Placky smažte na rozohriatom oleji z oboch strán dozlatista."
            )
        ),
        Recipe(
            -12, "Cesnaková polievka s krutónmi", 25, 3,
            listOf(
                i("Cesnak", "5 strúčikov", "cesnak"),
                i("Zemiaky", "2 ks", *zemiaky),
                i("Chlieb na krutóny", "2 krajce", "chlieb", "chleb", "pecivo"),
                i("Syr", "50 g", *syrKeys),
                b("Voda alebo vývar", "1 l"), b("Soľ, korenie, rasca")
            ),
            listOf(
                "Zemiaky nakrájajte na kocky a varte vo vývare asi 15 minút.",
                "Pridajte pretlačený cesnak, soľ, korenie a rascu a povarte ešte 5 minút.",
                "Krutóny opečte na panvici a polievku podávajte s nimi a strúhaným syrom."
            )
        ),
        Recipe(
            -13, "Šošovicová polievka", 45, 4,
            listOf(
                i("Šošovica", "200 g", "sosovic"),
                i("Mrkva", "1 ks", "mrkv"),
                i("Cibuľa", "1 ks", *cibula),
                i("Cesnak", "2 strúčiky", "cesnak"),
                b("Olej"), b("Soľ, korenie, kmín"), b("Voda alebo vývar", "1,5 l")
            ),
            listOf(
                "Na oleji speňte cibuľu, cesnak a nastrúhanú mrkvu.",
                "Pridajte prepláchnutú šošovicu, zalejte vodou alebo vývarom.",
                "Varte asi 30 minút do mäkka, dochuťte soľou, korením a kmínom."
            )
        ),
        Recipe(
            -14, "Fazuľová polievka", 40, 4,
            listOf(
                i("Fazuľa", "400 g (konzerva)", "fazul"),
                i("Cibuľa", "1 ks", *cibula),
                i("Klobása", "100 g", "klobas", "salam", "parok"),
                b("Olej"), b("Soľ, korenie, sladká paprika"), b("Voda alebo vývar", "1 l")
            ),
            listOf(
                "Na oleji speňte cibuľu, pridajte nakrájanú klobásu a papriku.",
                "Pridajte fazuľu aj s nálevom a zalejte vývarom.",
                "Varte asi 20 minút a dochuťte soľou a korením."
            )
        ),
        Recipe(
            -15, "Hrachová polievka", 50, 4,
            listOf(
                i("Hrach", "200 g", "hrach"),
                i("Mrkva", "1 ks", "mrkv"),
                i("Cibuľa", "1 ks", *cibula),
                i("Zemiaky", "1 ks", *zemiaky),
                b("Soľ, korenie"), b("Voda alebo vývar", "1,5 l")
            ),
            listOf(
                "Hrach nechajte aspoň 2 hodiny namočený.",
                "Varte ho s nakrájanou zeleninou a zemiakom asi 40 minút do mäkka.",
                "Podľa chuti rozmixujte a dochuťte soľou a korením."
            )
        ),
        Recipe(
            -16, "Rizoto so zeleninou", 35, 3,
            listOf(
                i("Ryža", "200 g", *ryzaKeys),
                i("Zelenina (mrazená alebo čerstvá)", "300 g", *zelenina),
                i("Cibuľa", "1 ks", *cibula),
                b("Olej alebo maslo"), b("Soľ, korenie"), b("Vývar alebo voda", "500 ml")
            ),
            listOf(
                "Cibuľu speňte na oleji, pridajte ryžu a krátko ju opražte.",
                "Prilievajte horúci vývar po lyžiciach a občas premiešajte.",
                "V polovici varenia pridajte zeleninu a varte dokiaľ nie je ryža mäkká.",
                "Dochuťte soľou a korením."
            )
        ),
        Recipe(
            -17, "Kuracie mäso so zeleninou a ryžou", 40, 3,
            listOf(
                i("Kuracie mäso", "400 g", "kurac", "kuraci", "kurcat"),
                i("Zelenina", "300 g", *zelenina),
                i("Ryža", "200 g", *ryzaKeys),
                b("Olej"), b("Soľ, korenie, paprika")
            ),
            listOf(
                "Ryžu uvarte podľa návodu.",
                "Kuracie mäso nakrájajte na kúsky, ochuťte a opečte na oleji.",
                "Pridajte zeleninu, podlejte trochou vody a duste do mäkka.",
                "Podávajte s ryžou."
            )
        ),
        Recipe(
            -18, "Lečo", 35, 3,
            listOf(
                i("Paprika", "3 ks", "paprik"),
                i("Paradajky", "3 ks", "paradajk"),
                i("Cibuľa", "1 ks", *cibula),
                b("Olej"), b("Soľ, korenie, sladká paprika")
            ),
            listOf(
                "Cibuľu speňte na oleji, pridajte nakrájanú papriku a duste asi 10 minút.",
                "Pridajte nakrájané paradajky, osoľte, okoreňte a duste ďalších 15 minút.",
                "Podľa chuti pridajte opekanú klobásu alebo vajíčko."
            )
        ),
        Recipe(
            -19, "Palacinky", 30, 3,
            listOf(
                i("Hladká múka", "200 g", "muka", "muky"),
                i("Mlieko", "400 ml", *mliekoKeys),
                i("Vajcia", "2 ks", *vajcia),
                b("Olej na vyprážanie"), b("Cukor"), b("Štipka soli")
            ),
            listOf(
                "Vyšľahajte múku, mlieko, vajcia, štipku soli a lyžicu cukru na hladké cesto.",
                "Nechajte ho 10 minút odpočinúť.",
                "Palacinky smažte na tenko vytretej panvici z oboch strán dozlatista.",
                "Plňte džemom, tvarohom alebo ovocím."
            )
        ),
        Recipe(
            -20, "Ovsená kaša", 10, 1,
            listOf(
                i("Ovsené vločky", "80 g", "vlock", "ovsen"),
                i("Mlieko", "250 ml", *mliekoKeys),
                b("Med alebo cukor"), b("Štipka soli")
            ),
            listOf(
                "Vločky zalejte mliekom, pridajte štipku soli a za stáleho miešania varte 5 minút.",
                "Osladte medom alebo cukrom, pridajte ovocie alebo orechy podľa chuti."
            )
        ),
        Recipe(
            -21, "Mliečna ryža", 35, 3,
            listOf(
                i("Ryža", "150 g", *ryzaKeys),
                i("Mlieko", "600 ml", *mliekoKeys),
                b("Cukor"), b("Škorica alebo vanilka")
            ),
            listOf(
                "Mlieko privedte do varu, pridajte ryžu a na miernom ohni varte asi 25 minút za občasného miešania.",
                "Osladte, posypte škoricou a podávajte teplú."
            )
        )
    )
}
