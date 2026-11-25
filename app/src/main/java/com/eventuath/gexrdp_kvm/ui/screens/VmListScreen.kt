package com.eventuath.gexrdp_kvm.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventuath.gexrdp_kvm.core.net.ControlClient
import com.eventuath.gexrdp_kvm.core.net.GexConnection
import com.eventuath.gexrdp_kvm.core.net.toUserMessage
import com.eventuath.gexrdp_kvm.data.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class VmItem(
    val name: String,
    val state: String,
    val vcpus: Int,
    val memoryMiB: Int,
    val width: Int,
    val height: Int,
    val usbTablet: Boolean,
    val gamingReady: Boolean,
) {
    companion object {
        fun parse(json: JSONObject): VmItem {
            val display = json.optJSONObject("display") ?: JSONObject()
            return VmItem(
                name = json.optString("name"),
                state = json.optString("state", "unknown"),
                vcpus = json.optInt("vcpus", 0),
                memoryMiB = json.optInt("memoryMiB", 0),
                width = display.optInt("width", 0),
                height = display.optInt("height", 0),
                usbTablet = json.optBoolean("usbTablet", false),
                gamingReady = json.optBoolean("gamingReady", false),
            )
        }
    }
}

private sealed interface VmListState {
    data object Loading : VmListState
    data class Trust(val pin: String) : VmListState
    data class Failed(val message: String) : VmListState
    data class Ready(
        val vms: List<VmItem>,
        val warnings: List<String>,
        val serverName: String,
        val backend: String,
    ) : VmListState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VmListScreen(
    profile: ServerProfile,
    password: String,
    autoEnterSingleVm: Boolean = true,
    onBack: () -> Unit,
    onTrustPin: (String) -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember(profile.id) { mutableStateOf<VmListState>(VmListState.Loading) }
    var control by remember(profile.id) { mutableStateOf<ControlClient?>(null) }
    var reload by remember(profile.id) { mutableIntStateOf(0) }
    var busyVm by remember(profile.id) { mutableStateOf<String?>(null) }
    var autoEntered by remember(profile.id) { mutableStateOf(false) }

    LaunchedEffect(profile.id, profile.pin, reload) {
        if (profile.pin == null) {
            state = VmListState.Loading
            state = try {
                VmListState.Trust(GexConnection.probePin(profile.host, profile.port))
            } catch (e: Exception) {
                VmListState.Failed(e.toUserMessage())
            }
            return@LaunchedEffect
        }
        if (password.isBlank()) {
            state = VmListState.Failed("没有保存的密码，请编辑服务器重新填写")
            return@LaunchedEffect
        }
        state = VmListState.Loading
        try {
            control?.close()
            val client = ControlClient.connect(profile.host, profile.port, profile.pin)
            val hello = client.login(password)
            control = client
            val array = hello.optJSONArray("virtualMachines") ?: JSONArray()
            state = VmListState.Ready(
                vms = (0 until array.length()).mapNotNull { index ->
                    array.optJSONObject(index)?.let(VmItem::parse)
                },
                warnings = client.warnings,
                serverName = client.serverName,
                backend = client.backend,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            state = VmListState.Failed(e.toUserMessage())
        }
    }

    DisposableEffect(profile.id) {
        onDispose { control?.close() }
    }

    LaunchedEffect(state, autoEnterSingleVm) {
        val ready = state as? VmListState.Ready ?: return@LaunchedEffect
        val only = ready.vms.singleOrNull() ?: return@LaunchedEffect
        if (autoEnterSingleVm && !autoEntered && only.state == "running") {
            autoEntered = true
            onOpenSession(only.name)
        }
    }

    fun runAction(op: String, name: String, mode: String? = null) {
        val client = control ?: return
        if (busyVm != null) return
        busyVm = name
        scope.launch {
            try {
                withContext(Dispatchers.IO) { client.vmAction(op, name, mode) }
                reload += 1
            } catch (e: Exception) {
                Toast.makeText(context, e.toUserMessage(), Toast.LENGTH_LONG).show()
            } finally {
                busyVm = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(profile.name)
                        Text(
                            profile.endpoint,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
                actions = {
                    TextButton(onClick = { reload += 1 }) { Text("刷新") }
                    TextButton(
                        onClick = {
                            val client = control ?: return@TextButton
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) { client.hostLock() }
                                    Toast.makeText(context, "已锁定宿主机", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, e.toUserMessage(), Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                    ) {
                        Text("锁屏")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val current = state) {
                VmListState.Loading -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(" 正在连接 ${profile.endpoint}…")
                    }
                }

                is VmListState.Failed -> FailedState(
                    message = current.message,
                    onRetry = { reload += 1 },
                    onBack = onBack,
                )

                is VmListState.Trust -> TrustDialog(
                    profile = profile,
                    pin = current.pin,
                    onTrust = { onTrustPin(current.pin) },
                    onCancel = onBack,
                )

                is VmListState.Ready -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            HostHeader(current)
                        }
                        if (current.warnings.isNotEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                    ),
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        current.warnings.forEach {
                                            Text(
                                                "⚠ $it",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (current.vms.isEmpty()) {
                            item {
                                Text(
                                    "宿主机上还没有虚拟机",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(current.vms, key = { it.name }) { vm ->
                            VmCard(
                                vm = vm,
                                busy = busyVm == vm.name,
                                onOpen = { onOpenSession(vm.name) },
                                onStart = { runAction("vm.start", vm.name) },
                                onShutdown = { runAction("vm.shutdown", vm.name) },
                                onForceOff = { runAction("vm.shutdown", vm.name, "force") },
                                onReboot = { runAction("vm.reboot", vm.name) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostHeader(state: VmListState.Ready) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(state.serverName, style = MaterialTheme.typography.bodyMedium)
            Text(
                "backend: ${state.backend} · ${state.vms.size} 台虚拟机",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AssistChip(onClick = {}, label = { Text("已连接") })
    }
}

@Composable
private fun FailedState(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("连接失败", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text("返回") }
            TextButton(onClick = onRetry) { Text("重试") }
        }
    }
}

@Composable
private fun TrustDialog(
    profile: ServerProfile,
    pin: String,
    onTrust: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("需要确认服务器身份", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "已读取 ${profile.endpoint} 的证书。若与服务端 certgen 打印的指纹一致，请点击信任。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("首次连接：确认证书") },
        text = {
            Column {
                Text("SHA-256 指纹（SPKI）")
                Spacer(Modifier.height(8.dp))
                Text(
                    pin.chunked(4).joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "此指纹会保存在本机，之后连接时若证书变化将直接拒绝。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onTrust) { Text("信任并连接") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        },
    )
}

@Composable
private fun VmCard(
    vm: VmItem,
    busy: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onShutdown: () -> Unit,
    onForceOff: () -> Unit,
    onReboot: () -> Unit,
) {
    var menuOpen by remember(vm.name) { mutableStateOf(false) }
    val running = vm.state == "running"

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (running) onOpen() },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    vm.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                StateBadge(vm.state)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append("${vm.vcpus} vCPU · ${vm.memoryMiB} MiB")
                    if (vm.width > 0 && vm.height > 0) append(" · ${vm.width}×${vm.height}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (vm.usbTablet) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "已挂载 usb-tablet：相对鼠标与游戏模式不可用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (running) {
                    TextButton(onClick = onOpen, enabled = !busy) { Text("进入桌面") }
                    TextButton(onClick = onShutdown, enabled = !busy) { Text("关机") }
                    TextButton(onClick = onReboot, enabled = !busy) { Text("重启") }
                } else {
                    TextButton(onClick = onStart, enabled = !busy) { Text("开机") }
                }
                Spacer(Modifier.weight(1f))
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { menuOpen = true }) { Text("⋯") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("强制断电") },
                            enabled = running,
                            onClick = {
                                menuOpen = false
                                onForceOff()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("查看状态") },
                            onClick = {
                                menuOpen = false
                                onOpen()
                            },
                            enabled = running,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StateBadge(state: String) {
    val (label, color) = when (state) {
        "running" -> "运行中" to Color(0xFF2E7D32)
        "shutoff" -> "已关机" to Color(0xFF757575)
        "paused" -> "已暂停" to Color(0xFFF9A825)
        "crashed" -> "已崩溃" to Color(0xFFC62828)
        "pmsuspended" -> "休眠" to Color(0xFF6A1B9A)
        "shutdown" -> "关机中" to Color(0xFFF9A825)
        else -> state to Color(0xFF757575)
    }
    Text(label, color = color, style = MaterialTheme.typography.labelMedium)
}
