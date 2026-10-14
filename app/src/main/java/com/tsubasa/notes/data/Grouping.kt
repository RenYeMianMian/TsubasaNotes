package com.tsubasa.notes.data

/**
 * 列表分组：把「未完成」排在前面，「已完成」沉到后面。
 *
 * 关键设计：**分组只是展示层的变换，不改动用户手动排的顺序**。
 * 存储里仍然是用户拖出来的那一个顺序；渲染时才按完成状态拆成两段，
 * 每段内部保持原有相对顺序。这样关掉分组就能立刻回到用户原始顺序。
 */
object Grouping {

    /** 未完成在前、已完成在后；两组内部各自保持原顺序。 */
    fun order(notes: List<Note>, group: Boolean): List<Note> {
        if (!group) return notes
        val (todo, done) = notes.partition { !it.done }
        return todo + done
    }

    fun todoCount(notes: List<Note>): Int = notes.count { !it.done }

    fun doneCount(notes: List<Note>): Int = notes.count { it.done }

    /**
     * 把 [newPageOrder] 这个新顺序「贴回」全量列表里原来的位置上。
     *
     * 为什么需要它：分组开启时，页面上看到的一段（比如全部未完成项）在全量列表里
     * 可能是不连续的下标。直接按下标写回会把顺序弄乱；按「原来的位置」贴回去，
     * 才能保证只改变这一段内部的相对顺序，其它条目一动不动。
     *
     * [newPageOrder] 里的 id 必须都来自 [full]；缺失的 id 会被忽略，
     * [full] 中不在 [newPageOrder] 里的条目保持原位。
     */
    fun splice(full: List<Note>, newPageOrder: List<String>): List<Note> {
        if (newPageOrder.isEmpty()) return full
        val byId = full.associateBy { it.id }
        val wanted = newPageOrder.mapNotNull { byId[it] }
        if (wanted.isEmpty()) return full

        val slots = full.indices.filter { full[it].id in newPageOrder.toSet() }
        // 数量对不上说明入参不一致，宁可不动也不要写坏顺序
        if (slots.size != wanted.size) return full

        val result = full.toMutableList()
        slots.forEachIndexed { i, pos -> result[pos] = wanted[i] }
        return result
    }
}
