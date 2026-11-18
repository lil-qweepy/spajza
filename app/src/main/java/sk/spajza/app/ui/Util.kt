package sk.spajza.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import sk.spajza.app.data.Db

@Composable
fun useDb(): Db {
    val store = LocalStore.current
    val d by store.db.collectAsState()
    return d
}

fun daysText(n: Double, one: String, few: String, many: String): String {
    val i = Math.round(n).toInt()
    return when {
        i == 1 -> one
        i in 2..4 -> few
        else -> many
    }
}
