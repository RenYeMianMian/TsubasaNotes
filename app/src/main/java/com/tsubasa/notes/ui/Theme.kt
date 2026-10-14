package com.tsubasa.notes.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tsubasa.notes.widget.Palette

/**
 * 应用主题。
 *
 * 三个参数全部来自 [NotesViewModel.UiState]，**不再直接读 Prefs**。
 * 这一点很关键：Prefs 不是 Compose State，读它不会建立订阅关系 ——
 * 在设置里改了明暗或颜色，主题不会重组，界面看起来就像「选了没反应」。
 * 由调用方把 state 里的值传进来，改动才会真正触发重组。
 *
 * 配色来源按优先级：
 * 1. [appColor] == "custom" —— **无论系统版本**都用我们自己推导的配色。
 *    这一条必须排在动态配色前面，否则 Android 12+ 上会一直走系统莫奈配色，
 *    用户选的颜色被静默忽略。
 * 2. 否则 Android 12+ 用系统莫奈动态配色（dynamicLightColorScheme / dynamicDarkColorScheme）。
 * 3. 更低版本回落到根据「小组件种子色」推导出的配色，这样 App 和桌面小组件是同一套颜色。
 *
 * 明暗与配色来源是两件独立的事，所以「深色 + 自定义绿」这种组合成立 ——
 * 这也是把 appColor 和 appTheme 分成两个偏好键的原因。
 */
@Composable
fun TsubasaTheme(
    appTheme: String,
    appColor: String,
    appSeed: Int,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dark = when (appTheme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val scheme = when (schemeSource(appColor, Build.VERSION.SDK_INT)) {
        SchemeSource.CUSTOM -> schemeFromPalette(Palette.schemeFrom(appSeed, dark), dark)
        SchemeSource.DYNAMIC ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        SchemeSource.FALLBACK -> schemeFromPalette(Palette.schemeFrom(Palette.seed(context), dark), dark)
    }

    // 状态栏图标跟着主题明暗走。
    //
    // 注意这里**不再**设置 window.statusBarColor —— 因为 Activity 开了 edge-to-edge，
    // 状态栏是透明的，露出的是我们自己的背景。手动设成透明色反而会和
    // enableEdgeToEdge() 的配置打架（在某些 ROM 上会退化成黑色）。
    // 只需要告诉系统「图标该用深色还是浅色」就够了。
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * 配色的来源。抽成枚举是为了能脱离 Compose 单测 ——
 * 「自定义必须优先于动态配色」这条优先级是本次修 bug 的核心，
 * 而它以前写在一个 `when` 表达式的顺序里，测试根本够不着。
 */
enum class SchemeSource { CUSTOM, DYNAMIC, FALLBACK }

/**
 * 决定用哪一套配色。
 *
 * @param sdkInt 传进来而不是直接读 [Build.VERSION.SDK_INT]，这样测试能覆盖
 *   「Android 12+ 上选了自定义颜色」这个正是出过问题的组合。
 */
fun schemeSource(appColor: String, sdkInt: Int): SchemeSource = when {
    appColor == "custom" -> SchemeSource.CUSTOM
    sdkInt >= Build.VERSION_CODES.S -> SchemeSource.DYNAMIC
    else -> SchemeSource.FALLBACK
}

/**
 * 把 [Palette] 推导出来的配色翻译成 Compose 的 ColorScheme。
 *
 * 只填这八项是有意的：界面上真正用到的 `MaterialTheme.colorScheme.*` 全部落在
 * 这一组里，其余槽位交给 Material3 的默认值 —— 填了也不会被用到，反而增加
 * 以后跟 Palette 字段对不上的风险。
 */
private fun schemeFromPalette(p: Palette.Scheme, dark: Boolean): ColorScheme {
    val primary = Color(p.accent)
    val onPrimary = Color(p.onAccent)
    val background = Color(p.bg)
    val onBackground = Color(p.onBg)
    val divider = Color(p.divider)
    val muted = Color(p.muted)
    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            background = background,
            onBackground = onBackground,
            surface = background,
            onSurface = onBackground,
            surfaceVariant = divider,
            onSurfaceVariant = muted
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            background = background,
            onBackground = onBackground,
            surface = background,
            onSurface = onBackground,
            surfaceVariant = divider,
            onSurfaceVariant = muted
        )
    }
}
