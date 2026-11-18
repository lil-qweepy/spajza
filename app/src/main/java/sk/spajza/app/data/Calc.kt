package sk.spajza.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

object Calc {

    // ---------- prepocty mnozstva ----------

    fun grams(i: Item, q: Double): Double? = when {
        i.uom == Uom.KG -> q * 1000
        i.uom == Uom.G -> q
        i.uom.isCountable && i.net > 0 && !i.netLiquid -> q * i.net
        else -> null
    }

    fun ml(i: Item, q: Double): Double? = when {
        i.uom == Uom.L -> q * 1000
        i.uom == Uom.ML -> q
        i.uom.isCountable && i.net > 0 && i.netLiquid -> q * i.net
        else -> null
    }

    fun pieces(i: Item, q: Double): Double? {
        if (i.uom.isCountable) return q
        if (i.net > 0 && i.uom.isMass && !i.netLiquid) return (grams(i, q) ?: return null) / i.net
        if (i.net > 0 && i.uom.isVolume && i.netLiquid) return (ml(i, q) ?: return null) / i.net
        return null
    }

    /** Hodnota mnozstva q (v jednotke produktu) v EUR. */
    fun value(i: Item, q: Double): Double {
        if (i.price <= 0) return 0.0
        return when (i.basis) {
            PriceBasis.PER_PIECE, PriceBasis.PER_PACK -> (pieces(i, q) ?: 0.0) * i.price
            PriceBasis.PER_KG -> (grams(i, q) ?: 0.0) / 1000.0 * i.price
            PriceBasis.PER_L -> (ml(i, q) ?: 0.0) / 1000.0 * i.price
        }
    }

    fun kcal(i: Item, q: Double): Double {
        val k = i.kcal ?: return 0.0
        val amount = grams(i, q) ?: ml(i, q) ?: return 0.0
        return amount / 100.0 * k
    }

    fun waterMl(i: Item, q: Double): Double {
        if (!i.water) return 0.0
        return ml(i, q) ?: grams(i, q) ?: 0.0
    }

    fun unitPrice(i: Item): Double = value(i, 1.0)

    /** Cena pre danu jednotku (nakupny zoznam) prepocitana na zakladnu ceny produktu. */
    fun basisFor(uom: Uom, pricePerUnit: Double): Pair<PriceBasis, Double> = when (uom) {
        Uom.PCS -> PriceBasis.PER_PIECE to pricePerUnit
        Uom.PACK -> PriceBasis.PER_PACK to pricePerUnit
        Uom.KG -> PriceBasis.PER_KG to pricePerUnit
        Uom.G -> PriceBasis.PER_KG to pricePerUnit * 1000
        Uom.L -> PriceBasis.PER_L to pricePerUnit
        Uom.ML -> PriceBasis.PER_L to pricePerUnit * 1000
    }

    // ---------- datum ----------

    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val dateTimeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    fun today(): Long = LocalDate.now().toEpochDay()

    fun date(epochDay: Long?): String =
        if (epochDay == null) "—" else LocalDate.ofEpochDay(epochDay).format(dateFmt)

    fun dateTime(millis: Long): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        return dt.format(dateTimeFmt)
    }

    fun dayOf(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    fun daysUntil(epochDay: Long?): Long? = if (epochDay == null) null else epochDay - today()

    fun expiryText(epochDay: Long?): String {
        val d = daysUntil(epochDay) ?: return "bez dátumu"
        return when {
            d < -1L -> "po expirácii ${-d} dní"
            d == -1L -> "expirovalo včera"
            d == 0L -> "expiruje dnes"
            d == 1L -> "expiruje zajtra"
            d <= 4L -> "o $d dni"
            else -> "o $d dní"
        }
    }

    // ---------- formatovanie ----------

    private val sk: Locale = Locale.Builder().setLanguage("sk").setRegion("SK").build()

    fun money(v: Double): String = String.format(sk, "%,.2f €", v).replace(' ', ' ')

    fun num(v: Double, maxDecimals: Int = 2): String {
        if (abs(v - v.roundToLong()) < 1e-9) {
            return String.format(sk, "%,d", v.roundToLong()).replace(' ', ' ')
        }
        var s = String.format(sk, "%,." + maxDecimals + "f", v).replace(' ', ' ')
        if (s.contains(',')) s = s.trimEnd('0').trimEnd(',')
        return s
    }

    fun qty(v: Double, uom: Uom): String = num(v, 3) + " " + uom.label

    fun kcalText(v: Double): String = num(Math.round(v).toDouble()) + " kcal"

    fun waterText(ml: Double): String =
        if (ml >= 1000) num(ml / 1000.0, 1) + " l" else num(Math.round(ml).toDouble()) + " ml"

    fun parse(s: String): Double? = s.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()

    fun edit(v: Double?): String {
        if (v == null) return ""
        if (abs(v - v.roundToLong()) < 1e-9) return v.roundToLong().toString()
        return v.toString().trimEnd('0').trimEnd('.').replace('.', ',')
    }

    /** Odstrani diakritiku a zmeni na male pismena (na porovnavanie textov). */
    fun norm(s: String): String {
        val n = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        return n.replace(Regex("\\p{M}+"), "")
    }
}
