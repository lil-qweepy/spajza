package sk.spajza.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

val DEFAULT_CATEGORIES: List<String> = listOf(
    "Ovocie a zelenina", "Mliečne výrobky", "Mäso a ryby", "Pečivo", "Cestoviny, ryža a obilniny",
    "Konzervy", "Mrazené", "Nápoje", "Sladkosti a snacky", "Omáčky a korenie", "Iné"
)

private val DEFAULT_PLACES: List<String> = listOf(
    "Chladnička", "Mraznička", "Špajza", "Pivnica", "Kuchynská skrinka", "Garáž"
)

/**
 * Jednoduche uloziste: vsetky data su v jednom JSON subore (data.json) a v pamati.
 * Kazda zmena sa zapise na pozadi. Poskodeny subor sa nikdy nezmaze, ulozi sa bokom.
 */
/** Posledna zmena, ktoru je mozne vratit. */
data class UndoInfo(val id: Long, val label: String, val snapshot: Db)

class Store(private val context: Context) {
    private val dir: File = context.filesDir
    private val file: File = File(dir, "data.json")
    val imagesDir: File = File(dir, "images").also { it.mkdirs() }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val writer = Executors.newSingleThreadExecutor()

    /** Text upozornenia, ak sa subor s datami nepodarilo nacitat. */
    var loadWarning: String? = null
        private set

    private val state = MutableStateFlow(load())

    init {
        // Starsie velke fotky sa jednorazovo zmensia na pozadi.
        writer.execute {
            imagesDir.listFiles()?.filter { it.isFile && it.length() > 500_000 }?.forEach { shrinkImage(it) }
        }
    }
    val db: StateFlow<Db> get() = state

    private val undoState = MutableStateFlow<UndoInfo?>(null)
    val undoInfo: StateFlow<UndoInfo?> get() = undoState
    private var undoSeq = 0L

    private fun <T> undoable(label: String, changed: (T) -> Boolean, block: () -> T): T {
        val snap = state.value
        val r = block()
        if (changed(r)) {
            undoSeq++
            undoState.value = UndoInfo(undoSeq, label, snap)
        }
        return r
    }

    @Synchronized
    fun undo() {
        val u = undoState.value ?: return
        undoState.value = null
        state.value = u.snapshot
        persist(u.snapshot)
    }

    fun clearUndo(id: Long) {
        if (undoState.value?.id == id) undoState.value = null
    }

    private fun load(): Db {
        if (!file.exists()) return seed(Db())
        return try {
            seed(json.decodeFromString(Db.serializer(), file.readText()))
        } catch (e: Exception) {
            Log.e("Store", "Chyba nacitania dat", e)
            try {
                file.copyTo(File(dir, "data.corrupt-" + System.currentTimeMillis() + ".json"), overwrite = true)
            } catch (ignored: Exception) {
            }
            loadWarning = "Súbor s údajmi sa nepodarilo načítať, aplikácia začala odznova. " +
                "Pôvodný súbor zostal uložený v pamäti aplikácie. (" + (e.message ?: "chyba") + ")"
            seed(Db())
        }
    }

    private fun seed(d: Db): Db {
        var x = d
        if (x.places.isEmpty()) {
            var id = x.nextId
            val list = ArrayList<Place>()
            for (name in DEFAULT_PLACES) {
                list.add(Place(id, name))
                id++
            }
            x = x.copy(places = list, nextId = id)
        }
        if (x.categories.isEmpty()) x = x.copy(categories = DEFAULT_CATEGORIES)
        return x
    }

    private fun persist(d: Db) {
        writer.execute {
            try {
                val text = json.encodeToString(Db.serializer(), d)
                val tmp = File(dir, "data.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    file.writeText(text)
                    tmp.delete()
                }
            } catch (e: Exception) {
                Log.e("Store", "Chyba ukladania dat", e)
            }
        }
    }

    @Synchronized
    fun update(transform: (Db) -> Db) {
        undoState.value = null
        val n = transform(state.value)
        state.value = n
        persist(n)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        update { d -> d.copy(settings = transform(d.settings)) }
    }

