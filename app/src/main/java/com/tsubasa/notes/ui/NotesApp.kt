package com.tsubasa.notes.ui

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import com.tsubasa.notes.L10n
import com.tsubasa.notes.R
import com.tsubasa.notes.data.Note
import com.tsubasa.notes.data.Prefs
import com.tsubasa.notes.ui.background.ImageStore
import com.tsubasa.notes.widget.Palette
import kotlin.math.roundToInt

private val CARD_HEIGHT = 56.dp
private val CARD_GAP = 8.dp
private val CARD_RADIUS = 16.dp

/**
 * 列表区的动画目标。
 *
 * 只把 `items.isEmpty()` 当目标时，「换页 / 换标签」不会触发任何过渡，
 * 内容会原地瞬间替换。把筛选和页码一起放进来，才能判断是
 * 「空态切换」还是「翻页」，进而选不同的过渡。
 */
private data class ListTarget(
    val loading: Boolean,
    val empty: Boolean,
    val filter: Filter,
    val page: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesApp(vm: NotesViewModel) {
    val state by vm.state.collectAsState()
    val focusSignal by vm.focusSignal.collectAsState()

    var input by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Note?>(null) }

    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // 小组件点「+」进来：聚焦输入框
    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val items = vm.pageItems(state)
    val rows = vm.pageRows(state)
    val pages = vm.pageCount(state)
    val canReorder = vm.canReorder(state)

    // 系统返回键的优先级：先退出搜索，再退出多选，最后才真的退出 App。
    // 以前这里完全没有拦截，搜索状态下按返回会直接退出应用，
    // 是用户最容易撞上、也最容易觉得「这软件有 bug」的一处。
    BackHandler(enabled = searchMode || state.selecting) {
        if (searchMode) {
            searchMode = false
            vm.setQuery("")
        } else {
            vm.clearSelection()
        }
    }

    Scaffold(
        // 背景由 MainActivity 铺在 NotesApp 下面（可能是用户自选图片或主题色），
        // 所以 Scaffold 必须透明，否则它那层实色会把背景整个盖掉。
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // 栏本身透明：模糊已经在 MainActivity 里**整屏**做过了，
            // 这里只要不盖住背景，壁纸/磨砂就会从底下透上来。
            // （之前是每个栏各自糊一遍，导致中间那段没被糊到，见 README。）
            // 普通栏和多选栏之间淡入淡出：两者高度相同，直接替换会「啪」地闪一下，
            // 进入多选时视觉上很突兀。
            AnimatedContent(
                targetState = state.selecting,
                transitionSpec = {
                    (fadeIn(tween(180)) + slideInVertically(tween(180)) { -it / 6 })
                        .togetherWith(fadeOut(tween(120)))
                },
                label = "topBar"
            ) { selecting ->
                if (selecting) {
                    SelectionBar(
                        count = state.selected.size,
                        onCancel = { vm.clearSelection() },
                        onSelectAll = { vm.selectAll() },
                        onDone = { vm.setDoneSelected(true) },
                        onUndo = { vm.setDoneSelected(false) },
                        onDelete = {
                            // 删除直接改文件、没有任何确认框。以前误删就彻底没了，
                            // 现在先留一份快照，再用 Snackbar 给一次反悔机会。
                            val snapshot = vm.notesSnapshot()
                            vm.deleteSelected()
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.deleted_toast),
                                    actionLabel = context.getString(R.string.undo),
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    vm.restore(snapshot)
                                }
                            }
                        }
                    )
                } else {
                    TopBar(
                        searchMode = searchMode,
                        query = state.query,
                        onQuery = { vm.setQuery(it) },
                        onToggleSearch = {
                            searchMode = !searchMode
                            if (!searchMode) vm.setQuery("")
                        },
                        onSelect = { vm.selectAll() },
                        onSettings = { showSettings = true },
                        onSortDone = { vm.sortDoneToBottom() },
                        onClearDone = {
                            // 「清除已完成」以前只存在于数据层，界面上根本点不到。
                            // 现在接进溢出菜单，并且和批量删除一样给一次撤销机会。
                            val snapshot = vm.notesSnapshot()
                            vm.clearDone()
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.deleted_toast),
                                    actionLabel = context.getString(R.string.undo),
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    vm.restore(snapshot)
                                }
                            }
                        }
                    )
                }
            }
        },
        bottomBar = {
            InputBar(
                value = input,
                onValueChange = { input = it },
                focusRequester = focusRequester,
                onSubmit = {
                    if (input.isNotBlank()) {
                        vm.add(input)
                        input = ""
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            FilterTabs(
                filter = state.filter,
                onFilter = { vm.setFilter(it) }
            )

            Box(modifier = Modifier.weight(1f)) {
                // 列表 / 空态 / 换页 / 换标签都用同一段过渡：
                //  · 空↔非空：淡入 + 轻微放大（删掉最后一条时不再硬跳一帧）
                //  · 换页、换标签：按方向左右滑一小段再淡入，让人看得出「翻过去了」
                AnimatedContent(
                    targetState = ListTarget(
                        // 冷启动时 loading=true、notes 还是空的。以前这里直接用
                        // items.isEmpty() 判断，于是「还没有记事」的空态会先闪一下，
                        // 数据读完之后才被列表替换掉 —— 每次打开都像丢过一次数据。
                        // 加载期间单独一档：先留白，读完再决定显示列表还是空态。
                        loading = state.loading,
                        empty = items.isEmpty() && !state.loading,
                        filter = state.filter,
                        page = state.page
                    ),
                    transitionSpec = {
                        val sameShape = initialState.empty == targetState.empty
                        if (initialState.loading || targetState.loading) {
                            // 首屏加载结束：只淡入，不要左右滑 —— 用户还没做过任何操作
                            fadeIn(tween(220)).togetherWith(fadeOut(tween(120)))
                        } else if (sameShape) {
                            // 前进（页码变大 / 标签往后）从右侧进来，后退反之。
                            val forward = targetState.page > initialState.page ||
                                (targetState.page == initialState.page &&
                                    targetState.filter.ordinal > initialState.filter.ordinal)
                            val dir = if (forward) 1 else -1
                            (slideInHorizontally(tween(220)) { w -> dir * w / 5 } + fadeIn(tween(220)))
                                .togetherWith(
                                    slideOutHorizontally(tween(180)) { w -> -dir * w / 5 } +
                                        fadeOut(tween(160))
                                )
                        } else {
                            (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.96f))
                                .togetherWith(fadeOut(tween(140)))
                        }
                    },
                    label = "listState"
                ) { target ->
                    if (target.loading) {
                        // 读数据的那几十毫秒什么都不画，避免「空态闪一下」
                        Box(Modifier.fillMaxSize())
                    } else if (target.empty) {
                        EmptyState(
                            text = if (state.query.isNotBlank())
                                stringResource(R.string.search_no_match, state.query)
                            else stringResource(R.string.empty_list),
                            searching = state.query.isNotBlank()
                        )
                    } else {
                        NoteList(
                            rows = rows,
                            canReorder = canReorder,
                            selected = state.selected,
                            selecting = state.selecting,
                            onToggle = { vm.toggle(it) },
                            onEdit = { editing = it },
                            onSelect = { vm.toggleSelect(it) },
                            onLongPress = { vm.toggleSelect(it) },
                            onDrag = { f, t -> vm.dragReorder(f, t) },
                            onDragEnd = { vm.commitReorder() }
                        )
                    }
                }
            }

            // 分页条只在需要时出现，用展开/收起动画，
            // 否则条数刚好跨过一页时底部会突然多出一条、把列表顶上去。
            AnimatedVisibility(
                visible = pages > 1,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(120))
            ) {
                PaginationBar(
                    page = state.page,
                    pages = pages,
                    total = vm.visibleNotes(state).size,
                    pageSize = state.pageSize,
                    onPage = { vm.setPage(it) }
                )
            }
        }
    }

    if (showSettings) {
        SettingsSheet(
            vm = vm,
            onDismiss = { showSettings = false }
        )
    }

    editing?.let { note ->
        EditDialog(
            note = note,
            onDismiss = { editing = null },
            onSave = {
                vm.updateText(note.id, it)
                editing = null
            }
        )
    }
}

