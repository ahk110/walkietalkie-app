package com.offtalk.app.p2p

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class SocketService(
    private val onHandshakeResult: (Boolean, String?) -> Unit,
    private val onSosReceived: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var printWriter: PrintWriter? = null

    @Volatile private var isRunning = false
    private val tcpPort = 50004
    private val appId = "OFFTALK1"

    fun startServer(channelCode: String) {
        stop()
        isRunning = true
        thread {
            try {
                serverSocket = ServerSocket(tcpPort)
                Log.d("SocketService", "TCP Server active on $tcpPort")
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    handleConnection(socket, channelCode)
                }
            } catch (e: Exception) {
                Log.e("SocketService", "Server error", e)
            }
        }
    }

    fun connectToHost(hostAddress: String, channelCode: String) {
        stop()
        isRunning = true
        thread {
            try {
                val socket = Socket(hostAddress, tcpPort)
                handleConnection(socket, channelCode)
            } catch (e: Exception) {
                Log.e("SocketService", "Client connect error", e)
                onHandshakeResult(false, null)
            }
        }
    }

    private fun handleConnection(socket: Socket, expectedChannel: String) {
        this.clientSocket = socket
        try {
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            this.printWriter = writer

            writer.println("$appId:$expectedChannel")
            val handshakeMsg = reader.readLine()

            if (handshakeMsg != null && handshakeMsg.startsWith("$appId:")) {
                val remoteChannel = handshakeMsg.substringAfter("$appId:")
                if (remoteChannel == expectedChannel) {
                    onHandshakeResult(true, socket.inetAddress.hostAddress)

                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let {
                            if (it.startsWith("SOS:")) {
                                onSosReceived(it.substringAfter("SOS:"))
                            }
                        }
                    }
                } else {
                    Log.e("SocketService", "Channel mismatch: $remoteChannel vs $expectedChannel")
                    socket.close()
                    onHandshakeResult(false, null)
                }
            } else {
                socket.close()
                onHandshakeResult(false, null)
            }
        } catch (e: Exception) {
            Log.e("SocketService", "Connection handshaking error", e)
            onHandshakeResult(false, null)
        }
    }

    fun sendSos(coords: String) {
        thread {
            try {
                printWriter?.println("SOS:$coords")
            } catch (e: Exception) {
                Log.e("SocketService", "Failed to send SOS", e)
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            clientSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("SocketService", "Error stopping sockets", e)
        }
        clientSocket = null
        serverSocket = null
        printWriter = null
    }
}
