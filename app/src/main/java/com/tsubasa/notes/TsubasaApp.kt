package com.tsubasa.notes

import android.app.Application
import com.tsubasa.notes.data.WidgetBridge

/**
 * 应用入口。
 *
 * 做两件事，都是为了解决「换了壁纸，小组件配色要等很久才跟上」：
 *
 * 1. 启动时**先补刷一次**小组件。如果壁纸是在我们进程死掉的时候换的，
 *    那么配色此刻还是旧的，这一步让它在进程被拉起的瞬间就追上。
 *    用户说的「手动打开软件之后才会变」走的正是这条路 —— 这也是为什么
 *    必须有第 3 条兜底（见下），不能只靠用户想起来打开应用。
 * 2. 注册壁纸配色监听，让**进程活着期间**换壁纸能立刻生效
 *    （见 [WallpaperWatcher] 里「为什么清单广播收不到」的完整证据）。
 *
 * 放在 Application 而不是 Activity 里，是因为小组件刷新不该依赖用户打开界面 ——
 * 只要进程被任何理由拉起（点小组件、开机、定时闹钟、周期刷新），这两步就都会执行。
 *
 * **注意这里刻意不做第三件事**：不在这里装 [com.tsubasa.notes.widget.WidgetRefreshScheduler]
 * 的定时闹钟。因为 Application.onCreate 每次进程启动都会跑，而闹钟是「从现在起
 * 15 分钟后」计时，装在这里会被反复重置，反而让刷新无限延后。它改由小组件的
 * onUpdate / onDeleted（以及覆盖安装、开机广播）来装卸。
 */
class TsubasaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 先补刷：把「进程不在时错过的壁纸变化」追平。
        try {
            WidgetBridge.refresh(this)
        } catch (t: Throwable) {
            // 刷新失败不该让应用起不来；定时闹钟和周期刷新会兜住。
        }
        // 再订阅：进程活着时换壁纸即时生效。
        WallpaperWatcher.start(this)
    }
}
