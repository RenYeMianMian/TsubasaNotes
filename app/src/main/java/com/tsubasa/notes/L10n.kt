package com.tsubasa.notes

import android.content.Context
import android.content.res.Configuration
import com.tsubasa.notes.data.Prefs
import java.util.Locale

/**
 * 应用内语言切换。
 *
 * ---- 为什么不用 AppCompatDelegate.setApplicationLocales() ----
 *
 * 那是官方推荐的做法，但它依赖 AppCompatActivity 在 API < 33 上的兼容实现。
 * 本项目 MainActivity 继承的是 ComponentActivity（Compose 的标准宿主），
 * 拿不到那套回退逻辑，setApplicationLocales 在 Android 12 及以下会静默失效。
 *
 * 所以这里自己做：把语言存进 Prefs，然后在每个「取字符串的入口」
 * （Activity 的 attachBaseContext、小组件各回调）用 [wrap] 把 Context
 * 换成带目标 Locale 的版本。createConfigurationContext 是框架 API，
 * 从 API 17 就有，minSdk 26 完全够用。
 *
 * ---- 为什么不加 android:localeConfig ----
 *
 * 加了之后 Android 13+ 的系统「按应用设置语言」界面会冒出来，
 * 它会和这里的 Prefs 抢同一个设置：用户在系统里改了，下次进 App
 * 又被 attachBaseContext 按 Prefs 覆盖回去，看起来像「改了没用」。
 * 与其做两套互相打架的入口，不如只留 App 内这一套。
 */
object L10n {

    /** 跟随系统语言 */
    const val SYSTEM = "system"
    const val ZH = "zh"
    const val EN = "en"
    const val JA = "ja"

    /** 设置界面里按这个顺序列出语言选项。 */
    val TAGS = listOf(SYSTEM, ZH, EN, JA)

    /**
     * 语言选项的显示名。
     *
     * 三个具体语言一律用**它自己的语言**写（中文 / English / 日本語）——
     * 这是各系统的通行做法，用户哪怕把界面语言选成了看不懂的一种，
     * 也还能认出自己要选哪个。
     */
    fun label(context: Context, tag: String): String = when (tag) {
        ZH -> context.getString(R.string.lang_zh)
        EN -> context.getString(R.string.lang_en)
        JA -> context.getString(R.string.lang_ja)
        else -> context.getString(R.string.settings_lang_system)
    }

    /**
     * 把 [base] 换成带目标 Locale 的 Context。
     *
     * 选「跟随系统」时原样返回，不做任何包装 —— 这样系统语言一变，
     * 资源解析自然就跟着变，不需要我们插手。
     */
    fun wrap(base: Context): Context {
        val tag = Prefs.appLang(base)
        if (tag == SYSTEM) return base
        val locale = Locale.forLanguageTag(tag)
        // 拿系统当前 configuration 复制一份再改 locale，
        // 这样屏幕密度、深色模式、字号等其它配置不会被我们改丢。
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
