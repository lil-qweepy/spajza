package sk.spajza.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Item
import sk.spajza.app.data.Kinds
import sk.spajza.app.data.Ocr
import sk.spajza.app.data.PriceBasis
import sk.spajza.app.data.Receipt
import sk.spajza.app.data.Uom
import java.io.File

private data class RLine(val name: String, val qty: Double, val price: Double, val checked: Boolean)

@Composable
fun ReceiptScreen() {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    val lines = remember { mutableStateListOf<RLine>() }
    var place by remember { mutableStateOf(0) }
    var expiry by remember { mutableStateOf<Long?>(null) }

    fun run(uri: Uri) {
        busy = true
        msg = ""
        scope.launch {
            val r = Ocr.rows(ctx, uri)
            busy = false
            val rows = r.getOrNull()
            if (rows == null) {
                msg = "Fotku sa nepodarilo prečítať."
            } else {
                val parsed = Receipt.parse(rows)
                lines.clear()
                for (p in parsed) lines.add(RLine(p.name, p.qty, p.unitPrice, true))
                msg = if (parsed.isEmpty()) "Nenašli sa žiadne položky. Skúste ostrejšiu fotku celého bločku zblízka." else
                    "Rozpoznané položky skontrolujte. Názvy, počty a ceny môžete opraviť."
            }
        }
    }

    val file = remember { File(ctx.cacheDir, "ocr").also { it.mkdirs() }.let { File(it, "receipt.jpg") } }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) run(Uri.fromFile(file))
    }
    fun launchCamera() {
        try {
            take.launch(FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file))
        } catch (e: Exception) {
            msg = "Fotoaparát sa nepodarilo otvoriť."
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else msg = "Bez povolenia kamery použite fotku z galérie."
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) run(uri)
    }

    fun findItem(name: String): Item? {
        val n = Calc.norm(name)
        if (n.length < 3) return null
        return db.items.firstOrNull { Calc.norm(it.name) == n }
            ?: db.items.firstOrNull {
                val x = Calc.norm(it.name)
                x.length >= 4 && (x.contains(n) || n.contains(x))
            }
    }

    ScreenScaffold(title = "Nákup z bločku", onBack = { nav.pop() }) { pad ->
        PageList(pad) {
            item {
                SCard {
                    Muted("Odfoťte celý bloček na rovnom podklade. Položky sa rozpoznajú v telefóne, bez internetu. Presnosť závisí od kvality fotky a tlače bločku, preto vždy skontrolujte zoznam.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Button(enabled = !busy, onClick = {
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                            else permission.launch(Manifest.permission.CAMERA)
                        }) { Text(if (busy) "Čítam…" else "Odfotiť bloček") }
                        OutlinedButton(enabled = !busy, onClick = { pick.launch("image/*") }) { Text("Z galérie") }
                    }
                    if (msg.isNotBlank()) Muted(msg, Modifier.padding(top = 8.dp))
                }
            }
            if (lines.isNotEmpty()) {
                item {
                    SCard {
                        PlacePicker("Miesto uloženia (inak podľa produktu)", db, place, { place = it }, Modifier.fillMaxWidth())
                        DateField("Spoločná expirácia (voliteľné)", expiry, { expiry = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
                    }
                }
            }
            items(lines.size) { idx ->
                val l = lines[idx]
                val known = findItem(l.name)
                SCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = l.checked, onCheckedChange = { c -> if (idx < lines.size) lines[idx] = lines[idx].copy(checked = c) })
                        TextField2("Názov", l.name, { t -> if (idx < lines.size) lines[idx] = lines[idx].copy(name = t) }, Modifier.weight(1f))
                    }
                    var q by remember(idx, l.name) { mutableStateOf(Calc.edit(l.qty)) }
                    var p by remember(idx, l.name) { mutableStateOf(Calc.edit(l.price)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        NumField("Počet", q, { t ->
                            q = t
                            val v = Calc.parse(t)
                            if (v != null && v > 0 && idx < lines.size) lines[idx] = lines[idx].copy(qty = v)
                        }, Modifier.weight(1f))
                        NumField("Cena za kus", p, { t ->
                            p = t
                            val v = Calc.parse(t)
                            if (v != null && idx < lines.size) lines[idx] = lines[idx].copy(price = v)
                        }, Modifier.weight(1f), suffix = "€")
                    }
                    Muted(if (known != null) "Už máte: " + known.name + " – pridá sa k nemu" else "Nový produkt")
                }
            }
            if (lines.isNotEmpty()) {
                item {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = {
                        val today = Calc.today()
                        var n = 0
                        for (l in lines.toList()) {
                            if (!l.checked || l.name.isBlank()) continue
                            val ex = findItem(l.name)
                            val id: Long
                            val pl: Long?
                            if (ex != null) {
                                id = ex.id
                                pl = if (place > 0) placeAt(db, place) else ex.placeId
                                if (ex.price <= 0 && l.price > 0 && ex.uom.isCountable) {
                                    store.saveItem(ex.copy(price = l.price, basis = PriceBasis.PER_PIECE))
                                }
                            } else {
                                pl = if (place > 0) placeAt(db, place) else null
                                id = store.saveItem(
                                    Item(
                                        name = l.name.trim(), uom = Uom.PCS, price = l.price, basis = PriceBasis.PER_PIECE,
                                        kind = Kinds.guess(l.name), placeId = pl
                                    )
                                )
                            }
                            store.addStock(id, l.qty, expiry, today, pl, "Z bločku")
                            n++
                        }
                        lines.clear()
                        msg = "Do zásob pridaných položiek: $n"
                    }) { Text("Pridať vybrané do zásob") }
                }
            }
        }
    }
}
