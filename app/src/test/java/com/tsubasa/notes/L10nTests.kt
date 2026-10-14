package com.tsubasa.notes

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tsubasa.notes.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * 应用内语言切换（中文 / English / 日本語）。
 *
 * 重点验证三件事：
 * 1. [L10n.wrap] 真的换掉了 Locale（而不是返回了原 Context）；
 * 2. 三套 strings.xml 的**键集完全一致** —— 少一个键，那个语言下界面就会出现空白；
 * 3. 「跟随系统」这条路径必须原样返回，不能把系统语言也钉死。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class L10nTests {

    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        Prefs.setAppLang(ctx, L10n.SYSTEM)
    }

    // ------------------------------------------------------------ wrap

    @Test
    fun `选跟随系统时原样返回不包装`() {
        Prefs.setAppLang(ctx, L10n.SYSTEM)
        // 必须返回同一个对象：包装了就等于把系统语言钉死成当前这一种，
        // 用户在系统设置里换语言，App 不会跟着变。
        assertSame(ctx, L10n.wrap(ctx))
    }

    @Test
    fun `选中文时 Context 的 Locale 变成 zh`() {
        Prefs.setAppLang(ctx, L10n.ZH)
        val wrapped = L10n.wrap(ctx)
        assertEquals("zh", wrapped.resources.configuration.locales[0].language)
    }

    @Test
    fun `选英文时 Context 的 Locale 变成 en`() {
        Prefs.setAppLang(ctx, L10n.EN)
        val wrapped = L10n.wrap(ctx)
        assertEquals("en", wrapped.resources.configuration.locales[0].language)
    }

    @Test
    fun `选日语时 Context 的 Locale 变成 ja`() {
        Prefs.setAppLang(ctx, L10n.JA)
        val wrapped = L10n.wrap(ctx)
        assertEquals("ja", wrapped.resources.configuration.locales[0].language)
    }

    @Test
    fun `换语言后同一个字符串真的解析成不同文案`() {
        val zh = { Prefs.setAppLang(ctx, L10n.ZH); L10n.wrap(ctx).getString(R.string.settings_title) }
        val en = { Prefs.setAppLang(ctx, L10n.EN); L10n.wrap(ctx).getString(R.string.settings_title) }
        val ja = { Prefs.setAppLang(ctx, L10n.JA); L10n.wrap(ctx).getString(R.string.settings_title) }

        val zhText = zh()
        val enText = en()
        val jaText = ja()

        assertEquals("设置", zhText)
        assertEquals("Settings", enText)
        assertEquals("設定", jaText)
    }

    @Test
    fun `包装不会丢掉深色模式等其它配置`() {
        Prefs.setAppLang(ctx, L10n.JA)
        val wrapped = L10n.wrap(ctx)
        // 我们只改 locale，其它配置必须原样带过去
        assertEquals(
            ctx.resources.configuration.densityDpi,
            wrapped.resources.configuration.densityDpi
        )
    }

    // ------------------------------------------------------------ 语言选项名

    @Test
    fun `语言选项各自用自己的语言显示`() {
        // 这样用户哪怕误选了看不懂的语言，也还能认出该点哪个
        assertEquals("中文", L10n.label(ctx, L10n.ZH))
        assertEquals("English", L10n.label(ctx, L10n.EN))
        assertEquals("日本語", L10n.label(ctx, L10n.JA))
    }

    @Test
    fun `语言选项里包含跟随系统`() {
        assertTrue(L10n.TAGS.contains(L10n.SYSTEM))
        assertEquals(4, L10n.TAGS.size)
    }

    @Test
    fun `未知语言 tag 回退到跟随系统而不是崩溃`() {
        assertEquals(ctx.getString(R.string.settings_lang_system), L10n.label(ctx, "klingon"))
    }

    // ------------------------------------------------------------ Prefs

    @Test
    fun `语言偏好默认跟随系统`() {
        Prefs.setAppLang(ctx, L10n.SYSTEM)
        assertEquals(L10n.SYSTEM, Prefs.appLang(ctx))
    }

    @Test
    fun `语言偏好能存能读`() {
        Prefs.setAppLang(ctx, L10n.JA)
        assertEquals(L10n.JA, Prefs.appLang(ctx))
        Prefs.setAppLang(ctx, L10n.EN)
        assertEquals(L10n.EN, Prefs.appLang(ctx))
    }

    // ------------------------------------------------------------ App 名

    @Test
    fun `应用名中文是忆记事英文是 Tsubasa`() {
        Prefs.setAppLang(ctx, L10n.ZH)
        assertEquals("忆记事", L10n.wrap(ctx).getString(R.string.app_name))

        Prefs.setAppLang(ctx, L10n.EN)
        assertEquals("Tsubasa", L10n.wrap(ctx).getString(R.string.app_name))

        Prefs.setAppLang(ctx, L10n.JA)
        assertEquals("Tsubasa", L10n.wrap(ctx).getString(R.string.app_name))
    }

    @Test
    fun `旧名字莫奈记事已经不在任何语言里`() {
        L10n.TAGS.forEach { tag ->
            Prefs.setAppLang(ctx, tag)
            val name = L10n.wrap(ctx).getString(R.string.app_name)
            assertNotEquals("旧名不该残留", "莫奈记事", name)
        }
    }

    // ------------------------------------------------------------ 资源完整性

    /** 读一份 strings.xml 里定义的所有 name。 */
    private fun keysOf(path: String): Set<String> {
        val f = File(path)
        if (!f.exists()) return emptySet()
        return Regex("""name="([^"]+)"""")
            .findAll(f.readText())
            .map { it.groupValues[1] }
            .toSet()
    }

    @Test
    fun `三套字符串资源的键集完全一致`() {
        val base = keysOf("src/main/res/values/strings.xml")
        assertTrue("默认资源应当存在且非空", base.isNotEmpty())

        listOf("values-en", "values-ja").forEach { dir ->
            val other = keysOf("src/main/res/$dir/strings.xml")
            val missing = base - other
            val extra = other - base
            assertEquals("$dir 缺少这些键: $missing", emptySet<String>(), missing)
            assertEquals("$dir 多出这些键: $extra", emptySet<String>(), extra)
        }
    }

    @Test
    fun `英文资源里没有漏翻的中文`() {
        // 漏翻的典型症状：某一项还是中文。中日共用汉字，所以只查英文那一套。
        //
        // 例外：语言选项名是**故意**用各自语言写的（中文 / English / 日本語），
        // 这样用户哪怕误选了看不懂的语言，也还能认出该点哪个。
        val intended = setOf("lang_zh", "lang_ja")

        val en = File("src/main/res/values-en/strings.xml").readText()
        val han = Regex("[\\u4e00-\\u9fff]")
        val offenders = en.lines()
            .filter { it.contains("<string") }
            .filter { line -> intended.none { line.contains("name=\"$it\"") } }
            .filter { han.containsMatchIn(it) }
        assertEquals("英文资源里不该有汉字: $offenders", emptyList<String>(), offenders)
    }

    // ------------------------------------------------------------ 真实 Activity

    /**
     * 端到端验证 [MainActivity.attachBaseContext] 真的接上了。
     *
     * 上面那些 [L10n.wrap] 的单测证明「wrap 本身是对的」，但证明不了
     * 「Activity 真的调了它」—— 少写这一行 override，界面语言就永远不生效，
     * 而其它测试全绿。所以这里真的把 Activity 建起来，读它自己的资源。
     */
    @Test
    fun `Activity 建起来之后读到的就是选定的语言`() {
        Prefs.setAppLang(ctx, L10n.EN)

        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertEquals("en", activity.resources.configuration.locales[0].language)
            assertEquals("Settings", activity.getString(R.string.settings_title))
        } finally {
            controller.close()
        }
    }

    @Test
    fun `同一个 Activity 在换语言并重建后跟着换过来`() {
        Prefs.setAppLang(ctx, L10n.ZH)

        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            assertEquals("设置", controller.get().getString(R.string.settings_title))

            // 用户在设置里选了日语，然后界面调 recreate()
            Prefs.setAppLang(ctx, L10n.JA)
            controller.recreate()

            assertEquals("設定", controller.get().getString(R.string.settings_title))
        } finally {
            controller.close()
        }
    }
}
