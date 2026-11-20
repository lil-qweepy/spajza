package sk.spajza.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import sk.spajza.app.data.Amounts
import sk.spajza.app.data.Need
import sk.spajza.app.data.Uom
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sk.spajza.app.data.Batch
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Db
import sk.spajza.app.data.PlaceType
import sk.spajza.app.data.Shelf
import sk.spajza.app.data.Item
import sk.spajza.app.data.MoveType
import sk.spajza.app.data.OffResult
import sk.spajza.app.data.ShopItem
import sk.spajza.app.data.buildStock
import sk.spajza.app.data.placeName

fun placeOptions(db: Db): List<String> = listOf("Bez miesta") + db.places.map { it.name }

@Composable
fun PlacePicker(label: String, db: Db, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val store = LocalStore.current
    var newDialog by remember { mutableStateOf(false) }
    val options = placeOptions(db) + "＋ Nové miesto…"
    Picker(label, options, selected, { idx -> if (idx == options.size - 1) newDialog = true else onSelect(idx) }, modifier)
    if (newDialog) {
        NameDialog("Nové miesto uloženia", "", onDismiss = { newDialog = false }) { name ->
            val n = db.places.size
            store.savePlace(0, name)
            onSelect(n + 1)
        }
    }
}

fun placeIndex(db: Db, id: Long?): Int = if (id == null) 0 else db.places.indexOfFirst { it.id == id } + 1

fun placeAt(db: Db, idx: Int): Long? = if (idx <= 0) null else db.places.getOrNull(idx - 1)?.id

