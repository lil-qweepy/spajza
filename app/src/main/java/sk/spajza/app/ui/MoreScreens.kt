package sk.spajza.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Kinds
import sk.spajza.app.data.MoveType
import sk.spajza.app.data.Place
import sk.spajza.app.data.stock

// ---------- Viac ----------

@Composable
fun MoreScreen() {
    val nav = LocalNav.current
    ScreenScaffold(title = "Viac") { pad ->
        PageList(pad) {
            item { MenuRow(Icons.Filled.Receipt, "Pridať nákup z bločku", "Odfotíte bloček a položky sa pridajú") { nav.push(Dest.Receipt) } }
            item { MenuRow(Icons.Filled.Event, "Expirácia", "Čo sa kazí a kedy") { nav.push(Dest.Expiry) } }
            item { MenuRow(Icons.Filled.Shield, "Núdzové zásoby", "Na koľko dní vystačíte") { nav.push(Dest.Emergency) } }
            item { MenuRow(Icons.Filled.Lightbulb, "Tipy a kontrolný zoznam", "Čo robiť v krízových situáciách") { nav.push(Dest.Tips) } }
            item { MenuRow(Icons.Filled.LocationOn, "Miesta, kategórie a druhy", "Chladnička, špajza, pivnica…") { nav.push(Dest.Lists) } }
            item { MenuRow(Icons.Filled.BarChart, "Štatistiky", "Hodnota zásob, plytvanie, spotreba") { nav.push(Dest.Stats) } }
            item { MenuRow(Icons.Filled.History, "História pohybov", "Všetko, čo ste pridali alebo odobrali") { nav.push(Dest.Moves) } }
            item { MenuRow(Icons.Filled.Backup, "Záloha a obnova", "Export a import súboru") { nav.push(Dest.BackupScreen) } }
            item { MenuRow(Icons.Filled.Settings, "Nastavenia", "Upozornenia, domácnosť, kalórie") { nav.push(Dest.SettingsScreen) } }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    SCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Muted(sub)
            }
        }
    }
}

// ---------- Miesta a kategorie ----------

