package com.tsubasa.notes.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.annotation.ColorInt
import com.tsubasa.notes.data.Prefs
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 小组件配色。
 *
 * 桌面小组件跑在 RemoteViews 里，拿不到 Compose 的 MaterialTheme，所以这里自己做一套
 * 「莫奈取色」：
 *  - 系统模式（Android 12+）：从系统调色板 [android.R.color.system_accent1_500] 取种子色，
 *    所以小组件会真正跟随壁纸变色；
 *  - 自定义模式：用用户选的种子色；
 *  - 然后由种子色推导出一整套容器/文字/强调色，保证在任何机型、任何 Android 版本上
 *    都有一致的观感（Android 12 以下没有系统调色板，则回落到默认紫色种子）。
 */
object Palette {

    @ColorInt private const val FALLBACK_SEED = 0xFF6750A4.toInt()

    data class Scheme(
        @ColorInt val bg: Int,
        @ColorInt val onBg: Int,
        @ColorInt val accent: Int,
        @ColorInt val onAccent: Int,
        @ColorInt val muted: Int,
        @ColorInt val divider: Int,
        /** 未完成卡片的底色：贴近小组件背景，但略亮/略暗一档 */
        @ColorInt val cardTodo: Int,
        /** 已完成卡片的底色：和未完成明显区分，表示「压下去了」 */
        @ColorInt val cardDone: Int,
        val dark: Boolean
    )

    fun isDark(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    /** 取种子色：系统莫奈 或 用户自定义。 */
    @ColorInt
    fun seed(context: Context): Int {
        if (Prefs.widgetTheme(context) == "custom") return Prefs.widgetSeed(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return try {
                context.getColor(android.R.color.system_accent1_500)
            } catch (t: Throwable) {
                FALLBACK_SEED
            }
        }
        return FALLBACK_SEED
    }

    fun scheme(context: Context): Scheme = schemeFrom(seed(context), isDark(context))

    fun schemeFrom(@ColorInt seedColor: Int, dark: Boolean): Scheme {
        val hsl = toHsl(seedColor)
        val h = hsl[0]
        val s = hsl[1]

        return if (dark) {
            val bgColor = fromHsl(h, s * 0.30f, 0.11f)
            Scheme(
                bg = bgColor,
                onBg = fromHsl(h, min(s * 0.22f, 0.25f), 0.93f),
                accent = fromHsl(h, clamp(min(max(s, 0.50f), 0.90f), 0.30f, 0.90f), 0.76f),
                onAccent = fromHsl(h, s * 0.5f, 0.14f),
                muted = fromHsl(h, min(s * 0.18f, 0.22f), 0.62f),
                divider = fromHsl(h, s * 0.20f, 0.24f),
                // 深色下：未完成卡片比背景亮一点（浮起来），已完成再暗回去
                cardTodo = fromHsl(h, s * 0.26f, 0.17f),
                cardDone = fromHsl(h, s * 0.16f, 0.135f),
                dark = true
            )
        } else {
            val bgColor = fromHsl(h, s * 0.34f, 0.975f)
            Scheme(
                bg = bgColor,
                onBg = fromHsl(h, min(s * 0.35f, 0.40f), 0.16f),
                accent = fromHsl(h, clamp(min(max(s, 0.45f), 0.85f), 0.30f, 0.85f), 0.42f),
                onAccent = Color.WHITE,
                muted = fromHsl(h, min(s * 0.22f, 0.28f), 0.48f),
                divider = fromHsl(h, s * 0.26f, 0.88f),
                // 浅色下：未完成卡片纯白（干净、突出），已完成偏灰（退到后面）
                cardTodo = Color.WHITE,
                cardDone = fromHsl(h, s * 0.20f, 0.925f),
                dark = false
            )
        }
    }

    // ------------------------------------------------------------ 颜色工具

    private fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))

    /** @return [h(0..360), s(0..1), l(0..1)] */
    private fun toHsl(@ColorInt color: Int): FloatArray {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        val maxV = max(r, max(g, b))
        val minV = min(r, min(g, b))
        val l = (maxV + minV) / 2f
        val d = maxV - minV
        var h = 0f
        val s: Float
        if (d < 1e-6f) {
            s = 0f
        } else {
            s = if (l > 0.5f) d / (2f - maxV - minV) else d / (maxV + minV)
            h = when (maxV) {
                r -> ((g - b) / d + if (g < b) 6f else 0f)
                g -> ((b - r) / d + 2f)
                else -> ((r - g) / d + 4f)
            } * 60f
        }
        return floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    @ColorInt
    private fun fromHsl(h: Float, s: Float, l: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f / 360f
        val ss = s.coerceIn(0f, 1f)
        val ll = l.coerceIn(0f, 1f)
        if (ss < 1e-6f) {
            val v = (ll * 255f + 0.5f).toInt()
            return Color.rgb(v, v, v)
        }
        val q = if (ll < 0.5f) ll * (1f + ss) else ll + ss - ll * ss
        val p = 2f * ll - q
        val r = hue2rgb(p, q, hh + 1f / 3f)
        val g = hue2rgb(p, q, hh)
        val b = hue2rgb(p, q, hh - 1f / 3f)
        return Color.rgb(
            (r * 255f + 0.5f).toInt().coerceIn(0, 255),
            (g * 255f + 0.5f).toInt().coerceIn(0, 255),
            (b * 255f + 0.5f).toInt().coerceIn(0, 255)
        )
    }

    private fun hue2rgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }

    /** 两个颜色按比例混合，用于做半透明强调色等。 */
    @ColorInt
    fun blend(@ColorInt a: Int, @ColorInt b: Int, ratio: Float): Int {
        val t = ratio.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
            (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
            (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
        )
    }

    /** 供设置界面预览用：一组预设种子色。 */
    val presets: List<Int> = listOf(
        0xFF6750A4.toInt(), // 紫
        0xFF0061A4.toInt(), // 蓝
        0xFF00696D.toInt(), // 青
        0xFF3F6833.toInt(), // 绿
        0xFF8C4A60.toInt(), // 玫红
        0xFF8B5000.toInt(), // 琥珀
        0xFFB3261E.toInt(), // 红
        0xFF5D5F79.toInt()  // 蓝灰
    )

    /** 判断颜色是否偏亮（用于决定叠在上面的文字用黑还是白）。 */
    fun isLight(@ColorInt color: Int): Boolean {
        val y = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0
        return y > 0.6
    }

    fun contrastOn(@ColorInt bg: Int): Int = if (isLight(bg)) 0xFF1A1A1A.toInt() else Color.WHITE

    /** 距离，用于校验颜色确实变化了。 */
    fun distance(@ColorInt a: Int, @ColorInt b: Int): Int =
        abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))
}
