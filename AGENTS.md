# AutoFewards — Agent 接手手册（AGENTS.md）

> 本文档是接手 Agent 的唯一权威工程手册。需求权威来源是根目录的 `AUTOFEWARDS_KOTLIN_PLAN.md`（用户已三轮 grill 确认的完整需求），本文档负责「工程现状 + 怎么构建 + 怎么交付 + 踩过的坑」。
>
> 最后更新：2026-09-07，对应已交付 APK `0.5.0`（versionCode=7，versionName=0.5.0）。
>
> 全程中文回复用户。

---

## 0. 一句话现状

AutoFewards（`com.functy.autofewards`，Kotlin/Compose/Miuix 单 module）已完成 **UI 移植（M1–M3）+ 任务引擎全量接入（M4）+ 进度通知（M5）**：4 tab 主框架/Miuix 三段式主题不动，新增——
- **米游社**：扫码登录（passport QR→轮询→stoken v2→老接口验证）、Cookie/login_ticket 导入、游戏签到 luna（六游戏常量表、web salt、-5003 已签/success==1 验证码跳过）、米游币任务（58-61 mission、app salt、upvote 新端点、-100 刷 cookie 重试、1034 跳过）
- **WorkBuddy**：checkin-status + daily-checkin，10001 幂等成功
- **Bing**：原生 WebView + 油猴脚本注入（GM polyfill+自动启动补丁+30min 去重）+ 兜底搜索器（30 次/12–28s）+ CookieManager
- **调度**：wb+mhy 协程并行、Bing 后台挂载、运行中置灰、本地完成标记短路（done.wb/mhy/bing=日期）
- **通知**：task_progress 通道常驻进度通知（M5）；**导入导出**：Flutter 版 `autorewards-config` JSON 兼容（剥 `flutter.` 前缀 + bingCookies 回写 CookieManager）
- **待办**：真机验收三任务（验收基线见 PLAN §5）；死代码清理；发布收尾

### 0.1 M4 新增代码地图（引擎层）

```
core/DsSign.kt          # DS1/DS2 签名 + salt 常量（含 MiyoQian 2.106.2 配对盐）
core/MihoyoIds.kt       # uuid3 device_id + 伪 device_fp(40hex)
core/HttpBox.kt         # OkHttp 封装 + jsonSafe（非 JSON 合成错误结构）
core/AppLog.kt          # 环形日志(800) + 异步文件落盘 logs/autofewards.log
core/CnWords.kt         # 1000 汉语二字词库
core/TaskNotifier.kt    # 进度通知（task_notification 开关 gate）
core/TaskScheduler.kt   # 单例调度：runAll(并行)/runSingle/refreshStatuses(本地标记短路)
core/ConfigTransfer.kt  # 导出(clipboard)+导入(剥 flutter. 前缀+bingCookies 回写)
data/repository/MihoyoBbsService.kt   # 米游社全流程（962 行 Dart 的 1:1 翻译）
data/repository/WorkBuddyService.kt   # WB 签到
ui/screen/bing/BingEngine.kt          # WebView 生命周期外：脚本准备/兜底搜索器/console 留痕
ui/screen/bing/BingScreen.kt          # AndroidView 挂载 + 状态条 + 手动开始/兜底/重注入
ui/component/QrLoginDialog.kt         # WindowDialog + zxing 画码 + 2s 轮询 + ToastHost
ui/screen/account/AccountScreen.kt    # 扫码/Cookie/WB token/Flutter 配置导入
assets/userscripts/bing_rewards_1.3.2.user.js  # 油猴脚本（Flutter 版原样复制）
```

---

## 1. 权威文档与参考源

