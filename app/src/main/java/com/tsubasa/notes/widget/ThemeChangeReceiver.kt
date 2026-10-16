package com.tsubasa.notes.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tsubasa.notes.L10n

/**
 * 开机 / 覆盖安装后重刷小组件。
 *
 * 莫奈取色是「跟壁纸走」的，所以配色变了必须重算 —— 这是让小组件真正
 * 有莫奈感的关键一环。
 *
 * **换壁纸这件事不靠这里**：`WALLPAPER_CHANGED` 是隐式广播，API 26+ 上清单注册的
 * 接收器收不到（不在 `framework-sysconfig.xml` 的 23 条 `<allow-implicit-broadcast>`
 * 白名单里）。真正干活的是 [com.tsubasa.notes.WallpaperWatcher] ——
 * 在自己进程里订阅 `WallpaperManager` 的配色回调。清单里保留 WALLPAPER_CHANGED
 * 只是为了兼容个别自行放宽限制的厂商 ROM，属于 best-effort。
 */
class ThemeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            // 这里会重刷标题、空状态等文案，所以同样要套上应用内选定的语言，
            // 否则刷新后小组件会突然退回系统语言。
            WidgetRenderer.updateAll(L10n.wrap(context))
        } catch (t: Throwable) {
            Log.w("Tsubasa", "主题变化后刷新小组件失败", t)
        }
    }
}
