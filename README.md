# 忆记事 · Tsubasa

一个**以桌面小组件为中心**的安卓记事应用：在桌面上直接查看、勾选、排序记事，配色跟随壁纸（莫奈取色）。

> 应用名：中文 **忆记事** / 英文 **Tsubasa** / 日语 **Tsubasa**；包名 `com.tsubasa.notes`。
>
> 仓库：<https://github.com/RenYeMianMian/TsubasaNotes>（MIT License）

## 为什么这样设计

现有开源项目里，同时做到「桌面小组件 + 莫奈取色 + 多条目列表 + 拖拽排序 + 完成态」的**一个都没有**。
最接近的 `obsidian-todo-widget`（173★）只有列表和勾选、2023 年后停更；2025–2026 的新项目 star 基本为 0。
尤其 **拖拽排序在整个赛道是空白** —— 因为平台层面就不允许在小组件里拖拽（下面详述）。

所以这个 App 的取舍很明确：**小组件是主界面，App 是它的完整版**。

---

## 功能

### 桌面小组件（核心）

| 能力 | 说明 |
|---|---|
| 多条目列表 | 一屏显示多条记事，可滚动 |
| 标题显示待办数 | 标题栏显示「还有 N 项待办」，而不是 App 名字；全部完成时显示「都做完了 🎉」 |
| 圆角卡片 | 每条一张卡片，卡片之间留间距，不再挤成一团 |
| 颜色区分 | 未完成 = 白/亮卡片，已完成 = 灰/暗卡片，一眼分辨 |
| 已完成分界线 | 未完成和已完成之间有一条「已完成」标题行；未完成那段不插标题以省空间 |
| 逐条勾选 | 点左侧圆圈或文字即可切换完成状态，**不用打开 App** |
| 已完成划线 | 完成项文字变淡并加删除线 |
| 上移 | 每条右侧「↑」按钮，在小组件里直接调整顺序 |
| 删除 | 每条右侧垃圾桶 |
| 翻页 | 头部 `‹ 1/3 ›` 循环翻页，**每页条数可调（1–20）**；只有一页时页码和翻页按钮自动隐藏 |
| 快速新建 | 头部「+」直接跳到输入框 |
| 刷新 | 手动重读数据 |
| 莫奈取色 | 见下 |

另外还有一个**「快速添加」小组件**（2×1），点一下直接新建记事。

### 莫奈取色

- **Android 12+**：从系统调色板 `android.R.color.system_accent1_500` 取种子色，**真正跟随壁纸变化**；
- **自定义**：也可以自己在设置里选 8 个预设色之一；
- 由种子色推导出一整套容器色 / 文字色 / 强调色 / 分隔线色 / 卡片底色，用 HSL 计算，保证深浅色模式下对比度都够；
- 换壁纸、切深浅色、开机、升级后自动重算（`ThemeChangeReceiver`）；
- App 内默认同样用 Material 3 的 `dynamicLightColorScheme` / `dynamicDarkColorScheme`，与小组件观感一致。

**App 和小组件的配色是两套独立设置**：设置里「应用配色」只改 App，「小组件配色」只改桌面组件，
互不影响（用户明确要求分开）。所以可以做到「App 用紫色、小组件跟随壁纸」这种组合。

明暗（浅色 / 深色）和「用哪个颜色」也是两个正交的偏好键（`APP_THEME` / `APP_COLOR`），
因此「深色 + 自定义绿」是能组合出来的 —— 如果把 `custom` 塞进 `APP_THEME` 当第四个取值，
就只能二选一了。

> 这里踩过一个坑：`TsubasaTheme` 原来在 `SDK_INT >= S` 时**无条件**用动态取色，
> 完全忽略用户选的种子色，于是「自定义颜色」在 Android 12+ 上点了没反应。
> 现在优先级抽成了纯函数 `schemeSource(appColor, sdkInt)`（自定义 > 动态 > 推导），
> 并由 `AppColorTests` 覆盖「Android 12+ 上自定义必须赢」这个正是出过问题的组合。

### App 内

