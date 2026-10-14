package com.tsubasa.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.StringRes
import com.tsubasa.notes.MainActivity
import com.tsubasa.notes.R
import com.tsubasa.notes.data.Grouping
import com.tsubasa.notes.data.Note
import com.tsubasa.notes.data.Prefs
import com.tsubasa.notes.data.Repo
import kotlin.math.ceil

/**
 * 小组件列表里的一行：要么是分区标题，要么是一条记事。
 *
 * 与 App 侧保持同一套模型，这样两边的分区规则（什么时候插标题、插在哪）
 * 能用同一份逻辑思考，不会出现「App 有标题、小组件没有」这种不一致。
 */
sealed interface WidgetRow {
    data class Section(val title: String, val done: Boolean) : WidgetRow
    data class Item(val note: Note) : WidgetRow
}

/**
 * 把记事渲染成桌面小组件的 RemoteViews。
 *
 * 用 RemoteViews 而不是 Jetpack Glance：可交互的列表型小组件正好命中
 * RemoteViewsService + RemoteViewsFactory + PendingIntent 模板这套平台原生机制，
 * Glance 本质是它的封装，继承了同样的限制却多一层抽象。
 *
 * ---- 交互边界（改这个类之前先读）----
 *
 * 小组件里**只能做单击**，这是平台限制，不是实现偷懒：
 *
 * 1. **不能拖拽排序。** RemoteViews 只有 setOnClick* 一类点击 API，
 *    触摸/手势/拖拽方法一个都没有（Glance 同样没有）。
 *    根因是小组件跑在桌面进程里（AppWidgetHostView 负责渲染），
 *    而拖拽需要连续的触摸事件流，跨进程无法实时同步。
 *    所以排序用 [OP_UP]「↑」按钮做单步上移，精细排序回 App 里拖。
 *
 * 2. **不要加长按菜单。** 长按桌面小组件是系统用来「移动 / 重排小组件」的手势，
 *    在这里注册长按会和系统行为打架（要么收不到回调，要么把用户拖拽小组件的
 *    操作搅乱）。所以本类里只有点击类的 fill-in intent。
 */
object WidgetRenderer {

    const val ACTION_ITEM = "com.tsubasa.notes.ACTION_ITEM"
    const val ACTION_PAGE_PREV = "com.tsubasa.notes.ACTION_PAGE_PREV"
    const val ACTION_PAGE_NEXT = "com.tsubasa.notes.ACTION_PAGE_NEXT"
    const val ACTION_ADD = "com.tsubasa.notes.ACTION_ADD"
    const val ACTION_REFRESH = "com.tsubasa.notes.ACTION_REFRESH"

    const val EXTRA_NOTE_ID = "note_id"
    const val EXTRA_OP = "op"
    const val OP_TOGGLE = "toggle"
    const val OP_UP = "up"
    const val OP_DOWN = "down"
    const val OP_DELETE = "delete"

    /**
     * 分区标题文案。存**资源 id** 而不是成品字符串 —— 小组件渲染时才用
     * [Context.getString] 取当前语言，这样切换语言后不需要改动这里的常量。
     *
     * 注意这里只能是 `val` 不能是 `const val`：R.string.* 是生成的静态字段，
     * 不是 Kotlin 编译期常量，`const val` 会报「initializer should be a constant value」。
     */
    @StringRes
    val SECTION_TODO = R.string.section_todo

    @StringRes
    val SECTION_DONE = R.string.section_done

    /** 小组件要显示的记事，已按「未完成在前」排好（分组开关关掉时保持原顺序）。 */
    fun orderedVisible(context: Context): List<Note> {
        val list = Repo.visible(context, Prefs.widgetShowDone(context))
        return Grouping.order(list, Prefs.groupDone(context))
    }

    /** 根据页码算出当前页要显示的记事（不含分区标题）。 */
    fun pageItems(context: Context, widgetId: Int): List<Note> {
        val all = orderedVisible(context)
        val size = Prefs.widgetPageSize(context).coerceAtLeast(1)
        val pages = pageCount(all.size, size)
        val page = Prefs.widgetPage(context, widgetId).coerceIn(0, pages - 1)
        val from = page * size
        val to = minOf(from + size, all.size)
        return if (from >= to) emptyList() else all.subList(from, to)
    }

