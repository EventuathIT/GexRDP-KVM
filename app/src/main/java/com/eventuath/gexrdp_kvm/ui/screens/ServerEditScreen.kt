package com.eventuath.gexrdp_kvm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.eventuath.gexrdp_kvm.data.ServerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerEditScreen(
    profile: ServerProfile?,
    initialPassword: String,
    onCancel: () -> Unit,
    onSave: (ServerProfile, String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(profile?.name ?: "") }
    var host by rememberSaveable { mutableStateOf(profile?.host ?: "") }
    var port by rememberSaveable {
        mutableStateOf((profile?.port ?: ServerProfile.DEFAULT_PORT).toString())
    }
    var password by rememberSaveable { mutableStateOf(initialPassword) }
    var autoConnect by rememberSaveable { mutableStateOf(profile?.autoConnect ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val parsedPort = port.trim().toIntOrNull()
        error = when {
            name.isBlank() -> "请填写名称"
            host.isBlank() -> "请填写服务器地址"
            parsedPort == null || parsedPort !in 1..65535 -> "端口必须是 1–65535"
            password.isBlank() -> "请填写服务端密码"
            else -> null
        }
        val safePort = parsedPort ?: return
        if (error != null) return

        val endpointChanged = profile == null ||
            !profile.host.equals(host.trim(), ignoreCase = true) ||
            profile.port != safePort
        val saved = ServerProfile(
            id = profile?.id ?: java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            host = host.trim(),
            port = safePort,
            pin = if (endpointChanged) null else profile?.pin,
            autoConnect = autoConnect,
        )
        onSave(saved, password)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (profile == null) "添加服务器" else "编辑服务器") },
                navigationIcon = {
                    TextButton(onClick = onCancel) { Text("返回") }
                },
                actions = {
                    TextButton(onClick = { submit() }) { Text("保存") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("地址") },
                placeholder = { Text("192.168.1.10 或 gex.lan") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { value -> port = value.filter { it.isDigit() }.take(5) },
                label = { Text("端口") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("服务端密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启动时自动连接", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "打开 App 后直接进入该服务器的虚拟机列表",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoConnect, onCheckedChange = { autoConnect = it })
            }

            Spacer(Modifier.height(4.dp))
            Text(
                "密码会使用 Android Keystore 加密保存在本机。首次连接时会要求核对服务器证书指纹。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
