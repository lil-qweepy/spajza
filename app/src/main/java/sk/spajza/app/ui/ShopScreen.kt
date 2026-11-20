package sk.spajza.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import sk.spajza.app.data.Stores
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Forecasts
import sk.spajza.app.data.Prices
import sk.spajza.app.data.ShopPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.spajza.app.data.Pairing
import androidx.compose.material3.LinearProgressIndicator
import sk.spajza.app.data.ShopItem
import sk.spajza.app.data.Kinds
import sk.spajza.app.data.Uom
import sk.spajza.app.data.stock

private fun dealText(s: ShopItem): Pair<String, Color>? {
    val u = s.dealUntil ?: return null
    val d = Calc.daysUntil(u) ?: return null
    val date = Calc.date(u)
    return when {
        d < 0 -> ("Akcia skončila " + date) to Pal.Red
        d == 0L -> "Akcia končí dnes" to Pal.Orange
        d == 1L -> ("Akcia do zajtra (" + date + ")") to Pal.Orange
        d <= 3 -> ("Akcia do " + date + " (o " + d + " dni)") to Pal.Amber
        else -> ("Akcia do " + date + " (o " + d + " dní)") to Pal.Green
    }
}

@Composable
fun ShopScreen() {
    val store = LocalStore.current
    val db = useDb()
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ShopItem?>(null) }
    var message by remember { mutableStateOf("") }
    var storeFilter by remember { mutableStateOf("") }
    var budgetDialog by remember { mutableStateOf(false) }
    var cmp by remember { mutableStateOf<Prices.DealCompare?>(null) }

    val stock = remember(db) { db.stock() }
    val suggestions = remember(db) {
        Pairing.lowGroups(db).filter { !Pairing.onShopList(db, it) }
    }
    val forecasts = remember(db) {
        Forecasts.soonOut(db).filter { f ->
            !Pairing.onShopList(db, f.group) && suggestions.none { it.key == f.group.key }
        }
    }
    val usedStores = db.shop.map { it.store }.filter { it.isNotBlank() }.distinct().sorted()
    val filter = if (storeFilter in usedStores) storeFilter else ""
    val shown = db.shop.filter { filter.isEmpty() || it.store == filter }
        .sortedWith(compareBy<ShopItem>({ it.checked }, { it.dealUntil ?: Long.MAX_VALUE }))
    val total = shown.sumOf { it.cost() }
    val totalChecked = shown.filter { it.checked }.sumOf { it.cost() }
    val checkedCount = db.shop.count { it.checked }
    val saved = shown.sumOf { Prices.saving(db, it) }
    val savedChecked = shown.filter { it.checked }.sumOf { Prices.saving(db, it) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val pdfLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) { ShopPdf.write(ctx, uri, db, shown) }
                message = if (res.isSuccess) "PDF uložené." else "Export zlyhal: " + (res.exceptionOrNull()?.message ?: "chyba")
            }
        }
    }

    fun addByName() {
        val n = newName.trim()
        if (n.isEmpty()) return
        val known = db.items.firstOrNull { it.name.equals(n, ignoreCase = true) }
        if (known != null) {
            store.saveShop(ShopItem(itemId = known.id, name = known.name, qty = 1.0, uom = known.uom, price = Calc.unitPrice(known)))
        } else {
            store.saveShop(ShopItem(name = n))
        }
        newName = ""
    }

    ScreenScaffold(title = "Nákupný zoznam") { pad ->
        PageList(pad) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField2("Pridať položku", newName, { newName = it }, Modifier.weight(1f))
                    IconButton(onClick = { addByName() }) { Icon(Icons.Filled.Add, contentDescription = "Pridať") }
                    IconButton(onClick = { editing = ShopItem(name = newName.trim()) }) {
                        Icon(Icons.Filled.LocalOffer, contentDescription = "Akcia z letáku / položka s detailmi")
                    }
                }
            }
            if (usedStores.isNotEmpty()) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = filter.isEmpty(), onClick = { storeFilter = "" }, label = { Text("Všetky") })
                        for (st in usedStores) {
                            FilterChip(selected = filter == st, onClick = { storeFilter = st }, label = { Text(st) })
                        }
                    }
                }
            }
            if (suggestions.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionTitle("Dochádza – navrhujem kúpiť")
                        for (g in suggestions) {
                            val s = g.primary
                            val q = Math.max(1.0, Math.ceil(g.minQty * 2 - g.qty))
                            AssistChip(
                                onClick = {
                                    store.saveShop(
                                        ShopItem(itemId = s.item.id, name = s.item.name, qty = q, uom = s.item.uom, price = Calc.unitPrice(s.item))
                                    )
                                },
                                label = { Text((if (g.grouped) g.name else s.item.name) + " (zostáva " + Calc.qty(s.item, g.qty) + ")  + " + Calc.num(q)) }
                            )
                        }
                    }
                }
            }
            if (forecasts.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionTitle("Podľa vašej spotreby čoskoro dôjde")
                        for (f in forecasts) {
                            val it0 = f.stock.item
                            AssistChip(
                                onClick = {
                                    store.saveShop(
                                        ShopItem(itemId = it0.id, name = it0.name, qty = f.buy, uom = it0.uom, price = Calc.unitPrice(it0))
                                    )
                                },
                                label = { Text((if (f.group.grouped) f.group.name else it0.name) + " – " + Forecasts.daysText(f.daysLeft) + "  + " + Calc.qty(it0, f.buy)) }
                            )
                        }
                        Muted("Odhad z posledných 60 dní spotreby. Návrh kryje asi 14 dní.")
                    }
                }
            }
            item {
                val budget = db.settings.monthlyBudget
                if (budget > 0) {
                    val spent = Prices.monthSpent(db)
                    val listTotal = db.shop.sumOf { it.cost() }
                    val after = budget - spent - listTotal
                    SCard {
                        Text("Rozpočet na tento mesiac", fontWeight = FontWeight.Medium)
                        InfoRow("Minuté z nákupov", Calc.money(spent) + " z " + Calc.money(budget))
                        LinearProgressIndicator(
                            progress = { (spent / budget).toFloat().coerceIn(0f, 1f) },
                            color = if (spent > budget) Pal.Red else Pal.Green,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                        if (listTotal > 0) {
                            InfoRow("Zoznam spolu (odhad)", Calc.money(listTotal))
                            Text(
                                if (after >= 0) "Po nákupe zostane " + Calc.money(after) else "Rozpočet prekročíte o " + Calc.money(-after),
                                color = if (after >= 0) Pal.Green else Pal.Red,
                                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
                            )
                        } else if (spent > budget) {
                            Text("Rozpočet je prekročený o " + Calc.money(spent - budget), color = Pal.Red, style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { budgetDialog = true }) { Text("Zmeniť rozpočet") }
                    }
                } else {
                    OutlinedButton(onClick = { budgetDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Nastaviť mesačný rozpočet") }
                }
            }
            if (db.shop.isEmpty()) {
                item { EmptyState("Zoznam je prázdny", "Pridajte položku alebo ju pridajte z detailu produktu.") }
            } else {
                item {
                    SCard {
                        InfoRow("Odhadovaná cena spolu", Calc.money(total))
                        InfoRow("Označené v košíku (" + checkedCount + ")", Calc.money(totalChecked))
                        if (saved > 0.005) InfoRow("Ušetríte vďaka akciám", Calc.money(saved))
                        if (savedChecked > 0.005) InfoRow("Z toho v košíku ušetríte", Calc.money(savedChecked))
                    }
                }
            }
            items(shown.size) { idx ->
                val s = shown[idx]
                SCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = s.checked, onCheckedChange = { store.saveShop(s.copy(checked = it)) })
                        Column(Modifier.weight(1f).clickable { editing = s }.padding(horizontal = 6.dp)) {
                            Text(
                                s.name, fontWeight = FontWeight.Medium,
                                textDecoration = if (s.checked) TextDecoration.LineThrough else null
                            )
                            Muted(
                                Calc.qty(s.qty, s.uom) +
                                    (if (s.price > 0) " · " + Calc.money(s.price) + " / " + s.uom.label + " = " + Calc.money(s.cost()) else "") +
                                    (if (s.store.isNotBlank()) " · " + s.store else "")
                            )
                            val deal = dealText(s)
                            if (deal != null) Text(deal.first, color = deal.second, style = MaterialTheme.typography.bodySmall)
                            if (s.multiBuy) {
                                val bq = Calc.num(s.buyQty, 1)
                                val pq = Calc.num(s.payQty, 1)
                                Text("Akcia: kúp " + bq + ", zaplať " + pq, color = Pal.Green, style = MaterialTheme.typography.bodySmall)
                                val rem = s.qty % s.buyQty
                                if (rem > 1e-9 || s.qty < s.buyQty) {
                                    val target = Math.max(1.0, Math.ceil(s.qty / s.buyQty - 1e-9)) * s.buyQty
                                    Text(
                                        "Do akcie chýba " + Calc.num(target - s.qty, 1) + " " + s.uom.label,
                                        color = Pal.Amber, style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.clickable { store.saveShop(s.copy(qty = target)) }
                                    )
                                    Muted("Ťuknutím doplníte na " + Calc.num(target, 1) + " " + s.uom.label)
                                }
                            }
                            if (s.regularPrice > s.price && s.price > 0) {
                                val pct = Math.round((1 - s.price / s.regularPrice) * 100)
                                Muted("Bežne " + Calc.money(s.regularPrice) + " / " + s.uom.label + " · zľava " + pct + " %")
                            }
                            val ref = Prices.stockPrice(db, s)
                            if (ref != null) {
                                val diff = ref - s.effPrice()
                                val pct = Math.round(Math.abs(diff) / ref * 100)
                                if (diff > 0.004) {
                                    Text(
                                        "Oproti zásobám lacnejšie o " + Calc.money(diff) + " / " + s.uom.label + " (-" + pct + " %) · v zásobách ø " + Calc.money(ref),
                                        color = Pal.Green, style = MaterialTheme.typography.bodySmall
                                    )
                                } else if (diff < -0.004) {
                                    Text(
                                        "Drahšie ako v zásobách o " + Calc.money(-diff) + " / " + s.uom.label + " (+" + pct + " %) · v zásobách ø " + Calc.money(ref),
                                        color = Pal.Orange, style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            val sav = Prices.saving(db, s)
                            if (sav > 0.005) Muted("Ušetríte " + Calc.money(sav))
                            for (n in Prices.notes(db, s)) {
                                Text(
                                    n.text, style = MaterialTheme.typography.bodySmall,
                                    color = when (n.level) { 1 -> Pal.Green; 2 -> Pal.Orange; else -> Pal.Muted }
                                )
                            }
                            if (s.note.isNotBlank()) Muted(s.note)
                        }
                        IconButton(onClick = { store.deleteShop(s.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Odstrániť") }
                    }
                }
            }
            if (db.shop.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = checkedCount > 0, modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val n = store.transferChecked()
                                message = "Do zásob prenesených položiek: $n"
                            }
                        ) { Text("Prenesť označené do zásob") }
                        OutlinedButton(
                            enabled = checkedCount > 0, modifier = Modifier.fillMaxWidth(),
                            onClick = { store.clearCheckedShop() }
                        ) { Text("Odstrániť označené") }
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { pdfLauncher.launch("Nakupny_zoznam.pdf") }
                        ) { Text(if (filter.isEmpty()) "Exportovať do PDF" else "Exportovať do PDF (" + filter + ")") }
                        if (message.isNotBlank()) Muted(message)
                        Muted("Pri prenose sa položky pridajú do zásob s dnešným dátumom nákupu. Expiráciu doplníte v detaile produktu.")
                    }
                }
            }
        }
    }

    val c = cmp
    if (c != null) {
        fun nm(x: ShopItem) = if (x.store.isBlank()) "bez obchodu" else x.store
        AlertDialog(
            onDismissRequest = { cmp = null },
            title = { Text("Porovnanie akcií: " + c.cheaper.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        nm(c.cheaper) + " je lacnejšie o " + Calc.money(c.diff) + " / " + c.cheaper.uom.label + " (" + c.pct + " %)",
                        fontWeight = FontWeight.Medium, color = Pal.Green
                    )
                    InfoRow(nm(c.cheaper), Calc.money(c.cheaper.effPrice()) + " / " + c.cheaper.uom.label)
                    InfoRow(nm(c.dearer), Calc.money(c.dearer.effPrice()) + " / " + c.dearer.uom.label)
                    Muted("Ponechať obe, alebo odstrániť drahšiu z nákupného zoznamu?")
                }
            },
            confirmButton = { TextButton(onClick = { cmp = null }) { Text("Ponechať obe") } },
            dismissButton = { TextButton(onClick = { store.deleteShop(c.dearer.id); cmp = null }) { Text("Odstrániť drahšiu") } }
        )
    }

    if (budgetDialog) {
        var bv by remember { mutableStateOf(if (db.settings.monthlyBudget > 0) Calc.edit(db.settings.monthlyBudget) else "") }
        AlertDialog(
            onDismissRequest = { budgetDialog = false },
            title = { Text("Mesačný rozpočet na nákupy") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Rozpočet na mesiac", bv, { bv = it }, Modifier.fillMaxWidth(), suffix = "€")
                    Muted("Počíta sa len to, čo prenesiete zo zoznamu do zásob (tlačidlo Prenesť označené do zásob). Prázdne alebo 0 = bez sledovania.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = Calc.parse(bv) ?: 0.0
                    store.updateSettings { it.copy(monthlyBudget = v.coerceAtLeast(0.0)) }
                    budgetDialog = false
                }) { Text("Uložiť") }
            },
            dismissButton = { TextButton(onClick = { budgetDialog = false }) { Text("Zrušiť") } }
        )
    }

    val e = editing
    if (e != null) {
        var qty by remember(e.id) { mutableStateOf(Calc.edit(e.qty)) }
        var price by remember(e.id) { mutableStateOf(Calc.edit(e.price)) }
        var regular by remember(e.id) { mutableStateOf(if (e.regularPrice > 0) Calc.edit(e.regularPrice) else "") }
        var name by remember(e.id) { mutableStateOf(e.name) }
        var uomIdx by remember(e.id) { mutableStateOf(e.uom.ordinal) }
        var until by remember(e.id) { mutableStateOf(e.dealUntil) }
        var note by remember(e.id) { mutableStateOf(e.note) }
        val multiOptions = listOf(
            "Bez množstvovej akcie", "1+1 zadarmo (kúp 2, zaplať 1)", "2+1 zadarmo (kúp 3, zaplať 2)",
            "3+1 zadarmo (kúp 4, zaplať 3)", "Vlastná…"
        )
        var multiIdx by remember(e.id) {
            mutableStateOf(
                when {
                    !e.multiBuy -> 0
                    e.buyQty == 2.0 && e.payQty == 1.0 -> 1
                    e.buyQty == 3.0 && e.payQty == 2.0 -> 2
                    e.buyQty == 4.0 && e.payQty == 3.0 -> 3
                    else -> 4
                }
            )
        }
        var buyTxt by remember(e.id) { mutableStateOf(if (e.multiBuy) Calc.edit(e.buyQty) else "") }
        var payTxt by remember(e.id) { mutableStateOf(if (e.multiBuy) Calc.edit(e.payQty) else "") }
        val storeOptions = listOf("Bez obchodu") + Stores.all(db) + "Iný obchod…"
        var storeIdx by remember(e.id) {
            mutableStateOf(
                if (e.store.isBlank()) 0
                else storeOptions.indexOfFirst { it.equals(e.store, ignoreCase = true) }.let { if (it < 0) storeOptions.lastIndex else it }
            )
        }
        var customStore by remember(e.id) { mutableStateOf(if (storeIdx == storeOptions.lastIndex) e.store else "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (e.id == 0L) "Nová položka / akcia" else "Upraviť položku") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField2("Názov", name, { name = it }, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumField("Množstvo", qty, { qty = it }, Modifier.weight(1f))
                        val liquidShop = Kinds.isLiquid(name)
                        val unitsShop = Kinds.uomsFor(liquidShop)
                        val effUom = Kinds.fitUom(Uom.entries[uomIdx], liquidShop)
                        Picker("Jednotka", unitsShop.map { it.label }, unitsShop.indexOf(effUom).coerceAtLeast(0), { uomIdx = unitsShop[it].ordinal }, Modifier.weight(1f))
                    }
                    NumField("Cena za 1 jednotku (na regáli)", price, { price = it }, Modifier.fillMaxWidth(), suffix = "€")
                    Picker("Množstvová akcia", multiOptions, multiIdx, { multiIdx = it }, Modifier.fillMaxWidth())
                    if (multiIdx == 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NumField("Kúpiš", buyTxt, { buyTxt = it }, Modifier.weight(1f))
                            NumField("Zaplatíš", payTxt, { payTxt = it }, Modifier.weight(1f))
                        }
                    }
                    NumField("Bežná cena za jednotku (nepovinné)", regular, { regular = it }, Modifier.fillMaxWidth(), suffix = "€")
                    Picker("Obchod", storeOptions, storeIdx, { storeIdx = it }, Modifier.fillMaxWidth())
                    if (storeIdx == storeOptions.lastIndex) {
                        TextField2("Názov obchodu", customStore, { customStore = it }, Modifier.fillMaxWidth())
                    }
                    DateField("Akcia platí do", until, { until = it }, Modifier.fillMaxWidth())
                    TextField2("Poznámka (napr. leták Billa, max. 3 ks)", note, { note = it }, Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val storeName = when (storeIdx) {
                        0 -> ""
                        storeOptions.lastIndex -> customStore.trim()
                        else -> storeOptions[storeIdx]
                    }
                    val mb = when (multiIdx) {
                        1 -> 2.0 to 1.0
                        2 -> 3.0 to 2.0
                        3 -> 4.0 to 3.0
                        4 -> (Calc.parse(buyTxt) ?: 0.0) to (Calc.parse(payTxt) ?: 0.0)
                        else -> 0.0 to 0.0
                    }
                    val qtyNow = Calc.parse(qty) ?: e.qty
                    val saved = e.copy(
                            buyQty = mb.first, payQty = mb.second,
                            name = name.trim(), qty = if (mb.first > 1.0 && qtyNow < mb.first) mb.first else qtyNow,
                            uom = Kinds.fitUom(Uom.entries[uomIdx], Kinds.isLiquid(name)), price = Calc.parse(price) ?: 0.0,
                            regularPrice = Calc.parse(regular) ?: 0.0, store = storeName, dealUntil = until, note = note.trim()
                    )
                    val newId = store.saveShop(saved)
                    cmp = Prices.compareDeals(db, saved.copy(id = newId))
                    editing = null
                }) { Text("Uložiť") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Zrušiť") } }
        )
    }
}
