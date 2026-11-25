package com.eventuath.gexrdp_kvm.core.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 36-byte frame header used by the display channel.
 *
 * offset size field
 * 0      1    type
 * 1      1    flags
 * 2      2    reserved
 * 4      4    seq
 * 8      8    timestampMs
 * 16     2    x
 * 18     2    y
 * 20     2    width
 * 22     2    height
 * 24     4    stride
 * 28     1    format
 * 29     1    reserved
 * 30     2    reserved
 * 32     4    payloadLen
 */
data class FrameHeader(
    val type: Int,
    val flags: Int,
    val seq: Int,
    val timestampMs: Long,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val stride: Int,
    val format: Int,
    val payloadLen: Int,
) {
    val isKeyframe: Boolean get() = flags and GexProtocol.FLAG_KEYFRAME != 0
    val isFull: Boolean get() = type == GexProtocol.FRAME_FULL
}

object FrameCodec {
    fun decode(raw: ByteArray): FrameHeader {
        if (raw.size < GexProtocol.FRAME_HEADER_SIZE) {
            throw ProtocolException("frame header too small")
        }
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        return FrameHeader(
            type = buffer.get(0).toInt() and 0xFF,
            flags = buffer.get(1).toInt() and 0xFF,
            seq = buffer.getInt(4),
            timestampMs = buffer.getLong(8),
            x = buffer.getShort(16).toInt() and 0xFFFF,
            y = buffer.getShort(18).toInt() and 0xFFFF,
            width = buffer.getShort(20).toInt() and 0xFFFF,
            height = buffer.getShort(22).toInt() and 0xFFFF,
            stride = buffer.getInt(24),
            format = buffer.get(28).toInt() and 0xFF,
            payloadLen = buffer.getInt(32),
        )
    }

    fun encode(header: FrameHeader): ByteArray {
        val buffer = ByteBuffer.allocate(GexProtocol.FRAME_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(header.type.toByte())
        buffer.put(header.flags.toByte())
        buffer.putShort(0)
        buffer.putInt(header.seq)
        buffer.putLong(header.timestampMs)
        buffer.putShort(header.x.toShort())
        buffer.putShort(header.y.toShort())
        buffer.putShort(header.width.toShort())
        buffer.putShort(header.height.toShort())
        buffer.putInt(header.stride)
        buffer.put(header.format.toByte())
        buffer.put(0)
        buffer.putShort(0)
        buffer.putInt(header.payloadLen)
        return buffer.array()
    }
}

/**
 * Config payload is JSON, parsed with org.json on Android. This builder keeps
 * the protocol package free of Android-only dependencies for JVM tests.
 */
fun configJson(width: Int, height: Int, format: String = "rgba8888", scale: Double = 1.0): String =
    """{"width":$width,"height":$height,"format":"$format","scale":$scale}"""
