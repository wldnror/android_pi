package com.example.ssh_led

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {
    private lateinit var ipAddressTextView: TextView
    private lateinit var buttonGrid: GridLayout

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

        LocalBroadcastManager.getInstance(this).registerReceiver(
            ipReceiver, IntentFilter("UPDATE_IP_ADDRESS")
        )

        // 버튼 동적으로 생성 및 이벤트 처리
        for (i in 1..12) {
            val button = Button(this).apply {
                text = "버튼 $i"
                setOnClickListener { sendSignal(i.toString().padStart(4, '0')) }
            }
            buttonGrid.addView(button)
        }

        Intent(this, NetworkScanService::class.java).also {
            startForegroundService(it)
        }
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
