package com.tsubasa.notes

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tsubasa.notes.ui.TsubasaTheme
import com.tsubasa.notes.ui.NotesApp
import com.tsubasa.notes.ui.NotesViewModel
import com.tsubasa.notes.ui.background.blurRadiusDp
import com.tsubasa.notes.ui.background.rememberAppBackdrop

class MainActivity : ComponentActivity() {

    private val vm: NotesViewModel by viewModels()

    /**
     * 应用用户选定的界面语言。
     *
     * 在 attachBaseContext 里换 Context 是唯一「早到」的时机 —— 它比 onCreate
     * 还早，此时换掉之后，本次 Activity 里所有 getString / stringResource
     * 拿到的就已经是目标语言了。放到 onCreate 里就太晚了。
     *
     * 改语言后由设置界面调 recreate()，走一遍这个方法重新取资源。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(L10n.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前调用。
        //
        // 这一步是修「顶部黑边 + 状态栏图标看不见」的关键：
        // 开启 edge-to-edge 后，Compose 的内容会一直铺到状态栏底下，
        // 状态栏区域显示的就不再是窗口默认的黑色，而是我们自己的背景。
        // 不这么做的话，窗口背景会露出来 -> 黑边，
        // 而浅色模式下状态栏图标是深色，深色画在黑底上就等于看不见。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // 明暗、配色来源、自定义种子色都从 state 读并传进主题。
            //
            // 不能像以前那样让 TsubasaTheme 自己去读 Prefs：Prefs 不是 Compose State，
            // 读它建立不了订阅，在设置里改了颜色主题不会重组 —— 那就成了「选了没反应」。
            // 走 state 之后，配色改动会立刻反映到界面上，也不需要 recreate()。
            val state by vm.state.collectAsState()
            TsubasaTheme(
                appTheme = state.appTheme,
                appColor = state.appColor,
                appSeed = state.appSeed
            ) {
                // 背景铺满整屏，内容叠在它上面。
                //
                // 背景图路径同样来自 state（而不是直接读 Prefs）：
                // Prefs 不是 Compose State，直接读的话换了图界面不会刷新。
                val backdrop = rememberAppBackdrop(
                    path = state.appBgImage,
                    baseColor = MaterialTheme.colorScheme.background
                )

                // 模糊在**整屏**上做一次，所有栏共用这一层。
                //
                // 为什么整屏做而不是让每个栏各糊各的：
                // 1. 栏只覆盖自己那一条，**中间的列表区域就没被糊** ——
                //    这正是之前「顶面模糊、中间和底面没糊」的原因；
                // 2. 每个栏各自模糊要开 N 个离屏层，整屏只开 1 个，反而更省。
                //
                // 整屏模糊后，上面的内容全部叠在它上面，所以从状态栏到导航栏
                // 是**同一层连续的磨砂**，视觉上才统一。
                val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                val blurDp = with(LocalDensity.current) { blurRadiusDp(state.barBlur).dp }
                val blurEnabled = state.barBlur > 0 && canBlur

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (blurEnabled) {
                        // 模糊层：壁纸先被模糊，再被下面的内容盖住不需要糊的部分
                        Box(
                            Modifier
                                .fillMaxSize()
                                .blur(blurDp, BlurredEdgeTreatment.Unbounded)
                        ) {
                            backdrop(Modifier.fillMaxSize())
                        }
                    } else {
                        // 0% 时完全不创建模糊层，壁纸原样显示，零额外开销
                        backdrop(Modifier.fillMaxSize())
                    }

                    // 内容层：栏透明，壁纸/模糊从底下透上来
                    NotesApp(vm)
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** 小组件点「+」进来时，直接聚焦输入框。 */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_NEW_NOTE, false) == true) {
            vm.requestNewNoteFocus()
            intent.removeExtra(EXTRA_NEW_NOTE)
        }
    }

    companion object {
        const val EXTRA_NEW_NOTE = "extra_new_note"
    }
}