- 新增 / 编辑 / 删除 / 勾选
- 三个筛选页签：全部 / 未完成 / 已完成
- 搜索
- **圆角卡片 + 间距 + 完成状态配色**（和小组件观感一致）
- **「未完成 / 已完成」分区标题**，未完成在前
- **拖拽排序**（长按右侧手柄图标上下拖动）
- 分页，**每页条数可调（5–100）**
- 多选批量操作（长按任意条目进入）：批量完成 / 取消完成 / 删除
- 「已完成沉底」一键整理
- 浅色 / 深色 / 跟随系统
- **应用配色：跟随系统莫奈 / 自定义颜色**（8 个预设色，只影响 App）
- **界面语言：中文 / English / 日本語 / 跟随系统**（见下）
- **自定义软件背景图**（整屏壁纸，含状态栏区域）
- **整屏统一模糊** 0–100%（状态栏、顶栏、列表、底栏是同一层磨砂），0% 时零开销
- 状态栏与背景统一，图标颜色跟随明暗自动切换

### 界面语言

设置里可选 **跟随系统 / 中文 / English / 日本語**，切换后界面立刻重建生效，
**小组件也跟着换**（不只是 App 内）。

实现上有两个坑，都值得记一笔：

1. **不能用 `AppCompatDelegate.setApplicationLocales()`**。
   它是官方推荐的按应用设置语言的 API，但 `MainActivity` 继承的是 `ComponentActivity`
   而不是 `AppCompatActivity`，**API < 33 的兼容回退逻辑压根不会挂上**
   （那套回退是 AppCompat 自己实现的）。所以改为自研：语言存在 `Prefs` 里，
   `MainActivity.attachBaseContext(newBase)` 里用 `createConfigurationContext` 套上 locale ——
   这是**唯一比 `onCreate` 还早**的时机。切换后调 `activity.recreate()` 让界面重建。

2. **不加 `android:localeConfig`**。
   加了之后 Android 13+ 的「设置 → 应用 → 语言」会和 App 自己的偏好抢同一个设置：
   系统那边改完，`attachBaseContext` 又把它覆盖回去，表现就是「改了没用」。

小组件侧也要单独套（否则小组件跟随的是**系统**语言而不是 App 内选的语言）：
`NoteWidgetProvider.onReceive`（所有回调都从这里分发，套一次全覆盖）、
`NoteWidgetService.onGetViewFactory`、`AddNoteWidgetProvider.onUpdate`、
`ThemeChangeReceiver.onReceive` 都过一层 `L10n.wrap(context)`。

还有一个更隐蔽的点：布局里 `android:text="@string/..."` 这种**烘焙在 XML 里的文案**，
是**桌面进程**（宿主）按**系统语言**解析的，我们的 locale 包装影响不到它。
所以空状态提示、无障碍 `contentDescription` 这些，改成在 `WidgetRenderer` 里
用 `setTextViewText` / `setContentDescription` 主动覆盖一份。

> 顺带：分区标题（未完成 / 已完成）原本是 `NotesViewModel` 里的中文字面量常量，
> 现在改成 `@StringRes` id，由界面层 `stringResource()` 解析 ——
> ViewModel 是纯逻辑、拿不到 Context，这样才既能本地化又不污染分层。

### 已完成分区的行为细节

分组是**展示层的变换，不改动你手动排的顺序**：

- 打开分组时：未完成按你的顺序排在前面，已完成按你的顺序沉到「已完成」标题下方；
- 关掉分组（设置里可关）：立刻回到你手动排的原始顺序；
- 拖拽排序在分组开启时**限制在同一分区内** —— 拖过分割线会被拦住。
  这是刻意的：拖过去也会被分组立刻弹回原位，手感上像「拖不动」，不如直接拦住。
  想把某条从未完成变已完成，点左侧圆圈就行。

**App 与小组件在分界线上的区别**（有意为之）：

| | 未完成标题 | 已完成标题 |
|---|---|---|
| App | 显示 | 显示 |
| 小组件 | **不显示**（省一行空间） | 显示 |

小组件屏幕空间紧张，而未完成永远是列表开头那一段，用户一眼就知道，
所以省掉那一行把空间让给记事。只有「两种状态同时存在」时才插「已完成」标题；
清一色已完成时也不插（整页都是已完成，再顶个标题等于没说）。

### App 图标

图标是一只**黑白花猫头**，纯线条与色块、不使用任何渐变，资源包来自
`C:\Users\H\Downloads\cat-icon-android.zip`（原始资源 `C:\Users\H\Downloads\dsh-cat-icon`）。

| 参数 | 值 |
|---|---|
| 主墨色 INK | `#141414` |
| 背景色 BG | `#FFF6EA`（奶油底） |
| 描边宽度 | 5.5（108 视口单位） |
| 视口 | 108 × 108 |

