package com.tsubasa.notes

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 编码损坏守卫。
 *
 * ## 为什么需要这个
 *
 * 开发过程中有一次用 PowerShell 的 `Set-Content` 写文件，它把 UTF-8 当成 GBK 处理，
 * 导致中文变成乱码（「跟随系统」变成「璺熼殢绯荤粺」）。更麻烦的是：
 *
 * - **编译不会报错**（乱码在语法上是合法的字符串），
 * - **普通测试也不会失败**（它们不检查文案），
 * - 只有用户肉眼看到界面上那串怪字才发现。
 *
 * 所以这里直接扫源码，把这类损坏变成**构建期就能发现**的问题。
 *
 * 检测两类特征：
 * 1. 已知的 GBK 误解码产物字符（「锛」「鐨」「璺」「熼」等）；
 * 2. 私用区字符（U+E000–U+F8FF）—— 正常中文文案绝不会包含它们。
 */
class EncodingGuardTests {

    /**
     * GBK 误解码 UTF-8 的典型产物。
     *
     * 注意这里是**逐字拆分**后拼出来的，故意不写成连续字符串 ——
     * 否则本文件自己会被下面的扫描判为「含乱码」（踩过这个坑）。
     * 同时要小心别把正常汉字混进来：比如「储」是「存储」的正常字，
     * 放进来会误报。
     */
    private val knownBad: Set<Char> = buildSet {
        // 「锛」「鐨」这类是 UTF-8 被当 GBK 解码的典型产物
        addAll(listOf('锛', '鐨', '鏂', '鑾', '涓', '灏', '鍒', '璁', '閫', '鏄', '鍙', '鎴', '瀹', '浣', '鎵'))
        // 「鎼滅储」= 搜索；「璺熼殢绯荤粺」= 跟随系统
        addAll(listOf('鎼', '滅', '璺', '熼', '殢', '绯', '荤', '粺'))
        // 「璽熛殫葷粃」= 莫奈取色 一类
        addAll(listOf('璽', '熛', '殫', '葷', '粃'))
    }

    /** 本文件自己会包含这些字（作为检测表），扫描时跳过它。 */
    private val selfFile = "EncodingGuardTests.kt"

    private fun isPrivateUse(ch: Char): Boolean = ch.code in 0xE000..0xF8FF

    /** 收集一个文件里所有可疑字符。 */
    private fun suspiciousChars(text: String): List<Char> =
        text.toList().filter { it in knownBad || isPrivateUse(it) }

    private fun sourceRoot(): File = File("src")

    @Test
    fun `源码里没有编码损坏的中文`() {
        val root = sourceRoot()
        if (!root.exists()) return // 不在模块目录下运行时跳过

        val problems = mutableListOf<String>()

        root.walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
            // 跳过本文件：它的检测字表本身就包含这些字符
            .filter { it.name != selfFile }
            .forEach { file ->
                val text = try {
                    file.readText()
                } catch (t: Throwable) {
                    return@forEach
                }
                text.lines().forEachIndexed { idx, line ->
                    val bad = suspiciousChars(line)
                    if (bad.isNotEmpty()) {
                        problems += "${file.name}:${idx + 1} 含可疑字符 ${bad.distinct()} -> ${line.trim().take(80)}"
                    }
                }
            }

        assertTrue(
            "发现编码损坏（很可能是用 Set-Content 之类的工具写文件导致的）：\n" +
                problems.joinToString("\n"),
            problems.isEmpty()
        )
    }

    @Test
    fun `字符串资源里的中文都是可读的`() {
        val strings = File("src/main/res/values/strings.xml")
        if (!strings.exists()) return

        val bad = suspiciousChars(strings.readText())

        assertTrue(
            "strings.xml 里有编码损坏的字符: ${bad.distinct()}",
            bad.isEmpty()
        )
    }

    @Test
    fun `没有硬编码的乱码文案漏在界面代码里`() {
        // 界面文案应该走 strings.xml；硬编码的中文一旦损坏就特别难发现
        // （之前「跟随系统」和「搜索」就是这么坏掉的）
        val app = File("src/main/java/com/tsubasa/notes/ui/NotesApp.kt")
        if (!app.exists()) return

        val bad = suspiciousChars(app.readText())
        assertTrue(
            "NotesApp.kt 里有编码损坏的字符: ${bad.distinct()}",
            bad.isEmpty()
        )
    }

    @Test
    fun `检测逻辑本身是有效的`() {
        // 防止「守卫测试因为字表写错而永远通过」这种情况
        val corrupted = "璺熼殢绯荤粺"
        assertTrue(
            "检测逻辑失效了：它认不出已知的乱码样本",
            suspiciousChars(corrupted).isNotEmpty()
        )
        // 正常中文不该被误报（含「储」这个曾经误报过的字）
        assertTrue(
            "误报：正常中文被当成乱码了",
            suspiciousChars("跟随系统（莫奈取色）存储权限").isEmpty()
        )
    }
}