| 资料 | 路径 | 用途 |
|---|---|---|
| 需求蓝图（含全部功能规格、salt 常量、接口流程） | `/Users/functy/Desktop/functy's idk/AUTOFEWARDS_KOTLIN_PLAN.md` | M4–M6 的唯一需求来源，**做引擎前必读 §2（功能全集）与 §7（坑位清单）** |
| Flutter 版源码（功能已全部实现并踩平） | `/Users/functy/Desktop/functy's idk/rewards_runner/`（GitHub: functy23/autorewards-runner） | 引擎逻辑「手工翻译」的源；`lib/services/mihoyobbs/`、`lib/core/app_prefs.dart`、`docs/BUILD_NOTES.md` A.9–A.14 |
| KSU manager 源码（UI 母本） | `/Users/functy/Downloads/KernelSU-main/manager/` | UI/交互对照标准，版本组合与我们完全一致（AGP 9.4.0 / Kotlin 2.4.10 / miuix 0.9.3 / compose BOM 2026.08.00） |
| 油猴脚本 | Flutter 版 `assets/userscripts/bing_rewards_1.3.2.user.js`（74KB，MIT，greasyfork 538825） | M4 Bing 注入用，直接复制进 assets |
| 参考项目 | `/Users/functy/Downloads/`（MiyoQian-master、MihoyoBBSTools-master 等） | 米游社 DS/接口对照 |

---

## 2. 机器环境（functy 的 Mac，实测可用）

