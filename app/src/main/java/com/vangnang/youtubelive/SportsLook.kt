package com.vangnang.youtubelive

/** Conservative single-frame presets. Values are shader amounts, not UI percentages. */
enum class SportsLook(val title: String, val denoise: Float, val sharpen: Float,
                      val contrast: Float, val saturation: Float, val detailLimit: Float) {
    OFF("Tắt • Hình gốc", 0f, 0f, 1f, 1f, 0f),
    LIGHT("Nhẹ • Tự nhiên", 0.18f, 0.25f, 1.025f, 1.035f, 0.015f),
    MEDIUM("Vừa • Rõ nét hơn", 0.30f, 0.40f, 1.04f, 1.055f, 0.025f);

    companion object {
        fun fromStored(value: String?): SportsLook = entries.firstOrNull { it.name == value } ?: LIGHT
    }
}
