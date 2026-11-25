package com.eventuath.gexrdp_kvm.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/** Server list persistence: a small JSON file in the app's private storage. */
class ServerStore(context: Context) {

    private val file = File(context.filesDir, "servers.json")

    @Synchronized
    fun load(): List<ServerProfile> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let { add(ServerProfile.fromJson(it)) }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(profiles: List<ServerProfile>) {
        val array = JSONArray()
        profiles.forEach { array.put(it.toJson()) }
        file.writeText(array.toString())
    }

    fun upsert(profile: ServerProfile): List<ServerProfile> {
        val current = load().toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) current[index] = profile else current.add(profile)
        save(current)
        return current
    }

    fun remove(id: String): List<ServerProfile> {
        val current = load().filterNot { it.id == id }
        save(current)
        return current
    }

    fun find(id: String): ServerProfile? = load().firstOrNull { it.id == id }

    fun updatePin(id: String, pin: String): ServerProfile? {
        val profile = find(id) ?: return null
        val updated = profile.copy(pin = pin)
        upsert(updated)
        return updated
    }
}
