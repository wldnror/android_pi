package com.example.ssh_led

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
    private val displayedIpAddresses = HashSet<String>()
    private var scanNotificationId = -1
    private var isServiceInForeground = false

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "네트워크 스캔 서비스 채널",
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
            sendSignal("REQUEST_RECORDING_STATUS")
        }.start()

        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        // 서비스가 이미 포그라운드에 있는지 확인
        if (!isServiceInForeground) {
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("네트워크 스캔 서비스")
                .setContentText("라즈베리 파이 장치를 스캔 중입니다")
                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            startForeground(1, notification)
            isServiceInForeground = true
        }

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
            Log.e("NetworkScanService", "신호 전송 중 오류 발생: ", e)
        }
    }

    private fun listenForUdpBroadcast() {
        Thread {
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true  // 포트 재사용 허용
                    bind(java.net.InetSocketAddress(udpPort))  // 명시적으로 소켓을 포트에 바인드
                }

                socket.use {
                    val buffer = ByteArray(1024)
                    while (true) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        it.receive(packet)
                        val receivedText = String(packet.data, 0, packet.length).trim()
                        Log.d("NetworkScanService", "UDP 방송 수신: $receivedText")

                        // IP 주소가 수신된 경우 푸시 알림으로 표시
                        if (receivedText.startsWith("IP:")) {
                            val ipAddress = receivedText.substring(3)
                            showIpAddressNotification(ipAddress)
                        }

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
                Log.e("NetworkScanService", "UDP 방송 수신 중 오류 발생: ", e)
            }
        }.start()
    }

    // IP 주소를 푸시 알림으로 표시하는 함수
    private fun showIpAddressNotification(ipAddress: String) {
        // 이미 표시된 IP 주소인 경우 중복으로 간주하고 푸시 알림을 표시하지 않음
        if (displayedIpAddresses.contains(ipAddress)) {
            return
        }

        // 새로운 IP 주소를 표시했으므로 목록에 추가
        displayedIpAddresses.add(ipAddress)

        // 스캔 중인 푸시 알림이 이미 표시된 경우 해당 푸시 알림을 업데이트하여 새로운 IP 주소를 표시하고,
        // 그렇지 않은 경우 새로운 푸시 알림을 표시합니다.
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("연결된 IP 주소")
            .setContentText("연결된 IP 주소: $ipAddress")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (scanNotificationId != -1) {
            // 스캔 중인 푸시 알림이 이미 표시된 경우 해당 푸시 알림을 업데이트하여 새로운 IP 주소를 표시합니다.
            notificationManager.notify(scanNotificationId, notificationBuilder.build())
        } else {
            // 스캔 중인 푸시 알림이 표시되지 않은 경우 새로운 푸시 알림을 표시합니다.
            scanNotificationId = 2
            notificationManager.notify(scanNotificationId, notificationBuilder.build())
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