三处细节值得记下来，因为它们都**编译期不报错、只在手机上看得出问题**：

1. **安全区缩放 0.80**。自适应图标是 108dp 画布，但系统只保证中央 72dp 可见，
   最苛刻的圆形遮罩半径只有 36dp。猫形含描边最大半径是 43.61dp（左耳尖），
   会被圆角遮罩切掉耳朵，所以前景层统一缩放到 0.80 → 最大半径 34.9dp，留约 1.1dp 余量。
   这个缩放必须**同时**写在 `ic_launcher_foreground.xml`、`ic_launcher_monochrome.xml`
   的 `<group android:scaleX/Y="0.80" android:pivotX/Y="54">` 和生成 PNG 的脚本里，三处要一致。

2. **单色层是「实心剪影 + 镂空五官」**，不是把前景层整体染黑。
   前景层里脸是白色实心，直接当单色层用会在 Android 13+ 的主题图标下糊成一个**黑饼**。
   正确做法是一条合并的头部轮廓路径（含双耳）配 `android:fillType="evenOdd"`，
   把双眼、鼻子、嘴、四根胡须作为子路径挖空。

3. **`clip-path` 必须是 `<clip-path>` 子元素**，见下面的踩坑表 —— 资源包这里原本是错的。

`AndroidManifest.xml` 同时声明了 `android:icon` 和 `android:roundIcon`，
并保留了 mdpi–xxxhdpi 五档 PNG 兜底（桌面、设置页、应用商店仍会直接取 PNG）。

> `IconTests` 用 Robolectric 的 **NATIVE 图形模式**把矢量真的光栅化，然后做像素级断言：
> 墨色面积占比、所有墨色像素到圆心的最大距离（安全区）、单色层的镂空面积、
> 右耳尖黑尖那个具体像素是不是墨色。这些断言都做过**反向验证** ——
> 故意把缩放改回 1.0、把裁切范围换成退化路径，测试确实会挂。
>
> 注意：Robolectric 默认的 legacy 模式下 `VectorDrawable.draw()` 是个**空实现**，
> 渲染出来一片空白，什么也测不出来，必须加 `@GraphicsMode(GraphicsMode.Mode.NATIVE)`。

---

## 自定义背景

- **软件背景**：可以用自己的图片替换默认的主题色纯色；
- **小组件背景**：桌面小组件也能用自定义图片；
- 选图走系统的文档选择器（`OpenDocument`），**不需要任何存储权限**；
- 选中后会**复制进应用私有目录**并压缩，而不是长期持有相册 Uri：
  相册 Uri 只是临时授权，重启后可能失效；而且小组件是另一个进程，读相册 Uri 拿不到权限；
- 图片按目标尺寸采样解码（不是整张读进内存），避免大图 OOM；
- 小组件背景图还要额外压缩后再传给 RemoteViews —— 跨进程 Binder 事务有约 1MB 上限，
  传原图会抛 `TransactionTooLargeException`；
- 文件丢失（清数据、换机恢复）时自动退回默认背景，不会崩。

### 背景是「一整块壁纸」

壁纸铺在**整屏最底层**（`MainActivity` 里，内容之前），所以它会一直延伸到
状态栏和导航栏底下，看起来就是完整一张壁纸 —— 不是只有中间一块。

**背景图路径必须从可观察状态传进来**（`UiState.appBgImage`），不能让界面直接读
`Prefs`：SharedPreferences 不是 Compose State，直接读的话换了图界面不会重组，
表现就是「选了新图但背景不变」。这个坑踩过一次，现在有回归测试盯着。

---

## 背景模糊（整屏统一）

设置里可调 **0–100%**，默认 55%。

**状态栏、顶栏、中间列表、底栏是同一层磨砂** —— 模糊在整屏上做一次，
所有栏本身透明，壁纸/磨砂从底下透上来。

### 为什么是整屏做，而不是每个栏各糊各的

最初是让每个栏在自己的区域里重画背景并模糊。这个做法有个天然缺陷：
**栏只覆盖自己那一条，中间的列表区域根本没被糊到** —— 表现就是
「顶面糊了、中间和底面没糊」。而且每个栏各开一个离屏渲染层，反而更费。

改成整屏做一次之后：

- 从上到下是**同一层连续的磨砂**，视觉统一；
- 离屏层从 N 个降到 1 个，更省。

