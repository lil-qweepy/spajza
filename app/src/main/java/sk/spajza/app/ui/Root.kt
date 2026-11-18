package sk.spajza.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private fun iconFor(t: Tab): ImageVector = when (t) {
    Tab.Home -> Icons.Filled.Home
    Tab.Inventory -> Icons.Filled.Inventory2
    Tab.Recipes -> Icons.Filled.Restaurant
    Tab.Shop -> Icons.Filled.ShoppingCart
    Tab.More -> Icons.Filled.MoreHoriz
}

@Composable
fun Root() {
    val nav = LocalNav.current
    val top = nav.current
    val store = LocalStore.current
    val undo by store.undoInfo.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(undo?.id) {
        val u = undo
        if (u != null) {
            val res = snackbar.showSnackbar(u.label, actionLabel = "Späť", duration = SnackbarDuration.Long)
            if (res == SnackbarResult.ActionPerformed) store.undo() else store.clearUndo(u.id)
        }
    }

    BackHandler(enabled = top != null || nav.tab != Tab.Home) {
        if (top != null) nav.pop() else nav.tab = Tab.Home
    }

    Column(Modifier.fillMaxSize()) {
        val bodyModifier = if (top == null) {
            Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)
        } else {
            Modifier.weight(1f)
        }
        Box(bodyModifier) {
            when (top) {
                null -> when (nav.tab) {
                    Tab.Home -> HomeScreen()
                    Tab.Inventory -> InventoryScreen()
                    Tab.Recipes -> RecipesScreen()
                    Tab.Shop -> ShopScreen()
                    Tab.More -> MoreScreen()
                }
                is Dest.ItemDetail -> ItemDetailScreen(top.id)
                is Dest.ItemEdit -> ItemEditScreen(top)
                is Dest.Scan -> ScanScreen()
                is Dest.QuickAdd -> QuickAddScreen()
                is Dest.Cook -> CookScreen()
                is Dest.Expiry -> ExpiryScreen()
                is Dest.Emergency -> EmergencyScreen()
                is Dest.Tips -> TipsScreen()
                is Dest.Lists -> ListsScreen()
                is Dest.Stats -> StatsScreen()
                is Dest.Receipt -> ReceiptScreen()
                is Dest.Moves -> MovesScreen()
                is Dest.BackupScreen -> BackupScreen()
                is Dest.SettingsScreen -> SettingsScreen()
                is Dest.RecipeDetail -> RecipeDetailScreen(top.id)
                is Dest.RecipeEdit -> RecipeEditScreen(top.id)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        }
        if (top == null) {
            NavigationBar {
                for (t in Tab.entries) {
                    NavigationBarItem(
                        selected = nav.tab == t,
                        onClick = { nav.tab = t },
                        icon = { Icon(iconFor(t), contentDescription = t.title) },
                        label = { Text(t.title) }
                    )
                }
            }
        }
    }
}
