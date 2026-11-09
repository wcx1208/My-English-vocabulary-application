# 我的六级单词 · 安卓版

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
