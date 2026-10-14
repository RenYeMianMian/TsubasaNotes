package com.tsubasa.notes.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.tsubasa.notes.L10n
import com.tsubasa.notes.R
import com.tsubasa.notes.data.Prefs
import com.tsubasa.notes.data.Repo
import com.tsubasa.notes.data.WidgetBridge

/**
 * 小组件的广播入口。所有交互（勾选、上移、删除、翻页、刷新）都汇聚到这里，
 * 改完数据后统一刷新 RemoteViews。
 *
 * 数据改动本身会通过 [WidgetBridge] 广播触发刷新，所以这里不再重复刷新一次。
 */
class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            appWidgetManager.updateAppWidget(id, WidgetRenderer.build(context, id))
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        appWidgetManager.updateAppWidget(appWidgetId, WidgetRenderer.build(context, appWidgetId))
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { Prefs.clearWidgetPage(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 所有回调（onUpdate / onDeleted / onAppWidgetOptionsChanged）都是
        // 由 AppWidgetProvider.onReceive 用同一个 context 分发下去的，
        // 所以在这里套一次语言，下面几条路径就全都跟着走了。
        val localized = L10n.wrap(context)
        super.onReceive(localized, intent)

        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )

        when (intent.action) {
            WidgetBridge.ACTION_REFRESH -> {
                if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    WidgetRenderer.updateAll(localized)
                } else {
                    WidgetRenderer.updateWidget(localized, widgetId)
                }
            }

            WidgetRenderer.ACTION_PAGE_PREV -> turnPage(localized, widgetId, -1)
            WidgetRenderer.ACTION_PAGE_NEXT -> turnPage(localized, widgetId, +1)

            WidgetRenderer.ACTION_ITEM -> {
                val noteId = intent.getStringExtra(WidgetRenderer.EXTRA_NOTE_ID)
                val op = intent.getStringExtra(WidgetRenderer.EXTRA_OP)
                if (noteId == null || op == null) return
                try {
                    when (op) {
                        WidgetRenderer.OP_TOGGLE -> Repo.toggleDone(localized, noteId)
                        WidgetRenderer.OP_UP -> Repo.moveBy(localized, noteId, -1)
                        WidgetRenderer.OP_DOWN -> Repo.moveBy(localized, noteId, +1)
                        WidgetRenderer.OP_DELETE -> Repo.delete(localized, noteId)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "处理小组件操作失败: $op", t)
                }
                // Repo 写完数据已经广播刷新；这里只需要保证当前这个实例的列表内容更新
                val mgr = AppWidgetManager.getInstance(localized)
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    mgr.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
                } else {
                    WidgetRenderer.updateAll(localized)
                }
            }
        }
    }

    private fun turnPage(context: Context, widgetId: Int, delta: Int) {
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val total = WidgetRenderer.orderedVisible(context).size
        val size = Prefs.widgetPageSize(context).coerceAtLeast(1)
        val pages = WidgetRenderer.pageCount(total, size)
        var page = WidgetRenderer.currentPage(context, widgetId) + delta
        // 循环翻页：最后一页再点「下一页」回到第一页
        if (page >= pages) page = 0
        if (page < 0) page = pages - 1
        Prefs.setWidgetPage(context, widgetId, page)
        WidgetRenderer.updateWidget(context, widgetId)
    }

    private companion object {
        const val TAG = "Tsubasa"
    }
}
