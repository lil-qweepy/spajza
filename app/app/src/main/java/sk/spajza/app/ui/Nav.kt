package sk.spajza.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.lifecycle.ViewModel
import sk.spajza.app.data.Store

enum class Tab(val title: String) {
    Home("Domov"), Inventory("Zásoby"), Recipes("Recepty"), Shop("Nákup"), More("Viac")
}

sealed class Dest {
    data class ItemDetail(val id: Long) : Dest()
    /** Uprava existujuceho produktu. id = 0 (stary formular noveho produktu) presmeruje na QuickAdd. */
    data class ItemEdit(val id: Long, val ean: String = "", val name: String = "", val kind: String = "") : Dest()
    data object Scan : Dest()
    /** Jediny formular na pridanie potraviny; ean, name a kind (skener, recepty) ho predvyplnia. */
    data class QuickAdd(val ean: String = "", val name: String = "", val kind: String = "") : Dest()
    data object Cook : Dest()
    data object Expiry : Dest()
    data object Emergency : Dest()
    data object Lists : Dest()
    data object Stats : Dest()
    data object Moves : Dest()
    data object BackupScreen : Dest()
    data object SettingsScreen : Dest()
    data class RecipeDetail(val id: Long) : Dest()
    data class RecipeEdit(val id: Long) : Dest()
}

/** Polozka rozpracovaneho hromadneho nakupu (prezije otvorenie formulara produktu). */
data class Pending(val itemId: Long, val name: String, val qty: Double, val expiry: Long?)

class Navigator {
    var tab by mutableStateOf(Tab.Home)

    /** Hromadne naskenovane produkty, ktore este nie su v zasobach. */
    val bulkPending = mutableStateListOf<Pending>()
    var bulkReview by mutableStateOf(false)
    val stack = mutableStateListOf<Dest>()

    /** Miesto, ktore ma Zasoby po otvoreni predfiltrovat (-1 = ziadne). */
    var pendingPlace by mutableStateOf(-1L)

    val current: Dest? get() = stack.lastOrNull()

    fun push(d: Dest) {
        stack.add(d)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
    }

    /** Nahradi aktualnu obrazovku (napr. po ulozeni formulara). */
    fun replace(d: Dest) {
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
        stack.add(d)
    }

    fun goTab(t: Tab) {
        stack.clear()
        tab = t
    }
}

class NavVm : ViewModel() {
    val nav = Navigator()
}

val LocalStore = compositionLocalOf<Store> { error("Store nie je k dispozicii") }
val LocalNav = compositionLocalOf<Navigator> { error("Navigator nie je k dispozicii") }
