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
import java.util.Timer
import java.util.TimerTask

class NetworkScanService : Service() {
    private val udpPort = 12345
    private val handlerThread = HandlerThread("NetworkThread")
    private lateinit var handler: Handler
    private var lastIpNotification: String? = null
    private var lastUpdateTime: Long = 0L
    private var timer: Timer? = null
    private var isDisconnected = false

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
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val userAction = intent.getBooleanExtra("userAction", false)
        if (!userAction) {
            startForegroundServiceWithNotification()
        }
        val signal = intent.getStringExtra("signal") ?: "REQUEST_IP"

        Thread {
            when (signal) {
                "Right Blinker Activated", "Left Blinker Activated" -> {
                    sendSignal(signal)
                    if (!userAction) {
                        resetTimer()
                    }
                }
                "REQUEST_RECORDING_STATUS" -> {
                    sendSignal(signal)
                    if (!userAction) {
                        resetTimer()
                    }
                }
                else -> {
                    sendSignal(signal)
                    sendSignal("REQUEST_RECORDING_STATUS")
                    if (!userAction) {
                        resetTimer()
                    }
                }
            }
        }.start()

        if (!userAction) {
            startSignalSending()
            listenForUdpBroadcast()
        }

        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val prefs = getSharedPreferences("NetworkPreferences", Context.MODE_PRIVATE)
        val lastKnownIP = prefs.getString("last_ip_address", "")
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
        lastIpNotification = if (lastKnownIP.isNullOrEmpty()) "DISCONNECTED" else lastKnownIP
    }

    private fun startSignalSending() {
        handler.postDelayed({
            sendSignal("REQUEST_IP")
            sendSignal("REQUEST_RECORDING_STATUS")
            startSignalSending()
        }, 1000)
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
                    resetTimer()
                    handleReceivedPacket(packet)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("NetworkScanService", "UDP 브로드캐스트 수신 중 오류 발생: ", e)
            }
        }.start()
    }

    private fun resetTimer() {
        synchronized(this) {
            // 기존 타이머 취소
            try {
                timer?.cancel()
                timer?.purge()
            } catch (e: IllegalStateException) {
                Log.e("NetworkScanService", "Timer cancel error: ", e)
            }
        }
        // 새로운 타이머 생성
        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    // 타이머가 만료될 때만 isDisconnected를 true로 설정
                    if (System.currentTimeMillis() - lastUpdateTime >= 5000) {
                        isDisconnected = true
                        updateConnectionStatus(false)
                    }
                }
            }, 10000) // 5초 후에 작업 실행
        }
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        val prefs = getSharedPreferences("NetworkPreferences", Context.MODE_PRIVATE)
        val lastKnownIP = prefs.getString("last_ip_address", "")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (isConnected && !lastKnownIP.isNullOrEmpty()) {
            // 연결된 IP로 알림 업데이트
            updateNotification(lastKnownIP)
        } else if (!isConnected && lastIpNotification != "DISCONNECTED") {
            // 연결 끊김 알림 업데이트 (연결 끊김 상태가 변경되었을 때만)
            val notificationBuilder = Notification.Builder(this, "service_channel").apply {
                setContentTitle("용굴라이더와 연결되지 않았습니다.")
                setSmallIcon(android.R.drawable.stat_notify_sync)
            }
            startForeground(1, notificationBuilder.build())
            lastIpNotification = "DISCONNECTED"  // 상태 업데이트
        }
    }

    private fun handleReceivedPacket(packet: DatagramPacket) {
        val receivedText = String(packet.data, 0, packet.length).trim()
        Log.d("NetworkScanService", "Received UDP packet: $receivedText")
        val parts = receivedText.split(" - ")
        if (parts.size == 2) {
            val ipInfo = parts[0].substring(3).trim()
            val status = parts[1].trim()
            val prefs = getSharedPreferences("NetworkPreferences", Context.MODE_PRIVATE)

            // IP 정보를 저장하고 관련 알림을 업데이트합니다.
            prefs.edit().putString("last_ip_address", ipInfo).apply()
            val ipIntent = Intent("UPDATE_IP_ADDRESS").apply { putExtra("ip_address", ipInfo) }
            LocalBroadcastManager.getInstance(this).sendBroadcast(ipIntent)
            updateNotification(ipInfo)
            val recIntent = Intent("UPDATE_RECORDING_STATUS").apply { putExtra("recording_status", status) }
            LocalBroadcastManager.getInstance(this).sendBroadcast(recIntent)
        }
    }

    private fun updateNotification(ipAddress: String?) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < 5000) return
        lastUpdateTime = currentTime
        val ipPart = ipAddress?.split("-")?.first()?.trim()
        if (lastIpNotification == ipPart) return
        lastIpNotification = ipPart
        val prefs = getSharedPreferences("NetworkPreferences", Context.MODE_PRIVATE)
        val lastKnownIP = prefs.getString("last_ip_address", "")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationBuilder = Notification.Builder(this, "service_channel").apply {
            if (!lastKnownIP.isNullOrEmpty()) {
                setContentTitle("IP 연결됨")
                setContentText("연결된 IP: $lastKnownIP")
                setSmallIcon(android.R.drawable.stat_notify_sync)
            } else {
                setContentTitle("용굴라이더와 연결되지 않았습니다.")
                setSmallIcon(android.R.drawable.stat_notify_sync)
            }
        }
        startForeground(1, notificationBuilder.build())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
    }
}