@Composable
fun ItemDetailScreen(id: Long) {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    val scope = rememberCoroutineScope()
    val prod = db.items.firstOrNull { it.id == id }

    var addDialog by remember { mutableStateOf(false) }
    var useDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var editBatch by remember { mutableStateOf<Batch?>(null) }
    var moveBatch by remember { mutableStateOf<Batch?>(null) }
    var thawBatch by remember { mutableStateOf<Batch?>(null) }
    var openBatch by remember { mutableStateOf<Batch?>(null) }
    var loading by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf("") }

    if (prod == null) {
        ScreenScaffold("Produkt", onBack = { nav.pop() }) { pad ->
            PageList(pad) { item { EmptyState("Produkt už neexistuje") } }
        }
        return
    }
    val stock = buildStock(prod, db.batches.filter { it.itemId == id && it.qty > 0 })
    val moves = db.moves.filter { it.itemId == id }.take(12)

    ScreenScaffold(
        title = prod.name,
        onBack = { nav.pop() },
        actions = {
            IconButton(onClick = { nav.push(Dest.ItemEdit(prod.id)) }) { Icon(Icons.Filled.Edit, contentDescription = "Upraviť") }
            IconButton(onClick = { deleteDialog = true }) { Icon(Icons.Filled.Delete, contentDescription = "Zmazať") }
        }
    ) { pad ->
        PageList(pad) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemThumb(prod, 72.dp)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(prod.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val sub = listOf(prod.brand, prod.category, prod.kind).filter { it.isNotBlank() }
                        if (sub.isNotEmpty()) Muted(sub.joinToString(" · "))
                        if (prod.ean.isNotBlank()) Muted("EAN " + prod.ean)
                    }
                }
            }
            if (prod.draft) {
                item {
                    SCard {
                        Text("Chýbajú údaje", fontWeight = FontWeight.Medium)
                        Muted("Produkt vznikol len z fotky pri skenovaní. Doplňte názov a ďalšie údaje.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { nav.push(Dest.ItemEdit(prod.id)) }) { Text("Doplniť údaje") }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("na sklade", Calc.qty(stock.qty, prod.uom), modifier = Modifier.weight(1f))
                    StatTile("hodnota", Calc.money(stock.value), Pal.Green, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("kalórie", if (prod.kcal == null) "—" else Calc.kcalText(stock.kcal), modifier = Modifier.weight(1f))
                    StatTile("voda", if (prod.water) Calc.waterText(stock.waterMl) else "—", Pal.Blue, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { addDialog = true }, modifier = Modifier.weight(1f)) { Text("Pridať") }
                    OutlinedButton(onClick = { useDialog = true }, enabled = stock.qty > 0, modifier = Modifier.weight(1f)) { Text("Odobrať") }
                    OutlinedButton(onClick = {
                        val base = db.shop.firstOrNull { it.itemId == prod.id && !it.checked }
                        if (base == null) {
                            val price = Calc.unitPrice(prod)
                            store.saveShop(ShopItem(itemId = prod.id, name = prod.name, qty = 1.0, uom = prod.uom, price = price))
                        }
                        info = "Pridané do nákupného zoznamu"
                    }, modifier = Modifier.weight(1f)) { Text("Nákup") }
                }
                if (info.isNotBlank()) Muted(info, Modifier.padding(top = 6.dp))
            }
            if (prod.kcal == null && prod.ean.length >= 8) {
                item {
                    SCard {
                        Text("Chýbajú kalórie", fontWeight = FontWeight.Medium)
                        Muted("Skúsim ich stiahnuť z Open Food Facts podľa čiarového kódu.")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(enabled = !loading, onClick = {
                            loading = true
                            scope.launch {
                                val r = store.autoFillItem(prod.id)
                                loading = false
                                info = when (r) {
                                    is OffResult.Found -> if (r.product.kcal100 != null) "Údaje doplnené" else "Produkt sa našiel, ale bez kalórií"
                                    is OffResult.NotFound -> "Produkt sa v databáze nenašiel"
                                    is OffResult.Error -> "Chyba pripojenia – skúste neskôr"
                                    null -> ""
                                }
                            }
                        }) { Text(if (loading) "Sťahujem…" else "Stiahnuť z internetu") }
                    }
                }
            }
            if (stock.batches.map { it.placeId }.distinct().size > 1) {
                item {
                    SCard {
                        Text("Podľa miesta", fontWeight = FontWeight.Medium)
                        for ((pid, bs) in stock.batches.groupBy { it.placeId }) {
                            InfoRow(db.placeName(pid).ifBlank { "Bez miesta" }, Calc.qty(bs.sumOf { it.qty }, prod.uom))
                        }
                    }
                }
            }
            item { SectionTitle("Šarže (podľa expirácie)") }
            if (stock.batches.isEmpty()) {
                item { Muted("Nič nie je na sklade.") }
            }
            items(stock.batches.size) { idx ->
                val b = stock.batches[idx]
                SCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Calc.qty(b.qty, prod.uom), fontWeight = FontWeight.SemiBold)
                            Muted(
                                "Kúpené " + Calc.date(b.bought) +
                                    (if (db.placeName(b.placeId).isNotBlank()) " · " + db.placeName(b.placeId) else "")
                            )
                            if (b.expiry != null) Muted("Expirácia " + Calc.date(b.expiry))
                            if (b.opened != null) {
                                Muted(
                                    "Otvorené " + Calc.date(b.opened) +
                                        (if (b.origExpiry != null) " · pôvodne do " + Calc.date(b.origExpiry) else "")
                                )
                            }
                        }
                        ExpiryBadge(b.expiry)
                    }
                    if (b.opened == null) {
                        OutlinedButton(onClick = { openBatch = b }, modifier = Modifier.fillMaxWidth()) { Text("Otvoriť balenie") }
                    }
                    if (Shelf.typeOf(db.placeName(b.placeId)) == PlaceType.FREEZER) {
                        OutlinedButton(onClick = { thawBatch = b }, modifier = Modifier.fillMaxWidth()) { Text("Rozmraziť") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { editBatch = b }) { Text("Upraviť") }
                        TextButton(onClick = { moveBatch = b }) { Text("Presunúť") }
                        TextButton(onClick = { store.withdraw(prod.id, b.qty, MoveType.CONSUME, b.id) }) { Text("Spotrebované") }
                    }
                }
            }
            if (hasNutrition(prod)) {
                item { SectionTitle("Výživové hodnoty na 100 " + (if (prod.netLiquid || prod.uom.isVolume) "ml" else "g") + (if (prod.nutriEstimate) " (odhad)" else "")) }
                item {
                    SCard {
                        nutRow("Energia", prod.kcal, " kcal")
                        nutRow("Bielkoviny", prod.protein, " g")
                        nutRow("Tuky", prod.fat, " g")
                        nutRow("z toho nasýtené", prod.satFat, " g")
                        nutRow("Sacharidy", prod.carbs, " g")
                        nutRow("z toho cukry", prod.sugar, " g")
                        nutRow("Vláknina", prod.fiber, " g")
                        nutRow("Soľ", prod.salt, " g")
                    }
                }
            }
            item { SectionTitle("Posledné pohyby") }
            if (moves.isEmpty()) item { Muted("Zatiaľ žiadne.") }
            items(moves.size) { idx ->
                val m = moves[idx]
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(m.type.label + (if (m.note.isNotBlank()) " – " + m.note else ""), style = MaterialTheme.typography.bodyMedium)
                        Muted(Calc.dateTime(m.time) + (if (m.to.isNotBlank()) " · → " + m.to else ""))
                    }
                    Text((if (m.qty > 0) "+" else "") + Calc.qty(m.qty, m.uom), color = if (m.qty > 0) Pal.Green else Pal.Orange)
                }
            }
        }
    }

    if (addDialog) AddStockDialog(db, prod, onDismiss = { addDialog = false }) { q, ex, bought, place ->
        store.addStock(prod.id, q, ex, bought, place)
    }
    if (useDialog) UseDialog(prod, stock.qty, onDismiss = { useDialog = false }) { q, type ->
        store.withdraw(prod.id, q, type)
    }
    val eb = editBatch
    if (eb != null) EditBatchDialog(db, prod, eb, onDismiss = { editBatch = null }) { nb -> store.updateBatch(nb) }
    val ob = openBatch
    if (ob != null) OpenPackDialog(prod, ob) { openBatch = null }
    val mb = moveBatch
    if (mb != null) MoveDialog(db, prod, mb, onDismiss = { moveBatch = null }) { place, q, exp, change -> store.moveBatch(mb.id, place, q, exp, change) }
    val tb = thawBatch
    if (tb != null) ThawDialog(db, prod, tb, onDismiss = { thawBatch = null }) { place, q, exp -> store.moveBatch(tb.id, place, q, exp, true) }
    if (deleteDialog) {
        ConfirmDialog(
            "Zmazať produkt?", "Produkt \"" + prod.name + "\" aj s celou históriou zásob sa natrvalo zmaže.",
            "Zmazať", onConfirm = { store.deleteItem(prod.id); nav.pop() }, onDismiss = { deleteDialog = false }
        )
    }
}

