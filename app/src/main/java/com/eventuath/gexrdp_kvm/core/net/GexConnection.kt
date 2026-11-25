package com.eventuath.gexrdp_kvm.core.net

import com.eventuath.gexrdp_kvm.core.protocol.GexProtocol
import com.eventuath.gexrdp_kvm.core.protocol.Prelude
import com.eventuath.gexrdp_kvm.core.protocol.ProtocolException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class CertificatePinException(message: String) : IOException(message)

/**
 * Trust manager that accepts a certificate only when its SPKI SHA-256 pin
 * matches the stored value. A null pin accepts and records the certificate
 * (used by the explicit TOFU prompt in the UI, never for sending credentials).
 */
private class PinTrustManager(
    private val expectedPin: String?,
    private val observed: AtomicReference<String?>,
) : X509TrustManager {

    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        val leaf = chain.firstOrNull() ?: throw CertificateException("服务端未提供证书")
        val digest = MessageDigest.getInstance("SHA-256").digest(leaf.publicKey.encoded)
        val pin = digest.joinToString("") { "%02x".format(it) }
        observed.set(pin)
        if (expectedPin != null && !expectedPin.equals(pin, ignoreCase = true)) {
            throw CertificatePinException("证书指纹不匹配，可能不是同一台服务器")
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        throw CertificateException("不支持客户端证书")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/**
 * One TLS connection speaking GEX/1. Reads are blocking; call from IO.
 */
class GexConnection private constructor(
    private val socket: SSLSocket,
    val input: DataInputStream,
    val output: DataOutputStream,
    val pin: String,
) : Closeable {

    private val writeLock = Any()

    @Volatile
    private var closed = false

    fun send(bytes: ByteArray) {
        synchronized(writeLock) {
            output.write(bytes)
            output.flush()
        }
    }

    fun readLine(): String? {
        val buffer = ByteArrayOutputStream(128)
        while (true) {
            val value = input.read()
            if (value < 0) {
                return if (buffer.size() == 0) null else String(buffer.toByteArray(), Charsets.UTF_8)
            }
            if (value == '\n'.code) {
                return String(buffer.toByteArray(), Charsets.UTF_8)
            }
            buffer.write(value)
            if (buffer.size() > GexProtocol.MAX_CONTROL_LINE) {
                throw ProtocolException("控制消息超长")
            }
        }
    }

    fun readExact(size: Int): ByteArray {
        val data = ByteArray(size)
        input.readFully(data)
        return data
    }

    fun readExactInto(target: ByteArray) {
        input.readFully(target)
    }

    override fun close() {
        if (closed) return
        closed = true
        // Conscrypt's SSLSocket.close() drains queued records and trips
        // StrictMode's NetworkOnMainThreadException when a Composable's
        // DisposableEffect releases the connection on the UI thread.
        closePool.execute {
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }

    companion object {
        private val closePool = java.util.concurrent.Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "gex-socket-close").apply { isDaemon = true }
        }

        /** Connect, verify the certificate pin, then send this channel's prelude. */
        suspend fun open(
            host: String,
            port: Int,
            channel: Int,
            token: String = "",
            expectedPin: String,
            connectTimeoutMs: Int = 8000,
            readTimeoutMs: Int = 45_000,
        ): GexConnection = withContext(Dispatchers.IO) {
            val (socket, pin) = handshake(host, port, expectedPin, connectTimeoutMs, readTimeoutMs)
            val connection = GexConnection(
                socket = socket,
                input = DataInputStream(socket.inputStream),
                output = DataOutputStream(socket.outputStream),
                pin = pin,
            )
            try {
                connection.send(Prelude(channel = channel, token = token).encode())
            } catch (e: Exception) {
                connection.close()
                throw e
            }
            connection
        }

        /** Fetch the certificate pin without authenticating, for the TOFU dialog. */
        suspend fun probePin(host: String, port: Int, timeoutMs: Int = 8000): String =
            withContext(Dispatchers.IO) {
                val (socket, pin) = handshake(host, port, expectedPin = null, timeoutMs, timeoutMs)
                socket.close()
                pin
            }

        private fun handshake(
            host: String,
            port: Int,
            expectedPin: String?,
            connectTimeoutMs: Int,
            readTimeoutMs: Int,
        ): Pair<SSLSocket, String> {
            val raw = Socket()
            try {
                raw.tcpNoDelay = true
                raw.connect(InetSocketAddress(host, port), connectTimeoutMs)
                raw.soTimeout = readTimeoutMs
                val observed = AtomicReference<String?>(null)
                val context = SSLContext.getInstance("TLS")
                context.init(null, arrayOf<TrustManager>(PinTrustManager(expectedPin, observed)), SecureRandom())
                val socket = context.socketFactory.createSocket(raw, host, port, true) as SSLSocket
                socket.soTimeout = readTimeoutMs
                socket.startHandshake()
                val pin = observed.get() ?: throw CertificatePinException("服务端证书无效")
                return socket to pin
            } catch (e: Exception) {
                try {
                    raw.close()
                } catch (_: IOException) {
                }
                throw e
            }
        }
    }
}
