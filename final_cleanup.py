# -*- coding: utf-8 -*-
"""两个仓库最终优化：A 删 APK 重写 README；B 删 AI Studio 临时截图重写 README"""
import io, os

A = r"C:\Users\wcx\Desktop\实验\mywords-app"
B = r"C:\Users\wcx\Desktop\实验\words_sync"

A_README = """# 我的六级单词 · 安卓版

六级备考背单词应用（Jetpack Compose），**完全离线可用**。

## 下载安装

[**点此下载最新 APK**](https://github.com/wcx1208/My-English-vocabulary-application/releases/latest)（Android 8.0+，约 100 MB，含内置发音）

安装提示"未知来源应用"选允许即可；覆盖安装不会丢失学习进度。

## 功能

- **6194 词**：音标 · 释义 · 例句（100% 覆盖）· 易混辨析 · 词根记忆法 · 搭配 · 同义替换
- **4 级难度**：基础 2208 / 核心 2252 / 拓展 1569 / 进阶 61（BNC 词频 + 考纲标签客观定档）
- **四种学习模式**：卡片认词 / 释义选择 / 拼写 / 听写
- **FSRS-6 遗忘曲线调度**：每个词按记忆稳定性安排在遗忘临界点复习
- **6078 条真人美音内置**：飞行模式也能发音（内置 → 缓存 → 在线 → 系统 TTS 四级兜底）
- **个性化**：6 套渐变壁纸 / 相册自定义（应用内裁切 + 透明度可调）/ 自定义桌面图标
- **激励**：每日目标圆环、连续打卡、177 条英文名言
- 错词本 · 7 天热力图 · 进度导入导出 · 跨天自动重置

## 源码结构

```
app/src/main/java/com/wcx/mywords/
├── MainActivity.kt   页面与交互（主页/学习/词表/统计/设置/裁切器）
├── Fsrs.kt           FSRS-6 复习调度算法
├── Words.kt          词条模型、词库加载、进度持久化
├── Audio.kt          发音引擎（内置资产 → 本地缓存 → 在线 → TTS）
└── Theme.kt          壁纸与主题引擎（代码绘制渐变 + 自定义图片）
app/src/main/assets/
├── words.json        6194 词条（含例句/辨析/记忆法）
├── quotes.json       177 条激励名言
├── wallpaper_default.png  默认壁纸
└── audio/us/         6078 条真人美音 mp3
```

## 构建

用 Android Studio 打开本目录（AGP 8.4.2 / Kotlin 1.9.24 / compileSdk 34 / JDK 17），
或命令行（项目路径不能含中文，AGP 限制）：

```bash
./gradlew assembleDebug     # 产物在 app/build/outputs/apk/debug/
```

## 数据来源

[kajweb/dict](https://github.com/kajweb/dict)（不背单词 CET6 词书）· [ECDICT](https://github.com/skywind3000/ECDICT)（音标/柯林斯星级/BNC 词频）· [Tatoeba](https://tatoeba.org)（CC-BY 例句补充）· [py-fsrs](https://github.com/open-spaced-repetition/py-fsrs)（MIT，复习算法参考）

## 网页版

[wcx1208/words](https://github.com/wcx1208/words) · [在线使用](https://wcx1208.github.io/words/)
"""

B_README = """# 我的六级单词 · 网页版

六级备考背单词应用（网页版），数据与 [安卓版](https://github.com/wcx1208/My-English-vocabulary-application/releases/latest) 同源，共 **6194 词**、例句 100% 覆盖。

## 在线使用

**https://wcx1208.github.io/words/**

手机浏览器打开即可，可"添加到主屏幕"当 App 用；学习进度存在浏览器 localStorage。

## 功能

- **4 级难度分组**：基础 / 核心 / 拓展 / 进阶（BNC 词频 + 考纲标签定档）
- **四种学习模式**：卡片认词 / 释义选择 / 拼写 / 听写
- **间隔重复**：按遗忘曲线排复习，认识的词间隔拉长，忘记的词重新排队
- **词条信息**：音标 · 释义 · 例句 · 易混辨析 · 词根记忆法 · 搭配 · 同义替换
- **其他**：错词本 · 7 天热力图 · 目标圆环 · 连续打卡 · 进度导入导出 · 在线真人发音

## 本地运行

```bash
npm install     # 仅 1 个依赖（vite）
npm run dev     # http://localhost:3000
```

`index.html` 是零框架的原生应用（HTML + JS，无 React 构建步骤），`package.json` 只用于 Vite 开发服务器。

## 与 AI Studio 兼容

仓库遵守 AI Studio 的项目约定：

- `metadata.json` — 项目元数据（名称/描述/权限）
- `npm run dev` 固定 `--port=3000 --host=0.0.0.0`
- 依赖极简（仅 vite），避免沙箱安装失败
- `vite.config.ts` 使用相对 `base: './'`，可部署到任意子路径

## 数据

`data.js` — 6194 词条。字段：`w` 单词 · `i` 音标 · `m` 释义 · `n` 笔记（搭配/记忆法）· `ex`/`exm` 例句与译文 · `cf` 易混词 · `lv` 难度等级 · `imp` 重要度。

数据来源：[kajweb/dict](https://github.com/kajweb/dict) · [ECDICT](https://github.com/skywind3000/ECDICT) · [Tatoeba](https://tatoeba.org)（CC-BY）

## 相关

安卓版（完全离线，内置 6078 条真人发音）：[My-English-vocabulary-application](https://github.com/wcx1208/My-English-vocabulary-application/releases/latest)
"""

io.open(os.path.join(A, "README.md"), "w", encoding="utf-8").write(A_README)
io.open(os.path.join(A, ".gitignore"), "w", encoding="utf-8").write(
    ".gradle/\nbuild/\nlocal.properties\n*.log\n*.exe\n*.apk\nrelease/\n")
io.open(os.path.join(B, "README.md"), "w", encoding="utf-8").write(B_README)
io.open(os.path.join(B, ".gitignore"), "w", encoding="utf-8").write(
    "node_modules/\npackage-lock.json\n*.log\n.aistudio/artifacts/*/brain/*/.tempmediaStorage/\n")

import shutil
shutil.rmtree(os.path.join(B, ".aistudio"), ignore_errors=True)
print("READMEs written, .aistudio removed")
