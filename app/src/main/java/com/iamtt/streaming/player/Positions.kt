package com.iamtt.streaming.player

import android.content.Context

/** Remembers where you stopped in each video so it resumes there next time. */
object Positions {
    private const val PREFS = "positions"

    fun get(context: Context, id: String): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(id, 0L)

    fun save(context: Context, id: String, positionMs: Long, durationMs: Long) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Don't bother resuming from the first 30 s, and treat the last 3 min as "finished".
        val nearEnd = durationMs > 0 && durationMs - positionMs < 3 * 60_000
        if (positionMs < 30_000 || nearEnd) prefs.edit().remove(id).apply()
        else prefs.edit().putLong(id, positionMs).apply()
    }

    fun clear(context: Context, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(id).apply()
    }
}
