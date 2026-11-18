package sk.spajza.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.FilterChip
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
import sk.spajza.app.data.MoveType
import sk.spajza.app.data.placeName

private val FILTERS = listOf("Všetko", "Po expirácii", "Do 3 dní", "Do 7 dní", "Do 30 dní", "Bez dátumu")

@Composable
fun ExpiryScreen() {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    var filter by remember { mutableStateOf(0) }
    val today = Calc.today()
    val byId = remember(db) { db.items.associateBy { it.id } }

    val all = remember(db) {
        db.batches.filter { it.qty > 0 && byId.containsKey(it.itemId) }
            .sortedBy { it.expiry ?: Long.MAX_VALUE }
    }
    fun days(e: Long?): Long? = if (e == null) null else e - today
    val list = all.filter { b ->
        val d = days(b.expiry)
        when (filter) {
            1 -> d != null && d < 0
            2 -> d != null && d in 0..3
            3 -> d != null && d in 0..7
            4 -> d != null && d in 0..30
            5 -> d == null
            else -> true
        }
    }
    val cExpired = all.count { (days(it.expiry) ?: 1L) < 0 }
    val c3 = all.count { val d = days(it.expiry); d != null && d in 0..3 }
    val c7 = all.count { val d = days(it.expiry); d != null && d in 4..7 }
    val c30 = all.count { val d = days(it.expiry); d != null && d in 8..30 }

    ScreenScaffold(title = "Expirácia", onBack = { nav.pop() }) { pad ->
        PageList(pad) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.padding(0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("po expirácii", cExpired.toString(), Pal.Red, Modifier.weight(1f))
                        StatTile("do 3 dní", c3.toString(), Pal.Orange, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("4 – 7 dní", c7.toString(), Pal.Amber, Modifier.weight(1f))
                        StatTile("8 – 30 dní", c30.toString(), Pal.Blue, Modifier.weight(1f))
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(FILTERS.size) { idx ->
                            FilterChip(selected = filter == idx, onClick = { filter = idx }, label = { Text(FILTERS[idx]) })
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                item { EmptyState("Nič tu nie je", "Pre tento filter nemáte žiadne potraviny.") }
            }
            items(list.size) { idx ->
                val b = list[idx]
                val prod = byId[b.itemId]
                if (prod != null) {
                    SCard(onClick = { nav.push(Dest.ItemDetail(prod.id)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ItemThumb(prod, 48.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(prod.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Muted(
                                    Calc.qty(b.qty, prod.uom) +
                                        (if (db.placeName(b.placeId).isNotBlank()) " · " + db.placeName(b.placeId) else "") +
                                        (if (b.expiry != null) " · " + Calc.date(b.expiry) else "") +
                                        (if (b.opened != null) " · otvorené" else "")
                                )
                            }
                            ExpiryBadge(b.expiry)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { store.withdraw(prod.id, b.qty, MoveType.CONSUME, b.id) }) { Text("Spotrebované") }
                            TextButton(onClick = { store.withdraw(prod.id, b.qty, MoveType.DISCARD, b.id) }) { Text("Vyhodené") }
                        }
                    }
                }
            }
        }
    }
}