### 性能

- **调到 0% 时完全不创建模糊层**，壁纸原样显示，零额外开销 ——
  性能吃紧的设备直接拉到 0 即可；
- 模糊半径上限 28dp：再大离屏渲染开销增长很快，而视觉收益在 20dp 后基本饱和；
- API < 31 上 `blur` 是静默忽略的，会自动退化成「只有壁纸」。

---

## 小组件背景不透明度

设置里可调 0–100%，实时生效。

- 调低能看到桌面壁纸，配合自定义背景图效果更明显；
- **下限不是 0 而是 12%**：全透明的小组件在桌面上等于「看不见」，
  用户会以为组件坏了。保留一点底，让它始终可辨认、可点；
- 实现上设的是整个背景层的 `alpha`（`setFloat(..., "setAlpha", ...)`），
  而不是给颜色乘 alpha —— 后者在自定义背景图（Bitmap 自带 alpha）上不起作用。

---

## 状态栏

之前顶部有一条黑边、状态栏图标看不见，原因和修法：

| | 问题 | 修法 |
|---|---|---|
| 黑边 | `windowBackground` 是**透明的**，而窗口没铺满系统栏 → 状态栏区域背后什么都没有，显示成黑色 | 给一个真实颜色（浅色/深色两套） |
| 图标看不见 | 浅色模式下状态栏图标被设成**深色**，深色画在黑底上 = 看不见 | 开启 edge-to-edge，内容铺到状态栏底下 |
| 背景不统一 | 状态栏区域露出的是窗口背景，不是 App 配色 | 同上，状态栏区域显示的就是我们自己的背景 |

实现：`enableEdgeToEdge()`（必须在 `super.onCreate` 之前调用）+ 不再手动设
`window.statusBarColor`（开了 edge-to-edge 后它本来就是透明的，
手动设反而在某些 ROM 上会退化成黑色），只通过
`WindowCompat.getInsetsController(...).isAppearanceLightStatusBars` 控制图标明暗。

---

## 技术选型与关键决策

### 小组件为什么用 RemoteViews 而不是 Jetpack Glance

「可交互的列表型小组件」正好命中平台原生机制：
`RemoteViewsService` + `RemoteViewsFactory` + `PendingIntent` 模板。
Glance 本质是这套东西的封装，**继承了全部限制却多了一层抽象**，对集合型小组件并不划算。
（`IT-BAER/hado` 等新项目用 Glance，是因为它们的列表是静态展示，不需要逐条交互。）

### 拖拽排序为什么不在小组件里做

**这是平台限制，换任何框架都解决不了。**

桌面小组件根本不在 App 进程里运行：`RemoteViews` 是个 `Parcelable`（一份「界面说明书」），
App 构造好交给桌面，真正把界面画出来、接收手指触摸的是**桌面的进程**（`AppWidgetHostView`）。

把两边的 API 翻出来数一遍就很清楚：

| | 触摸 / 手势 / 拖拽 API |
|---|---|
| `RemoteViews` | **0 个**（只有 `setOnClickPendingIntent` 这类点击） |
| `Glance`（Google 官方新框架） | **0 个**（modifier 只有 Alignment / Background / CornerRadius / ClipToOutline / Enabled / SelectableGroup） |
| 普通 `View` | 5 个（`onTouchEvent`、`setOnTouchListener`、`setOnDragListener`、`startDragAndDrop`、`setOnLongClickListener`） |

原因是拖拽需要**连续的触摸事件流**（按下 → 移动 N 次 → 抬起），而跨进程通信是异步的：
每一帧都要跨进程问一次「拖到哪了」，延迟和抖动无法接受。加上桌面是系统进程，
不可能让第三方 App 在它的界面上跑任意触摸逻辑。所以 Google 只允许**点击**这种
一次性、离散、无需实时反馈的交互跨进程传递。

**注意：不要试图用「长按弹菜单」绕过这一点。** 长按桌面小组件正是系统用来
「移动 / 重排小组件」的手势 —— 用户长按我们的小组件时，系统会进入拖拽小组件本身的模式，
我们的长按回调要么收不到，要么和系统行为打架。所以小组件里只做**单击**交互。

所以方案是：
- **小组件里**：用「↑ 上移」按钮做粗调（单条上移一位）；
- **App 里**：完整拖拽排序，排好后小组件按同一顺序渲染（两者共享同一份数据）。

