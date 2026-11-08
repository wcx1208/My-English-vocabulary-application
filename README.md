# 我的六级单词（专属 App）

**当前版本 v2.8**：词库 6090 词、FSRS-6 遗忘曲线调度、辨析（形近/近义）、词根记忆法、真题例句、6078 条离线真人美音、可切换英音、自定义壁纸与图标、**完整备份导入/导出**（词卡 + 学习历史）。界面美化详见 [DESIGN.md](DESIGN.md)。

## 方式一：网页版（今天就能用，无需任何安装）

1. 把 `web/index.html` 和 `web/data.js` 两个文件一起发到手机（微信/QQ/数据线均可）；
2. 用浏览器打开 `index.html`（安卓 Chrome/Edge 都行），添加到主屏幕，效果和 App 几乎一样；
3. 数据存在手机本地（localStorage），换浏览器或删文件会丢进度。

## 方式二：安卓 APK（已编译好，直接装）

`Desktop\实验\我的六级单词_v2.8.apk`（约 99.9MB，兼容 Android 8.0+）：
1. 把 APK 通过微信/QQ/数据线发到手机，点开安装（允许"未知来源应用"即可，debug 签名仅供个人使用）；
2. 首次打开自动加载内置词库，无需网络；美音离线，英音按缓存 → 在线 → 系统 TTS 回退。

## 本机重新编译（改完代码/词库后更新 APK）

v2.8 已含全部代码改动。构建链已装在 `C:\Users\wcx\android-build\`（JDK17 + Gradle 8.7 + Android SDK）。AGP 不接受中文路径，构建副本在 `C:\Users\wcx\android-build\MyWords`：

```bat
robocopy "C:\Users\wcx\Desktop\实验\mywords-app\app" "C:\Users\wcx\android-build\MyWords\app" /E /XD build
cd C:\Users\wcx\android-build\MyWords
set "JAVA_HOME=C:\Users\wcx\android-build\jdk-17.0.20.1+1"
set "PATH=%JAVA_HOME%\bin;C:\Users\wcx\android-build\gradle-8.7\bin;%PATH%"
gradle.bat :app:assembleDebug --no-daemon --console=plain --max-workers=2
```

产物在 `C:\Users\wcx\android-build\MyWords\app\build\outputs\apk\debug\app-debug.apk`。
注意：本机虚拟内存提交余量小，gradle.properties 已锁定 -Xmx1200m + workers.max=2，别调大；词库更新先重跑 `extract_words.py` 再同步 `app/src/main/assets/words.json`。

## 学习自哪些网上项目（对照表）

| 本项目位置 | 学习对象 | 学到什么 |
|---|---|---|
| `MainActivity.kt` 整体结构 | peterdevacc/Landing（Apache-2.0） | 单 Activity + Compose + Material3 的背单词 App 骨架、分级词库组织 |
| `Words.kt` Scheduler | open-spaced-repetition/FSRS-Kotlin | 间隔重复调度思想（三键评级、难度/间隔自适应）；v1 是简化实现 |
| `MainActivity.kt` playAudio | 不背单词类应用的发音方案 | 有道 dictvoice 音频直链（type=1 英式，与手册音标一致） |
| `words.json` | kajweb/dict + ECDICT | 词库数据的组织方式（本项目的数据直接来自自己的手册） |
| 词表搜索/详情 | zyronon/TypeWords | 跟写/听写的产品思路（v2 计划实现打字模式） |

## v2 升级路线（按需）

1. **真 FSRS 调度**：引 `open-spaced-repetition` 的 fsrs 依赖替换 `Scheduler`；
2. **Room 数据库**：进度从 SharedPreferences 换 Room（参考 AnkiDroid，GPL，只学思路不抄码）；
3. **离线发音**：脚本批量下载 dictvoice mp3 进 assets，或接 sherpa-onnx 离线 TTS；
4. **打字练习模式**：参考 TypeWords 的跟写交互；
5. **打卡联动**：与《六级备考工具包》的 0-5 打卡规则对齐。

## 许可证注意

本项目自有代码可随意用；若日后抄 GPL 项目代码（AnkiDroid / TypeWords），整个 App 需开源。自用不受影响。
