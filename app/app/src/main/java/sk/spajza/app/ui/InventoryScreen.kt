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
import sk.spajza.app.data.buildStock
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
    var useFor by remember { mutableStateOf<StockItem?>(null) }
    var addFor by remember { mutableStateOf<StockItem?>(null) }
    var draftsOnly by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(nav.pendingPlace) {
        if (nav.pendingPlace >= 0) {
            placeFilter = nav.pendingPlace
            catFilter = ""
            nav.pendingPlace = -1L
        }
    }

    val all = remember(db) { db.stock() }
    val draftCount = all.count { it.item.draft }
    val list = remember(db, query, placeFilter, catFilter, showEmpty, sort, draftsOnly) {
        val q = Calc.norm(query.trim())
        val filtered = all.filter { s ->
            (showEmpty || s.qty > 0 || s.item.draft) && (!draftsOnly || s.item.draft) &&
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
                onClick = { nav.push(Dest.QuickAdd()) },
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
                        if (draftCount > 0) {
                            item {
                                FilterChip(selected = draftsOnly, onClick = { draftsOnly = !draftsOnly }, label = { Text("Doplniť údaje ($draftCount)") })
                            }
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
            // Rovnaky produkt na viacerych miestach (napr. maso v chladnicke aj v mrazicke): pri zoskupeni
            // alebo filtri podla miesta sa zobrazi zvlast pre kazde miesto, inak ako jeden riadok s rozpisom.
            val split = grouped || placeFilter >= 0
            val slices = ArrayList<StockItem>()
            for (st in list) {
                if (!split || st.batches.isEmpty()) {
                    slices.add(st)
                } else {
                    val byPlace = st.batches.groupBy { it.placeId }
                    for ((pid, bs) in byPlace) {
                        if (placeFilter >= 0 && pid != placeFilter) continue
                        slices.add(buildStock(st.item, bs))
                    }
                }
            }
            fun placeOf(st: StockItem): Long? = st.batches.firstOrNull()?.placeId ?: st.item.placeId
            val rows: List<Any> = if (grouped) {
                val out = ArrayList<Any>()
                val groups = slices.groupBy { st -> db.placeName(placeOf(st)).ifBlank { "Bez miesta" } }
                for (g in groups.keys.sorted()) {
                    out.add(g)
                    out.addAll(groups.getValue(g))
                }
                out
            } else {
                slices
            }
            items(rows.size) { idx ->
                val r = rows[idx]
                if (r is String) {
                    SectionTitle(r)
                } else if (r is StockItem) {
                    val places = r.batches.groupBy { it.placeId }
                    val breakdown = if (split || places.size < 2) "" else places.entries
                        .joinToString(" · ") { (pid, bs) ->
                            db.placeName(pid).ifBlank { "Bez miesta" } + " " + Calc.qty(bs.sumOf { it.qty }, r.item.uom)
                        }
                    InventoryRow(
                        r, if (breakdown.isNotEmpty()) "" else db.placeName(placeOf(r)), breakdown,
                        onOpen = if (r.qty > 0 && r.batches.any { it.opened == null }) ({ openFor = r }) else null,
                        onUse = if (r.qty > 0 && (!r.item.uom.isCountable || r.item.net > 0)) ({ useFor = r }) else null,
                        onMinus = if (r.item.uom.isCountable) ({
                            store.withdrawIn(r.item.id, minOf(1.0, r.qty), MoveType.CONSUME, r.batches.map { it.id }, "Rýchle odobratie")
                        }) else null,
                        onPlus = {
                            if (r.item.uom.isCountable) {
                                val last = r.batches.lastOrNull()
                                if (last != null) store.adjustBatch(last.id, last.qty + 1.0, "Rýchle pridanie")
                                else store.addStock(r.item.id, 1.0, null, Calc.today(), r.item.placeId, "Rýchle pridanie")
                            } else {
                                addFor = r
                            }
                        }
                    ) { nav.push(Dest.ItemDetail(r.item.id)) }
                }
            }
        }
    }

    val adding = addFor
    if (adding != null) {
        val slicePlace = adding.batches.firstOrNull()?.placeId ?: adding.item.placeId
        AddStockDialog(db, adding.item.copy(placeId = slicePlace), onDismiss = { addFor = null }) { q, ex, bought, place ->
            store.addStock(adding.item.id, q, ex, bought, place)
        }
    }

    val using = useFor
    if (using != null) {
        UseDialog(using.item, using.qty, onDismiss = { useFor = null }) { q, type ->
            store.withdrawIn(using.item.id, q, type, using.batches.map { it.id })
        }
    }

    val opening = openFor
    if (opening != null) {
        val ob = opening.batches.firstOrNull { it.opened == null }
        if (ob != null) OpenPackDialog(opening.item, ob) { openFor = null }
    }
}

@Composable
private fun InventoryRow(s: StockItem, place: String, breakdown: String, onOpen: (() -> Unit)?, onUse: (() -> Unit)?, onMinus: (() -> Unit)?, onPlus: () -> Unit, onClick: () -> Unit) {
    SCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemThumb(s.item, 52.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(s.item.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                val sub = ArrayList<String>()
                if (s.item.brand.isNotBlank()) sub.add(s.item.brand)
                if (place.isNotBlank()) sub.add(place)
                if (s.value > 0) sub.add(Calc.money(s.value))
                if (s.item.draft) sub.add("doplniť údaje")
                if (s.batches.any { it.opened != null }) sub.add("otvorené")
                if (sub.isNotEmpty()) Muted(sub.joinToString(" · "))
                if (breakdown.isNotEmpty()) Muted(breakdown)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Calc.qty(s.qty, s.item.uom), fontWeight = FontWeight.Bold)
                if (s.qty > 0) ExpiryBadge(s.nearest) else Muted("vypredané")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (onOpen != null) TextButton(onClick = onOpen) { Text("Otvoriť") }
            if (onUse != null) TextButton(onClick = onUse) { Text("Ubrať…") }
            if (onMinus != null) IconButton(enabled = s.qty > 0, onClick = onMinus) { Icon(Icons.Filled.Remove, contentDescription = "Ubrať jeden") }
            IconButton(onClick = onPlus) { Icon(Icons.Filled.Add, contentDescription = "Pridať") }
        }
    }
}
