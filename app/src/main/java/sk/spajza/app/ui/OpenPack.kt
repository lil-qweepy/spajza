package sk.spajza.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import sk.spajza.app.data.Batch
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Item
import sk.spajza.app.data.Kinds

private val LEFTS: List<Pair<Double, String>> = listOf(1.0 to "Celé", 0.75 to "¾", 0.5 to "½", 0.25 to "¼")
private val DAY_CHIPS: List<Int> = listOf(1, 2, 3, 5, 7, 14, 30)

/** Otvorenie balenia: skrati trvanlivost (napr. otvorene mlieko vydrzi 3 dni v chladnicke). */
@Composable
fun OpenPackDialog(item: Item, batch: Batch, onDismiss: () -> Unit) {
    val store = LocalStore.current
    val suggested = Kinds.openDays(item)
    var days by remember { mutableStateOf(suggested.toString()) }
    var amount by remember { mutableStateOf(if (item.uom.isCountable) "1" else Calc.edit(batch.qty)) }
    var left by remember { mutableStateOf(1.0) }
    var keep by remember { mutableStateOf(true) }

    val d = days.toIntOrNull()
    val a = Calc.parse(amount)
    val oldExpiry = batch.expiry
    val limit: Long? = if (d == null || d <= 0) null else Calc.today() + d
    val newExpiry: Long? = if (limit == null) null else if (oldExpiry == null) limit else minOf(oldExpiry, limit)
    val valid = d != null && d > 0 && a != null && a > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Otvoriť balenie") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Muted(item.name + " · na sklade " + Calc.qty(item, batch.qty))
                NumField("Koľko otvárate", amount, { amount = it }, Modifier.fillMaxWidth(), suffix = item.uom.label)
                Text("Koľko z otvoreného zostalo", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LEFTS.size) { idx ->
                        FilterChip(
                            selected = left == LEFTS[idx].first,
                            onClick = { left = LEFTS[idx].first },
                            label = { Text(LEFTS[idx].second) }
                        )
                    }
                }
                NumField(
                    "Spotrebovať do (dní od dnes)", days, { days = it.filter { c -> c.isDigit() } },
                    Modifier.fillMaxWidth(), suffix = "dní", supporting = "Odhad pre tento druh: " + suggested + " dní"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(DAY_CHIPS.size) { idx ->
                        FilterChip(
                            selected = d == DAY_CHIPS[idx],
                            onClick = { days = DAY_CHIPS[idx].toString() },
                            label = { Text(DAY_CHIPS[idx].toString() + " d") }
                        )
                    }
                }
                if (newExpiry != null) Text("Nová expirácia: " + Calc.date(newExpiry), fontWeight = FontWeight.Medium)
                if (oldExpiry != null && newExpiry != null && oldExpiry <= newExpiry) {
                    Muted("Pôvodná expirácia je skôr, preto zostáva.")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = keep, onCheckedChange = { keep = it })
                    Text("Zapamätať pre tento produkt")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val dd = d ?: 1
                // najprv uloz produkt, aby sa nezrusila moznost vratit otvorenie
                if (keep && dd != suggested && dd != item.openDays) store.saveItem(item.copy(openDays = dd))
                store.openPack(batch.id, a ?: 1.0, dd, left)
                onDismiss()
            }) { Text("Otvoriť") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}
