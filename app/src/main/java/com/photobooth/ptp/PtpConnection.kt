package com.photobooth.ptp

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Result of a PTP transaction (command + optional data + response).
 */
class TransactionResult(
    val response: PtpPacket?,
    val data: ByteArray? = null,
) {
    val isOk: Boolean get() = response?.code == PtpConstants.RC_OK
    val isBusy: Boolean get() = response?.code == PtpConstants.RC_DEVICE_BUSY
    val responseCode: Short? get() = response?.code
}

class PtpConnection(private val usbManager: UsbManager) {

    companion object {
        private const val TAG = "PtpConnection"
        private const val USB_CLASS_PTP = 6
        private const val READ_BUFFER_SIZE = 16384  // 16KB - matches remoteyourcam-usb
        private const val MAX_ZERO_READS = 50       // Max 0-byte reads before giving up
    }

    private var device: UsbDevice? = null
    private var connection: UsbDeviceConnection? = null
    private var ptpInterface: UsbInterface? = null
    private var endpointIn: UsbEndpoint? = null
    private var endpointOut: UsbEndpoint? = null
    private var endpointInterrupt: UsbEndpoint? = null

    // Reusable read buffer - avoids allocating 512KB per read
    private val readBuffer = ByteArray(READ_BUFFER_SIZE)

    val isConnected: Boolean get() = connection != null

    fun findNikonDevice(): UsbDevice? {
        for (dev in usbManager.deviceList.values) {
            if (dev.vendorId == PtpConstants.NIKON_VENDOR_ID) {
                Log.i(TAG, "Found Nikon: ${dev.deviceName}")
                return dev
            }
        }
        for (dev in usbManager.deviceList.values) {
            for (i in 0 until dev.interfaceCount) {
                if (dev.getInterface(i).interfaceClass == USB_CLASS_PTP) {
                    Log.i(TAG, "Found PTP device: ${dev.deviceName}")
                    return dev
                }
            }
        }
        return null
    }

    fun open(dev: UsbDevice): Boolean {
        if (isConnected) close()

        var iface: UsbInterface? = null
        for (i in 0 until dev.interfaceCount) {
            val candidate = dev.getInterface(i)
            if (candidate.interfaceClass == USB_CLASS_PTP) {
                iface = candidate
                break
            }
        }
        if (iface == null) {
            Log.e(TAG, "No PTP interface found")
            return false
        }

        val conn = usbManager.openDevice(dev)
        if (conn == null) {
            Log.e(TAG, "Failed to open USB device")
            return false
        }

        if (!conn.claimInterface(iface, true)) {
            Log.e(TAG, "Failed to claim interface")
            conn.close()
            return false
        }

        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        var epInterrupt: UsbEndpoint? = null

        for (i in 0 until iface.endpointCount) {
            val ep = iface.getEndpoint(i)
            when (ep.type) {
                android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_BULK -> {
                    if (ep.direction == android.hardware.usb.UsbConstants.USB_DIR_IN) epIn = ep
                    else epOut = ep
                }
                android.hardware.usb.UsbConstants.USB_ENDPOINT_XFER_INT -> epInterrupt = ep
            }
        }

        if (epIn == null || epOut == null) {
            Log.e(TAG, "Missing bulk endpoints")
            conn.releaseInterface(iface)
            conn.close()
            return false
        }

        device = dev
        connection = conn
        ptpInterface = iface
        endpointIn = epIn
        endpointOut = epOut
        endpointInterrupt = epInterrupt

        Log.i(TAG, "USB opened: ${dev.deviceName}")

        // PTP Device Reset
        try {
            conn.controlTransfer(0x21, 0x66, 0, 0, null, 0, PtpConstants.USB_TIMEOUT)
            Thread.sleep(2500)
        } catch (e: Exception) {
            Log.w(TAG, "PTP reset failed (non-fatal)", e)
            Thread.sleep(1000)
        }

        // Clear halt on endpoints
        try {
            conn.controlTransfer(0x02, 0x01, 0, epIn.address, null, 0, PtpConstants.USB_TIMEOUT)
            conn.controlTransfer(0x02, 0x01, 0, epOut.address, null, 0, PtpConstants.USB_TIMEOUT)
        } catch (_: Exception) {}

        // Drain stale data
        try {
            val drainBuf = ByteArray(512)
            while (conn.bulkTransfer(epIn, drainBuf, drainBuf.size, 100) > 0) {}
        } catch (_: Exception) {}

        Log.i(TAG, "Connection ready")
        return true
    }

    fun close() {
        connection?.let { conn ->
            ptpInterface?.let { conn.releaseInterface(it) }
            conn.close()
        }
        connection = null
        device = null
        ptpInterface = null
        endpointIn = null
        endpointOut = null
        endpointInterrupt = null
    }

    // ---- Low-level USB I/O with retry ----

    /**
     * Send raw bytes to the OUT endpoint.
     * Returns true on success.
     */
    private fun bulkWrite(data: ByteArray, timeout: Int = 3000): Boolean {
        val conn = connection ?: return false
        val ep = endpointOut ?: return false
        val sent = conn.bulkTransfer(ep, data, data.size, timeout)
        if (sent < 0) {
            Log.e(TAG, "bulkWrite failed: sent=$sent, size=${data.size}")
            return false
        }
        return true
    }

