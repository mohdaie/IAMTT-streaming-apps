package com.iamtt.streaming.data

import android.content.Context

/** Where a profile stopped in a video, for resuming and the Continue Watching row. */
data class WatchProgress(val videoId: String, val positionMs: Long, val durationMs: Long, val watchedAt: Long)

/**
 * Remembers, per profile, where each video was left off. Stored in the app's private
 * preferences as "profileId|videoId" -> "position,duration,watchedAt".
 */
class WatchHistory(context: Context) {
    private val prefs = context.getSharedPreferences("watch_history", Context.MODE_PRIVATE)

    fun resumeAt(profileId: String, videoId: String): Long = get(profileId, videoId)?.positionMs ?: 0L

    fun save(profileId: String, videoId: String, positionMs: Long, durationMs: Long) {
        // Don't bother resuming from the first 30 s, and treat the last 3 min as "finished".
        val nearEnd = durationMs > 0 && durationMs - positionMs < 3 * 60_000
        if (positionMs < 30_000 || nearEnd) clear(profileId, videoId)
        else prefs.edit().putString(key(profileId, videoId), "$positionMs,$durationMs,${System.currentTimeMillis()}").apply()
    }

    fun clear(profileId: String, videoId: String) {
        prefs.edit().remove(key(profileId, videoId)).apply()
    }

    /** Videos this profile started but didn't finish, most recently watched first. */
    fun inProgress(profileId: String): List<WatchProgress> {
        val prefix = "$profileId|"
        return prefs.all.mapNotNull { (k, v) ->
            if (!k.startsWith(prefix)) null else parse(k.removePrefix(prefix), v as? String)
        }.sortedByDescending { it.watchedAt }
    }

    fun forgetProfile(profileId: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$profileId|") }.forEach(editor::remove)
        editor.apply()
    }

    private fun get(profileId: String, videoId: String) =
        parse(videoId, prefs.getString(key(profileId, videoId), null))

    private fun key(profileId: String, videoId: String) = "$profileId|$videoId"

    private fun parse(videoId: String, value: String?): WatchProgress? {
        val parts = value?.split(',') ?: return null
        val position = parts.getOrNull(0)?.toLongOrNull() ?: return null
        return WatchProgress(videoId, position, parts.getOrNull(1)?.toLongOrNull() ?: 0, parts.getOrNull(2)?.toLongOrNull() ?: 0)
    }
}
