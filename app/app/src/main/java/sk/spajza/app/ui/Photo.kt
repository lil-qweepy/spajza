package sk.spajza.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Odfoti produkt fotoaparatom. Vrati funkciu, ktora fotoaparat spusti (vypyta si aj povolenie).
 * Vysledok je nazov suboru v ulozisku obrazkov, alebo null, ak sa fotka nepodarila / bola zrusena.
 */
@Composable
fun rememberProductCamera(onResult: (String?) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val store = LocalStore.current
    val latest by rememberUpdatedState(onResult)
    var target by remember { mutableStateOf<File?>(null) }

    val shoot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = target
        target = null
        if (ok && f != null && f.length() > 0) {
            store.shrinkImage(f)
            latest(f.name)
        } else {
            f?.delete()
            latest(null)
        }
    }

    fun launch() {
        val f = store.newImageFile()
        try {
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
            target = f
            shoot.launch(uri)
        } catch (e: Exception) {
            latest(null)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launch() else latest(null)
    }

    return {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launch()
        else permission.launch(Manifest.permission.CAMERA)
    }
}
