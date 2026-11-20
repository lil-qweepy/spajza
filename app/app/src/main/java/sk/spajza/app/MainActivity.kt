package sk.spajza.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import sk.spajza.app.notify.Notify
import sk.spajza.app.ui.LocalNav
import sk.spajza.app.ui.LocalStore
import sk.spajza.app.ui.NavVm
import sk.spajza.app.ui.Root
import sk.spajza.app.ui.SpajzaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val crash = CrashHandler.read(this)
        setContent {
            SpajzaTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var crashText by remember { mutableStateOf(crash) }
                    val text = crashText
                    if (text != null) {
                        CrashScreen(text) {
                            CrashHandler.clear(this@MainActivity)
                            crashText = null
                        }
                    } else {
                        Content()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            sk.spajza.app.notify.ExpiryWidget.refresh(this)
        } catch (e: Exception) {
            // nic
        }
    }

    @Composable
    private fun Content() {
        val store = (application as App).store
        val navVm: NavVm = viewModel()
        val db by store.db.collectAsState()
        val s = db.settings

        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            try {
                if (s.notificationsEnabled) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: Exception) {
                // povolenie sa da zapnut aj v nastaveniach
            }
        }
        LaunchedEffect(s.notificationsEnabled, s.notifyHour) {
            try {
                if (s.notificationsEnabled) Notify.schedule(this@MainActivity, s.notifyHour)
                else Notify.cancel(this@MainActivity)
            } catch (e: Exception) {
                // plan sa skusi znova pri dalsom spusteni
            }
        }
        LaunchedEffect(Unit) {
            try {
                val today = sk.spajza.app.data.Calc.today()
                if (store.settings.autoFetch && store.settings.lastAutoFill != today) {
                    store.autoFillAll()
                    store.updateSettings { it.copy(lastAutoFill = today) }
                }
            } catch (e: Exception) {
                // bez internetu sa to skusi nabudúce
            }
        }
        CompositionLocalProvider(LocalStore provides store, LocalNav provides navVm.nav) {
            Root()
        }
    }
}

@Composable
private fun CrashScreen(text: String, onContinue: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(32.dp))
        Text("Aplikácia sa minule neočakávane zatvorila", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Nižšie je technický popis chyby. Skopírujte ho a pošlite ho vývojárovi, opravu tak nájde okamžite.")
        Spacer(Modifier.height(12.dp))
        Button(onClick = {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("chyba", text))
        }) { Text("Kopírovať chybu") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onContinue) { Text("Pokračovať do aplikácie") }
        Spacer(Modifier.height(16.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
