package sk.spajza.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sk.spajza.app.data.Backup
import sk.spajza.app.notify.Notify

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val store = LocalStore.current
    val ctx = LocalContext.current
    val db = useDb()
    val s = db.settings
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Nastavenia", onBack = { nav.pop() }) { pad ->
        PageList(pad) {
            item {
                SCard {
                    SectionTitle("Upozornenia")
                    SwitchRow("Zapnúť upozornenia", s.notificationsEnabled) { v -> store.updateSettings { it.copy(notificationsEnabled = v) } }
                    SwitchRow("30 dní pred expiráciou", s.notify30, s.notificationsEnabled) { v -> store.updateSettings { it.copy(notify30 = v) } }
                    SwitchRow("7 dní pred expiráciou", s.notify7, s.notificationsEnabled) { v -> store.updateSettings { it.copy(notify7 = v) } }
                    SwitchRow("3 dni pred expiráciou", s.notify3, s.notificationsEnabled) { v -> store.updateSettings { it.copy(notify3 = v) } }
                    SwitchRow("Denný prehľad: čo spotrebovať dnes a zajtra", s.notifyDaily, s.notificationsEnabled) { v -> store.updateSettings { it.copy(notifyDaily = v) } }
                    SwitchRow("Keď zásoba klesne pod minimum", s.notifyLow, s.notificationsEnabled) { v -> store.updateSettings { it.copy(notifyLow = v) } }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Čas upozornenia")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { store.updateSettings { it.copy(notifyHour = (it.notifyHour + 23) % 24) } }) { Text("−") }
                            Text(s.notifyHour.toString() + ":00", Modifier.padding(horizontal = 12.dp), fontWeight = FontWeight.Bold)
                            OutlinedButton(onClick = { store.updateSettings { it.copy(notifyHour = (it.notifyHour + 1) % 24) } }) { Text("+") }
                        }
                    }
                    OutlinedButton(onClick = {
                        Notify.test(ctx)
                        msg = if (Notify.canPost(ctx)) "Testovacie upozornenie odoslané." else "Upozornenia nie sú povolené v nastaveniach telefónu."
                    }, modifier = Modifier.padding(top = 8.dp)) { Text("Odoslať testovacie upozornenie") }
                    if (msg.isNotBlank()) Muted(msg)
                }
            }
            item {
                SCard {
                    SectionTitle("Údaje z internetu")
                    SwitchRow("Automaticky dopĺňať kalórie z Open Food Facts", s.autoFetch) { v -> store.updateSettings { it.copy(autoFetch = v) } }
                    Muted("Raz denne (len ak je dostupný internet) sa doplnia chýbajúce kalórie a výživové hodnoty produktov, ktoré majú čiarový kód.")
                    OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            val n = store.autoFillAll()
                            busy = false
                            msg = "Doplnené kalórie pre produktov: $n"
                        }
                    }, modifier = Modifier.padding(top = 8.dp)) { Text(if (busy) "Sťahujem…" else "Doplniť teraz") }
                }
            }
            item {
                SCard {
                    SectionTitle("Domácnosť a núdzové zásoby")
                    Muted("Počet osôb: " + s.persons + " · " + sk.spajza.app.data.Calc.num(s.kcalPerDay) + " kcal a " +
                        sk.spajza.app.data.Calc.num(s.waterPerDayL, 1) + " l vody na osobu a deň · cieľ " + s.targetDays + " dní")
                    OutlinedButton(onClick = { nav.push(Dest.Emergency) }, modifier = Modifier.padding(top = 8.dp)) { Text("Upraviť") }
                }
            }
            item {
                SCard {
                    SectionTitle("O aplikácii")
                    Muted("Špajza 1.0. Všetky údaje sú uložené len vo vašom telefóne. Aplikácia nepotrebuje registráciu ani cloud. Internet sa používa iba na dohľadanie produktov v Open Food Facts.")
                }
            }
        }
    }
}

@Composable
fun BackupScreen() {
    val nav = LocalNav.current
    val store = LocalStore.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var confirmUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            scope.launch {
                val r = withContext(Dispatchers.IO) { Backup.export(ctx, store, uri) }
                msg = if (r.isSuccess) "Záloha uložená (obrázkov: " + (r.getOrNull() ?: 0) + ")." else "Export zlyhal: " + (r.exceptionOrNull()?.message ?: "chyba")
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmUri = uri
    }

    ScreenScaffold(title = "Záloha a obnova", onBack = { nav.pop() }) { pad ->
        PageList(pad) {
            item {
                SCard {
                    SectionTitle("Exportovať")
                    Muted("Vytvorí jeden súbor so všetkými údajmi a fotkami. Uložte ho do telefónu, na kartu alebo si ho pošlite.")
                    Button(onClick = {
                        exporter.launch("spajza-zaloha-" + java.time.LocalDate.now().toString() + ".zip")
                    }, modifier = Modifier.padding(top = 8.dp)) { Text("Exportovať zálohu") }
                }
            }
            item {
                SCard {
                    SectionTitle("Importovať")
                    Muted("Obnoví údaje zo zálohy. Aktuálne údaje v aplikácii budú nahradené.")
                    OutlinedButton(onClick = {
                        importer.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    }, modifier = Modifier.padding(top = 8.dp)) { Text("Importovať zálohu") }
                }
            }
            if (msg.isNotBlank()) item { Text(msg) }
        }
    }

    val u = confirmUri
    if (u != null) {
        ConfirmDialog(
            "Nahradiť všetky údaje?", "Všetky súčasné údaje v aplikácii sa nahradia obsahom zálohy.", "Importovať",
            onConfirm = {
                scope.launch {
                    val r = withContext(Dispatchers.IO) { Backup.import(ctx, store, u) }
                    msg = if (r.isSuccess) "Záloha obnovená (produktov: " + (r.getOrNull() ?: 0) + ")." else "Import zlyhal: " + (r.exceptionOrNull()?.message ?: "chyba")
                }
            },
            onDismiss = { confirmUri = null }
        )
    }
}
