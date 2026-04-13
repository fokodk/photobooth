package com.photobooth

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Minimal HTTP server that serves a single JPEG photo.
 * Runs on a background thread. People on the same WiFi can
 * download the photo by visiting http://<tablet-ip>:8080
 */
class PhotoServer(private val context: Context) {

    companion object {
        private const val TAG = "PhotoServer"
        private const val PORT = 8080
    }

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    @Volatile private var currentPhoto: ByteArray? = null
    @Volatile private var running = false

    val port: Int get() = PORT

    fun getLocalIpAddress(): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifiManager.connectionInfo.ipAddress
        if (ip == 0) return null
        return "${ip and 0xFF}.${ip shr 8 and 0xFF}.${ip shr 16 and 0xFF}.${ip shr 24 and 0xFF}"
    }

    fun start() {
        if (running) return
        running = true
        serverThread = thread(name = "PhotoServer", isDaemon = true) {
            try {
                serverSocket = ServerSocket(PORT)
                Log.i(TAG, "Server started on port $PORT")
                while (running) {
                    try {
                        val socket = serverSocket?.accept() ?: break
                        thread(isDaemon = true) { handleClient(socket) }
                    } catch (e: Exception) {
                        if (running) Log.w(TAG, "Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server failed to start", e)
            }
        }
    }

    fun stop() {
        running = false
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverThread = null
        currentPhoto = null
    }

    fun setPhoto(data: ByteArray) {
        currentPhoto = data
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 5000
            // Read the HTTP request (we don't care about the details)
            val input = socket.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            // Consume rest of headers
            while (true) {
                val line = input.readLine() ?: break
                if (line.isEmpty()) break
            }

            val output = BufferedOutputStream(socket.getOutputStream())
            val photo = currentPhoto

            if (photo != null && requestLine.contains("GET")) {
                // Serve the photo as a downloadable JPEG
                val headers = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: image/jpeg\r\n")
                    append("Content-Length: ${photo.size}\r\n")
                    append("Content-Disposition: attachment; filename=\"photobooth.jpg\"\r\n")
                    append("Cache-Control: no-cache\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                output.write(headers.toByteArray())
                output.write(photo)
            } else {
                val body = "<html><body><h1>Ingen billede endnu</h1></body></html>"
                val headers = buildString {
                    append("HTTP/1.1 404 Not Found\r\n")
                    append("Content-Type: text/html; charset=utf-8\r\n")
                    append("Content-Length: ${body.toByteArray().size}\r\n")
                    append("Connection: close\r\n")
                    append("\r\n")
                }
                output.write(headers.toByteArray())
                output.write(body.toByteArray())
            }
            output.flush()
        } catch (_: Exception) {
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
