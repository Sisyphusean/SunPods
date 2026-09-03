package com.peyi.sonypodsbattery

import java.io.ByteArrayOutputStream

data class SonyFrame(val type: Int, val sequence: Int, val payload: ByteArray)

object SonyProtocol {
    const val DATA_MDR = 0x0c
    const val ACK = 0x01
    private const val START = 0x3e
    private const val END = 0x3c
    private const val ESCAPE = 0x3d

    fun encode(type: Int, sequence: Int, payload: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(type)
        body.write(sequence and 1)
        val size = payload.size
        body.write((size ushr 24) and 0xff)
        body.write((size ushr 16) and 0xff)
        body.write((size ushr 8) and 0xff)
        body.write(size and 0xff)
        body.write(payload)
        val unescaped = body.toByteArray()
        body.write(unescaped.fold(0) { total, byte -> (total + (byte.toInt() and 0xff)) and 0xff })

        val result = ByteArrayOutputStream()
        result.write(START)
        body.toByteArray().forEach { byte ->
            when (val value = byte.toInt() and 0xff) {
                START, END, ESCAPE -> {
                    result.write(ESCAPE)
                    result.write(value - 0x10)
                }
                else -> result.write(value)
            }
        }
        result.write(END)
        return result.toByteArray()
    }

    class Decoder {
        private val body = ByteArrayOutputStream()
        private var receiving = false
        private var escaped = false

        fun feed(bytes: ByteArray): List<SonyFrame> {
            val frames = mutableListOf<SonyFrame>()
            bytes.forEach { raw ->
                val value = raw.toInt() and 0xff
                if (!receiving) {
                    if (value == START) {
                        receiving = true
                        escaped = false
                        body.reset()
                    }
                    return@forEach
                }
                if (escaped) {
                    body.write(value + 0x10)
                    escaped = false
                } else when (value) {
                    ESCAPE -> escaped = true
                    START -> body.reset()
                    END -> {
                        decodeBody(body.toByteArray())?.let(frames::add)
                        receiving = false
                        body.reset()
                    }
                    else -> body.write(value)
                }
            }
            return frames
        }

        private fun decodeBody(bytes: ByteArray): SonyFrame? {
            if (bytes.size < 7) return null
            val expectedChecksum = bytes.dropLast(1).fold(0) { total, byte ->
                (total + (byte.toInt() and 0xff)) and 0xff
            }
            if (expectedChecksum != (bytes.last().toInt() and 0xff)) return null
            val length = ((bytes[2].toInt() and 0xff) shl 24) or
                ((bytes[3].toInt() and 0xff) shl 16) or
                ((bytes[4].toInt() and 0xff) shl 8) or
                (bytes[5].toInt() and 0xff)
            if (length < 0 || bytes.size != length + 7) return null
            return SonyFrame(
                bytes[0].toInt() and 0xff,
                bytes[1].toInt() and 1,
                bytes.copyOfRange(6, 6 + length)
            )
        }
    }
}
