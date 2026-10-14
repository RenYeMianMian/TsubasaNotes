package com.tsubasa.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.tsubasa.notes.L10n

/** 「快速添加」小组件：一个按钮，点一下直接打开 App 并聚焦输入框。 */
class AddNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // 套上应用内选定的语言：按钮文字是布局里 bake 的 @string/widget_add_note，
        // 那部分由桌面进程 inflate（跟随系统语言，我们管不了），
        // 但这里主动取字符串的地方（以及配色）要走我们的语言。
        val context = L10n.wrap(context)
        appWidgetIds.forEach { id ->
            val scheme = Palette.scheme(context)
            val views = android.widget.RemoteViews(
                context.packageName,
                com.tsubasa.notes.R.layout.widget_add_button
            )
            views.setInt(com.tsubasa.notes.R.id.add_bg, "setColorFilter", scheme.accent)
            views.setInt(com.tsubasa.notes.R.id.add_icon, "setColorFilter", scheme.onAccent)
            views.setTextColor(com.tsubasa.notes.R.id.add_text, scheme.onAccent)
            // 布局里 bake 的 android:text 由桌面进程按系统语言解析，
            // 这里显式覆盖一次，按钮文字才会跟随应用内选定的语言。
            views.setTextViewText(
                com.tsubasa.notes.R.id.add_text,
                context.getString(com.tsubasa.notes.R.string.widget_add_note)
            )

            val i = Intent(context, com.tsubasa.notes.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(com.tsubasa.notes.MainActivity.EXTRA_NEW_NOTE, true)
            }
            views.setOnClickPendingIntent(
                com.tsubasa.notes.R.id.add_root,
                PendingIntent.getActivity(
                    context, 400 + id, i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}
