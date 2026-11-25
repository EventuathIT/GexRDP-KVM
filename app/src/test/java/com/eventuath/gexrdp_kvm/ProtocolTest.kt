package com.eventuath.gexrdp_kvm

import com.eventuath.gexrdp_kvm.core.protocol.FrameCodec
import com.eventuath.gexrdp_kvm.core.protocol.FrameHeader
import com.eventuath.gexrdp_kvm.core.protocol.GexProtocol
import com.eventuath.gexrdp_kvm.core.protocol.InputCodec
import com.eventuath.gexrdp_kvm.core.protocol.Prelude
import com.eventuath.gexrdp_kvm.core.protocol.configJson
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolTest {

    @Test
    fun preludeLayoutMatchesServer() {
        val encoded = Prelude(channel = GexProtocol.CHANNEL_INPUT, token = "abc").encode()
        assertEquals(13, encoded.size)
        assertEquals('G'.code.toByte(), encoded[0])
        assertEquals('E'.code.toByte(), encoded[1])
        assertEquals('X'.code.toByte(), encoded[2])
        assertEquals('1'.code.toByte(), encoded[3])
        assertEquals(1, readShortLe(encoded, 4))
        assertEquals(GexProtocol.CHANNEL_INPUT, encoded[6].toInt())
        assertEquals(3, readShortLe(encoded, 8))
        assertArrayEquals("abc".toByteArray(), encoded.copyOfRange(10, 13))
    }

    @Test
    fun emptyTokenPreludeIsTenBytes() {
        assertEquals(10, Prelude(channel = GexProtocol.CHANNEL_CONTROL).encode().size)
    }

    @Test
    fun frameHeaderRoundTrip() {
        val header = FrameHeader(
            type = GexProtocol.FRAME_DIRTY,
            flags = GexProtocol.FLAG_KEYFRAME,
            seq = 42,
            timestampMs = 1_700_000_000_000L,
            x = 16,
            y = 32,
            width = 64,
            height = 48,
            stride = 1920,
            format = GexProtocol.FORMAT_RGBA_8888,
            payloadLen = 64 * 48 * 4,
        )
        val encoded = FrameCodec.encode(header)
        assertEquals(GexProtocol.FRAME_HEADER_SIZE, encoded.size)
        val decoded = FrameCodec.decode(encoded)
        assertEquals(header, decoded)
        assertTrue(decoded.isKeyframe)
        assertFalse(decoded.isFull)
    }

    @Test
    fun frameHeaderFieldOffsets() {
        val header = FrameHeader(
            type = GexProtocol.FRAME_PING,
            flags = 0,
            seq = 1,
            timestampMs = 2,
            x = 3,
            y = 4,
            width = 5,
            height = 6,
            stride = 7,
            format = GexProtocol.FORMAT_BGRA_8888,
            payloadLen = 8,
        )
        val raw = FrameCodec.encode(header)
        assertEquals(GexProtocol.FRAME_PING, raw[0].toInt()) // type at 0
        assertEquals(0, raw[1].toInt())          // flags at 1
        assertEquals(1, readIntLe(raw, 4))       // seq at 4
        assertEquals(2L, readLongLe(raw, 8))     // timestamp at 8
        assertEquals(3, readShortLe(raw, 16))    // x at 16
        assertEquals(6, readShortLe(raw, 22))    // height at 22
        assertEquals(7, readIntLe(raw, 24))      // stride at 24
        assertEquals(1, raw[28].toInt())         // format at 28
        assertEquals(8, readIntLe(raw, 32))      // payloadLen at 32
    }

    @Test
    fun configJsonIsCompact() {
        assertEquals(
            """{"width":1280,"height":720,"format":"rgba8888","scale":1.0}""",
            configJson(1280, 720),
        )
    }

    @Test
    fun relativeInputPacket() {
        val packet = InputCodec.rel(-12, 34)
        assertEquals(7, packet.size)
        assertEquals(1, packet[0].toInt())       // INPUT_REL
        assertEquals(4, readShortLe(packet, 1))  // payload length
        assertEquals(-12, readSignedShortLe(packet, 3))
        assertEquals(34, readSignedShortLe(packet, 5))
    }

    @Test
    fun relativeInputClampsToShort() {
        val packet = InputCodec.rel(40_000, -40_000)
        assertEquals(32767, readSignedShortLe(packet, 3))
        assertEquals(-32768, readSignedShortLe(packet, 5))
    }

    @Test
    fun buttonPacket() {
        val packet = InputCodec.btn(GexProtocol.BUTTON_RIGHT, true)
        assertEquals(5, packet.size)
        assertEquals(2, packet[0].toInt())
        assertEquals(2, readShortLe(packet, 1))
        assertEquals(GexProtocol.BUTTON_RIGHT, packet[3].toInt())
        assertEquals(1, packet[4].toInt())
    }

    @Test
    fun keyPacketLayout() {
        val packet = InputCodec.key(30, down = true, mods = 3)
        assertEquals(9, packet.size)
        assertEquals(4, packet[0].toInt())
        assertEquals(6, readShortLe(packet, 1))
        assertEquals(30, readIntLe(packet, 3))
        assertEquals(1, packet[7].toInt())
        assertEquals(3, packet[8].toInt())
    }

    @Test
    fun wheelPacketLayout() {
        val packet = InputCodec.wheel(3, -1)
        assertEquals(3, packet[0].toInt())
        assertEquals(3, readShortLe(packet, 3))
        assertEquals(-1, readSignedShortLe(packet, 5))
    }

    private fun readShortLe(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readSignedShortLe(data: ByteArray, offset: Int): Int = readShortLe(data, offset).toShort().toInt()

    private fun readIntLe(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

    private fun readLongLe(data: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in 0 until 8) {
            value = value or ((data[offset + index].toLong() and 0xFF) shl (index * 8))
        }
        return value
    }
}
