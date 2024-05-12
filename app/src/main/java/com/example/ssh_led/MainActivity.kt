package com.example.ssh_led

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
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
import android.widget.ToggleButton

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
    private lateinit var toggleModeButton: ToggleButton


    // 스와이프 감지를 위한 변수
    private var x1: Float = 0.0f
    private var x2: Float = 0.0f
    private var y1: Float = 0.0f
    private var y2: Float = 0.0f
    private val MIN_DISTANCE = 150 // 최소 스와이프 거리

    private val ipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val ip = intent?.getStringExtra("ip_address")
            if (ip == "DISCONNECTED") {
                hasIP = false
                updateRecordingUI(isRecording)  // 연결이 끊겼을 때 UI 업데이트
                startIpSearchAnimation()  // IP 검색 애니메이션을 계속 실행
            } else if (ip != null) {
                ipAddressTextView.text = ip  // 연결된 IP 주소 업데이트
                hasIP = true
                updateRecordingUI(isRecording)  // 연결이 되었을 때 UI 업데이트
            }
        }
    }

    private var isAnimating = false // 애니메이션 상태를 저장하는 변수

    private fun startIpSearchAnimation() {
        ipUpdateHandler.removeCallbacks(ipUpdateRunnable)
        ipUpdateRunnable = object : Runnable {
            override fun run() {
                if (!hasIP) {
                    if (loadingDots.length >= 3) loadingDots = ""
                    loadingDots += "."
                    ipAddressTextView.text = "IP 주소를 검색 중입니다$loadingDots"
                    ipUpdateHandler.postDelayed(this, 550)  // 계속해서 애니메이션을 유지
                }
            }
        }
        ipUpdateHandler.post(ipUpdateRunnable)
    }


    private val recordingStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isRecordingUpdate = intent?.getStringExtra("recording_status")
            if (isRecordingUpdate == "RECORDING" || isRecordingUpdate == "NOT_RECORDING") {
                updateRecordingUI(isRecordingUpdate == "RECORDING")
            }
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
        sendSignal("Right Blinker Activated", true)
    }

    fun onLeftBlinkerClicked(view: View) {
        blinkerSequence(R.id.left_blinker_orange_1, R.id.left_blinker_orange_2, R.id.left_blinker_orange_3)
        sendSignal("Left Blinker Activated", true)
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
        toggleModeButton = findViewById(R.id.toggleModeButton)
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
            if (!hasIP) {
                // 서버와 연결되어 있지 않을 경우
                Toast.makeText(this, "용굴라이더와 연결 후 시도해 주세요.", Toast.LENGTH_LONG).show()
                recButton.clearAnimation()  // 서버와 연결이 없으면 애니메이션 중지
            } else {
                // 서버와 연결되어 있을 경우
                isRecording = !isRecording
                handleRecording()
                sendSignal(if (isRecording) "START_RECORDING" else "STOP_RECORDING", true)
            }
        }

        hornImageView.setOnClickListener {
            playSelectedSound(selectedSoundId)
        }

        // 토글 버튼 상태 변화에 대한 리스너 설정
        toggleModeButton.setOnCheckedChangeListener { _, isChecked ->
            val modeSignal = if (isChecked) "ENABLE_MANUAL_MODE" else "ENABLE_AUTO_MODE"
            sendSignal(modeSignal, true)  // 변경된 모드 신호를 서비스로 보냄
            Toast.makeText(this, if (isChecked) "수동 모드로 전환됩니다." else "자동 모드로 전환됩니다.", Toast.LENGTH_SHORT).show()
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


    // 스와이프 리스너 설정
        findViewById<View>(R.id.main_layout).setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    x1 = event.x
                    y1 = event.y
                    true
                }
                MotionEvent.ACTION_UP -> {
                    x2 = event.x
                    y2 = event.y
                    if (!isTouchInsideHornButton(x1, y1)) {
                        handleSwipe(x1, y1, x2, y2)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun isTouchInsideHornButton(x: Float, y: Float): Boolean {
        val location = IntArray(2)
        hornImageView.getLocationOnScreen(location)
        val xStart = location[0]
        val yStart = location[1]
        val xEnd = xStart + hornImageView.width
        val yEnd = yStart + hornImageView.height

        return x >= xStart && x <= xEnd && y >= yStart && y <= yEnd
    }

    private fun handleSwipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val deltaX = x2 - x1
        val deltaY = y2 - y1

        if (Math.abs(deltaX) > Math.abs(deltaY)) {
            if (deltaX > 0) {
                onHorizontalSwipeRight()
            } else {
                onHorizontalSwipeLeft()
            }
        } else {
            if (deltaY > 0) {
                onVerticalSwipeDown()
            } else {
                onVerticalSwipeUp()
            }
        }

        // 대각선 스와이프 추가
        if (Math.abs(deltaX) > MIN_DISTANCE && Math.abs(deltaY) > MIN_DISTANCE) {
            if (deltaX > 0 && deltaY > 0) {
                onDiagonalSwipeBottomRight()
            } else if (deltaX > 0 && deltaY < 0) {
                onDiagonalSwipeTopRight()
            } else if (deltaX < 0 && deltaY > 0) {
                onDiagonalSwipeBottomLeft()
            } else if (deltaX < 0 && deltaY < 0) {
                onDiagonalSwipeTopLeft()
            }
        }
    }

    private fun onHorizontalSwipeRight() {
//        Toast.makeText(this, "오른쪽으로 스와이프 감지됨", Toast.LENGTH_SHORT).show()
        blinkerSequence(R.id.right_blinker_orange_1, R.id.right_blinker_orange_2, R.id.right_blinker_orange_3)
        sendSignal("Right Blinker Activated", true)
    }

    private fun onHorizontalSwipeLeft() {
//        Toast.makeText(this, "왼쪽으로 스와이프 감지됨", Toast.LENGTH_SHORT).show()
        blinkerSequence(R.id.left_blinker_orange_1, R.id.left_blinker_orange_2, R.id.left_blinker_orange_3)
        sendSignal("Left Blinker Activated", true)
    }

    private fun onVerticalSwipeUp() {
        // 위로 수직 스와이프할 때의 반응
//        Toast.makeText(this, "위로 스와이프 감지됨", Toast.LENGTH_SHORT).show()
    }

    private fun onVerticalSwipeDown() {
        // 아래로 수직 스와이프할 때의 반응
//        Toast.makeText(this, "아래로 스와이프 감지됨", Toast.LENGTH_SHORT).show()
    }

    // 대각선 스와이프에 대한 예제 함수
    private fun onDiagonalSwipeBottomRight() {
        // 오른쪽 아래 대각선 방향으로 스와이프할 때의 반응
//        Toast.makeText(this, "오른쪽 아래로 스와이프 감지됨", Toast.LENGTH_SHORT).show()
        blinkerSequence(R.id.right_blinker_orange_1, R.id.right_blinker_orange_2, R.id.right_blinker_orange_3)
        sendSignal("Right Blinker Activated", true)
    }

    private fun onDiagonalSwipeTopRight() {
        // 오른쪽 위 대각선 방향으로 스와이프할 때의 반응
//        Toast.makeText(this, "오른쪽 위로 스와이프 감지됨", Toast.LENGTH_SHORT).show()
        blinkerSequence(R.id.right_blinker_orange_1, R.id.right_blinker_orange_2, R.id.right_blinker_orange_3)
        sendSignal("Right Blinker Activated", true)
    }

    private fun onDiagonalSwipeBottomLeft() {
        // 왼쪽 아래 대각선 방향으로 스와이프할 때의 반응
//        Toast.makeText(this, "왼쪽 아래로 스와이프 감지됨", Toast.LENGTH_SHORT).show()
        blinkerSequence(R.id.left_blinker_orange_1, R.id.left_blinker_orange_2, R.id.left_blinker_orange_3)
        sendSignal("Left Blinker Activated", true)
    }

    private fun onDiagonalSwipeTopLeft() {
        // 왼쪽 위 대각선 방향으로 스와이프할 때의 반응
        blinkerSequence(R.id.left_blinker_orange_1, R.id.left_blinker_orange_2, R.id.left_blinker_orange_3)
        sendSignal("Left Blinker Activated", true)
    }

    private fun registerReceivers() {
        LocalBroadcastManager.getInstance(this).registerReceiver(ipReceiver, IntentFilter("UPDATE_IP_ADDRESS"))
        LocalBroadcastManager.getInstance(this).registerReceiver(recordingStatusReceiver, IntentFilter("UPDATE_RECORDING_STATUS"))
    }

    private fun updateIpSearchStatus() {
        val status = "IP 주소를 검색 중입니다$loadingDots"
        ipAddressTextView.text = status
        // 상태를 SharedPreferences에 저장
        prefs.edit().putString("ip_search_status", status).apply()
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
            Toast.makeText(this, "녹화를 시작 합니다.", Toast.LENGTH_SHORT).show()
            sendSignal("START_RECORDING", true)
        } else {
            recButton.clearAnimation()
            Toast.makeText(this, "녹화를 중지 합니다.", Toast.LENGTH_SHORT).show()
            sendSignal("STOP_RECORDING", true)
        }
    }

    private fun updateRecordingUI(isRecordingUpdate: Boolean) {
        if (hasIP) {
            if (isRecording != isRecordingUpdate) {
                isRecording = isRecordingUpdate
                recButton.isSelected = isRecording
                if (isRecording) {
                    recButton.startAnimation(blinkAnimation)
                } else {
                    recButton.clearAnimation()
                }
            }
        } else {
            // 서버와 연결이 끊겼을 경우 녹화 중지 상태로 리셋
            isRecording = false  // 녹화 상태를 중지로 설정
            recButton.isSelected = false  // 버튼의 선택 상태를 비활성화
            recButton.clearAnimation()  // 애니메이션 중지
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

    private fun sendSignal(signal: String, userAction: Boolean = false) {
        val intent = Intent(this, NetworkScanService::class.java).apply {
            putExtra("signal", signal)
            putExtra("userAction", userAction)
        }
        startService(intent)
    }

    private fun checkAndRequestNotificationPermission() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
}