private fun hasNutrition(i: Item): Boolean =
    i.kcal != null || i.protein != null || i.fat != null || i.carbs != null || i.sugar != null || i.salt != null

@Composable
private fun nutRow(label: String, v: Double?, unit: String) {
    if (v != null) InfoRow(label, Calc.num(v, 1) + unit)
}

@Composable
fun AddStockDialog(db: Db, item: Item, onDismiss: () -> Unit, onOk: (Double, Long?, Long?, Long?) -> Unit) {
    var qty by remember { mutableStateOf(if (item.uom.isCountable) "1" else "") }
    var expiry by remember { mutableStateOf<Long?>(null) }
    var bought by remember { mutableStateOf<Long?>(Calc.today()) }
    var place by remember { mutableStateOf(placeIndex(db, item.placeId)) }
    val q = Calc.parse(qty)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pridať do zásob") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Množstvo", qty, { qty = it }, Modifier.fillMaxWidth(), suffix = item.uom.label)
                DateField("Dátum expirácie", expiry, { expiry = it }, Modifier.fillMaxWidth())
                DateField("Dátum nákupu", bought, { bought = it }, Modifier.fillMaxWidth())
                PlacePicker("Miesto uloženia", db, place, { place = it }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = q != null && q > 0, onClick = {
                onOk(q ?: 0.0, expiry, bought, placeAt(db, place))
                onDismiss()
            }) { Text("Pridať") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

/** Jednotka, v ktorej sa zadava odobrane mnozstvo (g/ml sa prepocita na jednotku produktu). */
private enum class UseUnit(val label: String) { NATIVE(""), G("g"), ML("ml") }

private fun quickAmounts(unit: String): List<Double> = when (unit) {
    "g" -> listOf(10.0, 50.0, 100.0, 250.0, 500.0)
    "ml" -> listOf(50.0, 100.0, 250.0, 500.0)
    "kg" -> listOf(0.25, 0.5, 1.0)
    "l" -> listOf(0.25, 0.5, 1.0)
    else -> listOf(1.0, 2.0, 3.0, 5.0)
}

@Composable
fun UseDialog(prod: Item, total: Double, onDismiss: () -> Unit, onOk: (Double, MoveType) -> Unit) {
    // Co sa da odobrat v g/ml: sypke a tekute potraviny a balenia so znamym obsahom.
    val alt: UseUnit? = when {
        prod.uom == Uom.KG || (prod.uom.isCountable && prod.net > 0 && !prod.netLiquid) -> UseUnit.G
        prod.uom == Uom.L || (prod.uom.isCountable && prod.net > 0 && prod.netLiquid) -> UseUnit.ML
        else -> null
    }
    val native = prod.uom
    val units: List<UseUnit> = if (alt == null) listOf(UseUnit.NATIVE) else if (native == Uom.G || native == Uom.ML) listOf(UseUnit.NATIVE) else listOf(alt, UseUnit.NATIVE)
    var unit by remember { mutableStateOf(units.first()) }
    var qty by remember { mutableStateOf(if (alt == null) "1" else "") }
    val reasons = listOf(MoveType.CONSUME, MoveType.DISCARD, MoveType.REMOVE)
    var reason by remember { mutableStateOf(0) }

    val unitLabel = if (unit == UseUnit.NATIVE) native.label else unit.label
    val typed = Calc.parse(qty)
    val nativeQty: Double? = when {
        typed == null || typed <= 0 -> null
        unit == UseUnit.NATIVE -> typed
        else -> Amounts.itemQty(prod, Need(typed, unit.label))
    }
    val tooMuch = nativeQty != null && nativeQty > total + 1e-9
    val valid = nativeQty != null && !tooMuch

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Odobrať zo zásob") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Muted(prod.name + " · na sklade " + Calc.qty(total, native))
                if (units.size > 1) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(units.size) { idx ->
                            val u = units[idx]
                            FilterChip(
                                selected = unit == u,
                                onClick = { unit = u; qty = "" },
                                label = { Text(if (u == UseUnit.NATIVE) native.label else u.label) }
                            )
                        }
                    }
                }
                NumField("Koľko odobrať", qty, { qty = it }, Modifier.fillMaxWidth(), suffix = unitLabel)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val quick = quickAmounts(unitLabel)
                    items(quick.size) { idx ->
                        AssistChip(onClick = { qty = Calc.edit(quick[idx]) }, label = { Text(Calc.num(quick[idx], 2) + " " + unitLabel) })
                    }
                    item { AssistChip(onClick = { unit = UseUnit.NATIVE; qty = Calc.edit(total) }, label = { Text("Všetko") }) }
                }
                if (tooMuch) {
                    Text("Na sklade je len " + Calc.qty(total, native) + ".", color = Pal.Red)
                } else if (nativeQty != null) {
                    Muted("Zostane " + Calc.qty((total - nativeQty).coerceAtLeast(0.0), native))
                } else if (typed != null && typed > 0) {
                    Muted("Tento produkt sa nedá prepočítať, zadajte množstvo v " + native.label + ".")
                }
                Text("Dôvod", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(reasons.size) { idx ->
                        FilterChip(selected = reason == idx, onClick = { reason = idx }, label = { Text(reasons[idx].label) })
                    }
                }
                Muted("Berie sa najprv z najbližšej expirácie.")
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onOk(nativeQty ?: 0.0, reasons[reason]); onDismiss() }) { Text("Odobrať") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

@Composable
private fun EditBatchDialog(db: Db, item: Item, b: Batch, onDismiss: () -> Unit, onOk: (Batch) -> Unit) {
    var qty by remember { mutableStateOf(Calc.edit(b.qty)) }
    var expiry by remember { mutableStateOf(b.expiry) }
    var bought by remember { mutableStateOf(b.bought) }
    var place by remember { mutableStateOf(placeIndex(db, b.placeId)) }
    val q = Calc.parse(qty)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upraviť šaržu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Množstvo", qty, { qty = it }, Modifier.fillMaxWidth(), suffix = item.uom.label)
                DateField("Dátum expirácie", expiry, { expiry = it }, Modifier.fillMaxWidth())
                DateField("Dátum nákupu", bought, { bought = it }, Modifier.fillMaxWidth())
                PlacePicker("Miesto uloženia", db, place, { place = it }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = q != null && q > 0, onClick = {
                onOk(b.copy(qty = q ?: b.qty, expiry = expiry, bought = bought, placeId = placeAt(db, place)))
                onDismiss()
            }) { Text("Uložiť") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

@Composable
private fun MoveDialog(db: Db, item: Item, b: Batch, onDismiss: () -> Unit, onOk: (Long?, Double, Long?, Boolean) -> Unit) {
    var qty by remember { mutableStateOf(Calc.edit(b.qty)) }
    var place by remember { mutableStateOf(placeIndex(db, b.placeId)) }
    var recalc by remember { mutableStateOf(true) }
    val q = Calc.parse(qty)
    val fromType = Shelf.typeOf(db.placeName(b.placeId))
    val toType = Shelf.typeOf(db.placeName(placeAt(db, place)))
    // Balenia po otvoreni nechavame tak, ako su (expiracia sa riadi dnami po otvoreni).
    val suggested: Long? = if (b.opened == null) Shelf.moveExpiry(b.expiry, Calc.today(), fromType, toType, item.category) else b.expiry
    val offer = suggested != b.expiry
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Presunúť na iné miesto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Koľko presunúť", qty, { qty = it }, Modifier.fillMaxWidth(), suffix = item.uom.label)
                PlacePicker("Nové miesto", db, place, { place = it }, Modifier.fillMaxWidth())
                if (offer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = recalc, onCheckedChange = { recalc = it })
                        Text(
                            "Prepočítať expiráciu na " + Calc.date(suggested) +
                                (if (b.expiry != null) " (bola " + Calc.date(b.expiry) + ")" else ""),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = q != null && q > 0, onClick = {
                onOk(placeAt(db, place), q ?: 0.0, suggested, offer && recalc)
                onDismiss()
            }) { Text("Presunúť") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

/** Rozmrazenie: presun z mrazicky (do chladnicky) a nastavenie datumu spotreby, aj vlastneho. */
@Composable
private fun ThawDialog(db: Db, item: Item, b: Batch, onDismiss: () -> Unit, onOk: (Long?, Double, Long?) -> Unit) {
    val today = Calc.today()
    val defaultDays = Shelf.thawDays(item.category)
    var qty by remember { mutableStateOf(Calc.edit(b.qty)) }
    val fridge = db.places.indexOfFirst { Shelf.typeOf(it.name) == PlaceType.FRIDGE }
    var place by remember { mutableStateOf(if (fridge >= 0) fridge + 1 else 0) }
    var expiry by remember { mutableStateOf<Long?>(today + defaultDays) }
    val q = Calc.parse(qty)
    val chips = listOf("Dnes" to 0, "Zajtra" to 1, "+2 dni" to 2, "+3 dni" to 3, "+5 dní" to 5)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rozmraziť") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Koľko rozmraziť", qty, { qty = it }, Modifier.fillMaxWidth(), suffix = item.uom.label)
                PlacePicker("Kam ho dáte", db, place, { place = it }, Modifier.fillMaxWidth())
                DateField("Spotrebovať do", expiry, { expiry = it }, Modifier.fillMaxWidth())
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chips.size) { idx ->
                        FilterChip(
                            selected = expiry == today + chips[idx].second,
                            onClick = { expiry = today + chips[idx].second },
                            label = { Text(chips[idx].first) }
                        )
                    }
                }
                Muted("Návrh: " + Shelf.describe(defaultDays) + " po rozmrazení. Rozmrazené potraviny nemrazte znova.")
            }
        },
        confirmButton = {
            TextButton(enabled = q != null && q > 0 && placeAt(db, place) != b.placeId, onClick = {
                onOk(placeAt(db, place), q ?: 0.0, expiry)
                onDismiss()
            }) { Text("Rozmraziť") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}
