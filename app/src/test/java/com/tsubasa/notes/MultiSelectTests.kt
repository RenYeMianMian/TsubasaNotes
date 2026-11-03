package com.tsubasa.notes

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tsubasa.notes.data.Note
import com.tsubasa.notes.data.Prefs
import com.tsubasa.notes.data.Repo
import com.tsubasa.notes.ui.Filter
import com.tsubasa.notes.ui.NotesViewModel
import com.tsubasa.notes.ui.UiState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * 多选与分页。
 *
 * 这两块以前完全没有测试覆盖，而它们恰好是「越用越怪」的高发区：
 * 批量删除后页码停在空页上、全选把其他页的条目也选进来、
 * 撤销之后选择态没清干净 —— 这些都不会崩，只会让人以为软件坏了。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiSelectTests {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        Repo.file(ctx).delete()
        Prefs.setAppPageSize(ctx, 5)
        Prefs.setGroupDone(ctx, false)
    }

    @After
    fun tearDown() {
        Repo.file(ctx).delete()
    }

    private fun newVm(): NotesViewModel = NotesViewModel(
        ApplicationProvider.getApplicationContext<Application>()
    )

    /** 等 refresh() 落地，见 AppColorTests 里的同名辅助。 */
    private fun await(vm: NotesViewModel, until: (UiState) -> Boolean = { !it.loading }): UiState {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper()
            val s = vm.state.value
            if (until(s)) return s
            Thread.sleep(5)
        }
        throw AssertionError("等待 ViewModel 状态超时：${vm.state.value}")
    }

    private fun seed(count: Int, prefix: String = "记事"): List<Note> {
        repeat(count) { Repo.add(ctx, "$prefix$it") }
        return Repo.all(ctx)
    }

    // ------------------------------------------------------------ 多选

    @Test
    fun `勾选和取消勾选都会更新选择态`() {
        val notes = seed(3)
        val vm = newVm()
        await(vm)

        vm.toggleSelect(notes[0].id)
        assertEquals(setOf(notes[0].id), vm.state.value.selected)
        assertTrue("选中之后应当进入多选模式", vm.state.value.selecting)

        vm.toggleSelect(notes[0].id)
        assertEquals(emptySet<String>(), vm.state.value.selected)
        assertFalse("取消最后一个选中项应当退出多选模式", vm.state.value.selecting)
    }

    @Test
    fun `全选只选当前页而不是全部`() {
        // 每页 5 条、共 7 条：全选必须只覆盖第 1 页的 5 条。
        // 选到第 2 页去，用户点一次删除就会删掉屏幕上根本没看到的东西。
        val notes = seed(7)
        val vm = newVm()
        await(vm)

        vm.selectAll()
        val s = vm.state.value
        assertEquals(5, s.selected.size)
        assertEquals(notes.take(5).map { it.id }.toSet(), s.selected)
    }

    @Test
    fun `批量删除只删选中的并清空选择`() {
        val notes = seed(4)
        val vm = newVm()
        await(vm)

        vm.toggleSelect(notes[0].id)
        vm.toggleSelect(notes[2].id)
        vm.deleteSelected()

        await(vm) { !it.loading && it.notes.size == 2 }
        val remaining = Repo.all(ctx).map { it.text }
        assertEquals(listOf("记事1", "记事3"), remaining)
        assertFalse("删除后必须退出多选模式", vm.state.value.selecting)
        assertEquals(emptySet<String>(), vm.state.value.selected)
    }

    @Test
    fun `批量标记完成与批量取消完成`() {
        val notes = seed(3)
        val vm = newVm()
        await(vm)

        vm.toggleSelect(notes[0].id)
        vm.toggleSelect(notes[1].id)
        vm.setDoneSelected(true)
        await(vm) { !it.loading && it.notes.count { n -> n.done } == 2 }

        vm.toggleSelect(notes[0].id)
        vm.toggleSelect(notes[1].id)
        vm.setDoneSelected(false)
        await(vm) { !it.loading && it.notes.none { n -> n.done } }

        assertTrue("全部取消完成后不应再有已完成项", Repo.all(ctx).none { it.done })
    }

    @Test
    fun `删除后撤销能把条目放回原位`() {
        val notes = seed(4)
        val vm = newVm()
        await(vm)

        val snapshot = vm.notesSnapshot()
        vm.toggleSelect(notes[1].id)
        vm.deleteSelected()
        await(vm) { !it.loading && it.notes.size == 3 }

        vm.restore(snapshot)
        await(vm) { !it.loading && it.notes.size == 4 }
        assertEquals(notes.map { it.text }, vm.state.value.notes.map { it.text })
    }

    @Test
    fun `清除已完成会留下未完成项`() {
        val notes = seed(3)
        val vm = newVm()
        await(vm)

        vm.toggleSelect(notes[0].id)
        vm.setDoneSelected(true)
        await(vm) { !it.loading && it.notes.any { n -> n.done } }

        vm.clearDone()
        await(vm) { !it.loading && it.notes.none { n -> n.done } }
        assertEquals(2, Repo.all(ctx).size)
    }

    // ------------------------------------------------------------ 分页

    @Test
    fun `删到只剩一页时页码会回到第一页`() {
        val notes = seed(6)
        val vm = newVm()
        await(vm)

        vm.setPage(1)
        assertEquals(1, vm.state.value.page)

        // 删掉第 1 页之外的所有条目后，第 2 页已经不存在了
        notes.drop(1).forEach { vm.toggleSelect(it.id) }
        vm.deleteSelected()

        await(vm) { !it.loading && it.notes.size == 1 }
        assertEquals("数据缩水后页码必须夹回有效范围", 0, vm.state.value.page)
    }

    @Test
    fun `setPage 会夹在有效范围内`() {
        seed(3)
        val vm = newVm()
        await(vm)

        vm.setPage(99)
        assertEquals("只有一页时任何页码都应落在第 0 页", 0, vm.state.value.page)
        vm.setPage(-5)
        assertEquals(0, vm.state.value.page)
    }

    @Test
    fun `换筛选和换搜索都会回到第一页`() {
        seed(12)
        val vm = newVm()
        await(vm)

        vm.setPage(2)
        assertEquals(2, vm.state.value.page)

        vm.setFilter(Filter.TODO)
        assertEquals(0, vm.state.value.page)

        vm.setPage(2)
        vm.setQuery("记事1")
        assertEquals(0, vm.state.value.page)
    }

    @Test
    fun `搜索没有命中时列表为空但页码仍有效`() {
        seed(3)
        val vm = newVm()
        await(vm)

        vm.setQuery("不可能出现的文字")
        val s = vm.state.value
        assertTrue("没有命中时应当没有可见条目", vm.pageItems(s).isEmpty())
        assertTrue("空结果也必须有至少一页，否则页码会算成 0", vm.pageCount(s) >= 1)
        assertEquals(0, s.page)
    }
}
