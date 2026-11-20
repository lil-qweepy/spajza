package sk.spajza.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Vysledok vyhladavania v Open Food Facts. */
data class OffProduct(
    val ean: String,
    val name: String,
    val brand: String,
    val manufacturer: String,
    val categoryHint: String,
    val imageUrl: String?,
    val netAmount: Double,
    val netIsLiquid: Boolean,
    val kcal100: Double?,
    val protein100: Double?,
    val fat100: Double?,
    val satFat100: Double?,
    val carbs100: Double?,
    val sugar100: Double?,
    val fiber100: Double?,
    val salt100: Double?,
    val isWater: Boolean
)

sealed class OffResult {
    data class Found(val product: OffProduct) : OffResult()
    object NotFound : OffResult()
    data class Error(val message: String) : OffResult()
}

object OpenFoodFacts {
    private const val UA = "DomaceZasoby/1.0 (Android; osobna aplikacia)"
    private const val FIELDS = "code,product_name,product_name_sk,generic_name,brands,manufacturing_places," +
        "brand_owner,image_front_url,image_url,categories,categories_tags,quantity,product_quantity," +
        "product_quantity_unit,nutriments"

    private const val OFF_HOST = "world.openfoodfacts.org"
    private const val OPF_HOST = "world.openproductsfacts.org"

    /** Hlada postupne vo viacerych zdrojoch: Open Food Facts, Open Products Facts, UPCitemdb. */
    suspend fun lookup(ean: String): OffResult {
        var error: OffResult.Error? = null
        for (code in codesFor(ean)) {
            for (host in listOf(OFF_HOST, OPF_HOST)) {
                val r = lookupOnce(code, host)
                if (r is OffResult.Found) return OffResult.Found(r.product.copy(ean = ean))
                if (r is OffResult.Error) error = r
            }
            val u = lookupUpcItemDb(code)
            if (u is OffResult.Found) return OffResult.Found(u.product.copy(ean = ean))
            if (u is OffResult.Error) error = u
        }
        return error ?: OffResult.NotFound
    }

    /** UPC-A (12 cifier) sa v databazach vedie aj ako EAN-13 s predponou 0. */
    private fun codesFor(ean: String): List<String> =
        if (ean.length == 12) listOf(ean, "0$ean") else listOf(ean)