@Composable
fun ListsScreen() {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    var placeDialog by remember { mutableStateOf<Place?>(null) }
    var newPlace by remember { mutableStateOf(false) }
    var catDialog by remember { mutableStateOf<String?>(null) }
    var newCat by remember { mutableStateOf(false) }
    var kindDialog by remember { mutableStateOf<String?>(null) }
    var newKind by remember { mutableStateOf(false) }
    var deletePlace by remember { mutableStateOf<Place?>(null) }
    var deleteCat by remember { mutableStateOf<String?>(null) }
    var deleteKind by remember { mutableStateOf<String?>(null) }

    ScreenScaffold(title = "Miesta, kategórie a druhy", onBack = { nav.pop() }) { pad ->
        PageList(pad) {
            item {
                Muted("Všetko si môžete pridať, premenovať, zmazať a zoradiť šípkami. Klepnutím na miesto zobrazíte jeho zásoby.")
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionTitle("Miesta uloženia")
                    IconButton(onClick = { newPlace = true }) { Icon(Icons.Filled.Add, contentDescription = "Pridať miesto") }
                }
            }
            items(db.places.size) { idx ->
                val p = db.places[idx]
                val count = db.batches.count { it.placeId == p.id && it.qty > 0 }
                SCard(onClick = { nav.pendingPlace = p.id; nav.goTab(Tab.Inventory) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Medium)
                            Muted("Šarží: $count")
                        }
                        IconButton(enabled = idx > 0, onClick = { store.movePlace(p.id, -1) }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Vyššie") }
                        IconButton(enabled = idx < db.places.size - 1, onClick = { store.movePlace(p.id, 1) }) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Nižšie") }
                        IconButton(onClick = { placeDialog = p }) { Icon(Icons.Filled.Edit, contentDescription = "Premenovať") }
                        IconButton(onClick = { deletePlace = p }) { Icon(Icons.Filled.Delete, contentDescription = "Zmazať") }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionTitle("Kategórie")
                    IconButton(onClick = { newCat = true }) { Icon(Icons.Filled.Add, contentDescription = "Pridať kategóriu") }
                }
            }
            items(db.categories.size) { idx ->
                val c = db.categories[idx]
                val count = db.items.count { it.category == c }
                SCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c, fontWeight = FontWeight.Medium)
                            Muted("Produktov: $count")
                        }
                        IconButton(enabled = idx > 0, onClick = { store.moveCategory(c, -1) }) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Vyššie") }
                        IconButton(enabled = idx < db.categories.size - 1, onClick = { store.moveCategory(c, 1) }) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Nižšie") }
                        IconButton(onClick = { catDialog = c }) { Icon(Icons.Filled.Edit, contentDescription = "Premenovať") }
                        if (c != "Iné") {
                            IconButton(onClick = { deleteCat = c }) { Icon(Icons.Filled.Delete, contentDescription = "Zmazať") }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    SectionTitle("Vlastné druhy potravín")
                    IconButton(onClick = { newKind = true }) { Icon(Icons.Filled.Add, contentDescription = "Pridať druh") }
                }
                Muted("Druh pomáha receptom nájsť surovinu (napr. „Múka“). Vstavané druhy (" + Kinds.names.size + ") sú vždy k dispozícii.")
            }
            items(db.kinds.size) { idx ->
                val k = db.kinds[idx]
                val count = db.items.count { it.kind == k }
                SCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(k, fontWeight = FontWeight.Medium)
                            Muted("Produktov: $count")
                        }
                        IconButton(onClick = { kindDialog = k }) { Icon(Icons.Filled.Edit, contentDescription = "Premenovať") }
                        IconButton(onClick = { deleteKind = k }) { Icon(Icons.Filled.Delete, contentDescription = "Zmazať") }
                    }
                }
            }
        }
    }

    if (newPlace) NameDialog("Nové miesto", "", onDismiss = { newPlace = false }) { store.savePlace(0, it) }
    val pd = placeDialog
    if (pd != null) NameDialog("Premenovať miesto", pd.name, onDismiss = { placeDialog = null }) { store.savePlace(pd.id, it) }
    if (newCat) NameDialog("Nová kategória", "", onDismiss = { newCat = false }) { store.saveCategory(null, it) }
    val cd = catDialog
    if (cd != null) NameDialog("Premenovať kategóriu", cd, onDismiss = { catDialog = null }) { store.saveCategory(cd, it) }
    if (newKind) NameDialog("Nový druh potraviny", "", onDismiss = { newKind = false }) { store.saveKind(null, it) }
    val kd = kindDialog
    if (kd != null) NameDialog("Premenovať druh", kd, onDismiss = { kindDialog = null }) { store.saveKind(kd, it) }
    val dp0 = deletePlace
    if (dp0 != null) {
        ConfirmDialog(
            "Zmazať miesto?", "Potraviny z miesta \"" + dp0.name + "\" zostanú v zásobách, len bez určeného miesta.", "Zmazať",
            onConfirm = { store.deletePlace(dp0.id) }, onDismiss = { deletePlace = null }
        )
    }
    val dc = deleteCat
    if (dc != null) {
        ConfirmDialog(
            "Zmazať kategóriu?", "Produkty z kategórie \"" + dc + "\" sa presunú do kategórie Iné.", "Zmazať",
            onConfirm = { store.deleteCategory(dc) }, onDismiss = { deleteCat = null }
        )
    }
    val dk = deleteKind
    if (dk != null) {
        ConfirmDialog(
            "Zmazať druh?", "Produktom s druhom \"" + dk + "\" sa druh vymaže, recepty ich budú hľadať podľa názvu.", "Zmazať",
            onConfirm = { store.deleteKind(dk) }, onDismiss = { deleteKind = null }
        )
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TextField2("Názov", text, { text = it }, Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onOk(text.trim()); onDismiss() }) { Text("Uložiť") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

// ---------- Historia pohybov ----------

@Composable
fun MovesScreen() {
    val nav = LocalNav.current
    val db = useDb()
    val list = db.moves.take(300)
    ScreenScaffold(title = "História pohybov", onBack = { nav.pop() }) { pad ->
        PageList(pad) {
            if (list.isEmpty()) item { EmptyState("Zatiaľ nič", "Tu uvidíte všetky zmeny zásob.") }
            items(list.size) { idx ->
                val m = list[idx]
                val color = when (m.type) {
                    MoveType.ADD -> Pal.Green
                    MoveType.DISCARD -> Pal.Red
                    MoveType.MOVE -> Pal.Blue
                    else -> Pal.Amber
                }
                SCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, fontWeight = FontWeight.Medium, maxLines = 1)
                            Muted(
                                m.type.label + " · " + Calc.dateTime(m.time) +
                                    (if (m.from.isNotBlank() || m.to.isNotBlank()) " · " + m.from + " → " + m.to else "") +
                                    (if (m.note.isNotBlank()) " · " + m.note else "")
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text((if (m.qty > 0) "+" else "") + Calc.qty(m.qty, m.uom), color = color, fontWeight = FontWeight.Bold)
                            if (m.value != 0.0 && m.type != MoveType.MOVE) Muted(Calc.money(kotlin.math.abs(m.value)))
                        }
                    }
                }
            }
        }
    }
}

