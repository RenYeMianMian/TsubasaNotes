package com.tsubasa.notes

import com.tsubasa.notes.ui.background.blurRadiusDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 整屏模糊的换算与边界。
 *
 * 现在的模糊是**整屏做一次**（见 MainActivity），不再是每个栏各自糊一遍 ——
 * 后者会导致中间那段没被糊到，正是之前「顶面糊了、中间和底面没糊」的原因。
 *
 * 这里测的是纯换算，布局层面的验证在 `BackdropBlurLayoutTests`。
 */
class BlurTests {

    @Test
    fun `0% 对应完全不模糊`() {
        // 这条最重要：0% 必须真的是 0，才谈得上「关掉模糊、零开销」
        assertEquals(0f, blurRadiusDp(0), 0.0001f)
    }

    @Test
    fun `100% 对应设定的半径上限`() {
        assertEquals(28f, blurRadiusDp(100), 0.0001f)
    }

    @Test
    fun `半径随百分比单调递增`() {
        var last = -1f
        for (p in 0..100 step 5) {
            val r = blurRadiusDp(p)
            assertTrue("百分比 $p 处没有递增", r > last)
            last = r
        }
    }

    @Test
    fun `越界的百分比会被夹住而不是溢出`() {
        // 越界的模糊半径会让 RenderEffect 抛异常（半径传非法值），必须夹住
        assertEquals(0f, blurRadiusDp(-999), 0.0001f)
        assertEquals(0f, blurRadiusDp(-1), 0.0001f)
        assertEquals(28f, blurRadiusDp(101), 0.0001f)
        assertEquals(28f, blurRadiusDp(99999), 0.0001f)
    }

    @Test
    fun `所有合法百分比都落在合理区间`() {
        for (p in 0..100) {
            val r = blurRadiusDp(p)
            assertTrue("blurRadiusDp($p) = $r 越界", r in 0f..28f)
        }
    }

    @Test
    fun `中点的半径大致是一半`() {
        assertEquals(14f, blurRadiusDp(50), 0.0001f)
    }
}
