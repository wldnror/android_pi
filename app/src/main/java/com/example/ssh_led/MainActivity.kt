package com.example.ssh_led

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class MainActivity : AppCompatActivity() {
    private lateinit var ipAddressTextView: TextView // 이 부분이 TextView의 참조를 제대로 선언합니다.

    private val ipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ip = intent?.getStringExtra("ip_address")
            ipAddressTextView.text = "라즈베리 파이 IP 주소: $ip"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ipAddressTextView = findViewById(R.id.ipAddressTextView)  // 여기에서 TextView를 찾아서 초기화합니다.

        LocalBroadcastManager.getInstance(this).registerReceiver(
            ipReceiver, IntentFilter("UPDATE_IP_ADDRESS")
        )

        Intent(this, NetworkScanService::class.java).also {
            startForegroundService(it)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(ipReceiver)
    }
}
