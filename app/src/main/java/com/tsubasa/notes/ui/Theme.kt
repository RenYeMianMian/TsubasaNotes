package com.tsubasa.notes.ui

import android.app.Activity
import android.os.Build
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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
        SchemeSource.CUSTOM -> {
            val p = Palette.schemeFrom(appSeed, dark)
            schemeFromSeed(appSeed, dark, p.bg)
        }

        // 动态配色要求 API 31，这里显式判断一次，而不是靠 schemeSource 的判断
        // 「传递」到编译期 —— 否则 lint 报 NewApi，将来改坏判断就会在低版本崩。
        SchemeSource.DYNAMIC ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicScheme(context, dark)
            } else {
                val seed = Palette.seed(context)
                schemeFromSeed(seed, dark, Palette.schemeFrom(seed, dark).bg)
            }

        SchemeSource.FALLBACK -> {
            val seed = Palette.seed(context)
            val p = Palette.schemeFrom(seed, dark)
            schemeFromSeed(seed, dark, p.bg)
        }
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
 * 系统莫奈动态配色。
 *
 * 单独抽出来是为了挂 [RequiresApi]：`dynamicLightColorScheme` 是 API 31 才有的，
 * 而 minSdk 是 26。以前直接在 `when` 分支里调用，lint 报 NewApi —— 逻辑上
 * 走不到（[schemeSource] 已经按版本判断过），但编译器/lint 看不出来，
 * 一旦以后有人改动了判断条件就会在低版本崩掉。
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicScheme(context: android.content.Context, dark: Boolean): ColorScheme =
    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

/**
 * 把种子色展开成一套完整的 Material 3 ColorScheme。
 *
 * 以前这里只填了八项（primary / background / surface / surfaceVariant …），
 * 其余槽位全都落到 [lightColorScheme] / [darkColorScheme] 的默认值上 ——
 * 也就是**默认的紫色基线**。后果是：用户把 App 配色改成绿色之后，
 * 卡片底色、选中态、FilterChip、对话框、分割线仍然是紫的，
 * 只有文字和背景跟着变。这正是「改了颜色但界面没整体变」的来源。
 *
 * 现在按 M3 的角色表逐个从种子色取色调（tone）：
 *  - 强调色 primary = tone 40（深色 80），容器 = tone 90（深色 30）；
 *  - 中性色 surface / outline 用低彩度，避免大面积染色发脏；
 *  - tertiary 用 +60° 色相，M3 惯例。
 *
 * @param seed 种子色（用户选的，或系统强调色，或默认紫）
 * @param base 背景色。沿用 [Palette.Scheme.bg]，这样壁纸底色的观感和以前一致，
 *   不会因为换了推导方式突然变一档亮度。
 */
