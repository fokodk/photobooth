package com.photobooth.ptp

object PtpConstants {
    // USB
    const val NIKON_VENDOR_ID = 0x04b0

    // PTP Container Types
    const val CONTAINER_TYPE_COMMAND: Short = 1
    const val CONTAINER_TYPE_DATA: Short = 2
    const val CONTAINER_TYPE_RESPONSE: Short = 3
    const val CONTAINER_TYPE_EVENT: Short = 4

    // PTP Operation Codes
    const val OC_GET_DEVICE_INFO: Short = 0x1001
    const val OC_OPEN_SESSION: Short = 0x1002
    const val OC_CLOSE_SESSION: Short = 0x1003
    const val OC_GET_STORAGE_IDS: Short = 0x1004
    const val OC_GET_OBJECT_HANDLES: Short = 0x1007
    const val OC_GET_OBJECT_INFO: Short = 0x1008
    const val OC_GET_OBJECT: Short = 0x1009
    const val OC_DELETE_OBJECT: Short = 0x100B.toShort()
    const val OC_INITIATE_CAPTURE: Short = 0x100E.toShort()

    // Nikon-specific Operation Codes
    const val OC_NIKON_INITIATE_CAPTURE_REC_IN_MEDIA: Short = 0x90C0.toShort()
    const val OC_NIKON_AF_DRIVE: Short = 0x90C1.toShort()
    const val OC_NIKON_START_LIVE_VIEW: Short = 0x9201.toShort()
    const val OC_NIKON_END_LIVE_VIEW: Short = 0x9202.toShort()
    const val OC_NIKON_GET_LIVE_VIEW_IMG: Short = 0x9203.toShort()
    const val OC_NIKON_DEVICE_READY: Short = 0x90C8.toShort()

    // PTP Response Codes
    const val RC_OK: Short = 0x2001
    const val RC_SESSION_ALREADY_OPEN: Short = 0x201E.toShort()
    const val RC_DEVICE_BUSY: Short = 0x2019.toShort()
    const val RC_STORE_FULL: Short = 0x200C.toShort()

    // PTP Event Codes
    const val EC_OBJECT_ADDED: Short = 0x4002
    const val EC_CAPTURE_COMPLETE: Short = 0x400D.toShort()
    const val EC_STORE_FULL: Short = 0x400A.toShort()

    // Storage type for "all storage"
    const val ALL_STORAGE = 0xFFFFFFFF.toInt()

    // PTP header size (length + type + code + transactionId)
    const val HEADER_SIZE = 12

    // PTP Response Codes (additional)
    const val RC_INVALID_STATUS: Short = 0x201F.toShort()
    const val RC_DEVICE_PROP_NOT_SUPPORTED: Short = 0xA001.toShort()

    // USB transfer timeout in ms
    const val USB_TIMEOUT = 10000        // Commands and responses
    const val DATA_TIMEOUT = 30000       // Large data transfers (image download)
    const val CAPTURE_TIMEOUT = 15000    // Waiting for capture events
    const val LV_TIMEOUT = 2000          // Live view frame timeout
}