    val settings: AppSettings get() = state.value.settings

    /** Nahradi vsetky data (import zalohy). */
    fun replaceAll(newDb: Db) {
        update { seed(newDb).normalize() }
    }

    // ---------- produkty ----------

    fun saveItem(item: Item): Long {
        var id = item.id
        update { d ->
            val d2 = if (item.id == 0L) {
                id = d.nextId
                d.copy(
                    items = d.items + item.copy(id = id, created = System.currentTimeMillis()),
                    nextId = id + 1
                )
            } else {
                d.copy(items = d.items.map { if (it.id == item.id) item else it })
            }
            d2.normalize()
        }
        return id
    }

    fun deleteItem(id: Long) {
        undoable<Unit>("Produkt zmazaný", { true }) { deleteItemNow(id) }
    }

    private fun deleteItemNow(id: Long) {
        update { d ->
            d.copy(
                items = d.items.filter { it.id != id },
                batches = d.batches.filter { it.itemId != id },
                shop = d.shop.map { if (it.itemId == id) it.copy(itemId = null) else it }
            ).normalize()
        }
    }

    fun findByEan(ean: String): Item? =
        if (ean.isBlank()) null else state.value.items.firstOrNull { it.ean == ean }

    // ---------- pohyby zasob ----------

    fun addStock(itemId: Long, qty: Double, expiry: Long?, bought: Long?, placeId: Long?, note: String = "") {
        update { d -> d.addStockPure(itemId, qty, expiry, bought, placeId, note).normalize() }
    }

    /** Odobratie / spotreba / vyhodenie. Berie najprv z najskorsej expiracie. Vrati skutocne odobrate mnozstvo. */
    fun withdraw(itemId: Long, qty: Double, type: MoveType, batchId: Long? = null, note: String = ""): Double =
        undoable(if (type == MoveType.DISCARD) "Vyhodené" else "Odobraté zo zásob", { it > 0 }) {
            withdrawNow(itemId, qty, type, batchId?.let { setOf(it) }, note)
        }

    /** Odobere len zo zadanych sarzi (napr. len z chladnicky, nie z mrazicky). */
    fun withdrawIn(itemId: Long, qty: Double, type: MoveType, batchIds: Collection<Long>, note: String = ""): Double =
        undoable(if (type == MoveType.DISCARD) "Vyhodené" else "Odobraté zo zásob", { it > 0 }) {
            withdrawNow(itemId, qty, type, batchIds.toSet(), note)
        }

    private fun withdrawNow(itemId: Long, qty: Double, type: MoveType, batchIds: Set<Long>?, note: String): Double {
        var taken = 0.0
        update { d ->
            val item = d.items.firstOrNull { it.id == itemId }
            if (item == null || qty <= 0) {
                d
            } else {
                var remaining = qty
                val left = HashMap<Long, Double>()
                val src = d.batches
                    .filter { it.itemId == itemId && (batchIds == null || it.id in batchIds) }
                    .sortedBy { it.expiry ?: Long.MAX_VALUE }
                for (b in src) {
                    if (remaining <= 1e-9) break
                    val t = minOf(b.qty, remaining)
                    left[b.id] = b.qty - t
                    remaining -= t
                    taken += t
                }
                val batches = d.batches.mapNotNull { b ->
                    val nq = left[b.id]
                    if (nq == null) b else if (nq <= 1e-9) null else b.copy(qty = nq)
                }
                var d2 = d.copy(batches = batches)
                if (taken > 0) d2 = d2.addMove(item, type, -taken, note)
                d2.normalize()
            }
        }
        return taken
    }

    /** Nastavi presne mnozstvo sarze. */
    fun adjustBatch(batchId: Long, newQty: Double, note: String = "") {
        update { d ->
            val b = d.batches.firstOrNull { it.id == batchId }
            val item = if (b == null) null else d.items.firstOrNull { it.id == b.itemId }
            if (b == null || item == null) {
                d
            } else {
                val delta = newQty - b.qty
                var d2 = d.copy(batches = d.batches.map { if (it.id == batchId) it.copy(qty = newQty) else it })
                if (kotlin.math.abs(delta) > 1e-9) d2 = d2.addMove(item, MoveType.ADJUST, delta, note)
                d2.normalize()
            }
        }
    }

