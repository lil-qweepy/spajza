package sk.spajza.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.OutlinedButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Db
import sk.spajza.app.data.Ean
import sk.spajza.app.data.Item
import sk.spajza.app.data.OffProduct
import sk.spajza.app.data.OffResult
import sk.spajza.app.data.OpenFoodFacts
import sk.spajza.app.data.Store
import sk.spajza.app.data.buildStock
import java.util.concurrent.Executors

/** Odstrani polozku z hromadneho nakupu; produkt, ktory vznikol len z fotky a nema zasoby, sa zmaze. */
private fun dropPending(store: Store, pending: MutableList<Pending>, idx: Int) {
    if (idx !in pending.indices) return
    val p = pending.removeAt(idx)
    val d = store.db.value
    val it0 = d.items.firstOrNull { x -> x.id == p.itemId }
    if (it0 != null && it0.draft && d.batches.none { b -> b.itemId == it0.id }) store.deleteItem(it0.id)
}

private class Votes {
    var code: String = ""
    var n: Int = 0
}

private sealed class Phase {
    data object Idle : Phase()
    data class Looking(val ean: String) : Phase()
    data class Local(val item: Item) : Phase()
    data class Remote(val product: OffProduct) : Phase()
    data class Missing(val ean: String, val error: String?) : Phase()
    data class Take(val item: Item) : Phase()
    data class Photo(val ean: String) : Phase()
    data class NotStocked(val ean: String) : Phase()
}

private fun findLocal(store: Store, ean: String): Item? {
    var r = store.findByEan(ean)
    if (r == null && ean.length == 12) r = store.findByEan("0$ean")
    if (r == null && ean.length == 13 && ean.startsWith("0")) r = store.findByEan(ean.substring(1))
    return r
}

