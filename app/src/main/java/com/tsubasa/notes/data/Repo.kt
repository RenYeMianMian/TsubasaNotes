package com.tsubasa.notes.data

import android.content.Context
import android.content.Intent
import android.os.FileObserver
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 记事仓库。
 *
 * 刻意**不使用 Room**：桌面小组件的 RemoteViewsFactory 会在系统需要时被拉起，
 * 走 Room 会引入一堆生命周期与并发上的复杂度，而这里的全部数据就是一个记事列表。
 * 用一个 JSON 文件 + 进程内锁 + 原子替换写入，够用且没有隐藏状态：
 * - 每次写入先写临时文件再 rename（原子），避免读到写了一半的内容；
 * - 读取不做内存缓存 —— 文件很小，而任何缓存都要面对「小组件和 App 谁先写」的
 *   失效问题，直接读反而更简单也更不容易出错。
 */
object Repo {

    private const val TAG = "Tsubasa"
    private const val FILE_NAME = "notes.json"
    private const val KEY_NOTES = "notes"

    private val lock = Any()

    private var observer: FileObserver? = null

    fun file(context: Context): File = File(context.applicationContext.filesDir, FILE_NAME)

    // ---------------------------------------------------------------- 读

    fun all(context: Context): List<Note> = synchronized(lock) { readFile(file(context)) }

    /** 小组件用：只取需要显示的（可选是否包含已完成）。 */
    fun visible(context: Context, includeDone: Boolean): List<Note> {
        val all = all(context)
        return if (includeDone) all else all.filter { !it.done }
    }

    private fun readFile(f: File): List<Note> {
        if (!f.exists()) return emptyList()
        return try {
            val text = f.readText()
            if (text.isBlank()) return emptyList()
            val arr = JSONObject(text).optJSONArray(KEY_NOTES) ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { Note.fromJson(it) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "读取记事失败，回退为空列表", t)
            emptyList()
        }
    }

    private fun write(context: Context, notes: List<Note>) {
        val f = file(context)
        val arr = JSONArray()
        notes.forEach { arr.put(it.toJson()) }
        val payload = JSONObject().put(KEY_NOTES, arr).toString()
        val tmp = File(f.parentFile, "$FILE_NAME.tmp")
        try {
            tmp.writeText(payload)
            if (f.exists() && !f.delete()) {
                Log.w(TAG, "删除旧文件失败，改为直接覆盖")
                f.writeText(payload)
                tmp.delete()
                return
            }
            if (!tmp.renameTo(f)) {
                // rename 失败（少见）时退化为直接写
                f.writeText(payload)
                tmp.delete()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "写入记事失败", t)
        }
    }

    /** 所有写操作都经过这里：加锁 + 落盘 + 通知小组件。 */
    private fun mutate(context: Context, block: (MutableList<Note>) -> Unit) {
        synchronized(lock) {
            val list = all(context).toMutableList()
            block(list)
            write(context, list.toList())
        }
        WidgetBridge.refresh(context)
    }

    // ---------------------------------------------------------------- 写

    fun add(context: Context, text: String): Note? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val note = Note(text = t)
        mutate(context) { it.add(note) }
        return note
    }

    fun updateText(context: Context, id: String, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        mutate(context) { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) list[i] = list[i].copy(text = t, updatedAt = System.currentTimeMillis())
        }
    }

    fun setDone(context: Context, id: String, done: Boolean) {
        mutate(context) { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) list[i] = list[i].copy(done = done, updatedAt = System.currentTimeMillis())
        }
    }

    fun toggleDone(context: Context, id: String) {
        val cur = all(context).firstOrNull { it.id == id } ?: return
        setDone(context, id, !cur.done)
    }

    fun delete(context: Context, id: String) {
        mutate(context) { list -> list.removeAll { it.id == id } }
    }

    /** 清空所有已完成项。 */
    fun clearDone(context: Context) {
        mutate(context) { list -> list.removeAll { it.done } }
    }

    /** 拖拽排序：把 from 位置的元素移动到 to 位置。 */
    fun move(context: Context, from: Int, to: Int) {
        mutate(context) { list ->
            if (from in list.indices && to in list.indices && from != to) {
                val item = list.removeAt(from)
                list.add(to, item)
            }
        }
    }

    /**
     * 按给定 id 顺序重排整个列表（拖拽结束时调用一次）。
     *
     * 拖动过程只在内存里改顺序，松手时才落盘一次 —— 拖动会连续产生几十次位置变化，
     * 每次都写文件既慢又会让小组件疯狂重绘。
     */
    fun reorder(context: Context, orderedIds: List<String>) {
        mutate(context) { list ->
            val byId = list.associateBy { it.id }
            val reordered = orderedIds.mapNotNull { byId[it] }
            val missing = list.filter { it.id !in orderedIds }
            list.clear()
            list.addAll(reordered)
            list.addAll(missing)
        }
    }

    /** 与上/下一条交换（小组件里的「上移」按钮用）。 */
    fun moveBy(context: Context, id: String, delta: Int) {
        mutate(context) { list ->
            val i = list.indexOfFirst { it.id == id }
            val j = i + delta
            if (i >= 0 && j in list.indices) {
                val item = list.removeAt(i)
                list.add(j, item)
            }
        }
    }

    /** 把已完成项沉到列表底部，未完成项保持相对顺序。 */
    fun sortDoneToBottom(context: Context) {
        mutate(context) { list ->
            val (done, todo) = list.partition { it.done }
            list.clear()
            list.addAll(todo)
            list.addAll(done)
        }
    }

    // ------------------------------------------------- App 进程内监听

    /**
     * App 在前台时监听数据文件（小组件里勾选/删除后，App 界面立刻同步）。
     *
     * 注意：只用于**触发界面刷新**，不作为读取数据的前置条件 —— 读取永远是直接读文件，
     * 所以即使漏掉一次事件也不会读到过期数据。
     */
    fun startWatching(context: Context, onChange: () -> Unit) {
        stopWatching()
        val dir = context.applicationContext.filesDir
        // 用 String 构造器而不是 File 构造器：FileObserver(File, int) 是 API 29 才有的，
        // 而本应用 minSdk 26，用错会在 Android 8/9 上直接崩。
        @Suppress("DEPRECATION")
        observer = object : FileObserver(dir.absolutePath, CREATE or MODIFY or MOVED_TO or DELETE) {
            override fun onEvent(event: Int, path: String?) {
                // 精确匹配，避免临时文件 notes.json.tmp 也触发一次
                if (path == FILE_NAME) onChange()
            }
        }.also {
            try {
                it.startWatching()
            } catch (t: Throwable) {
                Log.w(TAG, "启动文件监听失败", t)
            }
        }
    }

    fun stopWatching() {
        try {
            observer?.stopWatching()
        } catch (_: Throwable) {
        }
        observer = null
    }
}

/**
 * 通知桌面小组件刷新。
 *
 * 单独放在一个对象里，是为了让「数据层」不直接依赖 AppWidget 相关类，
 * 这样 Repo 在纯 JVM 环境下也能被引用。
 */
object WidgetBridge {
    fun refresh(context: Context) {
        try {
            context.sendBroadcast(
                Intent(ACTION_REFRESH).setPackage(context.packageName)
            )
        } catch (t: Throwable) {
            Log.w("Tsubasa", "刷新小组件广播失败", t)
        }
    }

    const val ACTION_REFRESH = "com.tsubasa.notes.ACTION_REFRESH"
}