小组件适合做「快速勾选」，精细排序回 App 里做更顺手（那里是自己的进程，拖拽真正跟手）。

### 数据存储为什么不用 Room

桌面小组件由系统按需拉起，和 App 是两个执行上下文。用 Room 就要处理多进程数据库、
连接生命周期、并发写等一堆问题，而这里的数据本质就是**一个有序列表**。

所以用一个 JSON 文件 + 进程内锁 + **原子替换写入**（先写 `.tmp` 再 rename）：
- 不会读到写了一半的内容；
- 读取不做内存缓存（文件很小，且任何缓存都要面对跨进程失效问题，直接读更简单可靠）；
- App 进程用 `FileObserver` 监听文件变化，小组件里改了东西 App 界面立刻同步。

`SharedPreferences` 的写入统一用 `commit()` 而不是 `apply()`：后者是异步写，
App 和小组件两个进程各自的缓存可能读到旧值（例如设置里改了每页条数但小组件没变）。

---

## 构建

需要 JDK 17、Android SDK（platform 34 + build-tools 34.0.0）、Gradle 8.7。

```bash
git clone https://github.com/RenYeMianMian/TsubasaNotes.git
cd TsubasaNotes
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk   # 或者写 local.properties 里的 sdk.dir
gradle assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`。
没有签名密钥时产出的是 `app-release-unsigned.apk`（见下「签名」），
想直接装机得自己补一份密钥，或者用 `gradle installDebug`。

项目位置：`D:\XLXZ\TsubasaNotes`（原本在 `C:\Users\H\Downloads\MonetNotes`，已迁移）。
工具链仍在 `C:\Users\H\Downloads\.android-toolchain`，`local.properties` 里的 `sdk.dir`
是绝对路径，所以迁移后不用改；但换机器时要改 —— 而 `local.properties` 不入库，
clone 下来的人靠 `ANDROID_HOME` 找 SDK。

`app/build.gradle.kts` 里 Robolectric 的 `robolectric.dependency.dir` **不能写死相对路径**：
原来写的是 `${rootDir.parentFile}/.android-toolchain/robolectric-deps`，项目一到 D 盘就
解析成不存在的 `D:\XLXZ\.android-toolchain\robolectric-deps`，Robolectric 会转头去
Maven Central 下载 android-all jar（本机不可达）导致测试全挂。现在按
「`-Probolectric.deps=...` 显式指定 → 项目上一级 → 工具链绝对路径」的顺序探测。

当前版本 **2.2**（`versionCode 4`）。版本历史：1.0 首版 → 1.9 修编码损坏 → 2.0 更名「忆记事 / Tsubasa」+ 三语言
→ 2.1 App 自定义配色 → 2.2 换猫头图标。

本机（无 Android Studio、Google Maven 不可达）是通过腾讯云镜像构建的，
见 `settings.gradle.kts` 里的 `mirrors.cloud.tencent.com` 仓库配置 —— 它同时代理了
Maven Central 和 Google Maven，所以 AGP / AndroidX 都能取到。

### 签名

签名用 `app/tsubasa.keystore`（别名与口令都是 `tsubasa`）。
这个密钥是**换包名之后重新生成的**：改包名时只重命名了 keystore 文件，
里面的别名/口令还是旧的 `monetnotes`，打包会直接报
`keystore password was incorrect`。因为 `applicationId` 已经从 `com.monetnotes`
变成 `com.tsubasa.notes`，本来就不存在跨包名的升级路径，换密钥没有副作用。

**但 keystore 与口令不入库**（`.gitignore` 里有 `*.keystore` / `keystore.properties`）。
理由不是洁癖：口令一旦公开，任何人都能用同一把密钥签一个「版本号更高」的
更新包，覆盖用户手机上装的正版 App。签名配置因此写成「有就用，没有就跳过」：

| 情况 | 结果 |
| --- | --- |
| `app/tsubasa.keystore` 存在，且 `keystore.properties` 有口令 | 正常签名，产出 `app-release.apk` |
| 两者缺一（例如刚 clone 下来） | **未签名** `app-release-unsigned.apk`，构建不报错 |

口令放在项目根目录的 `keystore.properties`（不入库）：

```properties
storePassword=...
keyAlias=...
keyPassword=...
```

这样 clone 下来的人没有密钥也能编译、跑测试，只是装不了签名包。
（旧密钥留在 `app/tsubasa.keystore.bak`，仅作存档，同样不入库。）

