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
            // 覆盖之前先看一眼旧文件还能不能解析。读不出来就说明它已经损坏，
            // 这时候直接写会把用户仅剩的数据彻底抹掉 —— 先留一份 .corrupt 备份。
            backupIfCorrupt(f)

            tmp.writeText(payload)

            // 先直接 rename：POSIX 的 rename(2) 会原子地替换已存在的目标文件。
            // 以前这里是「先 delete 再 rename」，中间存在一个文件不存在的窗口，
            // 小组件进程恰好在这个窗口里读就会拿到空列表，界面闪一下空白。
            if (tmp.renameTo(f)) return

            // rename 失败（少见，比如跨挂载点）时才退化为删掉再改名。
            if (f.exists() && f.delete()) {
                if (tmp.renameTo(f)) return
            }
            // 最后兜底：直接覆盖写。
            f.writeText(payload)
            tmp.delete()
        } catch (t: Throwable) {
            Log.e(TAG, "写入记事失败", t)
        }
    }

    /**
     * 旧文件解析不了时，先把它备份成 notes.json.corrupt。
     *
     * 不备份的话，[readFile] 的「解析失败回退为空列表」会连累下一次写入：
     * 用户看到的是「记事全没了」，再随便点一下勾选，空列表就被写回磁盘，
     * 真正的内容永远找不回来了。
     */
    private fun backupIfCorrupt(f: File) {
        if (!f.exists() || f.length() == 0L) return
        val parseable = try {
            val text = f.readText()
            text.isBlank() || JSONObject(text).has(KEY_NOTES)
        } catch (_: Throwable) {
            false
        }
        if (parseable) return
        val backup = File(f.parentFile, "$FILE_NAME.corrupt")
        try {
            f.copyTo(backup, overwrite = true)
            Log.w(TAG, "数据文件损坏，已备份到 ${backup.name}")
        } catch (t: Throwable) {
            Log.w(TAG, "备份损坏的数据文件失败", t)
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
        // 以前是「先 all() 读一次拿到当前状态，再 setDone() 写一次」——
        // 两次操作各自加锁，中间没有任何保护。App 和小组件同时点同一条时，
        // 两次读到的都是「未完成」，结果两次都写成「已完成」，勾选看起来丢了。
        // 现在读和写在同一次 mutate 里完成。
        mutate(context) { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i >= 0) {
                list[i] = list[i].copy(done = !list[i].done, updatedAt = System.currentTimeMillis())
            }
        }
    }

    fun delete(context: Context, id: String) {
        mutate(context) { list -> list.removeAll { it.id == id } }
    }

    /**
     * 把一份快照里「现在不见了」的记事按原来的位置插回去。
     *
     * 删除操作本身是不可逆的（直接改文件），但误删是很容易发生的：
     * 多选模式下点一下垃圾桶，整批就没了。这里配合 UI 的 Snackbar「撤销」，
     * 让用户有一次反悔的机会 —— 位置也尽量还原，不会把刚整理好的顺序打乱。
     */
    fun restore(context: Context, snapshot: List<Note>) {
        if (snapshot.isEmpty()) return
        mutate(context) { list ->
            snapshot.forEachIndexed { index, note ->
                if (list.none { it.id == note.id }) {
                    list.add(index.coerceIn(0, list.size), note)
                }
            }
        }
    }

    /**
     * 批量删除。
     *
     * 批量选择后一次删 N 条时，逐条调用 [delete] 会触发 N 次「读文件 + 写文件 + 广播」，
     * 数据量大时肉眼可见地卡，而且 N 次广播会让小组件重绘 N 遍。这里合成一次。
     */
    fun deleteAll(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        mutate(context) { list -> list.removeAll { it.id in set } }
    }

    /** 批量设置完成状态，理由同 [deleteAll]。 */
    fun setDoneAll(context: Context, ids: Collection<String>, done: Boolean) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        val now = System.currentTimeMillis()
        mutate(context) { list ->
            for (i in list.indices) {
                if (list[i].id in set) {
                    list[i] = list[i].copy(done = done, updatedAt = now)
                }
            }
        }
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
