package sk.spajza.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sk.spajza.app.data.BuiltInRecipes
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Amounts
import sk.spajza.app.data.IngMatch
import sk.spajza.app.data.Ingredient
import sk.spajza.app.data.Need
import sk.spajza.app.data.MoveType
import sk.spajza.app.data.Recipe
import sk.spajza.app.data.RecipeEngine
import sk.spajza.app.data.RecipeMatch
import sk.spajza.app.data.ShopItem
import sk.spajza.app.data.StockItem
import sk.spajza.app.data.Uom
import sk.spajza.app.data.stock

private val RFILTERS = listOf("Môžem uvariť", "Chýba 1–2", "Všetky", "Moje")

@Composable
fun RecipesScreen() {
    val nav = LocalNav.current
    val db = useDb()
    var filter by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }

    val stock = remember(db) { db.stock().filter { it.qty > 0 } }
    val matches = remember(db) { RecipeEngine.match(BuiltInRecipes.all + db.recipes, stock) }
    val list = matches.filter { m ->
        val okFilter = when (filter) {
            0 -> m.missing == 0 && m.needed > 0
            1 -> m.missing in 1..2
            3 -> m.recipe.id > 0
            else -> true
        }
        okFilter && (query.isBlank() || Calc.norm(m.recipe.name).contains(Calc.norm(query.trim())))
    }

    ScreenScaffold(
        title = "Recepty",
        floating = {
            ExtendedFloatingActionButton(
                onClick = { nav.push(Dest.RecipeEdit(0)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Vlastný recept") }
            )
        }
    ) { pad ->
        PageList(pad) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchField(query, { query = it }, Modifier.fillMaxWidth(), "Hľadať recept")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(RFILTERS.size) { idx ->
                            FilterChip(selected = filter == idx, onClick = { filter = idx }, label = { Text(RFILTERS[idx]) })
                        }
                    }
                    if (stock.isEmpty()) Muted("Recepty sa prispôsobia tomu, čo máte v zásobách. Zatiaľ nemáte nič naskladnené.")
                }
            }
            item {
                SCard(onClick = { nav.push(Dest.Cook) }) {
                    Text("Navarili ste niečo vlastné?", fontWeight = FontWeight.SemiBold)
                    Muted("Rýchlo odpočítajte použité suroviny zo zásob, aj bez receptu.")
                }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        "Nič nevyhovuje",
                        if (filter == 0) "Skúste „Chýba 1–2“ alebo „Všetky“." else ""
                    )
                }
            }
            items(list.size) { idx ->
                val m = list[idx]
                SCard(onClick = { nav.push(Dest.RecipeDetail(m.recipe.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.recipe.name, fontWeight = FontWeight.SemiBold)
                            Muted(m.recipe.minutes.toString() + " min · " + m.recipe.servings + " " +
                                daysText(m.recipe.servings.toDouble(), "porcia", "porcie", "porcií") +
                                (if (m.soonUsed > 0) " · využije blížiacu sa expiráciu" else ""))
                        }
                        if (m.needed > 0) {
                            if (m.missing == 0) Pill("máte všetko", Pal.Green)
                            else Pill("chýba " + m.missing, if (m.missing <= 2) Pal.Amber else Pal.Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecipeDetailScreen(id: Long) {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    var cookDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }
    var assign by remember { mutableStateOf<Ingredient?>(null) }
    var portions by remember(id) { mutableStateOf<Int?>(null) }

    val recipe = (BuiltInRecipes.all + db.recipes).firstOrNull { it.id == id }
    if (recipe == null) {
        ScreenScaffold("Recept", onBack = { nav.pop() }) { pad -> PageList(pad) { item { EmptyState("Recept sa nenašiel") } } }
        return
    }
    val servings = portions ?: recipe.servings
    val stock = remember(db) { db.stock().filter { it.qty > 0 } }
    val m: RecipeMatch = remember(db, id, servings) { RecipeEngine.match(listOf(recipe), stock, servings).first() }
    val missing = m.matches.filter { !it.ing.basic && !it.satisfied }

    ScreenScaffold(
        title = recipe.name,
        onBack = { nav.pop() },
        actions = {
            IconButton(onClick = { nav.push(Dest.RecipeEdit(recipe.id)) }) { Icon(Icons.Filled.Edit, contentDescription = if (recipe.id > 0) "Upraviť" else "Upraviť kópiu") }
            if (recipe.id > 0) {
                IconButton(onClick = { deleteDialog = true }) { Icon(Icons.Filled.Delete, contentDescription = "Zmazať") }
            }
        }
    ) { pad ->
        PageList(pad) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Muted(recipe.minutes.toString() + " min")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Porcie: ")
                        OutlinedButton(enabled = servings > 1, onClick = { portions = servings - 1 }) { Text("−") }
                        Text(servings.toString(), Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { portions = servings + 1 }) { Text("+") }
                    }
                }
            }
            item { SectionTitle("Suroviny") }
            items(m.matches.size) { idx ->
                val im = m.matches[idx]
                SCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val color = when {
                            im.ing.basic -> Pal.Muted
                            im.stock == null -> Pal.Red
                            !im.enough -> Pal.Amber
                            else -> Pal.Green
                        }
                        Dot(color)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            val shown = if (im.need != null) Amounts.format(im.need) else im.ing.amount
                            Text(im.ing.name + (if (shown.isNotBlank()) " – " + shown else ""))
                            val s = im.stock
                            if (s != null) {
                                Muted("Doma: " + s.item.name + " (" + Calc.qty(s.qty, s.item.uom) + ")")
                                val need = im.need
                                if (!im.enough && need != null) {
                                    val av = Amounts.available(im.all, need.unit) ?: 0.0
                                    Text("Máte len " + Amounts.format(Need(av, need.unit)) + ", chýba " + Amounts.format(Need(need.amount - av, need.unit)),
                                        color = Pal.Amber, style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (im.ing.basic) Muted("bežná surovina")
                            else Muted("nemáte doma")
                        }
                        if (im.stock == null && !im.ing.basic) {
                            TextButton(onClick = { assign = im.ing }) { Text("Mám doma") }
                        }
                    }
                }
            }
            item { SectionTitle("Postup") }
            items(recipe.steps.size) { idx ->
                Row {
                    Text((idx + 1).toString() + ".", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(recipe.steps[idx], modifier = Modifier.padding(start = 10.dp))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(6.dp))
                    Button(
                        enabled = m.matches.any { it.stock != null }, modifier = Modifier.fillMaxWidth(),
                        onClick = { cookDialog = true }
                    ) { Text("Uvarené – odpočítať zo zásob") }
                    OutlinedButton(
                        enabled = missing.isNotEmpty(), modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            for (x in missing) {
                                if (db.shop.none { s -> s.name.equals(x.ing.name, ignoreCase = true) && !s.checked }) {
                                    val need = x.need
                                    if (need != null) {
                                        val have = if (x.stock != null) (Amounts.available(x.all, need.unit) ?: 0.0) else 0.0
                                        val uom = when (need.unit) { "g" -> Uom.G; "ml" -> Uom.ML; else -> Uom.PCS }
                                        store.saveShop(ShopItem(name = x.ing.name, qty = Math.max(need.amount - have, 1.0), uom = uom))
                                    } else {
                                        store.saveShop(ShopItem(name = x.ing.name))
                                    }
                                }
                            }
                            info = "Chýbajúce suroviny (" + missing.size + ") sú v nákupnom zozname."
                        }
                    ) { Text("Chýbajúce do nákupného zoznamu") }
                    if (info.isNotBlank()) Muted(info)
                }
            }
        }
    }

    val asg = assign
    if (asg != null) {
        AssignDialog(asg, onDismiss = { assign = null })
    }
    if (cookDialog) {
        CookDialog(m, recipe.name, onDismiss = { cookDialog = false })
    }
    if (deleteDialog) {
        ConfirmDialog(
            "Zmazať recept?", "Recept \"" + recipe.name + "\" sa zmaže.", "Zmazať",
            onConfirm = { store.deleteRecipe(recipe.id); nav.pop() }, onDismiss = { deleteDialog = false }
        )
    }
}