- macOS arm64（darwin 23.6.0）；**工作区路径含撇号** `/Users/functy/Desktop/functy's idk/`——但本工程在无撇号路径，CocoaPods 坑不存在
- **工程路径：`/Users/functy/autofewards/`**（本手册所在目录）
- Java：Zulu 25.0.4.1 默认（gradle 兼容）；`/usr/libexec/java_home` 有 25/21/17。构建用默认 Java 25 即可
- Android SDK：`~/Library/Android/sdk`（platforms android-34/35/36/**37.0**、build-tools 35/36/37.0.0、ndk、cmake、licenses 齐全）
  - ⚠️ `android-37.0` 和 `build-tools/37.0.0` 是当时从 brew 的 sdkmanager 目录（`/opt/homebrew/share/android-commandlinetools/`）**手工复制**过来的——brew sdkmanager 装包落在它自己目录，不落在 `~/Library/Android/sdk`。以后缺平台/工具记得两边都看
- `local.properties` 已写死 `sdk.dir=/Users/functy/Library/Android/sdk`（gitignore 内）
- gradle：无系统 gradle，用 wrapper（**9.7.1**，distributionUrl 指向腾讯镜像 `mirrors.cloud.tencent.com/gradle/`，已缓存）
- **网络**：`repo.maven.apache.org`（maven central）**直连不通**；`dl.google.com`、`services.gradle.org` 直连通。解法已固化：
  - `settings.gradle.kts` 仓库链 = google() → mavenCentral() → **aliyun central/google 镜像**（aliyun 裸根路径 404 是正常的，artifact 路径 200）
  - miuix 构件坐标是 `top.yukonga.miuix.kmp:miuix-*-android`（**必须带 `-android` 后缀**，不带 `-android` 的 `miuix-android` 在 0.9.x 已不存在）
- 代理 `http://127.0.0.1:7890` 时有时无；当前构建**不需要**代理
- `gh` 已登录 functy23；全局 git user = functy23；jadx 在 `/opt/homebrew/bin/jadx`（反编译 AAR 查 API 用，很关键，见 §6.2）

---

## 3. 构建与交付流程（标准循环）

```bash
# 构建
cd /Users/functy/autofewards && ./gradlew :app:assembleDebug
# 产物
app/build/outputs/apk/debug/app-debug.apk   # ~72–76MB

# 交付：固定拷到 ~/Downloads，命名 AutoFewards-<版本>-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk ~/Downloads/AutoFewards-0.4.1-debug.apk
```

**装机（WiFi adb）**：设备 = 小米 PLK110，IP `192.168.0.111`，**无线调试端口每次会变**：

```bash
ADB=/Users/functy/Library/Android/sdk/platform-tools/adb
# 端口找法（mDNS 广播）：
dns-sd -B _adb-tls-connect._tcp local.        # 列出服务实例
dns-sd -L "adb-3B161F0162V00000-unUA6K" _adb-tls-connect._tcp local.   # 输出 "can be reached at Android.local.:<PORT>"
$ADB disconnect 2>/dev/null; $ADB connect 192.168.0.111:<PORT>
$ADB -s 192.168.0.111:<PORT> install -r <apk>
# 启动
$ADB -s 192.168.0.111:<PORT> shell am force-stop com.functy.autofewards
$ADB -s 192.168.0.111:<PORT> shell monkey -p com.functy.autofewards -c android.intent.category.LAUNCHER 1
```

失败重试节奏：`Connection refused` = 手机无线调试端口又变了或息屏，重新 mDNS 发现；`Host is down` = 手机不在网/休眠，重试几次。

**adb 连接状态示例（2026-09-06 实测）**：
- 常见格式是 `adb-3B161F0162V00000-unUA6K (3)._adb-tls-connect._tcp device`，用 `-t <transportId>` 定位最稳，不要猜 serial 字符串。

---

## 4. 用户协作守则（**重要，用户明确要求过**）

1. **验收一律发 AskUserQuestion 询问框**（会弹通知），不要自己截图/uiautomator 反复看——费 token 且用户可能正在用手机。问题要列清楚「本轮改了什么、请重点看哪里」
2. **手机在被用户使用时绝对禁止 input tap/swipe 等触摸操作**（发生过 swipe 把用户切到抖音的事故）。adb 只做 install/force-stop/monkey 启动/受控量取；装前如果不确定，用询问框问「要我现在装吗」
3. 大任务用 TodoWrite 追踪；一次交付一个版本号；每个询问框对应一次装机
4. 用户风格：快节奏、连续给改 UI 需求；回答过「先重连 adb 再装」「直接装（推荐）」说明接受默认项放第一个选项
5. **UI 改动必须实机验收**，编译通过 ≠ 完成

---

## 5. 工程结构（现状全景）

```
/Users/functy/autofewards/
├── AGENTS.md                      ← 本手册
├── settings.gradle.kts            # google→central→aliyun 镜像链
├── build.gradle.kts               # root：插件 alias 声明
├── gradle.properties              # jvmargs 3G、AndroidX、nonTransitiveRClass
├── gradle/libs.versions.toml      # 版本目录（版本见 §6.1）
├── gradle/wrapper/                # 9.7.1 + KernelSU 复制的 gradlew/wrapper.jar
└── app/
    ├── build.gradle.kts           # ⚠️ 插件= agp + compose-compiler + kotlin-parcelize，无 kotlin-android（§6.2 坑1）
    ├── proguard-rules.pro         # 空占位
    └── src/main/
        ├── AndroidManifest.xml    # .ui.MainActivity；⚠️ tools:overrideLibrary="top.yukonga.miuix.kmp.blur"（miuix-blur 要 minSdk33）
        ├── res/
        │   ├── drawable-nodpi/    # bing.png(白底已抠除)/miyoushe.png/workbuddy.png —— Flutter 原版图标
        │   └── values/            # strings.xml（全部中文文案）、themes.xml、colors.xml、mipmap-anydpi
        └── java/com/functy/autofewards/
            ├── AutoFewardsApp.kt            # Application；instance 单例；Android14+ 强制启用预测性返回
            ├── core/AppLog.kt               # 内存环形日志(500条) StateFlow<List<LogEntry>>，tag=WB/MHY/BING/SYS；M4 引擎写这里
            ├── data/model/TaskState.kt      # ⚠️ 死代码（M1 遗留）
            ├── data/repository/SettingsRepository.kt  # 接口+Impl(SharedPreferences)+observedKeys+changes() Flow
            └── ui/
                ├── MainActivity.kt          # setContent：edge-to-edge、CompositionLocals、Nav3 NavDisplay 在 Activity 层
                │                            #   entry<Route.Main> → MainScreen（自带 Scaffold+bottomBar）
                ├── LocalMainPagerState.kt   # CompositionLocal
                ├── UiMode.kt                # Miuix/Material 枚举（我们固定走 Miuix 分支）
                ├── navigation3/Navigator.kt # Route(Main) + Navigator(backStack push/pop) + LocalNavigator
                │                            #   ⚠️ Route.Theme 已删，不要再建主题页
                ├── theme/
                │   ├── Theme.kt             # ColorMode 全枚举、AppSettings、ThemeController.getAppSettings(repo)、各 CompositionLocal
                │   └── MiuixTheme.kt        # AutoFewardsTheme：KSU MiuixKernelSUTheme 同款（ThemeController→MiuixTheme→状态栏明暗）
                ├── util/                    # BlurExt(rememberBlurBackdrop/BlurredBar)、WindowSize(shouldShowSplitPane)
                ├── component/
                │   ├── FloatingBottomBar.kt # KSU 原样（liquid glass 悬浮底栏，514行）
                │   ├── PagerNavigationSpring.kt / ScrollToTop.kt
                │   ├── SquircleIcon.kt      # Flutter SquircleIcon 对应物：png + squircleBackground(size*0.30) + RoundedCornerShape 裁剪
                │   ├── LogCard.kt           # LogRow（日志行：3dp 色条 IntrinsicSize 居中对齐）+ LogCard(未被引用)
                │   ├── StatusCard.kt        # ⚠️ 死代码（M1 遗留；HomeScreen 内另有同名 private 版）
                │   ├── liquid/              # KSU 原样 4 文件（Lens/InnerShadow/Vibrancy/CombinedBackdrop）
                │   ├── miuix/animation/     # DampedDragAnimation / InteractiveHighlight（悬浮底栏依赖）
                │   ├── miuix/modifier/      # DragGestureInspector(inspectDragGestures)
                │   ├── miuix/effect/        # KSU 的 OS3 背景效果 7 文件，⚠️ 全部未被引用（整拷备用）
                │   └── bottombar/
                │       ├── BottomBar.kt           # MainPagerState(spring 滑页动画)+NavigationBadgeState+badgeFor+BottomBar/SideRail 分发
                │       ├── BottomBarMiuix.kt      # 普通NavigationBar/悬浮底栏双形态；BottomBarDestination=Home/Account/Bing/Setting
                │       └── NavigationRailMiuix.kt # 平板分屏 rail（展开态持久化被注释为 TODO）
                ├── viewmodel/
                │   ├── MainActivityViewModel.kt   # uiState(主题/模糊/悬浮栏/缩放) + selectedMainPage；监听 observedKeys
                │   └── HomeViewModel.kt          # ⚠️ 死代码（M1 模拟运行）
                └── screen/
                    ├── home/HomeScreen.kt        # 主页：绿色工作卡(三任务配置清单)+TaskPickerRow(复选框卡+执行卡)+LogCard+SupportLinks
                    ├── account/AccountScreen.kt  # 账号页（KSU 骨架占位；M3 扫码/token 入口）
                    ├── bing/BingScreen.kt        # Bing 页（占位；M4 WebView 挂载点）
                    └── settings/SettingPager.kt  # 设置页：WB/米游社/Bing 三分栏总开关+分项、界面组、导入导出占位、关于
```

> ⚠️ `ui/screen/theme/ThemeScreen.kt` **已删除**。不要再尝试恢复主题页或加二级主题入口。

---

## 6. 版本与 API 契约（照抄即对，改动前先看）

### 6.1 版本目录（libs.versions.toml）

AGP **9.4.0** / Kotlin **2.4.10** / Compose BOM **2026.08.00** / lifecycle **2.11.0**（含 `lifecycle-viewmodel-navigation3`） / activity-compose 1.13.0 / coroutines 1.11.0 / okhttp 5.5.0 / **miuix 0.9.3**（ui+icons+preference+blur+squircle 全 `-android`） / material3 1.5.0-alpha27 / **material-kolor 5.0.1**（PaletteStyle/ColorSpec/rememberDynamicColorScheme）/ zxing 3.5.3 / **navigation3 runtime+ui 1.1.7** / navigationevent 1.1.2 / **hiddenapibypass 6.1**。minSdk **31** / target+compile **37** / Java 21 / versionCode **6**。

### 6.2 三大编译级坑（新代码报错先对照这里）

1. **AGP 9.4 内置 Kotlin**：绝不能应用 `org.jetbrains.kotlin.android`（Kotlin 2.4 会主动抛错拒绝）。插件只有 `com.android.application` + `org.jetbrains.kotlin.plugin.compose` + `kotlin-parcelize`。jvmTarget 跟随 compileOptions(Java 21)，不要写旧 `kotlinOptions{}` DSL；自由参数用顶层 `kotlin { compilerOptions { freeCompilerArgs.addAll(...) } }`（KSU 同款，可用）
2. **miuix 0.9.3 API 形状**（jadx 反编译 AAR 实证）：
   - 图标：`MiuixIcons.X` 是**扩展属性**，必须同时 `import top.yukonga.miuix.kmp.icon.MiuixIcons` + `import top.yukonga.miuix.kmp.icon.extended.X`；icons 库**只有 extended 包**（无 basic）
   - `ThemeController(构造)`：第一个参数是**位置参数** `colorSchemeMode`，然后具名 `keyColor=/isDark=/paletteStyle=/colorSpec=`
   - `MiuixTheme.colorScheme` **没有** `onSurfaceVariant`；有 `onSurface`、`onSurfaceVariantSummary`、`onSurfaceContainer`、`surfaceContainerHighest` 等
   - `CircularProgressIndicator` 参数是 `colors: ProgressIndicatorColors`（无 `color=`）；要色圈用 `InfiniteProgressIndicator(modifier, color=)`
   - `NavigationBarItem(icon: ImageVector, label: String, selected, onClick, badge: (@Composable ()->Unit)?)` —— **直收值不收 lambda**
   - `Checkbox(state: ToggleableState /*androidx.compose.ui.state*/, onClick: (()->Unit)?, modifier, colors, enabled)`
   - `Text` 有 `color=`、`style=`、`fontSize=`、`maxLines`、`overflow`；`textStyles.body1/body2/footnote1/headline1/main/title4` 存在
   - preference：`SwitchPreference(title, summary, startAction, checked, onCheckedChange)`、`ArrowPreference(title, summary, startAction, onClick, endActions, holdDownState, bottomAction)`、`OverlayDropdownPreference(title, items, selectedIndex, onSelectedIndexChange, startAction)`、`TabRow(tabs: List<String>, selectedTabIndex, onTabSelected)`
   - **`TabRow` 默认背景是 `MiuixTheme.colorScheme.surface`**，放在 `surfaceContainer` 卡片里会显出一圈色差边。要用在卡片内必须传 `colors = TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)`
   - utils：`scrollEndHaptic()`、`overScrollVertical()`；blur：`rememberLayerBackdrop`/`layerBackdrop`/`textureBlur`；squircle：`Modifier.squircleBackground(color, cornerRadius)`
   - ⚠️ **`holdDownState = true` 是「对话框打开期间保持按压」的参数**——无条件设 true 会让按钮永远呈按住态（发灰）
3. **Nav3 (androidx.navigation3 1.1.7)**：
   - `NavDisplay(backStack, entryDecorators, onBack, entryProvider { entry<Route.X> { ... } })`
   - **必须显式传 KSU 同款 entryDecorators**，否则进入动画内容时机不对：
     ```kotlin
     entryDecorators = listOf(
         rememberSaveableStateHolderNavEntryDecorator(),
         rememberViewModelStoreNavEntryDecorator(),
     )
     ```
   - `rememberViewModelStoreNavEntryDecorator` 在 `androidx.lifecycle.viewmodel.navigation3` 包，需要 `androidx.lifecycle:lifecycle-viewmodel-navigation3:2.11.0`
   - `Route` 实现 `NavKey` + Parcelable 需要 **kotlin-parcelize** 插件（已加）
   - predictive back：Nav3 1.1.7 内置支持，依赖 Activity 1.13 的 NavigationEvent 链 + Android14+ `ApplicationInfo.setEnableOnBackInvokedCallback(true)`
   - 主 Pager 返回处理用 KSU 同款 `NavigationBackHandler`，不是普通 `BackHandler`

### 6.3 KSU → AutoFewards 移植映射（改 UI 时先对照 KSU 原文件）

| AF 文件 | KSU 原文件 | 移植方式 |
|---|---|---|
| ui/MainActivity.kt | ui/MainActivity.kt | 精简：去 Natives/安装逻辑、去 splash；**NavDisplay 在 Activity 层**，`entry<Route.Main> → MainScreen`（自带 Scaffold+bottomBar） |
| ui/theme/Theme.kt | ui/theme/Theme.kt + MiuixTheme.kt | ColorMode/AppSettings/CompositionLocals 1:1；ThemeController.getAppSettings(repo) 参数化 |
| ui/theme/MiuixTheme.kt | ui/theme/MiuixTheme.kt(MiuixKernelSUTheme) | 1:1（去掉 MonetColorsProvider.UpdateCss） |
| ui/component/liquid、miuix/animation、miuix/modifier、FloatingBottomBar、PagerNavigationSpring、ScrollToTop | 同名 | **原样拷贝**（仅改包名） |
| ui/util/BlurExt、WindowSize | 同名 | 原样（DeferredContent 依赖 Nav3 转场未拷） |
| ui/component/bottombar/* | 同名 | BottomBarMiuix：destination 换为 Home/Account/Bing/Setting；badgeFor 换为账号数/Bing 运行态；去 Natives/rootAvailable 守卫 |
| ui/screen/settings/SettingPager.kt | ui/screen/settings/SettingsMiuix.kt | 骨架同，内容全换本任务开关；主题三段式控制也在这里 |
| ui/screen/home/Account/Bing | home/SuperUser/Module | 骨架同，内容为本项目占位 |

**每个页面的标准骨架**（与 KSU 完全一致，新页面照抄）：
```kotlin
Scaffold(
    topBar = { BlurredBar(backdrop) { TopAppBar(color = barColor, title = ..., scrollBehavior = scrollBehavior) } },
    popupHost = { },
    contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
) { innerPadding ->
    Box(modifier = if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().scrollEndHaptic().overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection).padding(horizontal = 12.dp),
            contentPadding = innerPadding, overscrollEffect = null,
        ) { item { ...; Spacer(Modifier.height(bottomInnerPadding)) } }   // ← 尾部必须垫
    }
}
```

---

## 7. 主题系统与设置存储

### 7.1 数据流（为什么主题改动能即时生效）

```
设置页写 repo.themeMode（SharedPreferences "settings"）
  → MainActivityViewModel 监听 prefs.changes(SettingsRepositoryImpl.observedKeys)
  → uiState 更新 → MainActivity CompositionLocalProvider 提供 LocalColorMode/LocalEnableBlur/...
  → AutoFewardsTheme(appSettings) 用 ThemeController(colorSchemeMode, keyColor, isDark, paletteStyle, colorSpec) 重建 MiuixTheme
