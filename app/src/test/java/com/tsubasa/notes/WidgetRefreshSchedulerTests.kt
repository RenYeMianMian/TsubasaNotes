package com.tsubasa.notes

import android.app.AlarmManager
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.tsubasa.notes.data.Repo
import com.tsubasa.notes.widget.AddNoteWidgetProvider
import com.tsubasa.notes.widget.NoteWidgetProvider
import com.tsubasa.notes.widget.ThemeChangeReceiver
import com.tsubasa.notes.widget.WidgetRefreshScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * 「换了壁纸，小组件配色不会自己跟上，必须手动打开应用」这个 bug 的回归测试。
 *
 * 修复前只靠 `WallpaperWatcher`（进程内订阅 `WallpaperManager` 配色回调），
 * 但那个回调**只在进程活着时才有效** —— 进程一旦被系统回收，换壁纸就没有任何
 * 信号能把我们叫醒，用户看到的就是「不打开应用就永远不变色」。
 *
 * 系统层面四条路都被堵死了（清单广播收不到隐式广播、`CONFIGURATION_CHANGED`
 * 带 `FLAG_RECEIVER_REGISTERED_ONLY`、`AppWidgetServiceImpl` 不因配置变化重刷、
 * `updatePeriodMillis` 被平台夹到最低 30 分钟）。所以只能自己定闹钟：
 * [WidgetRefreshScheduler] 每 15 分钟用**显式**广播把进程拉起来重算配色。
 *
 * 这组测试盯的是三件最容易在后续改动里被悄悄破坏的事：
 * 1. 闹钟的 `PendingIntent` 必须是**显式广播**（带 component）。一旦有人图省事
 *    改成隐式广播，真机上会被 `BroadcastSkipPolicy.disallowBackgroundStart()`
 *    拦掉，功能静默失效 —— 这正是原 bug 的形态，单测是唯一能提前发现的地方。
 * 2. 间隔必须**小于平台的 30 分钟下限**，否则整个修复就失去意义。
 * 3. 没有小组件时必须撤掉闹钟，不能白白唤醒进程。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetRefreshSchedulerTests {

    private lateinit var ctx: ContextWrapper

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        Repo.file(ctx).delete()
        ShadowAlarmManager.reset()
    }

    @After
    fun tearDown() {
        Repo.file(ctx).delete()
        ShadowAlarmManager.reset()
    }

    private val alarmManager: AlarmManager
        get() = ctx.getSystemService(AlarmManager::class.java)

    private fun alarms() = shadowOf(alarmManager).scheduledAlarms

    /** 在 Robolectric 里注册一个小组件实例，`getAppWidgetIds` 才会有内容。 */
    private fun addListWidget(): Int {
        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
        return shadowOf(mgr).createWidget(NoteWidgetProvider::class.java, R.layout.widget_notes)
    }

    private fun addAddButtonWidget(): Int {
        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
        return shadowOf(mgr).createWidget(
            AddNoteWidgetProvider::class.java,
            R.layout.widget_add_button,
        )
    }

    /**
     * 模拟桌面把小组件删掉。
     *
     * Robolectric 的 `ShadowAppWidgetManager` 只给了 `createWidget`，没有对应的
     * `removeWidget`（删这个动作在真机上由 launcher 调 `AppWidgetHost.deleteAppWidgetId`
     * 完成）。它的内部状态就是一个 `widgetInfos` 映射，所以这里直接反射摘掉那一项 ——
     * 我们验的是「删掉之后 `hasAnyWidget` 会不会变成 false、闹钟会不会撤掉」，
     * 而不是 Robolectric 的删除 API 本身。
     */
    private fun removeWidget(id: Int) {
        val shadow = shadowOf(android.appwidget.AppWidgetManager.getInstance(ctx))
        val field = shadow.javaClass.getDeclaredField("widgetInfos")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(shadow) as MutableMap<Int, *>).remove(id)
    }

    // ------------------------------------------------------------ 闹钟本身

    @Test
    fun `有小组件时会装上定时刷新闹钟`() {
        addListWidget()
        WidgetRefreshScheduler.sync(ctx)

        assertEquals("有小组件时应该正好装一个闹钟", 1, alarms().size)
    }

    @Test
    fun `定时闹钟用显式广播否则会被后台限制拦掉`() {
        addListWidget()
        WidgetRefreshScheduler.sync(ctx)

        val alarm = alarms().firstOrNull()
        assertNotNull("应该装上闹钟", alarm)

        val pi = alarm!!.operation
        val shadowPi = shadowOf(pi)
        assertTrue("定时刷新必须是广播类型", shadowPi.isBroadcast)

        val saved = shadowPi.savedIntent
        // 关键：BroadcastSkipPolicy.disallowBackgroundStart() 只拦「无 component、
        // 无 package、也没有 INCLUDE_BACKGROUND」的广播。隐式广播在进程没起来时
        // 会被直接丢掉 —— 那就退化成了原 bug。
        assertEquals(
            "闹钟广播必须显式指向 ThemeChangeReceiver，否则进程没起来时收不到",
            ThemeChangeReceiver::class.java.name,
            saved.component?.className,
        )
        assertEquals(
            "闹钟广播的 action 要能被清单里的接收器匹配到",
            WidgetRefreshScheduler.ACTION_REFRESH_TICK,
            saved.action,
        )
    }

    @Test
    fun `定时闹钟是可重复的且间隔与常量一致`() {
        addListWidget()
        WidgetRefreshScheduler.sync(ctx)

        val alarm = alarms().first()
        assertEquals(
            "重复间隔应该等于 INTERVAL_MS",
            WidgetRefreshScheduler.INTERVAL_MS,
            alarm.interval,
        )
        assertTrue("间隔必须为正，否则闹钟不会重复", alarm.interval > 0)
    }

    @Test
    fun `间隔比平台下限更快否则修了等于没修`() {
        // AppWidgetServiceImpl.MIN_UPDATE_PERIOD = 30 分钟，updatePeriodMillis 会被
        // 平台夹到这个下限。我们自建闹钟的意义就在于绕过它，所以必须更短。
        val platformFloor = 30 * 60 * 1000L
        assertTrue(
            "定时刷新间隔（${WidgetRefreshScheduler.INTERVAL_MS}ms）必须小于平台下限" +
                "（${platformFloor}ms），否则不如直接用系统周期刷新",
            WidgetRefreshScheduler.INTERVAL_MS < platformFloor,
        )
    }

    @Test
    fun `没有小组件时不会装闹钟`() {
        // 用户一个小组件都没加，不该每 15 分钟把进程拉起来一次。
        WidgetRefreshScheduler.sync(ctx)

        assertTrue("没有小组件时不该有任何闹钟", alarms().isEmpty())
    }

    @Test
    fun `重复同步不会堆出多个闹钟`() {
        addListWidget()
        WidgetRefreshScheduler.sync(ctx)
        WidgetRefreshScheduler.sync(ctx)
        WidgetRefreshScheduler.sync(ctx)

        // 同一个 PendingIntent 上重复 setInexactRepeating 只会替换，不会堆积。
        assertEquals("重复调用应该幂等", 1, alarms().size)
    }

    @Test
    fun `把小组件删光之后闹钟会被撤掉`() {
        val id = addListWidget()
        WidgetRefreshScheduler.sync(ctx)
        assertEquals("先确认装上了", 1, alarms().size)

        removeWidget(id)

        WidgetRefreshScheduler.sync(ctx)
        assertTrue("最后一个小组件删掉后应该撤掉闹钟", alarms().isEmpty())
    }

    @Test
    fun `快速添加小组件也算有小组件`() {
        // 只装了「快速添加」时，那个按钮的配色同样需要跟着主题走。
        addAddButtonWidget()
        assertTrue(WidgetRefreshScheduler.hasAnyWidget(ctx))

        WidgetRefreshScheduler.sync(ctx)
        assertEquals("只装快速添加也要跑定时刷新", 1, alarms().size)
    }

    @Test
    fun `hasAnyWidget 在没有小组件时为假`() {
        assertFalse("默认不该认为有小组件", WidgetRefreshScheduler.hasAnyWidget(ctx))
    }

    // ------------------------------------------------- 从小组件生命周期自动装卸

    @Test
    fun `小组件 onUpdate 会装上闹钟`() {
        val id = addListWidget()
        NoteWidgetProvider().onUpdate(
            ctx,
            android.appwidget.AppWidgetManager.getInstance(ctx),
            intArrayOf(id),
        )

        assertEquals("onUpdate 应该顺带把闹钟装好", 1, alarms().size)
    }

    @Test
    fun `小组件 onDeleted 在删光后撤掉闹钟`() {
        val id = addListWidget()
        val provider = NoteWidgetProvider()
        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
        provider.onUpdate(ctx, mgr, intArrayOf(id))
        assertEquals(1, alarms().size)

        removeWidget(id)
        provider.onDeleted(ctx, intArrayOf(id))

        assertTrue("删光之后 onDeleted 应该撤掉闹钟", alarms().isEmpty())
    }

    @Test
    fun `快速添加 onUpdate 也会装上闹钟`() {
        val id = addAddButtonWidget()
        AddNoteWidgetProvider().onUpdate(
            ctx,
            android.appwidget.AppWidgetManager.getInstance(ctx),
            intArrayOf(id),
        )

        assertEquals("快速添加的 onUpdate 也要装闹钟", 1, alarms().size)
    }

    // -------------------------------------------------------------- 清单与接收

    @Test
    fun `定时刷新的 action 在清单里注册了`() {
        // 闹钟发出去之后必须有人接。清单里少了这条 action，广播会被直接丢弃
        // （显式 Intent 找不到接收器时不会报错，只会静默无事发生）。
        val resolved = ctx.packageManager.queryBroadcastReceivers(
            Intent(WidgetRefreshScheduler.ACTION_REFRESH_TICK).setPackage(ctx.packageName),
            0,
        )
        assertTrue(
            "清单里必须注册 ACTION_REFRESH_TICK，否则闹钟发出来没人接；" +
                "实际解析到的是 ${resolved.map { it.activityInfo?.name }}",
            resolved.any { it.activityInfo?.name == ThemeChangeReceiver::class.java.name },
        )
    }

    @Test
    fun `接收器处理定时刷新不会崩溃`() {
        // 定时刷新走的正是这条路径：重刷小组件 + 校正闹钟状态。
        ThemeChangeReceiver().onReceive(
            ctx,
            Intent(ctx, ThemeChangeReceiver::class.java)
                .setAction(WidgetRefreshScheduler.ACTION_REFRESH_TICK),
        )
        // 没崩即可；刷新的实际效果由 WidgetRenderer 的其他测试覆盖。
        assertNotNull(ctx)
    }

    @Test
    fun `定时刷新不会把用户数据弄丢`() {
        // 每次醒来只是重算配色、重刷 RemoteViews，绝不碰记事数据。
        val before = Repo.file(ctx).exists()
        ThemeChangeReceiver().onReceive(
            ctx,
            Intent(ctx, ThemeChangeReceiver::class.java)
                .setAction(WidgetRefreshScheduler.ACTION_REFRESH_TICK),
        )
        assertEquals("定时刷新不该创建或删除数据文件", before, Repo.file(ctx).exists())
    }

    // ------------------------------------------------------------------ 兜底

    @Test
    fun `定时闹钟的 PendingIntent 是不可变的`() {
        // targetSdk 34 下可变 PendingIntent 要求显式声明；这里不需要 fill-in，
        // 用 IMMUTABLE 更安全，也避免以后有人误加 FLAG_MUTABLE。
        addListWidget()
        WidgetRefreshScheduler.sync(ctx)

        val pi = alarms().first().operation
        assertTrue("定时闹钟的 PendingIntent 应该是不可变的", shadowOf(pi).isImmutable)
    }

    @Test
    fun `撤掉闹钟不会抛异常`() {
        // cancel 走的是同一个 PendingIntent；没有闹钟时重复撤也必须安全。
        WidgetRefreshScheduler.sync(ctx)
        WidgetRefreshScheduler.sync(ctx)

        assertTrue(alarms().isEmpty())
        assertNull("没有闹钟时 nextScheduledAlarm 应为空", shadowOf(alarmManager).nextScheduledAlarm)
    }
}
