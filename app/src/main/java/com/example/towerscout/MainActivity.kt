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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TowerScoutScreen()
                }
            }
        }
    }
}

private data class SignalSnapshot(
    val signalDbm: Int?,
    val networkType: String,
    val operator: String,
    val connection: String,
    val cellInfo: String,
    val readError: String? = null
)

private data class SavedPoint(
    val time: String,
    val signal: String,
    val network: String
)

@Composable
private fun TowerScoutScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var snapshot by remember {
        mutableStateOf(
            SignalSnapshot(null, "در حال خواندن…", "—", "—", "—")
        )
    }
    var savedPoints by remember { mutableStateOf(listOf<SavedPoint>()) }
    var speedText by remember { mutableStateOf("هنوز آزمونی انجام نشده") }
    var speedRunning by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("برای اطلاعات سلولی، مجوزهای لازم را بدهید.") }

    val neededPermissions = remember {
        mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        ).apply {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        statusText = if (
            results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            "مجوز موقعیت داده شد. اطلاعات شبکه در دسترس را می‌خوانم."
        } else {
            "بدون مجوز موقعیت، اطلاعات بعضی سلول‌ها نمایش داده نمی‌شود."
        }
        snapshot = readSnapshot(context)
    }

    // تازه‌سازی دوره‌ای؛ محدودیت‌های دستگاه/اپراتور ممکن است داده را با تأخیر بدهند.
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = readSnapshot(context)
            delay(2500)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("TowerScout", style = MaterialTheme.typography.headlineMedium)
            Text("پایش و بهینه‌سازی امن اتصال")
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("وضعیت زنده", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("قدرت سیگنال: ${snapshot.signalDbm?.let { "$it dBm" } ?: "در دسترس نیست"}")
                    Text("کیفیت تقریبی: ${snapshot.signalDbm?.let(::signalLabel) ?: "نامشخص"}")
                    Text("شبکهٔ موبایل: ${snapshot.networkType}")
                    Text("اپراتور: ${snapshot.operator}")
                    Text("اتصال اینترنت: ${snapshot.connection}")
                    Text("اطلاعات سلول: ${snapshot.cellInfo}")
                    snapshot.readError?.let { Text("یادداشت: $it") }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    permissionLauncher.launch(neededPermissions)
                }) {
                    Text("مجوزها / تازه‌سازی")
                }
                OutlinedButton(onClick = {
                    snapshot = readSnapshot(context)
                    statusText = "اطلاعات به‌روزرسانی شد."
                }) {
                    Text("اکنون بررسی کن")
                }
            }
            Text(statusText, style = MaterialTheme.typography.bodySmall)
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("بهینه‌سازی اتصال", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("• برای مقایسهٔ نقاط، در هر محل چند ثانیه صبر کنید و سیگنال را ثبت کنید.")
                    Text("• نزدیک پنجره یا فضای باز امتحان کنید؛ موانع فلزی و دیوار ضخیم سیگنال را کم می‌کنند.")
                    Text("• حالت هواپیما را حدود ۱۰ ثانیه روشن و سپس خاموش کنید تا اتصال دوباره برقرار شود.")
                    Text("• برای دانلود، Wi‑Fi پایدار یا شبکهٔ موبایلی با سیگنال بهتر را انتخاب کنید.")
                    Text("• VPN را فقط اگر لازم نیست خاموش کنید؛ برنامه نمی‌تواند تنظیمات مودم یا دکل را اجباراً عوض کند.")
                }
            }
        }

        item {
            Text("میانبرهای تنظیمات", style = MaterialTheme.typography.titleLarge)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openSettings(context, Settings.ACTION_WIRELESS_SETTINGS) }
                ) { Text("تنظیمات اینترنت و شبکه") }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openSettings(context, Settings.ACTION_DATA_ROAMING_SETTINGS) }
                ) { Text("تنظیمات شبکهٔ موبایل") }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openSettings(context, Settings.ACTION_WIFI_SETTINGS) }
                ) { Text("تنظیمات Wi‑Fi") }

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openSettings(context, Settings.ACTION_BATTERY_SAVER_SETTINGS) }
                ) { Text("تنظیمات باتری؛ بررسی محدودیت پس‌زمینه") }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("اطلاعات گوشی", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("سازنده: ${Build.MANUFACTURER}")
                    Text("مدل: ${Build.MODEL}")
                    Text("نسخهٔ اندروید: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    Text("دستگاه: ${Build.DEVICE}")
                    Text("شناسهٔ محصول: ${Build.PRODUCT}")
                    Text("نوع ارتباط اینترنت: ${getConnectionType(context)}")
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("آزمون دانلود", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("آزمون کوتاه و تقریبی است و از اینترنت موبایل یا Wi‑Fi داده مصرف می‌کند.")
                    Spacer(Modifier.height(8.dp))
                    Button(
                        enabled = !speedRunning,
                        onClick = {
                            scope.launch {
                                speedRunning = true
                                speedText = "در حال آزمون…"
                                speedText = runDownloadTest()
                                speedRunning = false
                            }
                        }
                    ) {
                        Text(if (speedRunning) "در حال آزمون…" else "شروع آزمون")
                    }
                    Text(speedText)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val signal = snapshot.signalDbm?.let { "$it dBm" } ?: "نامشخص"
                    val time = java.text.SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.getDefault()
                    ).format(java.util.Date())
                    savedPoints = listOf(
                        SavedPoint(time, signal, snapshot.networkType)
                    ) + savedPoints
                    statusText = "نقطهٔ فعلی ثبت شد؛ برای مقایسه در محل‌های دیگر تکرار کنید."
                }) {
                    Text("ثبت این نقطه")
                }

                OutlinedButton(onClick = { savedPoints = emptyList() }) {
                    Text("پاک‌کردن نقاط")
                }
            }
        }

        if (savedPoints.isNotEmpty()) {
            item {
                Text("نقاط ثبت‌شده", style = MaterialTheme.typography.titleLarge)
            }
            items(savedPoints) { point ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${point.time}  •  ${point.signal}  •  ${point.network}",
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            Text(
                "دقت داده‌ها به گوشی، اپراتور و نسخهٔ اندروید بستگی دارد. این برنامه دکل را تغییر نمی‌دهد و قدرت مودم را تنظیم نمی‌کند.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Suppress("MissingPermission")
private fun readSnapshot(context: Context): SignalSnapshot {
    val telephony = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val operator = runCatching { telephony.networkOperatorName }
        .getOrNull()
        .orEmpty()
        .ifBlank { "نامشخص" }

    val networkType = runCatching {
        when (telephony.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G / LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSDPA -> "3G / HSPA"
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "Wi‑Fi Calling"
            TelephonyManager.NETWORK_TYPE_UNKNOWN -> "نامشخص"
            else -> "شبکهٔ دیگر"
        }
    }.getOrDefault("در دسترس نیست")

    var strongestDbm: Int? = null
    var cellText = "اطلاعات سلول در دسترس نیست"
    var error: String? = null

    try {
        val hasLocation = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!hasLocation) {
            error = "برای قدرت سیگنال سلولی، مجوز موقعیت لازم است."
        } else {
            val cells: List<CellInfo> = telephony.allCellInfo.orEmpty()
            val registered = cells.filter { it.isRegistered }
            val candidates = if (registered.isNotEmpty()) registered else cells

            strongestDbm = candidates
                .mapNotNull { runCatching { it.cellSignalStrength.dbm }.getOrNull() }
                .filter { it < 0 }
                .maxOrNull()

            val first = registered.firstOrNull() ?: cells.firstOrNull()
            if (first != null) {
                cellText = "${first.javaClass.simpleName}${if (first.isRegistered) " (متصل)" else ""}"
            }
        }
    } catch (_: SecurityException) {
        error = "مجوز لازم داده نشده یا دسترسی توسط دستگاه محدود شده است."
    } catch (e: Exception) {
        error = e.localizedMessage ?: "خواندن اطلاعات سلول ممکن نشد."
    }

    return SignalSnapshot(
        signalDbm = strongestDbm,
        networkType = networkType,
        operator = operator,
        connection = getConnectionType(context),
        cellInfo = cellText,
        readError = error
    )
}

private fun hasPermission(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) ==
        PackageManager.PERMISSION_GRANTED

private fun signalLabel(dbm: Int): String = when {
    dbm >= -80 -> "عالی"
    dbm >= -90 -> "خوب"
    dbm >= -100 -> "متوسط"
    dbm >= -110 -> "ضعیف"
    else -> "بسیار ضعیف"
}

private fun getConnectionType(context: Context): String {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = manager.activeNetwork ?: return "بدون اتصال"
    val caps = manager.getNetworkCapabilities(network) ?: return "نامشخص"

    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi‑Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "دادهٔ موبایل"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> "اتصال دیگر"
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

private suspend fun runDownloadTest(): String = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null

    try {
        val url = URL("https://speed.cloudflare.com/__down?bytes=5000000")
        connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 20000
            requestMethod = "GET"
            setRequestProperty("Cache-Control", "no-cache")
        }

        val start = SystemClock.elapsedRealtime()
        var total = 0L
        val buffer = ByteArray(32 * 1024)

        connection.inputStream.use { input ->
            while (total < 5_000_000L) {
                val count = input.read(buffer)
                if (count <= 0) break
                total += count
            }
        }

        val seconds = ((SystemClock.elapsedRealtime() - start).coerceAtLeast(1)) / 1000.0
        if (total == 0L) {
            "داده‌ای دریافت نشد؛ اتصال یا دسترسی اینترنت را بررسی کنید."
        } else {
            val mbps = (total * 8.0 / seconds / 1_000_000.0)
            "سرعت تقریبی دانلود: ${String.format(Locale.getDefault(), "%.2f", mbps)} Mbps"
        }
    } catch (e: Exception) {
        "آزمون انجام نشد: ${e.localizedMessage ?: "اتصال را بررسی کنید"}"
    } finally {
        connection?.disconnect()
    }
}
