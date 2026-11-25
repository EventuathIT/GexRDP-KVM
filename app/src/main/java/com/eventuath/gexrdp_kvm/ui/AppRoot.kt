package com.eventuath.gexrdp_kvm.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.eventuath.gexrdp_kvm.GexApp
import com.eventuath.gexrdp_kvm.data.ServerProfile
import com.eventuath.gexrdp_kvm.ui.screens.ServerEditScreen
import com.eventuath.gexrdp_kvm.ui.screens.ServerListScreen
import com.eventuath.gexrdp_kvm.ui.screens.SessionScreen
import com.eventuath.gexrdp_kvm.ui.screens.VmListScreen

sealed interface Route {
    data object Servers : Route
    data class Edit(val profile: ServerProfile?) : Route
    data class Vms(val profile: ServerProfile) : Route
    data class Session(val profile: ServerProfile, val vmName: String) : Route
}

@Composable
fun GexRoot() {
    val app = LocalContext.current.applicationContext as GexApp
    var route by remember { mutableStateOf<Route>(Route.Servers) }
    var servers by remember { mutableStateOf(app.servers.load()) }
    var bootstrapped by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!bootstrapped) {
            bootstrapped = true
            val lastId = app.settings.lastServerId
            val last = lastId?.let { id -> servers.firstOrNull { it.id == id } }
            if (last != null && last.autoConnect && app.secrets.getPassword(last.id) != null) {
                route = Route.Vms(last)
            }
        }
    }

    when (val current = route) {
        Route.Servers -> ServerListScreen(
            servers = servers,
            onOpen = { profile ->
                app.settings.lastServerId = profile.id
                route = Route.Vms(profile)
            },
            onAdd = { route = Route.Edit(null) },
            onEdit = { route = Route.Edit(it) },
            onDelete = { profile ->
                app.secrets.remove(profile.id)
                servers = app.servers.remove(profile.id)
            },
        )

        is Route.Edit -> ServerEditScreen(
            profile = current.profile,
            initialPassword = current.profile?.let { app.secrets.getPassword(it.id) }.orEmpty(),
            onCancel = { route = Route.Servers },
            onSave = { profile, password ->
                servers = app.servers.upsert(profile)
                if (password.isNotEmpty()) {
                    app.secrets.putPassword(profile.id, password)
                }
                route = Route.Servers
            },
        )

        is Route.Vms -> VmListScreen(
            profile = current.profile,
            password = app.secrets.getPassword(current.profile.id).orEmpty(),
            onBack = { route = Route.Servers },
            onTrustPin = { pin ->
                val updated = app.servers.updatePin(current.profile.id, pin)
                if (updated != null) {
                    servers = app.servers.load()
                    route = Route.Vms(updated)
                }
            },
            autoEnterSingleVm = app.settings.autoEnterSingleVm,
            onOpenSession = { vmName -> route = Route.Session(current.profile, vmName) },
        )

        is Route.Session -> SessionScreen(
            profile = current.profile,
            vmName = current.vmName,
            password = app.secrets.getPassword(current.profile.id).orEmpty(),
            settings = app.settings,
            onExit = { route = Route.Vms(current.profile) },
        )
    }
}
