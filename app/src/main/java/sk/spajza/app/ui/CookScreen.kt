package sk.spajza.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sk.spajza.app.data.Calc
import sk.spajza.app.data.MoveType
import sk.spajza.app.data.StockItem
import sk.spajza.app.data.Uom
import sk.spajza.app.data.stock

private data class CookLine(val itemId: Long, val qty: String)

private fun cookDefault(u: Uom): Double = when (u) {
    Uom.PCS, Uom.PACK -> 1.0
    Uom.KG -> 0.5
    Uom.G -> 200.0
    Uom.L -> 0.5
    Uom.ML -> 200.0
}

private fun cookStep(u: Uom): Double = when (u) {
    Uom.PCS, Uom.PACK -> 1.0
    Uom.KG -> 0.1
    Uom.G -> 50.0
    Uom.L -> 0.1
    Uom.ML -> 50.0
}

private fun r3(x: Double): Double = Math.round(x * 1000) / 1000.0

/** Navarili ste vlastny obed: rychlo vyberte, co ste pouzili, a odpocita sa to zo zasob. */
@Composable
fun CookScreen() {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<CookLine>() }

    val stockList = remember(db) { db.stock().filter { it.qty > 0 } }
    val byId = remember(stockList) { stockList.associateBy { it.item.id } }
    val usedIds = lines.map { it.itemId }.toSet()
    val nq = Calc.norm(query.trim())
    val today = Calc.today()

    val sections: List<Pair<String, List<StockItem>>> = if (nq.isNotEmpty()) {
        listOf(
            "Výsledky" to stockList.filter {
                it.item.id !in usedIds && Calc.norm(it.item.name + " " + it.item.brand + " " + it.item.kind).contains(nq)
            }.take(10)
        )
    } else {
        val urgent = stockList.filter { s ->
            s.item.id !in usedIds && (s.batches.any { it.opened != null } || (s.nearest != null && s.nearest - today <= 5))
        }.sortedBy { it.nearest ?: Long.MAX_VALUE }.take(6)
        val urgentIds = urgent.map { it.item.id }.toSet()
        val freq = db.moves.filter { it.type == MoveType.CONSUME && it.itemId != null }
            .groupingBy { it.itemId }.eachCount()
        val often = freq.entries.sortedByDescending { it.value }
            .mapNotNull { e -> e.key?.let { byId[it] } }
            .filter { it.item.id !in usedIds && it.item.id !in urgentIds }
            .take(8)
        listOf("Najprv spotrebujte (otvorené, blížiaca sa expirácia)" to urgent, "Často používané" to often)
    }

    fun add(s: StockItem) {
        lines.add(CookLine(s.item.id, Calc.edit(r3(minOf(cookDefault(s.item.uom), s.qty)))))
        query = ""
    }

    fun submit() {
        val entries = ArrayList<Pair<Long, Double>>()
        for (ln in lines) {
            val s = byId[ln.itemId] ?: continue
            val q = Calc.parse(ln.qty) ?: 0.0
            if (q > 0) entries.add(ln.itemId to minOf(q, s.qty))
        }
        if (entries.isEmpty()) return
        store.withdrawAll(entries, if (name.isBlank()) "Vlastné varenie" else "Varenie: " + name.trim())
        nav.pop()
    }

    ScreenScaffold(
        title = "Navarené",
        onBack = { nav.pop() },
        floating = {
            if (lines.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { submit() },
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    text = { Text("Odpočítať zo zásob (" + lines.size + ")") }
                )
            }
        }
    ) { pad ->
        PageList(pad) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Muted("Vyberte, čo ste použili pri varení. Berie sa najprv z otvorených balení a z najbližšej expirácie. Odpočítanie môžete hneď vrátiť.")
                    TextField2("Čo ste varili? (nepovinné)", name, { name = it }, Modifier.fillMaxWidth())
                }
            }
            if (lines.isNotEmpty()) item { SectionTitle("Použité suroviny") }
            items(lines.size) { idx ->
                val ln = lines.getOrNull(idx)
                val s = if (ln == null) null else byId[ln.itemId]
                if (ln != null && s != null) {
                    val step = cookStep(s.item.uom)
                    val cur = Calc.parse(ln.qty) ?: 0.0
                    SCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ItemThumb(s.item, 40.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(s.item.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                Muted("Na sklade " + Calc.qty(s.item, s.qty))
                            }
                            IconButton(onClick = { lines.removeAt(idx) }) { Icon(Icons.Filled.Close, contentDescription = "Odstrániť") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { lines[idx] = ln.copy(qty = Calc.edit(r3(maxOf(cur - step, 0.0)))) }) {
                                Icon(Icons.Filled.Remove, contentDescription = "Menej")
                            }
                            NumField("", ln.qty, { lines[idx] = ln.copy(qty = it) }, Modifier.weight(1f), suffix = s.item.uom.label)
                            IconButton(onClick = { lines[idx] = ln.copy(qty = Calc.edit(r3(cur + step))) }) {
                                Icon(Icons.Filled.Add, contentDescription = "Viac")
                            }
                            TextButton(onClick = { lines[idx] = ln.copy(qty = Calc.edit(r3(s.qty))) }) { Text("Všetko") }
                        }
                        if (cur > s.qty + 1e-9) {
                            Text("Máte len " + Calc.qty(s.item, s.qty) + ", odpočíta sa maximum.", color = Pal.Amber)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionTitle("Pridať surovinu zo zásob")
                    SearchField(query, { query = it }, Modifier.fillMaxWidth(), "Hľadať (napr. cibuľa)")
                    if (stockList.isEmpty()) Muted("V zásobách zatiaľ nič nie je.")
                }
            }
            for (sec in sections) {
                if (sec.second.isNotEmpty()) {
                    item { Muted(sec.first) }
                    items(sec.second.size) { idx ->
                        val s = sec.second[idx]
                        SCard(onClick = { add(s) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ItemThumb(s.item, 40.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(s.item.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Muted("Na sklade " + Calc.qty(s.item, s.qty))
                                }
                                if (s.batches.any { it.opened != null }) Pill("otvorené", Pal.Amber) else ExpiryBadge(s.nearest)
                            }
                        }
                    }
                }
            }
            if (nq.isNotEmpty() && sections.all { it.second.isEmpty() }) {
                item { Muted("V zásobách sa nič nenašlo.") }
            }
        }
    }
}
