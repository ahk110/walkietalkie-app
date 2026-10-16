package com.offtalk.app.p2p

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Build
import android.util.Log
import com.offtalk.app.model.PeerDevice

@SuppressLint("MissingPermission")
class WifiDirectManager(
    private val context: Context,
    private val onPeersUpdated: (List<PeerDevice>) -> Unit,
    private val onConnectionInfoAvailable: (WifiP2pInfo) -> Unit
) {
    private val manager: WifiP2pManager? = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = manager?.initialize(context, context.mainLooper, null)

    private val serviceType = "_offtalk._tcp"
    private val discoveredPeersMap = mutableMapOf<String, PeerDevice>()
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    var isWifiP2pEnabled = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    isWifiP2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                    if (networkInfo?.isConnected == true) {
                        manager?.requestConnectionInfo(channel) { info ->
                            onConnectionInfoAvailable(info)
                        }
                    }
                }
            }
        }
    }

    fun registerReceiver() {
        context.registerReceiver(receiver, intentFilter)
    }

    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }

    fun startServiceBroadcastingAndDiscovery(channelCode: String) {
        if (manager == null || channel == null) return

        manager.clearLocalServices(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                val record = mapOf("channel" to channelCode, "app" to "offtalk_v1")
                val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance("OffTalkDevice", serviceType, record)

                manager.addLocalService(channel, serviceInfo, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        setupServiceDiscovery(channelCode)
                    }
                    override fun onFailure(reason: Int) {}
                })
            }
            override fun onFailure(reason: Int) {}
        })
    }

    private fun setupServiceDiscovery(currentChannelCode: String) {
        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { _, record, device ->
            if (record["app"] == "offtalk_v1" && record["channel"] == currentChannelCode) {
                val peer = PeerDevice(
                    deviceName = device.deviceName.ifEmpty { "OffTalk Peer" },
                    deviceAddress = device.deviceAddress,
                    channelCode = record["channel"] ?: ""
                )
                discoveredPeersMap[device.deviceAddress] = peer
                onPeersUpdated(discoveredPeersMap.values.toList())
            }
        }

        val serviceListener = WifiP2pManager.DnsSdServiceResponseListener { _, _, _ -> }

        manager?.setDnsSdResponseListeners(channel, serviceListener, txtListener)
        
        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager?.clearServiceRequests(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                manager.addServiceRequest(channel, serviceRequest, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        manager.discoverServices(channel, null)
                    }
                    override fun onFailure(reason: Int) {}
                })
            }
            override fun onFailure(reason: Int) {}
        })
    }

    fun connectToDevice(deviceAddress: String, onInitiated: () -> Unit) {
        val config = WifiP2pConfig().apply {
            this.deviceAddress = deviceAddress
        }
        manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onInitiated()
            }
            override fun onFailure(reason: Int) {
                Log.e("WifiDirectManager", "Connect failed with code $reason")
            }
        })
    }

    fun disconnect() {
        manager?.removeGroup(channel, null)
    }
}
