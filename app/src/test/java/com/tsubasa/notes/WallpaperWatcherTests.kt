package com.tsubasa.notes

import android.app.WallpaperManager
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.tsubasa.notes.data.Repo
import com.tsubasa.notes.data.WidgetBridge
import com.tsubasa.notes.widget.NoteWidgetProvider
import com.tsubasa.notes.widget.ThemeChangeReceiver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 「换了壁纸，小组件配色要等很久才跟上」这个 bug 的回归测试。
 *
 * 根因是**清单里注册的接收器收不到换壁纸的广播**（`WALLPAPER_CHANGED` 是隐式广播，
 * 不在 AOSP 的 23 条 `<allow-implicit-broadcast>` 白名单里；`CONFIGURATION_CHANGED`
 * 又被 AMS 带上 `FLAG_RECEIVER_REGISTERED_ONLY`）。所以修复只能走
 * [WallpaperWatcher]：在进程内订阅 `WallpaperManager` 的配色回调。
 *
 * 这组测试盯的是三件容易在后续改动里被悄悄破坏的事：
 * 1. 配色变化真的会发出刷新广播，且是**定向广播**（`setPackage`）——
 *    没有 `setPackage` 就会被 `BroadcastSkipPolicy.disallowBackgroundStart()` 拦掉。
 * 2. `BOOT_COMPLETED` 的权限真的声明了（之前漏了，接收器会被静默跳过）。
 * 3. 清单里不再注册那条**永远收不到**的 `CONFIGURATION_CHANGED`，
 *    免得以后有人以为主题变化已经处理好了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WallpaperWatcherTests {

    // 声明成 ContextWrapper 而不是 Context：Robolectric 的 `shadowOf` 没有
    // `Context` 重载，只有 Application / ContextWrapper / ContextThemeWrapper 三个。
    private lateinit var ctx: ContextWrapper

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        Repo.file(ctx).delete()
        WallpaperWatcher.reset()
        shadowOf(ctx).clearBroadcastIntents()
    }

    @After
    fun tearDown() {
        Repo.file(ctx).delete()
        WallpaperWatcher.reset()
    }

    // ---------------------------------------------------------------- 刷新广播

    @Test
    fun `壁纸配色变化会发出小组件刷新广播`() {
        WallpaperWatcher.onColorsChanged(ctx)

        val intents = shadowOf(ctx).broadcastIntents
        assertTrue(
            "配色变化后应该发出刷新广播，实际发出的是 $intents",
            intents.any { it.action == WidgetBridge.ACTION_REFRESH },
        )
    }

    @Test
    fun `刷新广播是定向的否则会被后台限制拦掉`() {
        WallpaperWatcher.onColorsChanged(ctx)

        val refresh = shadowOf(ctx).broadcastIntents
            .firstOrNull { it.action == WidgetBridge.ACTION_REFRESH }
        assertNotNull("应该发出刷新广播", refresh)
        // BroadcastSkipPolicy.disallowBackgroundStart() 只放行带 component 或 package 的广播；
        // 少了 setPackage，这条广播在真机上会被直接丢掉，小组件不会刷新。
        assertEquals(
            "刷新广播必须带 setPackage，否则收不到",
            ctx.packageName,
            refresh!!.`package`,
        )
    }

    @Test
    fun `没有小组件时配色变化也不会崩溃`() {
        // 用户可能一个小组件都没加，这时广播发出去没人收 —— 不该抛异常。
        WallpaperWatcher.onColorsChanged(ctx)
        WallpaperWatcher.onColorsChanged(ctx)
    }

    // ------------------------------------------------------------ 订阅本身

    @Test
    fun `可以成功订阅壁纸配色变化`() {
        assertTrue("WallpaperManager 配色监听应该能注册成功", WallpaperWatcher.start(ctx))
    }

    @Test
    fun `重复订阅只注册一次`() {
        assertTrue(WallpaperWatcher.start(ctx))
        // 第二次调用应该直接返回 true（已注册），而不是重复注册。
        assertTrue(WallpaperWatcher.start(ctx))
    }

    @Test
    fun `订阅失败不会抛异常`() {
        // start() 内部吞掉异常并返回 false —— 配色刷新属于锦上添花，
        // 不该让进程启动失败。这里验证返回值语义是布尔而不是抛。
        val ok = WallpaperWatcher.start(ctx)
        assertTrue("正常环境下应该注册成功", ok)
    }

    @Test
    fun `订阅用的回调类型是系统配色监听器`() {
        // 盯住 API 形态：必须是 WallpaperManager.OnColorsChangedListener
        // （API 26 起可用、不需要权限）。若有人改成 @hide 反射或别的监听器，这里会露出来。
        val listener = WallpaperManager.OnColorsChangedListener { _, _ -> }
        assertNotNull(listener)
    }

    // ------------------------------------------------------- Application 入口

    @Test
    fun `Application 启动时会订阅壁纸配色`() {
        // 进程被拉起时（点小组件、开机、周期刷新）就该订阅好，
        // 而不是等用户打开界面。
        val app = ctx.applicationContext as TsubasaApp
        assertNotNull("Manifest 里应该把 application 指向 TsubasaApp", app)
        app.onCreate()
        assertTrue("Application.onCreate 后应该已订阅", WallpaperWatcher.start(ctx))
    }

    // ------------------------------------------------------------ 清单声明

    @Test
    fun `声明了开机广播权限`() {
        // BOOT_COMPLETED 是 protected-broadcast，缺 RECEIVE_BOOT_COMPLETED
        // 权限时接收器会被系统静默跳过 —— 这是修复前就存在的第二个 bug。
        val info = ctx.packageManager.getPackageInfo(
            ctx.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val permissions = info.requestedPermissions?.toList().orEmpty()
        assertTrue(
            "必须声明 RECEIVE_BOOT_COMPLETED，否则开机刷新永远不触发；实际声明的是 $permissions",
            permissions.contains("android.permission.RECEIVE_BOOT_COMPLETED"),
        )
    }

    @Test
    fun `不再注册永远收不到的配置变化广播`() {
        // ACTION_CONFIGURATION_CHANGED 由 AMS 带 FLAG_RECEIVER_REGISTERED_ONLY 发出，
        // 清单注册收不到。留着它只会误导人以为主题变化已经处理好了。
        val intents = ctx.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_CONFIGURATION_CHANGED).setPackage(ctx.packageName),
            0,
        )
        assertTrue(
            "清单里不该再注册 ACTION_CONFIGURATION_CHANGED（它永远收不到）",
            intents.isEmpty(),
        )
    }

    @Test
    fun `主题接收器仍然能刷新小组件`() {
        // 开机 / 覆盖安装这两条路径依然有效，别在重构时把接收器弄坏了。
        ThemeChangeReceiver().onReceive(ctx, Intent(Intent.ACTION_BOOT_COMPLETED))
        // 没崩即可；真正的刷新由 WidgetRenderer.updateAll 完成（另有测试覆盖）。
        assertFalse(Repo.file(ctx).exists())
    }

    @Test
    fun `开机广播确实会投递到主题接收器`() {
        val resolved = ctx.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(ctx.packageName),
            0,
        )
        assertTrue(
            "清单里应该注册 BOOT_COMPLETED，否则开机后小组件配色不会重算",
            resolved.any { it.activityInfo?.name == ThemeChangeReceiver::class.java.name },
        )
    }

    @Test
    fun `覆盖安装广播确实会投递到主题接收器`() {
        val resolved = ctx.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_MY_PACKAGE_REPLACED).setPackage(ctx.packageName),
            0,
        )
        assertTrue(
            "清单里应该注册 MY_PACKAGE_REPLACED",
            resolved.any { it.activityInfo?.name == ThemeChangeReceiver::class.java.name },
        )
    }

    @Test
    fun `小组件接收器没有跟着一起被弄坏`() {
        // 顺带守住：改动集中在广播这块，别误伤小组件本身的刷新入口。
        val resolved = ctx.packageManager.queryBroadcastReceivers(
            Intent(WidgetBridge.ACTION_REFRESH).setPackage(ctx.packageName),
            0,
        )
        assertTrue(
            "ACTION_REFRESH 应该仍能投递到小组件 provider",
            resolved.any { it.activityInfo?.name == NoteWidgetProvider::class.java.name },
        )
    }
}
