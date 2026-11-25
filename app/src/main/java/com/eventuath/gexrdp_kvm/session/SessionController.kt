package com.eventuath.gexrdp_kvm.session

import com.eventuath.gexrdp_kvm.core.input.KeyMap
import com.eventuath.gexrdp_kvm.core.net.ControlClient
import com.eventuath.gexrdp_kvm.core.net.DisplayClient
import com.eventuath.gexrdp_kvm.core.net.InputClient
import com.eventuath.gexrdp_kvm.core.net.toUserMessage
import com.eventuath.gexrdp_kvm.core.protocol.InputCodec
import com.eventuath.gexrdp_kvm.data.ServerProfile
import com.eventuath.gexrdp_kvm.data.SettingsStore
import com.eventuath.gexrdp_kvm.ui.session.RemoteCanvasView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

data class SessionStats(
    val fps: Int = 0,
    val kbps: Int = 0,
    val frames: Long = 0,
    val inputEvents: Int = 0,
)

sealed interface SessionPhase {
    data object Connecting : SessionPhase
    data object Streaming : SessionPhase
    data class Failed(val message: String) : SessionPhase
    data object Closed : SessionPhase
}

/**
 * Owns the three channels of one live session (control is shared with the VM
 * list before this controller is created, but reconnects independently for
 * clarity) and translates UI gestures into GEX input packets.
 */
