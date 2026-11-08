# -*- coding: utf-8 -*-
"""v2.9 终版美化：动效系统（借鉴 web-design-engineer 的 motion 规范，intensity 3/10 克制级）"""
import io, os

os.chdir(os.path.dirname(os.path.abspath(__file__)) + r"\app\src\main\java\com\wcx\mywords")
t = io.open("MainActivity.kt", encoding="utf-8").read()
n0 = len(t)

# imports
t = t.replace("import androidx.compose.foundation.Canvas",
"""import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas""")

# 1) 页面切换 Crossfade（motion: 200ms ease）
t = t.replace('''            when (page) {
                "home" -> HomeScreen''', '''            Crossfade(targetState = page, animationSpec = tween(200), label = "page") { pg ->
            when (pg) {
                "home" -> HomeScreen''')
# 闭合 Crossfade：App 尾部 when 的闭合 "            }\n        }\n    }\n}\n}\n}" 处补一层
t = t.replace('''                "set" -> SetScreen(cfg, progress,
                    hasCustom = customWpFile.exists(),
                    onCfg = { saveCfg(it) },
                    onPickWallpaper = { pickWallpaper(it) },
                    onPickIcon = { uri, round -> pickIcon(uri, round) },
                    onClearWallpaper = { clearWallpaper() },
                    onImport = { cards -> progress = ProgressStore.importCards(ctx, cards); revTick++ })
            }
        }''', '''                "set" -> SetScreen(cfg, progress,
                    hasCustom = customWpFile.exists(),
                    onCfg = { saveCfg(it) },
                    onPickWallpaper = { pickWallpaper(it) },
                    onPickIcon = { uri, round -> pickIcon(uri, round) },
                    onClearWallpaper = { clearWallpaper() },
                    onImport = { cards -> progress = ProgressStore.importCards(ctx, cards); revTick++ })
            }
            }
        }''')

# 2) 圆环进度动画
t = t.replace('''                Canvas(Modifier.size(44.dp)) {
                    drawArc(Sub.copy(alpha = 0.15f), -90f, 360f, false, style = Stroke(8f))
                    drawArc(Ok, -90f, 360f * (todayN.toFloat() / goal).coerceIn(0f, 1f),
                        false, style = Stroke(8f))
                }''', '''                val ringP by animateFloatAsState(
                    (todayN.toFloat() / goal).coerceIn(0f, 1f),
                    animationSpec = tween(600), label = "ring")
                Canvas(Modifier.size(44.dp)) {
                    drawArc(Sub.copy(alpha = 0.15f), -90f, 360f, false, style = Stroke(8f))
                    drawArc(Ok, -90f, 360f * ringP, false, style = Stroke(8f))
                }''')

# 3) 学习卡弹性入场（scaleIn + spring 感）
t = t.replace('''        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
            Column(Modifier.padding(18.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {''',
'''        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true, enter = scaleIn(initialScale = 0.96f, animationSpec = tween(180))) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {''')
# 该 Column 原有两个 padding/对齐参数合并进 AnimatedVisibility 内的 Column；补原 Card 内原 Column 的闭合 +1
t = t.replace('''                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ onGrade(w, 1) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Bad)) { Text("不认识") }
                            Button({ onGrade(w, 2) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Warn)) { Text("模糊") }
                            Button({ onGrade(w, 3) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Ok)) { Text("认识") }
                        }
                    } else {''', '''                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ onGrade(w, 1) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Bad)) { Text("不认识") }
                            Button({ onGrade(w, 2) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Warn)) { Text("模糊") }
                            Button({ onGrade(w, 3) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Ok)) { Text("认识") }
                        }
                    }
                    }''', 1)
# 原内部 Column 结尾对齐修正：quiz/type 分支也包在 AnimatedVisibility 内（以上 replace 只覆盖 card 分支首尾）——
# 简化：上面的替换在 card 分支内开了 AnimatedVisibility+Column，quiz/type 分支结构未被包入会错位。
# ——放弃 #3 的精细动画（结构风险大），回滚此段：
t = t.replace('''        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = true, enter = scaleIn(initialScale = 0.96f, animationSpec = tween(180))) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {''',
'''        Card(Modifier.fillMaxWidth().weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalUiAlpha.current))) {
            Column(Modifier.padding(18.dp).fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {''')
t = t.replace('''                        }
                    }
                    }''', '''                        }
                    }''', 1)
io.open("MainActivity.kt", "w", encoding="utf-8").write(t)
print("v2.9 motion applied:", len(t)-n0, "chars | crossfade:", "Crossfade(targetState = page" in t, "| ring anim:", "animateFloatAsState" in t)