### 运行测试

```bash
gradle testDebugUnitTest
```

> 必须跑 **debug** 变体：`LayoutTests` / `L10nTests` 里要真实启动 Activity，
> 而提供测试宿主 Manifest 的 `ui-test-manifest` 只挂在 debug 变体上，
> 跑 release 变体会报「找不到测试用 Activity」。

161 个测试（Robolectric）：

| 测试类 | 数量 | 覆盖内容 |
|---|---|---|
| `RepoTests` | 21 | 增删改、排序、持久化、损坏文件容错 |
| `WidgetTests` | 35 | 分页、每页条数、分区标题、标题文案、页码显隐、配色 |
| `AppearanceTests` | 18 | 不透明度换算、模糊设置、背景图路径管理、渲染容错 |
| `L10nTests` | 17 | **语言包装、三套资源键集一致、Activity 端到端换语言** |
| `AppColorTests` | 16 | **App 配色优先级、与小组件隔离、进 UiState、配色推导** |
| `GroupingTests` | 13 | 未完成沉底、「贴回原顺序」的边界 |
| `LayoutTests` | 11 | 真实 inflate 布局、RemoteViews 类白名单校验 |
| `WidgetInteractionTests` | 11 | 模拟桌面广播：勾选 / 上移 / 删除 / 翻页 |
| `IconTests` | 9 | **图标矢量的像素级校验**（裁切、安全区、单色层镂空） |
| `BlurTests` | 6 | 模糊百分比换算与边界（0 必须真的等于 0） |
| `EncodingGuardTests` | 4 | **扫源码找编码损坏**，见下 |

### 测试里不要写死文案

加了 `values-en` 之后，Robolectric 的默认 locale 是 **en-US**，于是所有写死中文的断言
当场就挂了（`expected:<[还有 2 项待办]> but was:<[2 to go]>`）。

教训：**断言里的文案一律从资源取**（`ctx.getString(R.string.x)`），
不要写中文字面量。`L10nTests` 里还有一条「三套资源的键集必须完全一致」，
防止以后加字符串时漏翻某一门语言。

### 编码损坏守卫

开发中踩过一个很隐蔽的坑：用 PowerShell 的 `Set-Content` 写文件时，
它把 UTF-8 当成 GBK 处理，导致中文变成乱码（「跟随系统」→「璺熼殢绯荤粺」）。

这类损坏特别难发现，因为：

- **编译不报错** —— 乱码在语法上是合法的字符串；
- **普通测试不失败** —— 它们不检查文案；
- 只有用户肉眼看到界面上那串怪字才发现。

所以 `EncodingGuardTests` 直接扫源码，检测两类特征并让构建失败：

1. 已知的 GBK 误解码产物字符（「锛」「鐨」「璺」「熼」等）；
2. 私用区字符（U+E000–U+F8FF）—— 正常中文文案绝不会包含。

还有一个测试专门验证「检测逻辑本身有效」，防止字表写错导致守卫永远通过。

---

## 安装与使用

1. 安装 APK；
2. 长按桌面空白处 → **小组件** → 找到「忆记事」（英文/日语系统下显示「Tsubasa」）；
3. 把「忆记事 · 列表」拖到桌面；
4. 打开 App 或在小组件点「+」新建记事。

> 首次把小组件拖到桌面时如果显示空白，点一下小组件头部的刷新图标即可。

**权限**：不需要任何权限（没有网络、没有存储权限）。

---

## 代码结构

```
app/src/main/java/com/tsubasa/notes/
├── MainActivity.kt              入口；attachBaseContext 里套语言，处理「新建」意图
├── L10n.kt                      界面语言：locale 包装（attachBaseContext 用）
├── data/
│   ├── Note.kt                  记事数据模型（JSON 序列化）
│   ├── Prefs.kt                 所有偏好设置（App 与小组件共用）
│   ├── Repo.kt                  数据仓库：原子写入、排序、跨进程刷新
│   └── Grouping.kt              未完成沉底分组 + 「贴回原顺序」
├── ui/
│   ├── Theme.kt                 Material 3 主题 + 配色来源优先级 + 状态栏图标明暗
│   ├── NotesViewModel.kt        状态与业务逻辑
│   ├── NotesApp.kt              全部界面（列表、拖拽、分页、设置）
│   └── background/
│       ├── Backdrop.kt          App 背景（自定义图片或主题色纯色）
│       └── ImageStore.kt        背景图的导入/压缩/清除
└── widget/
    ├── Palette.kt               莫奈取色：种子色 → 完整配色方案
    ├── WidgetRenderer.kt        把记事渲染成 RemoteViews
    ├── NoteWidgetService.kt     集合小组件的适配器
    ├── NoteWidgetProvider.kt    小组件交互入口（勾选/上移/删除/翻页）
    ├── AddNoteWidgetProvider.kt 「快速添加」小组件
    └── ThemeChangeReceiver.kt   壁纸/主题变化后重刷
```

