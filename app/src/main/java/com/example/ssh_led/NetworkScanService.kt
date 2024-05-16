package com.example.ssh_led

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONObject
import java.io.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class NetworkScanService : Service() {
    private val udpPort = 12345
    private val secondaryUdpPort = 5005

    private val handlerThread = HandlerThread("NetworkThread")
    private lateinit var handler: Handler
    private var lastIpNotification: String? = null
    private var lastUpdateTime: Long = 0L
    private var timer: Timer? = null
    private var isDisconnected = false
    private var lastBlinkerState: String? = null
    private val receivedMessages = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        val userAction = intent.getBooleanExtra("userAction", false)
        val modeChange = intent.getStringExtra("modeChange")

        modeChange?.let { handleModeChange(it) }

        if (!userAction) {
            startForegroundServiceWithNotification()
            startSignalSending()
            listenForUdpBroadcast()
        }

        val signal = intent.getStringExtra("signal") ?: "REQUEST_IP"
        handleSignal(signal, userAction)

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "service_channel",
                "Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun handleModeChange(mode: String) {
        when (mode) {
            "manual" -> sendSignal("ENABLE_MANUAL_MODE")
            "auto" -> sendSignal("ENABLE_AUTO_MODE")
        }
    }

    private fun startForegroundServiceWithNotification() {
        val lastKnownIP = readIpFromCache()
        if (!lastKnownIP.isNullOrEmpty() && isServerReachable(lastKnownIP)) {
            updateNotification("연결된 IP: $lastKnownIP", "CONNECTED")
            lastIpNotification = "CONNECTED"
        } else {
            handleDisconnectedState()
        }
    }

    private fun handleDisconnectedState() {
        updateNotification("용굴라이더와 연결되지 않았습니다.", "DISCONNECTED")
        lastIpNotification = "DISCONNECTED"
    }

    private fun startSignalSending() {
        handler.postDelayed({
            sendSignal("REQUEST_IP")
            sendSignal("REQUEST_RECORDING_STATUS")
            startSignalSending()
        }, 5000)  // 주기 변경
    }

    private fun sendSignal(signal: String) {
        handler.post {
            try {
                DatagramSocket().use { socket ->
                    socket.broadcast = true
                    val sendData = signal.toByteArray()
                    val packet = DatagramPacket(sendData, sendData.size, InetAddress.getByName("255.255.255.255"), udpPort)
                    socket.send(packet)
                }
            } catch (e: IOException) {
                Log.e("NetworkScanService", "신호 전송 중 오류 발생: ", e)
            }
        }
    }

    private fun listenForUdpBroadcast() {
        arrayOf(udpPort, secondaryUdpPort).forEach { port ->
            Thread {
                try {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(java.net.InetSocketAddress(port))
                    }.use { socket ->
                        val buffer = ByteArray(1024)
                        while (true) {
                            val packet = DatagramPacket(buffer, buffer.size)
                            socket.receive(packet)
                            resetTimer()
                            if (port == udpPort) handleReceivedPacket(packet) else handleReceivedPacketSecondary(packet)
                        }
                    }
                } catch (e: IOException) {
                    Log.e("NetworkScanService", "UDP 브로드캐스트 수신 중 오류 발생: ", e)
                }
            }.start()
        }
    }

    private fun resetTimer() {
        synchronized(this) {
            timer?.cancel()
            timer?.purge()
        }
        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    if (System.currentTimeMillis() - lastUpdateTime >= 8000) {
                        isDisconnected = true
                        updateConnectionStatus(false)
                    }
                }
            }, 2000)
        }
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        val ipIntent = Intent("UPDATE_IP_ADDRESS").apply {
            putExtra("ip_address", if (isConnected) readIpFromCache() ?: "Unknown IP" else "DISCONNECTED")
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(ipIntent)

        val lastKnownIP = readIpFromCache()
        if (isConnected && !lastKnownIP.isNullOrEmpty()) {
            updateNotification("연결된 IP: $lastKnownIP", "CONNECTED")
        } else if (!isConnected && lastIpNotification != "DISCONNECTED") {
            updateNotification("용굴라이더와 연결되지 않았습니다.", "DISCONNECTED")
        }
    }

    private fun handleReceivedPacket(packet: DatagramPacket) {
        val receivedText = String(packet.data, 0, packet.length).trim()
        Log.d("NetworkScanService", "Received UDP packet: $receivedText")
        val parts = receivedText.split(" - ")
        if (parts.size == 2) {
            val ipInfo = parts[0].substring(3).trim()
            val status = parts[1].trim()
            saveIpToCache(ipInfo)
            updateNotification("연결된 IP: $ipInfo", "CONNECTED")
            broadcastUpdate("UPDATE_IP_ADDRESS", "ip_address", ipInfo)
            broadcastUpdate("UPDATE_RECORDING_STATUS", "recording_status", status)
        }
    }

    private fun handleReceivedPacketSecondary(packet: DatagramPacket) {
        val receivedText = String(packet.data, 0, packet.length).trim()
        Log.d("NetworkScanService", "Received UDP packet on port 5005: $receivedText")

        synchronized(this) {
            val currentTime = System.currentTimeMillis()
            if (receivedMessages[receivedText] == null || currentTime - receivedMessages[receivedText]!! > 1000) {
                receivedMessages[receivedText] = currentTime
                parseAndHandleJson(receivedText)
            }
        }
    }

    private fun parseAndHandleJson(receivedText: String) {
        try {
            val json = JSONObject(receivedText)
            val mode = json.getString("mode")
            val message = json.getJSONObject("message")
            val pin = message.getInt("pin")
            val state = message.getString("state")

            Log.d("NetworkScanService", "Mode: $mode, Pin: $pin, State: $state")

            val status = when {
                pin == 26 && state == "ON" -> "RIGHT_ON"
                pin == 26 && state == "OFF" -> "RIGHT_OFF"
                pin == 17 && state == "ON" -> "LEFT_ON"
                pin == 17 && state == "OFF" -> "LEFT_OFF"
                else -> null
            }

            if (status != null && status != lastBlinkerState) {
                lastBlinkerState = status
                broadcastUpdate("UPDATE_BLINKER_STATUS", "blinker_status", status)
            }

        } catch (e: Exception) {
            Log.e("NetworkScanService", "Error parsing JSON: ", e)
        }
    }

    private fun broadcastUpdate(action: String, key: String, value: String) {
        val intent = Intent(action).apply {
            putExtra(key, value)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun updateNotification(contentText: String, connectionStatus: String) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < 1000) return
        lastUpdateTime = currentTime

        if (lastIpNotification == connectionStatus) return
        lastIpNotification = connectionStatus

        // 연결 상태에 따른 알림 제목 설정
        val notificationTitle = if (connectionStatus == "CONNECTED") {
            "용굴라이더와 연결됨"
        } else {
            "용굴라이더와 연결되지 않았습니다"
        }

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notificationBuilder = NotificationCompat.Builder(this, "service_channel").apply {
            setContentTitle(notificationTitle)
            // 연결되지 않은 상태에서는 내용은 빈 문자열로 설정
            setContentText(if (connectionStatus == "CONNECTED") contentText else "")
            setSmallIcon(R.drawable.ic_stat_)
            setContentIntent(pendingIntent)  // 알림 클릭 시 실행할 인텐트 설정
            setAutoCancel(true)
        }
        startForeground(1, notificationBuilder.build())
    }

    private fun saveIpToCache(ipAddress: String) {
        try {
            File(cacheDir, "last_ip_address").writeText(ipAddress)
        } catch (e: IOException) {
            Log.e("NetworkScanService", "Error writing IP to cache", e)
        }
    }

    private fun readIpFromCache(): String? {
        return try {
            File(cacheDir, "last_ip_address").readText()
        } catch (e: FileNotFoundException) {
            Log.e("NetworkScanService", "IP cache file not found", e)
            null  // 파일이 없으면 null 반환
        } catch (e: IOException) {
            Log.e("NetworkScanService", "Error reading IP from cache", e)
            null
        }
    }

    private fun isServerReachable(ipAddress: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 $ipAddress")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            Log.d("Ping", "Ping output: $output")
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e("NetworkScanService", "Error checking server reachability", e)
            false
        }
    }

    private fun handleSignal(signal: String, userAction: Boolean) {
        handler.post {
            when (signal) {
                "Right Blinker Activated", "Left Blinker Activated",
                "REQUEST_RECORDING_STATUS" -> {
                    sendSignal(signal)
                    if (!userAction) resetTimer()
                }
                else -> {
                    sendSignal(signal)
                    sendSignal("REQUEST_RECORDING_STATUS")
                    if (!userAction) resetTimer()
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
    }
}
