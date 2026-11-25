package com.eventuath.gexrdp_kvm.core.protocol

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * GEX/1 constants and the 10-byte connection prelude.
 * Mirrors server/gexd/protocol.py - keep both sides in sync.
 */
object GexProtocol {
    const val VERSION = 1

    const val CHANNEL_CONTROL = 0
    const val CHANNEL_DISPLAY = 1
    const val CHANNEL_INPUT = 2
    const val CHANNEL_USB = 3
    const val CHANNEL_AUDIO = 4

    const val PRELUDE_SIZE = 10
    const val MAX_TOKEN = 512
    const val MAX_CONTROL_LINE = 1 shl 20

    const val FRAME_HEADER_SIZE = 36
    const val FRAME_FULL = 1
    const val FRAME_DIRTY = 2
    const val FRAME_CONFIG = 3
    const val FRAME_PING = 4

    const val FLAG_KEYFRAME = 1

    const val FORMAT_RGBA_8888 = 0
    const val FORMAT_BGRA_8888 = 1

    const val INPUT_HEADER_SIZE = 3
    const val MAX_INPUT_PAYLOAD = 4096

    const val BUTTON_LEFT = 1
    const val BUTTON_MIDDLE = 2
    const val BUTTON_RIGHT = 3
    const val BUTTON_SIDE = 4
    const val BUTTON_EXTRA = 5

    const val MOD_SHIFT = 1
    const val MOD_CTRL = 1 shl 1
    const val MOD_ALT = 1 shl 2
    const val MOD_META = 1 shl 3
}

class ProtocolException(message: String) : IOException(message)

data class Prelude(val channel: Int, val token: String = "") {
    fun encode(): ByteArray {
        val tokenBytes = token.toByteArray(Charsets.UTF_8)
        if (tokenBytes.size > GexProtocol.MAX_TOKEN) {
            throw ProtocolException("token too long")
        }
        val out = ByteArray(GexProtocol.PRELUDE_SIZE + tokenBytes.size)
        out[0] = 'G'.code.toByte()
        out[1] = 'E'.code.toByte()
        out[2] = 'X'.code.toByte()
        out[3] = '1'.code.toByte()
        writeShortLe(out, 4, GexProtocol.VERSION)
        out[6] = channel.toByte()
        out[7] = 0
        writeShortLe(out, 8, tokenBytes.size)
        System.arraycopy(tokenBytes, 0, out, GexProtocol.PRELUDE_SIZE, tokenBytes.size)
        return out
    }
}

fun readPrelude(input: InputStream): Prelude {
    val data = DataInputStream(input)
    val head = ByteArray(GexProtocol.PRELUDE_SIZE)
    try {
        data.readFully(head)
    } catch (e: EOFException) {
        throw ProtocolException("incomplete prelude")
    }
    if (head[0] != 'G'.code.toByte() || head[1] != 'E'.code.toByte() ||
        head[2] != 'X'.code.toByte() || head[3] != '1'.code.toByte()
    ) {
        throw ProtocolException("bad magic")
    }
    val version = readShortLe(head, 4)
    if (version != GexProtocol.VERSION) {
        throw ProtocolException("unsupported protocol version $version")
    }
    val channel = head[6].toInt() and 0xFF
    val tokenLength = readShortLe(head, 8)
    if (tokenLength > GexProtocol.MAX_TOKEN) {
        throw ProtocolException("token too long")
    }
    val token = if (tokenLength > 0) {
        val raw = ByteArray(tokenLength)
        data.readFully(raw)
        String(raw, Charsets.UTF_8)
    } else {
        ""
    }
    return Prelude(channel = channel, token = token)
}

internal fun writeShortLe(target: ByteArray, offset: Int, value: Int) {
    target[offset] = (value and 0xFF).toByte()
    target[offset + 1] = ((value shr 8) and 0xFF).toByte()
}

internal fun readShortLe(source: ByteArray, offset: Int): Int {
    return (source[offset].toInt() and 0xFF) or ((source[offset + 1].toInt() and 0xFF) shl 8)
}