    /** UPCitemdb (bezplatny limit): da len nazov, znacku a fotku, bez vyzivovych hodnot. */
    private suspend fun lookupUpcItemDb(ean: String): OffResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.upcitemdb.com/prod/trial/lookup?upc=$ean")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code == 404 || code == 429) return@withContext OffResult.NotFound
            if (code !in 200..299) return@withContext OffResult.NotFound
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(body).optJSONArray("items") ?: return@withContext OffResult.NotFound
            val p = arr.optJSONObject(0) ?: return@withContext OffResult.NotFound
            val title = p.str("title")
            if (title.isBlank()) return@withContext OffResult.NotFound
            val imgs = p.optJSONArray("images")
            val img = if (imgs != null && imgs.length() > 0) imgs.optString(0, "") else ""
            val qty = parseQuantity(title)
            OffResult.Found(
                OffProduct(
                    ean = ean, name = title, brand = p.str("brand"), manufacturer = "", categoryHint = p.str("category"),
                    imageUrl = if (img.startsWith("http")) img else null,
                    netAmount = qty?.first ?: 0.0, netIsLiquid = qty?.second ?: false,
                    kcal100 = null, protein100 = null, fat100 = null, satFat100 = null,
                    carbs100 = null, sugar100 = null, fiber100 = null, salt100 = null, isWater = false
                )
            )
        } catch (e: java.net.UnknownHostException) {
            OffResult.Error("Bez pripojenia na internet")
        } catch (e: Exception) {
            OffResult.NotFound
        }
    }

    private suspend fun lookupOnce(ean: String, host: String): OffResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://$host/api/v2/product/$ean.json?fields=$FIELDS")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code == 404) return@withContext OffResult.NotFound
            if (code !in 200..299) return@withContext OffResult.Error("Server vrátil chybu $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            if (root.optInt("status", 0) != 1) return@withContext OffResult.NotFound
            val p = root.optJSONObject("product") ?: return@withContext OffResult.NotFound
            val parsed = parse(ean, p)
            if (parsed.name.isBlank()) OffResult.NotFound else OffResult.Found(parsed)
        } catch (e: java.net.UnknownHostException) {
            OffResult.Error("Bez pripojenia na internet")
        } catch (e: java.net.SocketTimeoutException) {
            OffResult.Error("Vypršal čas pripojenia")
        } catch (e: Exception) {
            OffResult.Error(e.message ?: "Neznáma chyba")
        }
    }

    /** Hladanie podla nazvu (a znacky). Vysledky s kaloriami su prve. */
    suspend fun search(query: String): Result<List<OffProduct>> = withContext(Dispatchers.IO) {
        runCatching {
            // Ak plny dotaz nic nenajde, skusa sa postupne menej klucovych slov.
            val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            var found = emptyList<OffProduct>()
            var n = words.size
            while (n >= 1 && found.isEmpty()) {
                found = searchOnce(words.take(n).joinToString(" "))
                n--
            }
            found
        }
    }

    private fun searchOnce(query: String): List<OffProduct> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL(
            "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$q&search_simple=1" +
                "&action=process&json=1&page_size=15&lc=sk&fields=$FIELDS"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", UA)
        if (conn.responseCode !in 200..299) throw java.io.IOException("Server vrátil chybu ${conn.responseCode}")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val arr = JSONObject(body).optJSONArray("products") ?: return emptyList<OffProduct>()
        val list = (0 until arr.length()).mapNotNull { i ->
            val p = arr.optJSONObject(i) ?: return@mapNotNull null
            val o = parse(p.str("code"), p)
            if (o.name.isBlank()) null else o
        }
        return list.sortedByDescending { it.kcal100 != null }
    }

    private fun JSONObject.str(key: String): String = optString(key, "").trim().let { if (it == "null") "" else it }

    private fun JSONObject.dbl(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = opt(key)
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.replace(',', '.').toDoubleOrNull()
            else -> null
        }
    }

    private fun parse(ean: String, p: JSONObject): OffProduct {
        val name = p.str("product_name_sk").ifBlank { p.str("product_name") }.ifBlank { p.str("generic_name") }
        val brand = p.str("brands").split(",").firstOrNull()?.trim().orEmpty()
        val manufacturer = p.str("brand_owner").ifBlank { p.str("manufacturing_places") }
        val tags = p.optJSONArray("categories_tags")?.let { arr ->
            (0 until arr.length()).map { arr.optString(it) }
        }.orEmpty()
        val categories = p.str("categories")
        val image = p.str("image_front_url").ifBlank { p.str("image_url") }.ifBlank { null }

        // Hmotnost / objem
        var amount = 0.0
        var liquid = false
        val pq = p.dbl("product_quantity")
        val pqUnit = p.str("product_quantity_unit").lowercase()
        val quantityText = p.str("quantity")
        val parsedQ = parseQuantity(quantityText)
        if (parsedQ != null) {
            amount = parsedQ.first
            liquid = parsedQ.second
        } else if (pq != null && pq > 0) {
            amount = pq
            liquid = pqUnit == "ml" || pqUnit == "l" || pqUnit == "cl"
        }
        val isBeverage = tags.any { it.contains("beverage") || it.contains("water") }
        if (amount > 0 && isBeverage && !quantityText.lowercase().contains("g")) liquid = true

        val n = p.optJSONObject("nutriments")
        val kcal = n?.dbl("energy-kcal_100g") ?: n?.dbl("energy-kcal")
            ?: n?.dbl("energy_100g")?.let { it / 4.184 }

        val isWater = tags.any { it.contains("waters") || it == "en:water" || it.contains("mineral-waters") ||
            it.contains("spring-waters") }

        return OffProduct(
            ean = ean,
            name = name,
            brand = brand,
            manufacturer = manufacturer,
            categoryHint = (tags + categories).joinToString(" ").lowercase(),
            imageUrl = image,
            netAmount = amount,
            netIsLiquid = liquid,
            kcal100 = kcal,
            protein100 = n?.dbl("proteins_100g"),
            fat100 = n?.dbl("fat_100g"),
            satFat100 = n?.dbl("saturated-fat_100g"),
            carbs100 = n?.dbl("carbohydrates_100g"),
            sugar100 = n?.dbl("sugars_100g"),
            fiber100 = n?.dbl("fiber_100g"),
            salt100 = n?.dbl("salt_100g"),
            isWater = isWater
        )
    }

    /** "500 g", "1,5 l", "6 x 330 ml" -> (mnozstvo v g/ml, je kvapalina). */
    fun parseQuantity(text: String): Pair<Double, Boolean>? {
        if (text.isBlank()) return null
        val t = text.lowercase().replace(',', '.')
        val multi = Regex("(\\d+)\\s*[x×]\\s*(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|cl|dl|l)\\b").find(t)
        val single = Regex("(\\d+(?:\\.\\d+)?)\\s*(kg|g|ml|cl|dl|l)\\b").find(t)
        val (mult, value, unit) = when {
            multi != null -> Triple(multi.groupValues[1].toDouble(), multi.groupValues[2].toDouble(), multi.groupValues[3])
            single != null -> Triple(1.0, single.groupValues[1].toDouble(), single.groupValues[2])
            else -> return null
        }
        return when (unit) {
            "kg" -> (mult * value * 1000) to false
            "g" -> (mult * value) to false
            "l" -> (mult * value * 1000) to true
            "dl" -> (mult * value * 100) to true
            "cl" -> (mult * value * 10) to true
            "ml" -> (mult * value) to true
            else -> null
        }
    }

    /** Stiahne obrazok do lokalneho uloziska; vrati nazov suboru alebo null. */
    suspend fun downloadImage(url: String, target: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", UA)
            if (conn.responseCode !in 200..299) return@withContext false
            target.parentFile?.mkdirs()
            conn.inputStream.use { input -> target.outputStream().use { out -> input.copyTo(out) } }
            target.length() > 0
        } catch (e: Exception) {
            target.delete()
            false
        }
    }

    /** Priradi kategoriu podla klucovych slov z OFF. */
    /**
     * Kategoria z textu kategorii Open Food Facts a z nazvu. Druh rozpoznany z nazvu ma prednost.
     * Vseobecne znacky ako "plant-based foods and beverages" sa ignoruju (inak by aj ryza isla medzi napoje).
     */
    fun mapCategory(hint: String, name: String = ""): String {
        if (name.isNotBlank()) Kinds.categoryGuess(name)?.let { return it }
        val h = hint.lowercase()
            .replace("plant-based-foods-and-beverages", " ")
            .replace("plant-based foods and beverages", " ")
            .replace("foods-and-beverages", " ")
            .replace("foods and beverages", " ")
            .replace("food-and-beverages", " ")
            .replace("food and beverages", " ")
        fun has(vararg words: String) = words.any { Regex("(^|[^a-z])" + Regex.escape(it)).containsMatchIn(h) }
        fun hasWord(vararg words: String) = words.any { Regex("(^|[^a-z])" + Regex.escape(it) + "([^a-z]|$)").containsMatchIn(h) }
        return when {
            has("pasta", "rice", "rices", "cereal", "flour", "legume", "oat", "grain", "noodle") -> "Cestoviny, ryža a obilniny"
            has("beverage", "drink", "juice", "napoj", "waters", "mineral-water", "spring-water") || hasWord("water") -> "Nápoje"
            has("dairy", "milk", "cheese", "yogurt", "yoghurt", "butter", "cream", "egg") -> "Mliečne výrobky"
            has("meat", "sausage", "fish", "poultry", "seafood") || hasWord("ham") -> "Mäso a ryby"
            has("canned", "conserve", "preserved") -> "Konzervy"
            has("bread", "bakery", "biscuit", "pastr") -> "Pečivo"
            has("frozen") -> "Mrazené"
            has("fruit", "vegetable", "potato", "legumes") -> "Ovocie a zelenina"
            has("snack", "sweet", "chocolate", "candy", "confection", "cocoa", "honey", "jam") -> "Sladkosti a snacky"
            has("sauce", "spice", "condiment", "vinegar") || hasWord("oil", "oils") -> "Omáčky a korenie"
            else -> "Iné"
        }
    }
}
