# 设计来源：高星项目借鉴清单（App 美化）

> 调研方式：多 agent 并行搜索 GitHub + 实测可达性（2026-10-26）。星数为当时实查。

## 一、UI 参照（按借鉴程度排序）

| 项目 | 星数 | 借鉴了什么 |
|---|---|---|
| [android/nowinandroid](https://github.com/android/nowinandroid) | 22.0k★ | **主题引擎架构**：BackgroundTheme(gradientColors, tint) 组合 + 按设置动态生成 ColorScheme + supportsDynamicTheming 开关。我们的 `Theme.kt` 的 resolveWallpaper/colorScheme() 就是它的思路 |
| [android/compose-samples](https://github.com/android/compose-samples) | 23.5k★ | Jetpack（Jetchat）的毛玻璃语录卡（surface 88% 透明 + 衬线斜体）、Navigation 单 Activity 结构 |
| [pointfreeco/isowords](https://github.com/pointfreeco/isowords) | 3.0k★ | 美学标杆：近黑底+单一强调色+大字标题的克制感（星夜紫/墨·护眼两套深色壁纸的来源） |
| [chrisbanes/tivi](https://github.com/chrisbanes/tivi) | 6.7k★ | 页面级渐变背景 + 顶部内容浅色化的分层方式 |
| [baturyilmaz/wordpecker-app](https://github.com/baturyilmaz/wordpecker-app) | 2.3k★ | Duolingo 式游戏化：大圆角按钮、完成页庆祝+名言（学习完成页的 QuoteCard） |
| [peterdevacc/Landing 上岸单词](https://github.com/peterdevacc/Landing) | 61★ | 低饱和底色+大字号单词+圆角卡片（已有的骨架来源） |

## 二、视觉规范（源自 UI 标杆调研的共性提炼）

- 字号阶梯：单词 38-40 / 音标 15 / 释义 18 / 正文 13-14 / 辅助 11-12 / 按钮 15-16
- 圆角：卡片 16-20、按钮 12-16、标签胶囊
- 状态色只做点缀：认识 #2F9E44 / 模糊 #B7950B / 忘记 #E03131，按钮实底仅此三处
- 单卡聚焦：一屏一个视觉焦点，留白 ≥40%
- 每日激励句：衬线斜体 + 大引号 + 右对齐署名

## 三、壁纸系统

- **离线优先**：6 套壁纸全部 Compose 代码绘制（linearGradient / sweepGradient + 半透明光斑 blob），0 资源文件，APK 不增重，自动适配深浅色
- 每个页面默认映射不同壁纸（home=薄荷晨 / study=星夜紫 / list=蜜桃暖 / stat=森野绿 / set=墨护眼），同一壁纸在不同页面渐变角度与光斑位置也不同 → "每个界面都有不同的图"
- 设置页可固定任意一套（Cfg.wallpaper，"跟随页面"为默认）
- 在线图片方案评估结论：Unsplash Source 已废弃(503)、Pexels 需 key，Picsum 免费可用但离线优先的 App 选择代码绘制（如需照片壁纸，v2 可加 Picsum seed 图 + Coil 缓存）

## 四、激励英文句子

- 数据：[vinitshahdeo/inspirational-quotes](https://github.com/vinitshahdeo/inspirational-quotes)（339★，MIT），327 条中筛选 ≤95 字符纯英文短句 177 条 → `assets/quotes.json`
- 展示：按日期确定性轮换 + 每页错开 3 条索引，当天每页固定不闪变
- 备选 API 调研结论：Quotable 证书过期弃用、ZenQuotes 需要限频处理 → 内置数据集最稳

## 五、素材来源（数据层）

- 词库：不背单词 CET6 词书（kajweb/dict 3.7k★）+ ECDICT（8.5k★，音标/柯林斯星/BNC词频/cet6查漏）
- 记忆法（词根词缀）：不背单词 remMethod 字段，1836 条
- 辨析：词库内编辑距离≤2 形近配对 + syno 近义词 + max-mapper/common-english-errors 混淆对
- 算法：FSRS-6（open-spaced-repetition/py-fsrs，MIT）
- **例句 6194/6194 全覆盖**，四层管线：①不背单词词书真题例句（realExamSentence/sentence，3484+2011条）②dictionaryapi.dev 补单词 ③Tatoeba 开源句库（sentences_detailed，CC-BY，1360万句扫描匹配614条，CC-BY署名于本节）④专有名词/模板短语手工定制44条

## 六、自定义壁纸（v1.3 增补，代码就绪未编译）

- 相册选图：Photo Picker（`ActivityResultContracts.PickVisualMedia`，无需存储权限），图片拷贝进应用私有目录 `filesDir/custom_wallpaper.jpg`，重解码时降采样（≤1440px 宽）防 OOM
- 生效范围：概览/词表/统计/设置四页；**学习页排除**（保持内置星夜紫壁纸，避免图片干扰专注）
- 透明度滑杆 30%~100%：图片以可调 alpha 与主题渐变融合——低透明度时照片融入主题色、文字对比度自动提升；其上再叠轻压暗层
- 恢复默认：一键删除私有图并回到"跟随页面"
- 主题色系：自定义模式下卡片走深色玻璃方案（星夜紫系），任意照片上都保证可读

## 七、尚未编译说明

本阶段只改代码与资产（v1.3 待编译），编译命令见 README「本机重新编译」。
