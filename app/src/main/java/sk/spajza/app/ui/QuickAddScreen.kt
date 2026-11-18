package sk.spajza.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Db
import sk.spajza.app.data.Estimate
import sk.spajza.app.data.Item
import sk.spajza.app.data.Kinds
import sk.spajza.app.data.Nutri
import sk.spajza.app.data.Uom

private sealed class Pick {
    data class Existing(val item: Item) : Pick()
    data class Generic(val est: Estimate) : Pick()
    data class Fresh(val name: String) : Pick()
}

private val EXPIRY_CHIPS: List<Pair<String, Int>> = listOf(
    "+3 dni" to 3, "+1 týždeň" to 7, "+2 týždne" to 14, "+1 mesiac" to 30, "+3 mesiace" to 90, "+1 rok" to 365
)

private val PLACE_BY_CATEGORY: Map<String, String> = mapOf(
    "Mliečne výrobky" to "Chladnička", "Mäso a ryby" to "Chladnička", "Ovocie a zelenina" to "Chladnička",
    "Mrazené" to "Mraznička", "Pečivo" to "Kuchynská skrinka", "Cestoviny, ryža a obilniny" to "Špajza",
    "Konzervy" to "Špajza", "Omáčky a korenie" to "Špajza", "Sladkosti a snacky" to "Špajza", "Nápoje" to "Špajza"
)

/** Po kolkych dnoch od nakupu expirovala posledna (neotvorena) sarza tohto produktu. */
private fun learnedShelf(db: Db, itemId: Long): Int? {
    val b = db.batches.filter { it.itemId == itemId && it.opened == null && it.expiry != null && it.bought != null }
        .maxByOrNull { it.bought ?: 0L } ?: return null
    val ex = b.expiry ?: return null
    val bo = b.bought ?: return null
    val d = ex - bo
    return if (d in 1L..3650L) d.toInt() else null
}

private fun defaultPlaceIdx(db: Db, item: Item?, est: Estimate?, category: String): Int {
    if (item != null) {
        val pid = item.placeId
        if (pid != null) return placeIndex(db, pid)
        val last = db.batches.filter { it.itemId == item.id }.lastOrNull()?.placeId
        if (last != null) return placeIndex(db, last)
    }
    val wanted = listOf(est?.place ?: "", PLACE_BY_CATEGORY[category] ?: "")
    for (w in wanted) {
        if (w.isBlank()) continue
        val idx = db.places.indexOfFirst { it.name.equals(w, ignoreCase = true) }
        if (idx >= 0) return idx + 1
    }
    return 0
}