图标资源（`res/drawable/ic_launcher_foreground.xml`、`ic_launcher_monochrome.xml`、
`mipmap-anydpi-v26/`、`mipmap-*/`）由 `IconTests` 做像素级回归，见上文「App 图标」。

字符串资源三套，**键集必须完全一致**（有测试盯着）：
`res/values/strings.xml`（中文，同时是默认回退）、`res/values-en/`、`res/values-ja/`。
`app/build.gradle.kts` 里用 `resourceConfigurations += listOf("zh", "en", "ja")` 把
无关语言的 AndroidX 资源裁掉，APK 不会因为多语言而变大。

---

## 踩过的坑（都已在代码里修掉）

| 问题 | 后果 | 处理 |
|---|---|---|
| `RemoteViewsService` 写 `exported="false"` | 列表**完全渲染不出来** | 改 `exported="true"` + `BIND_REMOTEVIEWS` 权限（对照 Glance 官方就是这样） |
| `setPendingIntentTemplate` 用 `FLAG_IMMUTABLE` | Android 12+ 上**点每一行都没反应**，且不报错 | 改用 `FLAG_MUTABLE`（显式 intent，不受 Android 14 限制） |
| `setBackgroundColor` 设置小组件背景 | Android 12 以下**圆角消失** | 背景改成铺满的 ImageView + `setColorFilter`，任何版本都保留圆角 |
| 自定义 View 放进 RemoteViews | 未标 `@RemoteView` 会导致加载失败 | 干脆不用自定义 View，换成标准 ImageView 方案 |
| 向量图带静态 `android:tint="#FFFFFF"` | 会压过运行时的 `setColorFilter`，图标在浅色背景上**看不见** | 去掉静态 tint，改用中性填充色 |
| `FileObserver(File, int)` | API 29+ 才有，minSdk 26 上**直接崩** | 改用 `FileObserver(String, int)` |
| 拖拽时每次移动都落盘 | 卡顿 + 小组件疯狂重绘 | 拖动只在内存里改顺序，**松手时写一次** |
| 按 id 列表重排时漏掉条目 | 条目**丢失** | `reorder` 把未提及的条目保留在末尾（有测试覆盖） |
| 模糊层按 `constraints.maxHeight` 汇报尺寸 | 开模糊后**顶栏被撑成整屏**，列表和底栏全被挤没 | 已随「改为整屏模糊」一并消除（不再有按栏汇报尺寸的层） |
| 界面直接读 `Prefs` 当背景路径 | 换了背景图界面**不刷新**（Prefs 不是 Compose State） | 路径放进 `UiState`，由 StateFlow 驱动重组 |
| 设置面板 `Column` 不能滚动 | 内容超一屏后**和底部重叠** | 加 `verticalScroll` |
| 让每个栏各自模糊背景 | **中间列表区域没被糊到**，且要开 N 个离屏层 | 改成整屏模糊一次，所有栏透明 |
| 用 `Set-Content` 写含中文的源文件 | 中文变乱码、**换行被吞**，编译失败；界面出现「璺熼殢绯荤粺」 | 改用专用编辑工具；并加 `EncodingGuardTests` 让这类损坏在构建期就暴露 |
| 改包名时只重命名了 keystore 文件 | 打包报 `keystore password was incorrect` —— 文件里的别名/口令还是旧的 | 重新生成与 `build.gradle.kts` 声明一致的 keystore（包名已变，无升级路径要保） |
| `ListRow.Section` 用中文字面量常量 | 切换语言后分区标题仍是中文 | 改成 `@StringRes labelRes: Int`，由界面层解析 |
| `const val SECTION_TODO = R.string.x` | 编译失败：`Const 'val' initializer should be a constant value` | 改成 `val`（`R.string.*` 不是编译期常量） |
| 布局里烘焙的 `android:text` | 由**桌面进程**按系统语言解析，App 内选的语言管不到 | 在 `WidgetRenderer` 里 `setTextViewText` / `setContentDescription` 主动覆盖 |
| 图标矢量把裁切范围写成 `<group android:clip-path="...">` | AAPT2 直接拒绝：`attribute android:clip-path not found` —— VectorDrawable **没有**这个属性 | 改成 `<clip-path android:pathData="..." />` 子元素（`IconTests` 盯着渲染结果） |
| Robolectric 默认 legacy 图形模式下渲染矢量 | `VectorDrawable.draw()` 是**空实现**，渲染全空白，像素级断言全部形同虚设 | 加 `@GraphicsMode(GraphicsMode.Mode.NATIVE)` |
| 图标安全区缩放的断言在固定点取样 | 把 `scaleX/Y` 从 0.80 改回 1.0 后测试**照样通过**（取样点本来就在内容之外） | 改成实测所有墨色像素到圆心的**最大距离** |
| 图标裁切的断言只看墨色总面积 | 裁切退化成空后墨色只少 291 像素，仍落在宽松区间里，**抓不住** | 改成断言具体像素（右耳尖黑尖处必须是墨色） |
| 项目放在**没有 Low 完整性标签**的目录（如 `D:\XLXZ\...`） | Gradle 直接起不来：`Failed to create parent directory 'D:\XLXZ\TsubasaNotes\.gradle'`；而 PowerShell / robocopy 明明能写 | 见下「为什么 D 盘写不进去」 |

