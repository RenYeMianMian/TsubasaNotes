package com.tsubasa.notes.data

import android.content.Context
import android.content.SharedPreferences

/** 所有偏好设置集中在这里，App 与小组件共用同一份 SharedPreferences。 */
object Prefs {
    private const val NAME = "tsubasa_notes"

    /**
     * 统一用 commit()（同步落盘）而不是 apply()。
     *
     * 小组件和 App 是两个进程，各自持有自己的 SharedPreferences 缓存；apply() 是异步写，
     * 紧接着被另一个进程读时可能读到旧值（例如在设置里改了「每页条数」但小组件没变）。
     * 这里写入量极小，同步写完全无感，换来的是跨进程行为确定。
     */
    private fun edit(context: Context): SharedPreferences.Editor =
        sp(context).edit()

    /** App 主列表一页显示多少条 */
    const val APP_PAGE_SIZE = "app_page_size"
    /** 小组件一页显示多少条 */
    const val WIDGET_PAGE_SIZE = "widget_page_size"
    /** 小组件是否显示已完成项 */
    const val WIDGET_SHOW_DONE = "widget_show_done"
    /** 小组件配色：system = 跟随系统莫奈，custom = 用户自选 */
    const val WIDGET_THEME = "widget_theme"
    /** 自定义配色的种子色 */
    const val WIDGET_SEED = "widget_seed"
    /** App 深色模式：system / light / dark */
    const val APP_THEME = "app_theme"
    /**
     * App 配色来源：system = 跟随系统莫奈，custom = 用户自选。
     *
     * 和 [APP_THEME] **分开存**是有意的：明暗（浅色/深色）和「用哪个颜色」
     * 是两件互不相关的事 —— 用户可能想要「深色 + 自定义绿色」。
     * 如果把 custom 塞进 APP_THEME 当第四个取值，就只能二选一了。
     */
    const val APP_COLOR = "app_color"
    /** App 自定义配色的种子色（只在 APP_COLOR == custom 时生效） */
    const val APP_SEED = "app_seed"
    /** 是否把已完成项分到单独一段并沉底（App 与小组件共用） */
    const val GROUP_DONE = "group_done"
    /** 小组件背景不透明度，0..100（100 = 完全不透明） */
    const val WIDGET_BG_OPACITY = "widget_bg_opacity"
    /** 自定义小组件背景图的绝对路径（空 = 不用） */
    const val WIDGET_BG_IMAGE = "widget_bg_image"
    /** 自定义 App 背景图的绝对路径（空 = 不用） */
    const val APP_BG_IMAGE = "app_bg_image"
    /** 背景模糊强度，0..100（0 = 不模糊）。整屏统一模糊，不分栏 */
    const val BACKDROP_BLUR = "backdrop_blur"
    /** 界面语言：system / zh / en / ja */
    const val LANGUAGE = "app_language"
    /** 小组件当前页码，按 widgetId 分开存 */
    const val WIDGET_PAGE_PREFIX = "widget_page_"

    const val DEFAULT_APP_PAGE_SIZE = 20
    const val DEFAULT_WIDGET_PAGE_SIZE = 5
    const val DEFAULT_WIDGET_BG_OPACITY = 100
    const val DEFAULT_BACKDROP_BLUR = 55

