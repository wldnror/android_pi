package com.example.ssh_led

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var rightBlinker1: ImageView
    private lateinit var rightBlinker2: ImageView
    private lateinit var rightBlinker3: ImageView
    private lateinit var leftBlinker1: ImageView
    private lateinit var leftBlinker2: ImageView
    private lateinit var leftBlinker3: ImageView

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

    // 블링커 상태 업데이트를 위한 수신기 추가
    private val blinkerStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val blinkerStatus = intent?.getStringExtra("blinker_status")
            if (blinkerStatus != null) {
                handleBlinkerStatus(blinkerStatus)
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
                    ipUpdateHandler.postDelayed(this, 800)  // 계속해서 애니메이션을 유지
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

        // 수신기 등록
        LocalBroadcastManager.getInstance(this).registerReceiver(blinkerStatusReceiver, IntentFilter("UPDATE_BLINKER_STATUS"))

        // 배터리 최적화 중지 요청
        checkBatteryOptimization()
    }

    // 블링커 상태 처리 메서드
    private fun handleBlinkerStatus(blinkerStatus: String) {
        when (blinkerStatus) {
            "RIGHT_ON" -> {
                scope.launch {
                    setBlinkerVisibility(View.GONE, leftBlinker1, leftBlinker2, leftBlinker3)
                    blinkerSequence(rightBlinker1, rightBlinker2, rightBlinker3)
                }
            }
            "RIGHT_OFF" -> {
                setBlinkerVisibility(View.GONE, rightBlinker1, rightBlinker2, rightBlinker3)
            }
            "LEFT_ON" -> {
                scope.launch {
                    setBlinkerVisibility(View.GONE, rightBlinker1, rightBlinker2, rightBlinker3)
                    blinkerSequence(leftBlinker1, leftBlinker2, leftBlinker3)
                }
            }
            "LEFT_OFF" -> {
                setBlinkerVisibility(View.GONE, leftBlinker1, leftBlinker2, leftBlinker3)
            }
        }
    }

    private fun setBlinkerVisibility(visibility: Int, vararg blinkers: ImageView) {
        blinkers.forEach { it.visibility = visibility }
    }

    private fun setupLoadingAnimation() {
        ipUpdateHandler = Handler(Looper.getMainLooper())
        ipUpdateRunnable = object : Runnable {
            override fun run() {
                if (!hasIP) {
                    if (loadingDots.length >= 3) loadingDots = ""
                    loadingDots += "."
                    ipAddressTextView.text = "IP 주소를 검색 중입니다$loadingDots"
                    ipUpdateHandler.postDelayed(this, 200)
                }
            }
        }
        ipUpdateHandler.post(ipUpdateRunnable)
    }

    fun onRightBlinkerClicked(view: View) {
        scope.launch {
            setBlinkerVisibility(View.GONE, leftBlinker1, leftBlinker2, leftBlinker3)
            blinkerSequence(rightBlinker1, rightBlinker2, rightBlinker3)
        }
        sendSignal("Right Blinker Activated", true)
    }

    fun onLeftBlinkerClicked(view: View) {
        scope.launch {
            setBlinkerVisibility(View.GONE, rightBlinker1, rightBlinker2, rightBlinker3)
            blinkerSequence(leftBlinker1, leftBlinker2, leftBlinker3)
        }
        sendSignal("Left Blinker Activated", true)
    }

    private suspend fun blinkerSequence(vararg blinkers: ImageView) {
        val blinkCount = 1 // 깜빡일 횟수
        val delayTime = 50L // 각 깜빡임 사이의 지연 시간 (밀리초)
        val offTime = 0L // 꺼져 있는 시간 (밀리초)

        for (i in 0 until blinkCount) {
            blinkers.forEach {
                showAndHide(it, delayTime)
            }
            delay(offTime) // 전체 LED가 꺼져 있는 시간
        }
    }

    private suspend fun showAndHide(view: ImageView, delayTime: Long) {
        view.visibility = View.VISIBLE
        delay(delayTime)
        view.visibility = View.GONE
    }

    private fun setupViews() {
        ipAddressTextView = findViewById(R.id.ipAddressTextView)
        recButton = findViewById(R.id.recButton)
        progressBar = findViewById(R.id.progressBar)
        hornImageView = findViewById(R.id.horn_button)
        toggleModeButton = findViewById(R.id.toggleModeButton)
        blinkAnimation = AnimationUtils.loadAnimation(this, R.anim.blink)

        // 블링커 이미지들을 초기화
        rightBlinker1 = findViewById(R.id.right_blinker_orange_1)
        rightBlinker2 = findViewById(R.id.right_blinker_orange_2)
        rightBlinker3 = findViewById(R.id.right_blinker_orange_3)
        leftBlinker1 = findViewById(R.id.left_blinker_orange_1)
        leftBlinker2 = findViewById(R.id.left_blinker_orange_2)
        leftBlinker3 = findViewById(R.id.left_blinker_orange_3)

        // 블링커 이미지들을 초기에 보이지 않도록 설정
        rightBlinker1.visibility = View.GONE
        rightBlinker2.visibility = View.GONE
        rightBlinker3.visibility = View.GONE
        leftBlinker1.visibility = View.GONE
        leftBlinker2.visibility = View.GONE
        leftBlinker3.visibility = View.GONE
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
        scope.launch {
            setBlinkerVisibility(View.GONE, leftBlinker1, leftBlinker2, leftBlinker3)
            blinkerSequence(rightBlinker1, rightBlinker2, rightBlinker3)
        }
        sendSignal("Right Blinker Activated", true)
    }

    private fun onHorizontalSwipeLeft() {
        scope.launch {
            setBlinkerVisibility(View.GONE, rightBlinker1, rightBlinker2, rightBlinker3)
            blinkerSequence(leftBlinker1, leftBlinker2, leftBlinker3)
        }
        sendSignal("Left Blinker Activated", true)
    }

    private fun onVerticalSwipeUp() {
        // 위로 수직 스와이프할 때의 반응
    }

    private fun onVerticalSwipeDown() {
        // 아래로 수직 스와이프할 때의 반응
    }

    // 대각선 스와이프에 대한 예제 함수
    private fun onDiagonalSwipeBottomRight() {
        scope.launch {
            setBlinkerVisibility(View.GONE, leftBlinker1, leftBlinker2, leftBlinker3)
            blinkerSequence(rightBlinker1, rightBlinker2, rightBlinker3)
        }
        sendSignal("Right Blinker Activated", true)
    }

    private fun onDiagonalSwipeTopRight() {
        scope.launch {
            setBlinkerVisibility(View.GONE, leftBlinker1, leftBlinker2, leftBlinker3)
            blinkerSequence(rightBlinker1, rightBlinker2, rightBlinker3)
        }
        sendSignal("Right Blinker Activated", true)
    }

    private fun onDiagonalSwipeBottomLeft() {
        scope.launch {
            setBlinkerVisibility(View.GONE, rightBlinker1, rightBlinker2, rightBlinker3)
            blinkerSequence(leftBlinker1, leftBlinker2, leftBlinker3)
        }
        sendSignal("Left Blinker Activated", true)
    }

    private fun onDiagonalSwipeTopLeft() {
        scope.launch {
            setBlinkerVisibility(View.GONE, rightBlinker1, rightBlinker2, rightBlinker3)
            blinkerSequence(leftBlinker1, leftBlinker2, leftBlinker3)
        }
        sendSignal("Left Blinker Activated", true)
    }

    private fun registerReceivers() {
        LocalBroadcastManager.getInstance(this).registerReceiver(ipReceiver, IntentFilter("UPDATE_IP_ADDRESS"))
        LocalBroadcastManager.getInstance(this).registerReceiver(recordingStatusReceiver, IntentFilter("UPDATE_RECORDING_STATUS"))
        LocalBroadcastManager.getInstance(this).registerReceiver(blinkerStatusReceiver, IntentFilter("UPDATE_BLINKER_STATUS"))
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
        LocalBroadcastManager.getInstance(this).unregisterReceiver(blinkerStatusReceiver)  // 수신기 해제
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

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val packageName = packageName

        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$packageName")
            startActivityForResult(intent, 101)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                // 사용자에게 배터리 최적화 중지를 허용하지 않았음을 알리는 메시지
                Toast.makeText(this, "배터리 최적화 중지가 필요합니다. 설정에서 직접 변경해 주세요.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
