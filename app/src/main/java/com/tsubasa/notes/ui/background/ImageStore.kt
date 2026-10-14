package com.tsubasa.notes.ui.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File

/**
 * 自定义背景图的存放。
 *
 * 用户从相册选图后，我们**把文件复制进应用私有目录**，而不是长期持有相册的 Uri：
 * - 相册 Uri 只是临时授权，重启后可能失效（尤其是没申请持久权限时）；
 * - 私有目录里的文件随时可读，小组件进程也能用（小组件跨进程读相册 Uri 会拿不到权限）。
 *
 * 复制时顺便压缩，避免把一张 4000×3000 的原图存进来占几十 MB。
 */
object ImageStore {

    private const val TAG = "Tsubasa"
    private const val APP_BG = "app_bg.jpg"
    private const val WIDGET_BG = "widget_bg.jpg"

    /** 压缩后的最长边。够用又不至于太大。 */
    private const val MAX_EDGE = 1440

    fun appBgFile(context: Context): File = File(context.filesDir, APP_BG)
    fun widgetBgFile(context: Context): File = File(context.filesDir, WIDGET_BG)

    /**
     * 把选中的图片复制并压缩到私有目录。
     *
     * @return 成功返回文件绝对路径，失败返回 null
     */
    fun importBackground(context: Context, uri: Uri, forWidget: Boolean): String? {
        val target = if (forWidget) widgetBgFile(context) else appBgFile(context)
        return try {
            val bitmap = decodeScaled(context, uri) ?: return null
            target.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            bitmap.recycle()
            target.absolutePath
        } catch (t: Throwable) {
            Log.w(TAG, "导入背景图失败", t)
            null
        }
    }

    /** 先读尺寸算采样率，再解码 —— 避免把超大原图整个读进内存。 */
    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / (sample * 2) >= MAX_EDGE) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    /** 清掉自定义背景（设置里「恢复默认」用）。 */
    fun clear(context: Context, forWidget: Boolean): Boolean {
        val f = if (forWidget) widgetBgFile(context) else appBgFile(context)
        return try {
            if (f.exists()) f.delete() else true
        } catch (t: Throwable) {
            Log.w(TAG, "删除背景图失败", t)
            false
        }
    }

    /** 文件是否真的存在（路径存了但文件被清了的情况要兜住）。 */
    fun exists(path: String): Boolean =
        path.isNotBlank() && try {
            File(path).let { it.exists() && it.length() > 0 }
        } catch (t: Throwable) {
            false
        }

    /**
     * 把一张图片文件读成 Bitmap（带缩放，避免大图直接进内存）。
     *
     * 背景图是用户自己选的，可能是一张几千万像素的照片。直接解码会 OOM，
     * 所以先只读尺寸、算出采样率，再解码到目标大小。
     */
    fun loadScaled(path: String, targetWidthPx: Int): Bitmap? {
        if (path.isBlank()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            val target = targetWidthPx.coerceAtLeast(1)
            while (bounds.outWidth / (sample * 2) >= target) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, opts)
        } catch (t: Throwable) {
            Log.w(TAG, "解码背景图失败", t)
            null
        }
    }
}