    /**
     * Otvori balenie: cast sarze sa oddeli do novej "otvorenej" sarze so skratenou expiraciou.
     * leftFraction = aka cast otvoreneho balenia zostala (1 = nic sa este nezjedlo).
     * Vrati true, ak sa nieco zmenilo.
     */
    fun openPack(batchId: Long, amount: Double, days: Int, leftFraction: Double = 1.0): Boolean =
        undoable<Boolean>("Balenie otvorené", { it }) { openPackNow(batchId, amount, days, leftFraction) }

    private fun openPackNow(batchId: Long, amount: Double, days: Int, leftFraction: Double): Boolean {
        var done = false
        update { d ->
            val b = d.batches.firstOrNull { it.id == batchId }
            val item = if (b == null) null else d.items.firstOrNull { it.id == b.itemId }
            if (b == null || item == null || b.opened != null || amount <= 0 || days <= 0) {
                d
            } else {
                done = true
                val amt = minOf(amount, b.qty)
                val frac = leftFraction.coerceIn(0.0, 1.0)
                val today = Calc.today()
                val limit = today + days
                val oldExpiry = b.expiry
                val newExpiry = if (oldExpiry == null) limit else minOf(oldExpiry, limit)
                val rest = b.qty - amt
                val openedQty = amt * frac
                val used = amt - openedQty
                val list = ArrayList<Batch>(d.batches.filter { it.id != b.id })
                if (rest > 1e-9) list.add(b.copy(qty = rest))
                var nid = d.nextId
                if (openedQty > 1e-9) {
                    val idx = list.indexOfFirst {
                        it.itemId == b.itemId && it.opened == today && it.expiry == newExpiry &&
                            it.placeId == b.placeId && it.bought == b.bought
                    }
                    if (idx >= 0) {
                        list[idx] = list[idx].copy(qty = list[idx].qty + openedQty)
                    } else {
                        list.add(
                            Batch(
                                id = nid, itemId = b.itemId, qty = openedQty, bought = b.bought, expiry = newExpiry,
                                placeId = b.placeId, opened = today, origExpiry = oldExpiry
                            )
                        )
                        nid++
                    }
                }
                var d2 = d.copy(batches = list, nextId = nid)
                if (used > 1e-9) d2 = d2.addMove(item, MoveType.CONSUME, -used, "Pri otvorení")
                d2 = d2.addMove(item, MoveType.OPEN, 0.0, "Spotrebovať do " + Calc.date(newExpiry))
                d2.normalize()
            }
        }
        return done
    }

    /** Odoberie viac produktov naraz (napr. po uvareni obeda) ako jednu zmenu, ktoru je mozne vratit. */
    fun withdrawAll(entries: List<Pair<Long, Double>>, note: String): Int =
        undoable<Int>("Suroviny odpočítané", { it > 0 }) {
            var n = 0
            for (e in entries) {
                if (withdrawNow(e.first, e.second, MoveType.CONSUME, null, note) > 0) n++
            }
            n
        }

    /** Uprava udajov sarze (mnozstvo, datumy, miesto). */
    fun updateBatch(nb: Batch) {
        update { d ->
            val old = d.batches.firstOrNull { it.id == nb.id }
            val item = if (old == null) null else d.items.firstOrNull { it.id == old.itemId }
            if (old == null || item == null) {
                d
            } else {
                val fixed = if (old.expiry != nb.expiry) nb.copy(notified = 0) else nb
                var d2 = d.copy(batches = d.batches.map { if (it.id == nb.id) fixed else it })
                val delta = fixed.qty - old.qty
                if (kotlin.math.abs(delta) > 1e-9) d2 = d2.addMove(item, MoveType.ADJUST, delta, "Úprava šarže")
                d2.normalize()
            }
        }
    }