    /**
     * 当前页要渲染的行（含分区标题）。
     *
     * **只有「已完成」才插标题行，「未完成」不插** ——
     * 未完成永远是列表开头那一段，用户一眼就知道；而「已完成」需要一个明确的分界
     * 告诉人「这以下都做完了」。省掉「未完成」那一行能给记事腾出空间。
     *
     * 清一色未完成时自然也不会插任何标题。
     */
    fun pageRows(context: Context, widgetId: Int): List<WidgetRow> {
        val pageNotes = pageItems(context, widgetId)
        if (!Prefs.groupDone(context)) return pageNotes.map { WidgetRow.Item(it) }

        val all = orderedVisible(context)
        val todo = all.count { !it.done }
        val done = all.size - todo
        // 只有「两种状态同时存在」时，分界才有意义。
        // 清一色未完成 -> 不需要分界；清一色已完成 -> 同样不需要
        // （整页都是已完成，再顶一个「已完成」标题等于没说，还白占一行）。
        if (todo == 0 || done == 0) return pageNotes.map { WidgetRow.Item(it) }

        val rows = mutableListOf<WidgetRow>()
        var lastDone: Boolean? = null
        for (n in pageNotes) {
            // 只在「从未完成跨到已完成」的那一处插标题
            if (n.done && lastDone != true) {
                rows += WidgetRow.Section(context.getString(SECTION_DONE), done = true)
            }
            rows += WidgetRow.Item(n)
            lastDone = n.done
        }
        return rows
    }

    fun pageCount(total: Int, pageSize: Int): Int =
        if (total <= 0) 1 else ceil(total.toDouble() / pageSize.toDouble()).toInt()

    /**
     * 标题栏文字：显示还剩多少项待办，而不是 App 名字。
     *
     * 抽成纯函数是为了能直接测 —— RemoteViews 的内容没法在单测里读回来
     * （Robolectric 不提供对它的内省），把「算什么」和「怎么塞进 RemoteViews」分开，
     * 前者就变得可验证了。
     */
    fun titleText(context: Context, total: Int, todo: Int): String = when {
        total == 0 -> context.getString(R.string.widget_title_empty)
        // 全部完成时说「还有 0 项待办」很别扭，换成一句收尾的话
        todo == 0 -> context.getString(R.string.widget_title_all_done)
        else -> context.getString(R.string.widget_title_todo, todo)
    }

    /** 只有一页时不显示「1/1」——它不提供任何信息，纯粹占地方。 */
    fun pageText(page: Int, pages: Int): String? =
        if (pages > 1) "${page + 1}/$pages" else null

    /**
     * 把「背景不透明度（0..100 整数）」换算成 View 的 alpha（0f..1f）。
     *
     * 抽成纯函数是为了能直接测边界 —— RemoteViews 设进去的值在单测里读不回来。
     *
     * 注意下限不是 0：全透明的小组件在桌面上等于「看不见」，用户会以为组件坏了。
     * 所以即使滑到 0，也保留一点点底（[MIN_ALPHA]），让它始终可辨认、可点。
     */
    fun bgAlpha(opacity: Int): Float {
        val o = opacity.coerceIn(0, 100)
        val raw = o / 100f
        return MIN_ALPHA + raw * (1f - MIN_ALPHA)
    }

    /** 背景最低可见度：保证小组件不会完全「消失」。 */
    const val MIN_ALPHA = 0.12f

    /** 背景图解码上限（长边像素）。小组件没那么大，压到这个尺寸既够清晰又能过 Binder。 */
    private const val MAX_WIDGET_BG_PX = 1080

    private const val TAG = "Tsubasa"

    /** 自定义背景图是否可用（路径非空且文件真的存在）。 */
    fun hasCustomBg(context: Context): Boolean =
        com.tsubasa.notes.ui.background.ImageStore.exists(Prefs.widgetBgImage(context))

