package sk.spajza.app.data

/** Vypocet nudzovych zasob: na kolko dni vystaci jedlo a voda pre osoby v domacnosti. */
data class EmergencyReport(
    val persons: Int,
    val kcalTotal: Double,
    val waterMl: Double,
    val foodDays: Double,
    val waterDays: Double,
    val targetDays: Int,
    val missingKcal: Double,
    val missingWaterMl: Double,
    /** Produkty s nezname kalorie (nezapocitane). */
    val unknownKcal: List<StockItem>,
    /** Produkty oznacene ako voda, ale bez objemu (nezapocitane). */
    val unknownWater: List<StockItem>,
    val counted: List<StockItem>
) {
    val days: Double get() = minOf(foodDays, waterDays)
    val ok: Boolean get() = foodDays >= targetDays && waterDays >= targetDays
}

object Emergency {
    fun report(db: Db): EmergencyReport {
        val s = db.settings
        val persons = s.persons.coerceAtLeast(1)
        val stock = db.stock().filter { it.qty > 0 && it.item.emergency }
        var kcal = 0.0
        var water = 0.0
        val unknownK = ArrayList<StockItem>()
        val unknownW = ArrayList<StockItem>()
        for (st in stock) {
            if (st.item.water) {
                if (st.waterMl > 0) water += st.waterMl else unknownW.add(st)
            }
            if (st.kcal > 0) kcal += st.kcal
            else if (!st.item.water && st.item.kcal == null) unknownK.add(st)
        }
        val needKcalDay = persons * s.kcalPerDay
        val needWaterDay = persons * s.waterPerDayL * 1000.0
        val foodDays = if (needKcalDay > 0) kcal / needKcalDay else 0.0
        val waterDays = if (needWaterDay > 0) water / needWaterDay else 0.0
        val missK = (needKcalDay * s.targetDays - kcal).coerceAtLeast(0.0)
        val missW = (needWaterDay * s.targetDays - water).coerceAtLeast(0.0)
        return EmergencyReport(persons, kcal, water, foodDays, waterDays, s.targetDays, missK, missW, unknownK, unknownW, stock)
    }
}
