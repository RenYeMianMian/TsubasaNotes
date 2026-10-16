package com.tsubasa.notes.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/**
 * 定时把小组件配色「自己」重算一遍 —— 这是「换壁纸后颜色要等很久」的最终解法。
 *
 * ## 为什么只能靠轮询
 *
 * 配色是**我们自己算出来的**（[Palette] 从系统强调色推导出整套颜色），
 * 所以只有我们的代码真的跑起来，小组件才会换色。而换壁纸时：
 *
 * 1. 系统只发 `WALLPAPER_CHANGED` —— 隐式广播，API 26+ 上清单注册的接收器收不到；
 * 2. `CONFIGURATION_CHANGED` 带 `FLAG_RECEIVER_REGISTERED_ONLY`，同样收不到；
 * 3. `AppWidgetServiceImpl` 不会因为配置变化重刷小组件。
 *
 * 也就是说：**没有任何广播能叫醒我们**，能依赖的只有「定时醒来自己看一眼」。
 *
 * ## 为什么不直接把 updatePeriodMillis 改小
 *
 * `res/xml/note_widget_info.xml` 里的 `updatePeriodMillis` 会被平台强制夹到
 * `AppWidgetServiceImpl.MIN_UPDATE_PERIOD`（30 分钟），改小没有任何作用。
 * 我们自己用 [AlarmManager] 定的闹钟不受这个下限约束，可以更快。
 *
 * 用 [AlarmManager.setInexactRepeating] 而不是精确闹钟有三个好处：系统会把多个
 * 闹钟合并唤醒（省电）、不需要 `SCHEDULE_EXACT_ALARM` 权限、平台自己的小组件
 * 刷新用的也正是同一招。
 *
 * ## 为什么这个闹钟一定能送达
 *
 * [PendingIntent.getBroadcast] 指向的是**我们自己的组件**（显式 Intent），而
 * `BroadcastSkipPolicy.disallowBackgroundStart()` 只拦「无 component、无 package
 * 也没有 INCLUDE_BACKGROUND」的广播 —— 显式广播不在其中，进程没起来时系统会把
 * 我们拉起来投递。这正是 `WALLPAPER_CHANGED` 做不到而这里能做到的关键差别。
 *
 * ## 间隔为什么是 15 分钟
 *
 * 比平台的 30 分钟快一倍，用户感知上已经从「等很久」变成「自己会变」；
 * 又不至于像 5 分钟那样频繁唤醒进程。想更省电可以调大 [INTERVAL_MS]，
 * 想更灵敏可以调小，改这一个常量即可。
 */
object WidgetRefreshScheduler {

    private const val TAG = "Tsubasa"

    /** 轮询间隔：15 分钟。 */
    const val INTERVAL_MS = 15 * 60 * 1000L

    /** 定时刷新用的 action，注册在 [ThemeChangeReceiver] 上。 */
    const val ACTION_REFRESH_TICK = "com.tsubasa.notes.ACTION_REFRESH_TICK"

    /** 闹钟的请求码，固定值保证重复调用只替换同一个闹钟。 */
    private const val REQUEST_CODE = 900

    /**
     * 让闹钟状态和「桌面上到底还有没有小组件」保持一致：有就装，一个都没有就撤。
     *
     * 用户把小组件删光之后不该继续每 15 分钟把我们的进程拉起来一次。
     *
     * 这个方法被设计成**幂等**的，可以随便多调：重复装只会替换掉同一个
     * [PendingIntent] 上的闹钟，不会堆出一串。
     *
     * 调用点选在小组件的 [AppWidgetProvider.onUpdate] / `onDeleted`，而不是
     * Application.onCreate —— 后者每次进程启动都会跑，会把 15 分钟的计时
     * 反复重置，反而让刷新无限延后。而 `onUpdate` 由平台自己保证（每 30 分钟
     * 一次，加上添加/重启等时机）会来，所以闹钟即使丢了也能自愈。
     */
    fun sync(context: Context) {
        val app = context.applicationContext
        if (hasAnyWidget(app)) arm(app) else cancel(app)
    }

    /** 桌面上是否还有本应用的小组件（列表、快速添加，任意一个都算）。 */
    fun hasAnyWidget(context: Context): Boolean =
        widgetIds(context, NoteWidgetProvider::class.java).isNotEmpty() ||
            widgetIds(context, AddNoteWidgetProvider::class.java).isNotEmpty()

    private fun widgetIds(context: Context, cls: Class<*>): IntArray = try {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, cls)) ?: IntArray(0)
    } catch (t: Throwable) {
        IntArray(0)
    }

    private fun arm(context: Context) {
        val alarm = alarmManager(context) ?: return
        try {
            alarm.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + INTERVAL_MS,
                INTERVAL_MS,
                tickIntent(context)
            )
        } catch (t: Throwable) {
            // 装不上就退回平台的 30 分钟周期刷新，功能不受影响，只是慢一些。
            Log.w(TAG, "装定时刷新闹钟失败，配色将退回系统周期刷新", t)
        }
    }

    private fun cancel(context: Context) {
        val alarm = alarmManager(context) ?: return
        try {
            alarm.cancel(tickIntent(context))
        } catch (t: Throwable) {
            Log.w(TAG, "撤掉定时刷新闹钟失败", t)
        }
    }

    private fun alarmManager(context: Context): AlarmManager? = try {
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    } catch (t: Throwable) {
        null
    }

    private fun tickIntent(context: Context): PendingIntent {
        val i = Intent(context, ThemeChangeReceiver::class.java).apply {
            action = ACTION_REFRESH_TICK
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
