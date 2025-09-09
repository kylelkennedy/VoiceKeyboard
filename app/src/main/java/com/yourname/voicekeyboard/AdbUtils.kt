package com.yourname.voicekeyboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbUtils {
    companion object {
        private const val ADB_VERSION = 0x01000001
        private const val ADB_MAXDATA = 4096
        private const val A_CNXN = 0x4e584e43
        private const val A_OKAY = 0x59414b4f
        private const val A_WRTE = 0x45545257
        private const val A_CLSE = 0x45534c43
        private const val A_OPEN = 0x4e45504f
    }

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var localId = 1
    private var remoteId = 0
    private var isConnected = false

    suspend fun connect(host: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            socket = Socket(host, port)
            socket?.soTimeout = 5000 // 5 second timeout
            outputStream = socket?.getOutputStream()
            inputStream = socket?.getInputStream()

            // Send CNXN packet
            val systemType = "host::\u0000"
            val banner = "device::\u0000"

            sendPacket(A_CNXN, ADB_VERSION, ADB_MAXDATA, systemType.toByteArray())

            // Read response
            val response = readPacket()
            if (response?.command == A_CNXN) {
                isConnected = true
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            disconnect()
            false
        }
    }

    suspend fun sendKeyEvent(keyCode: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext false

        try {
            // Open shell service
            val shellService = "shell:input keyevent $keyCode\u0000"
            sendPacket(A_OPEN, localId, 0, shellService.toByteArray())

            // Read OKAY response
            val openResponse = readPacket()
            if (openResponse?.command != A_OKAY) {
                return@withContext false
            }

            remoteId = openResponse.arg0

            // Wait for command completion (we might get WRTE or CLSE)
            val resultResponse = readPacket()

            // Send close
            sendPacket(A_CLSE, localId, remoteId, ByteArray(0))

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun sendPacket(command: Int, arg0: Int, arg1: Int, data: ByteArray) {
        val header = ByteBuffer.allocate(24).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            putInt(command)
            putInt(arg0)
            putInt(arg1)
            putInt(data.size)
            putInt(checksum(data))
            putInt(command xor 0xffffffff.toInt())
        }

        outputStream?.write(header.array())
        if (data.isNotEmpty()) {
            outputStream?.write(data)
        }
        outputStream?.flush()
    }

    private fun readPacket(): AdbPacket? {
        return try {
            val headerBytes = ByteArray(24)
            var totalRead = 0

            while (totalRead < 24) {
                val bytesRead = inputStream?.read(headerBytes, totalRead, 24 - totalRead) ?: -1
                if (bytesRead == -1) return null
                totalRead += bytesRead
            }

            val header = ByteBuffer.wrap(headerBytes).apply {
                order(ByteOrder.LITTLE_ENDIAN)
            }

            val command = header.getInt()
            val arg0 = header.getInt()
            val arg1 = header.getInt()
            val dataLength = header.getInt()
            val dataCheck = header.getInt()
            val magic = header.getInt()

            val data = if (dataLength > 0) {
                val dataBytes = ByteArray(dataLength)
                var dataRead = 0
                while (dataRead < dataLength) {
                    val bytesRead = inputStream?.read(dataBytes, dataRead, dataLength - dataRead) ?: -1
                    if (bytesRead == -1) return null
                    dataRead += bytesRead
                }
                dataBytes
            } else {
                ByteArray(0)
            }

            AdbPacket(command, arg0, arg1, data)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun checksum(data: ByteArray): Int {
        var sum = 0
        for (byte in data) {
            sum += byte.toInt() and 0xFF
        }
        return sum
    }

    fun disconnect() {
        try {
            if (isConnected) {
                // Send close if we have an active connection
                if (remoteId != 0) {
                    sendPacket(A_CLSE, localId, remoteId, ByteArray(0))
                }
            }

            outputStream?.close()
            inputStream?.close()
            socket?.close()
            isConnected = false
            remoteId = 0
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private data class AdbPacket(
        val command: Int,
        val arg0: Int,
        val arg1: Int,
        val data: ByteArray
    )
}