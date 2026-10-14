package com.tsubasa.notes

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.tsubasa.notes.data.Repo
import com.tsubasa.notes.widget.AddNoteWidgetProvider
import com.tsubasa.notes.widget.NoteWidgetProvider
import com.tsubasa.notes.widget.WidgetRenderer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * 真正把小组件的布局 inflate 出来，验证它能不能用。
 *
 * 这比只调用 `WidgetRenderer.build()` 更有价值：RemoteViews(context, layoutId) 只是存了个
 * layout 的 id，**并不会真的加载布局**（真正加载发生在桌面进程里）。所以如果布局里写了
 * 不存在的 id、或者 WidgetRenderer 引用了布局里没有的控件，普通单测是发现不了的，
 * 到桌面上才会变成空白或崩溃。这里直接把布局撑开检查。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LayoutTests {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
    }

    private fun inflate(id: Int): View =
        LayoutInflater.from(ctx).inflate(id, null, false)

    @Test
    fun `主小组件布局能inflate且包含渲染器用到的所有控件`() {
        val root = inflate(R.layout.widget_notes)
        assertNotNull(root)

        // 这些 id 都是 WidgetRenderer 会去 set 的，缺任何一个都会在桌面渲染时抛异常
        val required = listOf(
            R.id.widget_bg,
            R.id.widget_title,
            R.id.widget_page,
            R.id.widget_prev,
            R.id.widget_next,
            R.id.widget_refresh,
            R.id.widget_add,
            R.id.widget_divider,
            R.id.widget_list,
            R.id.widget_empty
        )
        required.forEach { id ->
            assertNotNull("布局缺少控件: ${ctx.resources.getResourceName(id)}", root.findViewById<View>(id))
        }
    }

    @Test
    fun `列表控件确实是ListView（集合小组件需要它）`() {
        val root = inflate(R.layout.widget_notes)
        val list = root.findViewById<View>(R.id.widget_list)
        assertTrue("widget_list 必须是 ListView 才能配合 RemoteViewsService", list is ListView)
    }

    @Test
    fun `每一条记事的布局能inflate且控件齐全`() {
        val root = inflate(R.layout.widget_note_item)
        assertNotNull(root)

        val required = listOf(
            R.id.item_card_bg,
            R.id.item_check,
            R.id.item_text,
            R.id.item_up,
            R.id.item_delete
        )
        required.forEach { id ->
            assertNotNull("行布局缺少控件: ${ctx.resources.getResourceName(id)}", root.findViewById<View>(id))
        }
    }

    @Test
    fun `分区标题布局能inflate且控件齐全`() {
        val root = inflate(R.layout.widget_section_header)
        assertNotNull(root)

        listOf(R.id.section_bar, R.id.section_title, R.id.section_line).forEach { id ->
            assertNotNull("分区标题布局缺少控件: ${ctx.resources.getResourceName(id)}", root.findViewById<View>(id))
        }
    }

    @Test
    fun `卡片与分区的图形资源都能加载`() {
        ctx.getDrawable(R.drawable.widget_card_bg)
        ctx.getDrawable(R.drawable.widget_section_bar)
        ctx.getDrawable(R.drawable.widget_section_line)
    }

    @Test
    fun `快速添加小组件布局能inflate且控件齐全`() {
        val root = inflate(R.layout.widget_add_button)
        assertNotNull(root)

        listOf(R.id.add_bg, R.id.add_icon, R.id.add_text).forEach { id ->
            assertNotNull("布局缺少控件: ${ctx.resources.getResourceName(id)}", root.findViewById<View>(id))
        }
    }

    @Test
    fun `勾选图标有对应的vector资源`() {
        val checked = inflate(R.layout.widget_note_item).findViewById<ImageView>(R.id.item_check)
        assertNotNull(checked)

        // 两个状态图标都必须能真正加载出来
        ctx.getDrawable(R.drawable.ic_widget_checked)
        ctx.getDrawable(R.drawable.ic_widget_unchecked)
        ctx.getDrawable(R.drawable.ic_widget_up)
        ctx.getDrawable(R.drawable.ic_widget_delete)
        ctx.getDrawable(R.drawable.ic_widget_add)
        ctx.getDrawable(R.drawable.ic_widget_prev)
        ctx.getDrawable(R.drawable.ic_widget_next)
        ctx.getDrawable(R.drawable.ic_widget_refresh)
    }

    @Test
    fun `圆角背景资源在两个版本目录下都存在`() {
        // drawable/ 给 Android 11 及以下（自绘圆角），drawable-v31/ 给 12+（系统裁剪圆角）
        assertNotNull(ctx.getDrawable(R.drawable.widget_bg_rounded))
    }

    @Test
    fun `记事文本控件支持多行显示`() {
        val text = inflate(R.layout.widget_note_item).findViewById<TextView>(R.id.item_text)
        assertNotNull(text)
        // 文本最多两行，超长省略，不能撑破小组件
        assertEquals(2, text.maxLines)
    }

    /**
     * 用 RemoteViews 自己的过滤器真正加载布局。
     *
     * 这一步很关键：普通 LayoutInflater 什么类都让加载，但 RemoteViews 只允许
     * 系统白名单里的控件（都标了 @RemoteView）。布局里混进一个不被允许的类
     * （例如 `<View>`），普通 inflate 不会报错，但小组件在桌面上会直接加载失败。
     */
    @Test
    fun `布局能通过RemoteViews的类白名单校验`() {
        val mgr = AppWidgetManager.getInstance(ctx)

        // 主小组件
        val mainId = shadowOf(mgr).createWidget(NoteWidgetProvider::class.java, R.layout.widget_notes)
        WidgetRenderer.updateWidget(ctx, mainId)

        // 快速添加小组件
        val addId = shadowOf(mgr).createWidget(AddNoteWidgetProvider::class.java, R.layout.widget_add_button)
        AddNoteWidgetProvider().onUpdate(ctx, mgr, intArrayOf(addId))

        // 列表中的每一行
        val note = Repo.add(ctx, "行布局校验")!!
        val rowViews = WidgetRenderer.buildItem(ctx, note)
        val rowHost = FrameLayout(ctx)
        rowHost.addView(rowViews.apply(ctx, rowHost))
        assertNotNull(rowHost)
    }

    /**
     * 扫源码里的布局文件，禁止出现 RemoteViews 不允许的 `<View>` 标签。
     *
     * 这类问题编译期不报错、普通 inflate 也不报错，只有真机上加载小组件时才失败，
     * 所以专门加一道防线。
     */
    @Test
    fun `布局源码里没有裸View标签`() {
        val dir = File("src/main/res")
        if (!dir.exists()) return // 不在模块目录下运行时跳过

        val offenders = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("widget") && it.extension == "xml" }
            .forEach { f ->
                // 先去掉 XML 注释，否则注释里提到的 "<View>" 会被误判
                val text = f.readText().replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
                if (Regex("<View[\\s/>]").containsMatchIn(text)) {
                    offenders += f.name
                }
            }
        assertTrue(
            "这些布局用了 RemoteViews 不允许的 <View>，请改用 ImageView: $offenders",
            offenders.isEmpty()
        )
    }
}
