# 我的六级单词

六级（CET-6）备考背单词应用：**安卓离线版 + 网页版**同源，6194 词全例句，四种学习模式，遗忘曲线调度。

> 目标 550 分 · 从 345 分起步的 7 个月备考系统

---

## 📲 下载安装（安卓）

[**下载最新版 v3.0**](https://github.com/wcx1208/cet6-words/releases/latest) · Android 8.0+ · 约 83 MB

| 版本 | 界面 | 说明 |
|---|---|---|
| **v3.0**（推荐） | 与网页版完全同一套 | 内置网页版 `index.html` + `data.js`，两端一致 |
| [v2.9](https://github.com/wcx1208/cet6-words/releases/tag/v2.9) | Compose 原生界面 | 老版本，保留可回退 |

两版同包名，覆盖安装即可；v3.0 首次启动会把 v2.9 的学习进度与打卡记录自动迁移过来。

**完全离线**：6194 词、6078 条真人美音、界面资源全部内置，飞行模式下照常使用。

## 🌐 网页版

**https://wcx1208.github.io/cet6-words/**

源码在 `docs/` 目录（GitHub Pages）。手机浏览器可"添加到主屏幕"当 App 用，进度存浏览器本地。

安卓版的界面就是这份 `docs/index.html` 的原样内嵌（见 `app/src/main/assets/web/`），所以两端永远一致：改网页版 = 改安卓版界面。

## ✨ 功能

### 词库

- **6194 词**：手册精选 649 + 不背单词 CET6 词书 3560 + ECDICT 考纲查漏 1990
- **例句 100% 覆盖**：真题例句优先，其余来自 Tatoeba 开放语料
- **4 级难度**：基础 2208 / 核心 2252 / 拓展 1569 / 进阶 61（BNC 词频 + 四六级考纲标签客观定档）
- 每条含：音标 · 释义 · 例句 · 易混词辨析（形近/近义）· 词根记忆法 · 搭配 · 同义替换

### 学习

- **四种模式**：卡片认词 / 释义选择 / 拼写默写 / 听写
- **间隔重复调度**：认识的词间隔拉长，忘记的词重新排队（早期版本移植自 [py-fsrs](https://github.com/open-spaced-repetition/py-fsrs) 的 FSRS-6 权重）
- **不限量学习**：不设每日新词上限，随时学随时停
- 错词本 · 目标圆环 · 连续打卡 · 7 天热力图 · 跨天自动重置 · 进度导入导出

### 发音

四级兜底策略：**内置真人美音（6078 条）→ 本地缓存 → 有道在线 → 系统 TTS**

安卓版通过 JS Bridge 直接播放 APK 内的 mp3，网页版在无内置音频时才走在线接口。

### 个性化

- 6 套渐变壁纸 + 相册自定义（应用内裁切，透明度可调）
- 自定义桌面图标（方/圆遮罩）
- 177 条英文激励名言
- 深色模式（跟随系统 / 手动切换），安卓版状态栏同步变色

## 🗂 项目结构

```
app/src/main/java/com/wcx/mywords/
└── MainActivity.kt    WebView 外壳：加载 assets/web、发音桥接、进度迁移

app/src/main/assets/
├── web/index.html     与 docs/index.html 同一份界面
├── web/data.js        6194 词条数据
└── audio/us/          6078 条真人美音 mp3

docs/                  网页版（GitHub Pages）
legacy-compose/        v2.9 及更早的原生 Compose 界面源码（不参与编译）
```

## 🔨 构建

用 Android Studio 打开本目录（AGP 8.4.2 / Kotlin 1.9.24 / compileSdk 34 / JDK 17），或命令行：

```
gradlew assembleDebug        # 产物：app/build/outputs/apk/debug/app-debug.apk
```

> 项目路径不能含中文（AGP 限制）。

## 📚 数据来源

| 来源 | 用途 | 许可 |
|---|---|---|
| [kajweb/dict](https://github.com/kajweb/dict) | 不背单词 CET6 词书（释义/例句/记忆法） | — |
| [ECDICT](https://github.com/skywind3000/ECDICT) | 音标 · 柯林斯星级 · BNC 词频 · 考纲标签 | MIT |
| [Tatoeba](https://tatoeba.org) | 例句补充 | CC-BY |
| [py-fsrs](https://github.com/open-spaced-repetition/py-fsrs) | 复习算法参考 | MIT |

## 📄 备考配套打印资料

同系列（本地文件，不在本仓库）：高频精选词表 · 必背词汇 · 550 分详细备战手册 · 备考工具包
