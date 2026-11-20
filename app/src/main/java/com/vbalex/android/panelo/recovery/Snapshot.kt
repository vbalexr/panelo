// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.recovery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.resume

/** Last-known-good screenshot of the WebView, one WebP per orientation in filesDir. */
class Snapshot(context: Context) {
    class Shot(val bitmap: Bitmap, val savedAtMs: Long)

    private val dir = context.filesDir

    /** PixelCopy the [view] region of [window] and store it. Main thread; encoding happens on IO. */
    suspend fun capture(window: Window, view: View): Result<Unit> {
        if (view.width == 0 || view.height == 0 || !view.isShown) return Result.failure(IOException("view not visible"))
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val region = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        val copyResult = suspendCancellableCoroutine { cont ->
            PixelCopy.request(window, region, bitmap, { code -> cont.resume(code) }, Handler(Looper.getMainLooper()))
        }
        if (copyResult != PixelCopy.SUCCESS) {
            bitmap.recycle()
            Log.w("snapshot", "capture_failed", "pixelCopy" to copyResult)
            return Result.failure(IOException("PixelCopy error $copyResult"))
        }
        val landscape = view.width > view.height
        val saved = withContext(Dispatchers.IO) { save(bitmap, landscape) }
        bitmap.recycle()
        saved.onSuccess { Log.d("snapshot", "captured", "landscape" to landscape) }
        saved.onFailure { Log.w("snapshot", "save_failed", "error" to it.toString()) }
        return saved
    }

    /** Blocking IO: call off the main thread. */
    fun load(landscape: Boolean): Result<Shot> {
        val file = file(landscape)
        if (!file.exists()) return Result.failure(FileNotFoundException(file.name))
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return Result.failure(IOException("undecodable ${file.name}"))
        return Result.success(Shot(bitmap, file.lastModified()))
    }

    fun clear() {
        file(true).delete()
        file(false).delete()
    }

    private fun save(bitmap: Bitmap, landscape: Boolean): Result<Unit> {
        val target = file(landscape)
        val temp = File(dir, target.name + ".tmp")
        val ok = runCatching {
            temp.outputStream().use { out -> bitmap.compress(format(), 80, out) }
        }.getOrElse { return Result.failure(it) }
        if (!ok || !temp.renameTo(target)) return Result.failure(IOException("write failed"))
        return Result.success(Unit)
    }

    @Suppress("DEPRECATION")
    private fun format() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP

    private fun file(landscape: Boolean) = File(dir, if (landscape) "snapshot_landscape.webp" else "snapshot_portrait.webp")
}
