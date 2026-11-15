package com.example.towerscout

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NetworkScreen(this)
                }
            }
        }
    }
}

@Composable
private fun NetworkScreen(context: Context) {
    var status by remember {
        mutableStateOf("برای دیدن اطلاعات، مجوزها را بدهید.")
    }

    val permissions = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        status = readNetworkInfo(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "بررسی اتصال موبایل",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = status)
        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            val allGranted = permissions.all { permission ->
                ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
            }

            if (allGranted) {
                status = readNetworkInfo(context)
            } else {
                permissionLauncher.launch(permissions)
            }
        }) {
            Text("بررسی دوباره")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }) {
            Text("تنظیمات شبکه")
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("این برنامه دکل را انتخاب یا اتصال را به‌اجبار تغییر نمی‌دهد.")
    }
}

@Suppress("MissingPermission")
private fun readNetworkInfo(context: Context): String {
    val manager = context.getSystemService(Context.TELEPHONY_SERVICE)
        as TelephonyManager

    return try {
        val operator = manager.networkOperatorName.ifBlank { "نامشخص" }

        val networkType = when (manager.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_LTE -> "4G / LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "5G / NR"
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G / HSPA"
            TelephonyManager.NETWORK_TYPE_EDGE,
            TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
            else -> "نامشخص یا در دسترس نیست"
        }

        "اپراتور: $operator\nنوع شبکه: $networkType"
    } catch (exception: Exception) {
        "اطلاعات شبکه در دسترس نیست: ${exception.message ?: "خطای نامشخص"}"
    }
}
