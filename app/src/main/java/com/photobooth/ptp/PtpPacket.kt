package com.photobooth.ptp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PTP packet structure:
 * - Container Length (4 bytes, uint32, little-endian)
 * - Container Type (2 bytes, uint16)
 * - Operation/Response Code (2 bytes, uint16)
 * - Transaction ID (4 bytes, uint32)
 * - Payload (variable)
 */
class PtpPacket(
    val containerType: Short,
    val code: Short,
    val transactionId: Int,
    val payload: ByteArray = ByteArray(0)
) {
    val length: Int get() = PtpConstants.HEADER_SIZE + payload.size

    fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(length)
        buffer.putShort(containerType)
        buffer.putShort(code)
        buffer.putInt(transactionId)
        if (payload.isNotEmpty()) {
            buffer.put(payload)
        }
        return buffer.array()
    }

    companion object {
        fun command(code: Short, transactionId: Int, vararg params: Int): PtpPacket {
            val payload = if (params.isNotEmpty()) {
                val buf = ByteBuffer.allocate(params.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                params.forEach { buf.putInt(it) }
                buf.array()
            } else {
                ByteArray(0)
            }
            return PtpPacket(PtpConstants.CONTAINER_TYPE_COMMAND, code, transactionId, payload)
        }

        fun fromByteArray(data: ByteArray): PtpPacket {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val length = buffer.int
            val type = buffer.short
            val code = buffer.short
            val txId = buffer.int
            val payloadSize = length - PtpConstants.HEADER_SIZE
            val payload = if (payloadSize > 0 && buffer.remaining() >= payloadSize) {
                ByteArray(payloadSize).also { buffer.get(it) }
            } else {
                ByteArray(0)
            }
            return PtpPacket(type, code, txId, payload)
        }

        fun getParamsFromPayload(payload: ByteArray): List<Int> {
            if (payload.isEmpty()) return emptyList()
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            val params = mutableListOf<Int>()
            while (buffer.remaining() >= 4) {
                params.add(buffer.int)
            }
            return params
        }
    }
}
