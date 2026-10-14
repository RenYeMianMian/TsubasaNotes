package com.tsubasa.notes

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.tsubasa.notes.data.Prefs
import com.tsubasa.notes.ui.NotesViewModel
import com.tsubasa.notes.ui.SchemeSource
import com.tsubasa.notes.ui.UiState
import com.tsubasa.notes.ui.schemeSource
import com.tsubasa.notes.widget.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * App 自定义配色（「应用外观」里的跟随系统 / 自定义颜色）。
 *
 * 这里盯的是三件容易悄悄坏掉的事：
 * 1. **优先级** —— Android 12+ 上以前无条件用动态取色，用户选了自定义也没用，
 *    这正是本次要修的 bug，所以必须有一条测试把它钉死。
 * 2. **隔离** —— 用户明确要求 App 的配色只影响 App，不能顺手把小组件也改了。
 * 3. **进得了 UiState** —— Prefs 不是 Compose State，值不放进 StateFlow 的话
 *    界面不会重组，表现就是「选了没反应」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppColorTests {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
    }

    // ------------------------------------------------------------ 优先级

    @Test
    fun `选了自定义就用自定义，跟系统版本无关`() {
        // 这是本次修的核心 bug：Android 12 及以上必须让自定义赢过动态取色
        listOf(Build.VERSION_CODES.O, Build.VERSION_CODES.R, Build.VERSION_CODES.S,
            Build.VERSION_CODES.TIRAMISU, 35, 99).forEach { sdk ->
            assertEquals(
                "sdk=$sdk 时自定义配色必须优先",
                SchemeSource.CUSTOM,
                schemeSource("custom", sdk)
            )
        }
    }

    @Test
    fun `跟随系统时 Android12 及以上用动态取色`() {
        listOf(Build.VERSION_CODES.S, Build.VERSION_CODES.TIRAMISU, 99).forEach { sdk ->
            assertEquals(
                "sdk=$sdk 应该走动态取色",
                SchemeSource.DYNAMIC,
                schemeSource("system", sdk)
            )
        }
    }

    @Test
    fun `跟随系统时低版本回退到种子色推导`() {
        // Android 12 以下没有动态取色，只能拿系统强调色自己推
        listOf(Build.VERSION_CODES.O, Build.VERSION_CODES.P, Build.VERSION_CODES.R).forEach { sdk ->
            assertEquals(
                "sdk=$sdk 应该回退到推导配色",
                SchemeSource.FALLBACK,
                schemeSource("system", sdk)
            )
        }
    }

    @Test
    fun `未知的配色取值当作跟随系统`() {
        // 偏好文件损坏或来自旧版本时不能崩，退回到系统配色是安全的
        listOf("", "CUSTOM", "custom ", "whatever").forEach { v ->
            assertNotEquals(
                "「$v」不该被当成自定义",
                SchemeSource.CUSTOM,
                schemeSource(v, Build.VERSION_CODES.S)
            )
        }
    }

    // ------------------------------------------------------------ 偏好读写

    @Test
    fun `默认跟随系统且种子色是默认紫`() {
        assertEquals("system", Prefs.appColor(ctx))
        assertEquals(0xFF6750A4.toInt(), Prefs.appSeed(ctx))
    }

    @Test
    fun `配色来源能存能读`() {
        Prefs.setAppColor(ctx, "custom")
        assertEquals("custom", Prefs.appColor(ctx))
        Prefs.setAppColor(ctx, "system")
        assertEquals("system", Prefs.appColor(ctx))
    }

    @Test
    fun `App 种子色能存能读`() {
        val green = 0xFF3F6833.toInt()
        Prefs.setAppSeed(ctx, green)
        assertEquals(green, Prefs.appSeed(ctx))
    }

    // ------------------------------------------------------------ 与小组件隔离

    @Test
    fun `改 App 配色不会动到小组件配色`() {
        // 用户的要求就是两边分开：App 换色，桌面小组件保持原样
        val widgetSeedBefore = Prefs.widgetSeed(ctx)
        val widgetThemeBefore = Prefs.widgetTheme(ctx)

        Prefs.setAppColor(ctx, "custom")
        Prefs.setAppSeed(ctx, 0xFFB3261E.toInt())

        assertEquals("小组件种子色不该被改", widgetSeedBefore, Prefs.widgetSeed(ctx))
        assertEquals("小组件配色来源不该被改", widgetThemeBefore, Prefs.widgetTheme(ctx))
    }

    @Test
    fun `改小组件配色不会动到 App 配色`() {
        // 反方向也要成立，否则调小组件会把 App 的主题一起带跑
        val appSeedBefore = Prefs.appSeed(ctx)
        val appColorBefore = Prefs.appColor(ctx)

        Prefs.setWidgetTheme(ctx, "custom")
        Prefs.setWidgetSeed(ctx, 0xFF00696D.toInt())

        assertEquals("App 种子色不该被改", appSeedBefore, Prefs.appSeed(ctx))
        assertEquals("App 配色来源不该被改", appColorBefore, Prefs.appColor(ctx))
    }

    @Test
    fun `两边的种子色可以同时不同`() {
        Prefs.setAppSeed(ctx, 0xFF0061A4.toInt())
        Prefs.setWidgetSeed(ctx, 0xFF8B5000.toInt())
        assertEquals(0xFF0061A4.toInt(), Prefs.appSeed(ctx))
        assertEquals(0xFF8B5000.toInt(), Prefs.widgetSeed(ctx))
    }

    // ------------------------------------------------------------ 进 UiState

    @Test
    fun `App 配色会进入 UiState`() {
        // 不放进 StateFlow 的话界面不会重组，用户看到的就是「点了没反应」
        val vm = NotesViewModel(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        )
        vm.setAppColor("custom")
        vm.setAppSeed(0xFF3F6833.toInt())

        assertEquals("custom", vm.state.value.appColor)
        assertEquals(0xFF3F6833.toInt(), vm.state.value.appSeed)
    }

    @Test
    fun `App 明暗也会进入 UiState`() {
        // setAppTheme 以前写的是 update { it.copy() }，值没变时 StateFlow 会 conflate，
        // 等于不发射 —— 顺手修掉的那个 bug 用这条测试看着
        val vm = NotesViewModel(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        )
        vm.setAppTheme("dark")
        assertEquals("dark", vm.state.value.appTheme)
        vm.setAppTheme("light")
        assertEquals("light", vm.state.value.appTheme)
    }

    @Test
    fun `新建的 ViewModel 能从偏好里读出 App 配色`() {
        // 冷启动路径：refresh() 必须真的去读这两个偏好，否则「设置里显示自定义、
        // 重启后变回系统色」—— 用户会以为设置没保存住。
        //
        // refresh() 是异步的（viewModelScope + Dispatchers.IO），所以这里要
        // 推一下主线程 Looper 才能拿到结果，不能构造完就断言。
        Prefs.setAppColor(ctx, "custom")
        Prefs.setAppSeed(ctx, 0xFF8C4A60.toInt())

        val vm = NotesViewModel(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        )
        val s = awaitLoaded(vm)
        assertEquals("custom", s.appColor)
        assertEquals(0xFF8C4A60.toInt(), s.appSeed)
    }

    @Test
    fun `新建的 ViewModel 默认跟随系统`() {
        Prefs.setAppColor(ctx, "system")
        val vm = NotesViewModel(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        )
        val s = awaitLoaded(vm)
        assertEquals("system", s.appColor)
        assertEquals(0xFF6750A4.toInt(), s.appSeed)
    }

    /**
     * 等 [NotesViewModel.refresh] 落地。
     *
     * 协程跑在 IO 线程上，完成后把续体投回主线程，所以交替「推 Looper + 小睡」
     * 就能稳定拿到结果，比死等一个固定时长可靠。
     */
    private fun awaitLoaded(vm: NotesViewModel): UiState {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            val s = vm.state.value
            if (!s.loading) return s
            Thread.sleep(5)
        }
        throw AssertionError("refresh() 超时未完成，loading 一直是 true")
    }

    // ------------------------------------------------------------ 配色推导

    @Test
    fun `每个预设色都能推导出可用的配色`() {
        // 色板里的任意一个颜色都不能推出「看不见字」的配色
        Palette.presets.forEach { seed ->
            listOf(true, false).forEach { dark ->
                val s = Palette.schemeFrom(seed, dark)
                assertTrue("seed=$seed dark=$dark 的背景与文字不能同色", s.bg != s.onBg)
                assertTrue("seed=$seed dark=$dark 的强调色要与背景可分", s.accent != s.bg)
                assertEquals("明暗标记要与请求一致", dark, s.dark)
            }
        }
    }

    @Test
    fun `同一个种子色在深浅色下推出不同配色`() {
        val seed = Palette.presets.first()
        assertNotEquals(Palette.schemeFrom(seed, true).bg, Palette.schemeFrom(seed, false).bg)
    }
}
