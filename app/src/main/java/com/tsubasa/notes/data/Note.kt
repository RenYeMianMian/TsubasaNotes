package com.tsubasa.notes.data

import org.json.JSONObject
import java.util.UUID

/**
 * 一条记事。
 *
 * 顺序即列表顺序 —— 列表本身就是数组顺序，所以拖拽排序不需要额外的 order 字段，
 * 只需要在数组里移动元素即可（见 [Repo.move]）。
 */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("text", text)
        put("done", done)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Note = Note(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            text = o.optString("text", ""),
            done = o.optBoolean("done", false),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
