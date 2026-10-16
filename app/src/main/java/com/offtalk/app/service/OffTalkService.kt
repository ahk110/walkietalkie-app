package com.offtalk.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.offtalk.app.MainActivity
import com.offtalk.app.audio.AudioEngine
import com.offtalk.app.location.LocationProvider
import com.offtalk.app.model.PeerDevice
import com.offtalk.app.p2p.SocketService
import com.offtalk.app.p2p.WifiDirectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.net.InetAddress

class OffTalkService : Service() {

    private val binder = LocalBinder()

    private val _statusText = MutableStateFlow("Disconnected")
    val statusText: StateFlow<String> = _statusText

    private val _discoveredPeers = MutableStateFlow<List<PeerDevice>>(emptyList())
    val discoveredPeers: StateFlow<List<PeerDevice>> = _discoveredPeers

    private val _sosReceivedEvent = MutableStateFlow<String?>(null)
    val sosReceivedEvent: StateFlow<String?> = _sosReceivedEvent

    private lateinit var wifiDirectManager: WifiDirectManager
    private lateinit var socketService: SocketService
    private lateinit var audioEngine: AudioEngine
    private lateinit var locationProvider: LocationProvider

    private var connectedAddress: InetAddress? = null
    private var alarmRingtone: Ringtone? = null

    inner class LocalBinder : Binder() {
        fun getService(): OffTalkService = this@OffTalkService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()

        audioEngine = AudioEngine()
        locationProvider = LocationProvider(this)
        locationProvider.startLocationUpdates()

        socketService = SocketService(
            onHandshakeResult = { success, hostAddress ->
                if (success && hostAddress != null) {
                    connectedAddress = InetAddress.getByName(hostAddress)
                    _statusText.value = "Connected"
                    audioEngine.startPlayback()
                } else {
                    _statusText.value = "Disconnected"
                    audioEngine.stopPlayback()
                }
            },
            onSosReceived = { coords ->
                _sosReceivedEvent.value = coords
                playAlarmSound()
            }
        )

        wifiDirectManager = WifiDirectManager(
            context = this,
            onPeersUpdated = { peers -> _discoveredPeers.value = peers },
            onConnectionInfoAvailable = { info ->
                _statusText.value = "Connecting..."
                if (info.groupFormed && info.isGroupOwner) {
                    socketService.startServer(currentChannel)
                } else if (info.groupFormed) {
                    socketService.connectToHost(info.groupOwnerAddress.hostAddress ?: "", currentChannel)
                }
            }
        )
        wifiDirectManager.registerReceiver()

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        alarmRingtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
    }

    private var currentChannel: String = "1234"

    fun startDiscovery(channelCode: String) {
        currentChannel = channelCode
        _statusText.value = "Searching..."
        wifiDirectManager.startServiceBroadcastingAndDiscovery(channelCode)
    }

    fun connectToPeer(deviceAddress: String) {
        _statusText.value = "Connecting..."
        wifiDirectManager.connectToDevice(deviceAddress) {
            _statusText.value = "Connecting..."
        }
    }

    fun startTalking() {
        connectedAddress?.let {
            audioEngine.startRecording(it)
        }
    }

    fun stopTalking() {
        audioEngine.stopRecording()
    }

    fun sendSos() {
        val coords = locationProvider.getLastKnownCoordinates()
        socketService.sendSos(coords)
    }

    fun clearSosEvent() {
        _sosReceivedEvent.value = null
        alarmRingtone?.stop()
    }

    private fun playAlarmSound() {
        try {
            alarmRingtone?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "offtalk_service_channel"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "OffTalk P2P Service",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("OffTalk Active")
            .setContentText("Offline Walkie-Talkie is running in background")
            .setSmallIcon(android.R.drawable.stat_sys_speakerphone)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1001, notification)
    }

    override fun onDestroy() {
        audioEngine.stopRecording()
        audioEngine.stopPlayback()
        socketService.stop()
        wifiDirectManager.unregisterReceiver()
        locationProvider.stopLocationUpdates()
        alarmRingtone?.stop()
        super.onDestroy()
    }
}
