package com.savestate.app.data

import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for PSP PARAM.SFO files
 * SFO files contain metadata about PSP games including the title
 */
object SfoParser {
    
    private const val TAG = "SfoParser"
    
    // SFO Magic number: "\x00PSF"
    private val SFO_MAGIC = byteArrayOf(0x00, 0x50, 0x53, 0x46) // \x00PSF
    
    /**
     * Parse a PARAM.SFO file to extract the game title
     * 
     * @param sfoPath Path to the PARAM.SFO file
     * @return The game title, or null if parsing fails
     */
    fun parseParamSfo(sfoPath: String): String? {
        val file = File(sfoPath)
        if (!file.exists()) {
            Log.e(TAG, "PARAM.SFO file not found: $sfoPath")
            return null
        }
        
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val data = ByteArray(file.length().toInt())
                raf.readFully(data)
                parseFromBytes(data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing $sfoPath: ${e.message}", e)
            null
        }
    }

    /**
     * Parse PARAM.SFO from a raw byte array to extract the game title.
     * Useful when reading via SAF InputStream.
     */
    fun parseFromBytes(data: ByteArray): String? {
        try {
            if (data.size < 20) return null
            if (!data.slice(0..3).toByteArray().contentEquals(SFO_MAGIC)) {
                Log.w(TAG, "Invalid SFO magic number")
                return null
            }

            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(8)

            val keyTableStart = buffer.int
            val dataTableStart = buffer.int
            val numEntries = buffer.int

            val indexTableOffset = 20

            for (i in 0 until numEntries) {
                val entryOffset = indexTableOffset + (i * 16)
                buffer.position(entryOffset)

                val keyOffset = buffer.short.toInt() and 0xFFFF
                buffer.short // data_fmt
                val dataLen = buffer.int
                buffer.int   // data_max_len
                val dataOffset = buffer.int

                val keyStart = keyTableStart + keyOffset
                var keyEnd = keyStart
                while (keyEnd < data.size && data[keyEnd] != 0.toByte()) {
                    keyEnd++
                }
                val key = String(data, keyStart, keyEnd - keyStart, Charsets.UTF_8)

                if (key == "TITLE") {
                    val valueStart = dataTableStart + dataOffset
                    val valueEnd = minOf(valueStart + dataLen, data.size)
                    val rawValue = data.sliceArray(valueStart until valueEnd)

                    val nullPos = rawValue.indexOf(0.toByte())
                    val title = if (nullPos != -1) {
                        String(rawValue, 0, nullPos, Charsets.UTF_8)
                    } else {
                        String(rawValue, Charsets.UTF_8)
                    }.trim()

                    Log.d(TAG, "Extracted title '$title'")
                    return title
                }
            }

            Log.w(TAG, "'TITLE' key not found in SFO data")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing SFO bytes: ${e.message}", e)
            return null
        }
    }
}
