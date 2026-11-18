package sk.spajza.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Estimate
import sk.spajza.app.data.Item
import sk.spajza.app.data.Kinds
import sk.spajza.app.data.Nutri
import sk.spajza.app.data.OffProduct
import sk.spajza.app.data.OcrNutrition
import sk.spajza.app.data.Ocr
import sk.spajza.app.data.OffResult
import sk.spajza.app.data.OpenFoodFacts
import sk.spajza.app.data.PriceBasis
import sk.spajza.app.data.Uom

@Composable
fun ItemEditScreen(d: Dest.ItemEdit) {
    val nav = LocalNav.current
    val store = LocalStore.current
    val db = useDb()
    val scope = rememberCoroutineScope()
    val existing = if (d.id == 0L) null else db.items.firstOrNull { it.id == d.id }
    val isNew = existing == null

    var name by remember { mutableStateOf(existing?.name ?: d.name) }
    var brand by remember { mutableStateOf(existing?.brand ?: "") }
    var manu by remember { mutableStateOf(existing?.manufacturer ?: "") }
    var ean by remember { mutableStateOf(existing?.ean ?: d.ean) }
    var kind by remember { mutableStateOf(existing?.kind ?: d.kind) }
    var catIdx by remember {
        mutableStateOf(
            (db.categories.indexOf(existing?.category ?: "Iné")).let { if (it < 0) 0 else it }
        )
    }
    var uomIdx by remember { mutableStateOf((existing?.uom ?: Uom.PCS).ordinal) }
    var net by remember { mutableStateOf(if (existing != null && existing.net > 0) Calc.edit(existing.net) else "") }
    var liquid by remember {
        mutableStateOf(
            existing != null && (existing.uom.isVolume || (existing.uom.isCountable && existing.netLiquid))
        )
    }
    var liquidTouched by remember { mutableStateOf(false) }
    var price by remember { mutableStateOf(if (existing != null && existing.price > 0) Calc.edit(existing.price) else "") }
    var basisIdx by remember { mutableStateOf((existing?.basis ?: PriceBasis.PER_PIECE).ordinal) }
    var minQty by remember { mutableStateOf(if (existing != null && existing.minQty > 0) Calc.edit(existing.minQty) else "") }
    var placeIdx by remember { mutableStateOf(placeIndex(db, existing?.placeId)) }
    var kcal by remember { mutableStateOf(Calc.edit(existing?.kcal)) }
    var protein by remember { mutableStateOf(Calc.edit(existing?.protein)) }
    var fat by remember { mutableStateOf(Calc.edit(existing?.fat)) }
    var satFat by remember { mutableStateOf(Calc.edit(existing?.satFat)) }
    var carbs by remember { mutableStateOf(Calc.edit(existing?.carbs)) }
    var sugar by remember { mutableStateOf(Calc.edit(existing?.sugar)) }
    var fiber by remember { mutableStateOf(Calc.edit(existing?.fiber)) }
    var salt by remember { mutableStateOf(Calc.edit(existing?.salt)) }
    var water by remember { mutableStateOf(existing?.water ?: false) }
    var emergency by remember { mutableStateOf(existing?.emergency ?: true) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var openDaysText by remember { mutableStateOf(if (existing != null && existing.openDays > 0) existing.openDays.toString() else "") }
    var estApplied by remember { mutableStateOf(existing?.nutriEstimate ?: false) }
    var image by remember { mutableStateOf(existing?.image) }

    var initQty by remember { mutableStateOf("1") }
    var initExpiry by remember { mutableStateOf<Long?>(null) }
    var initPlace by remember { mutableStateOf(0) }
    var addInitial by remember { mutableStateOf(true) }

    var catTouched by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val n = store.importImage(uri)
            if (n != null) image = n
        }
    }

    val ctx = LocalContext.current
    var ocrBusy by remember { mutableStateOf(false) }
    var ocrResult by remember { mutableStateOf<OcrNutrition?>(null) }
    var ocrMsg by remember { mutableStateOf("") }

    fun runOcr(uri: android.net.Uri) {
        ocrBusy = true
        ocrMsg = ""
        scope.launch {
            val r = Ocr.recognize(ctx, uri)
            ocrBusy = false
            val v = r.getOrNull()
            if (v == null) ocrMsg = "Fotku sa nepodarilo prečítať."
            else if (!v.any) ocrMsg = "Na fotke sa nenašli výživové hodnoty. Skúste ostrejšiu fotku zblízka, len s tabuľkou."
            else ocrResult = v
        }
    }

    val ocrFile = remember { java.io.File(ctx.cacheDir, "ocr").also { it.mkdirs() }.let { java.io.File(it, "label.jpg") } }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) runOcr(android.net.Uri.fromFile(ocrFile))
    }
    fun launchCamera() {
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", ocrFile)
            takePhoto.launch(uri)
        } catch (e: Exception) {
            ocrMsg = "Fotoaparát sa nepodarilo otvoriť."
        }
    }
    val camPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else ocrMsg = "Bez povolenia kamery použite fotku z galérie."
    }
    val labelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runOcr(uri)
    }

    val uom = Uom.entries[uomIdx]

    fun setLiquid(v: Boolean) {
        liquid = v
        uomIdx = Kinds.fitUom(Uom.entries[uomIdx], v).ordinal
    }

    fun applyOff(p: OffProduct) {
        if (name.isBlank()) name = p.name
        if (brand.isBlank()) brand = p.brand
        if (manu.isBlank()) manu = p.manufacturer
        if (p.netAmount > 0 && uom.isCountable) net = Calc.edit(p.netAmount)
        if (p.netAmount > 0 || p.isWater) {
            liquidTouched = true
            setLiquid(p.netIsLiquid || p.isWater)
        }
        if (p.kcal100 != null) {
            kcal = Calc.edit(p.kcal100)
            estApplied = false
        }
        if (p.protein100 != null) protein = Calc.edit(p.protein100)
        if (p.fat100 != null) fat = Calc.edit(p.fat100)
        if (p.satFat100 != null) satFat = Calc.edit(p.satFat100)
        if (p.carbs100 != null) carbs = Calc.edit(p.carbs100)
        if (p.sugar100 != null) sugar = Calc.edit(p.sugar100)
        if (p.fiber100 != null) fiber = Calc.edit(p.fiber100)
        if (p.salt100 != null) salt = Calc.edit(p.salt100)
        if (p.isWater) water = true
        val cat = OpenFoodFacts.mapCategory(p.categoryHint, (kind + " " + name + " " + brand + " " + p.name))
        val ci = db.categories.indexOf(cat)
        if (ci >= 0 && isNew && !catTouched) catIdx = ci
    }

    fun applyEstimate(e: Estimate) {
        kcal = Calc.edit(e.kcal)
        protein = Calc.edit(e.protein)
        fat = Calc.edit(e.fat)
        satFat = Calc.edit(e.satFat)
        carbs = Calc.edit(e.carbs)
        sugar = Calc.edit(e.sugar)
        fiber = Calc.edit(e.fiber)
        salt = Calc.edit(e.salt)
        if (e.liquid != liquid) {
            liquidTouched = true
            setLiquid(e.liquid)
        }
        if (e.pieceG > 0 && uom.isCountable && net.isBlank()) net = Calc.edit(e.pieceG)
        if (e.water) water = true
        estApplied = true
        error = ""
        msg = "Doplnené priemerné hodnoty (odhad): " + e.title + ". Ak máte údaje z obalu, prepíšte ich."
    }

    fun lookup() {
        if (ean.length < 8) {
            error = "Zadajte čiarový kód (EAN)."
            return
        }
        loading = true
        error = ""
        msg = ""
        scope.launch {
            val r = OpenFoodFacts.lookup(ean.trim())
            when (r) {
                is OffResult.Found -> {
                    applyOff(r.product)
                    if (image == null) {
                        val img = store.downloadOffImage(r.product)
                        if (img != null) image = img
                    }
                    msg = "Údaje doplnené z Open Food Facts."
                }
                is OffResult.NotFound -> msg = "Produkt sa v Open Food Facts nenašiel. Vyplňte údaje ručne."
                is OffResult.Error -> error = "Chyba pripojenia: " + r.message
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        if (isNew && d.ean.length >= 8 && d.name.isBlank()) lookup()
    }

    // Kategoria sa sama prisposobi druhu potraviny, kym ju nezmenite rucne.
    LaunchedEffect(kind, name, brand) {
        if (isNew && !catTouched) {
            val c = (if (kind.isNotBlank()) Kinds.categoryFor(kind) else null) ?: Kinds.categoryGuess(name + " " + brand)
            val ci = if (c != null) db.categories.indexOf(c) else -1
            if (ci >= 0) catIdx = ci
        }
    }

    // Skupenstvo (tuhe / tekute) sa odhadne z druhu, nazvu a kategorie, kym ho nezmenite rucne.
    LaunchedEffect(kind, name, brand, catIdx) {
        if (isNew && !liquidTouched) {
            val txt = (if (kind.isNotBlank()) kind + " " else "") + name + " " + brand
            setLiquid(Kinds.isLiquid(txt, db.categories.getOrElse(catIdx) { "" }))
        }
    }

    var showSearch by remember { mutableStateOf(false) }
    var searchQ by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchErr by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<OffProduct>?>(null) }

    fun runSearch() {
        if (searchQ.trim().length < 2) {
            searchErr = "Zadajte aspoň jedno kľúčové slovo."
            return
        }
        searching = true
        searchErr = ""
        results = null
        scope.launch {
            val r = OpenFoodFacts.search(searchQ)
            searching = false
            val list = r.getOrNull()
            if (list == null) searchErr = "Chyba pripojenia: " + (r.exceptionOrNull()?.message ?: "neznáma chyba")
            else results = list
        }
    }

    fun openSearch() {
        searchQ = (name + " " + brand).trim()
        results = null
        searchErr = ""
        showSearch = true
        if (searchQ.length >= 2) runSearch()
    }

    fun save() {
        if (name.isBlank()) {
            error = "Zadajte názov produktu."
            return
        }
        val base = existing ?: Item(name = name.trim())
        val item = base.copy(
            name = name.trim(), brand = brand.trim(), manufacturer = manu.trim(), ean = ean.trim(),
            category = db.categories.getOrElse(catIdx) { "Iné" },
            kind = kind.trim().ifEmpty { Kinds.guess(name + " " + brand) }, image = image, uom = uom,
            net = if (uom.isCountable) (Calc.parse(net) ?: 0.0) else 0.0, netLiquid = liquid,
            price = Calc.parse(price) ?: 0.0, basis = PriceBasis.entries[basisIdx],
            kcal = Calc.parse(kcal), protein = Calc.parse(protein), fat = Calc.parse(fat),
            satFat = Calc.parse(satFat), carbs = Calc.parse(carbs), sugar = Calc.parse(sugar),
            fiber = Calc.parse(fiber), salt = Calc.parse(salt), minQty = Calc.parse(minQty) ?: 0.0,
            placeId = placeAt(db, placeIdx), water = water, emergency = emergency, note = note.trim(),
            openDays = openDaysText.toIntOrNull() ?: 0, nutriEstimate = estApplied && kcal.isNotBlank()
        )
        val id = store.saveItem(item)
        if (isNew) {
            val q = Calc.parse(initQty) ?: 0.0
            if (addInitial && q > 0) {
                store.addStock(id, q, initExpiry, Calc.today(), placeAt(db, if (initPlace == 0) placeIdx else initPlace))
            }
            nav.replace(Dest.ItemDetail(id))
        } else {
            nav.pop()
        }
    }

    ScreenScaffold(
        title = if (isNew) "Nový produkt" else "Upraviť produkt",
        onBack = { nav.pop() }
    ) { pad ->
        FormPage(pad) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ItemThumb(Item(name = name.ifBlank { "?" }, image = image), 72.dp)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { picker.launch("image/*") }) { Text("Vybrať fotku") }
                    if (image != null) OutlinedButton(onClick = { image = null }) { Text("Odstrániť fotku") }
                }
            }
            TextField2("Názov", name, { name = it }, Modifier.fillMaxWidth())
            TextField2("Značka", brand, { brand = it }, Modifier.fillMaxWidth())
            TextField2("Výrobca", manu, { manu = it }, Modifier.fillMaxWidth())
            TextField2("Čiarový kód (EAN)", ean, { ean = it.filter { c -> c.isDigit() } }, Modifier.fillMaxWidth(), keyboard = KeyboardType.Number)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !loading && ean.length >= 8, onClick = { lookup() }) {
                    Text(if (loading) "Sťahujem…" else "Doplniť z internetu")
                }
            }
            OutlinedButton(onClick = { openSearch() }) { Text("Hľadať výživové údaje podľa názvu") }
            if (msg.isNotBlank()) Muted(msg)
            if (error.isNotBlank()) Text(error, color = Pal.Red)

            val kindList = (Kinds.names + db.kinds + (if (kind.isNotBlank()) listOf(kind) else emptyList())).distinct()
            Picker("Druh potraviny (pre recepty)", listOf("Podľa názvu") + kindList, if (kind.isBlank()) 0 else kindList.indexOf(kind) + 1, { kind = if (it == 0) "" else kindList[it - 1] }, Modifier.fillMaxWidth())
            if (kind.isBlank() && name.isNotBlank()) {
                val g = Kinds.guess(name + " " + brand)
                Muted(if (g.isNotBlank()) "Rozpoznané ako: " + g else "Druh sa nerozpoznal, recepty ho nájdu podľa názvu.")
            }
            Picker("Kategória", db.categories, catIdx, { catIdx = it; catTouched = true }, Modifier.fillMaxWidth())
            PlacePicker("Miesto uloženia", db, placeIdx, { placeIdx = it }, Modifier.fillMaxWidth())

            SectionTitle("Množstvo a cena")
            Picker(
                "Skupenstvo", listOf("Tuhá potravina (g, kg)", "Tekutina (ml, l)"), if (liquid) 1 else 0,
                { liquidTouched = true; setLiquid(it == 1) }, Modifier.fillMaxWidth()
            )
            val unitList = Kinds.uomsFor(liquid)
            Picker(
                "Jednotka", unitList.map { it.label + " (" + it.longName + ")" }, unitList.indexOf(uom).coerceAtLeast(0),
                { uomIdx = unitList[it].ordinal }, Modifier.fillMaxWidth()
            )
            if (uom.isCountable) {
                NumField("Obsah jedného kusa", net, { net = it }, Modifier.fillMaxWidth(), suffix = if (liquid) "ml" else "g")
                Muted("Obsah je potrebný na výpočet kalórií, vody a ceny za kg/l.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                NumField("Cena", price, { price = it }, Modifier.weight(1f), suffix = "€")
                Picker("Cena je", PriceBasis.entries.map { it.label }, basisIdx, { basisIdx = it }, Modifier.weight(1f))
            }
            NumField("Minimálna zásoba (upozornenie)", minQty, { minQty = it }, Modifier.fillMaxWidth(), suffix = uom.label)
            val guessOpen = Kinds.openDays(Item(name = name, brand = brand, kind = kind, category = db.categories.getOrElse(catIdx) { "Iné" }))
            NumField(
                "Trvanlivosť po otvorení", openDaysText, { openDaysText = it.filter { c -> c.isDigit() } }, Modifier.fillMaxWidth(),
                suffix = "dní", supporting = "Prázdne = odhad podľa druhu (" + guessOpen + " dní)"
            )

            SectionTitle("Výživové hodnoty na 100 " + (if (liquid) "ml" else "g"))
            val est = remember(kind, name, brand) { Nutri.estimate(kind, name + " " + brand) }
            if (est != null && kcal.isBlank()) {
                SCard {
                    Text("Nemáte údaje z obalu? Použite priemerné hodnoty", fontWeight = FontWeight.Medium)
                    Muted(est.title + " (odhad): " + est.summary())
                    OutlinedButton(onClick = { applyEstimate(est) }) { Text("Použiť odhad") }
                }
            }
            if (estApplied && kcal.isNotBlank()) Muted("Hodnoty sú len priemerný odhad.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !ocrBusy, onClick = {
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                    else camPermission.launch(Manifest.permission.CAMERA)
                }) { Text(if (ocrBusy) "Čítam…" else "Odfotiť etiketu") }
                OutlinedButton(enabled = !ocrBusy, onClick = { labelPicker.launch("image/*") }) { Text("Fotka z galérie") }
            }
            Muted("Odfoťte tabuľku výživových hodnôt na obale. Rozpoznanie beží v telefóne, bez internetu.")
            if (ocrMsg.isNotBlank()) Text(ocrMsg, color = Pal.Amber)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Energia", kcal, { kcal = it; estApplied = false }, Modifier.weight(1f), suffix = "kcal")
                NumField("Bielkoviny", protein, { protein = it }, Modifier.weight(1f), suffix = "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Tuky", fat, { fat = it }, Modifier.weight(1f), suffix = "g")
                NumField("z toho nasýtené", satFat, { satFat = it }, Modifier.weight(1f), suffix = "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Sacharidy", carbs, { carbs = it }, Modifier.weight(1f), suffix = "g")
                NumField("z toho cukry", sugar, { sugar = it }, Modifier.weight(1f), suffix = "g")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Vláknina", fiber, { fiber = it }, Modifier.weight(1f), suffix = "g")
                NumField("Soľ", salt, { salt = it }, Modifier.weight(1f), suffix = "g")
            }

            SectionTitle("Núdzové zásoby")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = emergency, onCheckedChange = { emergency = it })
                Text("  Započítať do núdzových zásob")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = water, onCheckedChange = { water = it })
                Text("  Je to pitná voda")
            }
            TextField2("Poznámka", note, { note = it }, Modifier.fillMaxWidth(), singleLine = false)

            if (isNew) {
                SectionTitle("Úvodná zásoba")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = addInitial, onCheckedChange = { addInitial = it })
                    Text("Rovno pridať do zásob")
                }
                if (addInitial) {
                    NumField("Množstvo", initQty, { initQty = it }, Modifier.fillMaxWidth(), suffix = uom.label)
                    DateField("Dátum expirácie", initExpiry, { initExpiry = it }, Modifier.fillMaxWidth())
                }
            }
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Uložiť") }
        }
    }

    val oc = ocrResult
    if (oc != null) {
        AlertDialog(
            onDismissRequest = { ocrResult = null },
            title = { Text("Rozpoznané hodnoty") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Muted("Skontrolujte ich podľa obalu. Musia byť uvedené na 100 g alebo 100 ml.")
                    if (oc.kcal != null) InfoRow("Energia", Calc.num(oc.kcal, 1) + " kcal")
                    if (oc.protein != null) InfoRow("Bielkoviny", Calc.num(oc.protein, 1) + " g")
                    if (oc.fat != null) InfoRow("Tuky", Calc.num(oc.fat, 1) + " g")
                    if (oc.satFat != null) InfoRow("z toho nasýtené", Calc.num(oc.satFat, 1) + " g")
                    if (oc.carbs != null) InfoRow("Sacharidy", Calc.num(oc.carbs, 1) + " g")
                    if (oc.sugar != null) InfoRow("z toho cukry", Calc.num(oc.sugar, 1) + " g")
                    if (oc.fiber != null) InfoRow("Vláknina", Calc.num(oc.fiber, 1) + " g")
                    if (oc.salt != null) InfoRow("Soľ", Calc.num(oc.salt, 1) + " g")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (oc.kcal != null) kcal = Calc.edit(oc.kcal)
                    if (oc.protein != null) protein = Calc.edit(oc.protein)
                    if (oc.fat != null) fat = Calc.edit(oc.fat)
                    if (oc.satFat != null) satFat = Calc.edit(oc.satFat)
                    if (oc.carbs != null) carbs = Calc.edit(oc.carbs)
                    if (oc.sugar != null) sugar = Calc.edit(oc.sugar)
                    if (oc.fiber != null) fiber = Calc.edit(oc.fiber)
                    if (oc.salt != null) salt = Calc.edit(oc.salt)
                    estApplied = false
                    ocrResult = null
                }) { Text("Použiť") }
            },
            dismissButton = { TextButton(onClick = { ocrResult = null }) { Text("Zrušiť") } }
        )
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Výživové údaje podľa názvu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Muted("Zadajte kľúčové slová (napr. „cestoviny penne“). Údaje sú na 100 g alebo 100 ml. Pri čerstvých potravinách (cibuľa, zemiaky…) nájdete hore priemerný odhad.")
                    SearchField(searchQ, { searchQ = it }, Modifier.fillMaxWidth(), "Kľúčové slová")
                    Button(enabled = !searching, onClick = { runSearch() }) { Text(if (searching) "Hľadám…" else "Hľadať") }
                    if (searchErr.isNotBlank()) Text(searchErr, color = Pal.Red)
                    val generic = Nutri.search(searchQ).take(4)
                    if (generic.isNotEmpty()) {
                        Muted("Priemerné hodnoty (odhad pre čerstvé a základné potraviny)")
                        for (g in generic) {
                            TextButton(onClick = {
                                applyEstimate(g)
                                if (name.isBlank()) name = g.title
                                if (kind.isBlank() && g.kind in Kinds.names) kind = g.kind
                                showSearch = false
                            }) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(g.title + " (odhad)")
                                    Muted(g.summary())
                                }
                            }
                        }
                    }
                    val rs = results
                    if (rs != null && rs.isEmpty()) Muted("Nič sa nenašlo. Skúste kratšie alebo iné kľúčové slovo.")
                    if (rs != null) {
                        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                            for (p in rs) {
                                TextButton(onClick = {
                                    applyOff(p)
                                    if (ean.isBlank() && p.ean.length >= 8) ean = p.ean
                                    if (brand.isBlank()) brand = p.brand
                                    showSearch = false
                                    msg = "Údaje doplnené z produktu: " + p.name
                                    if (image == null) {
                                        scope.launch {
                                            val img = store.downloadOffImage(p)
                                            if (img != null) image = img
                                        }
                                    }
                                }) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(p.name + (if (p.brand.isNotBlank()) " (" + p.brand + ")" else ""))
                                        Muted(
                                            if (p.kcal100 != null) Calc.num(p.kcal100, 0) + " kcal/100 g · B " + Calc.num(p.protein100 ?: 0.0, 1) +
                                                " · T " + Calc.num(p.fat100 ?: 0.0, 1) + " · S " + Calc.num(p.carbs100 ?: 0.0, 1)
                                            else "bez výživových údajov"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSearch = false }) { Text("Zavrieť") } }
        )
    }
}