    fun sp(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun appPageSize(context: Context): Int =
        sp(context).getInt(APP_PAGE_SIZE, DEFAULT_APP_PAGE_SIZE).coerceIn(5, 100)

    fun setAppPageSize(context: Context, v: Int) {
        edit(context).putInt(APP_PAGE_SIZE, v.coerceIn(5, 100)).commit()
    }

    fun widgetPageSize(context: Context): Int =
        sp(context).getInt(WIDGET_PAGE_SIZE, DEFAULT_WIDGET_PAGE_SIZE).coerceIn(1, 20)

    fun setWidgetPageSize(context: Context, v: Int) {
        edit(context).putInt(WIDGET_PAGE_SIZE, v.coerceIn(1, 20)).commit()
    }

    fun widgetShowDone(context: Context): Boolean =
        sp(context).getBoolean(WIDGET_SHOW_DONE, true)

    fun setWidgetShowDone(context: Context, v: Boolean) {
        edit(context).putBoolean(WIDGET_SHOW_DONE, v).commit()
    }

    fun widgetTheme(context: Context): String =
        sp(context).getString(WIDGET_THEME, "system") ?: "system"

    fun setWidgetTheme(context: Context, v: String) {
        edit(context).putString(WIDGET_THEME, v).commit()
    }

    fun widgetSeed(context: Context): Int =
        sp(context).getInt(WIDGET_SEED, 0xFF6750A4.toInt())

    fun setWidgetSeed(context: Context, v: Int) {
        edit(context).putInt(WIDGET_SEED, v).commit()
    }

    fun appTheme(context: Context): String =
        sp(context).getString(APP_THEME, "system") ?: "system"

    fun setAppTheme(context: Context, v: String) {
        edit(context).putString(APP_THEME, v).commit()
    }

    /** App 配色来源：system = 跟随系统莫奈，custom = 用 [appSeed]。 */
    fun appColor(context: Context): String =
        sp(context).getString(APP_COLOR, "system") ?: "system"

    fun setAppColor(context: Context, v: String) {
        edit(context).putString(APP_COLOR, v).commit()
    }

    /**
     * App 自定义配色的种子色。
     *
     * 与小组件的 [WIDGET_SEED] **刻意分开存**：用户的要求是「App 的自定义颜色
     * 只影响 App，小组件另有自己的配色」，所以两边互不干扰 —— 改 App 配色
     * 不会顺手改掉桌面上小组件的颜色。
     */
    fun appSeed(context: Context): Int =
        sp(context).getInt(APP_SEED, 0xFF6750A4.toInt())

    fun setAppSeed(context: Context, v: Int) {
        edit(context).putInt(APP_SEED, v).commit()
    }

    /** 是否把已完成项分到单独一段并沉底。默认开启。 */
    fun groupDone(context: Context): Boolean =
        sp(context).getBoolean(GROUP_DONE, true)

    fun setGroupDone(context: Context, v: Boolean) {
        edit(context).putBoolean(GROUP_DONE, v).commit()
    }

    fun widgetPage(context: Context, widgetId: Int): Int =
        sp(context).getInt(WIDGET_PAGE_PREFIX + widgetId, 0)

    fun setWidgetPage(context: Context, widgetId: Int, page: Int) {
        edit(context).putInt(WIDGET_PAGE_PREFIX + widgetId, page).commit()
    }

    fun clearWidgetPage(context: Context, widgetId: Int) {
        edit(context).remove(WIDGET_PAGE_PREFIX + widgetId).commit()
    }

    // ------------------------------------------------------------ 外观 / 背景

    /**
     * 小组件背景不透明度，0..100。
     *
     * 存成 0..100 的整数而不是 float，是因为 SharedPreferences 读 float 时
     * 精度和默认值的处理更容易出岔子；整数好读好写也好夹范围。
     */
    fun widgetBgOpacity(context: Context): Int =
        sp(context).getInt(WIDGET_BG_OPACITY, DEFAULT_WIDGET_BG_OPACITY).coerceIn(0, 100)

    fun setWidgetBgOpacity(context: Context, v: Int) {
        edit(context).putInt(WIDGET_BG_OPACITY, v.coerceIn(0, 100)).commit()
    }

    fun widgetBgImage(context: Context): String =
        sp(context).getString(WIDGET_BG_IMAGE, "") ?: ""

    fun setWidgetBgImage(context: Context, path: String) {
        edit(context).putString(WIDGET_BG_IMAGE, path).commit()
    }

    fun appBgImage(context: Context): String =
        sp(context).getString(APP_BG_IMAGE, "") ?: ""

    fun setAppBgImage(context: Context, path: String) {
        edit(context).putString(APP_BG_IMAGE, path).commit()
    }

    /**
     * 背景模糊强度，0..100。
     *
     * 0 表示**完全不模糊**，此时连模糊图层都不会创建 —— 壁纸原样透出来，
     * 一点额外开销都没有。性能吃紧的设备把它拉到 0 即可。
     */
    fun backdropBlur(context: Context): Int =
        sp(context).getInt(BACKDROP_BLUR, DEFAULT_BACKDROP_BLUR).coerceIn(0, 100)

    fun setBackdropBlur(context: Context, v: Int) {
        edit(context).putInt(BACKDROP_BLUR, v.coerceIn(0, 100)).commit()
    }

    // ------------------------------------------------------------ 语言

    /**
     * 界面语言：system / zh / en / ja。
     *
     * 这里只存「用户选了什么」，真正把 Context 换成对应 Locale 的动作
     * 在 [com.tsubasa.notes.L10n.wrap] 里做 —— 因为 App 和小组件是
     * 两条完全不同的入口，各自需要在各自取字符串的地方套一层。
     */
    fun appLang(context: Context): String =
        sp(context).getString(LANGUAGE, "system") ?: "system"

    fun setAppLang(context: Context, v: String) {
        edit(context).putString(LANGUAGE, v).commit()
    }
}
