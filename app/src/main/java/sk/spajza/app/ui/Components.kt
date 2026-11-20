package sk.spajza.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import sk.spajza.app.data.Calc
import sk.spajza.app.data.Item

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floating: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Späť")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = floating,
        content = content
    )
}

/** Zoznam s obmedzenou sirkou (pekne vyzera aj na tablete). */
@Composable
fun PageList(padding: PaddingValues, content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** Posuvny formular s obmedzenou sirkou. */
@Composable
fun FormPage(padding: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = container)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = colors, border = border) {
            Column(Modifier.padding(14.dp)) { content() }
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = colors, border = border) {
            Column(Modifier.padding(14.dp)) { content() }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.size(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StatTile(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        Muted(label)
    }
}

@Composable
fun EmptyState(text: String, hint: String = "") {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hint.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Muted(hint)
        }
    }
}

// ---------- expiracia ----------

fun expiryColor(days: Long?): Color = when {
    days == null -> Pal.Muted
    days < 0 -> Pal.Red
    days <= 3 -> Pal.Orange
    days <= 7 -> Pal.Amber
    days <= 30 -> Pal.Blue
    else -> Pal.Green
}

@Composable
fun ExpiryBadge(expiry: Long?) {
    val days = Calc.daysUntil(expiry)
    val c = expiryColor(days)
    Text(
        text = if (expiry == null) "bez dátumu" else Calc.expiryText(expiry),
        color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

// ---------- obrazok produktu ----------

@Composable
fun ItemThumb(item: Item, size: Dp = 48.dp) {
    val store = LocalStore.current
    val file = remember(item.image) { store.imageFile(item.image) }
    val shape = RoundedCornerShape(12.dp)
    if (file != null) {
        AsyncImage(
            model = file, contentDescription = item.name, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(shape)
        )
    } else {
        Box(
            Modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                item.name.trim().take(1).uppercase(), fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = (size.value / 2.2f).sp
            )
        }
    }
}

// ---------- vstupy ----------

@Composable
fun NumField(
    label: String, value: String, onValue: (String) -> Unit,
    modifier: Modifier = Modifier, suffix: String? = null, supporting: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onValue(s.filter { it.isDigit() || it == ',' || it == '.' }) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        suffix = if (suffix != null) ({ Text(suffix) }) else null,
        supportingText = if (supporting != null) ({ Text(supporting) }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

@Composable
fun TextField2(
    label: String, value: String, onValue: (String) -> Unit,
    modifier: Modifier = Modifier, singleLine: Boolean = true, keyboard: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value, onValueChange = onValue, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = singleLine, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard)
    )
}

@Composable
fun SearchField(value: String, onValue: (String) -> Unit, modifier: Modifier = Modifier, hint: String = "Hľadať") {
    OutlinedTextField(
        value = value, onValueChange = onValue, singleLine = true, modifier = modifier,
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValue("") }) { Icon(Icons.Filled.Clear, contentDescription = "Vymazať") }
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

/** Vyber z moznosti (rozbalovaci zoznam). */
@Composable
fun Picker(
    label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = options.getOrElse(selected) { "" }, onValueChange = {}, readOnly = true,
            label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
        )
        Box(Modifier.matchParentSize().clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { idx, name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onSelect(idx) })
            }
        }
    }
}

/** Pole na vyber datumu; hodnota je den od epochy. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: Long?, onValue: (Long?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = if (value == null) "" else Calc.date(value), onValueChange = {}, readOnly = true,
            label = { Text(label) }, placeholder = { Text("DD.MM.RRRR") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (value != null) {
                    IconButton(onClick = { onValue(null) }) { Icon(Icons.Filled.Clear, contentDescription = "Zmazať dátum") }
                } else {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                }
            }
        )
        Box(Modifier.matchParentSize().padding(end = 52.dp).clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (value ?: Calc.today()) * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    val ms = state.selectedDateMillis
                    if (ms != null) onValue(Math.floorDiv(ms, 86_400_000L))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Zrušiť") } }
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String, text: String, confirm: String = "Potvrdiť",
    onConfirm: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušiť") } }
    )
}

@Composable
fun Dot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Zbalitelna sekcia formulara: nadpis, kratke zhrnutie a obsah, ktory sa rozbali klepnutim. */
@Composable
fun ExpandSection(
    title: String, summary: String = "", initiallyOpen: Boolean = false,
    content: @Composable () -> Unit
) {
    var open by remember { mutableStateOf(initiallyOpen) }
    SCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!open && summary.isNotBlank()) Muted(summary)
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}
