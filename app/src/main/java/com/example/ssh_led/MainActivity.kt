package com.example.ssh_led

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MainActivity : AppCompatActivity() {

    private lateinit var ipAddressTextView: TextView
    private val raspberryPiIpAddress = "라즈베리파이의_IP_주소"
    private val udpPort = 12345

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val button: Button = findViewById(R.id.sendButton)
        ipAddressTextView = findViewById(R.id.ipAddressTextView)

        button.setOnClickListener {
            sendUDPBroadcast("Hello Raspberry Pi!", udpPort)
        }
    }

    private fun sendUDPBroadcast(message: String, port: Int) {
        Thread(Runnable {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                val sendData = message.toByteArray()
                val sendPacket = DatagramPacket(
                    sendData,
                    sendData.size,
                    InetAddress.getByName("255.255.255.255"),
                    port
                )
                socket.send(sendPacket)

                // 라즈베리 파이의 IP 주소를 표시
                val raspPiAddress = InetAddress.getByName(raspberryPiIpAddress).hostAddress
                runOnUiThread {
                    ipAddressTextView.text = "라즈베리 파이 IP 주소: $raspPiAddress"
                }

                socket.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }).start()
    }
}