    fun currentPage(context: Context, widgetId: Int): Int {
        val total = orderedVisible(context).size
        val size = Prefs.widgetPageSize(context).coerceAtLeast(1)
        return Prefs.widgetPage(context, widgetId).coerceIn(0, pageCount(total, size) - 1)
    }

    fun build(context: Context, widgetId: Int): RemoteViews {
        val scheme = Palette.scheme(context)
        val views = RemoteViews(context.packageName, R.layout.widget_notes)

        // ---- 莫奈配色
        //
        // 背景是一个铺满的 ImageView，显示白色圆角矩形，再用 setColorFilter 上色。
        // 这样比 setInt("setBackgroundColor") 好在：圆角在 Android 12 以下也能保留
        // （纯色背景会覆盖掉 shape 的圆角），而且不依赖 API 31+ 的 setBackgroundTintList。
        views.setInt(R.id.widget_bg, "setColorFilter", scheme.bg)
        // 背景不透明度：直接设整个背景层的 alpha。
        // 用 setFloat(..., "setAlpha", ...) 而不是给颜色乘 alpha ——
        // 后者在自定义背景图（Bitmap 自带 alpha）上不起作用。
        views.setFloat(R.id.widget_bg, "setAlpha", bgAlpha(Prefs.widgetBgOpacity(context)))

        // ---- 自定义背景图（可选）
        applyCustomBackground(context, views)

        views.setTextColor(R.id.widget_title, scheme.onBg)
        views.setTextColor(R.id.widget_page, scheme.muted)
        views.setTextColor(R.id.widget_empty, scheme.muted)
        views.setInt(R.id.widget_add, "setColorFilter", scheme.accent)
        views.setInt(R.id.widget_prev, "setColorFilter", scheme.accent)
        views.setInt(R.id.widget_next, "setColorFilter", scheme.accent)
        views.setInt(R.id.widget_refresh, "setColorFilter", scheme.accent)
        views.setInt(R.id.widget_divider, "setBackgroundColor", scheme.divider)

        // ---- 列表（集合小组件）
        val svc = Intent(context, NoteWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // data 必须唯一，否则不同小组件实例会共用同一个 factory
            data = Uri.parse("tsubasa://widget/$widgetId")
        }
        views.setRemoteAdapter(R.id.widget_list, svc)

        // 列表项点击模板：每条记事的具体信息由 fill-in intent 补充进来。
        //
        // 必须用 FLAG_MUTABLE：Android 12+ 起 PendingIntent 默认不可变，而 fill-in 机制
        // 要求系统在发送时把 fill-in intent 合并进去 —— 用 FLAG_IMMUTABLE 的话，
        // 点每一行都会**静默无反应**（不报错，只是不触发）。
        // 这里的 intent 是显式的（指向自己的 receiver），所以不受 Android 14
        // 「可变 PendingIntent 不得使用隐式 intent」的限制。
        //
        // 请求码按 widgetId 区分，并带上 EXTRA_APPWIDGET_ID，
        // 这样点某一行只会刷新它所在的那个小组件实例。
        val itemTemplate = Intent(context, NoteWidgetProvider::class.java).apply {
            action = ACTION_ITEM
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        views.setPendingIntentTemplate(
            R.id.widget_list,
            PendingIntent.getBroadcast(
                context, 100 + widgetId, itemTemplate,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        )

        // ---- 按钮
        views.setOnClickPendingIntent(R.id.widget_title, activityIntent(context, widgetId))
        views.setOnClickPendingIntent(R.id.widget_add, activityIntent(context, widgetId, newNote = true))
        views.setOnClickPendingIntent(R.id.widget_prev, broadcast(context, widgetId, ACTION_PAGE_PREV, 200))
        views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, widgetId, ACTION_PAGE_NEXT, 201))
        views.setOnClickPendingIntent(R.id.widget_refresh, broadcast(context, widgetId, ACTION_REFRESH, 202))

        // ---- 页码 / 空状态
        val all = orderedVisible(context)
        val total = all.size
        val todo = all.count { !it.done }
        val pages = pageCount(total, Prefs.widgetPageSize(context))
        val page = currentPage(context, widgetId)

        // 标题显示「还有 N 项待办」——比显示 App 名字有用得多
        views.setTextViewText(R.id.widget_title, titleText(context, total, todo))

        // 只有一页时不显示页码。用 GONE（而不是 INVISIBLE）
        // 连它占的宽度也一并让给标题。
        val pageLabel = pageText(page, pages)
        if (pageLabel != null) {
            views.setViewVisibility(R.id.widget_page, View.VISIBLE)
            views.setTextViewText(R.id.widget_page, pageLabel)
        } else {
            views.setViewVisibility(R.id.widget_page, View.GONE)
        }

        val multi = if (pages > 1) View.VISIBLE else View.GONE
        views.setViewVisibility(R.id.widget_prev, multi)
        views.setViewVisibility(R.id.widget_next, multi)
        views.setViewVisibility(R.id.widget_empty, if (total == 0) View.VISIBLE else View.GONE)

        // 空状态文字也要在这里显式设一次。
        //
        // 布局里虽然 bake 了 @string/widget_empty，但那份文案是在**桌面进程**里
        // 解析的，用的是系统语言 —— 用户在 App 里选了别的语言，它不会跟着变。
        // 我们主动 setTextViewText 的这份才走应用内语言。
        views.setTextViewText(R.id.widget_empty, context.getString(R.string.widget_empty))

        // 图标按钮没有可见文字，读屏软件念的是 contentDescription。
        // 布局里 bake 的那份同样走系统语言，所以这里一并覆盖。
        views.setContentDescription(R.id.widget_prev, context.getString(R.string.widget_prev_page))
        views.setContentDescription(R.id.widget_next, context.getString(R.string.widget_next_page))
        views.setContentDescription(R.id.widget_refresh, context.getString(R.string.widget_refresh))
        views.setContentDescription(R.id.widget_add, context.getString(R.string.widget_add_note))

        return views
    }