@Composable
fun ScanScreen() {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    val votes = remember { Votes() }
    var phase by remember { mutableStateOf<Phase>(Phase.Idle) }
    var lastCode by remember { mutableStateOf("") }
    var lastTime by remember { mutableStateOf(0L) }
    var manual by remember { mutableStateOf(false) }
    var removeMode by remember { mutableStateOf(false) }
    var bulkMode by remember { mutableStateOf(nav.bulkReview || nav.bulkPending.isNotEmpty()) }
    val pending = nav.bulkPending
    var cameraError by remember { mutableStateOf("") }
    val session = remember { mutableStateListOf<String>() }

    fun bulkAdd(id: Long, name: String) {
        val idx = pending.indexOfFirst { it.itemId == id }
        if (idx >= 0) pending[idx] = pending[idx].copy(qty = pending[idx].qty + 1)
        else pending.add(Pending(id, name, 1.0, null))
    }

    var photoEan by remember { mutableStateOf<String?>(null) }
    var photoItemId by remember { mutableStateOf(0L) }
    val takePhoto = rememberProductCamera { img ->
        val e = photoEan
        val id = photoItemId
        photoEan = null
        photoItemId = 0L
        if (id != 0L) {
            // Hromadny rezim: produkt uz existuje (aby sa nepytal znova), fotka sa mu len pripoji.
            if (img != null) {
                store.db.value.items.firstOrNull { x -> x.id == id }?.let { cur -> store.saveItem(cur.copy(image = img)) }
            }
            phase = Phase.Idle
            lastTime = System.currentTimeMillis()
        } else if (e != null) {
            phase = Phase.Local(store.saveDraft(e, img))
        } else {
            phase = Phase.Idle
            lastTime = System.currentTimeMillis()
        }
    }

    fun startPhoto(ean: String) {
        if (bulkMode) {
            // Produkt sa ulozi hned, takze dalsi naskenovany kus rovnakeho kodu uz fotku nepyta.
            val draft = store.saveDraft(ean, null)
            bulkAdd(draft.id, draft.name)
            photoItemId = draft.id
        }
        photoEan = ean
        phase = Phase.Photo(ean)
        takePhoto()
    }

    fun handle(ean: String) {
        if (phase != Phase.Idle) return
        val now = System.currentTimeMillis()
        if (ean == lastCode && now - lastTime < 3000) return
        lastCode = ean
        lastTime = now
        val local = findLocal(store, ean)
        if (bulkMode) {
            if (local != null) {
                bulkAdd(local.id, local.name)
                return
            }
            phase = Phase.Looking(ean)
            scope.launch {
                val r = OpenFoodFacts.lookup(ean)
                when (r) {
                    is OffResult.Found -> {
                        val created = store.saveFromOff(r.product)
                        bulkAdd(created.id, created.name)
                        phase = Phase.Idle
                        lastTime = System.currentTimeMillis()
                    }
                    is OffResult.NotFound, is OffResult.Error -> startPhoto(ean)
                }
            }
            return
        }
        if (removeMode) {
            phase = if (local != null) Phase.Take(local) else Phase.NotStocked(ean)
            return
        }
        if (local != null) {
            phase = Phase.Local(local)
            return
        }
        phase = Phase.Looking(ean)
        scope.launch {
            val r = OpenFoodFacts.lookup(ean)
            phase = when (r) {
                is OffResult.Found -> Phase.Remote(r.product)
                is OffResult.NotFound -> Phase.Missing(ean, null)
                is OffResult.Error -> Phase.Missing(ean, r.message)
            }
        }
    }

    fun finish() {
        phase = Phase.Idle
        lastTime = System.currentTimeMillis()
    }

    ScreenScaffold(
        title = "Skenovať produkty",
        onBack = { nav.pop() },
        actions = {
            IconButton(onClick = { manual = true }) { Icon(Icons.Filled.Keyboard, contentDescription = "Zadať kód ručne") }
        }
    ) { pad ->
        if (nav.bulkReview) {
            BackHandler { nav.bulkReview = false }
            BulkReview(pad, db, pending, onBack = { nav.bulkReview = false }) { n ->
                session.add("Hromadne pridaných položiek: $n")
                nav.bulkReview = false
            }
        } else
        Column(Modifier.fillMaxSize().padding(pad)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (granted) {
                    CameraPreview(onCode = { code ->
                        // Kod sa prijme az ked ma spravnu kontrolnu cislicu a precita sa dvakrat po sebe.
                        if (Ean.valid(code)) {
                            if (code == votes.code) votes.n++ else { votes.code = code; votes.n = 1 }
                            if (votes.n >= 2) {
                                votes.n = 0
                                handle(code)
                            }
                        }
                    }, onError = { cameraError = it })
                    Box(
                        Modifier.width(280.dp).height(150.dp)
                            .border(BorderStroke(3.dp, Pal.Green), RoundedCornerShape(16.dp))
                    )
                    if (phase is Phase.Looking) {
                        CircularProgressIndicator(Modifier.size(48.dp))
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("Na skenovanie potrebuje aplikácia prístup ku kamere.")
                        Button(onClick = { permission.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Povoliť kameru")
                        }
                        Muted("Kód môžete zadať aj ručne cez ikonu klávesnice.", Modifier.padding(top = 8.dp))
                    }
                }
            }
            if (cameraError.isNotBlank()) Text(cameraError, color = Pal.Red, modifier = Modifier.padding(12.dp))
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !removeMode && !bulkMode, onClick = { removeMode = false; bulkMode = false }, label = { Text("Pridať") })
                    FilterChip(selected = bulkMode, onClick = { removeMode = false; bulkMode = true }, label = { Text("Hromadne po nákupe") })
                    FilterChip(selected = removeMode, onClick = { removeMode = true; bulkMode = false }, label = { Text("Odobrať") })
                }
                if (bulkMode) {
                    Muted("Skenujte produkty jeden za druhým, rovnaký kód viackrát zvýši počet. Ak sa produkt nenájde, odfotí sa a údaje doplníte neskôr.")
                    Button(enabled = pending.isNotEmpty(), onClick = { nav.bulkReview = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Dokončiť (" + pending.size + ")")
                    }
                }
                if (bulkMode && pending.isNotEmpty()) {
                    for (i in pending.indices.reversed().take(4)) {
                        val p = pending[i]
                        val live = db.items.firstOrNull { x -> x.id == p.itemId }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (live?.name ?: p.name) + " × " + Calc.num(p.qty),
                                style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f)
                            )
                            if (p.qty > 1.0) {
                                IconButton(onClick = { pending[i] = p.copy(qty = p.qty - 1) }) {
                                    Icon(androidx.compose.material.icons.Icons.Filled.Remove, contentDescription = "Ubrať jeden kus")
                                }
                            }
                            IconButton(onClick = { dropPending(store, pending, i) }) {
                                Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Odstrániť z nákupu")
                            }
                        }
                    }
                    if (pending.size > 4) Muted("… a ďalších " + (pending.size - 4) + ". Všetky uvidíte po stlačení Dokončiť.")
                } else if (session.isEmpty()) {
                    Muted(
                        if (removeMode) "Naskenujte produkt, ktorý ste spotrebovali alebo vyhodili."
                        else "Namierte kameru na čiarový kód. Môžete skenovať jeden produkt za druhým."
                    )
                } else {
                    Text("V tejto relácii (" + session.size + ")", style = MaterialTheme.typography.titleSmall)
                    for (s in session.takeLast(4).reversed()) Muted("• $s")
                }
            }
        }
    }

    val p = phase
    when (p) {
        is Phase.Local -> {
            AddStockDialog(db, p.item, onDismiss = { finish() }) { q, ex, bought, place ->
                store.addStock(p.item.id, q, ex, bought, place)
                session.add(p.item.name + " × " + Calc.qty(q, p.item.uom))
            }
        }
        is Phase.Remote -> {
            NewFromOffDialog(db, p.product, onDismiss = { finish() }) { q, ex, place ->
                scope.launch {
                    val created = store.saveFromOff(p.product)
                    store.addStock(created.id, q, ex, Calc.today(), place)
                    session.add(created.name + " × " + Calc.qty(q, created.uom))
                }
            }
        }
        is Phase.Take -> {
            val total = buildStock(p.item, db.batches.filter { it.itemId == p.item.id && it.qty > 0 }).qty
            UseDialog(p.item, total, onDismiss = { finish() }) { q, type ->
                val taken = store.withdraw(p.item.id, q, type)
                session.add("− " + p.item.name + " × " + Calc.qty(taken, p.item.uom))
            }
        }
        is Phase.NotStocked -> {
            AlertDialog(
                onDismissRequest = { finish() },
                title = { Text("Nie je v zásobách") },
                text = { Text("Produkt s kódom " + p.ean + " nemáte v zásobách, nie je čo odobrať.") },
                confirmButton = { TextButton(onClick = { finish() }) { Text("OK") } }
            )
        }
        is Phase.Missing -> {
            AlertDialog(
                onDismissRequest = { finish() },
                title = { Text(if (p.error == null) "Produkt sa nenašiel" else "Chyba pripojenia") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (p.error == null) "Kód " + p.ean + " nie je v databáze Open Food Facts. Môžete ho pridať ručne a zostane uložený vo vašej databáze."
                            else "Nepodarilo sa spojiť s Open Food Facts (" + p.error + "). Produkt môžete pridať ručne alebo skúsiť znova."
                        )
                        if (!removeMode) {
                            OutlinedButton(onClick = { startPhoto(p.ean) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Odfotiť a doplniť neskôr")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val e = p.ean
                        finish()
                        nav.push(Dest.QuickAdd(ean = e))
                    }) { Text("Vytvoriť ručne") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        val e = p.ean
                        finish()
                        if (p.error != null) {
                            lastTime = 0L
                            handle(e)
                        }
                    }) { Text(if (p.error != null) "Skúsiť znova" else "Zrušiť") }
                }
            )
        }
        else -> {}
    }

    if (manual) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text("Zadať čiarový kód") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextField2("EAN", text, { text = it.filter { c -> c.isDigit() } }, Modifier.fillMaxWidth(), keyboard = KeyboardType.Number)
                    if (text.length >= 8 && !Ean.valid(text)) {
                        Text("Kód nemá správnu kontrolnú číslicu. Skontrolujte, či ste číslice prepísali dobre.", color = Pal.Red)
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = Ean.valid(text), onClick = {
                    val e = text
                    manual = false
                    lastTime = 0L
                    handle(e)
                }) { Text("Hľadať") }
            },
            dismissButton = { TextButton(onClick = { manual = false }) { Text("Zrušiť") } }
        )
    }
}

