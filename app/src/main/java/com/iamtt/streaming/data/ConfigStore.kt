package com.iamtt.streaming.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import java.io.File

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Keeps the app settings in the app's private storage (not readable by other apps).
 * Updates are published as a StateFlow so the TV screen refreshes as soon as the
 * phone setup page changes something.
 */
class ConfigStore(context: Context) {
    private val file = File(context.filesDir, "config.json")
    private val _config = MutableStateFlow(load())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    val current: AppConfig get() = _config.value

    private fun load(): AppConfig = runCatching {
        if (file.exists()) AppJson.decodeFromString(AppConfig.serializer(), file.readText()) else AppConfig()
    }.getOrDefault(AppConfig())

    @Synchronized
    fun update(transform: (AppConfig) -> AppConfig) {
        _config.update(transform)
        val tmp = File(file.parentFile, "config.json.tmp")
        tmp.writeText(AppJson.encodeToString(AppConfig.serializer(), _config.value))
        tmp.renameTo(file)
    }

    fun setKey(json: String, email: String) = update {
        it.copy(serviceAccountJson = json, serviceAccountEmail = email, googleAccount = null)
    }

    /** Switches Drive access to [email]'s Google account and forgets any service account key. */
    fun setGoogleAccount(email: String) = update {
        it.copy(googleAccount = email, serviceAccountJson = null, serviceAccountEmail = null)
    }

    fun addFolder(folder: LibraryFolder) = update { cfg ->
        cfg.copy(folders = cfg.folders.filterNot { it.id == folder.id } + folder)
    }

    fun removeFolder(id: String) = update { cfg ->
        cfg.copy(folders = cfg.folders.filterNot { it.id == id })
    }

    /** Adds [profile], or replaces the one with the same id. */
    fun saveProfile(profile: Profile) = update { cfg ->
        val exists = cfg.profiles.any { it.id == profile.id }
        cfg.copy(profiles = if (exists) cfg.profiles.map { if (it.id == profile.id) profile else it } else cfg.profiles + profile)
    }

    fun deleteProfile(id: String) = update { cfg ->
        cfg.copy(profiles = cfg.profiles.filterNot { it.id == id })
    }
}
