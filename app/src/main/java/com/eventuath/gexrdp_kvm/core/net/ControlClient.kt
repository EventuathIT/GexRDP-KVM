package com.eventuath.gexrdp_kvm.core.net

import com.eventuath.gexrdp_kvm.core.protocol.GexProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException

class GexException(val code: String, message: String) : IOException(message)

/** Control channel: JSON Lines request/response plus server-pushed events. */
class ControlClient private constructor(private val connection: GexConnection) : Closeable {

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var nextId = 1
    private var heartbeat: Job? = null

    private val _events = MutableSharedFlow<JSONObject>(extraBufferCapacity = 16)
    val events: SharedFlow<JSONObject> = _events

    var token: String = ""
        private set
    var capabilities: List<String> = emptyList()
        private set
    var warnings: List<String> = emptyList()
        private set
    var serverName: String = ""
        private set
    var serverVersion: String = ""
        private set
    var backend: String = ""
        private set

    suspend fun login(password: String): JSONObject {
        val params = JSONObject()
            .put("protocol", GexProtocol.VERSION)
            .put(
                "client",
                JSONObject().put("name", "gex-android").put("version", CLIENT_VERSION),
            )
            .put(
                "auth",
                JSONObject().put("type", "password").put("password", password),
            )
        val result = execute("hello", params)
        token = result.optString("token")
        val server = result.optJSONObject("server") ?: JSONObject()
        serverName = server.optString("name", "gexd")
        serverVersion = server.optString("version", "")
        backend = result.optJSONObject("host")?.optString("backend", "") ?: ""
        capabilities = result.optJSONArray("capabilities")?.toStringList() ?: emptyList()
        warnings = result.optJSONArray("warnings")?.toStringList() ?: emptyList()
        startHeartbeat()
        return result
    }

    suspend fun request(op: String, params: JSONObject = JSONObject()): JSONObject =
        execute(op, params)

    suspend fun vmList(): List<JSONObject> =
        execute("vm.list", JSONObject())
            .optJSONArray("virtualMachines")
            ?.toObjectList()
            ?: emptyList()

    suspend fun vmAction(op: String, name: String, mode: String? = null): JSONObject {
        val params = JSONObject().put("name", name)
        if (mode != null) params.put("mode", mode)
        return execute(op, params)
    }

    suspend fun hostLock() {
        execute("host.lock", JSONObject())
    }

    suspend fun sessionOpen(vm: String, maxFps: Int): JSONObject =
        execute(
            "session.open",
            JSONObject()
                .put("vm", vm)
                .put("display", JSONObject().put("maxFps", maxFps))
                .put("input", JSONObject().put("mouseMode", "relative")),
        )

    suspend fun sessionClose(sessionId: String) {
        execute("session.close", JSONObject().put("sessionId", sessionId))
    }

    private suspend fun execute(op: String, params: JSONObject): JSONObject = mutex.withLock {
        val id = nextId++
        val payload = JSONObject()
            .put("id", id)
            .put("op", op)
            .put("params", params)
        // Every socket write/read must stay off the caller's dispatcher: the UI
        // calls login()/vmList() from a Compose LaunchedEffect on the main thread.
        withContext(Dispatchers.IO) {
            connection.send((payload.toString() + "\n").toByteArray(Charsets.UTF_8))
            while (true) {
                val line = connection.readLine() ?: throw IOException("连接已断开")
                if (line.isBlank()) continue
                val message = JSONObject(line)
                if (message.has("event")) {
                    _events.tryEmit(message)
                    continue
                }
                val messageId =
                    if (message.has("id") && !message.isNull("id")) message.optInt("id", -1) else -1
                if (messageId != id) continue
                if (!message.optBoolean("ok", false)) {
                    val error = message.optJSONObject("error")
                    throw GexException(
                        error?.optString("code") ?: "INTERNAL",
                        error?.optString("message") ?: "服务端返回错误",
                    )
                }
                return@withContext message.optJSONObject("result") ?: JSONObject()
            }
            @Suppress("UNREACHABLE_CODE")
            throw IOException("unreachable")
        }
    }

    private fun startHeartbeat() {
        if (heartbeat != null) return
        heartbeat = scope.launch {
            while (isActive) {
                delay(15_000)
                try {
                    execute("ping", JSONObject().put("t", System.currentTimeMillis()))
                } catch (_: Exception) {
                    return@launch
                }
            }
        }
    }

    override fun close() {
        scope.cancel()
        connection.close()
    }

    companion object {
        const val CLIENT_VERSION = "0.1.0"

        suspend fun connect(
            host: String,
            port: Int,
            pin: String,
            readTimeoutMs: Int = 45_000,
        ): ControlClient {
            val connection = GexConnection.open(
                host = host,
                port = port,
                channel = GexProtocol.CHANNEL_CONTROL,
                expectedPin = pin,
                readTimeoutMs = readTimeoutMs,
            )
            return ControlClient(connection)
        }
    }
}

internal fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }

internal fun JSONArray.toObjectList(): List<JSONObject> =
    (0 until length()).mapNotNull { index -> optJSONObject(index) }
