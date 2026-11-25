package com.eventuath.gexrdp_kvm.core.net

import com.eventuath.gexrdp_kvm.core.protocol.GexProtocol
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.Closeable

/**
 * Input channel. Small packets are queued and coalesced into a single write
 * so a burst of key/mouse events costs one syscall.
 */
class InputClient(private val connection: GexConnection) : Closeable {

    private val queue = Channel<ByteArray>(capacity = 256)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            try {
                while (isActive) {
                    val first = queue.receiveCatching().getOrNull() ?: return@launch
                    val batch = ByteArrayOutputStream(first.size * 4)
                    batch.write(first)
                    while (true) {
                        val next = queue.tryReceive().getOrNull() ?: break
                        batch.write(next)
                        if (batch.size() > 16 * 1024) break
                    }
                    connection.send(batch.toByteArray())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                // Session went away (server restart, VM stopped, network drop).
                // The display loop reports the failure; never crash the UI here.
                close()
            }
        }
    }

    fun send(packet: ByteArray) {
        queue.trySend(packet)
    }

    override fun close() {
        scope.cancel()
        connection.close()
    }

    companion object {
        suspend fun connect(host: String, port: Int, pin: String, token: String): InputClient {
            val connection = GexConnection.open(
                host = host,
                port = port,
                channel = GexProtocol.CHANNEL_INPUT,
                token = token,
                expectedPin = pin,
                readTimeoutMs = 0,
            )
            return InputClient(connection)
        }
    }
}
