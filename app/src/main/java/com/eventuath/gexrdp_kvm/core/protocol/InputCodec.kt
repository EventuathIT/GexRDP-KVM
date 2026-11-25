package com.eventuath.gexrdp_kvm.core.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Input channel packets: `u8 type | u16 payloadLen | payload`.
 * The client batches relative movement in 4 ms windows before calling this.
 */
object InputCodec {
    private const val TYPE_REL = 1
    private const val TYPE_BTN = 2
    private const val TYPE_WHEEL = 3
    private const val TYPE_KEY = 4
    private const val TYPE_ABS = 5
    private const val TYPE_PING = 6
    private const val TYPE_TEXT = 7

    fun rel(dx: Int, dy: Int): ByteArray {
        val payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(dx.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            .putShort(dy.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            .array()
        return packet(TYPE_REL, payload)
    }

    fun btn(button: Int, down: Boolean): ByteArray =
        packet(TYPE_BTN, byteArrayOf(button.toByte(), if (down) 1 else 0))

    fun wheel(vertical: Int, horizontal: Int = 0): ByteArray {
        val payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(vertical.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            .putShort(horizontal.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            .array()
        return packet(TYPE_WHEEL, payload)
    }

    fun key(evdev: Int, down: Boolean, mods: Int = 0): ByteArray {
        val payload = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(evdev)
            .put(if (down) 1 else 0)
            .put(mods.toByte())
            .array()
        return packet(TYPE_KEY, payload)
    }

    fun abs(x: Int, y: Int): ByteArray {
        val payload = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(x.coerceIn(0, 0xFFFF).toShort())
            .putShort(y.coerceIn(0, 0xFFFF).toShort())
            .array()
        return packet(TYPE_ABS, payload)
    }

    fun ping(clientMs: Long): ByteArray {
        val payload = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(clientMs).array()
        return packet(TYPE_PING, payload)
    }

    fun text(value: String): ByteArray = packet(TYPE_TEXT, value.toByteArray(Charsets.UTF_8))

    private fun packet(type: Int, payload: ByteArray): ByteArray {
        require(payload.size <= GexProtocol.MAX_INPUT_PAYLOAD) { "input payload too large" }
        val out = ByteBuffer.allocate(GexProtocol.INPUT_HEADER_SIZE + payload.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(type.toByte())
            .putShort(payload.size.toShort())
            .put(payload)
            .array()
        return out
    }
}
