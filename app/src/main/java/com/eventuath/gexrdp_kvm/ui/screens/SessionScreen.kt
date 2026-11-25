package com.eventuath.gexrdp_kvm.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.eventuath.gexrdp_kvm.data.ServerProfile
import com.eventuath.gexrdp_kvm.data.SettingsStore
import com.eventuath.gexrdp_kvm.session.SessionController
import com.eventuath.gexrdp_kvm.session.SessionPhase
import com.eventuath.gexrdp_kvm.ui.session.RemoteCanvasView
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    profile: ServerProfile,
    vmName: String,
    password: String,
    settings: SettingsStore,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val controller = remember(profile.id, vmName, attempt, password) {
        SessionController(profile, vmName, password, settings)
    }
    val phase by controller.phase.collectAsState()
    val stats by controller.stats.collectAsState()

    var showExitDialog by remember { mutableStateOf(false) }
    var showKeyboard by remember { mutableStateOf(false) }
    var showSensitivity by remember { mutableStateOf(false) }
    var pointerCaptured by remember { mutableStateOf(false) }
    var showOverlay by remember { mutableStateOf(true) }
    var sensitivity by remember { mutableFloatStateOf(settings.sensitivity) }
    var keyboardText by remember { mutableStateOf("") }

    LaunchedEffect(controller) {
        controller.onPointerReleased = {
            pointerCaptured = false
            Toast.makeText(context, "已释放鼠标", Toast.LENGTH_SHORT).show()
        }
        controller.start()
    }

    DisposableEffect(controller) {
        onDispose { controller.stop() }
    }

    BackHandler { showExitDialog = true }
    HideSystemBars()
    SessionOrientation()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                RemoteCanvasView(ctx).also { view ->
                    controller.attach(view)
                    view.requestFocus()
                }
            },
        )

        if (showOverlay) {
        OverlayBar(
            modifier = Modifier.align(Alignment.TopCenter),
            onBack = { showExitDialog = true },
            title = vmName,
            subtitle = "${stats.fps} fps · ${formatRate(stats.kbps)} · ${stats.inputEvents} in",
            captureLabel = if (pointerCaptured) "释放鼠标" else "锁定鼠标",
            onCapture = {
                if (pointerCaptured) {
                    controller.releasePointerCapture()
                    pointerCaptured = false
                } else {
                    controller.requestPointerCapture()
                    pointerCaptured = true
                }
            },
            onHide = { showOverlay = false },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            if (showSensitivity) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            "鼠标灵敏度 ${"%.2f".format(sensitivity)}×",
                            color = Color.White,
                        )
                        Slider(
                            value = sensitivity,
                            onValueChange = {
                                sensitivity = it
                                controller.setSensitivity(it)
                            },
                            valueRange = 0.2f..3.0f,
                        )
                    }
                }
            }

            if (showKeyboard) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f)),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        OutlinedTextField(
                            value = keyboardText,
                            onValueChange = { value ->
                                if (value.length > keyboardText.length) {
                                    controller.sendText(value.substring(keyboardText.length))
                                } else if (value.length < keyboardText.length) {
                                    repeat(keyboardText.length - value.length) {
                                        controller.onKey(KeyEvent.KEYCODE_DEL, true)
                                        controller.onKey(KeyEvent.KEYCODE_DEL, false)
                                    }
                                }
                                keyboardText = value.takeLast(32)
                            },
                            label = { Text("输入到虚拟机（仅 ASCII）") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                controller.sendEnter()
                                keyboardText = ""
                            }) { Text("回车") }
                            TextButton(onClick = {
                                controller.onKey(KeyEvent.KEYCODE_TAB, true)
                                controller.onKey(KeyEvent.KEYCODE_TAB, false)
                            }) { Text("Tab") }
                            TextButton(onClick = {
                                controller.onKey(KeyEvent.KEYCODE_ESCAPE, true)
                                controller.onKey(KeyEvent.KEYCODE_ESCAPE, false)
                            }) { Text("Esc") }
                            TextButton(onClick = { keyboardText = "" }) { Text("清空") }
                        }
                    }
                }
            }

            BottomBar(
                keyboardVisible = showKeyboard,
                onToggleKeyboard = {
                    showKeyboard = !showKeyboard
                    if (showKeyboard) showSensitivity = false
                },
                sensitivityVisible = showSensitivity,
                onToggleSensitivity = {
                    showSensitivity = !showSensitivity
                    if (showSensitivity) showKeyboard = false
                },
                onExit = { showExitDialog = true },
            )
        }
        } else {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                color = Color.Black.copy(alpha = 0.45f),
                shape = CircleShape,
                contentColor = Color.White,
            ) {
                TextButton(onClick = { showOverlay = true }) { Text("≡") }
            }
        }

        if (phase is SessionPhase.Connecting) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(14.dp))
                    Text("正在建立会话…", color = Color.White)
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("断开远程桌面？") },
            text = { Text("会话将关闭，虚拟机继续运行。") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    controller.stop()
                    onExit()
                }) { Text("断开") }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { Text("继续使用") }
            },
        )
    }

    val failed = phase as? SessionPhase.Failed
    if (failed != null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("会话中断") },
            text = { Text(failed.message) },
            confirmButton = {
                TextButton(onClick = {
                    attempt += 1
                }) { Text("重试") }
            },
            dismissButton = {
                TextButton(onClick = {
                    controller.stop()
                    onExit()
                }) { Text("返回") }
            },
        )
    }

    if (phase is SessionPhase.Closed) {
        LaunchedEffect(Unit) { onExit() }
    }
}

@Composable
private fun OverlayBar(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    title: String,
    subtitle: String,
    captureLabel: String,
    onCapture: () -> Unit,
    onHide: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) { Text("←") }

            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, maxLines = 1)
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.7f),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }

            TextButton(
                onClick = onCapture,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) { Text(captureLabel) }
            TextButton(
                onClick = onHide,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) { Text("隐藏") }
        }
    }
}

@Composable
private fun BottomBar(
    keyboardVisible: Boolean,
    onToggleKeyboard: () -> Unit,
    sensitivityVisible: Boolean,
    onToggleSensitivity: () -> Unit,
    onExit: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            TextButton(
                onClick = onToggleKeyboard,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) { Text(if (keyboardVisible) "隐藏键盘" else "键盘") }
            TextButton(
                onClick = onToggleSensitivity,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) { Text(if (sensitivityVisible) "收起灵敏度" else "灵敏度") }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = onExit,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
            ) { Text("退出") }
        }
    }
}

@Composable
private fun SessionOrientation() {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context.findActivity()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
private fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = view.context as? Activity
        val controller = activity?.let { WindowInsetsControllerCompat(it.window, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private fun formatRate(kbps: Int): String =
    if (kbps >= 1000) String.format("%.1f Mb/s", kbps / 1000.0) else "$kbps Kb/s"
