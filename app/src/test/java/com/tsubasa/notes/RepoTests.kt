package com.tsubasa.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tsubasa.notes.data.Note
import com.tsubasa.notes.data.Prefs
import com.tsubasa.notes.data.Repo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 数据层：增删改、排序、持久化的真实行为。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepoTests {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        Repo.file(ctx).delete()
    }

    @After
    fun tearDown() {
        Repo.file(ctx).delete()
    }

    @Test
    fun `新增记事并按顺序保留`() {
        Repo.add(ctx, "第一条")
        Repo.add(ctx, "第二条")
        Repo.add(ctx, "第三条")

        val all = Repo.all(ctx)
        assertEquals(3, all.size)
        assertEquals(listOf("第一条", "第二条", "第三条"), all.map { it.text })
    }

    @Test
    fun `空白内容不会被添加`() {
        Repo.add(ctx, "   ")
        Repo.add(ctx, "")
        assertEquals(0, Repo.all(ctx).size)
    }

    @Test
    fun `内容首尾空格会被去掉`() {
        Repo.add(ctx, "  买菜  ")
        assertEquals("买菜", Repo.all(ctx).first().text)
    }

    @Test
    fun `勾选与取消勾选`() {
        val n = Repo.add(ctx, "写周报")!!
        assertFalse(Repo.all(ctx).first().done)

        Repo.toggleDone(ctx, n.id)
        assertTrue(Repo.all(ctx).first().done)

        Repo.toggleDone(ctx, n.id)
        assertFalse(Repo.all(ctx).first().done)
    }

    @Test
    fun `删除只删掉目标那一条`() {
        val a = Repo.add(ctx, "A")!!
        Repo.add(ctx, "B")
        Repo.add(ctx, "C")

        Repo.delete(ctx, a.id)

        assertEquals(listOf("B", "C"), Repo.all(ctx).map { it.text })
    }

    @Test
    fun `拖拽排序把元素移动到目标位置`() {
        Repo.add(ctx, "A")
        Repo.add(ctx, "B")
        Repo.add(ctx, "C")

        // 把第 0 个（A）移到第 2 个位置
        Repo.move(ctx, 0, 2)

        assertEquals(listOf("B", "C", "A"), Repo.all(ctx).map { it.text })
    }

    @Test
    fun `按 id 列表重排`() {
        val a = Repo.add(ctx, "A")!!
        val b = Repo.add(ctx, "B")!!
        val c = Repo.add(ctx, "C")!!

        Repo.reorder(ctx, listOf(c.id, a.id, b.id))

        assertEquals(listOf("C", "A", "B"), Repo.all(ctx).map { it.text })
    }

    @Test
    fun `重排时漏掉的条目会保留在末尾而不是丢失`() {
        val a = Repo.add(ctx, "A")!!
        val b = Repo.add(ctx, "B")!!
        Repo.add(ctx, "C")

        // 只传两个 id，C 没在列表里 —— 不能被丢掉
        Repo.reorder(ctx, listOf(b.id, a.id))

        val texts = Repo.all(ctx).map { it.text }
        assertEquals(3, texts.size)
        assertEquals(listOf("B", "A", "C"), texts)
    }

    @Test
    fun `已完成沉底且未完成保持相对顺序`() {
        val a = Repo.add(ctx, "A")!!
        Repo.add(ctx, "B")
        val c = Repo.add(ctx, "C")!!
        Repo.add(ctx, "D")

        Repo.setDone(ctx, a.id, true)
        Repo.setDone(ctx, c.id, true)
        Repo.sortDoneToBottom(ctx)

        assertEquals(listOf("B", "D", "A", "C"), Repo.all(ctx).map { it.text })
    }

    @Test
    fun `清除已完成只留未完成`() {
        val a = Repo.add(ctx, "A")!!
        Repo.add(ctx, "B")
        Repo.setDone(ctx, a.id, true)

        Repo.clearDone(ctx)

        assertEquals(listOf("B"), Repo.all(ctx).map { it.text })
    }

    @Test
    fun `小组件上移一条`() {
        Repo.add(ctx, "A")
        val b = Repo.add(ctx, "B")!!

        Repo.moveBy(ctx, b.id, -1)

        assertEquals(listOf("B", "A"), Repo.all(ctx).map { it.text })
    }

    @Test
    fun `第一条上移不会越界`() {
        val a = Repo.add(ctx, "A")!!
        Repo.add(ctx, "B")

        Repo.moveBy(ctx, a.id, -1)

        assertEquals(listOf("A", "B"), Repo.all(ctx).map { it.text })
    }

    @Test
    fun `数据能跨实例读回（模拟小组件另起进程读取）`() {
        Repo.add(ctx, "持久化测试")
        val id = Repo.all(ctx).first().id
        Repo.setDone(ctx, id, true)

        // 直接读文件内容，确认真的落盘了，而不是只在内存里
        val raw = Repo.file(ctx).readText()
        assertTrue(raw.contains("持久化测试"))

        // 再次读取应保持一致
        val again = Repo.all(ctx)
        assertEquals(1, again.size)
        assertTrue(again.first().done)
    }

    @Test
    fun `文件损坏时回退为空列表而不是崩溃`() {
        Repo.file(ctx).writeText("{ 这不是合法 JSON")
        assertEquals(0, Repo.all(ctx).size)
    }

    @Test
    fun `visible 可以过滤掉已完成`() {
        val a = Repo.add(ctx, "A")!!
        Repo.add(ctx, "B")
        Repo.setDone(ctx, a.id, true)

        assertEquals(2, Repo.visible(ctx, includeDone = true).size)
        assertEquals(1, Repo.visible(ctx, includeDone = false).size)
        assertEquals("B", Repo.visible(ctx, includeDone = false).first().text)
    }

    @Test
    fun `编辑内容`() {
        val a = Repo.add(ctx, "旧内容")!!
        Repo.updateText(ctx, a.id, "新内容")
        assertEquals("新内容", Repo.all(ctx).first().text)
    }

    @Test
    fun `编辑成空白不会清掉内容`() {
        val a = Repo.add(ctx, "原内容")!!
        Repo.updateText(ctx, a.id, "   ")
        assertEquals("原内容", Repo.all(ctx).first().text)
    }

    @Test
    fun `Note 的 JSON 往返不丢字段`() {
        val n = Note(text = "带\n换行 和 \"引号\" 的内容", done = true)
        val back = Note.fromJson(n.toJson())

        assertEquals(n.id, back.id)
        assertEquals(n.text, back.text)
        assertEquals(n.done, back.done)
    }

    @Test
    fun `偏好设置的每页条数会被夹到合法范围`() {
        Prefs.setWidgetPageSize(ctx, 999)
        assertEquals(20, Prefs.widgetPageSize(ctx))

        Prefs.setWidgetPageSize(ctx, 0)
        assertEquals(1, Prefs.widgetPageSize(ctx))

        Prefs.setAppPageSize(ctx, 1)
        assertEquals(5, Prefs.appPageSize(ctx))
    }

    @Test
    fun `小组件页码按实例分开存`() {
        Prefs.setWidgetPage(ctx, 1, 3)
        Prefs.setWidgetPage(ctx, 2, 7)

        assertEquals(3, Prefs.widgetPage(ctx, 1))
        assertEquals(7, Prefs.widgetPage(ctx, 2))

        Prefs.clearWidgetPage(ctx, 1)
        assertEquals(0, Prefs.widgetPage(ctx, 1))
        assertEquals(7, Prefs.widgetPage(ctx, 2))
    }

    @Test
    fun `新增返回的对象能被后续查询找到`() {
        val n = Repo.add(ctx, "可检索")
        assertNotNull(n)
        assertTrue(Repo.all(ctx).any { it.id == n!!.id })
    }
}