internal fun schemeFromSeed(@ColorInt seed: Int, dark: Boolean, @ColorInt base: Int): ColorScheme {
    val accent = Palette.tonal(seed, if (dark) 80 else 40)
    val onAccent = Palette.tonal(seed, if (dark) 20 else 100)
    val accentContainer = Palette.tonal(seed, if (dark) 30 else 90)
    val onAccentContainer = Palette.tonal(seed, if (dark) 90 else 10)

    val secondary = Palette.tonal(seed, if (dark) 80 else 40, chroma = 0.55f)
    val onSecondary = Palette.tonal(seed, if (dark) 20 else 100, chroma = 0.55f)
    val secondaryContainer = Palette.tonal(seed, if (dark) 30 else 90, chroma = 0.55f)
    val onSecondaryContainer = Palette.tonal(seed, if (dark) 90 else 10, chroma = 0.55f)

    val tertiary = Palette.tonal(seed, if (dark) 80 else 40, chroma = 0.7f, hueShift = 60f)
    val onTertiary = Palette.tonal(seed, if (dark) 20 else 100, chroma = 0.7f, hueShift = 60f)
    val tertiaryContainer = Palette.tonal(seed, if (dark) 30 else 90, chroma = 0.7f, hueShift = 60f)
    val onTertiaryContainer = Palette.tonal(seed, if (dark) 90 else 10, chroma = 0.7f, hueShift = 60f)

    // 中性色：彩度压到 0.12~0.2，M3 里 surface 系列本来就几乎是灰的，
    // 只带一点点种子色的味道。
    val onSurface = Palette.tonal(seed, if (dark) 90 else 10, chroma = 0.12f)
    val onSurfaceVariant = Palette.tonal(seed, if (dark) 80 else 30, chroma = 0.16f)
    val surfaceVariant = Palette.tonal(seed, if (dark) 30 else 90, chroma = 0.16f)
    val outline = Palette.tonal(seed, if (dark) 60 else 50, chroma = 0.14f)
    val outlineVariant = Palette.tonal(seed, if (dark) 30 else 80, chroma = 0.14f)

    val surface = base
    val surfaceDim = Palette.tonal(seed, if (dark) 6 else 87, chroma = 0.14f)
    val surfaceBright = Palette.tonal(seed, if (dark) 24 else 98, chroma = 0.14f)
    val containerLowest = Palette.tonal(seed, if (dark) 4 else 100, chroma = 0.14f)
    val containerLow = Palette.tonal(seed, if (dark) 10 else 96, chroma = 0.14f)
    val container = Palette.tonal(seed, if (dark) 12 else 94, chroma = 0.14f)
    val containerHigh = Palette.tonal(seed, if (dark) 17 else 92, chroma = 0.14f)
    val containerHighest = Palette.tonal(seed, if (dark) 22 else 90, chroma = 0.14f)

    val inverseSurface = Palette.tonal(seed, if (dark) 90 else 20, chroma = 0.14f)
    val inverseOnSurface = Palette.tonal(seed, if (dark) 20 else 95, chroma = 0.14f)
    val inversePrimary = Palette.tonal(seed, if (dark) 40 else 80)

    // 错误色不跟随种子 —— 红色是语义色，跟着种子变绿就没人看得懂了。
    val error = if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E)
    val onError = if (dark) Color(0xFF601410) else Color(0xFFFFFFFF)
    val errorContainer = if (dark) Color(0xFF8C1D18) else Color(0xFFF9DEDC)
    val onErrorContainer = if (dark) Color(0xFFF9DEDC) else Color(0xFF410E0B)

    return ColorScheme(
        primary = Color(accent),
        onPrimary = Color(onAccent),
        primaryContainer = Color(accentContainer),
        onPrimaryContainer = Color(onAccentContainer),
        inversePrimary = Color(inversePrimary),
        secondary = Color(secondary),
        onSecondary = Color(onSecondary),
        secondaryContainer = Color(secondaryContainer),
        onSecondaryContainer = Color(onSecondaryContainer),
        tertiary = Color(tertiary),
        onTertiary = Color(onTertiary),
        tertiaryContainer = Color(tertiaryContainer),
        onTertiaryContainer = Color(onTertiaryContainer),
        background = Color(surface),
        onBackground = Color(onSurface),
        surface = Color(surface),
        onSurface = Color(onSurface),
        surfaceVariant = Color(surfaceVariant),
        onSurfaceVariant = Color(onSurfaceVariant),
        surfaceTint = Color(accent),
        inverseSurface = Color(inverseSurface),
        inverseOnSurface = Color(inverseOnSurface),
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = Color(outline),
        outlineVariant = Color(outlineVariant),
        scrim = Color(0xFF000000),
        surfaceBright = Color(surfaceBright),
        surfaceDim = Color(surfaceDim),
        surfaceContainer = Color(container),
        surfaceContainerHigh = Color(containerHigh),
        surfaceContainerHighest = Color(containerHighest),
        surfaceContainerLow = Color(containerLow),
        surfaceContainerLowest = Color(containerLowest)
    )
}
