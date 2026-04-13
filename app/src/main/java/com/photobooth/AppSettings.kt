package com.photobooth

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App settings persisted in SharedPreferences.
 */
class AppSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "photobooth_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_WATERMARK_TEXT = "watermark_text"
        private const val KEY_WATERMARK_ENABLED = "watermark_enabled"
        private const val KEY_COUNTDOWN_SECONDS = "countdown_seconds"
        private const val KEY_PREPARE_SECONDS = "prepare_seconds"
        private const val KEY_MIRROR_LIVE_VIEW = "mirror_live_view"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Language
    private val _language = MutableStateFlow(prefs.getString(KEY_LANGUAGE, "da") ?: "da")
    val language: StateFlow<String> = _language

    fun setLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
        _language.value = lang
    }

    // Watermark
    private val _watermarkText = MutableStateFlow(prefs.getString(KEY_WATERMARK_TEXT, "") ?: "")
    val watermarkText: StateFlow<String> = _watermarkText

    private val _watermarkEnabled = MutableStateFlow(prefs.getBoolean(KEY_WATERMARK_ENABLED, false))
    val watermarkEnabled: StateFlow<Boolean> = _watermarkEnabled

    fun setWatermarkText(text: String) {
        prefs.edit().putString(KEY_WATERMARK_TEXT, text).apply()
        _watermarkText.value = text
    }

    fun setWatermarkEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WATERMARK_ENABLED, enabled).apply()
        _watermarkEnabled.value = enabled
    }

    // Countdown duration
    private val _countdownSeconds = MutableStateFlow(prefs.getInt(KEY_COUNTDOWN_SECONDS, 5))
    val countdownSeconds: StateFlow<Int> = _countdownSeconds

    fun setCountdownSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_COUNTDOWN_SECONDS, seconds.coerceIn(3, 10)).apply()
        _countdownSeconds.value = seconds.coerceIn(3, 10)
    }

    // Prepare duration
    private val _prepareSeconds = MutableStateFlow(prefs.getInt(KEY_PREPARE_SECONDS, 5))
    val prepareSeconds: StateFlow<Int> = _prepareSeconds

    fun setPrepareSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_PREPARE_SECONDS, seconds.coerceIn(0, 10)).apply()
        _prepareSeconds.value = seconds.coerceIn(0, 10)
    }

    // Mirror live view
    private val _mirrorLiveView = MutableStateFlow(prefs.getBoolean(KEY_MIRROR_LIVE_VIEW, true))
    val mirrorLiveView: StateFlow<Boolean> = _mirrorLiveView

    fun setMirrorLiveView(mirror: Boolean) {
        prefs.edit().putBoolean(KEY_MIRROR_LIVE_VIEW, mirror).apply()
        _mirrorLiveView.value = mirror
    }
}
