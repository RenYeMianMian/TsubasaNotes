package com.tsubasa.notes.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tsubasa.notes.L10n

/**
 * 壁纸、深浅色、开机、升级之后重刷小组件。
 *
 * 莫奈取色是「跟壁纸走」的，所以换壁纸必须重算配色 —— 这是让小组件真正
 * 有莫奈感的关键一环。
 */
class ThemeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            // 这里会重刷标题、空状态等文案，所以同样要套上应用内选定的语言，
            // 否则换壁纸后小组件会突然退回系统语言。
            WidgetRenderer.updateAll(L10n.wrap(context))
        } catch (t: Throwable) {
            Log.w("Tsubasa", "主题变化后刷新小组件失败", t)
        }
    }
}
