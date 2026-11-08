# -*- coding: utf-8 -*-
import io, os

os.chdir(os.path.dirname(os.path.abspath(__file__)) + r"\app\src\main\java\com\wcx\mywords")
t = io.open("MainActivity.kt", encoding="utf-8").read()
# 回滚 Crossfade 包装
t = t.replace('''            Crossfade(targetState = page, animationSpec = tween(200), label = "page") { pg ->
            when (pg) {''', '''            when (page) {''', 1)
t = t.replace('''            }
            }
        }''', '''            }
        }''', 1)
io.open("MainActivity.kt", "w", encoding="utf-8").write(t)
print("crossfade rolled back:", "Crossfade(targetState = page" not in t)
