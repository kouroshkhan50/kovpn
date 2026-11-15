@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.towerscout

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.telephony.CellInfo
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TowerScoutTheme {
                TowerScoutApp()
            }
        }
    }
}

// ---------- تم ----------

private val DarkColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    secondary = Color(0xFF22C55E),
    tertiary = Color(0xFFF59E0B),
    background = Color(0xFF0B1120),
    surface = Color(0xFF111A2E),
    surfaceVariant = Color(0xFF1B2740),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFF87171)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    secondary = Color(0xFF16A34A),
    tertiary = Color(0xFFD97706),
    background = Color(0xFFF1F5F9),
    surface = Color.White,
    surfaceVariant = Color(0xFFE2E8F0),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
private fun TowerScoutTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

// ---------- مدل داده ----------

private data class SignalSnapshot(
    val signalDbm: Int? = null,
    val networkType: String = "—",
    val operator: String = "—",
    val connection: String = "—",
    val cellInfo: String = "—",
    val readError: String? = null
)

private data class SavedPoint(
    val time: String,
    val dbm: Int?,
    val network: String
)

private data class SpeedState(
    val running: Boolean = false,
    val mbps: Double? = null,
    val message: String = "آزمونی انجام نشده"
)

private enum class Tab(val title: String, val icon: String) {
    Dashboard("وضعیت", "◉"),
    Optimize("بهینه‌سازی", "⚡"),
    Tools("ابزارها", "🛠"),
    Device("گوشی", "📱")
}

// ---------- صفحهٔ اصلی ----------

@Composable
private fun TowerScoutApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(Tab.Dashboard) }
    var snapshot by remember { mutableStateOf(SignalSnapshot()) }
    val history = remember { mutableStateListOf<Int>() }
    val points = remember { mutableStateListOf<SavedPoint>() }
    var speed by remember { mutableStateOf(SpeedState()) }
    var toast by remember { mutableStateOf<String?>(null) }

    val permissions = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        snapshot = readSnapshot(context)
    }

    LaunchedEffect(Unit) {
        val missing = permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing) permissionLauncher.launch(permissions)

        while (true) {
            snapshot = readSnapshot(context)
            snapshot.signalDbm?.let {
                history.add(it)
                if (history.size > 40) history.removeAt(0)
            }
            delay(2000)
        }
    }

    // پیام کوتاه: با تغییر toast، تایمر ریست می‌شود
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1500)
            toast = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("TowerScout", fontWeight = FontWeight.Bold)
                        Text(
                            tab.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.icon, fontSize = 18.sp) },
                        label = { Text(item.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.Dashboard -> DashboardScreen(snapshot, history)
                Tab.Optimize -> OptimizeScreen(
                    snapshot = snapshot,
                    points = points,
                    onSavePoint = {
                        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        points.add(0, SavedPoint(time, snapshot.signalDbm, snapshot.networkType))
                        toast = "نقطه ثبت شد"
                    },
                    onClear = { points.clear() }
                )
                Tab.Tools -> ToolsScreen(
                    speed = speed,
                    onStartSpeed = {
                        scope.launch {
                            speed = SpeedState(running = true, message = "در حال آزمون…")
                            speed = runSpeedTest()
                        }
                    },
                    onOpenSettings = { action -> openSettings(context, action) }
                )
                Tab.Device -> DeviceScreen(context)
            }

            toast?.let {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        color = Color.White
                    )
                }
            }
        }
    }
}

// ---------- صفحهٔ وضعیت ----------