// ------------------------------------------------------------------ 顶栏

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    searchMode: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSelect: () -> Unit,
    onSettings: () -> Unit,
    onSortDone: () -> Unit,
    onClearDone: () -> Unit
) {
    // 顶栏透明，壁纸从底下透上来；模糊由 MainActivity 整屏统一负责
    var menuOpen by remember { mutableStateOf(false) }

    // 进入搜索时自动聚焦并弹键盘。
    // 以前点放大镜只是把标题换成输入框，还得再点一下输入框才能打字 ——
    // 多这一步会让人以为「搜索没反应」。
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(searchMode) {
        if (searchMode) runCatching { searchFocus.requestFocus() }
    }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        title = {
            if (searchMode) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocus)
                )
            } else {
                Text(
                    text = stringResource(R.string.app_name),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        navigationIcon = {
            if (searchMode) {
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_cancel)
                    )
                }
            }
        },
        actions = {
            if (!searchMode) {
                // MD3 的顶栏最多放 3 个操作位。以前这里挤了 4 个图标
                // （排序 / 多选 / 搜索 / 设置），小屏上又窄又容易点错，
                // 现在只留最常用的两个，其余收进溢出菜单。
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                }
                IconButton(onClick = onSelect) {
                    Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.action_select))
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_sort_done)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onSortDone()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_clear_done)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onClearDone()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_settings)) },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onSettings()
                            }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SelectionBar(
    count: Int,
    onCancel: () -> Unit,
    onSelectAll: () -> Unit,
    onDone: () -> Unit,
    onUndo: () -> Unit,
    onDelete: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        title = { Text(stringResource(R.string.selected_count, count)) },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Default.SelectAll, contentDescription = stringResource(R.string.action_select_all))
            }
            IconButton(onClick = onDone) {
                Icon(Icons.Default.CheckCircle, contentDescription = stringResource(R.string.action_mark_done))
            }
            IconButton(onClick = onUndo) {
                Icon(Icons.Default.RadioButtonUnchecked, contentDescription = stringResource(R.string.action_mark_undo))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_note))
            }
        }
    )
}

