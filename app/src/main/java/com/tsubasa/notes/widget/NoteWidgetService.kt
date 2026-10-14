package com.tsubasa.notes.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.tsubasa.notes.L10n
import com.tsubasa.notes.R
import com.tsubasa.notes.data.Note

/**
 * 集合小组件的适配器。桌面会通过 Binder 跨进程调用这里来取每一行。
 *
 * 本 Service 与 App 同进程（没有单独开 :widget 进程）：数据就是一个小 JSON 文件，
 * 读起来足够快，同进程还能避免两份 SharedPreferences 缓存不一致的问题。
 */
class NoteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        // 套上应用内选定的语言：每一行的分区标题、以及配色都从这里取字符串。
        NoteFactory(L10n.wrap(applicationContext), intent)
}

class NoteFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val widgetId: Int =
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

    private var rows: List<WidgetRow> = emptyList()

    override fun onCreate() {
        load()
    }

    override fun onDataSetChanged() {
        load()
    }

    private fun load() {
        rows = try {
            WidgetRenderer.pageRows(context, widgetId)
        } catch (t: Throwable) {
            emptyList()
        }
    }

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews? {
        val row = rows.getOrNull(position) ?: return null
        return try {
            when (row) {
                is WidgetRow.Item -> WidgetRenderer.buildItem(context, row.note)
                is WidgetRow.Section -> WidgetRenderer.buildSection(context, row.title, row.done)
            }
        } catch (t: Throwable) {
            null
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    /**
     * 两种行类型：记事卡片 和 分区标题。
     *
     * 声明为 2 是为了让桌面知道有两套布局，回收视图时不会把两种混用
     * （否则会出现「标题被当成记事渲染」的错乱）。
     */
    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long {
        val row = rows.getOrNull(position) ?: return position.toLong()
        return when (row) {
            // 记事用内容 id 做稳定标识
            is WidgetRow.Item -> row.note.id.hashCode().toLong()
            // 标题只有两个（未完成/已完成），用固定负数避免和记事 id 撞车
            is WidgetRow.Section -> if (row.done) -2L else -1L
        }
    }

    override fun hasStableIds(): Boolean = true

    override fun onDestroy() {
        rows = emptyList()
    }
}
