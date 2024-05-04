package com.example.ssh_led

import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ProgressBar
import android.view.View

class MainActivity : AppCompatActivity() {
    private lateinit var ipAddressTextView: TextView
    private lateinit var recButton: Button
    private lateinit var progressBar: ProgressBar
    private var isRecording = false
    private lateinit var blinkAnimation: Animation
    private var hasIP = false
    private val ipUpdateHandler = Handler()
    private lateinit var ipUpdateRunnable: Runnable

    private val ipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ip = intent?.getStringExtra("ip_address")
            ipAddressTextView.text = ip ?: "IP 주소를 받아오지 못했습니다."
            progressBar.visibility = View.GONE  // IP 주소를 받으면 ProgressBar 숨김
            hasIP = true
            ipUpdateHandler.removeCallbacks(ipUpdateRunnable)
        }
    }

    private val recordingStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isRecordingUpdate = intent?.getStringExtra("recording_status") == "RECORDING"
            updateRecordingUI(isRecordingUpdate)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sendSignal("REQUEST_RECORDING_STATUS")
        ipAddressTextView = findViewById(R.id.ipAddressTextView)
        recButton = findViewById(R.id.recButton)
        progressBar = findViewById(R.id.progressBar) // ProgressBar 인스턴스화
        blinkAnimation = AnimationUtils.loadAnimation(this, R.anim.blink)

        recButton.setOnClickListener {
            isRecording = !isRecording
            handleRecording()
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(ipReceiver, IntentFilter("UPDATE_IP_ADDRESS"))
        LocalBroadcastManager.getInstance(this).registerReceiver(recordingStatusReceiver, IntentFilter("UPDATE_RECORDING_STATUS"))

        ipUpdateRunnable = Runnable {
            if (!hasIP) {
                progressBar.visibility = View.VISIBLE  // IP 주소 요청 시 ProgressBar 표시
                sendSignal("REQUEST_IP")
                ipUpdateHandler.postDelayed(ipUpdateRunnable, 3000)
            }
        }
        ipUpdateHandler.post(ipUpdateRunnable)

        Intent(this, NetworkScanService::class.java).also {
            startForegroundService(it)
        }
        checkAndRequestNotificationPermission()
    }

    private fun handleRecording() {
        recButton.isSelected = isRecording
        if (isRecording) {
            recButton.startAnimation(blinkAnimation)
            sendSignal("START_RECORDING")
        } else {
            recButton.clearAnimation()
            sendSignal("STOP_RECORDING")
        }
    }

    private fun updateRecordingUI(isRecordingUpdate: Boolean) {
        if (isRecording != isRecordingUpdate) {
            isRecording = isRecordingUpdate
            recButton.isSelected = isRecording
            if (isRecording) {
                recButton.startAnimation(blinkAnimation)
            } else {
                recButton.clearAnimation()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(ipReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(recordingStatusReceiver)
        ipUpdateHandler.removeCallbacks(ipUpdateRunnable)
    }

    private fun sendSignal(signal: String) {
        val intent = Intent(this, NetworkScanService::class.java)
        intent.putExtra("signal", signal)
        startService(intent)
    }

    private fun checkAndRequestNotificationPermission() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
}
