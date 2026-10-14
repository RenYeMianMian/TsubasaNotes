package com.tsubasa.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tsubasa.notes.data.Prefs
import com.tsubasa.notes.ui.background.ImageStore
import com.tsubasa.notes.widget.WidgetRenderer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 本轮新增外观功能的逻辑：不透明度换算、UI 风格、背景图路径管理。
 *
 * 这些值最终都要跨进程传给小组件，所以边界（越界、文件不存在、空路径）
 * 必须稳 —— 一个越界的 alpha 会让 RemoteViews 直接抛异常，小组件变空白。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppearanceTests {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        ImageStore.clear(ctx, forWidget = true)
        ImageStore.clear(ctx, forWidget = false)
    }

    // ------------------------------------------------------------ 不透明度

    @Test
    fun `不透明度100对应完全不透明`() {
        assertEquals(1f, WidgetRenderer.bgAlpha(100), 0.0001f)
    }

    @Test
    fun `不透明度0仍保留一点可见度`() {
        // 全透明的小组件在桌面上等于「消失」，用户会以为组件坏了，
        // 所以下限是 MIN_ALPHA 而不是 0。
        val a = WidgetRenderer.bgAlpha(0)
        assertTrue("不能真的全透明", a > 0f)
        assertEquals(WidgetRenderer.MIN_ALPHA, a, 0.0001f)
    }

    @Test
    fun `alpha 永远落在 0到1 之间`() {
        // 越界的 alpha 会让 RemoteViews 抛异常，必须夹住
        listOf(-999, -1, 0, 1, 50, 99, 100, 101, 9999).forEach { v ->
            val a = WidgetRenderer.bgAlpha(v)
            assertTrue("bgAlpha($v) = $a 越界", a in 0f..1f)
        }
    }

    @Test
    fun `alpha 随不透明度单调递增`() {
        var last = -1f
        for (o in 0..100 step 5) {
            val a = WidgetRenderer.bgAlpha(o)
            assertTrue("不透明度 $o 处没有递增", a > last)
            last = a
        }
    }

    @Test
    fun `偏好里的不透明度会被夹到合法范围`() {
        Prefs.setWidgetBgOpacity(ctx, 500)
        assertEquals(100, Prefs.widgetBgOpacity(ctx))

        Prefs.setWidgetBgOpacity(ctx, -20)
        assertEquals(0, Prefs.widgetBgOpacity(ctx))

        Prefs.setWidgetBgOpacity(ctx, 42)
        assertEquals(42, Prefs.widgetBgOpacity(ctx))
    }

    // ------------------------------------------------------------ 背景模糊

    @Test
    fun `模糊设置会被夹到合法范围`() {
        Prefs.setBackdropBlur(ctx, 500)
        assertEquals(100, Prefs.backdropBlur(ctx))

        Prefs.setBackdropBlur(ctx, -20)
        assertEquals(0, Prefs.backdropBlur(ctx))

        Prefs.setBackdropBlur(ctx, 42)
        assertEquals(42, Prefs.backdropBlur(ctx))
    }

    @Test
    fun `模糊设置会进入 UiState`() {
        val vm = com.tsubasa.notes.ui.NotesViewModel(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        )
        vm.setBarBlur(70)
        assertEquals(70, vm.state.value.barBlur)
    }

    // ------------------------------------------------------------ 背景图

    @Test
    fun `默认没有自定义背景`() {
        assertEquals("", Prefs.appBgImage(ctx))
        assertEquals("", Prefs.widgetBgImage(ctx))
        assertFalse(ImageStore.exists(""))
    }

    @Test
    fun `不存在的路径会被判定为不可用`() {
        // 路径存了但文件被清了（用户清数据、换机恢复备份等），必须能识别出来，
        // 否则会拿着一个死路径去解码，白白报错
        assertFalse(ImageStore.exists("/完全不存在的路径/abc.jpg"))
        assertFalse(ImageStore.exists(ctx.filesDir.absolutePath + "/never_created.jpg"))
    }

    @Test
    fun `真实存在的文件会被判定为可用`() {
        val f = java.io.File(ctx.filesDir, "probe.jpg")
        f.writeBytes(ByteArray(64) { 1 })
        assertTrue(ImageStore.exists(f.absolutePath))
        f.delete()
    }

    @Test
    fun `空文件不算有效背景`() {
        // 长度 0 的文件通常是上次写入失败的残留，不能当成有效背景
        val f = java.io.File(ctx.filesDir, "empty.jpg")
        f.writeBytes(ByteArray(0))
        assertFalse(ImageStore.exists(f.absolutePath))
        f.delete()
    }

    @Test
    fun `设置与清除背景路径`() {
        Prefs.setAppBgImage(ctx, "/tmp/a.jpg")
        assertEquals("/tmp/a.jpg", Prefs.appBgImage(ctx))
        Prefs.setAppBgImage(ctx, "")
        assertEquals("", Prefs.appBgImage(ctx))

        Prefs.setWidgetBgImage(ctx, "/tmp/w.jpg")
        assertEquals("/tmp/w.jpg", Prefs.widgetBgImage(ctx))
        Prefs.setWidgetBgImage(ctx, "")
        assertEquals("", Prefs.widgetBgImage(ctx))
    }

    @Test
    fun `软件背景路径会进入 UiState（否则界面不会刷新）`() {
        // 这是「小组件背景能换、软件背景换不了」那个 bug 的回归测试。
        // 背景路径必须能从 state 读到，界面才可能在换图后重组；
        // 只写进 Prefs 是不够的 —— Prefs 不是 Compose State。
        val vm = com.tsubasa.notes.ui.NotesViewModel(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        )
        vm.setAppBgImage("/tmp/new_bg.jpg")

        assertEquals(
            "背景路径没有同步进 UiState",
            "/tmp/new_bg.jpg",
            vm.state.value.appBgImage
        )
    }

    @Test
    fun `清除软件背景也会同步进 UiState`() {
        val vm = com.tsubasa.notes.ui.NotesViewModel(
            ApplicationProvider.getApplicationContext<android.app.Application>()
        )
        vm.setAppBgImage("/tmp/x.jpg")
        vm.setAppBgImage("")

        assertEquals("", vm.state.value.appBgImage)
    }

    @Test
    fun `清除背景会删掉文件`() {
        val f = ImageStore.widgetBgFile(ctx)
        f.writeBytes(ByteArray(32) { 7 })
        assertTrue(f.exists())

        assertTrue(ImageStore.clear(ctx, forWidget = true))
        assertFalse(f.exists())
    }

    @Test
    fun `清除不存在的背景不会失败`() {
        // 用户可能连点两次「恢复默认」，第二次不能报错
        ImageStore.clear(ctx, forWidget = false)
        assertTrue(ImageStore.clear(ctx, forWidget = false))
    }

    // ------------------------------------------------------------ 小组件渲染容错

    @Test
    fun `背景路径指向不存在的文件时小组件仍能渲染`() {
        Prefs.setWidgetBgImage(ctx, "/不存在的/背景.jpg")

        // 不能因为背景图丢了就让整个小组件构建失败
        val views = WidgetRenderer.build(ctx, 1)
        org.junit.Assert.assertNotNull(views)
    }

    @Test
    fun `各种不透明度下小组件都能渲染`() {
        listOf(0, 1, 50, 99, 100).forEach { o ->
            Prefs.setWidgetBgOpacity(ctx, o)
            org.junit.Assert.assertNotNull(
                "不透明度 $o 时构建失败",
                WidgetRenderer.build(ctx, 1)
            )
        }
    }
}
