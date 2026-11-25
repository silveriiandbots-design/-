package com.example.mylauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LauncherApp() }
    }
}

data class Prefs(
    val iconSize: Int = 56,
    val columns: Int = 4,
    val labels: Boolean = true,
    val shape: Int = 0,   // 0 круг, 1 скруглённый квадрат, 2 квадрат
    val theme: Int = 0,   // 0 система, 1 светлая, 2 тёмная
    val scrim: Float = 0.35f,
)

data class AppItem(val label: String, val pkg: String, val cls: String, val icon: ImageBitmap)

fun loadPrefs(c: Context): Prefs {
    val sp = c.getSharedPreferences("p", 0); val d = Prefs()
    return Prefs(
        sp.getInt("iconSize", d.iconSize), sp.getInt("columns", d.columns),
        sp.getBoolean("labels", d.labels), sp.getInt("shape", d.shape),
        sp.getInt("theme", d.theme), sp.getFloat("scrim", d.scrim),
    )
}

fun savePrefs(c: Context, p: Prefs) {
    c.getSharedPreferences("p", 0).edit()
        .putInt("iconSize", p.iconSize).putInt("columns", p.columns)
        .putBoolean("labels", p.labels).putInt("shape", p.shape)
        .putInt("theme", p.theme).putFloat("scrim", p.scrim).apply()
}

fun loadApps(c: Context): List<AppItem> {
    val pm = c.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0).map {
        AppItem(
            it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name,
            it.loadIcon(pm).toBitmap(192, 192).asImageBitmap(),
        )
    }.sortedBy { it.label.lowercase() }
}

@Composable
fun LauncherApp() {
    val ctx = LocalContext.current
    var prefs by remember { mutableStateOf(loadPrefs(ctx)) }
    val dark = when (prefs.theme) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val scheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme) {
        Home(prefs) { prefs = it; savePrefs(ctx, it) }
    }
}

@Composable
fun Home(p: Prefs, set: (Prefs) -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.Default) { loadApps(ctx) } }
    var query by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(false) }
    val shown = apps.filter { it.label.contains(query, ignoreCase = true) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = p.scrim))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    placeholder = { Text("Поиск приложений") },
                    singleLine = true, shape = CircleShape,
                )
                IconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, "Настройки") }
            }
            LazyVerticalGrid(GridCells.Fixed(p.columns), Modifier.weight(1f)) {
                items(shown, key = { it.pkg + it.cls }) { AppCell(it, p) }
            }
        }
    }
    if (settings) SettingsSheet(p, set) { settings = false }
}

@Composable
fun AppCell(a: AppItem, p: Prefs) {
    val ctx = LocalContext.current
    val shape = when (p.shape) {
        0 -> CircleShape
        1 -> RoundedCornerShape(p.iconSize.dp * 0.28f)
        else -> RectangleShape
    }
    Column(
        Modifier.clip(RoundedCornerShape(16.dp))
            .clickable {
                ctx.startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(a.pkg, a.cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(a.icon, a.label, Modifier.size(p.iconSize.dp).clip(shape))
        if (p.labels) Text(
            a.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Text(title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyLarge)
    Slider(value, onChange, valueRange = range, steps = steps)
}

@Composable
fun ChipRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(title, Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.bodyLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(o) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(p: Prefs, set: (Prefs) -> Unit, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
            Text("Настройки", style = MaterialTheme.typography.titleLarge)
            SliderRow("Размер иконок: ${p.iconSize} dp", p.iconSize.toFloat(), 32f..96f) { set(p.copy(iconSize = it.roundToInt())) }
            SliderRow("Колонок: ${p.columns}", p.columns.toFloat(), 3f..7f, 3) { set(p.copy(columns = it.roundToInt())) }
            SliderRow("Затемнение фона: ${(p.scrim * 100).roundToInt()}%", p.scrim, 0f..0.9f) { set(p.copy(scrim = it)) }
            ChipRow("Форма иконок", listOf("Круг", "Скруглённая", "Квадрат"), p.shape) { set(p.copy(shape = it)) }
            ChipRow("Тема", listOf("Система", "Светлая", "Тёмная"), p.theme) { set(p.copy(theme = it)) }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Подписи под иконками", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(p.labels, { set(p.copy(labels = it)) })
            }
        }
    }
}
