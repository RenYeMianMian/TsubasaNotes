package com.tsubasa.notes.ui.background

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * App 背景（壁纸）。
 *
 * 两种情况：
 * 1. 用户选了自定义背景图 -> 显示图片；
 * 2. 没选图（或图片文件丢了，比如被清理、换机恢复）-> 用主题色纯色。
 *
 * 刻意**不做渐变**：之前那个渐变是为了配合「液态玻璃」的模糊底才加的，
 * 玻璃已经移除了，留着一个没来由的渐变只会显得奇怪。
 *
 * 背景由 MainActivity 铺在**整屏最底层**，所以它会一直延伸到状态栏和导航栏底下，
 * 看起来就是「一整块壁纸」。模糊同样在 MainActivity 整屏做一次 ——
 * 这样状态栏、顶栏、中间列表、底栏是**同一层模糊**，不会出现某段没糊的情况。
 *
 * @param path 背景图路径。必须由调用方从可观察状态传进来
 *             （直接读 Prefs 不会触发重组，换了图界面不刷新）。
 */
@Composable
fun rememberAppBackdrop(
    path: String,
    baseColor: Color
): @Composable (Modifier) -> Unit {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current

    // 目标宽度取屏幕宽度，避免把整张原图读进内存
    val targetWidth = with(density) { config.screenWidthDp.dp.toPx() }.toInt()

    val bitmap: Bitmap? = remember(path, targetWidth) {
        if (ImageStore.exists(path)) ImageStore.loadScaled(path, targetWidth) else null
    }

    return { modifier: Modifier ->
        Box(modifier.fillMaxSize().background(baseColor)) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * 0..100 的模糊百分比 -> 模糊半径（dp）。
 *
 * 上限取 28dp 是权衡的结果：半径越大，离屏渲染开销增长越快，
 * 而视觉收益在 20dp 之后就基本饱和了。
 *
 * 抽成纯函数是为了能直接测边界 —— 尤其是 **0 必须真的等于 0**，
 * 那才谈得上「彻底关掉模糊、零开销」。
 */
fun blurRadiusDp(percent: Int): Float =
    percent.coerceIn(0, 100) / 100f * 28f
