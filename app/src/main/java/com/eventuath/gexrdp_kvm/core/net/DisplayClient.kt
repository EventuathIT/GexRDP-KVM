package com.eventuath.gexrdp_kvm.core.net

import com.eventuath.gexrdp_kvm.core.protocol.FrameCodec
import com.eventuath.gexrdp_kvm.core.protocol.FrameHeader
import com.eventuath.gexrdp_kvm.core.protocol.GexProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException

/** Display channel: config frame, then full/dirty frame stream. */
class DisplayClient(private val connection: GexConnection) : Closeable {

    @Volatile
    private var closed = false

    /**
     * Blocking read loop; call from a coroutine on IO. Returns when the
     * connection closes or an error escapes.
     */
    suspend fun run(
        onConfig: (width: Int, height: Int) -> Unit,
        onFrame: (FrameHeader, ByteArray) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val headerBytes = ByteArray(GexProtocol.FRAME_HEADER_SIZE)
        while (!closed) {
            connection.readExactInto(headerBytes)
            val header = FrameCodec.decode(headerBytes)
            if (header.payloadLen < 0 || header.payloadLen > MAX_PAYLOAD) {
                throw IOException("非法帧长度 ${header.payloadLen}")
            }
            val payload = if (header.payloadLen > 0) connection.readExact(header.payloadLen) else EMPTY
            when (header.type) {
                GexProtocol.FRAME_CONFIG -> {
                    val config = JSONObject(String(payload, Charsets.UTF_8))
                    onConfig(config.optInt("width"), config.optInt("height"))
                }
                GexProtocol.FRAME_FULL, GexProtocol.FRAME_DIRTY -> onFrame(header, payload)
                GexProtocol.FRAME_PING -> Unit
                else -> Unit
            }
        }
    }

    override fun close() {
        closed = true
        connection.close()
    }

    companion object {
        private val EMPTY = ByteArray(0)
        private const val MAX_PAYLOAD = 64 * 1024 * 1024

        suspend fun connect(host: String, port: Int, pin: String, token: String): DisplayClient {
            val connection = GexConnection.open(
                host = host,
                port = port,
                channel = GexProtocol.CHANNEL_DISPLAY,
                token = token,
                expectedPin = pin,
                readTimeoutMs = 30_000,
            )
            return DisplayClient(connection)
        }
    }
}
