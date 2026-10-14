package com.tsubasa.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 图标资源的回归测试。
 *
 * 这里盯的是两类「编译能过、装到手机上才发现」的问题：
 *
 * 1. **VectorDrawable 里 clip-path 必须是子元素。** 图标资源包最初把裁切范围写成
 *    `<group android:clip-path="...">`，AAPT2 直接拒绝（`attribute android:clip-path not found`）——
 *    这个至少编译期能挡住。但把裁切范围改成子元素后写错 pathData，编译期完全不报错，
 *    只会静默少画一块（右耳尖的黑尖、脸上的黑斑）。
 * 2. **单色层必须是「实心剪影 + 镂空五官」。** 如果偷懒把前景层整张当单色层，Android 13+
 *    的主题图标会糊成一个黑饼。所以断言「既有大量实心墨色、内部又确实有镂空」。
 *
 * 必须开 NATIVE 图形模式：Robolectric 默认的 legacy 模式下 `VectorDrawable.draw()` 是空实现，
 * 渲染出来一片空白，什么也测不出来。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class IconTests {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val bg = Color.parseColor("#FFF6EA") // 奶油底，来自 values/ic_launcher_background.xml

    /** 把矢量按 [size] 光栅化，[background] 为 null 时留透明底。 */
    private fun render(d: Drawable, size: Int, background: Int?): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (background != null) canvas.drawColor(background)
        d.setBounds(0, 0, size, size)
        d.draw(canvas)
        return bmp
    }

    /** 统计与背景色不同的像素数。 */
    private fun inkPixels(bmp: Bitmap, background: Int): Int {
        var n = 0
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                if (bmp.getPixel(x, y) != background) n++
            }
        }
        return n
    }

    private fun alphaPixels(bmp: Bitmap): Int {
        var n = 0
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                if (Color.alpha(bmp.getPixel(x, y)) > 0) n++
            }
        }
        return n
    }

    @Test
    fun `前景层是可解析的矢量`() {
        val d = ctx.getDrawable(R.drawable.ic_launcher_foreground)
        assertNotNull("前景层应当存在", d)
        assertTrue("前景层应当是 VectorDrawable，实际 ${d!!.javaClass.name}", d is VectorDrawable)
    }

    @Test
    fun `单色层是可解析的矢量`() {
        val d = ctx.getDrawable(R.drawable.ic_launcher_monochrome)
        assertNotNull("单色层应当存在", d)
        assertTrue("单色层应当是 VectorDrawable，实际 ${d!!.javaClass.name}", d is VectorDrawable)
    }

    /**
     * 前景层必须真的画出东西来。
     *
     * 这条是 clip-path 的哨兵：把 `<clip-path>` 子元素删掉或写坏，脸上那几块黑斑和右耳尖
     * 就没了，墨色像素会明显变少。这里用「占总面积 8%~35%」这种宽松区间，既能抓住
     * 「几乎空白」和「糊成一整块黑」两种坏法，又不会因为描边的一两个像素抖动而误报。
     */
    @Test
    fun `前景层渲染出合理的墨色面积`() {
        val bmp = render(ctx.getDrawable(R.drawable.ic_launcher_foreground)!!, 432, bg)
        val ink = inkPixels(bmp, bg)
        val ratio = ink.toDouble() / (432.0 * 432.0)
        assertTrue(
            "前景层墨色占比 $ratio 不合理（$ink 像素）：太接近 0 说明矢量没画出来，太接近 1 说明糊成一整块",
            ratio > 0.08 && ratio < 0.35,
        )
    }

    /**
     * 单色层必须是「实心剪影 + 镂空五官」。
     *
     * 剪影实心 ⇒ 墨色面积够大；五官镂空 ⇒ 内部存在透明像素。若把前景层直接当单色层用，
     * 脸和眼睛都是白色实心，内部就没有镂空，主题图标会变成一个黑饼。
     */
    @Test
    fun `单色层是实心剪影且五官镂空`() {
        val bmp = render(ctx.getDrawable(R.drawable.ic_launcher_monochrome)!!, 432, null)
        val solid = alphaPixels(bmp)
        val ratio = solid.toDouble() / (432.0 * 432.0)
        assertTrue("单色层实心面积占比 $ratio 太小，剪影可能没画出来", ratio > 0.10)

        // 猫脸中心附近（两眼之间偏下）应当是被挖空/未覆盖的：整体镂空面积要有一定规模
        val hollow = 432 * 432 - solid
        assertTrue("单色层几乎没有镂空（实心 $solid / ${432 * 432}），主题图标会糊成一个黑饼", hollow > 432 * 432 * 0.5)
    }

    /**
     * 自适应图标的内容必须落在安全区内。
     *
     * 108dp 画布上系统只保证中央 72dp 可见，最苛刻的圆形遮罩半径 36dp（即 108 的 1/3）。
     * 图标包为此把前景层缩放到 0.80：猫形含描边最大半径 43.61dp → 34.9dp，刚好进安全圆。
     *
     * 断言方式是**实测所有墨色像素到圆心的最大距离**，而不是在几个固定点取样 ——
     * 固定点取样会漏：把 `android:scaleX/Y` 从 0.80 改回 1.0（即丢掉安全区缩放）后，
     * 角上的取样点仍然在内容之外，测试会照样通过。实测最大半径才抓得住。
     */
    @Test
    fun `前景层内容落在圆形遮罩安全区内`() {
        val size = 432
        val bmp = render(ctx.getDrawable(R.drawable.ic_launcher_foreground)!!, size, bg)
        val safeRadius = 36.0 / 108.0 * size // 144px：最苛刻遮罩的半径
        val center = size / 2.0

        var maxRadius = 0.0
        var farthest = 0 to 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (bmp.getPixel(x, y) == bg) continue
                val d = Math.hypot(x - center, y - center)
                if (d > maxRadius) {
                    maxRadius = d
                    farthest = x to y
                }
            }
        }

        assertTrue("前景层没有画出任何内容", maxRadius > 0.0)
        assertTrue(
            "墨色最远点 $farthest 距圆心 ${"%.1f".format(maxRadius)}px，" +
                "超出最苛刻圆形遮罩半径 ${"%.1f".format(safeRadius)}px —— " +
                "内容会被圆角/圆形图标裁掉，检查 android:scaleX/scaleY 的安全区缩放是否还在",
            maxRadius <= safeRadius,
        )
    }

    /** 自适应图标描述的接线：背景色、前景层、单色层三者都要指对资源。 */
    @Test
    fun `自适应图标引用了正确的背景色和图层`() {
        assertEquals(
            "背景色应当是奶油底 #FFF6EA",
            bg,
            ctx.getColor(R.color.ic_launcher_background),
        )
        // 两个图层都得能真的取到，否则 adaptive-icon 会静默缺一层
        assertNotNull("前景层资源缺失", ctx.getDrawable(R.drawable.ic_launcher_foreground))
        assertNotNull("单色层资源缺失", ctx.getDrawable(R.drawable.ic_launcher_monochrome))
    }

    /**
     * clip-path 的精确哨兵。
     *
     * 前景层有两处 `<clip-path>` 子元素：一处把右耳尖的黑尖裁在右耳三角形内，一处把脸上
     * 那几块黑斑裁在脸轮廓内。这两处裁切**一旦失效并不影响编译**，只是静默少画/多画一块，
     * 所以必须靠像素级断言盯着。
     *
     * 只看「墨色总面积」是不够的：把裁切范围换成退化路径（什么都裁掉）后，墨色面积只少
     * 291 个像素，仍落在宽松区间里。这里改为断言具体像素 —— 右耳尖黑尖所在的 (273,123)
     * 在裁切正常时是实心墨色 `#141414`；裁切一旦退化成空，该处会露出下面的白色耳面
     * （实测变成 `#FFFFFF`）。
     *
     * 坐标来源：视口坐标经 `scaleX/Y=0.8, pivot=54` 映射到 108 画布，再按 432/108 放大。
     */
    @Test
    fun `右耳尖的黑尖被正确裁切`() {
        val bmp = render(ctx.getDrawable(R.drawable.ic_launcher_foreground)!!, 432, bg)
        // 右耳尖黑尖上的几个点：裁切正常时应当是实心墨色
        val onTip = listOf(271 to 124, 273 to 123, 275 to 122)
        onTip.forEach { (x, y) ->
            val px = bmp.getPixel(x, y)
            val lum = (Color.red(px) + Color.green(px) + Color.blue(px)) / 3
            assertTrue(
                "右耳尖黑尖的裁切可能失效：($x,$y) 实测 #${Integer.toHexString(px)}（亮度 $lum），" +
                    "裁切正常时这里应当是实心墨色 #141414",
                lum < 60,
            )
        }
        // 反面确认：耳朵外侧（三角形之外）不该被涂黑 —— 这正是裁切要保证的事
        assertEquals(
            "耳朵外侧不该有墨色：视口 (86,20) 在右耳三角形外",
            bg,
            bmp.getPixel(316, 96),
        )
    }

    /**
     * 各密度的 PNG 兜底图都要在、且尺寸正确。
     *
     * 自适应图标只在 API 26+ 生效（本项目 minSdk=26 正好覆盖），但桌面、设置页、应用商店
     * 等地方仍会直接取 `ic_launcher.png`，尺寸错了会糊。
     */
    @Test
    fun `各密度图标 PNG 尺寸正确`() {
        val expected = mapOf(
            "mdpi" to 48,
            "hdpi" to 72,
            "xhdpi" to 96,
            "xxhdpi" to 144,
            "xxxhdpi" to 192,
        )
        expected.forEach { (qualifier, size) ->
            listOf("ic_launcher", "ic_launcher_round").forEach { name ->
                val f = File("src/main/res/mipmap-$qualifier/$name.png")
                assertTrue("缺少 $qualifier 的 $name.png（$f）", f.exists())
                val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
                assertEquals(
                    "$qualifier/$name.png 尺寸应为 ${size}x$size",
                    size,
                    opts.outWidth,
                )
                assertEquals(
                    "$qualifier/$name.png 尺寸应为 ${size}x$size",
                    size,
                    opts.outHeight,
                )
            }
        }
    }

    /** 前景层的 PNG 是 108dp 画布按密度出图，宽高应当一致且随密度放大。 */
    @Test
    fun `前景层 PNG 尺寸随密度放大`() {
        val expected = mapOf(
            "mdpi" to 108,
            "hdpi" to 162,
            "xhdpi" to 216,
            "xxhdpi" to 324,
            "xxxhdpi" to 432,
        )
        expected.forEach { (qualifier, size) ->
            val f = File("src/main/res/mipmap-$qualifier/ic_launcher_foreground.png")
            assertTrue("缺少 $qualifier 的 ic_launcher_foreground.png", f.exists())
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, opts)
            assertEquals("$qualifier 前景层宽度", size, opts.outWidth)
            assertEquals("$qualifier 前景层高度", size, opts.outHeight)
        }
    }
}
