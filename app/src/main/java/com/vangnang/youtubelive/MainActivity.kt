package com.vangnang.youtubelive

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import android.widget.FrameLayout
import android.widget.TextView
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.FpsListener
import com.vangnang.youtubelive.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var stream: GenericStream? = null
    private var replayEngine: ReplayEngine? = null
    private var generation = 0
    private var prepared = false
    private var cameraReady = false
    private var startWhenCameraReady = false
    private var activeSupport: CameraSupport? = null
    private var lastCameraIssue = "Chưa có lỗi camera."
    private var live = false
    private var front = false
    private var muted = false
    private var active: StreamConfig? = null
    private var destination = Destination.YOUTUBE
    private var cameraFps = 0.0
    private var captureFps = 0.0
    private var sensorState = "Chưa có kết quả cảm biến"
    private var encodedFps = 0
    private var lowFpsSeconds = 0
    private var foreground = false
    private var sessionEntered = false
    private var editTarget = OverlayTarget.SCOREBOARD
    private var fpsChoices = listOf(30)
    @Volatile private var scoreState = ScoreState()
    @Volatile private var sportsLook = SportsLook.LIGHT
    private lateinit var scoreStore: ScoreStore
    private val youtube = YouTubeLiveController()
    private var youtubeAccessToken: String? = null
    private var youtubeChannel: YouTubeChannel? = null
    private var pendingYouTubeAction: (() -> Unit)? = null
    private val scoreHistory = ArrayDeque<ScoreState>()
    private var scorePanel: ScorePanel? = null
    private val brandingHandler = Handler(Looper.getMainLooper())
    private val brandingExecutor = Executors.newSingleThreadExecutor()
    private var brandingToken = 0
    private val youtubeAuthorization = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            youtubeAuthorizationFailed("Anh chưa cấp quyền YouTube.")
            return@registerForActivityResult
        }
        runCatching { youtube.result(this, result.data) }
            .onSuccess { acceptYouTubeAuthorization(it) }
            .onFailure { youtubeAuthorizationFailed(it.message ?: "Không nhận được quyền YouTube.") }
    }
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (sessionEntered && hasPermissions()) prepare() else if (sessionEntered) {
            status("Cần cấp quyền camera và micro.")
            binding.controlsPanel.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        scoreStore = ScoreStore(getSharedPreferences("score_overlay", MODE_PRIVATE))
        scoreState = scoreStore.load().copy(tickerLabel = "")
        sportsLook = SportsLook.fromStored(getSharedPreferences("image_settings", MODE_PRIVATE).getString("sports_look", null))
        spinner(binding.sportsLook, SportsLook.entries.map { it.title })
        binding.sportsLook.setSelection(sportsLook.ordinal)
        binding.sportsLook.onItemSelectedListener = listener {
            setSportsLook(SportsLook.entries[binding.sportsLook.selectedItemPosition.coerceIn(0, SportsLook.entries.lastIndex)])
        }
        binding.cameraZoom.canZoom = {
            sessionEntered && prepared && binding.scoreEditor.visibility != View.VISIBLE &&
                binding.controlsPanel.visibility != View.VISIBLE && replayEngine?.busy != true
        }
        binding.cameraZoom.onZoom = { factor -> zoomCamera(factor) }
        binding.quickAPlus.setOnClickListener { quickPoint(0, 1) }
        binding.quickAMinus.setOnClickListener { quickPoint(0, -1) }
        binding.quickBPlus.setOnClickListener { quickPoint(1, 1) }
        binding.quickBMinus.setOnClickListener { quickPoint(1, -1) }
        binding.quickServeA1.setOnClickListener { quickServe(1, 1) }
        binding.quickServeA2.setOnClickListener { quickServe(1, 2) }
        binding.quickServeB1.setOnClickListener { quickServe(2, 1) }
        binding.quickServeB2.setOnClickListener { quickServe(2, 2) }
        binding.root.viewTreeObserver.addOnGlobalLayoutListener { refreshQuickScores() }
        binding.scoreEditor.snapshot = { editableOverlay(editTarget) }
        binding.scoreEditor.defaultPlacement = { defaultOverlayPlacement(editTarget) }
        binding.scoreEditor.onBegin = { rememberScore() }
        binding.scoreEditor.onChange = { placement ->
            scoreState = when (editTarget) {
                OverlayTarget.SCOREBOARD -> scoreState.withPlacement(placement)
                OverlayTarget.TICKER -> scoreState.withTickerPlacement(placement)
                OverlayTarget.QUICK_CONTROLS -> scoreState.copy(quickControlsPlacement = placement.constrained(quickControlsBounds()))
            }
            if (editTarget == OverlayTarget.QUICK_CONTROLS) applyQuickControlsPlacement()
        }
        binding.scoreEditor.onFinish = { scoreStore.save(scoreState, SystemClock.elapsedRealtime()) }
        binding.menuToggle.setOnClickListener {
            if (binding.controlsPanel.visibility == View.VISIBLE) binding.controlsPanel.visibility = View.GONE
            else showCompactMenu()
        }
        binding.editDone.setOnClickListener { setLayoutEditing(false) }
        binding.quickLive.setOnClickListener { if (live) confirmStop() else startLive() }
        binding.replayQuick.setOnClickListener {
            if (replayEngine?.busy == true) returnFromReplay() else startReplay()
        }
        binding.compactStatus.setOnClickListener { binding.controlsPanel.visibility = View.VISIBLE }
        binding.root.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val size = fitVideoPreview(view.width - view.paddingLeft - view.paddingRight, view.height - view.paddingTop - view.paddingBottom)
            if (size.width > 0 && size.height > 0) {
                val params = binding.videoStage.layoutParams as FrameLayout.LayoutParams
                if (params.width != size.width || params.height != size.height) {
                    params.width = size.width; params.height = size.height
                    binding.videoStage.layoutParams = params
                }
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            WindowInsetsCompat.CONSUMED
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.streamKey.isSaveEnabled = false
        spinner(binding.resolution, Quality.entries.map { it.title })
        spinner(binding.fps, fpsChoices.map { "$it FPS" })
        spinner(binding.replayDuration, listOf("3 giây", "5 giây", "8 giây"))
        spinner(binding.replaySpeed, listOf("Chậm 0,5×", "Chậm 0,25×"))
        spinner(binding.replayTransition, ReplayTransition.entries.map { it.title })
        val replaySettings = loadReplaySettings()
        binding.replayDuration.setSelection(listOf(3, 5, 8).indexOf(replaySettings.seconds))
        binding.replaySpeed.setSelection(if (replaySettings.speed == 0.5) 0 else 1)
        binding.replayTransition.setSelection(replaySettings.transition.ordinal)
        updateReplaySummary(replaySettings)
        binding.resolution.setSelection(1)
        binding.serverUrl.setText(serverFor(destination))
        binding.resolution.onItemSelectedListener = listener { refreshFpsChoices(); invalidateConfig() }
        binding.fps.onItemSelectedListener = listener { invalidateConfig() }
        binding.replayDuration.onItemSelectedListener = listener { saveReplaySettings() }
        binding.replaySpeed.onItemSelectedListener = listener { saveReplaySettings() }
        binding.replayTransition.onItemSelectedListener = listener { saveReplaySettings() }
        binding.preview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { if (sessionEntered && foreground && hasPermissions()) prepare() }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { stream?.getGlInterface()?.setPreviewResolution(width, height) }
            override fun surfaceDestroyed(holder: SurfaceHolder) { dispose(); status("Camera đã tạm dừng.") }
        })
        binding.applySettings.setOnClickListener { prepare() }
        binding.startStop.setOnClickListener { if (live) confirmStop() else startLive() }
        binding.switchCamera.setOnClickListener {
            if (!live) {
                val target = !front
                refreshFpsChoices(target)
                prepare(targetFront = target)
            }
        }
        binding.microphone.setOnClickListener { muted = !muted; applyMute() }
        binding.sportSettings.setOnClickListener { showScorePanel() }
        binding.youtubeCreateLive.setOnClickListener { prepareYouTubeLiveSession(autoStart = false) }
        configureSetup()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    !sessionEntered -> finish()
                    binding.scoreEditor.visibility == View.VISIBLE -> setLayoutEditing(false)
                    binding.controlsPanel.visibility == View.VISIBLE -> binding.controlsPanel.visibility = View.GONE
                    else -> requestSetup()
                }
            }
        })
        showSetup()
    }
    private fun configureSetup() {
        binding.setupPanel.enterCamera.setOnClickListener { enterCamera() }
        binding.setupPanel.youtubeConnect.setOnClickListener { requestYouTubeAuthorization() }
        binding.setupPanel.youtubeReconnect.setOnClickListener { requestYouTubeAuthorization() }
        refreshYouTubeConnection()
    }
    private fun refreshYouTubeConnection() {
        youtube.authorize(this)
            .addOnSuccessListener { result ->
                if (!result.hasResolution()) acceptYouTubeAuthorization(result)
                else showYouTubeConnection(null)
            }
            .addOnFailureListener { showYouTubeConnection(null) }
    }
    private fun requestYouTubeAuthorization(afterConnected: (() -> Unit)? = null) {
        pendingYouTubeAction = afterConnected
        binding.setupPanel.youtubeConnect.isEnabled = false
        binding.setupPanel.youtubeReconnect.isEnabled = false
        binding.setupPanel.youtubeAccountStatus.text = "Đang mở quyền YouTube…"
        youtube.authorize(this)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pendingIntent = result.pendingIntent
                    if (pendingIntent == null) youtubeAuthorizationFailed("Không mở được màn hình cấp quyền.")
                    else youtubeAuthorization.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
                } else acceptYouTubeAuthorization(result)
            }
            .addOnFailureListener { youtubeAuthorizationFailed(it.message ?: "Không kết nối được YouTube.") }
    }
    private fun acceptYouTubeAuthorization(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            youtubeAuthorizationFailed("Google chưa trả về quyền truy cập YouTube.")
            return
        }
        youtubeAccessToken = token
        binding.setupPanel.youtubeAccountStatus.text = "Đang đọc kênh YouTube…"
        lifecycleScope.launch {
            runCatching { youtube.channel(token) }
                .onSuccess { channel ->
                    youtubeChannel = channel
                    showYouTubeConnection(channel)
                    val action = pendingYouTubeAction
                    pendingYouTubeAction = null
                    action?.invoke()
                }
                .onFailure { youtubeAuthorizationFailed(it.message ?: "Không đọc được kênh YouTube.") }
        }
    }
    private fun showYouTubeConnection(channel: YouTubeChannel?) {
        binding.setupPanel.youtubeAccountStatus.text = channel?.let { "Đã kết nối: ${it.title}" }
            ?: "Chưa cấp quyền YouTube"
        binding.setupPanel.youtubeConnect.visibility = if (channel == null) View.VISIBLE else View.GONE
        binding.setupPanel.youtubeReconnect.visibility = if (channel == null) View.GONE else View.VISIBLE
        binding.setupPanel.youtubeConnect.isEnabled = true
        binding.setupPanel.youtubeReconnect.isEnabled = true
    }
    private fun youtubeAuthorizationFailed(message: String) {
        pendingYouTubeAction = null
        showYouTubeConnection(youtubeChannel)
        Toast.makeText(this, message.take(180), Toast.LENGTH_LONG).show()
    }
    private fun prepareYouTubeLiveSession(autoStart: Boolean) {
        requestYouTubeAuthorization {
            val token = youtubeAccessToken ?: return@requestYouTubeAuthorization
            binding.youtubeCreateLive.isEnabled = false
            status("Đang tải các phiên live YouTube…")
            lifecycleScope.launch {
                runCatching { youtube.upcomingBroadcasts(token) }
                    .onSuccess { showYouTubeLiveDialog(token, it, autoStart) }
                    .onFailure { showYouTubeApiError(it) }
                binding.youtubeCreateLive.isEnabled = true
            }
        }
    }
    private fun showYouTubeLiveDialog(
        token: String,
        broadcasts: List<YouTubeBroadcast>,
        autoStart: Boolean
    ) {
        val content = layoutInflater.inflate(R.layout.dialog_youtube_live, null)
        val chooser = content.findViewById<Spinner>(R.id.youtube_broadcast)
        val title = content.findViewById<EditText>(R.id.youtube_title)
        val titleInput = content.findViewById<View>(R.id.youtube_title_input)
        val privacy = content.findViewById<Spinner>(R.id.youtube_privacy)
        val privacyLabel = content.findViewById<TextView>(R.id.youtube_privacy_label)
        chooser.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("＋ TẠO BUỔI PHÁT MỚI") + broadcasts.map { it.title }
        )
        privacy.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("Không công khai", "Công khai", "Riêng tư")
        )
        title.setText(defaultYouTubeTitle())
        privacy.setSelection(1)
        chooser.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val creating = position == 0
                titleInput.visibility = if (creating) View.VISIBLE else View.GONE
                privacy.visibility = if (creating) View.VISIBLE else View.GONE
                privacyLabel.visibility = if (creating) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (autoStart) "Phát trực tiếp YouTube" else "Chọn phiên live YouTube")
            .setView(content)
            .setPositiveButton(if (autoStart) "PHÁT NGAY" else "CHUẨN BỊ", null)
            .setNegativeButton("HỦY", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = chooser.selectedItemPosition
                val newTitle = title.text.toString().trim()
                if (selected == 0 && newTitle.isBlank()) {
                    title.error = "Nhập tiêu đề buổi phát"
                    return@setOnClickListener
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                val privacyOptions = listOf("unlisted", "public", "private")
                val privacyValue = privacyOptions[privacy.selectedItemPosition.coerceIn(0, 2)]
                lifecycleScope.launch {
                    val result = runCatching {
                        if (selected == 0) youtube.createBroadcast(token, newTitle, privacyValue)
                        else youtube.useBroadcast(token, broadcasts[selected - 1])
                    }
                    result.onSuccess { session ->
                        binding.serverUrl.setText(session.serverUrl)
                        binding.streamKey.setText(session.streamKey)
                        dialog.dismiss()
                        binding.controlsPanel.visibility = View.VISIBLE
                        status("Đã sẵn sàng phiên YouTube: ${session.title}")
                        Toast.makeText(this@MainActivity,
                            if (autoStart) "Đã tạo phiên YouTube. Đang bắt đầu phát…"
                            else "Đã chuẩn bị phiên YouTube. Bấm LIVE khi camera sẵn sàng.",
                            Toast.LENGTH_LONG).show()
                        if (autoStart) startLive()
                    }.onFailure {
                        showYouTubeApiError(it)
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    }
                }
            }
        }
        dialog.show()
    }
    private fun defaultYouTubeTitle(): String {
        val time = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        return "Trực tiếp ${scoreState.sport.title} • $time"
    }
    private fun showYouTubeApiError(error: Throwable) {
        val detail = error.message?.take(180).orEmpty()
        status("Chưa chuẩn bị được phiên YouTube.")
        Toast.makeText(this,
            "YouTube chưa thực hiện được${if (detail.isBlank()) "." else ": $detail"}",
            Toast.LENGTH_LONG).show()
    }
    private fun setupSportSelection() = when (binding.setupPanel.setupSport.checkedButtonId) {
        R.id.setup_volleyball -> Sport.VOLLEYBALL
        R.id.setup_pickleball -> Sport.PICKLEBALL
        else -> Sport.FOOTBALL
    }
    private fun setupDestinationSelection() = when (binding.setupPanel.setupDestination.checkedButtonId) {
        R.id.setup_facebook -> Destination.FACEBOOK
        R.id.setup_custom -> Destination.CUSTOM
        else -> Destination.YOUTUBE
    }
    private fun showSetup() {
        val s = binding.setupPanel
        sessionEntered = false
        setLayoutEditing(false)
        dispose()
        scoreState = scoreState.pause(SystemClock.elapsedRealtime())
        scoreStore.save(scoreState, SystemClock.elapsedRealtime())
        s.setupSport.check(when (scoreState.sport) {
            Sport.FOOTBALL -> R.id.setup_football
            Sport.VOLLEYBALL -> R.id.setup_volleyball
            Sport.PICKLEBALL -> R.id.setup_pickleball
        })
        s.setupDestination.check(when (destination) {
            Destination.YOUTUBE -> R.id.setup_youtube
            Destination.FACEBOOK -> R.id.setup_facebook
            Destination.CUSTOM -> R.id.setup_custom
        })
        binding.streamKey.text?.clear()
        binding.controlsPanel.visibility = View.GONE
        binding.videoStage.visibility = View.GONE
        s.root.visibility = View.VISIBLE
        s.root.isFocusableInTouchMode = true
        s.root.requestFocus()
        refreshCompactControls()
    }
    private fun enterCamera() {
        val s = binding.setupPanel
        // Entering preview needs only sport + destination; credentials are requested inside the session.
        destination = setupDestinationSelection()
        val config = quickStartConfig(destination)
        val qualities = sessionQualities(destination)
        spinner(binding.resolution, qualities.map { it.title })
        binding.resolution.setSelection(qualities.indexOf(config.quality))
        refreshFpsChoices(front)
        binding.fps.setSelection(fpsChoices.indexOf(30).coerceAtLeast(0))
        binding.serverUrl.setText(serverFor(destination))
        binding.streamKey.text?.clear()
        // Undo must not cross into another sport/session after returning to the chooser.
        scoreHistory.clear()
        scoreState = setupScore(scoreState, setupSportSelection(), false, SystemClock.elapsedRealtime())
        scoreStore.save(scoreState, SystemClock.elapsedRealtime())
        configureSessionUi()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(s.root.windowToken, 0)
        s.root.clearFocus()
        s.root.visibility = View.GONE
        sessionEntered = true
        binding.videoStage.visibility = View.VISIBLE
        binding.controlsPanel.visibility = View.VISIBLE
        binding.controlsPanel.scrollTo(0, 0)
        refreshCompactControls()
        status("Chưa phát • ${config.label}")
        if (!hasPermissions()) permissions.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        else binding.preview.post { if (sessionEntered && !prepared) prepare() }
    }
    private fun configureSessionUi() {
        binding.sessionTitle.text = "${scoreState.sport.title} • ${destination.title}"
        binding.sportSettings.text = "Cài đặt ${scoreState.sport.title}"
        binding.keyInput.hint = sessionKeyLabel(destination)
        binding.serverInput.visibility = if (sessionShowsServer(destination)) View.VISIBLE else View.GONE
        binding.keyInput.visibility = if (sessionNeedsManualKey(destination)) View.VISIBLE else View.GONE
        binding.youtubeCreateLive.visibility = if (destination == Destination.YOUTUBE) View.VISIBLE else View.GONE
        binding.youtubeCreateLive.text = "CHỌN BUỔI PHÁT CÓ SẴN (TÙY CHỌN)"
        binding.platformHelp.text = when (destination) {
            Destination.FACEBOOK -> "Facebook: tối đa 1080p. Dán key của buổi live."
            Destination.YOUTUBE -> "YouTube không cần nhập Stream Key. Bấm LIVE, kiểm tra tiêu đề rồi chọn PHÁT NGAY."
            Destination.CUSTOM -> "Dán URL máy chủ RTMP/RTMPS và key, không phải link xem video."
        } + if (destination == Destination.YOUTUBE) "\nGiữ ứng dụng mở khi live."
            else "\nKey không được lưu. Giữ ứng dụng mở khi live."
    }
    private fun showScorePanel() {
        setLayoutEditing(false)
        binding.controlsPanel.visibility = View.GONE
        scorePanel?.dismiss()
        scorePanel = ScorePanel(this, { scoreState }, { action ->
            rememberScore(); scoreState = action(scoreState)
            scoreStore.save(scoreState, SystemClock.elapsedRealtime())
            refreshQuickScores()
        }, {
            if (scoreHistory.isNotEmpty()) {
                scoreState = scoreHistory.removeLast()
                scoreStore.save(scoreState, SystemClock.elapsedRealtime())
                refreshQuickScores()
            }
        }).also { it.show() }
    }
    private fun showCompactMenu() {
        PopupMenu(this, binding.menuToggle).apply {
            menu.add(0, 1, 0, "${scoreState.sport.title} • Tỉ số / chữ chạy")
            menu.add(0, 2, 1, "Kéo / phóng bảng tỉ số")
                .isEnabled = replayEngine?.busy != true
            menu.add(0, 13, 2, "Kéo / phóng chữ chạy").isEnabled = replayEngine?.busy != true
            menu.add(0, 14, 3, "Kéo / phóng nút thay đổi điểm").isEnabled = scoreState.quickScoresAvailable() && replayEngine?.busy != true
            menu.add(0, 3, 4, if (binding.controlsPanel.visibility == View.VISIBLE) "Ẩn cài đặt" else "Cài đặt phát / replay")
            menu.add(0, 4, 3, if (muted) "Bật micro" else "Tắt micro")
            menu.add(0, 5, 4, "Đổi camera trước / sau").isEnabled = !live
            menu.add(0, 6, 5, "Chọn lại môn / nơi phát")
            menu.add(0, 7, 6, if (replayEngine?.busy == true) "Dừng replay • Về LIVE" else "Phát lại ngay").isEnabled = live
            menu.add(0, 8, 7, "Zoom camera về 1×").isEnabled = prepared && replayEngine?.busy != true
            menu.add(0, 10, 9, "Thông tin / giấy phép")
            menu.add(0, 11, 2, "Bộ lọc thể thao • ${sportsLook.title}")
            menu.add(0, 12, 10, "Chẩn đoán camera / FPS")
            if (!sessionShowsServer(destination)) menu.add(0, 9, 8, "URL ${destination.title} (nâng cao)").isEnabled = !live
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showScorePanel()
                    2 -> setLayoutEditing(true, OverlayTarget.SCOREBOARD)
                    13 -> setLayoutEditing(true, OverlayTarget.TICKER)
                    14 -> setLayoutEditing(true, OverlayTarget.QUICK_CONTROLS)
                    3 -> binding.controlsPanel.visibility = if (binding.controlsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    4 -> { muted = !muted; applyMute() }
                    5 -> if (!live) {
                        val target = !front
                        refreshFpsChoices(target)
                        prepare(targetFront = target)
                    }
                    6 -> requestSetup()
                    7 -> if (replayEngine?.busy == true) returnFromReplay() else startReplay()
                    8 -> zoomCamera(1f, reset = true)
                    10 -> showAppInformation()
                    11 -> showSportsLook()
                    12 -> showCameraDiagnostics()
                    9 -> {
                        binding.serverInput.visibility = View.VISIBLE
                        binding.controlsPanel.visibility = View.VISIBLE
                        binding.controlsPanel.scrollTo(0, 0)
                    }
                }
                true
            }
        }.show()
    }
    private fun showCameraDiagnostics() {
        val report = runCatching { cameraDiagnosticReport(this, front, selected()) }
            .getOrElse { "Không đọc được metadata: ${it.javaClass.simpleName}" } +
            "\n\nĐang dùng: ${activeSupport?.description ?: "chưa mở camera"}" +
            "\nFPS callback cảm biến: ${String.format(Locale.US, "%.1f", captureFps)}" +
            "\nFPS hình vào bộ lọc: ${String.format(Locale.US, "%.1f", cameraFps)} • mã hóa: ${if (live) encodedFps.toString() else "chưa phát"}" +
            "\nĐã xác nhận cấu hình đang chạy (${active?.label ?: "không có"}): $cameraReady\n$sensorState\nLần lỗi gần nhất:\n$lastCameraIssue"
        val text = android.widget.TextView(this).apply {
            this.text = report; textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
            setPadding(24, 16, 24, 16); setTextIsSelectable(true)
        }
        AlertDialog.Builder(this).setTitle("Chẩn đoán camera / FPS")
            .setView(android.widget.ScrollView(this).apply { addView(text) })
            .setPositiveButton("Sao chép") { _, _ ->
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Camera diagnostic", report))
                Toast.makeText(this, "Đã sao chép báo cáo; không chứa Stream Key.", Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Đóng", null).show()
    }
    private fun returnFromReplay() {
        replayEngine?.cancel()
        status("Đã về LIVE • Đang nạp lại bộ đệm replay", live)
        refreshCompactControls()
    }
    private fun setSportsLook(look: SportsLook) {
        sportsLook = look
        getSharedPreferences("image_settings", MODE_PRIVATE).edit().putString("sports_look", look.name).apply()
        // Never prepare/dispose/restart the camera or clear replay when changing image treatment.
        if (binding.sportsLook.selectedItemPosition != look.ordinal) binding.sportsLook.setSelection(look.ordinal)
    }
    private fun showSportsLook() {
        AlertDialog.Builder(this).setTitle("Thể thao tự nhiên")
            .setSingleChoiceItems(SportsLook.entries.map { it.title }.toTypedArray(), sportsLook.ordinal) { dialog, index ->
                setSportsLook(SportsLook.entries[index])
                Toast.makeText(this, "Bộ lọc: ${sportsLook.title} • Áp dụng ngay cho camera", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }.setNegativeButton("Đóng", null).show()
    }
    private fun showAppInformation() {
        AlertDialog.Builder(this).setTitle("Live Sân Đấu")
            .setItems(arrayOf("Thông tin ứng dụng / quyền nội dung", "Giấy phép phần mềm nguồn mở")) { _, index ->
                val paths = if (index == 0) listOf("about.txt") else listOf(
                    "licenses/THIRD_PARTY.txt", "licenses/Apache-2.0.txt",
                    "licenses/SLF4J-MIT.txt", "licenses/RUNTIME_NOTICES.txt")
                val body = paths.joinToString("\n\n") { path ->
                    runCatching { assets.open(path).bufferedReader().use { it.readText() } }
                        .getOrElse { "Không mở được $path. Xem gói mã nguồn đi kèm." }
                }
                val view = android.widget.TextView(this).apply {
                    text = body; textSize = 14f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
                    val inset = (20 * resources.displayMetrics.density).toInt()
                    setPadding(inset, inset, inset, inset); setTextIsSelectable(true)
                }
                AlertDialog.Builder(this).setTitle(if (index == 0) "Thông tin ứng dụng" else "Giấy phép nguồn mở")
                    .setView(android.widget.ScrollView(this).apply { addView(view) })
                    .setPositiveButton("Đóng", null).show()
            }.show()
    }
    private fun currentReplaySettings() = ReplaySettings(
        listOf(3, 5, 8)[binding.replayDuration.selectedItemPosition.coerceIn(0, 2)],
        if (binding.replaySpeed.selectedItemPosition == 0) 0.5 else 0.25,
        ReplayTransition.entries[binding.replayTransition.selectedItemPosition.coerceIn(0, ReplayTransition.entries.lastIndex)]
    )
    private fun loadReplaySettings(): ReplaySettings {
        val prefs = getSharedPreferences("replay_settings", MODE_PRIVATE)
        return ReplaySettings.safe(prefs.getInt("seconds", 5), prefs.getFloat("speed", 0.5f).toDouble(), prefs.getString("transition", null))
    }
    private fun saveReplaySettings() {
        val settings = currentReplaySettings()
        getSharedPreferences("replay_settings", MODE_PRIVATE).edit()
            .putInt("seconds", settings.seconds).putFloat("speed", settings.speed.toFloat())
            .putString("transition", settings.transition.name).apply()
        updateReplaySummary(settings)
    }
    private fun updateReplaySummary(settings: ReplaySettings) {
        binding.replaySummary.text = "Chạm REPLAY: ${settings.seconds} giây • ${if (settings.speed == 0.5) "0,5×" else "0,25×"}\n${settings.transition.title}"
    }
    private fun startReplay() {
        if (!live) { Toast.makeText(this, "Bắt đầu LIVE rồi chờ bộ đệm replay.", Toast.LENGTH_SHORT).show(); return }
        val engine = replayEngine ?: return
        val settings = currentReplaySettings()
        val error = engine.play(settings.seconds, settings.speed, settings.transition)
        if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        refreshCompactControls()
    }
    private fun confirmStop() {
        AlertDialog.Builder(this).setTitle("Dừng livestream?")
            .setMessage("Luồng đang phát sẽ ngắt. Bạn có thể bắt đầu lại sau.")
            .setNegativeButton("Tiếp tục phát", null).setPositiveButton("Dừng phát") { _, _ -> stopLive() }.show()
    }
    private fun requestSetup() {
        if (!live) showSetup() else AlertDialog.Builder(this).setTitle("Dừng live để chọn lại?")
            .setMessage("Thao tác này sẽ dừng luồng đang phát.")
            .setNegativeButton("Hủy", null).setPositiveButton("Dừng và chọn lại") { _, _ -> showSetup() }.show()
    }
    private fun refreshCompactControls() {
        refreshQuickScores()
        val editing = binding.scoreEditor.visibility == View.VISIBLE
        binding.menuToggle.visibility = if (sessionEntered && !editing) View.VISIBLE else View.GONE
        binding.editDone.visibility = if (sessionEntered && editing) View.VISIBLE else View.GONE
        binding.quickLive.visibility = if (sessionEntered && !editing) View.VISIBLE else View.GONE
        binding.replayQuick.visibility = if (sessionEntered && live && !editing) View.VISIBLE else View.GONE
        binding.replayQuick.text = if (replayEngine?.busy == true) "VỀ LIVE" else "REPLAY"
        binding.replayQuick.contentDescription = if (replayEngine?.busy == true) "Dừng phát lại, trở về camera trực tiếp ngay" else "Phát lại ngay theo cấu hình đã lưu"
        binding.compactStatus.visibility = if (sessionEntered && !editing) View.VISIBLE else View.GONE
        binding.quickLive.text = if (live) "■" else "LIVE"
        binding.quickLive.contentDescription = if (live) "Dừng livestream" else "Bắt đầu livestream"
        val params = binding.quickLive.layoutParams
        val targetWidth = ((if (live) 48 else 80) * resources.displayMetrics.density).toInt()
        if (params.width != targetWidth) { params.width = targetWidth; binding.quickLive.layoutParams = params }
    }
    private fun rememberScore() {
        if (scoreHistory.size >= 60) scoreHistory.removeFirst()
        scoreHistory.addLast(scoreState.pause(SystemClock.elapsedRealtime()))
    }
    private fun refreshQuickScores() {
        val s = scoreState
        val editingQuick = binding.scoreEditor.visibility == View.VISIBLE && editTarget == OverlayTarget.QUICK_CONTROLS
        val show = sessionEntered && s.quickScoresAvailable() &&
            (binding.scoreEditor.visibility != View.VISIBLE || editingQuick) &&
            binding.controlsPanel.visibility != View.VISIBLE && replayEngine?.busy != true
        val visibility = if (show) View.VISIBLE else View.GONE
        if (binding.quickScores.visibility != visibility) binding.quickScores.visibility = visibility
        val a = "${s.teamA} · ${s.scoreA}"
        val b = "${s.teamB} · ${s.scoreB}"
        if (binding.quickTeamA.text.toString() != a) binding.quickTeamA.text = a
        if (binding.quickTeamB.text.toString() != b) binding.quickTeamB.text = b
        val serveVisibility = if (show && s.sport == Sport.PICKLEBALL) View.VISIBLE else View.GONE
        binding.quickServeAControls.visibility = serveVisibility
        binding.quickServeBControls.visibility = serveVisibility
        val activeServe = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.live_green))
        val idleServe = ColorStateList.valueOf(0xFF274558.toInt())
        listOf(
            binding.quickServeA1 to (s.serving == 1 && s.serverNumber == 1),
            binding.quickServeA2 to (s.serving == 1 && s.serverNumber == 2),
            binding.quickServeB1 to (s.serving == 2 && s.serverNumber == 1),
            binding.quickServeB2 to (s.serving == 2 && s.serverNumber == 2)
        ).forEach { (button, active) ->
            val tint = if (active) activeServe else idleServe
            if (button.backgroundTintList != tint) button.backgroundTintList = tint
        }
        val quickButtons = listOf(
            binding.quickAMinus, binding.quickAPlus, binding.quickBMinus, binding.quickBPlus,
            binding.quickServeA1, binding.quickServeA2, binding.quickServeB1, binding.quickServeB2
        )
        quickButtons.forEach { it.isEnabled = !editingQuick }
        binding.quickScores.alpha = if (editingQuick) 0.72f else 1f
        if (show) binding.quickScores.post { applyQuickControlsPlacement() }
    }
    private fun quickPoint(team: Int, delta: Int) {
        if (!sessionEntered || !scoreState.quickScoresAvailable() || replayEngine?.busy == true) return
        rememberScore()
        scoreState = scoreState.changeScore(team, delta)
        scoreStore.save(scoreState, SystemClock.elapsedRealtime())
        refreshQuickScores()
    }
    private fun quickServe(team: Int, server: Int) {
        if (!sessionEntered || scoreState.sport != Sport.PICKLEBALL ||
            !scoreState.quickScoresAvailable() || replayEngine?.busy == true) return
        rememberScore()
        scoreState = scoreState.selectPickleballServe(team, server)
        scoreStore.save(scoreState, SystemClock.elapsedRealtime())
        refreshQuickScores()
    }
    private fun zoomCamera(factor: Float, reset: Boolean = false) {
        if (!prepared || replayEngine?.busy == true) return
        val camera = stream?.videoSource as? LiveCameraSource ?: return
        try {
            val range = camera.getZoomRange()
            val next = nextCameraZoom(if (reset) 1f else camera.getZoom(), if (reset) 1f else factor, range.lower, range.upper)
            camera.setZoom(next)
            binding.cameraZoom.showZoom(camera.getZoom())
        } catch (_: Exception) {
            Toast.makeText(this, "Camera này chưa cho phép zoom ở cấu hình hiện tại.", Toast.LENGTH_SHORT).show()
        }
    }
    private fun quickControlsBounds(): BoardBounds {
        val stageWidth = binding.videoStage.width.takeIf { it > 0 } ?: 1280
        val stageHeight = binding.videoStage.height.takeIf { it > 0 } ?: 720
        val width = binding.quickScores.width.takeIf { it > 0 } ?: (stageWidth * 0.43f).toInt()
        val height = binding.quickScores.height.takeIf { it > 0 } ?: (stageHeight * 0.18f).toInt()
        return BoardBounds(0f, 0f, width * 1280f / stageWidth, height * 720f / stageHeight)
    }
    private fun editableOverlay(target: OverlayTarget): EditableOverlay = when (target) {
        OverlayTarget.SCOREBOARD -> EditableOverlay(scoreState.placement(), scoreState.boardBounds(), target.title)
        OverlayTarget.TICKER -> EditableOverlay(scoreState.tickerPlacementValue(), tickerBounds(), target.title)
        OverlayTarget.QUICK_CONTROLS -> {
            val bounds = quickControlsBounds()
            EditableOverlay((scoreState.quickControlsPlacement ?: defaultQuickControlsPlacement(bounds)).constrained(bounds), bounds, target.title)
        }
    }
    private fun defaultOverlayPlacement(target: OverlayTarget): BoardPlacement = when (target) {
        OverlayTarget.SCOREBOARD -> scoreState.defaultPlacement()
        OverlayTarget.TICKER -> defaultTickerPlacement()
        OverlayTarget.QUICK_CONTROLS -> defaultQuickControlsPlacement(quickControlsBounds())
    }
    private fun applyQuickControlsPlacement() {
        if (binding.videoStage.width <= 0 || binding.videoStage.height <= 0 || binding.quickScores.width <= 0) return
        val bounds = quickControlsBounds()
        val placement = (scoreState.quickControlsPlacement ?: defaultQuickControlsPlacement(bounds)).constrained(bounds)
        binding.quickScores.pivotX = 0f
        binding.quickScores.pivotY = 0f
        binding.quickScores.scaleX = placement.scale
        binding.quickScores.scaleY = placement.scale
        binding.quickScores.x = binding.videoStage.x + placement.x * binding.videoStage.width / 1280f
        binding.quickScores.y = binding.videoStage.y + placement.y * binding.videoStage.height / 720f
    }
    private fun setLayoutEditing(editing: Boolean, target: OverlayTarget = editTarget) {
        if (editing && replayEngine?.busy == true) return
        if (editing) {
            editTarget = target
            scorePanel?.dismiss(); scorePanel = null
            if (target == OverlayTarget.SCOREBOARD) scoreState = scoreState.copy(visible = true)
            binding.controlsPanel.visibility = View.GONE
        } else if (binding.scoreEditor.visibility == View.VISIBLE) {
            scoreStore.save(scoreState, SystemClock.elapsedRealtime())
        }
        binding.scoreEditor.visibility = if (editing) View.VISIBLE else View.GONE
        binding.scoreEditor.invalidate()
        refreshCompactControls()
    }
    private fun spinner(view: android.widget.Spinner, choices: List<String>) {
        view.adapter = ArrayAdapter(this, R.layout.spinner_item, choices).apply { setDropDownViewResource(R.layout.spinner_dropdown_item) }
    }
    private fun listener(action: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = action()
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
    private fun selected() = StreamConfig(destination, sessionQualities(destination).getOrElse(binding.resolution.selectedItemPosition) { Quality.HD }, fpsChoices.getOrElse(binding.fps.selectedItemPosition) { 30 })
    private fun refreshFpsChoices(targetFront: Boolean = front) {
        val quality = sessionQualities(destination).getOrElse(binding.resolution.selectedItemPosition) { Quality.HD }
        val previous = fpsChoices.getOrElse(binding.fps.selectedItemPosition) { 30 }
        fpsChoices = runCatching {
            publishedFpsOptions(this, targetFront, StreamConfig(destination, quality, 30))
        }.getOrDefault(emptyList()).ifEmpty { listOf(30) }
        spinner(binding.fps, fpsChoices.map { "$it FPS" })
        binding.fps.setSelection(fpsChoices.indexOf(previous).takeIf { it >= 0 } ?: fpsChoices.indexOf(30).coerceAtLeast(0))
    }
    private fun startRemoteBranding() {
        val token = ++brandingToken
        fetchRemoteBranding(token)
    }
    private fun fetchRemoteBranding(token: Int) {
        brandingExecutor.execute {
            val branding = runCatching { RemoteBrandingClient.fetch() }.getOrNull()
            runOnUiThread {
                if (token != brandingToken || isDestroyed) return@runOnUiThread
                branding?.let {
                    val label = if (it.enabled) it.label else ""
                    if (scoreState.tickerLabel != label) scoreState = scoreState.copy(tickerLabel = label)
                }
                brandingHandler.postDelayed({ fetchRemoteBranding(token) }, 60_000)
            }
        }
    }
    private fun stopRemoteBranding() {
        brandingToken++
        brandingHandler.removeCallbacksAndMessages(null)
        if (scoreState.tickerLabel.isNotEmpty()) scoreState = scoreState.copy(tickerLabel = "")
    }
    private fun serverFor(value: Destination) = getPreferences(MODE_PRIVATE).getString("server_${value.name}", value.defaultUrl).orEmpty()
    private fun hasPermissions() = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    private fun invalidateConfig() {
        if (live || !sessionEntered) return
        val next = selected()
        // Selecting a spinner is not an instruction to release the running camera.
        if (prepared && active == next) {
            binding.quality.text = "Đang dùng: ${next.label}"
            status(if (cameraReady) "Sẵn sàng • ${next.label}" else "Đang kiểm tra hình camera và FPS thực tế…")
            return
        }
        binding.quality.text = "Đã chọn: ${next.label}" + (active?.let { "\nCamera vẫn chạy: ${it.label}" } ?: "")
        status(next.validate() ?: "Chưa áp dụng. Camera vẫn dùng cấu hình hiện tại; bấm ÁP DỤNG để đổi.")
    }
    private fun prepare(config: StreamConfig = selected(), targetFront: Boolean = front, recovering: Boolean = false): Boolean {
        if (!sessionEntered || live || !foreground || !hasPermissions() || !binding.preview.holder.surface.isValid) return false
        if (prepared && !cameraReady) {
            status("Camera đang khởi tạo; chờ khung hình đầu tiên rồi đổi cấu hình.")
            return false
        }
        // Check support BEFORE releasing preview: an unsupported 60 FPS choice must not freeze it.
        val candidates = try { checkCameraCandidates(this, targetFront, config) }
        catch (error: Exception) {
            lastCameraIssue = (if (error is IllegalArgumentException) error.message else "Không đọc được khả năng camera.").orEmpty()
            status(lastCameraIssue +
                if (prepared) "\nCamera vẫn chạy cấu hình cũ; chưa áp dụng lựa chọn mới." else "\nChọn cấu hình thấp hơn rồi bấm ÁP DỤNG.")
            binding.quality.text = "Đã chọn: ${config.label}\nĐang chạy: ${active?.label ?: "chưa mở camera"} • lựa chọn mới chưa áp dụng"
            binding.controlsPanel.visibility = View.VISIBLE
            return false
        }
        val plan = CameraAttemptPlan(config, targetFront, active, front, recovering, candidates.map { it.mode })
        return prepareNext(plan)
    }

    private fun prepareNext(plan: CameraAttemptPlan, autoStartWhenReady: Boolean = false): Boolean {
        if (!sessionEntered || live || !foreground || !hasPermissions() || !binding.preview.holder.surface.isValid) return false
        val support = CameraSupport(plan.next() ?: return false)
        val config = plan.config
        dispose()
        binding.quality.text = config.label
        val token = generation
        return try {
            val camera = LiveCameraSource(applicationContext, support, config.fps,
                onReady = { ui(token) {
                    cameraReady = true
                    status("Sẵn sàng • ${config.label} • cấu hình ${plan.attempted}/${plan.total}" +
                        if (plan.recovering) "\nĐã khôi phục cấu hình cũ; lựa chọn mới chưa áp dụng." else "")
                    if (startWhenCameraReady) { startWhenCameraReady = false; startLive() }
                } },
                onFps = { measured -> ui(token) { cameraFps = measured; showFps() } },
                onCaptureFps = { measured -> ui(token) { captureFps = measured } },
                onSensorState = { detail -> ui(token) { sensorState = detail } },
                onFailure = { reason -> ui(token) {
                    failPreparation(plan, reason, live, startWhenCameraReady)
                } })
            val current = GenericStream(applicationContext, checker(token), camera, MicrophoneSource())
            stream = current
            current.getGlInterface().autoHandleOrientation = true
            current.getStreamClient().setReTries(5)
            current.getStreamClient().setLogs(false)
            current.setFpsListener(object : FpsListener.Callback {
                override fun onFps(fps: Int) { ui(token) {
                    encodedFps = fps
                    lowFpsSeconds = if (live && fps < config.fps * 0.85) lowFpsSeconds + 1 else 0
                    showFps()
                } }
            })
            check(current.prepareVideo(config.quality.width, config.quality.height, config.bitrate, config.fps, 2, 0))
            current.getGlInterface().forceFpsLimit(config.fps)
            check(current.prepareAudio(44_100, true, 128_000))
            val memory = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val replay = ReplayEngine((if (memory.isLowRamDevice || memory.memoryClass <= 128) 16 else 32) * 1024 * 1024) { message ->
                ui(token) {
                    status(message, live && message.startsWith("Đã về LIVE"))
                    refreshCompactControls()
                    if (live && replayEngine?.busy == false) binding.preview.postDelayed({
                        if (generation == token && live && replayEngine?.busy == false) stream?.requestKeyframe()
                    }, 1300)
                }
            }
            replayEngine = replay
            current.setRecordController(ReplayRecordController(replay))
            // Default RenderMode.ALL burns overlays into both preview and encoded stream.
            // Treat camera first: graphics stay untouched; replay retains its recorded appearance.
            current.getGlInterface().addFilter(SportsLookFilter({ sportsLook }, camera::observeLatchedFrame))
            current.getGlInterface().addFilter(ScoreOverlayFilter { scoreState })
            current.getGlInterface().addFilter(ReplayFilter(replay))
            active = config; activeSupport = support; prepared = true; front = plan.targetFront
            startWhenCameraReady = autoStartWhenReady
            binding.switchCamera.text = if (front) "Camera: trước" else "Camera: sau"
            applyMute()
            current.startPreview(binding.preview)
            binding.quality.text = "Đang chạy/kiểm tra: ${config.label}\n${support.description}" + if (selected() != config) "\nLựa chọn ${selected().label} CHƯA áp dụng." else ""
            status("Đang thử cấu hình ${plan.attempted}/${plan.total} • Camera ${support.mode.cameraId} • ${config.label}")
            true
        } catch (error: Exception) {
            val reason = "${support.description}\nKhởi tạo camera/bộ mã hóa: ${error.javaClass.simpleName}: ${error.message}"
            val camera = stream?.videoSource as? LiveCameraSource
            if (camera != null && !camera.isClosed()) {
                status("Đang đóng camera sau lỗi khởi tạo trước khi thử cấu hình khác…")
                camera.stopForRetry { ui(token) {
                    failPreparation(plan, reason, wasLive = false,
                        pendingStart = autoStartWhenReady || startWhenCameraReady)
                } }
                true // Asynchronous close; no stream starts until a candidate is verified.
            } else {
                failPreparation(plan, reason, wasLive = false, pendingStart = autoStartWhenReady)
            }
        }
    }

    private fun failPreparation(plan: CameraAttemptPlan, reason: String, wasLive: Boolean, pendingStart: Boolean): Boolean {
        // Camera failures arrive after onClosed; dispose invalidates old UI callbacks before retry.
        lastCameraIssue = plan.recordFailure(reason)
        dispose()
        if (!wasLive && foreground && sessionEntered && plan.hasNext()) {
            return prepareNext(plan, pendingStart)
        }
        val report = plan.failureReport()
        if (!wasLive && !plan.recovering && plan.restoreConfig != null && foreground && sessionEntered) {
            val restored = prepare(plan.restoreConfig, plan.restoreFront, recovering = true)
            lastCameraIssue = report
            status("Chưa đạt ${plan.config.label} sau ${plan.attempted} cấu hình.\n" +
                if (restored) "Đang khôi phục ${plan.restoreConfig.label}; KHÔNG tự phát. Xem Chẩn đoán camera."
                else "Không khôi phục được camera. Xem Chẩn đoán camera.")
        } else {
            status("Không hoàn tất cấu hình ${plan.config.label}.\n$reason\nMở ☰ → Chẩn đoán camera để xem các lần thử.")
        }
        binding.controlsPanel.visibility = View.VISIBLE
        // Restoring a 30 FPS preview is not successful preparation of the requested 60 FPS stream.
        return false
    }
    private fun startLive() {
        if (destination == Destination.YOUTUBE && binding.streamKey.text.isNullOrBlank()) {
            prepareYouTubeLiveSession(autoStart = true)
            return
        }
        val url = try { buildStreamUrl(binding.serverUrl.text.toString(), binding.streamKey.text.toString()) }
        catch (error: IllegalArgumentException) { status(error.message ?: "Kiểm tra URL và key."); binding.controlsPanel.visibility = View.VISIBLE; return }
        if ((!prepared || active != selected()) && !prepare()) return
        if (!cameraReady) {
            startWhenCameraReady = true
            status("Đang đợi camera sẵn sàng rồi mới kết nối live…")
            return
        }
        getPreferences(MODE_PRIVATE).edit().putString("server_${destination.name}", binding.serverUrl.text.toString().trim()).apply()
        setLive(true)
        binding.controlsPanel.visibility = View.GONE
        status("Đang kết nối ${destination.title}…")
        try { stream?.startStream(url) } catch (_: Exception) { dispose(); status("Không thể kết nối. Kiểm tra Server URL, key và mạng.") }
    }
    private fun stopLive() { dispose(); prepare(); status("Đã dừng phát.") }
    private fun applyMute() {
        (stream?.audioSource as? MicrophoneSource)?.let { if (muted) it.mute() else it.unMute() }
        binding.microphone.text = if (muted) "Bật micro" else "Tắt micro"
    }
    private fun setLive(value: Boolean) {
        live = value
        binding.startStop.text = if (value) "DỪNG PHÁT" else "BẮT ĐẦU PHÁT"
        listOf(binding.resolution, binding.fps, binding.applySettings, binding.switchCamera, binding.serverUrl, binding.streamKey).forEach { it.isEnabled = !value }
        refreshCompactControls()
    }
    private fun status(message: String, connected: Boolean = false) {
        binding.status.text = message
        binding.status.setTextColor(ContextCompat.getColor(this, if (connected) R.color.live_green else R.color.text_secondary))
        binding.compactStatus.text = message.lineSequence().firstOrNull().orEmpty()
        binding.compactStatus.contentDescription = message
        binding.compactStatus.setTextColor(ContextCompat.getColor(this, if (connected) R.color.live_green else R.color.white))
    }
    private fun showFps() {
        binding.actualFps.text = String.format(Locale.getDefault(), "Cảm biến: %.1f FPS • Hình qua GL: %.1f FPS • Mã hóa: %s", captureFps, cameraFps, if (live) "$encodedFps FPS" else "chưa phát") +
            (if (cameraFps > 0 && cameraFps < (active?.fps ?: 30) * 0.85)
                "\n⚠ " + frameRateFailure(active?.fps ?: 30, captureFps, cameraFps) else "") +
            (if (lowFpsSeconds >= 5) "\n⚠ FPS mã hóa thấp hơn mục tiêu. Kiểm tra ánh sáng/nhiệt độ hoặc giảm cấu hình." else "")
    }
    private fun ui(token: Int, action: () -> Unit) = runOnUiThread { if (token == generation && !isDestroyed) action() }
    private fun checker(token: Int) = object : ConnectChecker {
        override fun onConnectionStarted(url: String) { ui(token) { status("Đang kết nối ${destination.title}…") } }
        override fun onConnectionSuccess() { ui(token) { status("● ĐANG GỬI LUỒNG • ${destination.title}\nKiểm tra trang quản lý live để lên sóng.", true) } }
        override fun onConnectionFailed(reason: String) { ui(token) {
            val current = stream
            if (live && current != null && current.getStreamClient().reTry(3000, reason, null)) status("Đang thử kết nối lại…")
            else { dispose(); status("Kết nối thất bại. Kiểm tra mạng, Server URL và key còn hiệu lực.") }
        } }
        override fun onNewBitrate(bitrate: Long) { ui(token) { binding.bitrate.text = String.format(Locale.getDefault(), "Đang gửi: %.2f Mb/s", bitrate / 1_000_000.0) } }
        override fun onDisconnect() { ui(token) { dispose(); status("Đã ngắt kết nối. Bấm BẮT ĐẦU PHÁT để thử lại.") } }
        override fun onAuthError() { ui(token) { dispose(); status("Không xác thực được. Lấy lại Stream Key trên nền tảng.") } }
        override fun onAuthSuccess() = Unit
    }
    private fun dispose() {
        generation++
        replayEngine?.close(); replayEngine = null
        val previous = stream
        stream = null; prepared = false; active = null; activeSupport = null
        cameraReady = false; startWhenCameraReady = false
        setLive(false)
        try { previous?.release() } catch (_: Exception) { }
        sensorState = "Chưa có kết quả cảm biến"
        cameraFps = 0.0; captureFps = 0.0; encodedFps = 0; lowFpsSeconds = 0
        binding.bitrate.text = "Đang gửi: 0 Mb/s"
        showFps()
    }
    override fun onResume() {
        super.onResume(); foreground = true; startRemoteBranding()
        refreshYouTubeConnection()
        if (sessionEntered && !live && !prepared && hasPermissions()) binding.preview.post { if (foreground && sessionEntered && !prepared) prepare() }
    }
    override fun onPause() {
        foreground = false
        stopRemoteBranding()
        setLayoutEditing(false)
        scorePanel?.dismiss(); scorePanel = null
        scoreState = scoreState.pause(SystemClock.elapsedRealtime())
        scoreStore.save(scoreState, SystemClock.elapsedRealtime())
        if (live) Toast.makeText(this, "Đã dừng live vì ứng dụng ra nền. Giữ ứng dụng mở khi phát.", Toast.LENGTH_LONG).show()
        dispose(); super.onPause()
    }
    override fun onDestroy() { stopRemoteBranding(); brandingExecutor.shutdownNow(); dispose(); super.onDestroy() }
}