// ------------------------------------------------------------------ 筛选

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterTabs(filter: Filter, onFilter: (Filter) -> Unit) {
    val tabs = listOf(
        Filter.ALL to stringResource(R.string.tab_all),
        Filter.TODO to stringResource(R.string.tab_todo),
        Filter.DONE to stringResource(R.string.tab_done)
    )
    TabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == filter }.coerceAtLeast(0),
        // 透明：这一条也在「整屏模糊」的范围里，盖住背景就会露出一块没糊的实色
        containerColor = Color.Transparent,
        // M3 的指示器本来就会跟着选中项滑动，这里只是把两端收窄，
        // 让它看起来像「标签下划线」而不是整条分割线。
        indicator = { tabPositions ->
            val index = tabs.indexOfFirst { it.first == filter }.coerceAtLeast(0)
            if (index in tabPositions.indices) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier
                        .tabIndicatorOffset(tabPositions[index])
                        .padding(horizontal = 24.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        tabs.forEach { (f, label) ->
            Tab(
                selected = filter == f,
                onClick = { onFilter(f) },
                text = { Text(label) }
            )
        }
    }
}

// ------------------------------------------------------------------ 列表

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteList(
    rows: List<ListRow>,
    canReorder: Boolean,
    selected: Set<String>,
    selecting: Boolean,
    onToggle: (String) -> Unit,
    onEdit: (Note) -> Unit,
    onSelect: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onDrag: (Int, Int) -> Unit,
    onDragEnd: () -> Unit
) {
    val listState = rememberLazyListState()

    // 拖拽状态。用 id 而不是下标作为「正在拖谁」的标识：
    // 重排会让下标变化，用下标会导致手势被中断。
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // 手势回调里需要读到最新的列表，用 rememberUpdatedState 避免闭包过期，
    // 同时又不重启 pointerInput（重启会打断拖拽）。
    val latestRows = rememberUpdatedState(rows)

    // 一行的高度 = 卡片高度 + 上下间距。
    // 拖拽时用这个换算「移动了几格」；用常量而不是实测高度，
    // 是因为拖拽过程中行还在动，实测值会抖。
    val density = androidx.compose.ui.platform.LocalDensity.current
    val slotHeightPx = with(density) { (CARD_HEIGHT + CARD_GAP).toPx() }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP)
    ) {
        itemsIndexed(rows, key = { _, row ->
            when (row) {
                is ListRow.Item -> row.note.id
                is ListRow.Section -> "section-${row.done}"
            }
        }) { _, row ->
            when (row) {
                is ListRow.Section -> SectionHeader(
                    row = row,
                    // 拖拽换位时标题也要跟着走，否则会看到卡片从标题底下穿过去
                    modifier = Modifier.animateItemPlacement(
                        spring(stiffness = Spring.StiffnessMediumLow)
                    )
                )

                is ListRow.Item -> {
                    val note = row.note
                    val isDragging = draggingId == note.id

                    // 拖拽只在「记事」之间换位，标题不参与 ——
                    // 下面换算 cur/target 时用的是「页内记事序列」，
                    // 与 ViewModel 里 pageItems 的顺序一致。
                    val handleModifier = if (canReorder) {
                        Modifier.pointerInput(note.id) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingId = note.id
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggingId = null
                                    dragOffset = 0f
                                    onDragEnd()
                                },
                                onDragCancel = {
                                    draggingId = null
                                    dragOffset = 0f
                                    onDragEnd()
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val id = draggingId ?: return@detectDragGestures
                                    val list = latestRows.value
                                        .filterIsInstance<ListRow.Item>()
                                        .map { it.note }
                                    val cur = list.indexOfFirst { it.id == id }
                                    if (cur < 0) return@detectDragGestures
                                    val shift = (dragOffset / slotHeightPx).roundToInt()
                                    if (shift != 0) {
                                        val target = (cur + shift).coerceIn(0, list.lastIndex)
                                        if (target != cur) {
                                            onDrag(cur, target)
                                            // 补偿已经消费掉的位移，避免跳变
                                            dragOffset -= (target - cur) * slotHeightPx
                                        }
                                    }
                                }
                            )
                        }
                    } else Modifier

                    NoteRow(
                        note = note,
                        isSelected = selected.contains(note.id),
                        selecting = selecting,
                        canReorder = canReorder,
                        handleModifier = handleModifier,
                        modifier = Modifier
                            // 让其它行在换位时平滑滑过去，而不是瞬间跳一格。
                            // 被拖的那一行不参与，否则会和手指的 translationY 打架。
                            .then(
                                if (isDragging) Modifier
                                else Modifier.animateItemPlacement(
                                    spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            )
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                if (isDragging) {
                                    translationY = dragOffset
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                    shadowElevation = 14f
                                }
                            },
                        onToggle = { onToggle(note.id) },
                        onEdit = { onEdit(note) },
                        onSelect = { onSelect(note.id) },
                        onLongPress = { onLongPress(note.id) }
                    )
                }
            }
        }
    }
}

