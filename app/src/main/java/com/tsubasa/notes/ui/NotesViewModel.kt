package com.tsubasa.notes.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tsubasa.notes.R
import com.tsubasa.notes.data.Grouping
import com.tsubasa.notes.data.Note
import com.tsubasa.notes.data.Prefs
import com.tsubasa.notes.data.Repo
import com.tsubasa.notes.data.WidgetBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Filter { ALL, TODO, DONE }

/**
 * 列表里的一行：要么是分区标题，要么是一条记事。
 *
 * 把「标题」也当成一种行，渲染端（LazyColumn）就能用同一套 key/拖拽逻辑处理，
 * 不用为标题单独写一遍分页和索引换算。
 */
sealed interface ListRow {
    /**
     * 分区标题。[labelRes] 存的是**字符串资源 id 而不是成品文案** ——
     * ViewModel 里没有 Context，取不到当前语言；而且把文案在这里定死，
     * 用户切换语言后旧文案会一直留在已经构造好的行里。渲染端拿到 id
     * 再 stringResource()，语言一换自然就对了。
     */
    data class Section(@StringRes val labelRes: Int, val count: Int, val done: Boolean) : ListRow
    data class Item(val note: Note) : ListRow
}

data class UiState(
    val notes: List<Note> = emptyList(),
    val filter: Filter = Filter.ALL,
    val query: String = "",
    val page: Int = 0,
    val pageSize: Int = Prefs.DEFAULT_APP_PAGE_SIZE,
    val selected: Set<String> = emptySet(),
    val selecting: Boolean = false,
    /** 未完成在前、已完成沉底并加分区标题 */
    val groupDone: Boolean = true,
    /**
     * 自定义软件背景图的路径。
     *
     * 放在 UiState 里而不是让界面直接读 Prefs，是因为 Prefs 只是普通读取，
     * **不是 Compose 的 State** —— 改了它不会触发重组，背景图就永远不刷新
     * （小组件那边能变是因为它走广播刷新）。放进 StateFlow 才能让界面跟着变。
     */
    val appBgImage: String = "",
    /** 背景模糊强度 0..100（0 = 不模糊），整屏统一模糊 */
    val barBlur: Int = Prefs.DEFAULT_BACKDROP_BLUR,
    /**
     * App 配色来源：system / custom，以及自定义时的种子色。
     *
     * 和 [appBgImage] 同样的理由放进 UiState：[TsubasaTheme] 里读的是 Prefs，
     * 而 Prefs 不是 Compose State —— 用户在设置里换了颜色，主题不会重组，
     * 看起来就像「选了没反应」。放进 StateFlow 后配色改动会立刻反映到界面上。
     */
    val appColor: String = "system",
    /** 自定义配色的种子色。默认 Material 紫，和小组件那边的默认值一致。 */
    val appSeed: Int = 0xFF6750A4.toInt(),
    /** App 明暗：system / light / dark */
    val appTheme: String = "system",
    val loading: Boolean = true
)

class NotesViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** 自增信号：让界面聚焦输入框（小组件点「+」进来时用）。 */
    private val _focusSignal = MutableStateFlow(0)
    val focusSignal: StateFlow<Int> = _focusSignal.asStateFlow()

    fun requestNewNoteFocus() {
        _focusSignal.value = _focusSignal.value + 1
    }

    init {
        refresh()
        // 小组件改了数据（勾选/删除/排序）时，App 立刻同步
        Repo.startWatching(getApplication()) { refresh() }
    }

    fun refresh() {
        viewModelScope.launch {
            val notes = withContext(Dispatchers.IO) { Repo.all(getApplication()) }
            val size = Prefs.appPageSize(getApplication())
            val group = Prefs.groupDone(getApplication())
            val appBg = Prefs.appBgImage(getApplication())
            val blur = Prefs.backdropBlur(getApplication())
            val appColor = Prefs.appColor(getApplication())
            val appSeed = Prefs.appSeed(getApplication())
            val appTheme = Prefs.appTheme(getApplication())
            _state.value = _state.value.copy(
                notes = notes,
                pageSize = size,
                groupDone = group,
                appBgImage = appBg,
                barBlur = blur,
                appColor = appColor,
                appSeed = appSeed,
                appTheme = appTheme,
                loading = false
            )
        }
    }

    /**
     * 过滤 + 搜索 + 分组之后的结果（分页前）。
     *
     * 注意分组只在这里做，**不改动 [UiState.notes] 里用户手动排的顺序** ——
     * 所以关掉分组能立刻回到用户原来的顺序。
     */
    fun visibleNotes(s: UiState): List<Note> {
        var list = s.notes
        list = when (s.filter) {
            Filter.ALL -> list
            Filter.TODO -> list.filter { !it.done }
            Filter.DONE -> list.filter { it.done }
        }
        if (s.query.isNotBlank()) {
            val q = s.query.trim()
            list = list.filter { it.text.contains(q, ignoreCase = true) }
        }
        return Grouping.order(list, s.groupDone && s.filter == Filter.ALL)
    }

    /** 当前页的记事（不含标题）。 */
    fun pageItems(s: UiState): List<Note> {
        val all = visibleNotes(s)
        val from = (s.page * s.pageSize).coerceIn(0, all.size)
        val to = (from + s.pageSize).coerceAtMost(all.size)
        return if (from >= to) emptyList() else all.subList(from, to)
    }

    fun pageCount(s: UiState): Int {
        val total = visibleNotes(s).size
        return if (total <= 0) 1 else (total + s.pageSize - 1) / s.pageSize
    }

    /**
     * 当前页要渲染的行（含分区标题）。
     *
     * 什么时候插标题：只有当整个列表里**同时存在**未完成和已完成时才插，
     * 否则（清一色未完成）插一个「未完成」标题纯属噪音。
     * 标题出现在每页开头以及页内发生分区切换的位置。
     */
    fun pageRows(s: UiState): List<ListRow> {
        val visible = visibleNotes(s)
        val pageNotes = pageItems(s)
        if (!s.groupDone || s.filter != Filter.ALL) {
            return pageNotes.map { ListRow.Item(it) }
        }

        val todo = visible.count { !it.done }
        val done = visible.size - todo
        if (todo == 0 || done == 0) {
            // 只有一类，不需要分区标题
            return pageNotes.map { ListRow.Item(it) }
        }

        val rows = mutableListOf<ListRow>()
        var lastDone: Boolean? = null
        for (n in pageNotes) {
            if (lastDone == null || n.done != lastDone) {
                rows += if (n.done) {
                    ListRow.Section(R.string.section_done, done, done = true)
                } else {
                    ListRow.Section(R.string.section_todo, todo, done = false)
                }
            }
            rows += ListRow.Item(n)
            lastDone = n.done
        }
        return rows
    }

    /** 是否允许拖拽排序：只在「全部」且无搜索时开放（过滤后重排语义不明确）。 */
    fun canReorder(s: UiState): Boolean = s.filter == Filter.ALL && s.query.isBlank()

    // ------------------------------------------------------------ 操作

    fun setFilter(f: Filter) = update {
        it.copy(filter = f, page = 0, selected = emptySet(), selecting = false)
    }

    fun setQuery(q: String) = update { it.copy(query = q, page = 0) }

    fun setPage(p: Int) = update { it.copy(page = p.coerceAtLeast(0)) }

    fun add(text: String) {
        if (text.isBlank()) return
        io { Repo.add(getApplication(), text) }
    }

    fun updateText(id: String, text: String) = io { Repo.updateText(getApplication(), id, text) }

    fun toggle(id: String) = io { Repo.toggleDone(getApplication(), id) }

    fun delete(id: String) = io { Repo.delete(getApplication(), id) }

    fun clearDone() = io { Repo.clearDone(getApplication()) }

    fun sortDoneToBottom() = io { Repo.sortDoneToBottom(getApplication()) }

    /**
     * 拖拽排序：传入的是**当前页内**的位置。
     *
     * 两个要点：
     * 1. 拖动过程只改内存（界面跟手），松手才落盘 —— 见 [commitReorder]；
     * 2. 排序作用在「展示顺序」上，再按 id 贴回全量列表（[Grouping.splice]）。
     *    分组开启时，页面上连续的一段在全量列表里可能是跳跃的下标，
     *    直接按下标写回会把顺序弄乱。
     */
    fun dragReorder(fromLocal: Int, toLocal: Int) {
        val s = _state.value
        if (!canReorder(s)) return

        val visible = visibleNotes(s).toMutableList()
        val base = s.page * s.pageSize
        val from = base + fromLocal
        val to = base + toLocal
        if (from !in visible.indices || to !in visible.indices || from == to) return

        // 分组开启时不允许跨分区拖动：拖过去也会被分组立刻弹回原位，
        // 手感上像「拖不动」，不如直接拦住。想改完成状态请点勾选。
        if (s.groupDone && visible[from].done != visible[to].done) return

        val item = visible.removeAt(from)
        visible.add(to, item)

        _state.value = s.copy(notes = Grouping.splice(s.notes, visible.map { it.id }))
    }

    /** 松手后把当前顺序落盘，并同步到小组件。 */
    fun commitReorder() {
        val ids = _state.value.notes.map { it.id }
        io { Repo.reorder(getApplication(), ids) }
    }

    // 多选
    fun toggleSelect(id: String) = update { s ->
        val sel = if (s.selected.contains(id)) s.selected - id else s.selected + id
        s.copy(selected = sel, selecting = sel.isNotEmpty())
    }

    fun clearSelection() = update { it.copy(selected = emptySet(), selecting = false) }

    fun selectAll() = update { s ->
        val all = pageItems(s).map { it.id }.toSet()
        s.copy(selected = all, selecting = all.isNotEmpty())
    }

    fun deleteSelected() {
        val ids = _state.value.selected
        if (ids.isEmpty()) return
        io { ids.forEach { Repo.delete(getApplication(), it) } }
        clearSelection()
    }

    fun setDoneSelected(done: Boolean) {
        val ids = _state.value.selected
        if (ids.isEmpty()) return
        io { ids.forEach { Repo.setDone(getApplication(), it, done) } }
        clearSelection()
    }

    // ------------------------------------------------------------ 设置

    fun setAppPageSize(v: Int) {
        Prefs.setAppPageSize(getApplication(), v)
        update { it.copy(pageSize = Prefs.appPageSize(getApplication()), page = 0) }
    }

    fun setGroupDone(v: Boolean) {
        Prefs.setGroupDone(getApplication(), v)
        // 分组会改变可见顺序，页码要回到第一页，避免停在一个越界的页上
        update { it.copy(groupDone = v, page = 0, selected = emptySet(), selecting = false) }
        WidgetBridge.refresh(getApplication())
    }

    fun setWidgetPageSize(v: Int) {
        Prefs.setWidgetPageSize(getApplication(), v)
        WidgetBridge.refresh(getApplication())
        update { it.copy() }
    }

    fun setWidgetShowDone(v: Boolean) {
        Prefs.setWidgetShowDone(getApplication(), v)
        WidgetBridge.refresh(getApplication())
        update { it.copy() }
    }

    fun setWidgetTheme(v: String) {
        Prefs.setWidgetTheme(getApplication(), v)
        WidgetBridge.refresh(getApplication())
        update { it.copy() }
    }

    fun setWidgetSeed(v: Int) {
        Prefs.setWidgetSeed(getApplication(), v)
        WidgetBridge.refresh(getApplication())
        update { it.copy() }
    }

    fun setAppTheme(v: String) {
        Prefs.setAppTheme(getApplication(), v)
        update { it.copy(appTheme = v) }
    }

    /**
     * 切换 App 配色来源（system / custom）。
     *
     * **不刷小组件**：App 的自定义颜色按用户要求只影响 App，小组件有自己的配色。
     */
    fun setAppColor(v: String) {
        Prefs.setAppColor(getApplication(), v)
        update { it.copy(appColor = v) }
    }

    /** 选 App 自定义配色的种子色。只影响 App，所以同样不刷小组件。 */
    fun setAppSeed(v: Int) {
        Prefs.setAppSeed(getApplication(), v)
        update { it.copy(appSeed = v) }
    }

    /**
     * 切换界面语言。
     *
     * 这里**只写偏好、不动 state** —— 语言是在 Activity 的 attachBaseContext
     * 里套上去的，光改 state 不会让已经建好的界面换语言。调用方写完这个
     * 要自己调 recreate() 让整棵界面重新取一遍资源。
     *
     * 小组件那边不需要重建：它每次刷新都是新起一轮 getString，
     * 下次 [WidgetBridge.refresh] 时就自动是目标语言了。
     */
    fun setAppLang(tag: String) {
        Prefs.setAppLang(getApplication(), tag)
        WidgetBridge.refresh(getApplication())
    }

    // ------------------------------------------------------------ 外观（背景 / 透明度）

    fun setWidgetBgOpacity(v: Int) {
        Prefs.setWidgetBgOpacity(getApplication(), v)
        WidgetBridge.refresh(getApplication())
        update { it.copy() }
    }

    fun setAppBgImage(path: String) {
        Prefs.setAppBgImage(getApplication(), path)
        // 同步进 state，界面才会立刻换背景（不能只写 Prefs）
        update { it.copy(appBgImage = path) }
    }

    fun setBarBlur(v: Int) {
        Prefs.setBackdropBlur(getApplication(), v)
        update { it.copy(barBlur = v.coerceIn(0, 100)) }
    }

    fun setWidgetBgImage(path: String) {
        Prefs.setWidgetBgImage(getApplication(), path)
        WidgetBridge.refresh(getApplication())
        update { it.copy() }
    }

    override fun onCleared() {
        Repo.stopWatching()
        super.onCleared()
    }

    // ------------------------------------------------------------ 工具

    private fun update(block: (UiState) -> UiState) {
        _state.value = block(_state.value)
    }

    private fun io(block: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            refresh()
        }
    }

    companion object {
        // 分区标题的文案已移入 strings.xml（section_todo / section_done），
        // 见 ListRow.Section.labelRes 的说明。
    }
}
