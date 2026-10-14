package com.tsubasa.notes

import com.tsubasa.notes.data.Grouping
import com.tsubasa.notes.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分组（未完成在前、已完成沉底）与「贴回原顺序」的逻辑。 */
class GroupingTests {

    private fun n(id: String, done: Boolean = false) =
        Note(id = id, text = id, done = done)

    // ------------------------------------------------------------ order

    @Test
    fun `分组把未完成排在前面且各自保持原顺序`() {
        val list = listOf(
            n("A", done = true),
            n("B"),
            n("C", done = true),
            n("D")
        )

        val ordered = Grouping.order(list, group = true)

        assertEquals(listOf("B", "D", "A", "C"), ordered.map { it.id })
    }

    @Test
    fun `关掉分组时顺序完全不动`() {
        val list = listOf(n("A", true), n("B"), n("C", true))

        val ordered = Grouping.order(list, group = false)

        assertEquals(listOf("A", "B", "C"), ordered.map { it.id })
    }

    @Test
    fun `全部未完成时分组不改变顺序`() {
        val list = listOf(n("A"), n("B"), n("C"))
        assertEquals(listOf("A", "B", "C"), Grouping.order(list, true).map { it.id })
    }

    @Test
    fun `全部已完成时分组不改变顺序`() {
        val list = listOf(n("A", true), n("B", true))
        assertEquals(listOf("A", "B"), Grouping.order(list, true).map { it.id })
    }

    @Test
    fun `空列表不炸`() {
        assertEquals(0, Grouping.order(emptyList(), true).size)
    }

    @Test
    fun `分组不会丢条目也不会重复`() {
        val list = List(20) { n("id$it", done = it % 3 == 0) }
        val ordered = Grouping.order(list, true)

        assertEquals(20, ordered.size)
        assertEquals(list.map { it.id }.toSet(), ordered.map { it.id }.toSet())
    }

    @Test
    fun `计数正确`() {
        val list = listOf(n("A"), n("B", true), n("C"))
        assertEquals(2, Grouping.todoCount(list))
        assertEquals(1, Grouping.doneCount(list))
    }

    // ------------------------------------------------------------ splice

    @Test
    fun `splice 把新顺序贴回原来的位置`() {
        // 全量顺序：A B C D
        val full = listOf(n("A"), n("B"), n("C"), n("D"))
        // 用户把「A B」这一段调成了「B A」
        val result = Grouping.splice(full, listOf("B", "A"))

        assertEquals(listOf("B", "A", "C", "D"), result.map { it.id })
    }

    @Test
    fun `splice 只动被提到的那一段，其它条目原位不动`() {
        val full = listOf(n("A"), n("B"), n("C"), n("D"), n("E"))
        // 只重排 B 和 D（在列表里是不连续的），C 应该留在原地
        val result = Grouping.splice(full, listOf("D", "B"))

        assertEquals(listOf("A", "D", "C", "B", "E"), result.map { it.id })
    }

    @Test
    fun `splice 传空列表时原样返回`() {
        val full = listOf(n("A"), n("B"))
        assertEquals(full.map { it.id }, Grouping.splice(full, emptyList()).map { it.id })
    }

    @Test
    fun `splice 遇到不存在的 id 时不会写坏顺序`() {
        val full = listOf(n("A"), n("B"))
        val result = Grouping.splice(full, listOf("A", "不存在"))

        // 数量对不上，应当原样返回而不是产生缺项
        assertEquals(listOf("A", "B"), result.map { it.id })
    }

    @Test
    fun `splice 之后条目总数不变`() {
        val full = List(10) { n("id$it") }
        val reordered = listOf("id3", "id1", "id2")
        val result = Grouping.splice(full, reordered)

        assertEquals(10, result.size)
        assertEquals(full.map { it.id }.toSet(), result.map { it.id }.toSet())
    }

    @Test
    fun `splice 的结果确实按给定顺序排列在那几个位置上`() {
        val full = List(6) { n("id$it") }
        val result = Grouping.splice(full, listOf("id5", "id0", "id3"))

        // 0/3/5 这三个位置现在应该依次是 id5 id0 id3
        assertEquals("id5", result[0].id)
        assertEquals("id0", result[3].id)
        assertEquals("id3", result[5].id)
        // 其它位置不动
        assertEquals("id1", result[1].id)
        assertEquals("id2", result[2].id)
        assertEquals("id4", result[4].id)
    }
}
