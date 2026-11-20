package sk.spajza.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import sk.spajza.app.data.Amounts
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Db
import sk.spajza.app.data.Ean
import sk.spajza.app.data.Estimate
import sk.spajza.app.data.Item
import sk.spajza.app.data.Kinds
import sk.spajza.app.data.MoveType
import sk.spajza.app.data.Need
import sk.spajza.app.data.Nutri
import sk.spajza.app.data.OffProduct
import sk.spajza.app.data.OffResult
import sk.spajza.app.data.OpenFoodFacts
import sk.spajza.app.data.PlaceType
import sk.spajza.app.data.Shelf
import sk.spajza.app.data.Similar
import sk.spajza.app.data.Uom
import sk.spajza.app.data.placeName

private sealed class Pick {
    /** qty = uz zadane mnozstvo (pri prepnuti z noveho produktu na existujuci). */
    data class Existing(val item: Item, val qty: String = "") : Pick()
    data class Generic(val est: Estimate) : Pick()
    /** ean a kind predvyplni skener alebo recept. */
    data class Fresh(val name: String, val ean: String = "", val kind: String = "") : Pick()
}

val EXPIRY_CHIPS: List<Pair<String, Int>> = listOf(
    "+3 dni" to 3, "+1 týždeň" to 7, "+2 týždne" to 14, "+1 mesiac" to 30, "+3 mesiace" to 90, "+1 rok" to 365
)

private val PLACE_BY_CATEGORY: Map<String, String> = mapOf(
    "Mliečne výrobky" to "Chladnička", "Mäso a ryby" to "Chladnička", "Ovocie a zelenina" to "Chladnička",
    "Mrazené" to "Mraznička", "Pečivo" to "Kuchynská skrinka", "Cestoviny, ryža a obilniny" to "Špajza",
    "Konzervy" to "Špajza", "Omáčky a korenie" to "Špajza", "Sladkosti a snacky" to "Špajza", "Nápoje" to "Špajza"
)

/** Po kolkych dnoch od nakupu expirovala posledna (neotvorena) sarza tohto produktu a kde bola ulozena. */
private data class Learned(val days: Int, val placeId: Long?)

private fun learnedShelf(db: Db, itemId: Long): Learned? {
    val b = db.batches.filter { it.itemId == itemId && it.opened == null && it.expiry != null && it.bought != null }
        .maxByOrNull { it.bought ?: 0L } ?: return null
    val ex = b.expiry ?: return null
    val bo = b.bought ?: return null
    val d = ex - bo
    return if (d in 1L..3650L) Learned(d.toInt(), b.placeId) else null
}

/**
 * Miesta, kde produkt uz je alebo kde sa zvykne pridavat (najpravdepodobnejsie prve).
 * Berie aktualne sarze, historiu pridani a predvolene miesto produktu.
 */
private fun placeCandidates(db: Db, item: Item): List<Long> {
    val score = LinkedHashMap<Long, Double>()
    for (b in db.batches) {
        val pid = b.placeId ?: continue
        if (b.itemId == item.id && b.qty > 0) score[pid] = (score[pid] ?: 0.0) + 3.0
    }
    val byName = db.places.associateBy { Calc.norm(it.name) }
    for (m in db.moves) {
        if (m.itemId != item.id || m.type != MoveType.ADD || m.to.isBlank()) continue
        val p = byName[Calc.norm(m.to)] ?: continue
        score[p.id] = (score[p.id] ?: 0.0) + 1.0
    }
    val own = item.placeId
    if (own != null && db.places.any { it.id == own }) score[own] = (score[own] ?: 0.0) + 2.0
    return score.entries.sortedByDescending { it.value }.map { it.key }
}

