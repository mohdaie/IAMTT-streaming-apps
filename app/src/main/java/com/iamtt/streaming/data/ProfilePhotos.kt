package com.iamtt.streaming.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlin.math.min

/**
 * Profile photos picked from the gallery, stored square and small in the app's private files.
 * A newly picked photo waits as "pending" until the profile is saved, so Cancel leaves the old one.
 */
class ProfilePhotos(context: Context) {
    private val resolver = context.contentResolver
    private val dir = File(context.filesDir, "avatars")

    private val pending = File(dir, "pending.jpg")

    fun file(profileId: String) = File(dir, "$profileId.jpg")

    /** Crops [uri]'s image to a centred square and keeps it as the pending photo. Blocking. */
    fun savePending(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "That file isn't a picture." }
        // Decode at no more than twice the stored size, so big camera photos don't use lots of memory.
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Couldn't open that picture.")
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, SIZE, SIZE, true)
        dir.mkdirs()
        pending.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return scaled
    }

    /** Makes the pending photo [profileId]'s picture. */
    fun commitPending(profileId: String) {
        if (pending.exists()) pending.renameTo(file(profileId))
    }

    fun discardPending() {
        pending.delete()
    }

    fun load(profileId: String): Bitmap? = file(profileId).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun delete(profileId: String) {
        file(profileId).delete()
    }

    private companion object {
        const val SIZE = 320
    }
}
