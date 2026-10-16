package com.offtalk.app.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentLinkedQueue

class AudioEngine {
    private val sampleRate = 16000
    private val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
    private val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    @Volatile private var isRecording = false
    @Volatile private var isPlaying = false
    private val jitterBuffer = ConcurrentLinkedQueue<ByteArray>()
    private val udpPort = 50005

    @SuppressLint("MissingPermission")
    fun startRecording(targetAddress: InetAddress) {
        if (isRecording) return
        isRecording = true
        val packetSize = 640
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfigIn, audioFormat, packetSize * 4)
        audioRecord?.startRecording()

        Thread {
            try {
                val socket = DatagramSocket()
                val buffer = ByteArray(packetSize)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        val packet = DatagramPacket(buffer, read, targetAddress, udpPort)
                        socket.send(packet)
                    }
                }
                socket.close()
            } catch (e: Exception) { Log.e("AudioEngine", "Audio TX Error", e) }
        }.start()
    }

    fun stopRecording() {
        isRecording = false
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
    }

    fun startPlayback() {
        if (isPlaying) return
        isPlaying = true
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(audioFormat).setSampleRate(sampleRate).setChannelMask(channelConfigOut).build())
            .setBufferSizeInBytes(640 * 10)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()

        Thread {
            try {
                val socket = DatagramSocket(udpPort)
                socket.soTimeout = 1000
                val buffer = ByteArray(1024)
                while (isPlaying) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        jitterBuffer.add(packet.data.copyOf(packet.length))
                    } catch (_: Exception) {}
                }
                socket.close()
            } catch (e: Exception) { Log.e("AudioEngine", "Audio RX Error", e) }
        }.start()

        Thread {
            while (isPlaying) {
                val pcm = jitterBuffer.poll()
                if (pcm != null) { audioTrack?.write(pcm, 0, pcm.size) } else { Thread.sleep(10) }
            }
        }.start()
    }

    fun stopPlayback() {
        isPlaying = false
        try { audioTrack?.stop(); audioTrack?.release() } catch (_: Exception) {}
        audioTrack = null
        jitterBuffer.clear()
    }
}