private fun defaultPlaceIdx(db: Db, item: Item?, est: Estimate?, category: String): Int {
    if (item != null) {
        val top = placeCandidates(db, item).firstOrNull()
        if (top != null) return placeIndex(db, top)
    }
    val wanted = listOf(est?.place ?: "", PLACE_BY_CATEGORY[category] ?: "")
    for (w in wanted) {
        if (w.isBlank()) continue
        val idx = db.places.indexOfFirst { it.name.equals(w, ignoreCase = true) }
        if (idx >= 0) return idx + 1
    }
    return 0
}

/** Prepocita mnozstvo zadane v jednotke u (napr. 500 g) na jednotku produktu (napr. ks); null, ak to nejde. */
private fun toItemQty(item: Item, q: Double, u: Uom): Double? {
    if (u == item.uom) return q
    return when {
        u.isMass -> Amounts.itemQty(item, Need(if (u == Uom.KG) q * 1000 else q, "g"))
        u.isVolume -> Amounts.itemQty(item, Need(if (u == Uom.L) q * 1000 else q, "ml"))
        else -> q
    }
}

/** Rychle pridanie potraviny: napiste nazov, zadajte mnozstvo a hotovo. */
@Composable
fun QuickAddScreen(d: Dest.QuickAdd = Dest.QuickAdd()) {
    val nav = LocalNav.current
    val db = useDb()
    val prefilled = d.ean.isNotBlank() || d.name.isNotBlank() || d.kind.isNotBlank()
    var query by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<Pick?>(if (prefilled) Pick.Fresh(d.name, d.ean, d.kind) else null) }
    var added by remember { mutableStateOf("") }
    val q = query.trim()
    val nq = Calc.norm(q)

    val mine = remember(db, nq) {
        if (nq.isEmpty()) {
            db.items.sortedByDescending { it.created }.take(8)
        } else {
            val direct = db.items.filter { Calc.norm(it.name + " " + it.brand + " " + it.kind).contains(nq) }
            // aj podobne produkty ("muka hladka" ~ "Muka"), aby sa nezakladali duplicity
            val similar = Similar.find(db.items, q).filter { s -> direct.none { it.id == s.id } }
            (direct + similar).take(8)
        }
    }
    val generic = remember(db, q) {
        Nutri.search(q).filter { g -> db.items.none { Calc.norm(it.name) == Calc.norm(g.title) } }.take(6)
    }

    ScreenScaffold(
        title = "Pridať potravinu",
        onBack = { nav.pop() },
        actions = {
            IconButton(onClick = { nav.push(Dest.Scan) }) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Skenovať") }
        }
    ) { pad ->
        val p = pick
        if (p != null) {
            FormPage(pad) {
                key(p) {
                    QuickForm(
                        pick = p,
                        prefilled = prefilled,
                        onCancel = { if (prefilled) nav.pop() else pick = null },
                        onSwitch = { item, qty -> pick = Pick.Existing(item, qty) },
                        onAdded = { text, close ->
                            added = text
                            pick = null
                            query = ""
                            if (close) nav.pop()
                        }
                    )
                }
            }
        } else {
            PageList(pad) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (added.isNotBlank()) Text(added, color = Pal.Green, fontWeight = FontWeight.Medium)
                        SearchField(query, { query = it }, Modifier.fillMaxWidth(), "Čo ste kúpili? (napr. cibuľa)")
                        if (q.isEmpty() && db.items.isEmpty()) {
                            Muted("Začnite písať názov potraviny, napr. „cibuľa“ alebo „mlieko“. Pri základných potravinách doplníme aj odhad výživových hodnôt.")
                        }
                    }
                }
                if (mine.isNotEmpty()) {
                    item { SectionTitle(if (nq.isEmpty()) "Naposledy pridané" else "Už ich máte v zozname") }
                    items(mine.size) { idx ->
                        val it0 = mine[idx]
                        SCard(onClick = { pick = Pick.Existing(it0) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ItemThumb(it0, 40.dp)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(it0.name, fontWeight = FontWeight.Medium, maxLines = 1)
                                    val sub = listOf(it0.brand, it0.category).filter { s -> s.isNotBlank() }
                                    if (sub.isNotEmpty()) Muted(sub.joinToString(" · "))
                                }
                            }
                        }
                    }
                }
                if (generic.isNotEmpty()) {
                    item { SectionTitle("Bežné potraviny (s odhadom výživových hodnôt)") }
                    items(generic.size) { idx ->
                        val g = generic[idx]
                        SCard(onClick = { pick = Pick.Generic(g) }) {
                            Text(g.title, fontWeight = FontWeight.Medium)
                            Muted(g.summary())
                        }
                    }
                }
                if (q.length >= 2) {
                    item {
                        OutlinedButton(onClick = { pick = Pick.Fresh(q) }, modifier = Modifier.fillMaxWidth()) {
                            Text("＋ Pridať „" + q + "“ ako nový produkt")
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { nav.push(Dest.Scan) }) { Text("Skenovať čiarový kód") }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickForm(
    pick: Pick,
    prefilled: Boolean,
    onCancel: () -> Unit,
    onSwitch: (Item, String) -> Unit,
    onAdded: (String, Boolean) -> Unit
) {
    val store = LocalStore.current
    val db = useDb()
    val scope = rememberCoroutineScope()

    val existing: Item? = (pick as? Pick.Existing)?.item
    val startName: String = when (pick) {
        is Pick.Existing -> pick.item.name
        is Pick.Generic -> pick.est.title
        is Pick.Fresh -> pick.name
    }
    val kindOf: String = (pick as? Pick.Fresh)?.kind.orEmpty()
    val est: Estimate? = when (pick) {
        is Pick.Existing -> null
        is Pick.Generic -> pick.est
        is Pick.Fresh -> Nutri.estimate(pick.kind, pick.name)
    }
    val category0: String = existing?.category ?: est?.category ?: Kinds.categoryGuess(startName) ?: "Iné"
    val liquid0: Boolean = if (existing != null) {
        existing.uom.isVolume || existing.netLiquid ||
            (existing.uom.isCountable && existing.net <= 0 && Kinds.isLiquid(existing.name, existing.category))
    } else est?.liquid ?: Kinds.isLiquid(startName, category0)

    var name by remember { mutableStateOf(startName) }
    var brand by remember { mutableStateOf("") }
    var manu by remember { mutableStateOf("") }
    var ean by remember { mutableStateOf((pick as? Pick.Fresh)?.ean.orEmpty()) }
    var category by remember { mutableStateOf(category0) }
    /** true = obsah kusa/balenia je v ml, false = v g (pouzije sa len pri jednotke ks / bal.). */
    var liq by remember { mutableStateOf(liquid0) }
    var unit by remember { mutableStateOf(existing?.uom ?: est?.uom ?: (if (liquid0) Uom.L else Uom.PCS)) }
    var qty by remember { mutableStateOf((pick as? Pick.Existing)?.qty?.ifBlank { "1" } ?: "1") }
    var net by remember { mutableStateOf(if (est != null && est.pieceG > 0) Calc.edit(est.pieceG) else "") }
    var price by remember { mutableStateOf("") }
    var minQty by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var image by remember { mutableStateOf<String?>(null) }
    val shoot = rememberProductCamera { n -> if (n != null) image = n }

    // Vyzivove hodnoty na 100 g / 100 ml (len pri novom produkte)
    var kcal by remember { mutableStateOf(Calc.edit(est?.kcal)) }
    var protein by remember { mutableStateOf(Calc.edit(est?.protein)) }
    var fat by remember { mutableStateOf(Calc.edit(est?.fat)) }
    var satFat by remember { mutableStateOf(Calc.edit(est?.satFat)) }
    var carbs by remember { mutableStateOf(Calc.edit(est?.carbs)) }
    var sugar by remember { mutableStateOf(Calc.edit(est?.sugar)) }
    var fiber by remember { mutableStateOf(Calc.edit(est?.fiber)) }
    var salt by remember { mutableStateOf(Calc.edit(est?.salt)) }
    var water by remember { mutableStateOf(est?.water ?: false) }
    var estApplied by remember { mutableStateOf(est != null) }

    // ---- Expiracia: zaklad (dni) a miesto, pre ktore plati; pri zmene miesta sa prepocita ----
    val learned: Learned? = if (existing != null) learnedShelf(db, existing.id) else null
    val baseDays: Int? = learned?.days ?: est?.shelfDays?.takeIf { it > 0 }
    val baseName: String = if (learned != null) db.placeName(learned.placeId)
    else (est?.place ?: "").ifBlank { PLACE_BY_CATEGORY[category0] ?: "" }
    val baseType: PlaceType = Shelf.typeOf(baseName)

    var placeIdx by remember { mutableStateOf(defaultPlaceIdx(db, existing, est, category0)) }
    val placeLabel: String = db.placeName(placeAt(db, placeIdx))
    val suggestedDays: Int? = Shelf.days(baseDays, baseType, Shelf.typeOf(placeLabel), category)
    val suggestedExpiry: Long? = suggestedDays?.let { Calc.today() + it }
    var expiry by remember { mutableStateOf(suggestedExpiry) }
    /** true, ak pouzivatel datum zmenil sam; potom ho zmena miesta neprepisuje. */
    var expiryTouched by remember { mutableStateOf(false) }
    LaunchedEffect(placeIdx, db.places) {
        if (!expiryTouched) expiry = suggestedExpiry
    }

    // Jednotky, v ktorych sa da mnozstvo zadat (g/ml aj pri produkte v ks, ak poznáme obsah kusa).
    val unitOptions: List<Uom> = when {
        existing == null -> Uom.entries.toList()
        existing.uom.isMass -> listOf(existing.uom, if (existing.uom == Uom.KG) Uom.G else Uom.KG)
        existing.uom.isVolume -> listOf(existing.uom, if (existing.uom == Uom.L) Uom.ML else Uom.L)
        existing.net > 0 -> listOf(existing.uom) + (if (existing.netLiquid) listOf(Uom.L, Uom.ML) else listOf(Uom.KG, Uom.G))
        else -> listOf(existing.uom)
    }
    val itemUom: Uom = existing?.uom ?: unit
    val needsNet: Boolean = if (existing != null) existing.uom.isCountable && existing.net <= 0 else unit.isCountable

    // ---- Podobne produkty (duplicity) ----
    var dupList by remember { mutableStateOf<List<Item>>(emptyList()) }
    var dupAck by remember { mutableStateOf(false) }
    var dupClose by remember { mutableStateOf(false) }

    fun applyEstimate(e: Estimate) {
        kcal = Calc.edit(e.kcal)
        protein = Calc.edit(e.protein)
        fat = Calc.edit(e.fat)
        satFat = Calc.edit(e.satFat)
        carbs = Calc.edit(e.carbs)
        sugar = Calc.edit(e.sugar)
        fiber = Calc.edit(e.fiber)
        salt = Calc.edit(e.salt)
        if (e.liquid != liq) {
            liq = e.liquid
            unit = Kinds.fitUom(unit, liq)
        }
        if (e.pieceG > 0 && unit.isCountable && net.isBlank()) net = Calc.edit(e.pieceG)
        if (e.water) water = true
        estApplied = true
        error = ""
        msg = "Doplnené priemerné hodnoty (odhad): " + e.title + ". Ak máte údaje z obalu, prepíšte ich."
    }

    fun applyOff(p: OffProduct) {
        if (name.isBlank()) name = p.name
        if (brand.isBlank()) brand = p.brand
        if (manu.isBlank()) manu = p.manufacturer
        if (p.netAmount > 0 && unit.isCountable) net = Calc.edit(p.netAmount)
        if (p.netAmount > 0 || p.isWater) {
            liq = p.netIsLiquid || p.isWater
            unit = Kinds.fitUom(unit, liq)
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
        val cat = OpenFoodFacts.mapCategory(p.categoryHint, kindOf + " " + name + " " + brand + " " + p.name)
        if (cat in db.categories && (cat != "Iné" || category == "Iné")) category = cat
    }

    fun lookup() {
        if (!Ean.valid(ean)) {
            error = "Zadajte platný čiarový kód (EAN)."
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

    fun commit(close: Boolean) {
        val qv = Calc.parse(qty)
        if (qv == null || qv <= 0) {
            error = "Zadajte množstvo."
            return
        }
        if (name.isBlank()) {
            error = "Zadajte názov."
            return
        }
        if (existing == null && !dupAck) {
            val code = ean.trim()
            val byEan = if (code.length >= 8) db.items.filter { it.ean == code } else emptyList()
            val similar = (byEan + Similar.find(db.items, name)).distinctBy { it.id }
            if (similar.isNotEmpty()) {
                dupList = similar.take(4)
                dupClose = close
                return
            }
        }
        val priceV = Calc.parse(price) ?: 0.0
        val minV = Calc.parse(minQty) ?: 0.0
        val netV = Calc.parse(net) ?: 0.0
        val place = placeAt(db, placeIdx)
        val id: Long
        val addQty: Double
        if (existing != null) {
            var cur: Item = existing
            if (cur.uom.isCountable && cur.net <= 0 && netV > 0) cur = cur.copy(net = netV, netLiquid = liq)
            if (priceV > 0) {
                val bp = Calc.basisFor(cur.uom, priceV)
                cur = cur.copy(price = bp.second, basis = bp.first)
            }
            if (minV > 0) cur = cur.copy(minQty = minV)
            val conv = toItemQty(cur, qv, unit)
            if (conv == null || conv <= 0) {
                error = "Pre tento produkt nepoznáme obsah kusa, zadajte množstvo v jednotke " + cur.uom.label + "."
                return
            }
            if (cur != existing) store.saveItem(cur)
            id = existing.id
            addQty = conv
        } else {
            val cat = if (category in db.categories) category else "Iné"
            val bp = Calc.basisFor(unit, priceV)
            val kind = kindOf.ifBlank { est?.kind?.takeIf { it in Kinds.names } ?: Kinds.guess(name) }
            val item = Item(
                name = name.trim(), brand = brand.trim(), manufacturer = manu.trim(), ean = ean.trim(),
                category = cat, kind = kind, uom = unit, image = image,
                net = if (unit.isCountable) netV else 0.0,
                netLiquid = if (unit.isVolume) true else if (unit.isMass) false else liq,
                price = if (priceV > 0) bp.second else 0.0, basis = bp.first, minQty = minV,
                kcal = Calc.parse(kcal), protein = Calc.parse(protein), fat = Calc.parse(fat), satFat = Calc.parse(satFat),
                carbs = Calc.parse(carbs), sugar = Calc.parse(sugar), fiber = Calc.parse(fiber), salt = Calc.parse(salt),
                placeId = place, water = water, nutriEstimate = estApplied && kcal.isNotBlank()
            )
            id = store.saveItem(item)
            addQty = qv
        }
        store.addStock(id, addQty, expiry, Calc.today(), place)
        onAdded("Pridané: " + name.trim() + " " + Calc.qty(addQty, itemUom), close)
    }

    // ---- 1. Minimum, co treba vyplnit ----
    if (existing != null) {
        Text(existing.name, style = androidx.compose.material3.MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Muted("Už ho máte v zozname, pridáte ďalšie množstvo.")
    } else {
        TextField2("Názov", name, { name = it }, Modifier.fillMaxWidth())
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        NumField("Množstvo", qty, { qty = it }, Modifier.weight(1f), suffix = if (unitOptions.size == 1) unit.label else null)
        if (unitOptions.size > 1) {
            Picker(
                "Jednotka", unitOptions.map { it.label }, unitOptions.indexOf(unit).coerceAtLeast(0),
                {
                    unit = unitOptions[it]
                    if (unit.isVolume) liq = true else if (unit.isMass) liq = false
                },
                Modifier.weight(1f)
            )
        }
    }
    if (existing != null && unit != existing.uom) {
        val qv = Calc.parse(qty)
        val conv = if (qv != null && qv > 0) toItemQty(existing, qv, unit) else null
        if (conv != null) Muted("Pridá sa " + Calc.qty(conv, existing.uom) + " (obsah 1 " + existing.uom.label + " = " + Calc.num(existing.net, 1) + (if (existing.netLiquid) " ml" else " g") + ").")
    }
    if (needsNet) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            NumField(
                "Obsah 1 " + itemUom.label + " (nepovinné)", net, { net = it }, Modifier.weight(1f),
                supporting = "Napr. 1000 pre 1 kg múky"
            )
            Picker("Jednotka obsahu", listOf("g", "ml"), if (liq) 1 else 0, { liq = it == 1 }, Modifier.weight(0.6f))
        }
    }

    DateField("Dátum expirácie", expiry, { expiry = it; expiryTouched = true }, Modifier.fillMaxWidth())
    val sd = suggestedDays
    if (sd != null && expiry == suggestedExpiry) {
        Muted(
            if (learned != null && Shelf.typeOf(placeLabel) == baseType) {
                "Predvyplnené podľa vašej predchádzajúcej šarže (" + Shelf.describe(sd) + ")."
            } else {
                "Odhad trvanlivosti" + (if (placeLabel.isNotBlank()) " pre miesto „" + placeLabel + "“" else "") +
                    " (" + Shelf.describe(sd) + "), skontrolujte dátum na obale."
            }
        )
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(EXPIRY_CHIPS.size) { idx ->
            FilterChip(
                selected = expiry == Calc.today() + EXPIRY_CHIPS[idx].second,
                onClick = { expiry = Calc.today() + EXPIRY_CHIPS[idx].second; expiryTouched = true },
                label = { Text(EXPIRY_CHIPS[idx].first) }
            )
        }
    }

    // ---- 2. Miesto: ponukne vsetky miesta, kde produkt uz je ----
    val cands: List<Long> = remember(db, existing?.id) {
        if (existing != null) placeCandidates(db, existing).take(4) else emptyList()
    }
    if (existing != null && cands.isNotEmpty()) {
        Text(if (cands.size > 1) "Kde ho už máte (vyberte, kam pridáte)" else "Kde ho už máte", fontWeight = FontWeight.Medium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cands.size) { idx ->
                val pid = cands[idx]
                val here = db.batches.filter { it.itemId == existing.id && it.placeId == pid && it.qty > 0 }.sumOf { it.qty }
                val label = db.places.firstOrNull { it.id == pid }?.name.orEmpty() +
                    (if (here > 0) " · " + Calc.qty(existing, here) else "")
                FilterChip(
                    selected = placeAt(db, placeIdx) == pid,
                    onClick = { placeIdx = placeIndex(db, pid) },
                    label = { Text(label) }
                )
            }
        }
    }
    PlacePicker(
        if (existing != null && cands.isNotEmpty()) "Iné miesto uloženia" else "Miesto uloženia",
        db, placeIdx, { placeIdx = it }, Modifier.fillMaxWidth()
    )

    // ---- 3. Nepovinne udaje (zbalene) ----
    ExpandSection(
        "Viac možností",
        listOf(
            if (brand.isNotBlank()) brand else "",
            if (price.isNotBlank()) price + " €" else "",
            if (minQty.isNotBlank()) "min. " + minQty + " " + itemUom.label else "",
            if (image != null) "fotka" else ""
        ).filter { it.isNotBlank() }.joinToString(" · ").ifBlank {
            if (existing == null) "fotka, značka, cena, EAN, výživové hodnoty" else "cena, minimálna zásoba"
        },
        initiallyOpen = existing == null && ean.isNotBlank()
    ) {
        if (existing == null) {
            TextField2("Značka", brand, { brand = it }, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (image != null) ItemThumb(Item(name = name.ifBlank { "?" }, image = image), 56.dp)
                OutlinedButton(onClick = { shoot() }) { Text(if (image == null) "Odfotiť produkt" else "Odfotiť znova") }
            }
        }
        NumField("Cena za 1 " + itemUom.label, price, { price = it }, Modifier.fillMaxWidth(), suffix = "€")
        NumField("Minimálna zásoba (upozornenie)", minQty, { minQty = it }, Modifier.fillMaxWidth(), suffix = itemUom.label)
        if (existing == null) {
            ExpandSection(
                "Čiarový kód (EAN)",
                ean.ifBlank { "Doplniť údaje z internetu podľa kódu" },
                initiallyOpen = ean.isNotBlank()
            ) {
                TextField2("Čiarový kód (EAN)", ean, { ean = it.filter { c -> c.isDigit() } }, Modifier.fillMaxWidth(), keyboard = KeyboardType.Number)
                if (ean.length >= 8 && !Ean.valid(ean)) {
                    Text("Kód nemá správnu kontrolnú číslicu, skontrolujte číslice.", color = Pal.Red)
                }
                OutlinedButton(enabled = !loading && Ean.valid(ean), onClick = { lookup() }) {
                    Text(if (loading) "Sťahujem…" else "Doplniť podľa EAN")
                }
            }
            ExpandSection(
                "Výživové hodnoty",
                if (kcal.isNotBlank()) kcal + " kcal na 100 " + (if (liq) "ml" else "g") + (if (estApplied) " (odhad)" else "") else "Nevyplnené"
            ) {
                Muted("Hodnoty na 100 " + (if (liq) "ml" else "g"))
                val liveEst = remember(kindOf, name, brand) { Nutri.estimate(kindOf, name + " " + brand) }
                if (liveEst != null && kcal.isBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Nemáte údaje z obalu? Použite priemerné hodnoty", fontWeight = FontWeight.Medium)
                        Muted(liveEst.title + " (odhad): " + liveEst.summary())
                        OutlinedButton(onClick = { applyEstimate(liveEst) }) { Text("Použiť odhad") }
                    }
                }
                if (estApplied && kcal.isNotBlank()) Muted("Hodnoty sú len priemerný odhad.")
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
            }
        } else {
            Muted("Výživové hodnoty a ďalšie údaje upravíte v detaile produktu (Upraviť).")
        }
    }

    if (msg.isNotBlank()) Muted(msg)
    if (error.isNotBlank()) Text(error, color = Pal.Red)
    if (prefilled) {
        Button(onClick = { commit(true) }, modifier = Modifier.fillMaxWidth()) { Text("Pridať") }
    } else {
        Button(onClick = { commit(false) }, modifier = Modifier.fillMaxWidth()) { Text("Pridať a ďalší") }
        OutlinedButton(onClick = { commit(true) }, modifier = Modifier.fillMaxWidth()) { Text("Pridať a zavrieť") }
    }
    TextButton(onClick = onCancel) { Text("Späť") }

    if (dupList.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { dupList = emptyList() },
            title = { Text("Už máte podobný produkt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Muted("Chcete radšej pridať množstvo k existujúcemu produktu?")
                    for (it0 in dupList) {
                        TextButton(onClick = {
                            dupList = emptyList()
                            onSwitch(it0, if (unit == it0.uom) qty else "")
                        }) {
                            Text("Pridať k „" + it0.name + "“" + (if (it0.brand.isNotBlank()) " (" + it0.brand + ")" else ""))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    dupAck = true
                    dupList = emptyList()
                    commit(dupClose)
                }) { Text("Vytvoriť nový") }
            },
            dismissButton = { TextButton(onClick = { dupList = emptyList() }) { Text("Zrušiť") } }
        )
    }
}