@Composable
private fun DashboardScreen(snapshot: SignalSnapshot, history: List<Int>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SignalGauge(snapshot.signalDbm)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard("شبکه", snapshot.networkType, Modifier.weight(1f))
            InfoCard("اتصال", snapshot.connection, Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoCard("اپراتور", snapshot.operator, Modifier.weight(1f))
            InfoCard(
                "کیفیت",
                snapshot.signalDbm?.let { qualityLabel(it) } ?: "نامشخص",
                Modifier.weight(1f)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("نمودار زندهٔ سیگنال", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                SignalChart(history, Modifier.fillMaxWidth().height(140.dp))
            }
        }

        snapshot.readError?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(it, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

@Composable
private fun SignalGauge(dbm: Int?) {
    val target = dbm?.let { ((it + 120) / 70f).coerceIn(0f, 1f) } ?: 0f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "gauge"
    )
    val color = dbm?.let { qualityColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(200.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 22f
                    drawArc(
                        color = Color.Gray.copy(alpha = 0.2f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(color.copy(alpha = 0.4f), color)),
                        startAngle = 135f,
                        sweepAngle = 270f * progress,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = dbm?.toString() ?: "--",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Text("dBm", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                dbm?.let { qualityLabel(it) } ?: "در انتظار داده",
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@Composable
private fun SignalChart(values: List<Int>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val w = size.width
        val h = size.height
        val minV = -120f
        val maxV = -50f
        val stepX = w / (values.size - 1)
        var prev: Offset? = null
        values.forEachIndexed { i, v ->
            val y = h - ((v - minV) / (maxV - minV)).coerceIn(0f, 1f) * h
            val p = Offset(i * stepX, y)
            prev?.let { drawLine(line, it, p, strokeWidth = 5f, cap = StrokeCap.Round) }
            prev = p
        }
    }
}

@Composable
private fun InfoCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}

// ---------- بهینه‌سازی ----------

@Composable
private fun OptimizeScreen(
    snapshot: SignalSnapshot,
    points: List<SavedPoint>,
    onSavePoint: () -> Unit,
    onClear: () -> Unit
) {
    val advice = adviceFor(snapshot.signalDbm)
    val best = points.maxByOrNull { it.dbm ?: Int.MIN_VALUE }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("پیشنهاد هوشمند", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(advice, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onSavePoint, modifier = Modifier.weight(1f)) {
                    Text("ثبت این نقطه")
                }
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text("پاک‌کردن")
                }
            }
        }

        item {
            Text("مقایسهٔ نقاط", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "در چند نقطه ثبت کنید و بهترین را برای دانلود انتخاب کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (points.isEmpty()) {
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("هنوز نقطه‌ای ثبت نشده است.", modifier = Modifier.padding(16.dp))
                }
            }
        }

        items(points) { p ->
            val isBest = p == best && p.dbm != null
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBest) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.time, fontWeight = FontWeight.SemiBold)
                        Text(p.network, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        p.dbm?.let { "$it dBm" } ?: "—",
                        fontWeight = FontWeight.Bold,
                        color = p.dbm?.let { qualityColor(it) }
                            ?: MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isBest) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "★ بهترین",
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Text("نکته‌های کلیدی", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))
            listOf(
                "نزدیک پنجره یا فضای باز بایستید.",
                "موانع فلزی، آسانسور و زیرزمین سیگنال را ضعیف می‌کنند.",
                "حالت هواپیما را ۱۰ ثانیه روشن و خاموش کنید تا اتصال تازه شود.",
                "برای دانلود بزرگ، Wi‑Fi پایدار را ترجیح دهید.",
                "برنامه‌های پس‌زمینه را ببندید تا پهنای باند آزاد شود."
            ).forEach {
                Text("• $it", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

private fun adviceFor(dbm: Int?): String = when {
    dbm == null -> "برای دریافت پیشنهاد، مجوز موقعیت را بدهید تا قدرت سیگنال خوانده شود."
    dbm >= -80 -> "سیگنال عالی است؛ همین‌جا برای دانلود مناسب است."
    dbm >= -90 -> "سیگنال خوب است؛ برای دانلود بزرگ کمی جابه‌جا شوید و نقطه‌های بهتر را مقایسه کنید."
    dbm >= -100 -> "سیگنال متوسط است؛ نزدیک پنجره بروید یا Wi‑Fi را امتحان کنید."
    dbm >= -110 -> "سیگنال ضعیف است؛ به فضای باز یا طبقه بالاتر بروید."
    else -> "سیگنال بسیار ضعیف است؛ دانلود بزرگ را به زمان یا مکان بهتر موکول کنید."
}

// ---------- ابزارها ----------

@Composable
private fun ToolsScreen(
    speed: SpeedState,
    onStartSpeed: () -> Unit,
    onOpenSettings: (String) -> Unit
) {
    val shortcuts = listOf(
        "اینترنت و شبکه" to Settings.ACTION_WIRELESS_SETTINGS,
        "شبکهٔ موبایل" to Settings.ACTION_DATA_ROAMING_SETTINGS,
        "Wi‑Fi" to Settings.ACTION_WIFI_SETTINGS,
        "محدودیت باتری" to Settings.ACTION_BATTERY_SAVER_SETTINGS
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("آزمون سرعت دانلود", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        speed.mbps?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "--",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Mbps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(speed.message, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onStartSpeed, enabled = !speed.running) {
                        if (speed.running) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (speed.running) "در حال آزمون" else "شروع آزمون")
                    }
                }
            }
        }

        item {
            Text("میانبرهای تنظیمات", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }

        items(shortcuts) { (label, action) ->
            OutlinedButton(
                onClick = { onOpenSettings(action) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(label)
            }
        }
    }
}

// ---------- گوشی ----------

@Composable
private fun DeviceScreen(context: Context) {
    val rows = listOf(
        "سازنده" to Build.MANUFACTURER,
        "مدل" to Build.MODEL,
        "دستگاه" to Build.DEVICE,
        "اندروید" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "اتصال فعلی" to getConnectionType(context)
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rows) { (k, v) ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp)) {
                    Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(v, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ---------- منطق سیستمی ----------

@Suppress("MissingPermission")
private fun readSnapshot(context: Context): SignalSnapshot {
    val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val operator = runCatching { telephony.networkOperatorName }
        .getOrNull().orEmpty().ifBlank { "نامشخص" }

    val networkType = runCatching {
        when (telephony.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSDPA -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "نامشخص"
            else -> "دیگر"
        }
    }.getOrDefault("در دسترس نیست")

    var dbm: Int? = null
    var cellText = "—"
    var error: String? = null

    try {
        val hasLocation =
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!hasLocation) {
            error = "برای خواندن قدرت سیگنال سلولی، مجوز موقعیت را بدهید."
        } else {
            val cells: List<CellInfo> = telephony.allCellInfo.orEmpty()
            val registered = cells.filter { it.isRegistered }
            val pool = registered.ifEmpty { cells }
            dbm = pool
                .mapNotNull { runCatching { it.cellSignalStrength.dbm }.getOrNull() }
                .filter { it < 0 }
                .maxOrNull()
            cellText = pool.firstOrNull()?.javaClass?.simpleName?.removePrefix("CellInfo") ?: "—"
        }
    } catch (_: SecurityException) {
        error = "دسترسی توسط سیستم محدود شده است."
    } catch (e: Exception) {
        error = e.localizedMessage ?: "خواندن اطلاعات سلول ممکن نشد."
    }

    return SignalSnapshot(
        signalDbm = dbm,
        networkType = networkType,
        operator = operator,
        connection = getConnectionType(context),
        cellInfo = cellText,
        readError = error
    )
}

private fun hasPermission(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun qualityLabel(dbm: Int): String = when {
    dbm >= -80 -> "عالی"
    dbm >= -90 -> "خوب"
    dbm >= -100 -> "متوسط"
    dbm >= -110 -> "ضعیف"
    else -> "بسیار ضعیف"
}

private fun qualityColor(dbm: Int): Color = when {
    dbm >= -80 -> Color(0xFF22C55E)
    dbm >= -90 -> Color(0xFF84CC16)
    dbm >= -100 -> Color(0xFFF59E0B)
    dbm >= -110 -> Color(0xFFF97316)
    else -> Color(0xFFEF4444)
}

private fun getConnectionType(context: Context): String {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return "بدون اتصال"
    val caps = cm.getNetworkCapabilities(network) ?: return "نامشخص"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "موبایل"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "دیگر"
    }
}

private fun openSettings(context: Context, action: String) {
    runCatching {
        context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        context.startActivity(
            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private suspend fun runSpeedTest(): SpeedState = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        val target = 5_000_000L
        connection = (URL("https://speed.cloudflare.com/__down?bytes=$target")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            setRequestProperty("Cache-Control", "no-cache")
        }
        val start = SystemClock.elapsedRealtime()
        var total = 0L
        val buffer = ByteArray(32 * 1024)
        connection.inputStream.use { input ->
            while (total < target) {
                val n = input.read(buffer)
                if (n <= 0) break
                total += n
            }
        }
        val seconds = ((SystemClock.elapsedRealtime() - start).coerceAtLeast(1)) / 1000.0
        SpeedState(
            running = false,
            mbps = total * 8.0 / seconds / 1_000_000.0,
            message = "آزمون کامل شد (${total / 1_000_000} MB)"
        )
    } catch (e: Exception) {
        SpeedState(
            running = false,
            message = "آزمون انجام نشد: ${e.localizedMessage ?: "اتصال را بررسی کنید"}"
        )
    } finally {
        connection?.disconnect()
    }
}