    /** 渲染单独一行。由 [NoteWidgetService] 调用。 */
    fun buildItem(context: Context, note: Note): RemoteViews {
        val scheme = Palette.scheme(context)
        val views = RemoteViews(context.packageName, R.layout.widget_note_item)

        // 卡片底色区分完成状态（未完成白/亮，已完成灰/暗）
        views.setInt(
            R.id.item_card_bg, "setColorFilter",
            if (note.done) scheme.cardDone else scheme.cardTodo
        )

        views.setTextViewText(R.id.item_text, note.text)
        views.setTextColor(R.id.item_text, if (note.done) scheme.muted else scheme.onBg)
        views.setInt(R.id.item_check, "setColorFilter", if (note.done) scheme.accent else scheme.muted)
        views.setImageViewResource(
            R.id.item_check,
            if (note.done) R.drawable.ic_widget_checked else R.drawable.ic_widget_unchecked
        )
        views.setInt(R.id.item_up, "setColorFilter", scheme.muted)
        views.setInt(R.id.item_delete, "setColorFilter", scheme.muted)

        // 已完成项不再需要「上移」，隐藏掉更干净
        views.setViewVisibility(R.id.item_up, if (note.done) View.GONE else View.VISIBLE)

        // 逐条点击：fill-in intent 会与 setPendingIntentTemplate 的模板合并
        views.setOnClickFillInIntent(R.id.item_check, fillIn(note.id, OP_TOGGLE))
        views.setOnClickFillInIntent(R.id.item_up, fillIn(note.id, OP_UP))
        views.setOnClickFillInIntent(R.id.item_delete, fillIn(note.id, OP_DELETE))
        // 点文字区域也切换完成状态，比点小图标好按
        views.setOnClickFillInIntent(R.id.item_text, fillIn(note.id, OP_TOGGLE))

        // 同上：读屏文案也要跟着应用内语言走
        views.setContentDescription(R.id.item_check, context.getString(R.string.widget_toggle_done))
        views.setContentDescription(R.id.item_up, context.getString(R.string.widget_move_up))
        views.setContentDescription(R.id.item_delete, context.getString(R.string.widget_delete))

        return views
    }

