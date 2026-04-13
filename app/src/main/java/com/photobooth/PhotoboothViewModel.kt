package com.photobooth

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photobooth.ptp.PtpCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhotoboothViewModel(
    private val context: Context,
    private val usbManager: UsbManager,
) : ViewModel() {

    companion object {
        private const val TAG = "PhotoboothVM"
        private const val CAPTURE_MAX_RETRIES = 3
        private const val DEBOUNCE_MS = 2000L
        private const val LV_MAX_ERRORS = 5
    }

    sealed class UiState {
        data object NotConnected : UiState()
        data object Idle : UiState()
        data object Countdown : UiState()
        data object Capturing : UiState()
        data class Preview(val photoFile: java.io.File) : UiState()
        data class Error(val message: String) : UiState()
    }

    private val camera = PtpCamera(usbManager)
    val photoServer = PhotoServer(context)
    val settings = AppSettings(context)

    init {
        photoServer.start()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.NotConnected)
    val uiState: StateFlow<UiState> = _uiState

    private val _photoCount = MutableStateFlow(0)
    val photoCount: StateFlow<Int> = _photoCount

    private val _liveViewFrame = MutableStateFlow<Bitmap?>(null)
    val liveViewFrame: StateFlow<Bitmap?> = _liveViewFrame

    private var liveViewJob: Job? = null
    private var reconnectJob: Job? = null
    private var lastCaptureTime = 0L
    @Volatile private var afRequested = false

    private var connectJob: Job? = null
    @Volatile private var isConnecting = false

    fun connectCamera(device: UsbDevice? = null) {
        // Already connected or in the process of connecting? Do nothing.
        if (camera.isConnected || isConnecting) {
            Log.i(TAG, "Already connected/connecting, ignoring connectCamera()")
            return
        }

        // Cancel any ongoing reconnect or connect
        reconnectJob?.cancel()
        reconnectJob = null
        connectJob?.cancel()

        isConnecting = true
        connectJob = viewModelScope.launch {
            // Full cleanup first
            liveViewJob?.cancel()
            liveViewJob = null
            _liveViewFrame.value = null

            val dev = device ?: camera.findDevice()
            if (dev == null) {
                _uiState.value = UiState.NotConnected
                return@launch
            }

            try {
                val result = camera.connect(dev)
                if (result.isSuccess) {
                    Log.i(TAG, "Camera connected")
                    _uiState.value = UiState.Idle
                } else {
                    Log.e(TAG, "Connection failed", result.exceptionOrNull())
                    camera.disconnect()
                    _uiState.value = UiState.Error(
                        result.exceptionOrNull()?.message ?: "Forbindelse fejlede"
                    )
                }
            } finally {
                isConnecting = false
            }
        }
    }

    private fun handleConnectionLost() {
        Log.w(TAG, "Connection lost, cleaning up...")
        isConnecting = false
        connectJob?.cancel()
        liveViewJob?.cancel()
        liveViewJob = null
        _liveViewFrame.value = null
        camera.disconnect()
        _uiState.value = UiState.NotConnected
        startAutoReconnect()
    }

    private fun startAutoReconnect() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            for (attempt in 1..30) {
                delay(2000)
                if (!isActive) return@launch
                // Skip if already connected (e.g. connectCamera() ran first)
                if (camera.isConnected) {
                    Log.i(TAG, "Already connected, stopping auto-reconnect")
                    _uiState.value = UiState.Idle
                    return@launch
                }
                val dev = camera.findDevice()
                if (dev != null) {
                    isConnecting = true
                    try {
                        val result = camera.connect(dev)
                        if (result.isSuccess) {
                            Log.i(TAG, "Auto-reconnected on attempt $attempt")
                            _uiState.value = UiState.Idle
                            return@launch
                        }
                        camera.disconnect()
                    } finally {
                        isConnecting = false
                    }
                }
            }
            Log.w(TAG, "Auto-reconnect gave up")
        }
    }

    private fun startLiveView() {
        liveViewJob?.cancel()
        liveViewJob = viewModelScope.launch(Dispatchers.IO) {
            delay(300)

            var started = false
            for (attempt in 1..3) {
                if (!camera.isConnected) {
                    withContext(Dispatchers.Main) { handleConnectionLost() }
                    return@launch
                }
                started = camera.startLiveView()
                if (started) break
                delay(1000)
            }
            if (!started) {
                Log.w(TAG, "Could not start live view")
                return@launch
            }

            // Camera needs time to set up live view sensor readout.
            // Poll DeviceReady until camera reports OK.
            for (i in 1..15) {
                delay(200)
                if (camera.isAlive()) {
                    Log.i(TAG, "Camera ready for LV frames after ${i * 200}ms")
                    break
                }
            }

            var consecutiveErrors = 0
            val targetFrameTime = 66L // ~15fps
            while (isActive && camera.isLiveViewActive) {
                val frameStart = System.currentTimeMillis()
                try {
                    if (afRequested) {
                        afRequested = false
                        camera.triggerAfDuringLiveView()
                        delay(1500)
                    }

                    val jpegData = camera.getLiveViewFrame()
                    if (jpegData != null && jpegData.size > 100) {
                        val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
                        if (bitmap != null) {
                            _liveViewFrame.value = bitmap
                            consecutiveErrors = 0
                        }
                    } else {
                        consecutiveErrors++
                        if (consecutiveErrors > LV_MAX_ERRORS) {
                            Log.w(TAG, "Live view: $LV_MAX_ERRORS consecutive errors")
                            withContext(Dispatchers.Main) { handleConnectionLost() }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    consecutiveErrors++
                    if (consecutiveErrors > LV_MAX_ERRORS) {
                        withContext(Dispatchers.Main) { handleConnectionLost() }
                        return@launch
                    }
                    delay(200)
                }

                val elapsed = System.currentTimeMillis() - frameStart
                if (elapsed < targetFrameTime) {
                    delay(targetFrameTime - elapsed)
                }
            }
        }
    }

    private suspend fun stopLiveView() {
        val job = liveViewJob
        liveViewJob = null
        _liveViewFrame.value = null
        job?.cancel()
        job?.join()
        withContext(Dispatchers.IO) {
            try { camera.stopLiveView() } catch (_: Exception) {}
        }
    }

    fun triggerAutofocus() {
        afRequested = true
    }

    fun startCountdown() {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < DEBOUNCE_MS) return
        lastCaptureTime = now

        // Quick health check before starting
        viewModelScope.launch(Dispatchers.IO) {
            if (!camera.isConnected || !camera.isAlive()) {
                withContext(Dispatchers.Main) { handleConnectionLost() }
                return@launch
            }
            withContext(Dispatchers.Main) {
                startLiveView()
                _uiState.value = UiState.Countdown
            }
        }
    }

    fun capturePhoto() {
        _uiState.value = UiState.Capturing

        viewModelScope.launch {
            stopLiveView()
            delay(500)

            var lastError: String? = null
            for (attempt in 1..CAPTURE_MAX_RETRIES) {
                if (!camera.isConnected) {
                    handleConnectionLost()
                    return@launch
                }
                val result = camera.captureWithLiveView()
                if (result.isSuccess) {
                    val imageData = result.getOrThrow()
                    savePhoto(imageData)
                    val cacheFile = java.io.File(context.cacheDir, "last_photo.jpg")
                    cacheFile.writeBytes(imageData)
                    photoServer.setPhoto(imageData)
                    _photoCount.value++
                    _uiState.value = UiState.Preview(cacheFile)
                    return@launch
                }
                lastError = result.exceptionOrNull()?.message
                Log.w(TAG, "Capture attempt $attempt failed: $lastError")
                if (attempt < CAPTURE_MAX_RETRIES) delay(1500)
            }
            _uiState.value = UiState.Error(lastError ?: "Billedet kunne ikke tages")
        }
    }

    fun dismissPreview() {
        _uiState.value = UiState.Idle
    }

    fun retakeFromPreview() {
        _uiState.value = UiState.Countdown
        startLiveView()
    }

    fun resetToIdle() {
        viewModelScope.launch {
            stopLiveView()
            if (camera.isConnected) {
                _uiState.value = UiState.Idle
            } else {
                connectCamera()
            }
        }
    }

    fun onCameraDetached() {
        handleConnectionLost()
    }

    /**
     * Apply watermark text to JPEG data. Returns new JPEG bytes with watermark.
     */
    private fun applyWatermark(imageData: ByteArray, text: String): ByteArray {
        val original = BitmapFactory.decodeByteArray(imageData, 0, imageData.size) ?: return imageData
        val bmp = original.copy(Bitmap.Config.ARGB_8888, true)
        original.recycle()

        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt()  // white, 60% opacity
            textSize = bmp.height * 0.035f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(bmp.height * 0.005f, 2f, 2f, 0x80000000.toInt())
        }
        val x = bmp.width * 0.03f
        val y = bmp.height - bmp.height * 0.03f
        canvas.drawText(text, x, y, paint)

        val stream = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        bmp.recycle()
        return stream.toByteArray()
    }

    private suspend fun savePhoto(imageData: ByteArray) = withContext(Dispatchers.IO) {
        try {
            // Apply watermark if enabled
            val finalData = if (settings.watermarkEnabled.value && settings.watermarkText.value.isNotBlank()) {
                applyWatermark(imageData, settings.watermarkText.value)
            } else {
                imageData
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
            val filename = "Photobooth_$timestamp.jpg"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Photobooth")
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues,
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(finalData)
                }
                Log.i(TAG, "Saved: $filename (${finalData.size} bytes)")
            }

            // Also update cache file and photo server with watermarked version
            if (finalData !== imageData) {
                val cacheFile = java.io.File(context.cacheDir, "last_photo.jpg")
                cacheFile.writeBytes(finalData)
                photoServer.setPhoto(finalData)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Save failed", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectJob?.cancel()
        liveViewJob?.cancel()
        reconnectJob?.cancel()
        camera.disconnect()
        photoServer.stop()
    }
}