    /**
     * Read one USB packet from IN endpoint into the reusable buffer.
     * Handles 0-byte reads by retrying (remoteyourcam-usb pattern).
     * Returns number of bytes read, or -1 on error.
     */
    private fun bulkRead(timeout: Int = PtpConstants.USB_TIMEOUT): Int {
        val conn = connection ?: return -1
        val ep = endpointIn ?: return -1

        var zeroCount = 0
        while (zeroCount < MAX_ZERO_READS) {
            val received = conn.bulkTransfer(ep, readBuffer, readBuffer.size, timeout)
            if (received > 0) return received
            if (received < 0) return -1  // Real error
            // received == 0: transient, retry
            zeroCount++
        }
        Log.e(TAG, "Too many zero-byte reads ($MAX_ZERO_READS)")
        return -1
    }

    // ---- Atomic PTP Transaction ----

    /**
     * Execute a complete PTP transaction atomically:
     * 1. Send command
     * 2. Optionally receive data phase
     * 3. Receive response
     *
     * This prevents protocol desync by always consuming all phases.
     *
     * @param command The PTP command packet to send
     * @param expectData Whether to expect a data phase before the response
     * @param timeout Timeout for data reads (commands use USB_TIMEOUT)
     */
    fun runTransaction(
        command: PtpPacket,
        expectData: Boolean = false,
        timeout: Int = PtpConstants.USB_TIMEOUT,
    ): TransactionResult {
        // Phase 1: Send command
        if (!bulkWrite(command.toByteArray())) {
            Log.e(TAG, "Transaction send failed: 0x${command.code.toUShort().toString(16)}")
            return TransactionResult(null)
        }

        // Phase 2: Read first packet
        val firstRead = bulkRead(timeout)
        if (firstRead < PtpConstants.HEADER_SIZE) {
            if (firstRead != -1) Log.e(TAG, "Transaction first read too short: $firstRead")
            return TransactionResult(null)
        }

        val firstPacket = PtpPacket.fromByteArray(readBuffer.copyOf(firstRead))

        // If we got a Response directly (no data phase)
        if (firstPacket.containerType == PtpConstants.CONTAINER_TYPE_RESPONSE) {
            return TransactionResult(firstPacket)
        }

        // If we got a Data phase
        if (firstPacket.containerType == PtpConstants.CONTAINER_TYPE_DATA) {
            val totalLength = ByteBuffer.wrap(readBuffer, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val dataOffset = PtpConstants.HEADER_SIZE
            val dataLength = totalLength - dataOffset

            val data = ByteArray(dataLength)
            val firstDataSize = minOf(firstRead - dataOffset, dataLength)
            System.arraycopy(readBuffer, dataOffset, data, 0, firstDataSize)

            var received = firstDataSize
            while (received < dataLength) {
                val chunkRead = bulkRead(timeout)
                if (chunkRead < 0) {
                    Log.e(TAG, "Transaction data chunk failed at $received/$dataLength")
                    return TransactionResult(null, null)
                }
                val toCopy = minOf(chunkRead, dataLength - received)
                System.arraycopy(readBuffer, 0, data, received, toCopy)
                received += toCopy
            }

            // Phase 3: Read response after data
            val respRead = bulkRead(PtpConstants.USB_TIMEOUT)
            val response = try {
                if (respRead >= PtpConstants.HEADER_SIZE) {
                    PtpPacket.fromByteArray(readBuffer.copyOf(respRead))
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Response parse failed after data: ${e.message}")
                null
            }

            return TransactionResult(response, data)
        }

        // Unexpected packet type
        Log.w(TAG, "Unexpected packet type: ${firstPacket.containerType}")
        return TransactionResult(null)
    }

    // ---- Convenience methods for operations without runTransaction ----

    fun sendCommand(packet: PtpPacket): Boolean {
        val conn = connection
        if (conn == null) {
            Log.e(TAG, "sendCommand: connection is null! code=0x${packet.code.toUShort().toString(16)}")
            return false
        }
        return bulkWrite(packet.toByteArray())
    }

    fun receiveResponse(timeout: Int = PtpConstants.USB_TIMEOUT): PtpPacket? {
        val received = bulkRead(timeout)
        if (received < PtpConstants.HEADER_SIZE) return null
        return PtpPacket.fromByteArray(readBuffer.copyOf(received))
    }

    /**
     * Receive data phase. Reads multi-packet data into a single ByteArray.
     * Uses the reusable readBuffer for chunk reads.
     */
    fun receiveData(timeout: Int = PtpConstants.DATA_TIMEOUT): ByteArray? {
        val firstRead = bulkRead(timeout)
        if (firstRead < PtpConstants.HEADER_SIZE) {
            if (firstRead != -1) Log.e(TAG, "Data header failed: $firstRead bytes")
            return null
        }

        val totalLength = ByteBuffer.wrap(readBuffer, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
        val dataOffset = PtpConstants.HEADER_SIZE
        val dataLength = totalLength - dataOffset

        val result = ByteArray(dataLength)
        val firstDataSize = minOf(firstRead - dataOffset, dataLength)
        System.arraycopy(readBuffer, dataOffset, result, 0, firstDataSize)

        var received = firstDataSize
        while (received < dataLength) {
            val chunkRead = bulkRead(timeout)
            if (chunkRead < 0) {
                Log.e(TAG, "Data chunk failed at $received/$dataLength")
                return null
            }
            val toCopy = minOf(chunkRead, dataLength - received)
            System.arraycopy(readBuffer, 0, result, received, toCopy)
            received += toCopy
        }

        return result
    }

    fun receiveEvent(timeout: Int = PtpConstants.CAPTURE_TIMEOUT): PtpPacket? {
        val conn = connection ?: return null
        val ep = endpointInterrupt ?: return null
        val buffer = ByteArray(64)

        val received = conn.bulkTransfer(ep, buffer, buffer.size, timeout)
        if (received < PtpConstants.HEADER_SIZE) return null

        return PtpPacket.fromByteArray(buffer.copyOf(received))
    }
}