// ---------- Statistiky ----------

@Composable
fun StatsScreen() {
    val nav = LocalNav.current
    val db = useDb()
    val stock = remember(db) { db.stock().filter { it.qty > 0 } }
    val total = stock.sumOf { it.value }
    val since = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
    val recent = db.moves.filter { it.time >= since }
    val consumed = recent.filter { it.type == MoveType.CONSUME }.sumOf { -it.value }
    val wasted = recent.filter { it.type == MoveType.DISCARD }.sumOf { -it.value }
    val wasteShare = if (consumed + wasted > 0) wasted / (consumed + wasted) else 0.0
    val byCat = stock.groupBy { it.item.category }.map { it.key to it.value.sumOf { s -> s.value } }
        .filter { it.second > 0 }.sortedByDescending { it.second }
    val byPlace = db.places.map { p ->
        var v = 0.0
        for (b in db.batches) {
            if (b.placeId == p.id && b.qty > 0) {
                val it0 = db.items.firstOrNull { x -> x.id == b.itemId }
                if (it0 != null) v += Calc.value(it0, b.qty)
            }
        }
        p.name to v
    }.filter { it.second > 0 }.sortedByDescending { it.second }
    val top = stock.sortedByDescending { it.value }.take(5).filter { it.value > 0 }
    val snaps = db.snaps.takeLast(60)

    ScreenScaffold(title = "Štatistiky", onBack = { nav.pop() }) { pad ->
        PageList(pad) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("hodnota zásob", Calc.money(total), Pal.Green, Modifier.weight(1f))
                    StatTile("produktov", stock.size.toString(), modifier = Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("spotrebované (30 dní)", Calc.money(consumed), modifier = Modifier.weight(1f))
                    StatTile("vyhodené (30 dní)", Calc.money(wasted), if (wasted > 0) Pal.Red else Pal.Green, Modifier.weight(1f))
                }
            }
            if (consumed + wasted > 0) {
                item { Muted("Plytvanie tvorí " + Calc.num(wasteShare * 100, 0) + " % hodnoty odobratých potravín.") }
            }
            item {
                SCard {
                    SectionTitle("Vývoj hodnoty zásob")
                    if (snaps.size < 2) {
                        Muted("Graf sa vytvorí po niekoľkých dňoch používania.")
                    } else {
                        Spacer(Modifier.height(8.dp))
                        LineChart(snaps.map { it.value }, Modifier.fillMaxWidth().height(140.dp))
                        Muted(Calc.date(snaps.first().day) + " – " + Calc.date(snaps.last().day))
                    }
                }
            }
            item {
                SCard {
                    SectionTitle("Hodnota podľa kategórie")
                    if (byCat.isEmpty()) Muted("Zatiaľ bez cien. Zadajte cenu pri produkte.")
                    for ((name, v) in byCat) BarRow(name, v, byCat.first().second)
                }
            }
            item {
                SCard {
                    SectionTitle("Hodnota podľa miesta")
                    if (byPlace.isEmpty()) Muted("Zatiaľ bez údajov.")
                    for ((name, v) in byPlace) BarRow(name, v, byPlace.first().second)
                }
            }
            if (top.isNotEmpty()) {
                item {
                    SCard {
                        SectionTitle("Najcennejšie zásoby")
                        for (s in top) InfoRow(s.item.name, Calc.money(s.value))
                    }
                }
            }
        }
    }
}

@Composable
private fun BarRow(label: String, v: Double, max: Double) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(Calc.money(v), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        LinearProgressIndicator(
            progress = { if (max > 0) (v / max).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

@Composable
fun LineChart(values: List<Double>, modifier: Modifier = Modifier) {
    val color = Pal.Green
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val mx = values.max()
        val mn = values.min()
        val range = if (mx - mn < 1e-9) 1.0 else mx - mn
        val stepX = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = stepX * i
            val y = (size.height - ((v - mn) / range * (size.height - 8f)).toFloat() - 4f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
        drawLine(Pal.Outline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
    }
}