private fun defaultUse(s: StockItem): String = when (s.item.uom) {
    Uom.KG -> "0,5"
    Uom.G -> "200"
    Uom.L -> "0,5"
    Uom.ML -> "200"
    else -> "1"
}

@Composable
private fun CookDialog(m: RecipeMatch, recipeName: String, onDismiss: () -> Unit) {
    val store = LocalStore.current
    val used = m.matches.filter { it.stock != null && !it.ing.basic }
    val checked = remember { mutableStateMapOf<Int, Boolean>() }
    val qtys = remember { mutableStateMapOf<Int, String>() }

    fun initial(im: IngMatch): String {
        val s = im.stock ?: return "1"
        val need = im.need
        if (need != null) {
            val q = Amounts.itemQty(s.item, need)
            if (q != null) return Calc.edit(Math.round(q * 1000) / 1000.0)
        }
        return defaultUse(s)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Čo ste spotrebovali?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Muted("Množstvá podľa receptu môžete upraviť. Berie sa najprv z najbližšej expirácie.")
                used.forEachIndexed { idx, im ->
                    val s = im.stock
                    if (s != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked[idx] ?: true, onCheckedChange = { checked[idx] = it })
                            Column(Modifier.weight(1f)) {
                                Text(s.item.name, maxLines = 1)
                                Muted("Na sklade " + Calc.qty(s.qty, s.item.uom))
                            }
                            NumField("", qtys[idx] ?: initial(im), { qtys[idx] = it }, Modifier.weight(0.7f), suffix = s.item.uom.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                used.forEachIndexed { idx, im ->
                    val primary = im.stock
                    if (primary != null && (checked[idx] ?: true)) {
                        var left = Calc.parse(qtys[idx] ?: initial(im)) ?: 0.0
                        for (s in im.all) {
                            if (left <= 1e-9) break
                            if (s.item.uom != primary.item.uom) continue
                            val t = minOf(left, s.qty)
                            if (t > 0) store.withdraw(s.item.id, t, MoveType.CONSUME, null, "Recept: $recipeName")
                            left -= t
                        }
                    }
                }
                onDismiss()
            }) { Text("Odpočítať") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

@Composable
fun RecipeEditScreen(id: Long) {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    val existing = db.recipes.firstOrNull { it.id == id }
    val source: Recipe? = existing ?: BuiltInRecipes.all.firstOrNull { it.id == id }

    var name by remember { mutableStateOf(if (existing == null && source != null) source.name + " (moja verzia)" else (source?.name ?: "")) }
    var minutes by remember { mutableStateOf((source?.minutes ?: 30).toString()) }
    var servings by remember { mutableStateOf((source?.servings ?: 2).toString()) }
    var ings by remember {
        mutableStateOf(
            source?.ingredients?.joinToString("\n") { i ->
                (if (i.basic) "*" else "") + i.name + (if (i.amount.isNotBlank()) " - " + i.amount else "")
            } ?: ""
        )
    }
    var steps by remember { mutableStateOf(source?.steps?.joinToString("\n") ?: "") }
    var error by remember { mutableStateOf("") }

    fun save() {
        if (name.isBlank()) {
            error = "Zadajte názov receptu."
            return
        }
        val parsed = ArrayList<Ingredient>()
        for (raw in ings.lines()) {
            var line = raw.trim()
            if (line.isEmpty()) continue
            val basic = line.startsWith("*")
            if (basic) line = line.removePrefix("*").trim()
            val parts = line.split(Regex("\\s[-–]\\s"), limit = 2)
            val n = parts[0].trim()
            val a = if (parts.size > 1) parts[1].trim() else ""
            parsed.add(Ingredient(n, if (basic) emptyList() else RecipeEngine.keysFor(n), a, basic))
        }
        if (parsed.isEmpty()) {
            error = "Pridajte aspoň jednu surovinu."
            return
        }
        val st = steps.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val r = Recipe(
            id = existing?.id ?: 0, name = name.trim(), minutes = minutes.toIntOrNull() ?: 30,
            servings = servings.toIntOrNull() ?: 2, ingredients = parsed, steps = st
        )
        val newId = store.saveRecipe(r)
        if (existing == null) nav.replace(Dest.RecipeDetail(newId)) else nav.pop()
    }

    ScreenScaffold(title = if (existing == null && source == null) "Nový recept" else if (existing == null) "Moja verzia receptu" else "Upraviť recept", onBack = { nav.pop() }) { pad ->
        FormPage(pad) {
            TextField2("Názov", name, { name = it }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Minút", minutes, { minutes = it.filter { c -> c.isDigit() } }, Modifier.weight(1f))
                NumField("Porcií", servings, { servings = it.filter { c -> c.isDigit() } }, Modifier.weight(1f))
            }
            TextField2("Suroviny (každá na nový riadok)", ings, { ings = it }, Modifier.fillMaxWidth(), singleLine = false)
            Muted("Formát: „Cestoviny - 200 g“. Surovinu, ktorú máte vždy doma (soľ, olej), začnite hviezdičkou: „*Soľ“.")
            TextField2("Postup (každý krok na nový riadok)", steps, { steps = it }, Modifier.fillMaxWidth(), singleLine = false)
            if (error.isNotBlank()) Text(error, color = Pal.Red)
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Uložiť") }
        }
    }
}

@Composable
private fun AssignDialog(ing: Ingredient, onDismiss: () -> Unit) {
    val store = LocalStore.current
    val nav = LocalNav.current
    val db = useDb()
    var q by remember { mutableStateOf("") }
    val kindName = ing.name.split(Regex("\\s+alebo\\s+|\\s*\\(|,"))[0].trim()
    val list = db.items.filter { q.isBlank() || Calc.norm(it.name + " " + it.brand).contains(Calc.norm(q.trim())) }.take(30)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ktorý produkt je \"" + kindName + "\"?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Muted("Vyberte produkt, ktorý máte v zásobách. Aplikácia si to zapamätá a nabudúce ho v receptoch použije ako túto surovinu.")
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onDismiss()
                        nav.push(Dest.ItemEdit(0, name = kindName, kind = kindName))
                    }
                ) { Text("Pridať nový produkt: " + kindName) }
                SearchField(q, { q = it }, Modifier.fillMaxWidth(), "Hľadať produkt v zásobách")
                if (list.isEmpty()) Muted("V zásobách sa nič nenašlo. Pridajte nový produkt tlačidlom vyššie.")
                for (it0 in list) {
                    TextButton(onClick = {
                        store.saveItem(it0.copy(kind = kindName))
                        onDismiss()
                    }) { Text(it0.name + (if (it0.brand.isNotBlank()) " (" + it0.brand + ")" else "")) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}
