package com.example.ssh_led

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class MainActivity : AppCompatActivity() {
    private lateinit var ipAddressTextView: TextView
    private lateinit var buttonGrid: GridLayout
    private var isRecording = false  // 녹화 상태를 추적하는 플래그

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
        buttonGrid = findViewById(R.id.buttonGrid)
        val recButton: Button = findViewById(R.id.recButton)

        recButton.setOnClickListener {
            isRecording = !isRecording
            recButton.isSelected = isRecording // 버튼의 선택 상태 업데이트
            if (isRecording) {
                startRecording()  // 녹화 시작
            } else {
                stopRecording()   // 녹화 중지
            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            ipReceiver, IntentFilter("UPDATE_IP_ADDRESS")
        )

        // 동적으로 버튼 생성 및 이벤트 처리
        for (i in 1..12) {
            val button = Button(this).apply {
                text = if (i == 3) "REC" else "버튼 $i"
                setBackgroundColor(if (i == 3) Color.WHITE else Color.LTGRAY)
                setTextColor(if (i == 3 && isRecording) Color.BLACK else Color.DKGRAY)
                setOnClickListener {
                    if (i == 3) {
                        toggleRecording()  // REC 버튼 클릭 시 녹화 토글
                    } else {
                        sendSignal(i.toString().padStart(4, '0'))
                    }
                }
            }
            buttonGrid.addView(button)
        }

        Intent(this, NetworkScanService::class.java).also {
            startForegroundService(it)
        }
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        updateRecButton()
        if (isRecording) {
            startRecording()  // 녹화 시작 로직
        } else {
            stopRecording()   // 녹화 중지 로직
        }
    }

    private fun updateRecButton() {
        val recButton = buttonGrid.getChildAt(2) as Button  // REC 버튼 찾기 (3번 버튼)
        recButton.setBackgroundColor(if (isRecording) Color.RED else Color.WHITE)
        recButton.setTextColor(Color.BLACK)
    }

    private fun startRecording() {
        sendSignal("0003")
        // 여기에 녹화 시작을 위한 로직 구현
    }

    private fun stopRecording() {
        sendSignal("0003")
        // 여기에 녹화 중지를 위한 로직 구현
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