/** Rychle pridanie potraviny: napiste nazov, zadajte mnozstvo a hotovo. */
@Composable
fun QuickAddScreen() {
    val nav = LocalNav.current
    val db = useDb()
    var query by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<Pick?>(null) }
    var added by remember { mutableStateOf("") }
    val q = query.trim()
    val nq = Calc.norm(q)

    val mine = remember(db, nq) {
        if (nq.isEmpty()) db.items.sortedByDescending { it.created }.take(8)
        else db.items.filter { Calc.norm(it.name + " " + it.brand + " " + it.kind).contains(nq) }.take(8)
    }
    val generic = remember(db, q) {
        Nutri.search(q).filter { g -> db.items.none { Calc.norm(it.name) == Calc.norm(g.title) } }.take(6)
    }

    ScreenScaffold(
        title = "Pridať potravinu",
        onBack = { nav.pop() },
        actions = {
            IconButton(onClick = { nav.push(Dest.Scan) }) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Skenovať") }
        }
    ) { pad ->
        val p = pick
        if (p != null) {
            FormPage(pad) {
                QuickForm(
                    pick = p,
                    onCancel = { pick = null },
                    onAdded = { text, close ->
                        added = text
                        pick = null
                        query = ""
                        if (close) nav.pop()
                    }
                )
            }
        } else {
            PageList(pad) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (added.isNotBlank()) Text(added, color = Pal.Green, fontWeight = FontWeight.Medium)
                        SearchField(query, { query = it }, Modifier.fillMaxWidth(), "Čo ste kúpili? (napr. cibuľa)")
                        if (q.isEmpty() && db.items.isEmpty()) {
                            Muted("Začnite písať názov potraviny, napr. „cibuľa“ alebo „mlieko“. Pri základných potravinách doplníme aj odhad výživových hodnôt.")
                        }
                    }
                }
                if (mine.isNotEmpty()) {
                    item { SectionTitle(if (nq.isEmpty()) "Naposledy pridané" else "Už ich máte v zozname") }
                    items(mine.size) { idx ->
                        val it0 = mine[idx]
                        SCard(onClick = { pick = Pick.Existing(it0) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ItemThumb(it0, 40.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(it0.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                    val sub = listOf(it0.brand, it0.category).filter { s -> s.isNotBlank() }
                                    if (sub.isNotEmpty()) Muted(sub.joinToString(" · "))
                                }
                            }
                        }
                    }
                }
                if (generic.isNotEmpty()) {
                    item { SectionTitle("Bežné potraviny (s odhadom výživových hodnôt)") }
                    items(generic.size) { idx ->
                        val g = generic[idx]
                        SCard(onClick = { pick = Pick.Generic(g) }) {
                            Text(g.title, fontWeight = FontWeight.Medium)
                            Muted(g.summary())
                        }
                    }
                }
                if (q.length >= 2) {
                    item {
                        OutlinedButton(onClick = { pick = Pick.Fresh(q) }, modifier = Modifier.fillMaxWidth()) {
                            Text("＋ Pridať „" + q + "“ ako nový produkt")
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { nav.push(Dest.Scan) }) { Text("Skenovať čiarový kód") }
                        TextButton(onClick = { nav.replace(Dest.ItemEdit(0, name = q)) }) { Text("Podrobný formulár") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickForm(pick: Pick, onCancel: () -> Unit, onAdded: (String, Boolean) -> Unit) {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()

    val existing: Item? = (pick as? Pick.Existing)?.item
    val startName: String = when (pick) {
        is Pick.Existing -> pick.item.name
        is Pick.Generic -> pick.est.title
        is Pick.Fresh -> pick.name
    }
    val est: Estimate? = when (pick) {
        is Pick.Existing -> null
        is Pick.Generic -> pick.est
        is Pick.Fresh -> Nutri.estimate("", pick.name)
    }
    val category0: String = existing?.category ?: est?.category ?: Kinds.categoryGuess(startName) ?: "Iné"
    val liquid0: Boolean = if (existing != null) (existing.uom.isVolume || existing.netLiquid)
    else est?.liquid ?: Kinds.isLiquid(startName, category0)

    var name by remember { mutableStateOf(startName) }
    var liq by remember { mutableStateOf(liquid0) }
    var uom by remember { mutableStateOf(existing?.uom ?: est?.uom ?: (if (liquid0) Uom.L else Uom.PCS)) }
    var qty by remember { mutableStateOf("1") }
    var net by remember { mutableStateOf(if (est != null && est.pieceG > 0) Calc.edit(est.pieceG) else "") }
    var price by remember { mutableStateOf("") }
    var useEstimate by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    val learned: Int? = if (existing != null) learnedShelf(db, existing.id) else null
    val initialDays: Int? = learned ?: est?.shelfDays?.takeIf { it > 0 }
    val initialExpiry: Long? = if (initialDays != null) Calc.today() + initialDays else null
    var expiry by remember { mutableStateOf(initialExpiry) }
    var placeIdx by remember { mutableStateOf(defaultPlaceIdx(db, existing, est, category0)) }

    fun commit(close: Boolean) {
        val qv = Calc.parse(qty)
        if (qv == null || qv <= 0) {
            error = "Zadajte množstvo."
            return
        }
        if (name.isBlank()) {
            error = "Zadajte názov."
            return
        }
        val priceV = Calc.parse(price) ?: 0.0
        val place = placeAt(db, placeIdx)
        val usedUom: Uom
        var id = 0L
        if (existing != null) {
            usedUom = existing.uom
            if (priceV > 0) {
                val bp = Calc.basisFor(existing.uom, priceV)
                store.saveItem(existing.copy(price = bp.second, basis = bp.first))
            }
            id = existing.id
        } else {
            usedUom = uom
            val cat = if (category0 in db.categories) category0 else "Iné"
            val bp = Calc.basisFor(uom, priceV)
            val e: Estimate? = if (useEstimate) est else null
            val kind = est?.kind?.takeIf { it in Kinds.names } ?: Kinds.guess(name)
            val item = Item(
                name = name.trim(), category = cat, kind = kind, uom = uom,
                net = if (uom.isCountable) (Calc.parse(net) ?: 0.0) else 0.0, netLiquid = liq,
                price = if (priceV > 0) bp.second else 0.0, basis = bp.first,
                kcal = e?.kcal, protein = e?.protein, fat = e?.fat, satFat = e?.satFat,
                carbs = e?.carbs, sugar = e?.sugar, fiber = e?.fiber, salt = e?.salt,
                placeId = place, water = e?.water ?: false, nutriEstimate = e != null
            )
            id = store.saveItem(item)
        }
        store.addStock(id, qv, expiry, Calc.today(), place)
        onAdded("Pridané: " + name.trim() + " " + Calc.qty(qv, usedUom), close)
    }

    if (existing != null) {
        Text(existing.name, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Muted("Už ho máte v zozname, pridáte ďalšie množstvo.")
    } else {
        TextField2("Názov", name, { name = it }, Modifier.fillMaxWidth())
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        NumField("Množstvo", qty, { qty = it }, Modifier.weight(1f), suffix = if (existing != null) existing.uom.label else null)
        if (existing == null) {
            val unitList = Kinds.uomsFor(liq)
            Picker(
                "Jednotka", unitList.map { it.label }, unitList.indexOf(uom).coerceAtLeast(0),
                { uom = unitList[it] }, Modifier.weight(1f)
            )
        }
    }
    if (existing == null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = liq, onCheckedChange = { liq = it; uom = Kinds.fitUom(uom, it) })
            Text("Je to tekutina")
        }
        if (uom.isCountable) {
            NumField(
                "Obsah 1 " + uom.label + " (nepovinné)", net, { net = it }, Modifier.fillMaxWidth(),
                suffix = if (liq) "ml" else "g"
            )
        }
    }

    DateField("Dátum expirácie", expiry, { expiry = it }, Modifier.fillMaxWidth())
    if (initialDays != null && expiry == initialExpiry) {
        Muted(
            if (learned != null) "Predvyplnené podľa vašej predchádzajúcej šarže (" + learned + " dní)."
            else "Predvyplnený odhad trvanlivosti (" + initialDays + " dní), skontrolujte dátum na obale."
        )
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(EXPIRY_CHIPS.size) { idx ->
            FilterChip(
                selected = false,
                onClick = { expiry = Calc.today() + EXPIRY_CHIPS[idx].second },
                label = { Text(EXPIRY_CHIPS[idx].first) }
            )
        }
    }

    PlacePicker("Miesto uloženia", db, placeIdx, { placeIdx = it }, Modifier.fillMaxWidth())
    NumField(
        "Cena za 1 " + (if (existing != null) existing.uom.label else uom.label) + " (nepovinné)",
        price, { price = it }, Modifier.fillMaxWidth(), suffix = "€"
    )

    if (existing == null && est != null) {
        Row(verticalAlignment = Alignment.Top) {
            Checkbox(checked = useEstimate, onCheckedChange = { useEstimate = it })
            Column(Modifier.padding(top = 12.dp)) {
                Text("Doplniť priemerné výživové hodnoty (odhad)")
                Muted(est.summary())
            }
        }
    }

    if (error.isNotBlank()) Text(error, color = Pal.Red)
    Button(onClick = { commit(false) }, modifier = Modifier.fillMaxWidth()) { Text("Pridať a ďalší") }
    OutlinedButton(onClick = { commit(true) }, modifier = Modifier.fillMaxWidth()) { Text("Pridať a zavrieť") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onCancel) { Text("Späť na výber") }
        TextButton(onClick = {
            if (existing != null) nav.replace(Dest.ItemEdit(existing.id))
            else nav.replace(Dest.ItemEdit(0, name = name, kind = est?.kind?.takeIf { it in Kinds.names } ?: ""))
        }) { Text("Podrobný formulár") }
    }
}
