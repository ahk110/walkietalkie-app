package com.offtalk.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import com.offtalk.app.service.OffTalkService
import com.offtalk.app.ui.MainScreen
import com.offtalk.app.ui.theme.OffTalkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var offTalkService: OffTalkService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as OffTalkService.LocalBinder
            offTalkService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            offTalkService = null
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions -> checkHardwareStates() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()

        val serviceIntent = Intent(this, OffTalkService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)

        setContent {
            OffTalkTheme {
                var statusText by remember { mutableStateOf("Disconnected") }
                var peers by remember { mutableStateOf(emptyList<com.offtalk.app.model.PeerDevice>()) }
                var sosCoords by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(isBound) {
                    if (isBound) {
                        offTalkService?.let { service ->
                            launch { service.statusText.collect { statusText = it } }
                            launch { service.discoveredPeers.collect { peers = it } }
                            launch { service.sosReceivedEvent.collect { sosCoords = it } }
                        }
                    }
                }

                MainScreen(
                    statusText = statusText,
                    discoveredPeers = peers,
                    sosReceivedCoords = sosCoords,
                    onChannelChange = {},
                    onSearchClicked = { channel ->
                        checkHardwareStates()
                        offTalkService?.startDiscovery(channel)
                    },
                    onConnectClicked = { peer -> offTalkService?.connectToPeer(peer.deviceAddress) },
                    onPttPressStateChanged = { pressed ->
                        if (pressed) offTalkService?.startTalking() else offTalkService?.stopTalking()
                    },
                    onSosClicked = {
                        offTalkService?.sendSos()
                        Toast.makeText(this, "SOS Broadcast Sent", Toast.LENGTH_SHORT).show()
                    },
                    onDismissSos = { offTalkService?.clearSosEvent() }
                )
            }
        }
    }

    private fun checkHardwareStates() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Wi-Fi is OFF. Enable Wi-Fi for Wi-Fi Direct.", Toast.LENGTH_LONG).show()
        }
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Location/GPS is OFF. Required for Wi-Fi Direct and SOS.", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissionsLauncher.launch(missing.toTypedArray())
    }

    override fun onDestroy() {
        if (isBound) { unbindService(connection); isBound = false }
        super.onDestroy()
    }
}
