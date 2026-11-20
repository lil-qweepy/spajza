package sk.spajza.app.data

/** Kontrola ciarovych kodov podla kontrolnej cislice (EAN-8, UPC-A, EAN-13). */
object Ean {
    fun valid(code: String): Boolean {
        if (code.length != 8 && code.length != 12 && code.length != 13) return false
        if (!code.all { it in '0'..'9' }) return false
        val digits = code.map { it - '0' }
        var sum = 0
        // zprava od cislice pred kontrolnou: vahy 3, 1, 3, 1 ...
        for ((i, d) in digits.dropLast(1).reversed().withIndex()) sum += d * (if (i % 2 == 0) 3 else 1)
        return (10 - sum % 10) % 10 == digits.last()
    }
}
