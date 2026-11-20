package sk.spajza.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Pairing
import sk.spajza.app.data.MoveType
import sk.spajza.app.data.ShopItem
import sk.spajza.app.data.placeName
import sk.spajza.app.data.stock

private const val HOME_LIMIT = 6

private fun openedText(days: Long): String = when {
    days <= 0L -> "otvorené dnes"
    days == 1L -> "otvorené včera"
    else -> "otvorené pred $days dňami"
}

/** Úvodná obrazovka: len to, čo treba riešiť (čoskoro expiruje, otvorené, dochádza). */
@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    val stock = remember(db) { db.stock().filter { it.qty > 0 } }
    val itemsById = remember(db) { db.items.associateBy { it.id } }
    val today = Calc.today()

    // Neotvorené balenia, ktoré expirujú do 7 dní (alebo už expirovali).
    val soon = remember(db) {
        db.batches.filter { it.opened == null && it.expiry != null && it.qty > 0 && (it.expiry - today) <= 7 }
            .sortedBy { it.expiry }
    }
    // Otvorené balenia: spotrebúvajú sa ako prvé.
    val opened = remember(db) {
        db.batches.filter { it.opened != null && it.qty > 0 && itemsById.containsKey(it.itemId) }
            .sortedBy { it.expiry ?: Long.MAX_VALUE }
    }
    val low = remember(db) { Pairing.lowGroups(db) }
    val allClear = db.items.isNotEmpty() && soon.isEmpty() && opened.isEmpty() && low.isEmpty()

    ScreenScaffold(
        title = "Špajza",
        actions = {
            IconButton(onClick = { nav.push(Dest.Scan) }) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Skenovať") }
            IconButton(onClick = { nav.push(Dest.SettingsScreen) }) { Icon(Icons.Filled.Settings, contentDescription = "Nastavenia") }
        }
    ) { pad ->
        PageList(pad) {
            if (db.items.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { nav.push(Dest.QuickAdd()) }, modifier = Modifier.weight(1f)) { Text("Pridať potravinu") }
                        OutlinedButton(onClick = { nav.push(Dest.Cook) }, modifier = Modifier.weight(1f)) { Text("Navarené") }
                    }
                }
            }
            if (db.items.isEmpty()) {
                item {
                    SCard {
                        Text("Vitajte v Špajzi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Muted("Začnite naskenovaním čiarového kódu prvého produktu alebo ho pridajte ručne. Údaje sa uložia len vo vašom telefóne.")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { nav.push(Dest.Scan) }) {
                                Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Skenovať")
                            }
                            OutlinedButton(onClick = { nav.push(Dest.QuickAdd()) }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Pridať ručne")
                            }
                        }
                    }
                }
            }
            if (allClear) {
                item {
                    SCard {
                        Text("Všetko je v poriadku", fontWeight = FontWeight.SemiBold, color = Pal.Green)
                        Muted("Do týždňa nič neexpiruje, nič nie je otvorené a nič nedochádza.")
                    }
                }
            }

            // ---- Čoskoro expiruje ----
            if (soon.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionTitle("Čoskoro expiruje (" + soon.size + ")")
                        TextButton(onClick = { nav.push(Dest.Expiry) }) { Text("Všetko") }
                    }
                }
                items(soon.take(HOME_LIMIT).size) { idx ->
                    val b = soon[idx]
                    val it0 = itemsById[b.itemId]
                    if (it0 != null) {
                        SCard(onClick = { nav.push(Dest.ItemDetail(it0.id)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ItemThumb(it0, 44.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(it0.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Muted(Calc.qty(it0, b.qty) + (if (db.placeName(b.placeId).isNotBlank()) " · " + db.placeName(b.placeId) else ""))
                                }
                                ExpiryBadge(b.expiry)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { store.withdraw(it0.id, b.qty, MoveType.CONSUME, b.id) }) { Text("Spotrebované") }
                                TextButton(onClick = { store.withdraw(it0.id, b.qty, MoveType.DISCARD, b.id) }) { Text("Vyhodiť") }
                            }
                        }
                    }
                }
                if (soon.size > HOME_LIMIT) {
                    item { Muted("… a ďalších " + (soon.size - HOME_LIMIT) + " v zozname Expirácia.") }
                }
            }

            // ---- Otvorené ----
            if (opened.isNotEmpty()) {
                item { SectionTitle("Otvorené (" + opened.size + ")") }
                items(opened.take(HOME_LIMIT).size) { idx ->
                    val b = opened[idx]
                    val it0 = itemsById[b.itemId]
                    if (it0 != null) {
                        val ago = openedText(b.opened?.let { o -> today - o } ?: 0L)
                        SCard(onClick = { nav.push(Dest.ItemDetail(it0.id)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ItemThumb(it0, 44.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(it0.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Muted(
                                        Calc.qty(it0, b.qty) + " · " + ago +
                                            (if (db.placeName(b.placeId).isNotBlank()) " · " + db.placeName(b.placeId) else "")
                                    )
                                }
                                ExpiryBadge(b.expiry)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { store.withdraw(it0.id, b.qty, MoveType.CONSUME, b.id) }) { Text("Spotrebované") }
                            }
                        }
                    }
                }
                if (opened.size > HOME_LIMIT) {
                    item { Muted("… a ďalších " + (opened.size - HOME_LIMIT) + " otvorených v Zásobách.") }
                }
            }

            // ---- Dochádza ----
            if (low.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionTitle("Dochádza (" + low.size + ")")
                        TextButton(onClick = { nav.goTab(Tab.Shop) }) { Text("Nákup") }
                    }
                }
                items(low.take(HOME_LIMIT).size) { idx ->
                    val g = low[idx]
                    val s = g.primary
                    SCard(onClick = { nav.push(Dest.ItemDetail(s.item.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ItemThumb(s.item, 40.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(if (g.grouped) g.name else s.item.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                Muted("Zostáva " + Calc.qty(s.item, g.qty) + " (minimum " + Calc.qty(s.item, g.minQty) + ")" +
                                    (if (g.grouped) " · " + g.members.size + " produkty" else ""))
                            }
                        }
                        val onList = Pairing.onShopList(db, g, includeChecked = false)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (onList) {
                                Muted("V nákupnom zozname", Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp))
                            } else {
                                TextButton(onClick = {
                                    val q = Math.max(1.0, Math.ceil(g.minQty * 2 - g.qty))
                                    store.saveShop(ShopItem(itemId = s.item.id, name = s.item.name, qty = q, uom = s.item.uom, price = Calc.unitPrice(s.item)))
                                }) { Text("Pridať do nákupu") }
                            }
                        }
                    }
                }
                if (low.size > HOME_LIMIT) {
                    item { Muted("… a ďalších " + (low.size - HOME_LIMIT) + " v Zásobách.") }
                }
            }
        }
    }
}
