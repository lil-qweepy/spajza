package sk.spajza.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sk.spajza.app.data.Calc
import sk.spajza.app.data.MoveType
import sk.spajza.app.data.StockItem
import sk.spajza.app.data.Uom
import sk.spajza.app.data.placeName
import sk.spajza.app.data.stock

private val SORTS = listOf("Názov", "Expirácia", "Množstvo", "Hodnota", "Naposledy pridané")

@Composable
fun InventoryScreen() {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    var query by rememberSaveable { mutableStateOf("") }
    var placeFilter by rememberSaveable { mutableStateOf(-1L) }
    var catFilter by rememberSaveable { mutableStateOf("") }
    var showEmpty by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var grouped by rememberSaveable { mutableStateOf(false) }
    var openFor by remember { mutableStateOf<StockItem?>(null) }
    LaunchedEffect(nav.pendingPlace) {
        if (nav.pendingPlace >= 0) {
            placeFilter = nav.pendingPlace
            catFilter = ""
            nav.pendingPlace = -1L
        }
    }

    val all = remember(db) { db.stock() }
    val list = remember(db, query, placeFilter, catFilter, showEmpty, sort) {
        val q = Calc.norm(query.trim())
        val filtered = all.filter { s ->
            (showEmpty || s.qty > 0) &&
                (q.isEmpty() || Calc.norm(s.item.name + " " + s.item.brand + " " + s.item.kind + " " + s.item.ean).contains(q)) &&
                (catFilter.isEmpty() || s.item.category == catFilter) &&
                (placeFilter < 0 || s.batches.any { it.placeId == placeFilter } || (s.batches.isEmpty() && s.item.placeId == placeFilter))
        }
        when (sort) {
            1 -> filtered.sortedBy { it.nearest ?: Long.MAX_VALUE }
            2 -> filtered.sortedByDescending { it.qty }
            3 -> filtered.sortedByDescending { it.value }
            4 -> filtered.sortedByDescending { it.item.created }
            else -> filtered
        }
    }

    ScreenScaffold(
        title = "Zásoby",
        actions = {
            IconButton(onClick = { nav.push(Dest.Cook) }) { Icon(Icons.Filled.Restaurant, contentDescription = "Navarené – odpočítať suroviny") }
            IconButton(onClick = { nav.push(Dest.Scan) }) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Skenovať") }
            IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, contentDescription = "Radenie") }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                SORTS.forEachIndexed { idx, name ->
                    DropdownMenuItem(
                        text = { Text(if (idx == sort) "✓ " + name else name) },
                        onClick = { sort = idx; sortMenu = false }
                    )
                }
            }
        },
        floating = {
            ExtendedFloatingActionButton(
                onClick = { nav.push(Dest.QuickAdd) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Pridať") }
            )
        }
    ) { pad ->
        PageList(pad) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchField(query, { query = it }, Modifier.fillMaxWidth(), "Názov, značka alebo EAN")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = placeFilter < 0 && catFilter.isEmpty(), onClick = { placeFilter = -1L; catFilter = "" }, label = { Text("Všetko") })
                        }
                        items(db.places.size) { idx ->
                            val p = db.places[idx]
                            FilterChip(selected = placeFilter == p.id, onClick = { placeFilter = if (placeFilter == p.id) -1L else p.id }, label = { Text(p.name) })
                        }
                        item {
                            FilterChip(selected = false, onClick = { nav.push(Dest.Lists) }, label = { Text("＋ Upraviť miesta") })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(db.categories.size) { idx ->
                            val c = db.categories[idx]
                            FilterChip(selected = catFilter == c, onClick = { catFilter = if (catFilter == c) "" else c }, label = { Text(c) })
                        }
                        item {
                            FilterChip(selected = showEmpty, onClick = { showEmpty = !showEmpty }, label = { Text("Aj vypredané") })
                        }
                        item {
                            FilterChip(selected = grouped, onClick = { grouped = !grouped }, label = { Text("Zoskupiť podľa miesta") })
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        if (all.isEmpty()) "Zatiaľ nič nemáte" else "Nič sa nenašlo",
                        if (all.isEmpty()) "Naskenujte produkt alebo ho pridajte ručne." else "Zmeňte vyhľadávanie alebo filter."
                    )
                }
            }
            val rows: List<Any> = if (grouped) {
                val out = ArrayList<Any>()
                val groups = list.groupBy { st -> db.placeName(st.batches.firstOrNull()?.placeId ?: st.item.placeId).ifBlank { "Bez miesta" } }
                for (g in groups.keys.sorted()) {
                    out.add(g)
                    out.addAll(groups.getValue(g))
                }
                out
            } else {
                list
            }
            items(rows.size) { idx ->
                val r = rows[idx]
                if (r is String) {
                    SectionTitle(r)
                } else if (r is StockItem) {
                    InventoryRow(
                        r, db.placeName(r.batches.firstOrNull()?.placeId ?: r.item.placeId),
                        onOpen = if (r.qty > 0 && r.batches.any { it.opened == null }) ({ openFor = r }) else null,
                        onMinus = {
                            store.withdraw(r.item.id, minOf(stepFor(r.item.uom), r.qty), MoveType.CONSUME, null, "Rýchle odobratie")
                        },
                        onPlus = {
                            val last = r.batches.lastOrNull()
                            if (last != null) store.adjustBatch(last.id, last.qty + stepFor(r.item.uom), "Rýchle pridanie")
                            else store.addStock(r.item.id, stepFor(r.item.uom), null, Calc.today(), r.item.placeId, "Rýchle pridanie")
                        }
                    ) { nav.push(Dest.ItemDetail(r.item.id)) }
                }
            }
        }
    }

    val opening = openFor
    if (opening != null) {
        val ob = opening.batches.firstOrNull { it.opened == null }
        if (ob != null) OpenPackDialog(opening.item, ob) { openFor = null }
    }
}

private fun stepFor(u: Uom): Double = when (u) {
    Uom.PCS, Uom.PACK -> 1.0
    Uom.KG -> 0.1
    Uom.G -> 100.0
    Uom.L -> 0.5
    Uom.ML -> 100.0
}

@Composable
private fun InventoryRow(s: StockItem, place: String, onOpen: (() -> Unit)?, onMinus: () -> Unit, onPlus: () -> Unit, onClick: () -> Unit) {
    SCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemThumb(s.item, 52.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(s.item.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                val sub = ArrayList<String>()
                if (s.item.brand.isNotBlank()) sub.add(s.item.brand)
                if (place.isNotBlank()) sub.add(place)
                if (s.value > 0) sub.add(Calc.money(s.value))
                if (s.batches.any { it.opened != null }) sub.add("otvorené")
                if (sub.isNotEmpty()) Muted(sub.joinToString(" · "))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Calc.qty(s.qty, s.item.uom), fontWeight = FontWeight.Bold)
                if (s.qty > 0) ExpiryBadge(s.nearest) else Muted("vypredané")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Muted("krok " + Calc.num(stepFor(s.item.uom), 1) + " " + s.item.uom.label)
            if (onOpen != null) TextButton(onClick = onOpen) { Text("Otvoriť") }
            IconButton(enabled = s.qty > 0, onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = "Ubrať") }
            IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "Pridať") }
        }
    }
}