    /**
     * Presunie (cast) sarze na ine miesto. Ak changeExpiry = true, presunuta cast dostane expiraciu newExpiry
     * (napr. po zmrazeni mesiace, po rozmrazeni dni).
     */
    fun moveBatch(batchId: Long, toPlace: Long?, qty: Double, newExpiry: Long? = null, changeExpiry: Boolean = false) {
        update { d ->
            val b = d.batches.firstOrNull { it.id == batchId }
            val item = if (b == null) null else d.items.firstOrNull { it.id == b.itemId }
            if (b == null || item == null || b.placeId == toPlace || qty <= 0) {
                d
            } else {
                val moved = minOf(qty, b.qty)
                val left = b.qty - moved
                val exp = if (changeExpiry) newExpiry else b.expiry
                val expChanged = exp != b.expiry
                val list = ArrayList<Batch>(d.batches.filter { it.id != b.id })
                if (left > 1e-9) list.add(b.copy(qty = left))
                var nid = d.nextId
                val idx = list.indexOfFirst {
                    it.itemId == b.itemId && it.expiry == exp && it.placeId == toPlace && it.bought == b.bought &&
                        it.opened == b.opened
                }
                if (idx >= 0) {
                    list[idx] = list[idx].copy(qty = list[idx].qty + moved)
                } else {
                    list.add(b.copy(id = nid, qty = moved, placeId = toPlace, expiry = exp, notified = if (expChanged) 0 else b.notified))
                    nid++
                }
                d.copy(batches = list, nextId = nid)
                    .addMove(
                        item, MoveType.MOVE, moved,
                        note = if (expChanged) "Expirácia prepočítaná: " + Calc.date(exp) else "",
                        from = d.placeName(b.placeId), to = d.placeName(toPlace)
                    )
                    .normalize()
            }
        }
    }

    // ---------- miesta a kategorie ----------

    fun savePlace(id: Long, name: String) {
        update { d ->
            if (id == 0L) d.copy(places = d.places + Place(d.nextId, name), nextId = d.nextId + 1)
            else d.copy(places = d.places.map { if (it.id == id) it.copy(name = name) else it })
        }
    }

    fun deletePlace(id: Long) {
        undoable<Unit>("Miesto zmazané", { true }) { deletePlaceNow(id) }
    }

    private fun deletePlaceNow(id: Long) {
        update { d ->
            d.copy(
                places = d.places.filter { it.id != id },
                batches = d.batches.map { if (it.placeId == id) it.copy(placeId = null) else it },
                items = d.items.map { if (it.placeId == id) it.copy(placeId = null) else it }
            )
        }
    }

    fun saveCategory(old: String?, name: String) {
        update { d ->
            if (old == null) {
                if (name in d.categories) d else d.copy(categories = d.categories + name)
            } else {
                d.copy(
                    categories = d.categories.map { if (it == old) name else it }.distinct(),
                    items = d.items.map { if (it.category == old) it.copy(category = name) else it }
                )
            }
        }
    }

    fun deleteCategory(name: String) {
        update { d ->
            d.copy(
                categories = d.categories.filter { it != name },
                items = d.items.map { if (it.category == name) it.copy(category = "Iné") else it }
            )
        }
    }

    fun movePlace(id: Long, dir: Int) {
        update { d -> d.copy(places = d.places.moved(d.places.indexOfFirst { it.id == id }, dir)) }
    }

    fun moveCategory(name: String, dir: Int) {
        update { d -> d.copy(categories = d.categories.moved(d.categories.indexOf(name), dir)) }
    }

    fun saveKind(old: String?, name: String) {
        update { d ->
            if (old == null) {
                if (name in d.kinds) d else d.copy(kinds = d.kinds + name)
            } else {
                d.copy(
                    kinds = d.kinds.map { if (it == old) name else it }.distinct(),
                    items = d.items.map { if (it.kind == old) it.copy(kind = name) else it }
                )
            }
        }
    }

