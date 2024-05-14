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
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Timer
import java.util.TimerTask
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
        val modeChange = intent.getStringExtra("modeChange")

        if (modeChange != null) {
            handleModeChange(modeChange)
        }

        if (!userAction) {
            startForegroundServiceWithNotification()
            startSignalSending()
            listenForUdpBroadcast()
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

    private fun handleModeChange(mode: String) {
        when (mode) {
            "manual" -> sendSignal("ENABLE_MANUAL_MODE")
            "auto" -> sendSignal("ENABLE_AUTO_MODE")
        }
    }

    private fun startForegroundServiceWithNotification() {
        val lastKnownIP = readIpFromCache()
        if (!lastKnownIP.isNullOrEmpty()) {
            if (isServerReachable(lastKnownIP)) {
                val notificationBuilder = Notification.Builder(this, "service_channel")
                    .setContentTitle("용굴라이더와 연결됨")
                    .setContentText("연결된 IP: $lastKnownIP")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
                startForeground(1, notificationBuilder.build())
                lastIpNotification = lastKnownIP
            } else {
                handleDisconnectedState()
            }
        } else {
            handleDisconnectedState()
        }
    }

    private fun handleDisconnectedState() {
        val notificationBuilder = Notification.Builder(this, "service_channel")
            .setContentTitle("용굴라이더와 연결되지 않았습니다.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
        startForeground(1, notificationBuilder.build())
        lastIpNotification = "DISCONNECTED"
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
        // 12345 포트에서 수신하는 스레드
        Thread {
            try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(12345))  // 첫 번째 포트
                }.use { socket ->
                    val buffer = ByteArray(1024)
                    while (true) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        resetTimer()
                        handleReceivedPacket(packet)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("NetworkScanService", "UDP 브로드캐스트 수신 중 오류 발생: ", e)
            }
        }.start()

        // 5005 포트에서 수신하는 스레드
        Thread {
            try {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(java.net.InetSocketAddress(5005))  // 두 번째 포트
                }.use { socket ->
                    val buffer = ByteArray(1024)
                    while (true) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        resetTimer()
                        handleReceivedPacketSecondary(packet)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e("NetworkScanService", "UDP 브로드캐스트 수신 중 오류 발생: ", e)
            }
        }.start()
    }

    private fun resetTimer() {
        synchronized(this) {
            try {
                timer?.cancel()
                timer?.purge()
            } catch (e: IllegalStateException) {
                Log.e("NetworkScanService", "Timer cancel error: ", e)
            }
        }
        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    if (System.currentTimeMillis() - lastUpdateTime >= 15000) {
                        isDisconnected = true
                        updateConnectionStatus(false)
                    }
                }
            }, 1000)
        }
    }

    private fun updateConnectionStatus(isConnected: Boolean) {
        val ipIntent = Intent("UPDATE_IP_ADDRESS")
        if (isConnected) {
            val ip = readIpFromCache() ?: "Unknown IP"
            ipIntent.putExtra("ip_address", ip)
        } else {
            ipIntent.putExtra("ip_address", "DISCONNECTED")
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(ipIntent)
        val lastKnownIP = readIpFromCache()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (isConnected && !lastKnownIP.isNullOrEmpty()) {
            updateNotification(lastKnownIP)
        } else if (!isConnected && lastIpNotification != "DISCONNECTED") {
            val notificationBuilder = Notification.Builder(this, "service_channel").apply {
                setContentTitle("용굴라이더와 연결되지 않았습니다.")
                setSmallIcon(android.R.drawable.stat_notify_sync)
            }
            startForeground(1, notificationBuilder.build())
            lastIpNotification = "DISCONNECTED"
        }
    }

    private fun handleReceivedPacket(packet: DatagramPacket) {
        val receivedText = String(packet.data, 0, packet.length).trim()
        Log.d("NetworkScanService", "Received UDP packet: $receivedText")
        val parts = receivedText.split(" - ")
        if (parts.size == 2) {
            val ipInfo = parts[0].substring(3).trim()
            val status = parts[1].trim()

            Log.d("NetworkScanService", "IP: $ipInfo, Status: $status")

            saveIpToCache(ipInfo)
            val ipIntent = Intent("UPDATE_IP_ADDRESS").apply { putExtra("ip_address", ipInfo) }
            LocalBroadcastManager.getInstance(this).sendBroadcast(ipIntent)
            updateNotification(ipInfo)
            val recIntent = Intent("UPDATE_RECORDING_STATUS").apply { putExtra("recording_status", status) }
            LocalBroadcastManager.getInstance(this).sendBroadcast(recIntent)
        }
    }

    private fun handleReceivedPacketSecondary(packet: DatagramPacket) {
        val receivedText = String(packet.data, 0, packet.length).trim()
        Log.d("NetworkScanService", "Received UDP packet on port 5005: $receivedText")

        synchronized(this) {
            val currentTime = System.currentTimeMillis()
            if (receivedMessages[receivedText] == null || currentTime - receivedMessages[receivedText]!! > 1000) {
                receivedMessages[receivedText] = currentTime

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
                        sendBlinkerStatusBroadcast(status)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("NetworkScanService", "Error parsing JSON: ", e)
                }
            }
        }
    }

    private fun sendBlinkerStatusBroadcast(status: String) {
        val blinkerIntent = Intent("UPDATE_BLINKER_STATUS").apply {
            putExtra("blinker_status", status)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(blinkerIntent)
    }

    private fun updateNotification(ipAddress: String?) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime < 1000) return
        lastUpdateTime = currentTime
        val ipPart = ipAddress?.split("-")?.first()?.trim()
        if (lastIpNotification == ipPart) return
        lastIpNotification = ipPart
        val lastKnownIP = readIpFromCache()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationBuilder = Notification.Builder(this, "service_channel").apply {
            if (!lastKnownIP.isNullOrEmpty()) {
                setContentTitle("용굴라이더와 연결됨")
                setContentText("연결된 IP: $lastKnownIP")
                setSmallIcon(android.R.drawable.stat_notify_sync)
            } else {
                setContentTitle("용굴라이더와 연결되지 않았습니다.")
                    .setSmallIcon(android.R.drawable.stat_notify_sync)
            }
        }
        startForeground(1, notificationBuilder.build())
    }

    private fun saveIpToCache(ipAddress: String) {
        File(cacheDir, "last_ip_address").writeText(ipAddress)
    }

    private fun readIpFromCache(): String? {
        return try {
            File(cacheDir, "last_ip_address").readText()
        } catch (e: IOException) {
            Log.e("NetworkScanService", "Error reading IP from cache", e)
            null
        }
    }

    private fun isServerReachable(ipAddress: String): Boolean {
        try {
            val process = Runtime.getRuntime().exec("/system/bin/ping -c 1 $ipAddress")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val output = StringBuilder()
            while (reader.readLine().also { line = it } != null) {
                output.append(line + "\n")
            }
            reader.close()

            Log.d("Ping", "Ping output: $output")

            val exitVal = process.waitFor()
            return (exitVal == 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
    }
}
