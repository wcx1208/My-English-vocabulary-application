# -*- coding: utf-8 -*-
"""仓库 1 的 index.html 是 Argon 美化版（引用 data.js + localStorage）——直接推到 words 仓库"""
import io, os, shutil, subprocess

os.chdir(r"C:\Users\wcx\Desktop\实验\words_sync")
# 拷贝 Argon 版覆盖旧版
shutil.copyfile(r"C:\Users\wcx\Desktop\实验\new_index.html", "index.html")
# data.js 确认同步（Argon 版引用 data.js ✓）
print("data.js size:", os.path.getsize("data.js"))

# 提交推送
os.system('git add -A && git -c user.name=wcx1208 -c user.email=wcx1208@users.noreply.github.com commit -m "升级为 Argon 设计版：主题系统/暗色模式/虚拟滚动/骨架屏/四模式完整 UI（92KB 精修版）" && git push 2>&1 | tail -1')
