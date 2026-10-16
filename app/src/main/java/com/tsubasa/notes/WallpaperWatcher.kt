package com.tsubasa.notes

import android.app.WallpaperManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tsubasa.notes.data.WidgetBridge

/**
 * 壁纸配色变化的监听器 —— 解决「换了壁纸，小组件配色要等很久才跟上」。
 *
 * ## 为什么清单里注册的 BroadcastReceiver 收不到
 *
 * 换壁纸时系统只发一条 `android.intent.action.WALLPAPER_CHANGED`
 * （`WallpaperManagerService.notifyCallbacksLocked()` 里 `sendBroadcastAsUser`），
 * 但这条路在 API 26+（正是本项目的 minSdk）上走不通：
 *
 * 1. 它是**隐式广播**。AOSP 的 `data/etc/framework-sysconfig.xml` 里
 *    `<allow-implicit-broadcast>` 白名单共 23 条（telephony / SMS / 媒体扫描等），
 *    **明确不含 WALLPAPER_CHANGED**；`BroadcastSkipPolicy.shouldSkipMessage()` 会把它
 *    判成「Background execution not allowed: receiving ...」直接丢掉。
 * 2. 它同时是 `<protected-broadcast>`，只有系统进程能发，我们自己补发也不行。
 *
 * 看起来能替代的 `ACTION_CONFIGURATION_CHANGED` 同样无效：
 * `ActivityManagerService.broadcastGlobalConfigurationChanged()` 给它加了
 * `FLAG_RECEIVER_REGISTERED_ONLY`，**只有运行时注册的接收器收得到**。
 *
 * 而「等一会儿自己变好」也不是巧合：`AppWidgetServiceImpl` 里
 * `MIN_UPDATE_PERIOD = 30 * 60 * 1000`，把小组件的 `updatePeriodMillis` 强制夹到最少 30 分钟 ——
 * 所以把 `res/xml/note_widget_info.xml` 里的周期改小**没有任何用**。
 * 同理 `AppWidgetServiceImpl` 全文不含 `onConfigurationChanged` / `UI_MODE` / `colorMode`，
 * 系统也不会因为换壁纸而主动重刷小组件。
 *
 * ## 实际做法
 *
 * 在自己进程里直接向 `WallpaperManager` 注册配色回调（公开 API、**不需要任何权限**、
 * API 26 起可用）。配色一变就在本进程内收到通知，再用**定向广播**通知桌面重刷：
 * [WidgetBridge.refresh] 带 `setPackage`，而 `BroadcastSkipPolicy.disallowBackgroundStart()`
 * 只拦「无 component、无 package、也没有 INCLUDE_BACKGROUND」的广播，
 * 所以定向广播不受后台启动限制影响，能可靠送达。
 *
 * ## 说清楚代价
 *
 * 回调只在**我们的进程活着**时有效。进程被杀之后换壁纸，要等下次进程被拉起
 * （开 App、点小组件、或 30 分钟周期刷新）才会跟上。这是没有常驻服务时能做到的上限 ——
 * 系统不提供任何能唤醒我们的信号（上面两条广播就是全部，且都到不了）。
 */
object WallpaperWatcher {

    private const val TAG = "Tsubasa"

    /** 强引用持有回调，避免被 GC；同时保证 [start] 可重复调用。 */
    private var listener: WallpaperManager.OnColorsChangedListener? = null

    /**
     * 开始监听壁纸配色。重复调用只注册一次。
     *
     * @return 是否成功注册。失败不抛异常 —— 配色刷新属于锦上添花，
     *   不该让进程启动失败（个别 ROM 上 WallpaperManager 服务可能不可用）。
     */
    fun start(context: Context): Boolean {
        if (listener != null) return true
        return try {
            val app = context.applicationContext
            val callback = WallpaperManager.OnColorsChangedListener { _, _ -> onColorsChanged(app) }
            WallpaperManager.getInstance(app)
                .addOnColorsChangedListener(callback, Handler(Looper.getMainLooper()))
            listener = callback
            true
        } catch (t: Throwable) {
            Log.w(TAG, "注册壁纸配色监听失败，小组件配色将退回周期刷新", t)
            false
        }
    }

    /**
     * 壁纸配色真的变了（系统主题色通常也跟着变）时该做的事。
     *
     * 单独抽成公开方法有两个原因：让回调保持一行；以及让它能被单元测试直接驱动
     * （Robolectric 不会替我们触发真实的系统回调）。
     */
    fun onColorsChanged(context: Context) {
        WidgetBridge.refresh(context)
    }

    /** 仅用于测试：忘掉已注册的回调，让 [start] 能重新注册。 */
    internal fun reset() {
        listener = null
    }
}