### 为什么 D 盘写不进去（完整性级别，不是权限）

迁移到 D 盘后 Gradle 立刻失败，但 `robocopy`、`New-Item`、`cmd /c mkdir` 全都正常。
原因**不是 DACL，而是 Windows 的完整性级别（Mandatory Integrity Control）**：

- 这个环境里的 **java.exe 跑在 Low 完整性**（`S-1-16-4096`），而 pwsh 自己是 Medium；
- `C:\Users\H\Downloads` 被系统标成 `Low Mandatory Level:(OI)(CI)(NW)`，所以 java 写它没问题
  （Low → Low 允许；`NW` = No-Write-Up 只挡往上写）；
- `D:\` 和 `D:\XLXZ` **没有标签**（等于 Medium），于是 java 的每次写入都是「往上写」，被强制策略拒绝。

两个特别容易带偏人的现象：`new File("D:/").canWrite()` 返回 **true**（Java 只看 DACL），
而 `Files.isWritable(D:/)` 返回 **false**（走真实探测）。

修法是把项目目录也标成 Low：

```powershell
icacls "D:\XLXZ\TsubasaNotes" /setintegritylevel "(OI)(CI)Low" /T /C
```

两个坑：

1. **必须带 `/T`**。只给根目录设 `(OI)(CI)` 时，**已存在的子目录不会追溯继承** ——
   `app\` 是 robocopy 在设标签之前建的，于是只有 `.gradle`（设标签之后才建）带上了标签，
   构建还是在 `app\build` 上失败。实测：只设根目录 → `Successfully processed 1 files`，
   `app\` 仍然「no mandatory label」；加 `/T` 后 139 个文件全部处理成功。
2. **设标签需要 `WRITE_OWNER` 权限**，而子项从 `D:\XLXZ` 继承来的只有 *Modify*（不含它），
   所以 `/T` 第一次会 `Failed processing 44 files: Access is denied`。先补授权再设：

```powershell
icacls "D:\XLXZ\TsubasaNotes" /grant "${env:COMPUTERNAME}\${env:USERNAME}:(OI)(CI)F"
icacls "D:\XLXZ\TsubasaNotes" /setintegritylevel "(OI)(CI)Low" /T /C
```

（只标了项目目录，没动 `D:\XLXZ`。若以后要在项目目录之外写 D 盘，同样会失败。）

---

## 已知限制

- 小组件里**不能拖拽**（平台限制，见上），只能用「↑」按钮或到 App 里拖；
- 小组件里**也不做长按菜单**：长按是系统用来「移动 / 重排小组件」的手势，会和我们的
  回调打架（见上文「拖拽排序为什么不在小组件里做」）；
- 小组件列表最多显示到系统给的高度，超出部分需要滚动或翻页；
- 莫奈取色在 **Android 12 以下**回落到默认紫色种子（系统没有提供调色板 API）；
- 已完成项默认仍然显示在小组件里（可以在设置里关掉）。
