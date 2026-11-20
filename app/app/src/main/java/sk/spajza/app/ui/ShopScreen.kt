package sk.spajza.app.ui

import androidx.compose.foundation.clickable
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
import sk.spajza.app.data.ShopItem
import sk.spajza.app.data.Kinds
import sk.spajza.app.data.Uom
import sk.spajza.app.data.stock

@Composable
fun ShopScreen() {
    val store = LocalStore.current
    val db = useDb()
    var newName by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ShopItem?>(null) }
    var message by remember { mutableStateOf("") }

    val stock = remember(db) { db.stock() }
    val suggestions = remember(db) {
        stock.filter { it.low && db.shop.none { s -> s.itemId == it.item.id } }
    }
    val total = db.shop.sumOf { it.qty * it.price }
    val totalChecked = db.shop.filter { it.checked }.sumOf { it.qty * it.price }
    val checkedCount = db.shop.count { it.checked }

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
                }
            }
            if (suggestions.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionTitle("Dochádza – navrhujem kúpiť")
                        for (s in suggestions) {
                            val q = Math.max(1.0, Math.ceil(s.item.minQty * 2 - s.qty))
                            AssistChip(
                                onClick = {
                                    store.saveShop(
                                        ShopItem(itemId = s.item.id, name = s.item.name, qty = q, uom = s.item.uom, price = Calc.unitPrice(s.item))
                                    )
                                },
                                label = { Text(s.item.name + " (zostáva " + Calc.qty(s.qty, s.item.uom) + ")  + " + Calc.num(q)) }
                            )
                        }
                    }
                }
            }
            if (db.shop.isEmpty()) {
                item { EmptyState("Zoznam je prázdny", "Pridajte položku alebo ju pridajte z detailu produktu.") }
            } else {
                item {
                    SCard {
                        InfoRow("Odhadovaná cena spolu", Calc.money(total))
                        InfoRow("Označené v košíku (" + checkedCount + ")", Calc.money(totalChecked))
                    }
                }
            }
            items(db.shop.size) { idx ->
                val s = db.shop[idx]
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
                                    (if (s.price > 0) " · " + Calc.money(s.price) + " / " + s.uom.label + " = " + Calc.money(s.price * s.qty) else "")
                            )
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
                        if (message.isNotBlank()) Muted(message)
                        Muted("Pri prenose sa položky pridajú do zásob s dnešným dátumom nákupu. Expiráciu doplníte v detaile produktu.")
                    }
                }
            }
        }
    }

    val e = editing
    if (e != null) {
        var qty by remember(e.id) { mutableStateOf(Calc.edit(e.qty)) }
        var price by remember(e.id) { mutableStateOf(Calc.edit(e.price)) }
        var name by remember(e.id) { mutableStateOf(e.name) }
        var uomIdx by remember(e.id) { mutableStateOf(e.uom.ordinal) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Upraviť položku") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextField2("Názov", name, { name = it }, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumField("Množstvo", qty, { qty = it }, Modifier.weight(1f))
                        val liquidShop = Kinds.isLiquid(name)
                        val unitsShop = Kinds.uomsFor(liquidShop)
                        val effUom = Kinds.fitUom(Uom.entries[uomIdx], liquidShop)
                        Picker("Jednotka", unitsShop.map { it.label }, unitsShop.indexOf(effUom).coerceAtLeast(0), { uomIdx = unitsShop[it].ordinal }, Modifier.weight(1f))
                    }
                    NumField("Cena za jednotku", price, { price = it }, Modifier.fillMaxWidth(), suffix = "€")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.saveShop(
                        e.copy(
                            name = name.trim().ifEmpty { e.name }, qty = Calc.parse(qty) ?: e.qty,
                            uom = Kinds.fitUom(Uom.entries[uomIdx], Kinds.isLiquid(name)), price = Calc.parse(price) ?: 0.0
                        )
                    )
                    editing = null
                }) { Text("Uložiť") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Zrušiť") } }
        )
    }
}
