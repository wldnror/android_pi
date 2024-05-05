package com.example.ssh_led

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.content.BroadcastReceiver
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import com.google.android.material.navigation.NavigationView
import android.content.SharedPreferences
import android.widget.Toast
import kotlinx.coroutines.*
import android.os.Looper
import android.widget.ProgressBar


class MainActivity : AppCompatActivity() {
    private val scope = MainScope()

    private lateinit var ipAddressTextView: TextView
    private lateinit var recButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var hornImageView: ImageView
    private lateinit var blinkAnimation: Animation
    private var isRecording = false
    private var hasIP = false
    private lateinit var ipUpdateHandler: Handler
    private lateinit var ipUpdateRunnable: Runnable
    private var mediaPlayer: MediaPlayer? = null
    private var selectedSoundId: Int? = null
    private lateinit var prefs: SharedPreferences
    private var loadingDots = ""

    private val ipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ip = intent?.getStringExtra("ip_address")
            ipAddressTextView.text = ip ?: "IP 주소를 받아오지 못했습니다."
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

        prefs = getSharedPreferences("SoundPreferences", Context.MODE_PRIVATE)
        selectedSoundId = prefs.getInt("selected_sound_id", -1)

        setupViews()
        setupListeners()
        registerReceivers()
        initiateServices()
        setupLoadingAnimation()
    }

    private fun setupLoadingAnimation() {
        ipUpdateHandler = Handler(Looper.getMainLooper())
        ipUpdateRunnable = object : Runnable {
            override fun run() {
                if (!hasIP) {
                    if (loadingDots.length >= 3) loadingDots = ""
                    loadingDots += "."
                    ipAddressTextView.text = "IP 주소를 검색 중입니다$loadingDots"
                    ipUpdateHandler.postDelayed(this, 500)
                }
            }
        }
        ipUpdateHandler.post(ipUpdateRunnable)
    }

    fun onRightBlinkerClicked(view: View) {
        blinkerSequence(R.id.right_blinker_orange_1, R.id.right_blinker_orange_2, R.id.right_blinker_orange_3)
    }

    fun onLeftBlinkerClicked(view: View) {
        blinkerSequence(R.id.left_blinker_orange_1, R.id.left_blinker_orange_2, R.id.left_blinker_orange_3)
    }

    private fun blinkerSequence(first: Int, second: Int, third: Int) {
        scope.launch {
            showAndHide(first)
            delay(100)
            showAndHide(second)
            delay(100)
            showAndHide(third)
        }
    }

    private suspend fun showAndHide(viewId: Int) {
        findViewById<View>(viewId).apply {
            visibility = View.VISIBLE
            delay(100)
            visibility = View.GONE
        }
    }

    private fun setupViews() {
        ipAddressTextView = findViewById(R.id.ipAddressTextView)
        recButton = findViewById(R.id.recButton)
        progressBar = findViewById(R.id.progressBar)
        hornImageView = findViewById(R.id.horn_button)
        blinkAnimation = AnimationUtils.loadAnimation(this, R.anim.blink)

        // 블링커 이미지들을 초기에 보이지 않도록 설정
        findViewById<ImageView>(R.id.left_blinker_orange_1).visibility = View.GONE
        findViewById<ImageView>(R.id.left_blinker_orange_2).visibility = View.GONE
        findViewById<ImageView>(R.id.left_blinker_orange_3).visibility = View.GONE
        findViewById<ImageView>(R.id.right_blinker_orange_1).visibility = View.GONE
        findViewById<ImageView>(R.id.right_blinker_orange_2).visibility = View.GONE
        findViewById<ImageView>(R.id.right_blinker_orange_3).visibility = View.GONE
    }

    private fun setupListeners() {
        recButton.setOnClickListener {
            isRecording = !isRecording
            handleRecording()
        }

        hornImageView.setOnClickListener {
            playSelectedSound(selectedSoundId)
        }

        val navView: NavigationView = findViewById(R.id.nav_view)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_horn -> {
                    showHornSelectionDialog()
                    true
                }
                else -> false
            }
        }
    }

    private fun registerReceivers() {
        LocalBroadcastManager.getInstance(this).registerReceiver(ipReceiver, IntentFilter("UPDATE_IP_ADDRESS"))
        LocalBroadcastManager.getInstance(this).registerReceiver(recordingStatusReceiver, IntentFilter("UPDATE_RECORDING_STATUS"))
    }

    private fun initiateServices() {
        Intent(this, NetworkScanService::class.java).also {
            startForegroundService(it)
        }
        checkAndRequestNotificationPermission()
    }

    private fun showHornSelectionDialog() {
        val hornSounds = arrayOf("남자 목소리", "여자 목소리", "자동차 클락션", "자전거 경적", "자전거 따르릉")
        val soundFiles = arrayOf(
            R.raw.man_excuse_me_please,
            R.raw.woman_excuse_me_please,
            R.raw.car_horn,
            R.raw.bicycle_horn,
            R.raw.bicycle_bell
        )

        AlertDialog.Builder(this).apply {
            setTitle("경적 선택")
            setSingleChoiceItems(hornSounds, -1) { dialog, which ->
                playSelectedSound(soundFiles[which])
            }
            setPositiveButton("확인") { dialog, which ->
                val selectedPosition = (dialog as AlertDialog).listView.checkedItemPosition
                if (selectedPosition != -1) {
                    selectedSoundId = soundFiles[selectedPosition]
                    prefs.edit().putInt("selected_sound_id", selectedSoundId!!).apply()
                }
            }
            setNegativeButton("취소", null)
            show()
        }
    }

    private fun playSelectedSound(soundId: Int?) {
        if (soundId == null || soundId == -1) {
            // 사운드가 선택되지 않았을 때 경고 표시
            Toast.makeText(this, "선택된 사운드가 없습니다. 먼저 사운드를 선택해주세요.", Toast.LENGTH_SHORT).show()
        } else {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, soundId)
            mediaPlayer?.start()
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
        mediaPlayer?.release()
        mediaPlayer = null
        LocalBroadcastManager.getInstance(this).unregisterReceiver(ipReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(recordingStatusReceiver)
        ipUpdateHandler.removeCallbacks(ipUpdateRunnable)
        scope.cancel() // Cancel coroutines when the activity is destroyed
    }

    private fun sendSignal(signal: String) {
        Intent(this, NetworkScanService::class.java).also { intent ->
            intent.putExtra("signal", signal)
            startService(intent)
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
}
