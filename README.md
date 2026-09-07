# AutoFewards

Android 自动签到工具：**米游社**（游戏签到 + 米游币任务）、**WorkBuddy**（每日积分）、**Bing Rewards**（积分搜索）。Kotlin + Jetpack Compose + [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix) 实现，UI 骨架取自 KernelSU manager。

> Flutter / macOS 桌面版见 [functy23/autorewards-runner](https://github.com/functy23/autorewards-runner)（功能母本）；本仓库为 Android 原生重写版，两版配置 JSON 互通（设置 → 导入/导出配置）。

## 功能

| 任务 | 内容 |
|---|---|
| 米游社 | 扫码登录（stoken v2）；游戏签到（原神/星铁/绝区零等六游戏 luna 接口）；米游币任务（社区签到/看帖/点赞/分享） |
| WorkBuddy | 粘贴桌面端 accessToken，每日签到（10001 幂等） |
| Bing Rewards | 原生 WebView 挂载，注入油猴脚本自动搜索 + 内置 1000 词兜底搜索器 |

- 一键运行：WorkBuddy + 米游社并行执行，Bing 后台挂载自动跑
- 本地完成标记：当日已签直接短路显示，跨天自动重置
- 任务进度通知、内存 + 文件双日志（按 WB/MHY/BING 分 tag）
- 登录态仅存本机 SharedPreferences（`sec.*` 键），日志永不打印凭据

## 构建

```bash
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

环境：AGP 9.4 / Kotlin 2.4.10 / minSdk 31 / target 37 / Java 21（详见 [AGENTS.md](AGENTS.md)）。

## 使用

1. 「账号」页：米游社**扫码登录**（推荐）或粘贴 Cookie；WorkBuddy 粘贴 token
2. 「设置」页：按需开关任务分项；或粘贴 Flutter 版导出的配置 JSON 一键迁移
3. 「总览」页勾选任务 → 开始执行；Bing 页可手动开始/兜底搜索/重新注入

## 免责声明

本项目仅供学习交流，请遵守各平台服务条款；账号风险自负。