@Composable
private fun NewFromOffDialog(db: Db, p: OffProduct, onDismiss: () -> Unit, onOk: (Double, Long?, Long?) -> Unit) {
    var qty by remember { mutableStateOf("1") }
    var expiry by remember { mutableStateOf<Long?>(null) }
    var place by remember { mutableStateOf(0) }
    val q = Calc.parse(qty)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(p.name.ifBlank { "Nový produkt" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val sub = listOf(p.brand, if (p.netAmount > 0) Calc.num(p.netAmount) + (if (p.netIsLiquid) " ml" else " g") else "")
                    .filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotBlank()) Muted(sub)
                if (p.kcal100 != null) Muted(Calc.num(p.kcal100) + " kcal / 100 " + (if (p.netIsLiquid) "ml" else "g"))
                Muted("Produkt sa uloží do vašej databázy, takže nabudúce funguje aj bez internetu.")
                NumField("Počet kusov", qty, { qty = it }, Modifier.fillMaxWidth(), suffix = "ks")
                DateField("Dátum expirácie", expiry, { expiry = it }, Modifier.fillMaxWidth())
                PlacePicker("Miesto uloženia", db, place, { place = it }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = q != null && q > 0, onClick = { onOk(q ?: 0.0, expiry, placeAt(db, place)); onDismiss() }) { Text("Pridať") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

@Composable
private fun CameraPreview(onCode: (String) -> Unit, onError: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A)
                .build()
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            try {
                scanner.close()
            } catch (e: Exception) {
                // nic
            }
            executor.shutdown()
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp)),
        factory = { c ->
            val view = PreviewView(c)
            val future = ProcessCameraProvider.getInstance(c)
            future.addListener({
                try {
                    val provider = future.get()
                    val preview = Preview.Builder().build()
                    preview.setSurfaceProvider(view.surfaceProvider)
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis.setAnalyzer(executor) { proxy -> analyze(scanner, proxy, onCode) }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (e: Exception) {
                    onError("Kameru sa nepodarilo spustiť: " + (e.message ?: "neznáma chyba"))
                }
            }, ContextCompat.getMainExecutor(c))
            view
        }
    )
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyze(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    proxy: ImageProxy,
    onCode: (String) -> Unit
) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    try {
        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { codes ->
                val v = codes.firstNotNullOfOrNull { it.rawValue }
                if (v != null) onCode(v)
            }
            .addOnCompleteListener { proxy.close() }
    } catch (e: Exception) {
        proxy.close()
    }
}