class SessionController(
    val profile: ServerProfile,
    val vmName: String,
    private val password: String,
    private val settings: SettingsStore,
) : RemoteCanvasView.InputSink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingDx = AtomicInteger(0)
    private val pendingDy = AtomicInteger(0)
    private val frames = AtomicLong(0)
    private val bytes = AtomicLong(0)
    private val inputEvents = AtomicInteger(0)

    private var control: ControlClient? = null
    private var display: DisplayClient? = null
    private var input: InputClient? = null
    private var sessionId: String? = null
    private var started = false
    private var pendingConfig: Pair<Int, Int>? = null
    private var view: RemoteCanvasView? = null

    @Volatile
    private var sensitivity = settings.sensitivity

    @Volatile
    private var activeMods = 0

    @Volatile
    private var closed = false

    private val _phase = MutableStateFlow<SessionPhase>(SessionPhase.Connecting)
    val phase: StateFlow<SessionPhase> = _phase

    private val _stats = MutableStateFlow(SessionStats())
    val stats: StateFlow<SessionStats> = _stats

    /** Invoked when the guest should release the pointer (three-finger tap). */
    var onPointerReleased: (() -> Unit)? = null

    fun attach(view: RemoteCanvasView) {
        this.view = view
        view.sink = this
        pendingConfig?.let { (width, height) ->
            view.post { view.configure(width, height) }
        }
        view.post { view.requestFocus() }
    }

    fun detach(view: RemoteCanvasView) {
        if (this.view === view) {
            this.view = null
            view.sink = null
        }
    }

    fun start() {
        if (started) return
        started = true
        scope.launch {
            try {
                val pin = profile.pin ?: throw IllegalStateException("服务器尚未信任其证书")
                val controlClient = ControlClient.connect(profile.host, profile.port, pin)
                control = controlClient
                controlClient.login(password)
                val opened = controlClient.sessionOpen(vmName, settings.maxFps)
                sessionId = opened.optString("sessionId")

                val displayClient = DisplayClient.connect(profile.host, profile.port, pin, controlClient.token)
                display = displayClient
                val inputClient = InputClient.connect(profile.host, profile.port, pin, controlClient.token)
                input = inputClient
                inputClient.start()

                _phase.value = SessionPhase.Streaming
                launch { mouseLoop() }
                launch { statsLoop() }
                displayClient.run(
                    onConfig = { width, height ->
                        pendingConfig = width to height
                        view?.post { view?.configure(width, height) }
                    },
                    onFrame = { header, payload ->
                        frames.incrementAndGet()
                        bytes.addAndGet(payload.size.toLong())
                        view?.post { view?.applyFrame(header, payload) }
                    },
                )
                if (!closed) _phase.value = SessionPhase.Closed
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!closed) _phase.value = SessionPhase.Failed(e.toUserMessage())
            } finally {
                closeQuietly()
            }
        }
    }

    private suspend fun mouseLoop() {
        val context = currentCoroutineContext()
        while (context.isActive) {
            delay(4)
            val dx = pendingDx.getAndSet(0)
            val dy = pendingDy.getAndSet(0)
            if (dx != 0 || dy != 0) {
                input?.send(InputCodec.rel(dx, dy))
            }
        }
    }

    private suspend fun statsLoop() {
        var previousFrames = 0L
        var previousBytes = 0L
        while (currentCoroutineContext().isActive) {
            delay(1000)
            val totalFrames = frames.get()
            val totalBytes = bytes.get()
            _stats.value = SessionStats(
                fps = (totalFrames - previousFrames).toInt(),
                kbps = ((totalBytes - previousBytes) * 8 / 1000).toInt(),
                frames = totalFrames,
                inputEvents = inputEvents.get(),
            )
            previousFrames = totalFrames
            previousBytes = totalBytes
        }
    }

    fun stop() {
        if (closed) return
        closed = true
        val id = sessionId
        scope.launch {
            try {
                if (id != null) control?.sessionClose(id)
            } catch (_: Exception) {
            } finally {
                closeQuietly()
                scope.cancel()
            }
        }
    }

    private fun closeQuietly() {
        runCatching { display?.close() }
        runCatching { input?.close() }
        runCatching { control?.close() }
        display = null
        input = null
        control = null
    }

    fun setSensitivity(value: Float) {
        sensitivity = value.coerceIn(0.2f, 3.0f)
        settings.sensitivity = sensitivity
    }

    fun requestPointerCapture() {
        view?.post { view?.startPointerCapture() }
    }

    fun releasePointerCapture() {
        view?.post { view?.stopPointerCapture() }
    }

    // ------------------------------------------------------------------ //
    // RemoteCanvasView.InputSink
    // ------------------------------------------------------------------ //

    override fun onRelative(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        inputEvents.incrementAndGet()
        pendingDx.addAndGet((dx * sensitivity).roundToInt())
        pendingDy.addAndGet((dy * sensitivity).roundToInt())
    }

    override fun onButton(button: Int, down: Boolean) {
        inputEvents.incrementAndGet()
        input?.send(InputCodec.btn(button, down))
    }

    override fun onWheel(vertical: Int, horizontal: Int) {
        inputEvents.incrementAndGet()
        val value = if (settings.wheelInverted) -vertical else vertical
        input?.send(InputCodec.wheel(value, horizontal))
    }

    override fun onKey(keyCode: Int, down: Boolean) {
        val evdev = KeyMap.evdev(keyCode) ?: return
        inputEvents.incrementAndGet()
        val mods = activeMods
        input?.send(InputCodec.key(evdev, down, mods))
        val bit = KeyMap.modsOf(keyCode)
        if (bit != 0) {
            activeMods = if (down) mods or bit else mods and bit.inv()
        }
    }

    override fun onReleasePointer() {
        view?.post {
            view?.stopPointerCapture()
            onPointerReleased?.invoke()
        }
    }

    /** Types plain ASCII text from the soft keyboard. */
    fun sendText(text: String) {
        for (char in text) {
            val mapped = KeyMap.charToKey(char) ?: continue
            if (mapped.shift) input?.send(InputCodec.key(42, true, activeMods))
            input?.send(InputCodec.key(mapped.evdev, true, activeMods))
            input?.send(InputCodec.key(mapped.evdev, false, activeMods))
            if (mapped.shift) input?.send(InputCodec.key(42, false, activeMods))
        }
    }

    fun sendEnter() {
        input?.send(InputCodec.key(28, true, activeMods))
        input?.send(InputCodec.key(28, false, activeMods))
    }

}