    fun deleteKind(name: String) {
        update { d ->
            d.copy(kinds = d.kinds.filter { it != name }, items = d.items.map { if (it.kind == name) it.copy(kind = "") else it })
        }
    }

    // ---------- nakupny zoznam ----------

    fun saveShop(s: ShopItem) {
        update { d ->
            if (s.id == 0L) d.copy(shop = d.shop + s.copy(id = d.nextId), nextId = d.nextId + 1)
            else d.copy(shop = d.shop.map { if (it.id == s.id) s else it })
        }
    }

    fun deleteShop(id: Long) {
        undoable<Unit>("Položka odstránená", { true }) { update { d -> d.copy(shop = d.shop.filter { it.id != id }) } }
    }

    fun clearCheckedShop() {
        undoable<Unit>("Označené odstránené", { true }) { update { d -> d.copy(shop = d.shop.filter { !it.checked }) } }
    }

    /** Prenesie oznacene polozky do inventara. Vrati pocet prenesenych. */
    fun transferChecked(): Int = undoable("Prenesené do zásob", { it > 0 }) { transferNow() }

    private fun transferNow(): Int {
        var count = 0
        update { d ->
            var x = d
            val checked = d.shop.filter { it.checked && it.qty > 0 }
            count = checked.size
            val today = Calc.today()
            for (s in checked) {
                val existing = s.itemId?.let { id -> x.items.firstOrNull { it.id == id } }
                    ?: x.items.firstOrNull { it.name.equals(s.name, ignoreCase = true) }
                val item: Item
                if (existing == null) {
                    val bp = Calc.basisFor(s.uom, s.price)
                    val created = Item(
                        id = x.nextId, name = s.name, uom = s.uom, price = bp.second, basis = bp.first,
                        created = System.currentTimeMillis()
                    )
                    x = x.copy(items = x.items + created, nextId = x.nextId + 1)
                    item = created
                } else if (s.price > 0 && existing.uom == s.uom) {
                    val bp = Calc.basisFor(s.uom, s.price)
                    val upd = existing.copy(price = bp.second, basis = bp.first)
                    x = x.copy(items = x.items.map { if (it.id == upd.id) upd else it })
                    item = upd
                } else {
                    item = existing
                }
                val qty = convertQty(s.qty, s.uom, item.uom)
                val place = item.placeId ?: x.places.firstOrNull()?.id
                x = x.addStockPure(item.id, qty, null, today, place, "Z nákupného zoznamu")
            }
            x.copy(shop = x.shop.filter { s -> checked.none { it.id == s.id } }).normalize()
        }
        return count
    }

    // ---------- recepty ----------

    fun saveRecipe(r: Recipe): Long {
        var id = r.id
        update { d ->
            if (r.id == 0L) {
                id = d.nextId
                d.copy(recipes = d.recipes + r.copy(id = id), nextId = id + 1)
            } else {
                d.copy(recipes = d.recipes.map { if (it.id == r.id) r else it })
            }
        }
        return id
    }

    fun deleteRecipe(id: Long) {
        undoable<Unit>("Recept zmazaný", { true }) { update { d -> d.copy(recipes = d.recipes.filter { it.id != id }) } }
    }

    // ---------- obrazky ----------

    fun newImageFile(): File = File(imagesDir, "img_" + UUID.randomUUID() + ".jpg")

    /** Zmensi velku fotku (dlhsia strana max. 1200 px, JPEG 85 %) a zohladni jej otocenie. Pri chybe ostane povodny subor. */
    fun shrinkImage(f: File, maxSide: Int = 1200) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return
            var sample = 1
            while (maxOf(w, h) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = BitmapFactory.decodeFile(f.absolutePath, opts) ?: return
            val orientation = ExifInterface(f.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val scale = minOf(1f, maxSide.toFloat() / maxOf(raw.width, raw.height))
            val out: Bitmap = if (scale < 1f || degrees != 0f) {
                val m = Matrix()
                if (scale < 1f) m.postScale(scale, scale)
                if (degrees != 0f) m.postRotate(degrees)
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
            } else {
                raw
            }
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (out !== raw) out.recycle()
            raw.recycle()
            if (tmp.length() > 0 && tmp.renameTo(f).not()) tmp.delete()
        } catch (e: Throwable) {
            Log.w("Store", "Zmensenie fotky zlyhalo", e)
        }
    }

