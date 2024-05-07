package com.example.ssh_led

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class NetworkScanService : Service() {
    private val udpPort = 12345
    private val handlerThread = HandlerThread("NetworkThread")
    private lateinit var handler: Handler
    private var lastIpNotification: String? = null  // 이전에 알림을 보낸 IP 주소를 저장할 변수

    override fun onCreate() {
        super.onCreate()
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "service_channel",
                "Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        startForegroundServiceWithNotification()
        val signal = intent.getStringExtra("signal") ?: "REQUEST_IP"

        Thread {
            when (signal) {
                "Right Blinker Activated", "Left Blinker Activated" -> sendSignal(signal)
                "REQUEST_RECORDING_STATUS" -> sendSignal(signal)
                else -> {
                    sendSignal(signal)  // IP 요청 등 기타 신호 처리
                    sendSignal("REQUEST_RECORDING_STATUS")  // 녹화 상태 요청
                }
            }
        }.start()

        startSignalSending()
        listenForUdpBroadcast()

        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val prefs = getSharedPreferences("NetworkPreferences", Context.MODE_PRIVATE)
        val lastKnownIP = prefs.getString("last_ip_address", "")

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationBuilder = Notification.Builder(this, "service_channel")

        if (!lastKnownIP.isNullOrEmpty()) {
            notificationBuilder
                .setContentTitle("IP 연결됨")
                .setContentText("연결된 IP: $lastKnownIP")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
        } else {
            notificationBuilder
                .setContentTitle("용굴라이더와 연결되지 않았습니다.")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
        }

        startForeground(1, notificationBuilder.build())
    }


    private fun startSignalSending() {
        handler.postDelayed({
            sendSignal("REQUEST_IP")
            sendSignal("REQUEST_RECORDING_STATUS")
            startSignalSending()  // 재귀적으로 반복
        }, 1000)  // 1초 후에 다시 실행
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
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(udpPort))
                }

                val buffer = ByteArray(1024)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val receivedText = String(packet.data, 0, packet.length).trim()
                    Log.d("NetworkScanService", "Received UDP packet: $receivedText")

                    // 메시지 형식 "IP:192.168.1.2 - RECORDING" 처리
                    val parts = receivedText.split(" - ")
                    if (parts.size == 2) {
                        val ipInfo = parts[0].substring(3).trim() // "IP:192.168.1.2"에서 IP 주소 추출
                        val status = parts[1].trim() // "RECORDING" 또는 "NOT_RECORDING"

                        // SharedPreferences에 IP 주소 저장
                        val prefs = getSharedPreferences("NetworkPreferences", Context.MODE_PRIVATE)
                        prefs.edit().putString("last_ip_address", ipInfo).apply()

                        // IP 주소 업데이트
                        val ipIntent = Intent("UPDATE_IP_ADDRESS")
                        ipIntent.putExtra("ip_address", ipInfo)
                        LocalBroadcastManager.getInstance(this).sendBroadcast(ipIntent)
                        updateNotification(ipInfo)

                        // 녹화 상태 업데이트
                        val recIntent = Intent("UPDATE_RECORDING_STATUS")
                        recIntent.putExtra("recording_status", status)
                        LocalBroadcastManager.getInstance(this).sendBroadcast(recIntent)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("NetworkScanService", "UDP 브로드캐스트 수신 중 오류 발생: ", e)
            }
        }.start()
    }

    private var lastUpdateTime = 0L

    private fun updateNotification(ipAddress: String?) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < 0) { // 1초 이내의 메시지는 무시
            return
        }
        lastUpdateTime = currentTime

        // IP 주소의 앞부분만 사용
        val ipPart = ipAddress?.split("-")?.first()?.trim()

        // 이전 알림과 동일한 경우 알림을 보내지 않음
        if (lastIpNotification == ipPart) {
            return
        }

        lastIpNotification = ipPart  // 새로운 IP로 업데이트

        val prefs = getSharedPreferences("NetworkPreferences", Context.MODE_PRIVATE)
        val lastKnownIP = prefs.getString("last_ip_address", "")

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationBuilder = Notification.Builder(this, "service_channel")

        if (!lastKnownIP.isNullOrEmpty()) {
            notificationBuilder
                .setContentTitle("IP 연결됨")
                .setContentText("연결된 IP: $lastKnownIP")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
        } else {
            notificationBuilder
                .setContentTitle("용굴라이더와 연결되지 않았습니다.")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
        }

        val notificationId = 1
        startForeground(notificationId, notificationBuilder.build())
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
    }
}
