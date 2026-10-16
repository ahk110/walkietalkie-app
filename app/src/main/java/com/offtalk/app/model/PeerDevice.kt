package com.offtalk.app.model

data class PeerDevice(
    val deviceName: String,
    val deviceAddress: String,
    val channelCode: String,
    val isGroupOwner: Boolean = false
)
