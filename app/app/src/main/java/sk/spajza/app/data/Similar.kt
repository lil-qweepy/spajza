package sk.spajza.app.data

/** Hľadanie podobných produktov (napr. "múka hladká" ~ "Múka"), aby sa nevytvárali duplicity. */
object Similar {
    private val STOP = setOf("bez", "pre", "bio", "cca", "the")

    /** Kmene slov: bez diakritiky, skrátené na 5 znakov, aby sedeli aj sklonené tvary (cibula ~ cibulu). */
    private fun stems(s: String): Set<String> =
        Calc.norm(s).split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in STOP }
            .map { if (it.length > 5) it.take(5) else it }
            .toSet()

    /** Dva názvy sú podobné, ak všetky slová kratšieho sa nachádzajú v dlhšom. */
    fun similar(a: String, b: String): Boolean {
        val sa = stems(a)
        val sb = stems(b)
        if (sa.isEmpty() || sb.isEmpty()) return false
        val (small, big) = if (sa.size <= sb.size) sa to sb else sb to sa
        return big.containsAll(small)
    }

    /** Produkty z [items] podobné názvu [name]; presná zhoda je prvá. */
    fun find(items: List<Item>, name: String): List<Item> {
        val n = Calc.norm(name).trim()
        if (n.length < 2) return emptyList()
        return items.filter { similar(it.name, name) }
            .sortedBy { if (Calc.norm(it.name).trim() == n) 0 else 1 }
    }
}
