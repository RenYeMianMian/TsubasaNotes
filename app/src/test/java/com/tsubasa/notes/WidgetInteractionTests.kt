package com.tsubasa.notes

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.tsubasa.notes.data.Prefs
import com.tsubasa.notes.data.Repo
import com.tsubasa.notes.widget.NoteWidgetProvider
import com.tsubasa.notes.widget.WidgetRenderer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 小组件的**交互链路**：模拟桌面发过来的广播，验证点一下真的改了数据。
 *
 * 这条链路是小组件的核心（用户不打开 App 就能勾选/排序），
 * 光有渲染逻辑正确不够 —— fill-in intent 带过来的 op 必须被正确处理。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetInteractionTests {

    private lateinit var ctx: Context
    private lateinit var provider: NoteWidgetProvider
    private var widgetId: Int = 0

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        Repo.file(ctx).delete()

        // 先在 AppWidgetManager 里「注册」一个真实的小组件实例。
        // 否则 updateAppWidget(id, ...) 会因为查不到 widgetInfo 而在 Robolectric 里抛 NPE
        // （真机上对不存在的 id 更新只是空操作，不会崩）。
        val mgr = AppWidgetManager.getInstance(ctx)
        widgetId = shadowOf(mgr).createWidget(
            NoteWidgetProvider::class.java,
            R.layout.widget_notes
        )

        Prefs.setWidgetPageSize(ctx, 5)
        Prefs.setWidgetShowDone(ctx, true)
        Prefs.setWidgetPage(ctx, widgetId, 0)
        provider = NoteWidgetProvider()
    }

    @After
    fun tearDown() {
        Repo.file(ctx).delete()
    }

    /** 模拟桌面把列表项的点击事件发过来。 */
    private fun sendItemClick(noteId: String, op: String) {
        val i = Intent(ctx, NoteWidgetProvider::class.java).apply {
            action = WidgetRenderer.ACTION_ITEM
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            putExtra(WidgetRenderer.EXTRA_NOTE_ID, noteId)
            putExtra(WidgetRenderer.EXTRA_OP, op)
        }
        provider.onReceive(ctx, i)
    }

    private fun sendAction(action: String) {
        val i = Intent(ctx, NoteWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        provider.onReceive(ctx, i)
    }

    @Test
    fun `点勾选会把记事标记为已完成`() {
        val n = Repo.add(ctx, "买牛奶")!!

        sendItemClick(n.id, WidgetRenderer.OP_TOGGLE)

        assertTrue("点一下应该变成已完成", Repo.all(ctx).first().done)
    }

    @Test
    fun `再点一次会取消完成`() {
        val n = Repo.add(ctx, "买牛奶")!!
        Repo.setDone(ctx, n.id, true)

        sendItemClick(n.id, WidgetRenderer.OP_TOGGLE)

        assertFalse("再点一下应该取消完成", Repo.all(ctx).first().done)
    }

    @Test
    fun `点上移会把该条往前挪一位`() {
        Repo.add(ctx, "A")
        val b = Repo.add(ctx, "B")!!

        sendItemClick(b.id, WidgetRenderer.OP_UP)

        assertEquals(listOf("B", "A"), Repo.all(ctx).map { it.text })
    }

    @Test
    fun `点删除会删掉该条`() {
        val a = Repo.add(ctx, "A")!!
        Repo.add(ctx, "B")

        sendItemClick(a.id, WidgetRenderer.OP_DELETE)

        assertEquals(listOf("B"), Repo.all(ctx).map { it.text })
    }

    @Test
    fun `翻页会改变当前页显示的记事`() {
        repeat(12) { Repo.add(ctx, "记事 ${it + 1}") }
        Prefs.setWidgetPageSize(ctx, 5)

        assertEquals("记事 1", WidgetRenderer.pageItems(ctx, widgetId).first().text)

        sendAction(WidgetRenderer.ACTION_PAGE_NEXT)
        assertEquals("记事 6", WidgetRenderer.pageItems(ctx, widgetId).first().text)

        sendAction(WidgetRenderer.ACTION_PAGE_NEXT)
        assertEquals("记事 11", WidgetRenderer.pageItems(ctx, widgetId).first().text)
    }

    @Test
    fun `翻到最后一页再点下一页会绕回第一页`() {
        repeat(12) { Repo.add(ctx, "记事 ${it + 1}") }
        Prefs.setWidgetPageSize(ctx, 5)
        Prefs.setWidgetPage(ctx, widgetId, 2) // 第三页（最后一页）

        sendAction(WidgetRenderer.ACTION_PAGE_NEXT)

        assertEquals(0, WidgetRenderer.currentPage(ctx, widgetId))
    }

    @Test
    fun `在第一页点上一页会绕到最后一页`() {
        repeat(12) { Repo.add(ctx, "记事 ${it + 1}") }
        Prefs.setWidgetPageSize(ctx, 5)
        Prefs.setWidgetPage(ctx, widgetId, 0)

        sendAction(WidgetRenderer.ACTION_PAGE_PREV)

        assertEquals(2, WidgetRenderer.currentPage(ctx, widgetId))
    }

    @Test
    fun `删除最后一条后页码会回到有效范围`() {
        repeat(11) { Repo.add(ctx, "记事 ${it + 1}") }
        Prefs.setWidgetPageSize(ctx, 5)
        Prefs.setWidgetPage(ctx, widgetId, 2) // 第三页，只有 1 条

        // 把第三页那条删掉，总共只剩 10 条 = 2 页
        val last = Repo.all(ctx).last()
        sendItemClick(last.id, WidgetRenderer.OP_DELETE)

        // 存的页码还是 2，但读取时要夹回有效范围，不能返回空
        val items = WidgetRenderer.pageItems(ctx, widgetId)
        assertTrue("删除后仍应显示有效内容", items.isNotEmpty())
        assertEquals("记事 6", items.first().text)
    }

    @Test
    fun `操作不存在的记事id不会崩溃`() {
        Repo.add(ctx, "A")

        sendItemClick("不存在的id", WidgetRenderer.OP_TOGGLE)
        sendItemClick("不存在的id", WidgetRenderer.OP_UP)
        sendItemClick("不存在的id", WidgetRenderer.OP_DELETE)

        assertEquals(1, Repo.all(ctx).size)
    }

    @Test
    fun `缺少附加信息的事件不会崩溃`() {
        val i = Intent(ctx, NoteWidgetProvider::class.java).apply {
            action = WidgetRenderer.ACTION_ITEM
        }
        provider.onReceive(ctx, i) // 没有 id、没有 op
        assertTrue(true)
    }

    @Test
    fun `刷新广播不会改变数据`() {
        Repo.add(ctx, "A")
        sendAction(WidgetRenderer.ACTION_REFRESH)
        assertEquals(1, Repo.all(ctx).size)
    }
}
