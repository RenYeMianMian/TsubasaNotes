package com.tsubasa.notes.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tsubasa.notes.L10n

/**
 * 重刷小组件的兜底入口：开机、覆盖安装、以及**我们自己的定时闹钟**。
 *
 * 莫奈取色是「跟壁纸走」的，所以配色变了必须重算 —— 这是让小组件真正
 * 有莫奈感的关键一环。
 *
 * **换壁纸这件事不能靠系统广播**：`WALLPAPER_CHANGED` 是隐式广播，API 26+ 上清单
 * 注册的接收器收不到（不在 `framework-sysconfig.xml` 的 23 条
 * `<allow-implicit-broadcast>` 白名单里）。清单里保留它只是为了兼容个别自行放宽
 * 限制的厂商 ROM，属于 best-effort。
 *
 * 真正干活的是 [WidgetRefreshScheduler] 定的那个定时闹钟：它用**显式**广播
 * （指向本类）把进程拉起来，每 [WidgetRefreshScheduler.INTERVAL_MS] 重算一次配色。
 * 详见该类的注释。
 */
class ThemeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            // 这里会重刷标题、空状态等文案，所以同样要套上应用内选定的语言，
            // 否则刷新后小组件会突然退回系统语言。
            WidgetRenderer.updateAll(L10n.wrap(context))
            // 顺手校正一次闹钟状态：用户可能刚把小组件删光，这一步能立刻停掉轮询。
            WidgetRefreshScheduler.sync(context)
        } catch (t: Throwable) {
            Log.w(TAG, "主题变化后刷新小组件失败", t)
        }
    }

    private companion object {
        const val TAG = "Tsubasa"
    }
}
