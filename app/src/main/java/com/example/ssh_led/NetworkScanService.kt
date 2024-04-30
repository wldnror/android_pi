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
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Network Scan Service")
            .setContentText("Scanning for Raspberry Pi devices")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .build()

        startForeground(1, notification)

        Thread(Runnable {
            scanForDevices()
        }).start()

        return START_NOT_STICKY
    }

    private fun scanForDevices() {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val message = "Hello Raspberry Pi!"
                val sendData = message.toByteArray()
                val packet = DatagramPacket(sendData, sendData.size, InetAddress.getByName("255.255.255.255"), udpPort)
                socket.send(packet)

                // Listen for response
                val buf = ByteArray(1024)
                val response = DatagramPacket(buf, buf.size)
                socket.receive(response)
                val responseText = String(response.data, 0, response.length)

                updateMainActivity(responseText)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun updateMainActivity(ip: String) {
        val intent = Intent("UPDATE_IP_ADDRESS")
        intent.putExtra("ip_address", ip)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