/**
 * 分区标题：一条短横线 + 文字 + 数量。
 *
 * 未完成用强调色，已完成用弱化色 —— 一眼就能看出「这条线以下都是做完的」。
 */
@Composable
private fun SectionHeader(row: ListRow.Section, modifier: Modifier = Modifier) {
    val color = if (row.done) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(row.labelRes),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = row.count.toString(),
            color = color.copy(alpha = 0.7f),
            fontSize = 11.sp
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = color.copy(alpha = 0.22f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
// 这里有两个 Modifier 参数：一个是整行的，一个是只给拖拽手柄的。
// lint 的 ModifierParameter 规则要求 Modifier 参数叫 modifier，但第二个是
// 手柄专用的手势修饰符，改成 modifier 反而看不出它挂在哪 —— 显式压制。
@Suppress("ModifierParameter")
private fun NoteRow(
    note: Note,
    isSelected: Boolean,
    selecting: Boolean,
    canReorder: Boolean,
    handleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onSelect: () -> Unit,
    onLongPress: () -> Unit
) {
    val textColor by animateColorAsState(
        targetValue = if (note.done) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface,
        label = "textColor"
    )

    // 卡片底色区分完成状态：
    // 未完成用 surfaceContainerLow（干净、贴近背景，视觉上「还没做完」），
    // 已完成用 surfaceContainerHighest（略深/略浅一档，视觉上「压下去了」）。
    val cardColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.secondaryContainer
            note.done -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "cardColor"
    )

    // 只有一种卡片渲染路径（玻璃模式已移除：模糊在性能一般的设备上不划算）。
    val rowContent: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 4.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selecting) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle
                    else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    modifier = Modifier
                        .size(44.dp)
                        .padding(11.dp)
                )
            } else {
                IconButton(onClick = onToggle, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = if (note.done) Icons.Default.CheckCircle
                        else Icons.Default.RadioButtonUnchecked,
                        contentDescription = stringResource(R.string.widget_toggle_done),
                        tint = if (note.done) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
                }
            }

            Text(
                text = note.text,
                color = textColor,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (note.done) TextDecoration.LineThrough else null,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )

            if (!selecting) {
                if (canReorder) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = stringResource(R.string.drag_handle),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .size(38.dp)
                            .padding(8.dp)
                            .alpha(0.7f)
                            // 拖拽手势挂在这里（只在这 38dp 的手柄上生效），
                            // 这样列表其他地方仍然可以正常上下滚动
                            .then(handleModifier)
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_note),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }

    // 按下时轻微缩小：M3 的状态层反馈，让点击有实感。
    // 用 spring 而不是 tween，松手回弹更自然，不会「一顿」。
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    Card(
        shape = RoundedCornerShape(CARD_RADIUS),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = when {
                isSelected -> 2.dp
                note.done -> 0.dp
                else -> 1.dp
            },
            pressedElevation = 1.dp
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { if (selecting) onSelect() else onEdit() },
                onLongClick = { onLongPress() }
            )
    ) {
        rowContent()
    }
}

