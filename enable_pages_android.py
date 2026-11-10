# -*- coding: utf-8 -*-
"""开启 GitHub Pages（main branch / docs 目录）"""
import subprocess, json, os, urllib.request

os.chdir(r"C:\Users\wcx\Desktop\实验\mywords-app")
cred = subprocess.run(["git", "credential", "fill"],
                      input="protocol=https\nhost=github.com\n\n",
                      capture_output=True, text=True, encoding="utf-8", errors="ignore").stdout
token = next((l.split("=", 1)[1] for l in cred.splitlines() if l.startswith("password=")), None)
H = {"Authorization": "Bearer " + token, "Accept": "application/vnd.github+json",
     "User-Agent": "wcx1208", "Content-Type": "application/json"}

req = urllib.request.Request(
    "https://api.github.com/repos/wcx1208/cet6-vocabulary-android/pages",
    data=json.dumps({"source": {"branch": "main", "path": "/docs"}}).encode(),
    headers=H, method="POST")
try:
    r = urllib.request.urlopen(req, timeout=30)
    print("Pages created:", r.status)
except urllib.error.HTTPError as e:
    body = e.read().decode("utf-8", "ignore")
    print("Pages:", e.code, body[:200])
    if e.code == 409: print("(already enabled)")

# 验证
import time
time.sleep(3)
try:
    r = urllib.request.urlopen("https://wcx1208.github.io/cet6-vocabulary-android/", timeout=15)
    print("Pages check:", r.status, len(r.read()), "bytes")
except Exception as e:
    print("Pages not ready yet (needs 1-2 min to build):", type(e).__name__)
