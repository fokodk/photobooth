package com.photobooth

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.photobooth.ptp.PtpConstants
import com.photobooth.ui.PhotoboothApp
import com.photobooth.ui.theme.PhotoboothTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val ACTION_USB_PERMISSION = "com.photobooth.USB_PERMISSION"
    }

    private lateinit var usbManager: UsbManager
    private lateinit var viewModel: PhotoboothViewModel

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    if (granted && device != null) {
                        Log.d(TAG, "USB permission granted for ${device.deviceName}")
                        viewModel.connectCamera(device)
                    } else {
                        Log.w(TAG, "USB permission denied")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Log.d(TAG, "USB device attached")
                    tryConnectCamera()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Log.d(TAG, "USB device detached")
                    viewModel.onCameraDetached()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fullscreen / immersive mode
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())

        usbManager = getSystemService(USB_SERVICE) as UsbManager
        viewModel = PhotoboothViewModel(applicationContext, usbManager)

        // Register USB receivers
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }

        // Try to connect to camera if already attached
        tryConnectCamera()

        setContent {
            PhotoboothTheme {
                PhotoboothApp(viewModel = viewModel)
            }
        }
    }

    private fun tryConnectCamera() {
        // Look for Nikon camera in connected USB devices
        for (device in usbManager.deviceList.values) {
            if (device.vendorId == PtpConstants.NIKON_VENDOR_ID || isUsbPtpDevice(device)) {
                if (usbManager.hasPermission(device)) {
                    viewModel.connectCamera(device)
                } else {
                    requestUsbPermission(device)
                }
                return
            }
        }
        Log.d(TAG, "No camera found among ${usbManager.deviceList.size} USB devices")
    }

    private fun isUsbPtpDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == 6) return true // Still Image class
        }
        return false
    }

    private fun requestUsbPermission(device: UsbDevice) {
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(packageName)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(this, 0, intent, flags)
        usbManager.requestPermission(device, permissionIntent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTop: USB_DEVICE_ATTACHED comes here instead of recreating the activity
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            Log.d(TAG, "USB device attached (onNewIntent)")
            tryConnectCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }
}