// ------------------------------------------------------------------ 底栏

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            // 开了 edge-to-edge 后，内容会延伸到导航栏底下。
            // 输入框本身得避开导航条，否则会被系统导航条盖住
            // （模糊是整屏做的，不需要在这里额外处理）。
            .navigationBarsPadding()
            // 底栏透明，壁纸/磨砂从底下透上来
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(stringResource(R.string.input_hint)) },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = onSubmit,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
        }
    }
}

@Composable
private fun PaginationBar(
    page: Int,
    pages: Int,
    total: Int,
    pageSize: Int,
    onPage: (Int) -> Unit
) {
    val from = page * pageSize + 1
    val to = ((page + 1) * pageSize).coerceAtMost(total)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 只留很淡的一层，别盖住背后的磨砂效果
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { onPage(page - 1) }, enabled = page > 0) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.widget_prev_page)
            )
        }
        Text(
            text = stringResource(R.string.page_indicator, from, to, total, page + 1, pages),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = { onPage(page + 1) }, enabled = page < pages - 1) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.widget_next_page)
            )
        }
    }
}

/**
 * 空态。
 *
 * 以前只有一行灰字，看起来像「内容加载失败」而不是「这里本来就空」。
 * 加一个低饱和的图标之后，空态才读得出是「还没写」而不是「坏了」。
 */
