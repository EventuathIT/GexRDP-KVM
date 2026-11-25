package com.eventuath.gexrdp_kvm.data

import org.json.JSONObject
import java.util.UUID

data class ServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val host: String,
    val port: Int = DEFAULT_PORT,
    val pin: String? = null,
    val autoConnect: Boolean = true,
) {
    val endpoint: String get() = "$host:$port"

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("host", host)
        .put("port", port)
        .put("pin", pin ?: JSONObject.NULL)
        .put("autoConnect", autoConnect)

    companion object {
        const val DEFAULT_PORT = 50024

        fun fromJson(json: JSONObject): ServerProfile = ServerProfile(
            id = json.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = json.optString("name", "未命名"),
            host = json.optString("host"),
            port = json.optInt("port", DEFAULT_PORT),
            pin = json.optString("pin").takeIf { it.isNotBlank() },
            autoConnect = json.optBoolean("autoConnect", true),
        )
    }
}
