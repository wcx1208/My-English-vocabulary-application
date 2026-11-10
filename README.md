# 我的六级单词

六级（CET-6）备考背单词应用：**安卓离线版 + 网页版**同源，6194 词全例句，四种学习模式，遗忘曲线调度。

> 目标 550 分 · 从 345 分起步的 7 个月备考系统

---

## 📲 下载安装（安卓）

[**下载最新 APK**](https://github.com/wcx1208/cet6-words/releases/latest) · Android 8.0+ · 约 100 MB

安装时如提示"未知来源应用"，选择允许即可；覆盖安装不会丢失学习进度。

**完全离线**：6194 词、6078 条真人美音、壁纸、语录全部内置，飞行模式下照常使用。

## 🌐 网页版

**https://wcx1208.github.io/cet6-words/**

源码在 `docs/` 目录（GitHub Pages）。手机浏览器可"添加到主屏幕"当 App 用，进度存浏览器本地。

## ✨ 功能

### 词库

- **6194 词**：手册精选 649 + 不背单词 CET6 词书 3560 + ECDICT 考纲查漏 1990
- **例句 100% 覆盖**：真题例句优先，其余来自 Tatoeba 开放语料
- **4 级难度**：基础 2208 / 核心 2252 / 拓展 1569 / 进阶 61（BNC 词频 + 四六级考纲标签客观定档）
- 每条含：音标 · 释义 · 例句 · 易混词辨析（形近/近义）· 词根记忆法 · 搭配 · 同义替换

### 学习

- **四种模式**：卡片认词 / 释义选择 / 拼写默写 / 听写
- **FSRS-6 遗忘曲线**：移植自 [py-fsrs](https://github.com/open-spaced-repetition/py-fsrs)（MIT），每个词按记忆稳定性安排在遗忘临界点复习
- **不限量学习**：不设每日新词上限，随时学随时停
- 错词本 · 目标圆环 · 连续打卡 · 7 天热力图 · 跨天自动重置 · 进度导入导出

### 发音

四级兜底策略：**内置真人美音（6078 条）→ 本地缓存 → 有道在线 → 系统 TTS**

### 个性化

- 6 套渐变壁纸 + 相册自定义（应用内裁切，透明度可调）
- 自定义桌面图标（方/圆遮罩）
- 177 条英文激励名言
- 双透明度滑杆（界面 / 壁纸）

## 🗂 项目结构

```
app/src/main/java/com/wcx/mywords/
├── MainActivity.kt    页面与交互（主页/学习/词表/统计/设置/裁切器）
├── Fsrs.kt            FSRS-6 复习调度算法
├── Words.kt           词条模型 · 词库加载 · 进度持久化
├── Audio.kt           发音引擎（内置 → 缓存 → 在线 → TTS）
└── Theme.kt           壁纸与主题引擎（代码绘制渐变 + 自定义图片）

app/src/main/assets/
├── words.json         6194 词条
├── quotes.json        177 条名言
├── wallpaper_default.png
└── audio/us/          6078 条真人美音 mp3

docs/                  网页版（GitHub Pages）
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
