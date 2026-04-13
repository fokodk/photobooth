package com.photobooth.ptp

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PtpCamera(private val usbManager: UsbManager) {

    companion object {
        private const val TAG = "PtpCamera"
        private const val SESSION_ID = 1
        private const val MAX_BUSY_RETRIES = 10
        private const val BUSY_RETRY_DELAY_MS = 300L
    }

    private val connection = PtpConnection(usbManager)
    private val ptpLock = Mutex()
    private var transactionId = 0
    @Volatile private var sessionOpen = false

    val isConnected: Boolean get() = connection.isConnected && sessionOpen

    private fun nextTransactionId(): Int = ++transactionId

    fun findDevice(): UsbDevice? = connection.findNikonDevice()

    /**
     * Quick health check - sends DeviceReady and checks if camera responds.
     */
    fun isAlive(): Boolean {
        if (!isConnected) return false
        val result = connection.runTransaction(
            PtpPacket.command(PtpConstants.OC_NIKON_DEVICE_READY, nextTransactionId())
        )
        return result.response != null
    }

    suspend fun connect(device: UsbDevice): Result<Unit> = ptpLock.withLock {
        withContext(Dispatchers.IO) {
            try {
                disconnectInternal()

                for (attempt in 1..3) {
                    Log.i(TAG, "Connection attempt $attempt/3")

                    if (!connection.open(device)) {
                        return@withContext Result.failure(Exception("Kunne ikke forbinde til kameraet. Tjek USB-tilladelse."))
                    }
                    transactionId = 0

                    val result = openSession()
                    if (result.isSuccess) {
                        return@withContext result
                    }

                    Log.w(TAG, "OpenSession attempt $attempt failed: ${result.exceptionOrNull()?.message}")
                    connection.close()

                    if (attempt < 3) {
                        val delayMs = attempt * 2000L
                        Log.i(TAG, "Waiting ${delayMs}ms before retry...")
                        delay(delayMs)
                    }
                }

                Result.failure(Exception("Kunne ikke åbne session efter 3 forsøg."))
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed", e)
                Result.failure(e)
            }
        }
    }

    fun disconnect() {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        if (sessionOpen) {
            try {
                isLiveViewActive = false
                connection.sendCommand(PtpPacket.command(PtpConstants.OC_CLOSE_SESSION, nextTransactionId()))
                connection.receiveResponse(2000)
            } catch (e: Exception) {
                Log.w(TAG, "Error closing session", e)
            }
            sessionOpen = false
        }
        connection.close()
    }

    private fun openSession(): Result<Unit> {
        // Probe with GetDeviceInfo (no session required) to check camera responds
        try {
            val probeResult = connection.runTransaction(
                PtpPacket.command(PtpConstants.OC_GET_DEVICE_INFO, nextTransactionId()),
                expectData = true,
                timeout = 5000,
            )
            Log.i(TAG, "Probe GetDeviceInfo: data=${probeResult.data?.size ?: "null"} bytes, resp=0x${probeResult.responseCode?.toUShort()?.toString(16)}")
        } catch (e: Exception) {
            Log.w(TAG, "Probe failed: ${e.message}")
        }

        // Close stale session
        try {
            connection.runTransaction(
                PtpPacket.command(PtpConstants.OC_CLOSE_SESSION, nextTransactionId())
            )
        } catch (_: Exception) {}

        // Open new session
        val result = connection.runTransaction(
            PtpPacket.command(PtpConstants.OC_OPEN_SESSION, nextTransactionId(), SESSION_ID)
        )
        Log.i(TAG, "OpenSession response: ${result.responseCode?.let { "0x${it.toUShort().toString(16)}" } ?: "null"}")

        if (result.response == null) {
            return Result.failure(Exception("Intet svar fra kamera"))
        }

        return when (result.responseCode) {
            PtpConstants.RC_OK, PtpConstants.RC_SESSION_ALREADY_OPEN -> {
                sessionOpen = true
                Log.i(TAG, "Session opened successfully")
                Result.success(Unit)
            }
            else -> {
                Result.failure(Exception("OpenSession fejlede: 0x${result.responseCode?.toUShort()?.toString(16)}"))
            }
        }
    }

    /**
     * Wait for the camera to become ready (not busy).
     */
    private suspend fun waitForReady(): Boolean {
        for (i in 0 until MAX_BUSY_RETRIES) {
            val result = connection.runTransaction(
                PtpPacket.command(PtpConstants.OC_NIKON_DEVICE_READY, nextTransactionId())
            )
            if (result.isOk) return true
            if (!result.isBusy) return true // Not busy, proceed
            delay(BUSY_RETRY_DELAY_MS)
        }
        return false
    }

    // ---- Capture ----

    suspend fun captureWithLiveView(): Result<ByteArray> = withContext(Dispatchers.IO) {
        if (!isConnected) {
            return@withContext Result.failure(Exception("Kamera ikke forbundet"))
        }

        try {
            // Stop live view if still active
            if (isLiveViewActive) {
                stopLiveView()
                delay(300)
            }

            waitForReady()
            captureInternal()
        } catch (e: Exception) {
            Log.e(TAG, "CaptureWithLiveView failed", e)
            Result.failure(e)
        }
    }

    private suspend fun captureInternal(): Result<ByteArray> {
        val capturePacket = PtpPacket.command(
            PtpConstants.OC_INITIATE_CAPTURE,
            nextTransactionId(),
            0, 0
        )

        if (!connection.sendCommand(capturePacket)) {
            sessionOpen = false
            return Result.failure(Exception("Kunne ikke sende capture-kommando"))
        }

        val captureResponse = connection.receiveResponse(PtpConstants.CAPTURE_TIMEOUT)
        if (captureResponse?.containerType == PtpConstants.CONTAINER_TYPE_RESPONSE &&
            captureResponse.code != PtpConstants.RC_OK) {
            return Result.failure(
                Exception("Capture fejlede: 0x${captureResponse.code.toUShort().toString(16)}")
            )
        }

        // Wait for events: ObjectAdded then CaptureComplete
        var objectHandle: Int? = null
        val startTime = System.currentTimeMillis()
        val timeout = PtpConstants.CAPTURE_TIMEOUT.toLong()

        while (System.currentTimeMillis() - startTime < timeout) {
            val event = connection.receiveEvent(2000)
            if (event == null) {
                delay(100)
                continue
            }
            when (event.code) {
                PtpConstants.EC_OBJECT_ADDED -> {
                    val params = PtpPacket.getParamsFromPayload(event.payload)
                    if (params.isNotEmpty()) {
                        objectHandle = params[0]
                        Log.i(TAG, "Object added: handle=0x${objectHandle.toUInt().toString(16)}")
                    }
                }
                PtpConstants.EC_CAPTURE_COMPLETE -> {
                    Log.i(TAG, "Capture complete")
                    break
                }
            }
        }

        if (objectHandle == null) {
            objectHandle = getLatestObjectHandle()
        }
        if (objectHandle == null) {
            return Result.failure(Exception("Kunne ikke finde det tagne billede"))
        }

        val imageData = getObject(objectHandle)
            ?: return Result.failure(Exception("Kunne ikke downloade billedet"))

        Log.i(TAG, "Captured image: ${imageData.size} bytes")
        return Result.success(imageData)
    }

    private fun getLatestObjectHandle(): Int? {
        val result = connection.runTransaction(
            PtpPacket.command(
                PtpConstants.OC_GET_OBJECT_HANDLES,
                nextTransactionId(),
                PtpConstants.ALL_STORAGE, 0, 0
            ),
            expectData = true,
            timeout = PtpConstants.DATA_TIMEOUT,
        )

        val data = result.data ?: return null
        if (data.size < 4) return null

        val buffer = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val count = buffer.int
        if (count == 0) return null

        var lastHandle = 0
        for (i in 0 until count) {
            if (buffer.remaining() >= 4) {
                lastHandle = buffer.int
            }
        }
        Log.i(TAG, "Found $count objects, latest handle=0x${lastHandle.toUInt().toString(16)}")
        return lastHandle
    }

    private fun getObject(objectHandle: Int): ByteArray? {
        val result = connection.runTransaction(
            PtpPacket.command(PtpConstants.OC_GET_OBJECT, nextTransactionId(), objectHandle),
            expectData = true,
            timeout = PtpConstants.DATA_TIMEOUT,
        )

        if (result.response != null && result.responseCode != PtpConstants.RC_OK) {
            Log.w(TAG, "GetObject response: 0x${result.responseCode?.toUShort()?.toString(16)}")
        }

        return result.data
    }

    // ---- Live View ----

    @Volatile var isLiveViewActive = false
        private set

    fun startLiveView(): Boolean {
        if (!isConnected) return false

        // Retry with DeviceBusy handling
        for (attempt in 1..5) {
            val result = connection.runTransaction(
                PtpPacket.command(PtpConstants.OC_NIKON_START_LIVE_VIEW, nextTransactionId())
            )
            if (result.isOk) {
                isLiveViewActive = true
                Log.i(TAG, "Live view started")
                return true
            }
            if (result.isBusy) {
                Log.i(TAG, "StartLiveView busy, retry $attempt/5")
                Thread.sleep(200)
                continue
            }
            // Other error
            Log.w(TAG, "StartLiveView response: 0x${result.responseCode?.toUShort()?.toString(16)}")
            return false
        }
        Log.w(TAG, "StartLiveView failed after 5 busy retries")
        return false
    }

    fun stopLiveView(): Boolean {
        isLiveViewActive = false
        if (!connection.isConnected) return false
        val result = connection.runTransaction(
            PtpPacket.command(PtpConstants.OC_NIKON_END_LIVE_VIEW, nextTransactionId())
        )
        return result.isOk
    }

    /**
     * Trigger autofocus during live view.
     * Must be called when live view is active and no concurrent getLiveViewFrame().
     */
    fun triggerAfDuringLiveView(): Boolean {
        if (!isConnected || !isLiveViewActive) return false
        val result = connection.runTransaction(
            PtpPacket.command(PtpConstants.OC_NIKON_AF_DRIVE, nextTransactionId())
        )
        Log.i(TAG, "AF during LV: 0x${result.responseCode?.toUShort()?.toString(16)}")
        return result.isOk
    }

    /**
     * Get one live view frame. Returns JPEG data or null.
     * Nikon live view has a proprietary header before the JPEG SOI marker.
     */
    fun getLiveViewFrame(): ByteArray? {
        if (!isConnected || !isLiveViewActive) return null

        // Use separate send/receive (not runTransaction) for live view frames
        // to avoid any interaction with synchronized/mutex patterns
        val packet = PtpPacket.command(PtpConstants.OC_NIKON_GET_LIVE_VIEW_IMG, nextTransactionId())
        if (!connection.sendCommand(packet)) {
            Log.w(TAG, "LV frame: send failed")
            return null
        }

        val data = connection.receiveData(PtpConstants.LV_TIMEOUT)

        // Consume trailing response
        connection.receiveResponse(PtpConstants.LV_TIMEOUT)

        if (data == null || data.size < 128) return null

        // Find JPEG start marker (0xFF 0xD8)
        for (i in 0 until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte()) {
                return data.copyOfRange(i, data.size)
            }
        }

        return null
    }
}
