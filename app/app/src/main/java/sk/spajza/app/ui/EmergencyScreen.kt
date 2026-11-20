package sk.spajza.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import sk.spajza.app.data.Calc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.spajza.app.data.Emergency
import sk.spajza.app.data.EmergencyPdf

@Composable
fun EmergencyScreen() {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    val s = db.settings
    val r = remember(db) { Emergency.report(db) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pdfMsg by remember { mutableStateOf("") }
    val pdfLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) { EmergencyPdf.write(ctx, uri, db, Emergency.report(db)) }
                pdfMsg = if (res.isSuccess) "PDF uložené." else "Export zlyhal: " + (res.exceptionOrNull()?.message ?: "chyba")
            }
        }
    }
    var kcalText by remember { mutableStateOf(Calc.edit(s.kcalPerDay)) }
    var waterText by remember { mutableStateOf(Calc.edit(s.waterPerDayL)) }

    val target = r.targetDays.coerceAtLeast(1)
    val statusColor: Color = when {
        r.days >= target -> Pal.Green
        r.days >= target / 2.0 -> Pal.Amber
        else -> Pal.Red
    }

    ScreenScaffold(title = "Núdzové zásoby", onBack = { nav.pop() }) { pad ->
        PageList(pad) {
            item {
                SCard {
                    Text("Vystačíte na", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Calc.num(r.days, 1) + " " + daysText(r.days, "deň", "dni", "dní"),
                        style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = statusColor
                    )
                    Muted("pre " + r.persons + " " + daysText(r.persons.toDouble(), "osobu", "osoby", "osôb") + " (jedlo aj voda, platí menšia hodnota)")
                    Spacer(Modifier.height(12.dp))
                    BarLine("Jedlo", r.foodDays, target, Calc.kcalText(r.kcalTotal))
                    Spacer(Modifier.height(8.dp))
                    BarLine("Voda", r.waterDays, target, Calc.waterText(r.waterMl))
                }
            }
            item {
                SCard {
                    SectionTitle("Do cieľa " + r.targetDays + " dní chýba")
                    InfoRow("Energia", if (r.missingKcal <= 0) "máte dosť" else Calc.kcalText(r.missingKcal))
                    InfoRow("Voda", if (r.missingWaterMl <= 0) "máte dosť" else Calc.waterText(r.missingWaterMl))
                    if (r.missingWaterMl > 0) {
                        Muted("To je približne " + Math.ceil(r.missingWaterMl / 1500.0).toInt() + " fliaš po 1,5 l.")
                    }
                    if (r.missingKcal > 0) {
                        Muted("Pre orientáciu: 1 kg ryže alebo cestovín má okolo 3 500 kcal.")
                    }
                }
            }
            item {
                SCard {
                    SectionTitle("Nastavenie domácnosti")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Počet osôb")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(enabled = s.persons > 1, onClick = { store.updateSettings { it.copy(persons = it.persons - 1) } }) { Text("−") }
                            Text(s.persons.toString(), modifier = Modifier.padding(horizontal = 14.dp), fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { store.updateSettings { it.copy(persons = it.persons + 1) } }) { Text("+") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Cieľ (dní)")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(enabled = s.targetDays > 1, onClick = { store.updateSettings { it.copy(targetDays = it.targetDays - 1) } }) { Text("−") }
                            Text(s.targetDays.toString(), modifier = Modifier.padding(horizontal = 14.dp), fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { store.updateSettings { it.copy(targetDays = it.targetDays + 1) } }) { Text("+") }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumField("Energia / osoba / deň", kcalText, {
                            kcalText = it
                            val v = Calc.parse(it)
                            if (v != null && v > 0) store.updateSettings { st -> st.copy(kcalPerDay = v) }
                        }, Modifier.weight(1f), suffix = "kcal")
                        NumField("Voda / osoba / deň", waterText, {
                            waterText = it
                            val v = Calc.parse(it)
                            if (v != null && v > 0) store.updateSettings { st -> st.copy(waterPerDayL = v) }
                        }, Modifier.weight(1f), suffix = "l")
                    }
                }
            }
            if (r.unknownWater.isNotEmpty() || r.unknownKcal.isNotEmpty()) {
                item {
                    SCard {
                        SectionTitle("Nezapočítané – chýbajú údaje")
                        for (x in r.unknownWater) {
                            TextButton(onClick = { nav.push(Dest.ItemEdit(x.item.id)) }) {
                                Text(x.item.name + " – voda bez objemu")
                            }
                        }
                        for (x in r.unknownKcal) {
                            TextButton(onClick = { nav.push(Dest.ItemEdit(x.item.id)) }) {
                                Text(x.item.name + " – bez kalórií alebo obsahu")
                            }
                        }
                        Muted("Doplňte obsah balenia (g/ml) a kalórie na 100 g, aby sa započítali.")
                    }
                }
            }
            item { SectionTitle("Čo sa započítava (" + r.counted.size + ")") }
            val top = r.counted.sortedByDescending { it.kcal + it.waterMl / 10.0 }
            items(top.size) { idx ->
                val x = top[idx]
                SCard(onClick = { nav.push(Dest.ItemDetail(x.item.id)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemThumb(x.item, 40.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(x.item.name, maxLines = 1, fontWeight = FontWeight.Medium)
                            Muted(Calc.qty(x.qty, x.item.uom))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (x.kcal > 0) Text(Calc.kcalText(x.kcal), style = MaterialTheme.typography.bodySmall)
                            if (x.waterMl > 0) Text(Calc.waterText(x.waterMl), style = MaterialTheme.typography.bodySmall, color = Pal.Blue)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { pdfLauncher.launch("nudzove-zasoby-" + java.time.LocalDate.now().toString() + ".pdf") },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Exportovať do PDF (na vytlačenie)") }
                    if (pdfMsg.isNotBlank()) Muted(pdfMsg)
                }
            }
        }
    }
}

@Composable
private fun BarLine(label: String, days: Double, target: Int, detail: String) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label + ": " + Calc.num(days, 1) + " dní")
            Muted(detail)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (days / target).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = if (days >= target) Pal.Green else if (days >= target / 2.0) Pal.Amber else Pal.Red
        )
    }
}
