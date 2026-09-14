package com.vangnang.youtubelive

/** Safe preset colors: no free-form parsing is needed while operating a live match. */
enum class OverlayColor(val title: String, val argb: Int?) {
    DEFAULT("Mặc định theo mẫu", null),
    WHITE("Trắng", 0xFFFFFFFF.toInt()),
    BLACK("Đen", 0xFF090D12.toInt()),
    NAVY("Xanh đêm", 0xFF071D32.toInt()),
    CYAN("Xanh ngọc", 0xFF20C4D6.toInt()),
    RED("Đỏ", 0xFFE32636.toInt()),
    GOLD("Vàng", 0xFFFFC857.toInt()),
    PURPLE("Tím", 0xFF694FA0.toInt())
}

data class OverlayColors(
    val text: Int? = null,
    val border: Int? = null,
    val background: Int? = null
)

fun overlayColorIndex(value: Int?): Int = OverlayColor.entries.indexOfFirst { it.argb == value }.coerceAtLeast(0)

fun applyOverlayColors(marks: List<Mark>, colors: OverlayColors): List<Mark> = marks.map { mark ->
    when (mark) {
        is Mark.Caption -> colors.text?.let { mark.copy(color = it) } ?: mark
        is Mark.Panel -> {
            val alpha = mark.ink.colors.firstOrNull()?.ushr(24) ?: 255
            val protected = mark.ink.colors == listOf(BroadcastDesign.serveGreen) || alpha <= 0xAA
            if (protected) mark else mark.copy(
                ink = colors.background?.let { Ink(it) } ?: mark.ink,
                stroke = colors.border ?: mark.stroke
            )
        }
        is Mark.Shape -> mark.copy(
            ink = colors.background?.let { Ink(it) } ?: mark.ink,
            stroke = colors.border ?: mark.stroke
        )
        is Mark.Dot -> mark
    }
}
