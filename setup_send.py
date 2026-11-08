# -*- coding: utf-8 -*-
import socket, subprocess, os

# 局域网 IPv4
ip = subprocess.run(["ipconfig"], capture_output=True, text=True, encoding="gbk", errors="ignore").stdout
ipv4 = [l.split(":")[1].strip() for l in ip.splitlines() if "IPv4" in l and "192.168" in l]
print("LAN IP:", ipv4[0] if ipv4 else "none")
os.chdir(r"C:\Users\wcx\Desktop\实验")
try:
    import qrcode
    img = qrcode.make(f"http://{ipv4[0]}:8900/mypinyin.apk")
    img.save(r"C:\Users\wcx\Desktop\实验\扫码下载.png")
    print("QR saved")
except ImportError:
    print("NO_QRCODE")