@Composable
private fun BulkReview(
    pad: androidx.compose.foundation.layout.PaddingValues,
    db: Db,
    pending: androidx.compose.runtime.snapshots.SnapshotStateList<Pending>,
    onBack: () -> Unit,
    onDone: (Int) -> Unit
) {
    val nav = LocalNav.current
    val store = LocalStore.current
    var place by remember { mutableStateOf(0) }
    var expiry by remember { mutableStateOf<Long?>(null) }
    // Nazvy dopisane priamo v kontrole (pre produkty, ktore vznikli len z fotky).
    val names = remember { androidx.compose.runtime.mutableStateMapOf<Long, String>() }

    fun commitName(id: Long) {
        val typed = names[id]?.trim().orEmpty()
        val cur = db.items.firstOrNull { x -> x.id == id } ?: return
        if (cur.draft && typed.isNotEmpty()) store.saveItem(cur.copy(name = typed, draft = false))
    }

    PageList(pad) {
        item { SectionTitle("Kontrola nákupu (" + pending.size + ")") }
        item {
            SCard {
                Muted("Spoločné nastavenie pre všetky položky. Expirácia pri konkrétnej položke má prednosť.")
                PlacePicker("Miesto uloženia (inak podľa produktu)", db, place, { place = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
                DateField("Spoločná expirácia", expiry, { expiry = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
            }
        }
        items(pending.size) { idx ->
            val p = pending[idx]
            val it0 = db.items.firstOrNull { x -> x.id == p.itemId }
            SCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (it0 != null) ItemThumb(it0, 56.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        if (it0 != null && it0.draft) {
                            TextField2("Názov produktu", names[p.itemId] ?: "", { v -> names[p.itemId] = v }, Modifier.fillMaxWidth())
                        } else {
                            Text(it0?.name ?: p.name, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                        }
                        if (it0 != null && it0.ean.isNotBlank()) Muted("EAN " + it0.ean)
                    }
                    IconButton(onClick = { dropPending(store, pending, idx) }) {
                        Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Odstrániť")
                    }
                }
                var q by remember(p.itemId) { mutableStateOf(Calc.edit(p.qty)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top, modifier = Modifier.padding(top = 8.dp)) {
                    NumField("Počet", q, {
                        q = it
                        val v = Calc.parse(it)
                        if (v != null && v > 0 && idx < pending.size) pending[idx] = pending[idx].copy(qty = v)
                    }, Modifier.weight(0.6f), suffix = it0?.uom?.label)
                    DateField("Expirácia", p.expiry, { e -> if (idx < pending.size) pending[idx] = pending[idx].copy(expiry = e) }, Modifier.weight(1f))
                }
                if (it0 != null) {
                    TextButton(onClick = {
                        commitName(p.itemId)
                        nav.push(Dest.ItemEdit(p.itemId))
                    }) { Text(if (it0.draft) "Doplniť všetky údaje" else "Upraviť údaje") }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = pending.isNotEmpty(), modifier = Modifier.fillMaxWidth(), onClick = {
                    val today = Calc.today()
                    var n = 0
                    for (p in pending.toList()) commitName(p.itemId)
                    for (p in pending.toList()) {
                        val it0 = db.items.firstOrNull { x -> x.id == p.itemId } ?: continue
                        val pl = if (place > 0) placeAt(db, place) else it0.placeId
                        store.addStock(p.itemId, p.qty, p.expiry ?: expiry, today, pl, "Hromadný nákup")
                        n++
                    }
                    pending.clear()
                    onDone(n)
                }) { Text("Pridať všetko do zásob") }
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Späť ku skenovaniu") }
            }
        }
    }
}