```

### 7.2 主题现状（重要：不要回退成复杂主题）

- **仅保留三档**：0=跟随系统，1=浅色，2=深色。
- 独立主题页已删，设置页内用 `TabRow` 三段式实现。
- Monet/keyColor/colorStyle/colorSpec/AMOLED 已不再是 UI 入口，虽然底层类型仍保留。
- `TabRow` 放在卡片内必须用透明背景，否则有默认色差框。

### 7.3 SharedPreferences 键位表（前缀 `settings` 文件）

| 键 | 类型/默认 | 含义 |
|---|---|---|
| ui_mode | String "miuix" | 固定 miuix |
| color_mode | Int 0 | 0跟随/1浅/2深（3-5 Monet三态/6 AMOLED 保留但不再作为 UI 入口） |
| enable_blur | Bool false | 界面模糊 |
| enable_floating_bottom_bar | Bool false | 悬浮底栏 |
| enable_floating_bottom_bar_blur | Bool false | 悬浮底栏模糊 |
| enable_navigation_badge | Bool true | 导航徽标 |
| page_scale | Float 1.0 | 页面缩放（UI 尚无入口） |
| task_wb_enabled / task_mhy_enabled / task_bing_enabled | Bool true | 三任务总开关（M4 引擎读取） |
| mhy_game_sign / mhy_bbs_sign / mhy_read / mhy_like / mhy_cancel_like / mhy_share | Bool true | 米游社分项 |
| mhy_sign_games | String "genshin,starrail,zzz" | 游戏签到启用列表 |
| mhy_forums | String "5,2" | 社区签到/帖子分区（gids） |
| bing_fallback / bing_progress / task_notification | Bool true | Bing 分项/通知 |
| sec.mhy.cookie / sec.mhy.stoken / sec.mhy.stuid / sec.mhy.mid | String? | 米游社登录态（敏感，**永不进日志/observedKeys**） |
| sec.wb.token / sec.wb.uid / sec.wb.domain / sec.wb.enterpriseId | String? | WorkBuddy 登录态（敏感） |
| done.wb / done.mhy / done.bing | String "YYYY-MM-DD" | 本地完成标记（跨天自动重置；引擎读写） |

⚠️ `MainActivityViewModel.observedKeys` 必须与实际 UI 键同步。敏感键（sec.*）与 done.* **不进** observedKeys；done.* 变化通过 TaskScheduler.statuses StateFlow 传 UI。

---

## 8. 已完成修复（0.4.0 → 0.5.0）

1. **M4 引擎全量接入**（§0.1 代码地图）：米游社扫码/luna/米游币、WB、Bing WebView、并行调度。
2. **本地完成标记短路**：refreshStatuses/runAll 先看 `done.*` 日期标记，当日已完成直接「已签到（本地标记）」不查接口（Flutter 版 A.13.6 同款）。
3. **主页执行卡接线**：勾选任务 → TaskScheduler.runAll 并行；运行中复选框/按钮置灰不消失；勾 Bing 时后台挂载 BingEngine（不切页）。
4. **账号页 M3 落地**：扫码登录对话框（zxing 画码 + 2s 轮询 + 120s 超时）、Cookie 导入验证、WB token 保存、Flutter 配置 JSON 导入。
5. **设置页导入/导出**：导出到剪贴板（含 Bing cookies）；导入剥 `flutter.` 前缀 + bingCookies 回写 CookieManager。
6. **进度通知（M5）**：task_progress 通道，start/busy/progress/finish，5s 自消；task_notification 开关 gate；运行前请求 POST_NOTIFICATIONS 权限。
7. **版本号**：0.5.0，versionCode 7。

（0.3.5→0.4.0 的修复记录见 git 历史，此处不再保留。）

---

## 9. 其他遗留事项（低优先级但别忘）

1. **死代码清理**：`ui/component/StatusCard.kt`、`ui/viewmodel/HomeViewModel.kt`、`data/model/TaskState.kt`；`ui/component/miuix/effect/`（KSU 整拷备用，未引用）；`LogCard.kt` 的 public `LogCard`（HomeScreen 用的是私有版，`LogRow` 在用——别整文件删）
2. NavigationRailMiuix 的展开态持久化被注释（`navigationRailExpanded` 键未做）；NavigationBadgeState 目前恒空（badgeFor 逻辑已备好，可接 TaskScheduler.isRunning/账号数）
3. core-splashscreen 依赖已引未用（KSU 有 splash；想补就抄 KSU MainActivity 的 installSplashScreen + themes.xml Starting 样式）
4. bing.png 已做过白底抠除（PIL 阈值 235 透明化，原图在 Flutter 项目 assets 里）
5. 导入配置后 Bing cookies 写系统 CookieManager（url=domain 域根或 bing.com）；脚本 GM 存储与去重标记依赖 WebView localStorage（domStorageEnabled 已开）
6. 米游社任务完成判定沿用 Flutter 版：runAll 返回 ok 即写 done.mhy（含「任务状态可得 0 分」正常短路情形）

---

## 10. 引擎坑位速查（从 Flutter 版血泪清单继承，改引擎前必读）

1. **stoken 验证只用老接口** `getCookieAccountInfoBySToken`——ma-cn-session getTokenBySToken 有设备风控（-5300），穷举头无效。
2. **salt 配对**：luna 游戏签到用 web salt `G1ktdwFL…`+2.106.2+client_type 5+x-rpc-signgame；米游币任务用 app salt `idMMaGYm…`+2.106.2+client_type 2；X6 salt 只签 POST（b=body 逐字节一致）。
3. **点赞端点**是 `post/api/post/upvote`（带 gids）；旧 apihub/sapi/upvotePost 已死。
4. **gids ↔ forum_id 两套编号**（2↔26、5↔34…），配置归一化见 MihoyoBbsService.configuredGids。
5. **jsonSafe**：非 JSON（403 HTML）合成 {retcode:-1}，绝不炸任务链；分享 web 裸头 403 → 退避换 app 通道重试一次。
6. **-100 刷 cookie_token 重试一次；1034 极验跳过留痕；-5003=已签；success==1=验证码跳过**。
7. **Bing JS 注入所有 Kotlin 参数必须插值成 JS 字面量**（裸标识符=ReferenceError 静默死亡，A.12.2）；自动启动靠 localStorage `__gm_autostart_done_at` 30min 去重，点击成功派发后才写标记。
8. **WB 幂等**：HTTP 400+code 10001=已签成功；today_checked_in 不可靠只作快速短路；401/403=过期。
9. **日志严禁出现 cookie/token 值**（QR 轮询只记 token type/len 形状）。
10. 敏感键 sec.* 不进 observedKeys、不进日志；导入导出走 ConfigTransfer（Flutter 兼容格式）。
11. **OkHttp 同步调用必须离开主线程**：HttpBox.get/post 是同步阻塞——TaskScheduler 内部已在 Dispatchers.Default，但 UI 协程（rememberCoroutineScope，Main 调度）直调 service 方法要手动 `withContext(Dispatchers.IO)`，否则 NetworkOnMainThreadException（0.5.0 已踩：扫码轮询/Cookie 导入）。

---

## 11. 快速自检清单（每次改完跑一遍）

- [ ] `./gradlew :app:assembleDebug` 通过（注意 §6.2 坑）
- [ ] 新增设置键 → observedKeys 同步（敏感键除外）
- [ ] Nav3 页面必须使用完整 `entryDecorators`
- [ ] 不要重建 ThemeScreen / Route.Theme
- [ ] 新页面 → 标准骨架（§6.3）+ 尾部 `Spacer(bottomInnerPadding)`
- [ ] 新图标 → miuix icons extended 双 import 或 drawable-nodpi png + SquircleIcon
- [ ] `TabRow` 放卡片内 → `TabRowDefaults.tabRowColors(backgroundColor = Color.Transparent)`
- [ ] 动过引擎 → 日志无敏感值；任务失败路径都留痕（jsonSafe/1034/-5003）
- [ ] APK → `~/Downloads/AutoFewards-<next>-debug.apk` → adb 装机（先确认用户没用手机）→ AskUserQuestion 验收
- [ ] 动过主题/底栏 → 深浅两种模式 + 悬浮栏开/关 四象限都看一眼