@Composable
private fun EmptyState(text: String, searching: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (searching) Icons.Default.SearchOff else Icons.Default.EditNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ------------------------------------------------------------------ 对话框
@Composable
private fun EditDialog(
    note: Note,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var field by remember {
        // 打开就聚焦并把整段文字选中：改一条记事时直接打字就能整体替换，
        // 不用先手动全选。（之前注释写着「全选」，但实际只做了聚焦。）
        mutableStateOf(TextFieldValue(note.text, TextRange(0, note.text.length)))
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_note)) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                minLines = 3,
                shape = RoundedCornerShape(12.dp),
                // 弹窗里的输入框以前没有 IME 动作，改完只能伸手去点「保存」。
                // 现在动作键是「完成」，按下直接保存。
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(field.text) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(field.text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

// ------------------------------------------------------------------ 设置

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    vm: NotesViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 数值类状态用 mutableFloatStateOf / mutableIntStateOf：滑杆每帧都在改值，
    // 用 mutableStateOf(Float) 会每帧自动装箱一次。
    var widgetPageSize by remember { mutableFloatStateOf(Prefs.widgetPageSize(context).toFloat()) }
    var appPageSize by remember { mutableFloatStateOf(Prefs.appPageSize(context).toFloat()) }
    var showDone by remember { mutableStateOf(Prefs.widgetShowDone(context)) }
    var widgetTheme by remember { mutableStateOf(Prefs.widgetTheme(context)) }
    var appTheme by remember { mutableStateOf(Prefs.appTheme(context)) }
    var seed by remember { mutableIntStateOf(Prefs.widgetSeed(context)) }
    var groupDone by remember { mutableStateOf(Prefs.groupDone(context)) }

    // App 自己的配色（和小组件配色互相独立，改这边不会动到桌面上的小组件）
    var appColor by remember { mutableStateOf(Prefs.appColor(context)) }
    var appSeedColor by remember { mutableIntStateOf(Prefs.appSeed(context)) }

    // 本轮新增的外观项
    var bgOpacity by remember { mutableFloatStateOf(Prefs.widgetBgOpacity(context).toFloat()) }
    var barBlur by remember { mutableFloatStateOf(Prefs.backdropBlur(context).toFloat()) }
    var appBg by remember { mutableStateOf(Prefs.appBgImage(context)) }
    var widgetBg by remember { mutableStateOf(Prefs.widgetBgImage(context)) }

    var lang by remember { mutableStateOf(Prefs.appLang(context)) }

    // 选图用系统相册。用 rememberLauncherForActivityResult 而不是 startActivityForResult，
    // 免去 requestCode 管理和 onActivityResult 转发。
    val pickAppBg = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = ImageStore.importBackground(context, uri, forWidget = false)
            if (path != null) {
                appBg = path
                vm.setAppBgImage(path)
            } else {
                Toast.makeText(context, R.string.bg_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val pickWidgetBg = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = ImageStore.importBackground(context, uri, forWidget = true)
            if (path != null) {
                widgetBg = path
                vm.setWidgetBgImage(path)
            } else {
                Toast.makeText(context, R.string.bg_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // 设置项已经多到一屏放不下。**必须让内容可滚动**，
        // 否则超出部分会被裁掉、和底部叠在一起（之前「预览和下面的调节栏重叠」就是这个原因）。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))

            // ---- 自定义背景
            SettingsSection(stringResource(R.string.settings_background))

            BackgroundRow(
                label = stringResource(R.string.settings_app_bg),
                hasImage = appBg.isNotBlank() && ImageStore.exists(appBg),
                onPick = { pickAppBg.launch(arrayOf("image/*")) },
                onClear = {
                    ImageStore.clear(context, forWidget = false)
                    appBg = ""
                    vm.setAppBgImage("")
                }
            )

            Spacer(Modifier.height(6.dp))

            BackgroundRow(
                label = stringResource(R.string.settings_widget_bg),
                hasImage = widgetBg.isNotBlank() && ImageStore.exists(widgetBg),
                onPick = { pickWidgetBg.launch(arrayOf("image/*")) },
                onClear = {
                    ImageStore.clear(context, forWidget = true)
                    widgetBg = ""
                    vm.setWidgetBgImage("")
                }
            )

            // ---- 背景模糊强度
            Spacer(Modifier.height(10.dp))
            SettingLabel(
                stringResource(R.string.settings_blur),
                "${barBlur.toInt()}%"
            )
            Slider(
                value = barBlur,
                onValueChange = { barBlur = it },
                onValueChangeFinished = { vm.setBarBlur(barBlur.roundToInt()) },
                valueRange = 0f..100f,
                steps = 19
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // ---- 小组件每页条数
            SettingLabel(stringResource(R.string.settings_page_size_widget), widgetPageSize.toInt().toString())
            Slider(
                value = widgetPageSize,
                onValueChange = { widgetPageSize = it },
                onValueChangeFinished = { vm.setWidgetPageSize(widgetPageSize.roundToInt()) },
                valueRange = 1f..20f,
                steps = 18
            )

            // ---- 小组件是否显示已完成
            // 整行都能点：只有开关本体可点的话，命中区域太小，
            // 手指稍微偏一点就没反应（M3 的 ListItem 也是整行可点）。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        showDone = !showDone
                        vm.setWidgetShowDone(showDone)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings_widget_show_done),
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp
                )
                // onCheckedChange = null：点击交给整行处理，
                // 否则点开关会同时触发开关和行，等于连按两下、状态不翻转。
                Switch(checked = showDone, onCheckedChange = null)
            }

            // ---- 已完成是否沉底分组
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        groupDone = !groupDone
                        vm.setGroupDone(groupDone)
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.settings_group_done),
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp
                )
                Switch(checked = groupDone, onCheckedChange = null)
            }

            // ---- 小组件背景不透明度
            SettingLabel(
                stringResource(R.string.settings_widget_opacity),
                "${bgOpacity.toInt()}%"
            )
            Slider(
                value = bgOpacity,
                onValueChange = { bgOpacity = it },
                onValueChangeFinished = { vm.setWidgetBgOpacity(bgOpacity.roundToInt()) },
                valueRange = 0f..100f,
                steps = 19
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // ---- 小组件配色
            SettingsSection(stringResource(R.string.settings_widget_theme))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = widgetTheme == "system",
                    onClick = {
                        widgetTheme = "system"
                        vm.setWidgetTheme("system")
                    },
                    label = { Text(stringResource(R.string.settings_theme_system), fontSize = 12.sp) }
                )
                FilterChip(
                    selected = widgetTheme == "custom",
                    onClick = {
                        widgetTheme = "custom"
                        vm.setWidgetTheme("custom")
                    },
                    label = { Text(stringResource(R.string.settings_theme_custom), fontSize = 12.sp) }
                )
            }

            if (widgetTheme == "custom") {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_seed), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Palette.presets.take(8).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(androidx.compose.ui.graphics.Color(c), CircleShape)
                                .clickable {
                                    seed = c
                                    vm.setWidgetSeed(c)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (seed == c) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color(Palette.contrastOn(c)),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ---- 配色预览
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.settings_preview), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            val previewSeed = if (widgetTheme == "custom") seed else Palette.seed(context)
            val preview = Palette.schemeFrom(previewSeed, Palette.isDark(context))
            Card(
                colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color(preview.bg)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(preview.accent),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.settings_preview_done),
                            color = androidx.compose.ui.graphics.Color(preview.onBg),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color(preview.muted),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.settings_preview_todo),
                            color = androidx.compose.ui.graphics.Color(preview.onBg),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // ---- 应用内每页条数
            SettingLabel(stringResource(R.string.settings_page_size_app), appPageSize.toInt().toString())
            Slider(
                value = appPageSize,
                onValueChange = { appPageSize = it },
                onValueChangeFinished = { vm.setAppPageSize(appPageSize.roundToInt()) },
                valueRange = 5f..100f,
                steps = 18
            )

            // ---- 应用外观
            SettingsSection(stringResource(R.string.settings_app_theme))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "system" to stringResource(R.string.settings_theme_system_short),
                    "light" to stringResource(R.string.settings_theme_light),
                    "dark" to stringResource(R.string.settings_theme_dark)
                ).forEach { (v, label) ->
                    FilterChip(
                        selected = appTheme == v,
                        onClick = {
                            appTheme = v
                            vm.setAppTheme(v)
                        },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            // ---- 应用配色来源（跟随系统莫奈 / 自定义颜色）
            //
            // 和上面「小组件配色」是两套独立设置：改这里只影响 App 本身，
            // 桌面上的小组件仍按它自己的配色走（用户明确要求两者分开）。
            // 明暗和配色来源也互相独立，所以「深色 + 自定义绿」是能组合出来的。
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_app_color), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "system" to stringResource(R.string.settings_theme_system_short),
                    "custom" to stringResource(R.string.settings_theme_custom)
                ).forEach { (v, label) ->
                    FilterChip(
                        selected = appColor == v,
                        onClick = {
                            appColor = v
                            vm.setAppColor(v)
                        },
                        label = { Text(label, fontSize = 12.sp) }
                    )
                }
            }

            if (appColor == "custom") {
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.settings_seed), fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Palette.presets.take(8).forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(c), CircleShape)
                                .clickable {
                                    appSeedColor = c
                                    vm.setAppSeed(c)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (appSeedColor == c) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(Palette.contrastOn(c)),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ---- 语言
            Spacer(Modifier.height(16.dp))
            SettingsSection(stringResource(R.string.settings_language))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                L10n.TAGS.forEach { tag ->
                    FilterChip(
                        selected = lang == tag,
                        onClick = {
                            if (lang != tag) {
                                lang = tag
                                vm.setAppLang(tag)
                                // 语言是在 attachBaseContext 里套上去的，当前 Activity
                                // 已经带着旧语言建好了，只能重建一次才能整体换过来。
                                (context as? Activity)?.recreate()
                            }
                        },
                        label = { Text(L10n.label(context, tag), fontSize = 12.sp) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.settings_about_text),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 设置页的小节标题。
 *
 * M3 里这种分组标题用 labelLarge 那种小号字、主题色，比正文再大一号的
 * 15sp 黑字更能体现层级 —— 原来的写法所有文字一样重，整页看起来是平的。
 */
@Composable
private fun SettingsSection(title: String) {
    Spacer(Modifier.height(18.dp))
    Text(
        text = title,
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SettingLabel(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 一行「选择背景图 / 恢复默认」。
 *
 * 选图用系统的文档选择器（OpenDocument）而不是直接读相册权限：
 * 这样**不需要申请任何存储权限**，用户选中的那一个文件会被临时授权给我们，
 * 我们立刻把它复制进私有目录，之后就不再依赖那个授权。
 */
@Composable
private fun BackgroundRow(
    label: String,
    hasImage: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onPick) {
            Text(
                stringResource(
                    if (hasImage) R.string.bg_change else R.string.bg_pick
                ),
                fontSize = 13.sp
            )
        }
        if (hasImage) {
            TextButton(onClick = onClear) {
                Text(
                    stringResource(R.string.bg_clear),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