    /** 渲染「未完成 / 已完成」分区标题行。 */
    fun buildSection(context: Context, title: String, done: Boolean): RemoteViews {
        val scheme = Palette.scheme(context)
        val views = RemoteViews(context.packageName, R.layout.widget_section_header)

        val color = if (done) scheme.muted else scheme.accent
        views.setTextViewText(R.id.section_title, title)
        views.setTextColor(R.id.section_title, color)
        views.setInt(R.id.section_bar, "setColorFilter", color)
        // 细线用更淡的同色，视觉上是一条「分隔」而不是「强调」
        views.setInt(R.id.section_line, "setColorFilter", Palette.blend(scheme.bg, color, 0.28f))

        // 标题行本身不可点，避免误触
        return views
    }

    private fun fillIn(noteId: String, op: String): Intent =
        Intent().putExtra(EXTRA_NOTE_ID, noteId).putExtra(EXTRA_OP, op)

    /**
     * 套用用户自定义的小组件背景图。
     *
     * 几个必须注意的点：
     * - 图片要**先解码成 Bitmap 再传**。RemoteViews 跨进程传输，
     *   `setImageViewUri` 要求桌面进程有权读那个 Uri —— 应用私有目录的文件它读不到。
     *   直接塞 Bitmap 走 Binder，就没有权限问题。
     * - 传之前要压到合适大小：Binder 事务有 1MB 左右的上限，
     *   一张原图很容易超，超了会抛 TransactionTooLargeException。
     * - 文件可能被清掉，所以路径存了也要再确认存在，否则静默退回纯色背景。
     */
    private fun applyCustomBackground(context: Context, views: RemoteViews) {
        val path = Prefs.widgetBgImage(context)
        if (!com.tsubasa.notes.ui.background.ImageStore.exists(path)) {
            views.setViewVisibility(R.id.widget_bg_image, View.GONE)
            return
        }

        val bitmap = try {
            decodeWidgetBitmap(path)
        } catch (t: Throwable) {
            Log.w(TAG, "解码小组件背景图失败", t)
            null
        }

        if (bitmap == null) {
            views.setViewVisibility(R.id.widget_bg_image, View.GONE)
            return
        }

        views.setViewVisibility(R.id.widget_bg_image, View.VISIBLE)
        views.setImageViewBitmap(R.id.widget_bg_image, bitmap)
        views.setFloat(
            R.id.widget_bg_image, "setAlpha",
            bgAlpha(Prefs.widgetBgOpacity(context))
        )
    }

    /**
     * 解码并压缩背景图。
     *
     * 小组件一般也就几百 dp 宽，按 [MAX_WIDGET_BG_PX] 采样足够了；
     * 再压成 JPEG，确保 Binder 能传过去。
     */
    private fun decodeWidgetBitmap(path: String): android.graphics.Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        while (longEdge / (sample * 2) >= MAX_WIDGET_BG_PX) sample *= 2

        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return android.graphics.BitmapFactory.decodeFile(path, opts)
    }

    private fun broadcast(context: Context, widgetId: Int, action: String, code: Int): PendingIntent {
        val i = Intent(context, NoteWidgetProvider::class.java).apply {
            this.action = action
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        }
        return PendingIntent.getBroadcast(
            context, code + widgetId, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun activityIntent(context: Context, widgetId: Int, newNote: Boolean = false): PendingIntent {
        val i = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NEW_NOTE, newNote)
        }
        return PendingIntent.getActivity(
            context, if (newNote) 300 + widgetId else 301 + widgetId, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ------------------------------------------------------------ 刷新

    fun updateWidget(context: Context, widgetId: Int) {
        val mgr = AppWidgetManager.getInstance(context)
        mgr.updateAppWidget(widgetId, build(context, widgetId))
        mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
    }

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, NoteWidgetProvider::class.java))
        if (ids == null || ids.isEmpty()) return
        ids.forEach { id -> mgr.updateAppWidget(id, build(context, id)) }
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }
}
