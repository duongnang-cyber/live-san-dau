package com.vangnang.youtubelive

import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/** Operator-only controls. This dialog is never captured in the encoded video. */
class ScorePanel(
    private val activity: AppCompatActivity,
    private val current: () -> ScoreState,
    private val change: ((ScoreState) -> ScoreState) -> Unit,
    private val undo: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var dialog: Dialog
    private val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)) }
    private var refreshing = false
    fun dismiss() { if (::dialog.isInitialized) dialog.dismiss() }
    private fun dp(n: Int) = (n * activity.resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(activity, id)
    private fun label(text: String, parent: LinearLayout = root, size: Float = 15f): TextView = TextView(activity).apply {
        this.text = text; textSize = size; setTextColor(color(R.color.field_label))
        setPadding(0, dp(6), 0, dp(4)); parent.addView(this)
    }
    private fun row(parent: LinearLayout = root) = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; parent.addView(this) }
    private fun button(text: String, parent: LinearLayout = root, action: () -> Unit): Button = Button(activity).apply {
        this.text = text; isAllCaps = false; minHeight = dp(48)
        parent.addView(this, LinearLayout.LayoutParams(if (parent.orientation == LinearLayout.HORIZONTAL) 0 else -1, -2, if (parent.orientation == LinearLayout.HORIZONTAL) 1f else 0f))
        setOnClickListener { action() }
    }
    private fun input(title: String, value: String, max: Int, numeric: Boolean = false): EditText {
        label(title)
        return EditText(activity).apply {
            setText(value); textSize = 16f; setSingleLine(true)
            setTextColor(ContextCompat.getColorStateList(activity, R.color.field_value_state))
            setHintTextColor(color(R.color.field_placeholder))
            setBackgroundColor(color(R.color.field_surface))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(max)); root.addView(this)
        }
    }
    private fun toggle(title: String, checked: Boolean, action: (Boolean) -> Unit): androidx.appcompat.widget.SwitchCompat = androidx.appcompat.widget.SwitchCompat(activity).apply {
        text = title; isChecked = checked; minHeight = dp(48)
        setTextColor(color(R.color.field_label)); root.addView(this)
        setOnCheckedChangeListener { _, value -> if (!refreshing) action(value) }
    }
    private fun choices(items: List<String>, initial: Int, parent: LinearLayout = root, action: (Int) -> Unit): Spinner = Spinner(activity).apply {
        adapter = ArrayAdapter(activity, R.layout.spinner_item, items).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        setBackgroundColor(color(R.color.field_surface))
        parent.addView(this, LinearLayout.LayoutParams(-1, dp(48))); setSelection(initial)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { if (!refreshing) action(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) = Unit
        }
    }
    fun show() {
        val initial = current()
        val controls = sportControls(initial.sport)
        label("${initial.sport.title} • CÀI ĐẶT", size = 20f)
        label("Cập nhật được khi đang live. Chỉ đồ họa xuất hiện trong video, không có các nút điều khiển này.", size = 13f)
        val visible = toggle("Hiện bảng tỉ số", initial.visible) { value -> change { it.copy(visible = value, intermission = if (value) it.intermission else false) } }
        label("Mẫu bảng tỉ số ${initial.sport.title}")
        val style = choices(BoardStyle.entries.map { it.title }, initial.boardStyle().ordinal) { index ->
            if (current().boardStyle().ordinal != index) change { it.withBoardStyle(BoardStyle.entries[index]) }
        }
        val quickControls = if (controls.sets) toggle("Hiện nút ± điểm trên màn hình", initial.quickScoreControls) { value ->
            change { it.copy(quickScoreControls = value) }
        } else null
        val scoreText = label("", size = 21f).apply { setTextColor(color(R.color.field_value)) }
        val points = row()
        button("A −1", points) { change { it.changeScore(0, -1) } }
        button("A +1", points) { change { it.changeScore(0, 1) } }
        button("B −1", points) { change { it.changeScore(1, -1) } }
        button("B +1", points) { change { it.changeScore(1, 1) } }
        val setsSection = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; root.addView(this) }
        val setsText = label("", setsSection).apply { setTextColor(color(R.color.field_value)) }
        val setsRow = row(setsSection)
        button("Set A −", setsRow) { change { it.changeSets(0, -1) } }
        button("Set A +", setsRow) { change { it.changeSets(0, 1) } }
        button("Set B −", setsRow) { change { it.changeSets(1, -1) } }
        button("Set B +", setsRow) { change { it.changeSets(1, 1) } }
        button("Set mới: đưa điểm về 0 (giữ số set thắng)", setsSection) {
            AlertDialog.Builder(activity).setMessage("Đưa điểm hai đội về 0? Số set thắng được giữ nguyên; bạn tự chọn tên set tiếp theo.")
                .setNegativeButton("Hủy", null).setPositiveButton("Set mới") { _, _ -> change { it.copy(scoreA = 0, scoreB = 0) } }.show()
        }
        label(if (initial.sport == Sport.PICKLEBALL) "Nhập điểm/set thủ công; không tự tính side-out hoặc xác định thắng trận."
            else "Nhập điểm/set thủ công; không tự xác định thắng set/trận.", setsSection, 13f)
        val servingSection = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; root.addView(this) }
        label("Đội giao bóng", servingSection)
        val serving = choices(listOf("Không hiển thị", "Đội A", "Đội B"), initial.serving, servingSection) { value ->
            if (value != current().serving) change { it.copy(serving = value) }
        }
        val serverRow = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; servingSection.addView(this) }
        label("Pickleball: tay giao bóng (đơn dùng tay 1)", serverRow)
        val server = choices(listOf("Tay giao số 1 · một vạch xanh", "Tay giao số 2 · hai vạch xanh"), initial.serverNumber - 1, serverRow) { value ->
            if (value + 1 != current().serverNumber) change { it.copy(serverNumber = value + 1) }
        }
        setsSection.visibility = if (controls.sets) View.VISIBLE else View.GONE
        servingSection.visibility = if (controls.serving) View.VISIBLE else View.GONE
        serverRow.visibility = if (controls.serverNumber) View.VISIBLE else View.GONE
        val actions = row()
        button("Hoàn tác", actions) { undo() }
        button("Đổi bên A ↔ B", actions) { change { it.swapTeams() } }

        label("ĐỒNG HỒ / ${controls.breakLabel.uppercase()}", size = 18f)
        val clockText = label("", size = 21f).apply { setTextColor(color(R.color.field_value)) }
        val clockButton = button("Chạy / dừng đồng hồ") { change { it.toggleClock(SystemClock.elapsedRealtime()) } }
        val clockInput = input("Đặt phút:giây", initial.clock(SystemClock.elapsedRealtime()), 6)
        button("Đặt lại thời gian đã nhập") {
            val parts = clockInput.text.toString().trim().split(':')
            val minutes = parts.getOrNull(0)?.toIntOrNull()
            val seconds = parts.getOrNull(1)?.toIntOrNull()
            if (parts.size != 2 || minutes == null || seconds == null || minutes !in 0..999 || seconds !in 0..59) clockInput.error = "Nhập phút:giây, ví dụ 45:00"
            else change { it.setClock(minutes, seconds, SystemClock.elapsedRealtime()) }
        }
        val period = input("${controls.periodLabel} đang thi đấu", initial.period, 30)
        val breakTitle = input("Tiêu đề bảng ${controls.breakLabel}", initial.breakTitle, 50)
        button("Cập nhật ${controls.periodLabel.lowercase()} và tiêu đề") { change { it.copy(period = period.text.toString().trim().ifBlank { it.period }, breakTitle = breakTitle.text.toString().trim().ifBlank { it.breakTitle }) } }
        val intermission = toggle("Hiện bảng lớn ${controls.breakLabel}", initial.intermission) { value -> change { it.showBreak(value, SystemClock.elapsedRealtime()) } }
        label("Bật bảng ${controls.breakLabel} sẽ dừng đồng hồ. Khi trở lại trận, bấm Chạy để tiếp tục.", size = 13f)

        label("TÊN ĐỘI / GIẢI ĐẤU", size = 18f)
        val nameA = input("Tên đội / người chơi A", initial.teamA, 40)
        val nameB = input("Tên đội / người chơi B", initial.teamB, 40)
        val event = input("Tên giải / nội dung giới thiệu", initial.event, 80)
        button("Cập nhật tên đội / giải") { change { it.copy(teamA = nameA.text.toString().trim().ifBlank { "ĐỘI A" }, teamB = nameB.text.toString().trim().ifBlank { "ĐỘI B" }, event = event.text.toString().trim()) } }

        label("CHỮ CHẠY QUẢNG CÁO / GIỚI THIỆU", size = 18f)
        val tickerOn = toggle("Bật chữ chạy", initial.tickerVisible) { value -> change { it.copy(tickerVisible = value) } }
        val ticker = input("Nội dung (tối đa 500 ký tự)", initial.tickerText, 500)
        ticker.setSingleLine(false); ticker.minLines = 2
        val tag = input("Nhãn bên trái chữ chạy", initial.tickerLabel, 20)
        label("Tốc độ chữ chạy")
        var speed = initial.tickerSpeed
        choices(listOf("Chậm", "Vừa", "Nhanh"), if (speed < 70) 0 else if (speed < 120) 1 else 2) { speed = listOf(50, 90, 150)[it] }
        button("Cập nhật nội dung chữ chạy") {
            val text = ticker.text.toString().replace(Regex("\\s+"), " ").trim()
            change { it.copy(tickerText = text, tickerLabel = tag.text.toString().trim().ifBlank { "THÔNG TIN" }, tickerSpeed = speed) }
            if (text.isEmpty()) Toast.makeText(activity, "Chữ chạy ẩn vì chưa có nội dung.", Toast.LENGTH_SHORT).show()
        }
        button(if (controls.sets) "Trận mới: xóa điểm, set và đồng hồ" else "Trận mới: xóa điểm và đồng hồ") {
            AlertDialog.Builder(activity).setTitle("Bắt đầu trận mới?").setMessage("Xóa tỉ số và đồng hồ. Giữ tên đội, nội dung chữ chạy và cài đặt phát.")
                .setNegativeButton("Hủy", null).setPositiveButton("Trận mới") { _, _ -> change { it.resetMatch() } }.show()
        }
        button("ĐÓNG / XEM HÌNH LIVE") { dialog.dismiss() }
        dialog = Dialog(activity).apply {
            setContentView(ScrollView(activity).apply { addView(root) })
            window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.90).toInt().coerceAtMost(dp(620)), (activity.resources.displayMetrics.heightPixels * 0.88).toInt())
        }
        var lastUiState = initial
        val refresh = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                val s = current()
                refreshing = true
                if (lastUiState.teamA != s.teamA) nameA.setText(s.teamA)
                if (lastUiState.teamB != s.teamB) nameB.setText(s.teamB)
                if (lastUiState.event != s.event) event.setText(s.event)
                if (lastUiState.period != s.period) period.setText(s.period)
                if (lastUiState.breakTitle != s.breakTitle) breakTitle.setText(s.breakTitle)
                if (lastUiState.tickerText != s.tickerText) ticker.setText(s.tickerText)
                if (lastUiState.tickerLabel != s.tickerLabel) tag.setText(s.tickerLabel)
                if (lastUiState.elapsedMs != s.elapsedMs && !clockInput.hasFocus()) clockInput.setText(s.clock(SystemClock.elapsedRealtime()))
                visible.isChecked = s.visible; intermission.isChecked = s.intermission; tickerOn.isChecked = s.tickerVisible
                if (style.selectedItemPosition != s.boardStyle().ordinal) style.setSelection(s.boardStyle().ordinal)
                quickControls?.isChecked = s.quickScoreControls
                if (serving.selectedItemPosition != s.serving) serving.setSelection(s.serving)
                if (server.selectedItemPosition != s.serverNumber - 1) server.setSelection(s.serverNumber - 1)
                scoreText.text = "A: ${s.teamA}   ${s.scoreA}  —  ${s.scoreB}   B: ${s.teamB}"
                setsText.text = "Số set thắng: A ${s.setsA} — ${s.setsB} B"
                clockText.text = "${s.period} • ${s.clock(SystemClock.elapsedRealtime())}"
                clockButton.text = if (s.runningSince == null) "▶ Chạy đồng hồ" else "Ⅱ Dừng đồng hồ"
                clockButton.isEnabled = !s.intermission
                refreshing = false
                lastUiState = s
                handler.postDelayed(this, 250)
            }
        }
        dialog.setOnDismissListener { handler.removeCallbacksAndMessages(null) }
        dialog.show()
        dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * 0.90).toInt().coerceAtMost(dp(620)), (activity.resources.displayMetrics.heightPixels * 0.88).toInt())
        handler.post(refresh)
    }
}
