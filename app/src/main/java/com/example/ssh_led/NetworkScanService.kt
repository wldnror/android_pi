package com.example.ssh_led

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import android.util.Log

class NetworkScanService : Service() {
    private val CHANNEL_ID = "NetworkScanServiceChannel"
    private val udpPort = 12345

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Network Scan Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val signal = intent.getStringExtra("signal") ?: "REQUEST_IP"

        Thread {
            sendSignal(signal)
        }.start()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Network Scan Service")
            .setContentText("Scanning for Raspberry Pi devices")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .build()

        startForeground(1, notification)
        listenForUdpBroadcast()

        return START_NOT_STICKY
    }

    private fun sendSignal(signal: String) {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val sendData = signal.toByteArray()
                val packet = DatagramPacket(sendData, sendData.size, InetAddress.getByName("255.255.255.255"), udpPort)
                socket.send(packet)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("NetworkScanService", "Error sending signal: ", e)
        }
    }

    private fun listenForUdpBroadcast() {
        Thread {
            try {
                DatagramSocket(udpPort).use { socket ->
                    val buffer = ByteArray(1024)
                    while (true) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val receivedText = String(packet.data, 0, packet.length).trim()
                        Log.d("NetworkScanService", "Received UDP broadcast: $receivedText")
                        val updateIntent = if (receivedText.startsWith("IP:")) {
                            Intent("UPDATE_IP_ADDRESS").apply { putExtra("ip_address", receivedText.substring(3)) }
                        } else {
                            Intent("UPDATE_RECORDING_STATUS").apply { putExtra("recording_status", receivedText) }
                        }
                        LocalBroadcastManager.getInstance(this@NetworkScanService).sendBroadcast(updateIntent)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("NetworkScanService", "Error receiving UDP broadcast: ", e)
            }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
