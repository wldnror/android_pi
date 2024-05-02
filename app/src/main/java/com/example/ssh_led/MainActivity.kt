package com.example.ssh_led

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.animation.Animation
import android.view.animation.AnimationUtils

class MainActivity : AppCompatActivity() {
    private lateinit var ipAddressTextView: TextView
    private lateinit var recButton: Button
    private var isRecording = false  // 녹화 상태를 추적하는 플래그
    private lateinit var blinkAnimation: Animation

    private val ipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ip = intent?.getStringExtra("ip_address")
            ipAddressTextView.text = ip ?: "IP 주소를 받아오지 못했습니다."
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

        ipAddressTextView = findViewById(R.id.ipAddressTextView)
        recButton = findViewById(R.id.recButton)

        blinkAnimation = AnimationUtils.loadAnimation(this, R.anim.blink)

        recButton.setOnClickListener {
            isRecording = !isRecording
            handleRecording()
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            ipReceiver, IntentFilter("UPDATE_IP_ADDRESS")
        )
        LocalBroadcastManager.getInstance(this).registerReceiver(
            recordingStatusReceiver, IntentFilter("UPDATE_RECORDING_STATUS")
        )

        sendSignal("CHECK_STATUS")

        Intent(this, NetworkScanService::class.java).also {
            startForegroundService(it)
        }
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
    }

    private fun sendSignal(signal: String) {
        val intent = Intent(this, NetworkScanService::class.java)
        intent.putExtra("signal", signal)
        startService(intent)
    }
}
