package com.vangnang.youtubelive

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.widget.EditText
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
import android.widget.Spinner
import android.widget.Toast
import android.widget.FrameLayout
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.sources.audio.MicrophoneSource
import com.pedro.library.generic.GenericStream
import com.pedro.library.util.FpsListener
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.vangnang.youtubelive.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var facebookCallbackManager: CallbackManager
    private var stream: GenericStream? = null
    private var facebookPage: FacebookLive.Page? = null
    private var facebookBroadcast: FacebookLive.Broadcast? = null
    private var facebookBusy = false
    private var facebookRequest = 0
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
    private var destination = Destination.FACEBOOK
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
    private val scoreHistory = ArrayDeque<ScoreState>()
    private var scorePanel: ScorePanel? = null
    private val brandingHandler = Handler(Looper.getMainLooper())
    private val brandingExecutor = Executors.newSingleThreadExecutor()
    private var brandingToken = 0
    private val builtInSportButtons = linkedMapOf(
        R.id.setup_football to Sport.FOOTBALL,
        R.id.setup_volleyball to Sport.VOLLEYBALL,
        R.id.setup_pickleball to Sport.PICKLEBALL
    )
    @Volatile private var replayArtwork = ReplayArtwork()
    @Volatile private var logoArtwork: android.graphics.Bitmap? = null
    // Accessed on brandingExecutor only.
    private val artworkCache = LinkedHashMap<String, android.graphics.Bitmap>()
    private val customSportButtons = linkedMapOf<Int, SetupSport>()
    private var loadedCustomSports: List<SetupSport>? = null
    @Volatile private var remoteLicenseExpires = 0L
    @Volatile private var remotePaired = false
    @Volatile private var remoteDialogTitle = "Kết nối taode.online"
    @Volatile private var remoteDialogMessage = "Nhập mã do admin tạo trên website. Mã ghép nối dùng để đăng ký máy; mã miễn phí quảng cáo dùng sau khi đã ghép nối."
    @Volatile private var remoteDialogHint = "Mã ghép nối hoặc mã miễn phí quảng cáo"
    @Volatile private var remoteAboutTitle = "Thông tin ứng dụng"
    @Volatile private var remoteAboutBody = ""
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
        binding.setupPanel.remoteControl.setOnClickListener { showRemoteControlDialog() }
        facebookCallbackManager = CallbackManager.Factory.create()
        LoginManager.getInstance().registerCallback(facebookCallbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                refreshFacebookAccount()
                chooseFacebookDestination()
            }

            override fun onCancel() {
                refreshFacebookAccount()
                Toast.makeText(this@MainActivity, "Bạn đã hủy đăng nhập Facebook.", Toast.LENGTH_SHORT).show()
            }

            override fun onError(error: FacebookException) {
                refreshFacebookAccount()
                Toast.makeText(this@MainActivity, "Không đăng nhập được Facebook. Kiểm tra mạng và cấu hình ứng dụng Meta.", Toast.LENGTH_LONG).show()
            }
        })
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
    private fun applyPublicSetupUi(ui: org.json.JSONObject) {
        fun text(key: String, fallback: String) = ui.optString(key, fallback).takeIf { it.isNotBlank() } ?: fallback
        binding.setupPanel.setupTitle.text = text("title", binding.setupPanel.setupTitle.text.toString())
        binding.setupPanel.setupSubtitle.text = text("subtitle", binding.setupPanel.setupSubtitle.text.toString())
        binding.setupPanel.setupSportHeading.text = text("sport_heading", binding.setupPanel.setupSportHeading.text.toString())
        binding.setupPanel.setupDestinationHeading.text = text("destination_heading", binding.setupPanel.setupDestinationHeading.text.toString())
        binding.setupPanel.enterCamera.text = text("camera_button", binding.setupPanel.enterCamera.text.toString())
        binding.setupPanel.remoteControl.text = text("remote_button", binding.setupPanel.remoteControl.text.toString())
        remoteDialogTitle = text("remote_title", remoteDialogTitle)
        remoteDialogMessage = text("remote_message", remoteDialogMessage)
        remoteDialogHint = text("remote_hint", remoteDialogHint)
        val group = binding.setupPanel.setupSport
        val wasFirstRefresh = loadedCustomSports == null
        val checked = group.checkedButtonId
        val preferredCustomId = customSportButtons[checked]?.id ?: scoreState.customSportId?.takeIf {
            wasFirstRefresh && builtInSportButtons[checked] == scoreState.sport
        }
        val sports = ui.optJSONObject("sports")
        fun sport(id: Int, key: String) {
            val button = binding.setupPanel.root.findViewById<com.google.android.material.button.MaterialButton>(id)
            val value = sports?.optJSONObject(key)
            button.visibility = if (value?.has("visible") == true && !value.optBoolean("visible", true)) View.GONE else View.VISIBLE
            if (value?.has("text") == true) button.text = value.optString("text", button.text.toString())
        }
        sport(R.id.setup_football, "FOOTBALL")
        sport(R.id.setup_volleyball, "VOLLEYBALL")
        sport(R.id.setup_pickleball, "PICKLEBALL")
        val custom = readSetupSports(ui.optJSONArray("custom_sports"))
        if (loadedCustomSports != custom) {
            customSportButtons.keys.forEach { id -> group.findViewById<View>(id)?.let { group.removeView(it) } }
            customSportButtons.clear()
            for (item in custom) {
                val button = layoutInflater.inflate(R.layout.setup_custom_sport, group, false) as com.google.android.material.button.MaterialButton
                button.id = View.generateViewId()
                button.text = item.title
                customSportButtons[button.id] = item
                group.addView(button)
            }
            loadedCustomSports = custom
        }
        val preferred = customSportButtons.entries.firstOrNull { it.value.id == preferredCustomId }?.key ?: checked
        selectVisibleSetupSport(preferred)
        val menus = ui.optJSONObject("menus")
        fun menu(id: Int, key: String) {
            val view = binding.setupPanel.root.findViewById<View>(id)
            if (menus?.has(key) == true) view.visibility = if (menus.optBoolean(key, true)) View.VISIBLE else View.GONE
        }
        menu(R.id.setup_facebook, "facebook")
        menu(R.id.setup_youtube, "youtube")
        menu(R.id.setup_custom, "custom")
        menu(R.id.remote_control, "remote")
        menu(R.id.enter_camera, "camera")
    }

    private fun refreshDestinationSetup() {
        val choice = setupDestinationSelection()
        binding.setupPanel.facebookAccountPanel.visibility = if (choice == Destination.FACEBOOK) View.VISIBLE else View.GONE
    }
    private fun configureSetup() {
        binding.setupPanel.setupDestination.addOnButtonCheckedListener { _, _, checked -> if (checked) refreshDestinationSetup() }
        binding.setupPanel.facebookLogin.setOnClickListener {
            if (getString(R.string.facebook_client_token).isBlank()) {
                AlertDialog.Builder(this).setMessage("Bản cài đặt chưa có cấu hình Facebook. Cần bổ sung Client Token trước khi đăng nhập.").setPositiveButton("Đóng", null).show()
            } else if (facebookAccessToken() != null) {
                AlertDialog.Builder(this).setItems(arrayOf("Chọn Fanpage hoặc trang cá nhân", "Đăng xuất")) { _, choice ->
                    if (choice == 0) chooseFacebookDestination() else {
                        facebookRequest++
                        facebookPage = null
                        LoginManager.getInstance().logOut()
                        refreshFacebookAccount()
                    }
                }.show()
            } else {
                chooseFacebookDestination()
            }
        }
        binding.setupPanel.enterCamera.setOnClickListener { enterCamera() }
        refreshFacebookAccount()
    }
    private fun facebookAccessToken(): AccessToken? = AccessToken.getCurrentAccessToken()?.takeUnless { it.isExpired }
    private fun refreshFacebookAccount() {
        val connected = facebookAccessToken() != null
        if (!connected) facebookPage = null
        binding.setupPanel.facebookAccountStatus.text = if (connected) {
            facebookPage?.let { "${if (it.isProfile) "Trang cá nhân" else "Fanpage"}: ${it.name}" } ?: "Đã đăng nhập • chọn nơi phát"
        } else {
            "Chọn trang cá nhân hoặc Fanpage để đăng nhập"
        }
        binding.setupPanel.facebookLogin.text = if (connected) "CHỌN NƠI PHÁT" else "ĐĂNG NHẬP"
    }
    private fun visibleSetupSport(id: Int): Boolean =
        binding.setupPanel.setupSport.findViewById<View>(id)?.visibility == View.VISIBLE

    private fun selectVisibleSetupSport(preferred: Int) {
        val candidates = builtInSportButtons.keys + customSportButtons.keys
        val id = preferred.takeIf(::visibleSetupSport) ?: candidates.firstOrNull(::visibleSetupSport)
        id?.let(binding.setupPanel.setupSport::check)
    }

    private fun setupDestinationSelection() = when (binding.setupPanel.setupDestination.checkedButtonId) {
        R.id.setup_facebook -> Destination.FACEBOOK
        R.id.setup_youtube -> Destination.YOUTUBE
        R.id.setup_custom -> Destination.CUSTOM
        else -> Destination.FACEBOOK
    }
    private fun showSetup() {
        val s = binding.setupPanel
        sessionEntered = false
        setLayoutEditing(false)
        dispose()
        scoreState = scoreState.pause(SystemClock.elapsedRealtime())
        scoreStore.save(scoreState, SystemClock.elapsedRealtime())
        val preferredSport = customSportButtons.entries.firstOrNull { it.value.id == scoreState.customSportId }?.key
            ?: builtInSportButtons.entries.first { it.value == scoreState.sport }.key
        selectVisibleSetupSport(preferredSport)
        s.setupDestination.check(when (destination) {
            Destination.YOUTUBE -> R.id.setup_youtube
            Destination.FACEBOOK -> R.id.setup_facebook
            Destination.CUSTOM -> R.id.setup_custom
        })
        refreshDestinationSetup()
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
        if (destination == Destination.FACEBOOK && facebookPage == null) {
            Toast.makeText(this, "Đăng nhập và chọn Fanpage hoặc trang cá nhân trước khi vào camera.", Toast.LENGTH_LONG).show()
            return
        }
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
        val selectedButton = s.setupSport.checkedButtonId
        val customSport = customSportButtons[selectedButton]
        val profile = customSport?.profile ?: builtInSportButtons[selectedButton] ?: Sport.FOOTBALL
        scoreState = setupScore(scoreState, profile, false, SystemClock.elapsedRealtime(), customSport)
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
        binding.sessionTitle.text = "${scoreState.sportTitle} • ${destination.title}"
        binding.sportSettings.text = "Cài đặt ${scoreState.sportTitle}"
        binding.keyInput.hint = sessionKeyLabel(destination)
        binding.serverInput.visibility = if (sessionShowsServer(destination)) View.VISIBLE else View.GONE
        binding.keyInput.visibility = if (destination != Destination.FACEBOOK && sessionNeedsManualKey(destination)) View.VISIBLE else View.GONE
        binding.platformHelp.text = when (destination) {
            Destination.FACEBOOK -> "Phát lên ${if (facebookPage?.isProfile == true) "trang cá nhân" else "Fanpage"} ${facebookPage?.name.orEmpty()}. Bấm LIVE, nhập tiêu đề rồi xác nhận phát."
            Destination.YOUTUBE -> "YouTube: tạo buổi live trên YouTube Studio rồi dán Stream Key."
            Destination.CUSTOM -> "Dán URL máy chủ RTMP/RTMPS và key, không phải link xem video."
        } + "\nKey không được lưu. Giữ ứng dụng mở khi live."
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
        }, {
            // The website controls the event label for devices without an active license.
            remoteLicenseExpires > (System.currentTimeMillis() / 1000L)
        }).also { it.show() }
    }
    private fun showCompactMenu() {
        PopupMenu(this, binding.menuToggle).apply {
            menu.add(0, 1, 0, "${scoreState.sportTitle} • Tỉ số / chữ chạy")
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
                val body = if (index == 0 && remoteAboutBody.isNotBlank()) remoteAboutBody else paths.joinToString("\n\n") { path ->
                    runCatching { assets.open(path).bufferedReader().use { it.readText() } }
                        .getOrElse { "Không mở được $path. Xem gói mã nguồn đi kèm." }
                }
                val view = android.widget.TextView(this).apply {
                    text = body; textSize = 14f; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
                    val inset = (20 * resources.displayMetrics.density).toInt()
                    setPadding(inset, inset, inset, inset); setTextIsSelectable(true)
                }
                AlertDialog.Builder(this).setTitle(if (index == 0) remoteAboutTitle else "Giấy phép nguồn mở")
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
    private fun showRemoteControlDialog() {
        val input = EditText(this).apply { hint = remoteDialogHint; setSingleLine(true) }
        AlertDialog.Builder(this).setTitle(remoteDialogTitle).setMessage(remoteDialogMessage).setView(input)
            .setNegativeButton("Hủy", null).setPositiveButton("Xác nhận") { _, _ ->
                val code = input.text.toString().trim()
                if (code.isBlank()) return@setPositiveButton
                brandingExecutor.execute {
                    val result = runCatching { if (RemoteBrandingClient.paired(this)) RemoteBrandingClient.activate(this, code) else RemoteBrandingClient.pair(this, code, "Android ${android.os.Build.MODEL}") }.getOrNull()
                    runOnUiThread {
                        remotePaired = result != null
                        Toast.makeText(this, if (result != null) "Đã kết nối website" else "Mã sai hoặc đã hết hạn", Toast.LENGTH_LONG).show()
                        if (result != null) startRemoteBranding()
                    }
                }
            }.show()
    }
    private fun startRemoteBranding() {
        remotePaired = RemoteBrandingClient.paired(this)
        val token = ++brandingToken
        fetchRemoteBranding(token)
    }
    private fun loadRemoteArtwork(asset: String): android.graphics.Bitmap? {
        if (asset.isBlank()) return null
        artworkCache[asset]?.let { return it }
        val image = runCatching { RemoteBrandingClient.fetchAsset(this, asset) }.getOrNull() ?: return null
        artworkCache[asset] = image
        if (artworkCache.size > 4) artworkCache.remove(artworkCache.keys.first())
        return image
    }

    private fun fetchRemoteBranding(token: Int) {
        brandingExecutor.execute {
            val branding = runCatching { RemoteBrandingClient.fetch(this@MainActivity) }.getOrNull()
            val public = if (branding?.ui == null) {
                runCatching { RemoteBrandingClient.fetchPublicUi(this@MainActivity) }.getOrNull()
            } else null
            val logoAsset = branding?.logo?.optString("asset").orEmpty()
            val logo = if (branding?.enabled == true) loadRemoteArtwork(logoAsset) else null
            val replay = branding?.replay
            val artwork = replay?.let {
                val asset = if (it.optBoolean("show_asset", false)) loadRemoteArtwork(it.optString("asset")) else null
                ReplayArtwork(it.optString("label", "PHÁT LẠI").take(80), it.optBoolean("show_label", true), asset)
            } ?: ReplayArtwork()
            runOnUiThread {
                if (token != brandingToken || isDestroyed) return@runOnUiThread
                (branding?.ui ?: public?.optJSONObject("ui"))?.let(::applyPublicSetupUi)
                public?.optJSONObject("about")?.let { about ->
                    remoteAboutTitle = about.optString("title").takeIf { it.isNotBlank() }?.take(160) ?: remoteAboutTitle
                    remoteAboutBody = about.optString("body").takeIf { it.isNotBlank() }?.take(6000) ?: remoteAboutBody
                }
                branding?.let {
                    replayArtwork = artwork
                    logoArtwork = logo
                    remoteLicenseExpires = it.licenseExpires
                    it.about?.let { about ->
                        remoteAboutTitle = about.optString("title", remoteAboutTitle).take(160)
                        remoteAboutBody = about.optString("body", remoteAboutBody).take(6000)
                    }
                    val label = if (it.enabled) it.label else ""
                    val logoConfig = it.logo
                    val logoText = if (it.enabled) logoConfig?.optString("text", "").orEmpty().take(80) else ""
                    scoreState = scoreState.copy(
                        logoVisible = logo != null || logoText.isNotBlank(),
                        logoText = logoText,
                        logoX = logoConfig?.optDouble("x", 1050.0)?.toFloat()?.coerceIn(0f, 1200f) ?: 1050f,
                        logoY = logoConfig?.optDouble("y", 35.0)?.toFloat()?.coerceIn(0f, 680f) ?: 35f,
                        logoWidth = logoConfig?.optDouble("width", 180.0)?.toFloat()?.coerceIn(40f, 500f) ?: 180f,
                        logoOpacity = logoConfig?.optDouble("opacity", 0.8)?.toFloat()?.coerceIn(0f, 1f) ?: 0.8f
                    )
                    it.replay?.let { replay ->
                        val seconds = replay.optInt("seconds", 5).takeIf { v -> v in listOf(3, 5, 8) } ?: 5
                        val speed = replay.optDouble("speed", 0.5).takeIf { v -> v == 0.5 || v == 0.25 } ?: 0.5
                        val transition = replay.optString("transition", "SPORTS").takeIf { v -> ReplayTransition.entries.any { it.name == v } } ?: "SPORTS"
                        getSharedPreferences("replay_settings", MODE_PRIVATE).edit()
                            .putInt("seconds", seconds).putFloat("speed", speed.toFloat()).putString("transition", transition).apply()
                        if (!live) {
                            binding.replayDuration.setSelection(listOf(3, 5, 8).indexOf(seconds))
                            binding.replaySpeed.setSelection(if (speed == 0.5) 0 else 1)
                            binding.replayTransition.setSelection(ReplayTransition.entries.indexOfFirst { it.name == transition }.coerceAtLeast(0))
                            updateReplaySummary(ReplaySettings.safe(seconds, speed, transition))
                        }
                    }
                    val match = it.match
                    if (match != null) {
                        val licensed = it.licenseExpires > (System.currentTimeMillis() / 1000L)
                        val fixedEvent = if (licensed) match.optString("event", scoreState.event) else "TRỰC TIẾP THỂ THAO"
                        val remoteClock = match.optInt("clockSeconds", -1).coerceIn(-1, 59_999)
                        val remoteRunning = match.optBoolean("clockRunning", false)
                        scoreState = scoreState.copy(
                            teamA = match.optString("teamA", scoreState.teamA).take(40),
                            teamB = match.optString("teamB", scoreState.teamB).take(40),
                            event = fixedEvent.take(80),
                            period = match.optString("period", scoreState.period).take(30),
                            scoreA = match.optInt("scoreA", scoreState.scoreA).coerceIn(0, 999),
                            scoreB = match.optInt("scoreB", scoreState.scoreB).coerceIn(0, 999),
                            setsA = match.optInt("setsA", scoreState.setsA).coerceIn(0, 99),
                            setsB = match.optInt("setsB", scoreState.setsB).coerceIn(0, 99),
                            elapsedMs = if (remoteClock >= 0) remoteClock * 1000L else scoreState.elapsedMs,
                            runningSince = if (remoteClock >= 0 && remoteRunning) SystemClock.elapsedRealtime() else if (remoteClock >= 0) null else scoreState.runningSince,
                            tickerText = match.optString("tickerText", scoreState.tickerText).take(500),
                            tickerVisible = match.optBoolean("tickerVisible", scoreState.tickerVisible)
                        )
                    } else if (it.licenseExpires <= (System.currentTimeMillis() / 1000L) && scoreState.event != "TRỰC TIẾP THỂ THAO") {
                        scoreState = scoreState.copy(event = "TRỰC TIẾP THỂ THAO")
                    }
                    if (scoreState.tickerLabel != label) scoreState = scoreState.copy(tickerLabel = label)
                }
                // Website promises propagation in about ten seconds.
                brandingHandler.postDelayed({ fetchRemoteBranding(token) }, 10_000)
            }
        }
    }
    private fun stopRemoteBranding() {
        brandingToken++
        brandingHandler.removeCallbacksAndMessages(null)
        remoteLicenseExpires = 0L
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
            current.getGlInterface().addFilter(ScoreOverlayFilter({ scoreState }, { logoArtwork }))
            current.getGlInterface().addFilter(ReplayFilter(replay) { replayArtwork })
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
        if (destination == Destination.FACEBOOK) {
            startFacebookLive()
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
    private fun launchFacebookLogin(target: String) {
        startActivityForResult(Intent(this, FacebookLoginActivity::class.java).putExtra("login_target", target), FacebookLoginActivity.REQUEST_CODE)
    }
    private fun chooseFacebookDestination() {
        AlertDialog.Builder(this).setTitle("Chọn nơi phát Facebook")
            .setItems(arrayOf("Trang cá nhân của tôi", "Fanpage tôi quản lý")) { _, index ->
                if (facebookAccessToken() == null) launchFacebookLogin(if (index == 0) "profile" else "page")
                else if (index == 0) chooseFacebookProfile() else chooseFacebookPage()
            }.setNegativeButton("Hủy", null).show()
    }
    private fun chooseFacebookProfile() {
        val token = facebookAccessToken() ?: return
        val request = ++facebookRequest
        facebookPage = null
        binding.setupPanel.facebookAccountStatus.text = "Đang kiểm tra quyền trang cá nhân…"
        FacebookLive.checkPermissions(token) { states, error ->
            if (isDestroyed || isFinishing || request != facebookRequest || facebookAccessToken() != token) return@checkPermissions
            if (states == null || error != null) {
                refreshFacebookAccount()
                AlertDialog.Builder(this).setMessage(error ?: "Chưa kiểm tra được quyền.")
                    .setPositiveButton("Thử lại") { _, _ -> chooseFacebookProfile() }.setNegativeButton("Đóng", null).show()
            } else if (FacebookPermissionState.missing(states, true).isNotEmpty()) {
                refreshFacebookAccount()
                AlertDialog.Builder(this).setTitle("Cần quyền phát trang cá nhân")
                    .setMessage("Cần quyền publish_video. Nếu Meta báo Invalid Scopes, vào ứng dụng Meta → Trường hợp sử dụng → Truy cập API Video trực tiếp → Quyền và tính năng để kiểm tra publish_video. APK không thể tự cấp quyền thay Meta. Sau khi cấu hình, bấm Cấp quyền và hoàn tất xác nhận Facebook.")
                    .setPositiveButton("Cấp quyền") { _, _ -> launchFacebookLogin("profile") }.setNegativeButton("Đóng", null).show()
            } else FacebookLive.profile(token) { profile, profileError ->
                if (isDestroyed || request != facebookRequest || facebookAccessToken() != token) return@profile
                facebookPage = profile
                refreshFacebookAccount()
                if (profile == null) AlertDialog.Builder(this).setMessage(profileError ?: "Không đọc được tài khoản.").setPositiveButton("Đóng", null).show()
            }
        }
    }
    private fun chooseFacebookPage() {
        val token = facebookAccessToken() ?: return
        val request = ++facebookRequest
        facebookPage = null
        binding.setupPanel.facebookAccountStatus.text = "Đang kiểm tra quyền Facebook…"
        FacebookLive.checkPermissions(token) { states, error ->
            if (isDestroyed || isFinishing || request != facebookRequest || facebookAccessToken() != token) return@checkPermissions
            if (error != null || states == null) {
                refreshFacebookAccount()
                AlertDialog.Builder(this).setTitle("Chưa kiểm tra được quyền")
                    .setMessage(error ?: "Facebook chưa trả kết quả.")
                    .setPositiveButton("Thử lại") { _, _ -> chooseFacebookPage() }
                    .setNegativeButton("Đóng", null).show()
                return@checkPermissions
            }
            val missing = FacebookPermissionState.missing(states)
            if (missing.isNotEmpty()) {
                refreshFacebookAccount()
                val detail = FacebookLive.pagePermissions.joinToString("\n") {
                    "$it: " + when (states[it]) {
                        "granted" -> "Đã cấp"
                        "declined" -> "Đã từ chối"
                        "expired" -> "Đã hết hạn"
                        else -> "Chưa được cấp"
                    }
                }
                AlertDialog.Builder(this).setTitle("Cần cấp thêm quyền Trang")
                    .setMessage("Facebook trả về:\n\n$detail\n\nBấm Cấp lại quyền, chọn Fanpage và lưu để hoàn tất.")
                    .setPositiveButton("Cấp lại quyền") { _, _ ->
                        launchFacebookLogin("page")
                    }.setNegativeButton("Đóng", null).show()
            } else loadFacebookPages(token, request)
        }
    }
    private fun loadFacebookPages(token: AccessToken, request: Int) {
        binding.setupPanel.facebookAccountStatus.text = "Đang tải danh sách Fanpage…"
        FacebookLive.pages(token) { pages, error ->
            if (isDestroyed || request != facebookRequest) return@pages
            if (error != null || pages.isEmpty()) {
                refreshFacebookAccount()
                AlertDialog.Builder(this).setMessage(error ?: "Không có Fanpage được cấp quyền. Kiểm tra quyền quản lý và cấp quyền Trang trên Facebook.").setPositiveButton("Đóng", null).show()
            } else {
                AlertDialog.Builder(this).setTitle("Chọn Fanpage phát live")
                    .setItems(pages.map { it.name }.toTypedArray()) { _, index ->
                        facebookPage = pages[index]
                        refreshFacebookAccount()
                    }.setNegativeButton("Hủy", null).show()
            }
        }
    }
    private fun startFacebookLive() {
        if (facebookBusy || live) return
        val page = facebookPage ?: run { status("Quay về màn hình đầu để chọn nơi phát Facebook."); return }
        if ((!prepared || active != selected()) && !prepare()) return
        if (!cameraReady) { status("Camera đang chuẩn bị. Hãy bấm LIVE khi có hình."); return }
        val title = EditText(this).apply { hint = "Tiêu đề buổi live"; setText("Trực tiếp ${scoreState.sportTitle}") }
        val form = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8); addView(title)
        }
        val privacy = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, R.layout.spinner_item,
                listOf("Chỉ mình tôi (thử nghiệm)", "Bạn bè", "Công khai")).apply { setDropDownViewResource(R.layout.spinner_dropdown_item) }
        }
        if (page.isProfile) {
            form.addView(android.widget.TextView(this).apply { text = "Ai được xem? Meta có thể giới hạn đối tượng theo cấu hình ứng dụng." })
            form.addView(privacy)
        }
        AlertDialog.Builder(this).setTitle("Phát lên ${if (page.isProfile) "trang cá nhân" else "Fanpage"}: ${page.name}").setView(form)
            .setNegativeButton("Hủy", null)
            .setPositiveButton("PHÁT LIVE") { _, _ ->
                if (facebookBusy || live || !foreground || !cameraReady) return@setPositiveButton
                val name = title.text.toString().trim()
                if (name.isEmpty()) { status("Cần nhập tiêu đề buổi live."); return@setPositiveButton }
                facebookBusy = true
                val request = ++facebookRequest
                status("Đang tạo phiên live trên ${page.name}…")
                FacebookLive.create(page, name, FacebookPermissionState.privacyValue(privacy.selectedItemPosition)) { broadcast, error ->
                    if (isDestroyed || request != facebookRequest || !foreground || !sessionEntered) {
                        broadcast?.let { FacebookLive.end(it) {} }
                        return@create
                    }
                    facebookBusy = false
                    if (broadcast == null) { status(error ?: "Không tạo được phiên Facebook Live."); return@create }
                    facebookBroadcast = broadcast
                    setLive(true)
                    binding.controlsPanel.visibility = View.GONE
                    try { stream?.startStream(broadcast.url) ?: throw IllegalStateException() }
                    catch (_: Exception) { dispose(); status("Không gửi được hình lên Facebook. Hãy thử lại.") }
                }
            }.show()
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
        override fun onConnectionSuccess() { ui(token) { status(if (destination == Destination.FACEBOOK) "● ĐANG GỬI LUỒNG • ${facebookPage?.name ?: "Facebook"}\nMở Facebook để kiểm tra hình và tiếng." else "● ĐANG GỬI LUỒNG • ${destination.title}\nKiểm tra trang quản lý live để lên sóng.", true) } }
        override fun onConnectionFailed(reason: String) { ui(token) {
            val current = stream
            if (live && current != null && current.getStreamClient().reTry(3000, reason, null)) status("Đang thử kết nối lại…")
            else { dispose(); status(if (destination == Destination.FACEBOOK) "Không kết nối được Facebook. Kiểm tra mạng rồi thử tạo lại phiên live." else "Kết nối thất bại. Kiểm tra mạng, Server URL và key còn hiệu lực.") }
        } }
        override fun onNewBitrate(bitrate: Long) { ui(token) { binding.bitrate.text = String.format(Locale.getDefault(), "Đang gửi: %.2f Mb/s", bitrate / 1_000_000.0) } }
        override fun onDisconnect() { ui(token) { dispose(); status("Đã ngắt kết nối. Bấm BẮT ĐẦU PHÁT để thử lại.") } }
        override fun onAuthError() { ui(token) { dispose(); status(if (destination == Destination.FACEBOOK) "Facebook từ chối luồng phát. Đăng nhập lại và kiểm tra quyền nơi phát." else "Không xác thực được. Lấy lại Stream Key trên nền tảng.") } }
        override fun onAuthSuccess() = Unit
    }
    private fun dispose() {
        facebookRequest++
        facebookBusy = false
        facebookBroadcast?.let { broadcast ->
            facebookBroadcast = null
            FacebookLive.end(broadcast) { error ->
                if (error != null && !isDestroyed) Toast.makeText(this, "Chưa xác nhận kết thúc live. Kiểm tra Fanpage trên Facebook.", Toast.LENGTH_LONG).show()
            }
        }
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
        refreshFacebookAccount()
        if (sessionEntered && !live && !prepared && hasPermissions()) binding.preview.post { if (foreground && sessionEntered && !prepared) prepare() }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FacebookLoginActivity.REQUEST_CODE) {
            refreshFacebookAccount()
            if (resultCode == RESULT_OK) {
                when (data?.getStringExtra("login_target")) {
                    "page" -> chooseFacebookPage()
                    "profile" -> chooseFacebookProfile()
                    else -> chooseFacebookDestination()
                }
            } else {
                Toast.makeText(this, if (data?.getBooleanExtra("login_error", false) == true)
                    "Không đăng nhập được Facebook. Kiểm tra mạng và cấu hình ứng dụng Meta."
                    else "Bạn đã hủy đăng nhập Facebook.", Toast.LENGTH_LONG).show()
            }
            return
        }
        facebookCallbackManager.onActivityResult(requestCode, resultCode, data)
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
