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
import androidx.compose.material3.LinearProgressIndicator
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
import sk.spajza.app.data.BuiltInRecipes
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Emergency
import sk.spajza.app.data.RecipeEngine
import sk.spajza.app.data.placeName
import sk.spajza.app.data.stock

@Composable
fun HomeScreen() {
    val nav = LocalNav.current
    val db = useDb()
    val stock = remember(db) { db.stock().filter { it.qty > 0 } }
    val itemsById = remember(db) { db.items.associateBy { it.id } }
    val today = Calc.today()

    val soon = remember(db) {
        db.batches.filter { it.expiry != null && it.qty > 0 && (it.expiry - today) <= 7 }
            .sortedBy { it.expiry }
    }
    val low = remember(db) { stock.filter { it.low } }
    val totalValue = stock.sumOf { it.value }
    val report = remember(db) { Emergency.report(db) }
    val best = remember(db) {
        RecipeEngine.match(BuiltInRecipes.all + db.recipes, stock).firstOrNull { it.missing == 0 && it.needed > 0 }
    }

    ScreenScaffold(
        title = "Špajza",
        actions = {
            IconButton(onClick = { nav.push(Dest.Scan) }) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Skenovať") }
            IconButton(onClick = { nav.push(Dest.SettingsScreen) }) { Icon(Icons.Filled.Settings, contentDescription = "Nastavenia") }
        }
    ) { pad ->
        PageList(pad) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("produktov doma", stock.size.toString(), modifier = Modifier.weight(1f))
                    StatTile("hodnota zásob", Calc.money(totalValue), Pal.Green, Modifier.weight(1f))
                    StatTile("treba spotrebovať", soon.size.toString(), if (soon.isEmpty()) Pal.Green else Pal.Orange, Modifier.weight(1f))
                }
            }
            if (db.items.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { nav.push(Dest.QuickAdd) }, modifier = Modifier.weight(1f)) { Text("Pridať potravinu") }
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
                            OutlinedButton(onClick = { nav.push(Dest.QuickAdd) }) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Pridať ručne")
                            }
                        }
                    }
                }
            }
            if (soon.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        SectionTitle("Čo sa kazí")
                        TextButton(onClick = { nav.push(Dest.Expiry) }) { Text("Všetko") }
                    }
                }
                items(soon.take(5).size) { idx ->
                    val b = soon[idx]
                    val it0 = itemsById[b.itemId]
                    if (it0 != null) {
                        SCard(onClick = { nav.push(Dest.ItemDetail(it0.id)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ItemThumb(it0, 44.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(it0.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Muted(Calc.qty(b.qty, it0.uom) + (if (db.placeName(b.placeId).isNotBlank()) " · " + db.placeName(b.placeId) else ""))
                                }
                                ExpiryBadge(b.expiry)
                            }
                        }
                    }
                }
            }
            if (best != null) {
                item {
                    SectionTitle("Dnes môžete uvariť")
                    SCard(onClick = { nav.push(Dest.RecipeDetail(best.recipe.id)) }) {
                        Text(best.recipe.name, fontWeight = FontWeight.Medium)
                        Muted(best.recipe.minutes.toString() + " min · všetky suroviny máte doma" +
                            (if (best.soonUsed > 0) " · využije potraviny s blížiacou sa expiráciou" else ""))
                    }
                }
            }
            if (low.isNotEmpty()) {
                item { SectionTitle("Dochádza") }
                items(low.take(5).size) { idx ->
                    val s = low[idx]
                    SCard(onClick = { nav.push(Dest.ItemDetail(s.item.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ItemThumb(s.item, 40.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(s.item.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                Muted("Zostáva " + Calc.qty(s.qty, s.item.uom) + " (minimum " + Calc.qty(s.item.minQty, s.item.uom) + ")")
                            }
                        }
                    }
                }
            }
            item {
                SectionTitle("Núdzové zásoby")
                SCard(onClick = { nav.push(Dest.Emergency) }) {
                    val dFood = report.foodDays
                    val dWater = report.waterDays
                    Text(
                        "Jedlo: " + Calc.num(dFood, 1) + " dní · Voda: " + Calc.num(dWater, 1) + " dní",
                        fontWeight = FontWeight.Medium
                    )
                    Muted("pre " + report.persons + " " + daysText(report.persons.toDouble(), "osobu", "osoby", "osôb") +
                        ", cieľ " + report.targetDays + " dní")
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (report.days / report.targetDays.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (report.ok) Pal.Green else Pal.Amber
                    )
                }
            }
        }
    }
}
