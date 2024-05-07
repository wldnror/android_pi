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
import java.util.LinkedList
import java.util.Queue

class NetworkScanService : Service() {
    private val udpPort = 12345
    private lateinit var handler: Handler
    private lateinit var handlerThread: HandlerThread // 클래스 변수로 선언
    private var lastUpdateTime = 0L
    private var lastIPAddress: String? = null
    private var lastRecordStatus: String? = null  // 마지막으로 알림된 IP 주소 저장
    private var messageQueue: Queue<String> = LinkedList()

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("NetworkThread").apply {
            start()
        }
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationBuilder = Notification.Builder(this, "service_channel")
            .setContentTitle("용굴라이더와 연결되지 않았습니다.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)

        startForeground(1, notificationBuilder.build())
    }

    private fun startSignalSending() {
        handler.postDelayed({
            sendSignal("REQUEST_IP")
            sendSignal("REQUEST_RECORDING_STATUS")
            startSignalSending()  // 재귀적으로 반복
        }, 5000)  // 5초 후에 다시 실행
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
                    val packet = DatagramPacket(buffer, buffer.size) // packet 변수 정의
                    socket.receive(packet)
                    val receivedText = String(packet.data, 0, packet.length).trim() // receivedText 정의
                    Log.d("NetworkScanService", "Received UDP packet: $receivedText")

                    if (receivedText.startsWith("IP:")) {
                        // 메시지에서 "-" 기준으로 IP 주소만 추출
                        val ipAddress = receivedText.substringAfter("IP:").substringBefore(" -")
                        val ipIntent = Intent("UPDATE_IP_ADDRESS")
                        ipIntent.putExtra("ip_address", ipAddress)
                        updateNotification(ipAddress, null) // IP 연결 알림 업데이트
                        LocalBroadcastManager.getInstance(this).sendBroadcast(ipIntent)
                    } else if (receivedText == "RECORDING" || receivedText == "NOT_RECORDING") {
                        val recIntent = Intent("UPDATE_RECORDING_STATUS")
                        recIntent.putExtra("recording_status", receivedText)
                        LocalBroadcastManager.getInstance(this).sendBroadcast(recIntent)
                    }

                    // 메시지 큐에 추가
                    synchronized(messageQueue) {
                        messageQueue.add(receivedText)
                    }

                    // 메시지 큐 처리
                    processMessageQueue()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("NetworkScanService", "UDP 브로드캐스트 수신 중 오류 발생: ", e)
            }
        }.start()
    }

    private fun processMessageQueue() {
        var latestIPMessage: String? = null
        var latestRecordMessage: String? = null

        synchronized(messageQueue) {
            while (messageQueue.isNotEmpty()) {
                val message = messageQueue.poll()
                if (message.startsWith("IP:")) {
                    latestIPMessage = message
                } else if (message == "RECORDING" || message == "NOT_RECORDING") {
                    latestRecordMessage = message
                }
            }
        }

        if (latestIPMessage != null || latestRecordMessage != null) {
            updateNotification(latestIPMessage, latestRecordMessage)
        }
    }

    private fun updateNotification(ipAddress: String?, recordStatus: String?) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < 1000) {
            return // 최근 업데이트 이후 1초 이내의 알림은 무시
        }
        lastUpdateTime = currentTime

        // 상태 변화가 없으면 업데이트하지 않음
        if (ipAddress == lastIPAddress && recordStatus == lastRecordStatus) {
            return
        }

        // 새로운 상태로 업데이트
        lastIPAddress = ipAddress
        lastRecordStatus = recordStatus

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = 1  // 모든 상태 알림에 대해 동일 ID 사용
        val notificationBuilder = Notification.Builder(this, "service_channel")

        if (ipAddress != null) {
            // IP 주소가 존재하는 경우에만 "IP 연결됨" 알림 표시
            notificationBuilder.setContentTitle("IP 연결됨").setContentText("연결된 IP: $ipAddress")
            notificationBuilder.setSmallIcon(android.R.drawable.stat_notify_sync)
            startForeground(notificationId, notificationBuilder.build())
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely() // 이제 handlerThread는 클래스 수준에서 선언된 변수이므로 접근 가능
    }
}
