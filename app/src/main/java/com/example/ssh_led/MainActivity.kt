package com.example.ssh_led

import android.graphics.Color
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
            ipAddressTextView.text = "AI멀티백 IP 주소: $ip"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ipAddressTextView = findViewById(R.id.ipAddressTextView)
        recButton = findViewById(R.id.recButton)

        // 애니메이션 로드
        blinkAnimation = AnimationUtils.loadAnimation(this, R.anim.blink)

        recButton.setOnClickListener {
            isRecording = !isRecording
            handleRecording()
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            ipReceiver, IntentFilter("UPDATE_IP_ADDRESS")
        )

        Intent(this, NetworkScanService::class.java).also {
            startForegroundService(it)
        }
    }

    private fun handleRecording() {
        recButton.isSelected = isRecording // 버튼의 선택 상태 업데이트
        if (isRecording) {
            recButton.startAnimation(blinkAnimation)  // 깜빡임 애니메이션 시작
            startRecording()
        } else {
            recButton.clearAnimation()  // 깜빡임 애니메이션 중지
            stopRecording()
        }
    }

    private fun startRecording() {
        // 녹화 시작 로직
        sendSignal("0003")
    }

    private fun stopRecording() {
        // 녹화 중지 로직
        sendSignal("0003")
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(ipReceiver)
    }

    private fun sendSignal(signal: String) {
        val intent = Intent(this, NetworkScanService::class.java)
        intent.putExtra("signal", signal)
        startService(intent)
    }
}
