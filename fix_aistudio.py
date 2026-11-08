# -*- coding: utf-8 -*-
"""AI Studio 修复：仓库根补 Vite web 应用（package.json + index.html + data.js）"""
import io, os, shutil, json

os.chdir(os.path.dirname(os.path.abspath(__file__)))

# 1) package.json（AI Studio 期望 dev server：vite 5173）
io.open("package.json", "w", encoding="utf-8").write(json.dumps({
    "name": "my-english-vocabulary-application",
    "private": True,
    "version": "2.9.0",
    "type": "module",
    "scripts": {
        "dev": "vite --host 0.0.0.0",
        "start": "vite --host 0.0.0.0",
        "build": "vite build",
        "preview": "vite preview --host 0.0.0.0"
    },
    "devDependencies": {"vite": "^5.4.0"}
}, ensure_ascii=False, indent=2))

# 2) vite 配置：端口 3000（Google 生态常用）+ host
io.open("vite.config.js", "w", encoding="utf-8").write(
"""import { defineConfig } from 'vite'
export default defineConfig({
  server: { host: true, port: 3000, strictPort: true },
  preview: { host: true, port: 3000, strictPort: true }
})
""")

# 3) 根 index.html = 网页版应用（web/index.html 即完整应用）
shutil.copyfile("web/index.html", "index.html")
# 4) 根 data.js = 最新 4 级制词库
shutil.copyfile("web/data.js", "data.js")
print("AI Studio web app ready:", os.path.exists("index.html"), os.path.exists("data.js"), os.path.exists("package.json"))