    fun imageFile(name: String?): File? {
        if (name == null) return null
        val f = File(imagesDir, name)
        return if (f.exists()) f else null
    }

    /** Skopiruje obrazok z URI (galeria) do lokalneho uloziska. */
    fun importImage(uri: Uri): String? = try {
        val f = newImageFile()
        context.contentResolver.openInputStream(uri)?.use { input ->
            f.outputStream().use { input.copyTo(it) }
        }
        if (f.length() > 0) {
            shrinkImage(f)
            f.name
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    // ---------- Open Food Facts ----------

    suspend fun downloadOffImage(off: OffProduct): String? {
        val url = off.imageUrl ?: return null
        val name = "off_" + off.ean.ifBlank { UUID.randomUUID().toString() } + ".jpg"
        return if (OpenFoodFacts.downloadImage(url, File(imagesDir, name))) name else null
    }

    /** Ulozi produkt z Open Food Facts do vlastnej databazy (aj s fotografiou). */
    suspend fun saveFromOff(off: OffProduct): Item {
        val category = OpenFoodFacts.mapCategory(off.categoryHint, off.name + " " + off.brand)
        val image = downloadOffImage(off)
        var saved: Item? = null
        update { d ->
            val cats = if (category in d.categories) d.categories else d.categories + category
            val item = Item(
                id = d.nextId, ean = off.ean, name = off.name, brand = off.brand, manufacturer = off.manufacturer,
                category = category, kind = Kinds.guess(off.name + " " + off.brand), image = image, net = off.netAmount, netLiquid = off.netIsLiquid,
                kcal = off.kcal100, protein = off.protein100, fat = off.fat100, satFat = off.satFat100,
                carbs = off.carbs100, sugar = off.sugar100, fiber = off.fiber100, salt = off.salt100,
                water = off.isWater, created = System.currentTimeMillis()
            )
            saved = item
            d.copy(items = d.items + item, categories = cats, nextId = d.nextId + 1)
        }
        return saved!!
    }

    /** Ulozi nenajdeny produkt len s kodom a fotkou; udaje sa doplnia neskor (draft). */
    fun saveDraft(ean: String, image: String?): Item {
        var saved: Item? = null
        update { d ->
            val item = Item(
                id = d.nextId, ean = ean, name = "Nový produkt " + ean, image = image,
                draft = true, created = System.currentTimeMillis()
            )
            saved = item
            d.copy(items = d.items + item, nextId = d.nextId + 1)
        }
        return saved!!
    }

    /** Doplni len chybajuce udaje produktu; uz zadane hodnoty nemeni. */
    private suspend fun mergeOff(p: Item, off: OffProduct): Item {
        val useNet = p.uom.isCountable && p.net <= 0 && off.netAmount > 0
        return p.copy(
            brand = p.brand.ifBlank { off.brand },
            manufacturer = p.manufacturer.ifBlank { off.manufacturer },
            image = p.image ?: downloadOffImage(off),
            net = if (useNet) off.netAmount else p.net,
            netLiquid = if (useNet) off.netIsLiquid else p.netLiquid,
            kcal = p.kcal ?: off.kcal100,
            protein = p.protein ?: off.protein100,
            fat = p.fat ?: off.fat100,
            satFat = p.satFat ?: off.satFat100,
            carbs = p.carbs ?: off.carbs100,
            sugar = p.sugar ?: off.sugar100,
            fiber = p.fiber ?: off.fiber100,
            salt = p.salt ?: off.salt100,
            water = p.water || off.isWater
        )
    }

    /** Doplni chybajuce kalorie a vyzivove hodnoty podla EAN. */
    suspend fun autoFillItem(id: Long): OffResult? {
        val p = state.value.items.firstOrNull { it.id == id } ?: return null
        if (p.ean.length < 8 || p.kcal != null) return null
        val r = OpenFoodFacts.lookup(p.ean)
        if (r is OffResult.Found) {
            val merged = mergeOff(p, r.product)
            if (merged != p) {
                update { d -> d.copy(items = d.items.map { if (it.id == id) merged else it }).normalize() }
            }
        }
        return r
    }

    /** Doplni kalorie vsetkym produktom s EAN, ktore ich nemaju. Pri chybe pripojenia skonci skor. */
    suspend fun autoFillAll(): Int {
        var filled = 0
        for (p in state.value.items) {
            if (p.ean.length < 8 || p.kcal != null) continue
            when (val r = autoFillItem(p.id)) {
                is OffResult.Found -> if (r.product.kcal100 != null) filled++
                is OffResult.Error -> return filled
                else -> {}
            }
        }
        return filled
    }
}

// ---------- ciste funkcie nad databazou ----------

private fun <T> List<T>.moved(index: Int, dir: Int): List<T> {
    val j = index + dir
    if (index < 0 || j < 0 || j >= size) return this
    val m = ArrayList(this)
    val t = m[index]
    m[index] = m[j]
    m[j] = t
    return m
}

private fun convertQty(q: Double, from: Uom, to: Uom): Double = when {
    from == to -> q
    from == Uom.KG && to == Uom.G -> q * 1000
    from == Uom.G && to == Uom.KG -> q / 1000
    from == Uom.L && to == Uom.ML -> q * 1000
    from == Uom.ML && to == Uom.L -> q / 1000
    else -> q
}

private fun Db.addMove(
    item: Item, type: MoveType, qty: Double, note: String = "", from: String = "", to: String = ""
): Db {
    val m = Move(
        id = nextId, itemId = item.id, name = item.name, type = type, qty = qty, uom = item.uom,
        value = Calc.value(item, qty), kcal = Calc.kcal(item, qty), waterMl = Calc.waterMl(item, qty),
        time = System.currentTimeMillis(), note = note, from = from, to = to
    )
    return copy(moves = (listOf(m) + moves).take(5000), nextId = nextId + 1)
}

private fun Db.addStockPure(
    itemId: Long, qty: Double, expiry: Long?, bought: Long?, placeId: Long?, note: String
): Db {
    if (qty <= 0) return this
    val item = items.firstOrNull { it.id == itemId } ?: return this
    val ex = batches.firstOrNull {
        it.itemId == itemId && it.expiry == expiry && it.placeId == placeId && it.bought == bought && it.opened == null
    }
    val d2 = if (ex != null) {
        copy(batches = batches.map { if (it.id == ex.id) it.copy(qty = it.qty + qty) else it })
    } else {
        copy(
            batches = batches + Batch(
                id = nextId, itemId = itemId, qty = qty, bought = bought, expiry = expiry, placeId = placeId
            ),
            nextId = nextId + 1
        )
    }
    return d2.addMove(item, MoveType.ADD, qty, note, to = placeName(placeId))
}

/** Upratanie po zmene: prazdne sarze, znacky nizkych zasob a denny snimok hodnoty zasob. */
private fun Db.normalize(): Db {
    val totals = HashMap<Long, Double>()
    for (b in batches) totals[b.itemId] = (totals[b.itemId] ?: 0.0) + b.qty
    val fixed = items.map { i ->
        val q = totals[i.id] ?: 0.0
        val low = i.minQty > 0 && q <= i.minQty
        if (!low && i.lowNotified) i.copy(lowNotified = false) else i
    }
    val byId = fixed.associateBy { it.id }
    val bs = batches.filter { it.qty > 1e-9 && byId.containsKey(it.itemId) }
    var v = 0.0
    for (b in bs) {
        val it0 = byId[b.itemId]
        if (it0 != null) v += Calc.value(it0, b.qty)
    }
    val day = Calc.today()
    val snaps2 = (snaps.filter { it.day != day } + Snap(day, v)).takeLast(400)
    return copy(items = fixed, batches = bs, snaps = snaps2)
}
