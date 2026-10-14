package com.tsubasa.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tsubasa.notes.data.Prefs
import com.tsubasa.notes.data.Repo
import com.tsubasa.notes.widget.Palette
import com.tsubasa.notes.widget.WidgetRenderer
import com.tsubasa.notes.widget.WidgetRow
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

/**
 * 小组件的渲染与分页逻辑。
 *
 * 重点验证「每页显示多少条」「翻页」「配色」这三件用户直接看得见的事。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetTests {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        Repo.file(ctx).delete()
        Prefs.setWidgetPageSize(ctx, 5)
        Prefs.setWidgetShowDone(ctx, true)
        Prefs.setWidgetTheme(ctx, "custom")
        Prefs.setWidgetSeed(ctx, 0xFF0061A4.toInt())
        Prefs.setGroupDone(ctx, true)
        Prefs.setWidgetPage(ctx, 1, 0)
    }

    @After
    fun tearDown() {
        Repo.file(ctx).delete()
    }

    private fun seed(n: Int) {
        repeat(n) { Repo.add(ctx, "记事 ${it + 1}") }
    }

    // ------------------------------------------------------------ 分页

    @Test
    fun `每页条数决定第一页显示多少`() {
        seed(12)
        Prefs.setWidgetPageSize(ctx, 5)

        val page1 = WidgetRenderer.pageItems(ctx, 1)

        assertEquals(5, page1.size)
        assertEquals("记事 1", page1.first().text)
        assertEquals("记事 5", page1.last().text)
    }

    @Test
    fun `改成每页三条后立即生效`() {
        seed(12)
        Prefs.setWidgetPageSize(ctx, 3)

        assertEquals(3, WidgetRenderer.pageItems(ctx, 1).size)
    }

    @Test
    fun `最后一页只显示剩余条数`() {
        seed(12)
        Prefs.setWidgetPageSize(ctx, 5)
        Prefs.setWidgetPage(ctx, 1, 2) // 第三页：第 11、12 条

        val last = WidgetRenderer.pageItems(ctx, 1)

        assertEquals(2, last.size)
        assertEquals("记事 11", last.first().text)
        assertEquals("记事 12", last.last().text)
    }

    @Test
    fun `总页数按向上取整`() {
        assertEquals(1, WidgetRenderer.pageCount(0, 5))
        assertEquals(1, WidgetRenderer.pageCount(5, 5))
        assertEquals(2, WidgetRenderer.pageCount(6, 5))
        assertEquals(3, WidgetRenderer.pageCount(11, 5))
    }

    @Test
    fun `页码超出范围时会被夹回最后一页`() {
        seed(6)
        Prefs.setWidgetPageSize(ctx, 5)
        Prefs.setWidgetPage(ctx, 1, 99) // 只有 2 页，却存了第 100 页

        val page = WidgetRenderer.pageItems(ctx, 1)

        assertEquals(1, page.size)
        assertEquals("记事 6", page.first().text)
    }

    @Test
    fun `没有记事时返回空且页数为 1`() {
        assertEquals(0, WidgetRenderer.pageItems(ctx, 1).size)
        assertEquals(1, WidgetRenderer.pageCount(0, 5))
    }

    @Test
    fun `隐藏已完成项后分页会跟着变`() {
        seed(10)
        val first = Repo.all(ctx).first().id
        Repo.setDone(ctx, first, true)

        Prefs.setWidgetShowDone(ctx, false)
        val shown = WidgetRenderer.pageItems(ctx, 1)

        assertEquals(5, shown.size)
        // 第 1 条已完成被藏起来了，所以第一条应该是「记事 2」
        assertEquals("记事 2", shown.first().text)
    }

    @Test
    fun `多个小组件实例的页码互不影响`() {
        seed(20)
        Prefs.setWidgetPageSize(ctx, 5)

        Prefs.setWidgetPage(ctx, 1, 1)
        Prefs.setWidgetPage(ctx, 2, 2)

        assertEquals("记事 6", WidgetRenderer.pageItems(ctx, 1).first().text)
        assertEquals("记事 11", WidgetRenderer.pageItems(ctx, 2).first().text)
    }

    // ------------------------------------------------------------ 渲染

    @Test
    fun `能构建出小组件的 RemoteViews 而不抛异常`() {
        seed(3)
        val views = WidgetRenderer.build(ctx, 1)
        assertNotNull(views)
    }

    @Test
    fun `空列表也能构建 RemoteViews`() {
        val views = WidgetRenderer.build(ctx, 1)
        assertNotNull(views)
    }

    @Test
    fun `能构建单条记事的 RemoteViews`() {
        val n = Repo.add(ctx, "渲染测试")!!
        assertNotNull(WidgetRenderer.buildItem(ctx, n))
    }

    @Test
    fun `已完成项也能渲染`() {
        val n = Repo.add(ctx, "已完成项")!!
        Repo.setDone(ctx, n.id, true)
        assertNotNull(WidgetRenderer.buildItem(ctx, Repo.all(ctx).first()))
    }

    @Test
    fun `能构建分区标题的 RemoteViews`() {
        assertNotNull(WidgetRenderer.buildSection(ctx, ctx.getString(WidgetRenderer.SECTION_TODO), done = false))
        assertNotNull(WidgetRenderer.buildSection(ctx, ctx.getString(WidgetRenderer.SECTION_DONE), done = true))
    }

    // ------------------------------------------------------------ 分区

    @Test
    fun `小组件里未完成排在已完成前面`() {
        Repo.add(ctx, "第一条")   // 会被标完成
        Repo.add(ctx, "第二条")
        Repo.add(ctx, "第三条")

        val first = Repo.all(ctx).first().id
        Repo.setDone(ctx, first, true)

        val ordered = WidgetRenderer.orderedVisible(ctx).map { it.text }

        assertEquals(listOf("第二条", "第三条", "第一条"), ordered)
    }

    @Test
    fun `小组件只给已完成插标题，未完成不插`() {
        Repo.add(ctx, "待办A")
        Repo.add(ctx, "待办B")
        val done = Repo.add(ctx, "已办C")!!
        Repo.setDone(ctx, done.id, true)

        val rows = WidgetRenderer.pageRows(ctx, 1)
        val sections = rows.filterIsInstance<WidgetRow.Section>()

        // 只应有「已完成」一个标题 —— 「未完成」那行被刻意省掉以节省空间
        assertEquals(1, sections.size)
        assertEquals(ctx.getString(WidgetRenderer.SECTION_DONE), sections[0].title)

        val titles = rows.map {
            when (it) {
                is WidgetRow.Section -> "S:${it.title}"
                is WidgetRow.Item -> "I:${it.note.text}"
            }
        }
        assertEquals(
            listOf(
                "I:待办A", "I:待办B",
                "S:${ctx.getString(R.string.section_done)}", "I:已办C"
            ),
            titles
        )
    }

    @Test
    fun `小组件里不会出现未完成标题`() {
        Repo.add(ctx, "待办")
        val d = Repo.add(ctx, "已办")!!
        Repo.setDone(ctx, d.id, true)

        val rows = WidgetRenderer.pageRows(ctx, 1)
        assertTrue(
            "小组件不该有「未完成」标题行",
            rows.filterIsInstance<WidgetRow.Section>()
                .none { it.title == ctx.getString(WidgetRenderer.SECTION_TODO) }
        )
    }

    @Test
    fun `清一色未完成时不插标题`() {
        seed(3)
        val rows = WidgetRenderer.pageRows(ctx, 1)
        assertTrue(
            "只有未完成时不该有分区标题",
            rows.none { it is WidgetRow.Section }
        )
    }

    @Test
    fun `清一色已完成时不插标题`() {
        seed(3)
        Repo.all(ctx).forEach { Repo.setDone(ctx, it.id, true) }

        val rows = WidgetRenderer.pageRows(ctx, 1)
        assertTrue(
            "全部完成时不需要分界（列表开头就是已完成）",
            rows.none { it is WidgetRow.Section }
        )
    }

    @Test
    fun `关掉分组后不插标题且顺序还原`() {
        Repo.add(ctx, "A")
        val b = Repo.add(ctx, "B")!!
        Repo.setDone(ctx, b.id, true)

        Prefs.setGroupDone(ctx, false)

        val rows = WidgetRenderer.pageRows(ctx, 1)
        assertTrue(rows.none { it is WidgetRow.Section })
        assertEquals(listOf("A", "B"), rows.filterIsInstance<WidgetRow.Item>().map { it.note.text })
    }

    @Test
    fun `隐藏已完成项后不会出现「已完成」标题`() {
        Repo.add(ctx, "待办")
        val d = Repo.add(ctx, "已办")!!
        Repo.setDone(ctx, d.id, true)

        Prefs.setWidgetShowDone(ctx, false)

        val rows = WidgetRenderer.pageRows(ctx, 1)
        assertTrue(rows.none { it is WidgetRow.Section })
        assertEquals(1, rows.filterIsInstance<WidgetRow.Item>().size)
    }

    @Test
    fun `翻页到第二页时若已跨分区会重新插入标题`() {
        // 每页 2 条：待办A 待办B | 已办C 已办D
        repeat(2) { Repo.add(ctx, "待办${it + 1}") }
        repeat(2) { Repo.add(ctx, "已办${it + 1}") }
        Repo.all(ctx).filter { it.text.startsWith("已办") }
            .forEach { Repo.setDone(ctx, it.id, true) }

        Prefs.setWidgetPageSize(ctx, 2)
        Prefs.setWidgetPage(ctx, 1, 1) // 第二页

        val rows = WidgetRenderer.pageRows(ctx, 1)
        val sections = rows.filterIsInstance<WidgetRow.Section>()

        assertEquals(1, sections.size)
        assertEquals(ctx.getString(WidgetRenderer.SECTION_DONE), sections[0].title)
    }

    @Test
    fun `每页条数只统计记事，不把标题算进去`() {
        repeat(3) { Repo.add(ctx, "待办${it + 1}") }
        val d = Repo.add(ctx, "已办")!!
        Repo.setDone(ctx, d.id, true)

        Prefs.setWidgetPageSize(ctx, 2)

        // 第一页应当是 2 条记事（外加可能的分区标题），而不是「2 行」
        assertEquals(2, WidgetRenderer.pageItems(ctx, 1).size)
    }

    @Test
    fun `卡片配色能区分完成状态`() {
        val light = Palette.schemeFrom(0xFF0061A4.toInt(), dark = false)
        val dark = Palette.schemeFrom(0xFF0061A4.toInt(), dark = true)

        // 浅色下未完成是纯白，已完成应更暗一些
        assertTrue(
            "浅色下已完成卡片应比未完成卡片暗",
            luminance(light.cardTodo) > luminance(light.cardDone)
        )
        // 深色下未完成卡片应比已完成亮
        assertTrue(
            "深色下未完成卡片应比已完成亮",
            luminance(dark.cardTodo) > luminance(dark.cardDone)
        )
    }

    // ------------------------------------------------------------ 标题与页码

    @Test
    fun `标题显示还有多少项待办`() {
        // 断言从资源取，而不是写死中文 —— 加了 values-en 之后，
        // Robolectric 默认 locale（en-US）会解析到英文，写死中文必挂。
        assertEquals(ctx.getString(R.string.widget_title_todo, 2), WidgetRenderer.titleText(ctx, total = 3, todo = 2))
        assertEquals(ctx.getString(R.string.widget_title_todo, 1), WidgetRenderer.titleText(ctx, total = 5, todo = 1))
    }

    @Test
    fun `全部完成时标题不会说还有0项待办`() {
        val title = WidgetRenderer.titleText(ctx, total = 3, todo = 0)

        // 不该出现「还有 0 项」这种把 0 塞进模板的文案。
        // 只断言「不含数字 0」——与语言无关，中英日都成立。
        assertFalse("不该出现「还有 0 项」", title.contains("0"))
        assertEquals(ctx.getString(R.string.widget_title_all_done), title)
    }

    @Test
    fun `没有记事时标题提示为空`() {
        assertEquals(
            ctx.getString(R.string.widget_title_empty),
            WidgetRenderer.titleText(ctx, total = 0, todo = 0)
        )
    }

    @Test
    fun `标题不再是应用名字`() {
        val title = WidgetRenderer.titleText(ctx, total = 4, todo = 3)
        // 三种语言的 App 名都不该出现在标题里（标题只讲还剩多少待办）
        listOf("忆记事", "Tsubasa").forEach {
            assertFalse("标题里不该出现 App 名字「$it」", title.contains(it))
        }
    }

    @Test
    fun `只有一页时不显示页码`() {
        assertEquals(null, WidgetRenderer.pageText(page = 0, pages = 1))
    }

    @Test
    fun `多页时显示页码`() {
        assertEquals("1/3", WidgetRenderer.pageText(page = 0, pages = 3))
        assertEquals("3/3", WidgetRenderer.pageText(page = 2, pages = 3))
    }

    @Test
    fun `页数不会因为条数为零而变成0`() {
        // 空列表也应是 1 页，否则页码逻辑会算出 0 页
        assertEquals(1, WidgetRenderer.pageCount(0, 5))
    }

    // ------------------------------------------------------------ 莫奈配色

    @Test
    fun `配色能生成且明暗两套都合法`() {
        val seedColor = 0xFF0061A4.toInt()

        val light = Palette.schemeFrom(seedColor, dark = false)
        val dark = Palette.schemeFrom(seedColor, dark = true)

        // 浅色方案：背景应比文字亮
        assertTrue("浅色背景应比文字亮", luminance(light.bg) > luminance(light.onBg))
        // 深色方案：背景应比文字暗
        assertTrue("深色背景应比文字暗", luminance(dark.bg) < luminance(dark.onBg))
    }

    @Test
    fun `换种子色确实会换出一套不同的配色`() {
        val blue = Palette.schemeFrom(0xFF0061A4.toInt(), dark = false)
        val red = Palette.schemeFrom(0xFFB3261E.toInt(), dark = false)

        assertTrue(
            "不同种子色应产生不同强调色",
            Palette.distance(blue.accent, red.accent) > 40
        )
    }

    @Test
    fun `自定义模式下取用户选的颜色作为种子`() {
        Prefs.setWidgetTheme(ctx, "custom")
        Prefs.setWidgetSeed(ctx, 0xFF3F6833.toInt())
        assertEquals(0xFF3F6833.toInt(), Palette.seed(ctx))
    }

    @Test
    fun `浅色背景下文字会用深色以保证可读`() {
        assertEquals(0xFF1A1A1A.toInt(), Palette.contrastOn(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), Palette.contrastOn(0xFF000000.toInt()))
    }

    @Test
    fun `灰色种子也不会让饱和度溢出`() {
        // 纯灰：饱和度为 0，这条路径容易出除零/越界
        val s = Palette.schemeFrom(0xFF808080.toInt(), dark = false)
        assertNotNull(s)
        assertTrue(luminance(s.bg) >= 0.0)
    }

    private fun luminance(c: Int): Double =
        (0.299 * android.graphics.Color.red(c) +
            0.587 * android.graphics.Color.green(c) +
            0.114 * android.graphics.Color.blue(c)) / 255.0
}
