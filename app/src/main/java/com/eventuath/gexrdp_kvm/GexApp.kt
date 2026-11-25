package com.eventuath.gexrdp_kvm

import android.app.Application
import com.eventuath.gexrdp_kvm.data.SecretStore
import com.eventuath.gexrdp_kvm.data.ServerStore
import com.eventuath.gexrdp_kvm.data.SettingsStore

class GexApp : Application() {

    lateinit var servers: ServerStore
        private set
    lateinit var secrets: SecretStore
        private set
    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        servers = ServerStore(this)
        secrets = SecretStore(this)
        settings = SettingsStore(this)
    }
}
