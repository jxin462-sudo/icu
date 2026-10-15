#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
rtsp_probe.py — 在 Windows PC 上探测局域网 RTSP 摄像头，定位「连接不上」的根因。

用法（在摄像头同一局域网下，于本机命令行执行）：
    python rtsp_probe.py rtsp://账号:密码@设备IP:554/stream1

它会依次做四件事，并打印清晰结论：
    [1] TCP 连通性   —— 端口 554 是否开放（网络层 / IP / 网段 / 防火墙）
    [2] DESCRIBE(无认证) —— 服务器是否开启 RTSP、是否需要认证、路径对不对
    [3] DESCRIBE(带 Digest) —— 账号密码是否正确、能否拿到 SDP
    [4] 码流检测     —— stream1 / stream2 分别是 H264 还是 H265
"""

import socket
import sys
import re
import hashlib
import base64
import urllib.parse


def local_ips():
    """返回本机所有 IPv4 地址，便于核对是否和摄像头同网段。"""
    ips = []
    try:
        import subprocess
        out = subprocess.check_output(
            ["powershell", "-NoProfile",
             "Get-NetIPAddress -AddressFamily IPv4 | Select-Object -ExpandProperty IPAddress"],
            text=True, stderr=subprocess.DEVNULL)
        ips = [l.strip() for l in out.splitlines() if l.strip()]
    except Exception:
        pass
    return ips


def send_rtsp(s, method, uri, auth_header=""):
    cseq = 1
    req = (
        f"{method} {uri} RTSP/1.0\r\n"
        f"CSeq: {cseq}\r\n"
        f"User-Agent: ICU-RTSP-Probe\r\n"
    )
    if auth_header:
        req += f"Authorization: {auth_header}\r\n"
    if method == "DESCRIBE":
        req += "Accept: application/sdp\r\n"
    req += "\r\n"
    s.sendall(req.encode("utf-8"))
    s.settimeout(6)
    data = b""
    try:
        while b"\r\n\r\n" not in data:
            chunk = s.recv(4096)
            if not chunk:
                break
            data += chunk
    except socket.timeout:
        pass
    head, _, body = data.partition(b"\r\n\r\n")
    header_text = head.decode("utf-8", "replace")
    m = re.search(r"(?i)content-length:\s*(\d+)", header_text)
    if m:
        cl = int(m.group(1))
        while len(body) < cl:
            try:
                chunk = s.recv(4096)
                if not chunk:
                    break
                body += chunk
            except socket.timeout:
                break
    return header_text, body


def parse_www_auth(header_text):
    m = re.search(r"(?i)www-authenticate:\s*(.+)", header_text)
    return m.group(1).strip() if m else ""


def digest_response(user, pw, realm, nonce, method, uri, qop="", nc=1, cnonce="", algorithm=""):
    def md5hex(s):
        return hashlib.md5(s.encode("utf-8")).hexdigest()
    ha1 = md5hex(f"{user}:{realm}:{pw}")
    if algorithm.lower().endswith("-sess"):
        ha1 = md5hex(f"{ha1}:{nonce}:{cnonce}")
    ha2 = md5hex(f"{method}:{uri}")
    if qop:
        nch = f"{nc:08x}"
        resp = md5hex(f"{ha1}:{nonce}:{nch}:{cnonce}:{qop}:{ha2}")
        return resp, nch
    resp = md5hex(f"{ha1}:{nonce}:{ha2}")
    return resp, ""


def build_digest_header(wa, user, pw, method, uri):
    realm = re.search(r'(?i)realm="([^"]*)"', wa)
    nonce = re.search(r'(?i)nonce="([^"]*)"', wa)
    qop_r = re.search(r'(?i)qop="?([^",]+)', wa)
    algo = re.search(r'(?i)algorithm="?([^",]+)', wa)
    opaque = re.search(r'(?i)opaque="([^"]*)"', wa)
    if not (realm and nonce):
        return ""
    realm = realm.group(1)
    nonce = nonce.group(1)
    qop = "auth" if (qop_r and "auth" in qop_r.group(1).lower()) else ""
    algorithm = algo.group(1) if algo else ""
    cnonce = "icuprobe0001"
    resp, nch = digest_response(user, pw, realm, nonce, method, uri, qop, 1, cnonce, algorithm)
    h = (f'Digest username="{user}", realm="{realm}", nonce="{nonce}", uri="{uri}", '
         f'response="{resp}"')
    if qop:
        h += f', qop={qop}, nc={nch}, cnonce="{cnonce}"'
    if algorithm:
        h += f', algorithm={algorithm}'
    if opaque:
        h += f', opaque="{opaque.group(1)}"'
    return h


def detect_codec(sdp_text):
    for line in sdp_text.splitlines():
        if line.startswith("a=rtpmap:") and ("H264" in line or "H265" in line):
            return "H265" if "H265" in line.upper() else "H264", line.strip()
    return "未知/未找到", ""


def probe_path(s, host, port, path, user, pw, label):
    uri = f"rtsp://{host}:{port}{path}"
    print(f"\n===== 探测 {label}: {uri} =====")
    hdr, body = send_rtsp(s, "DESCRIBE", uri)
    if not hdr:
        print("  ✗ 无响应（连接可能被关闭）")
        return
    status = hdr.splitlines()[0] if hdr else "(空)"
    code = 0
    mm = re.match(r"RTSP/1\.0\s+(\d+)", status)
    if mm:
        code = int(mm.group(1))
    print(f"  状态: {status}")

    if code == 200:
        sdp = body.decode("utf-8", "replace")
        codec, rtpmap = detect_codec(sdp)
        print(f"  ✓ 200，无需认证。码流: {codec}  ({rtpmap})")
        return codec

    if code == 401:
        wa = parse_www_auth(hdr)
        print(f"  服务器要求认证: {wa}")
        if not user:
            print("  ✗ URL 未带账号密码，无法继续（请按 rtsp://账号:密码@IP:554/stream1 填写）")
            return "need_auth_no_cred"
        wal = wa.lower()
        # 按服务器要求的认证方式回应：Basic 就回 Basic，Digest 就回 Digest（与 APK 行为一致）
        if wal.startswith("basic") or "basic " in wal:
            auth_kind = "Basic"
            auth_h = "Basic " + base64.b64encode(f"{user}:{pw}".encode("utf-8")).decode("ascii")
        else:
            auth_kind = "Digest"
            auth_h = build_digest_header(wa, user, pw, "DESCRIBE", uri)
        hdr2, body2 = send_rtsp(s, "DESCRIBE", uri, auth_h)
        status2 = hdr2.splitlines()[0] if hdr2 else "(空)"
        print(f"  带认证重试({auth_kind}): {status2}")
        if status2.startswith("RTSP/1.0 200"):
            sdp = body2.decode("utf-8", "replace")
            codec, rtpmap = detect_codec(sdp)
            print(f"  ✓ 认证成功。码流: {codec}  ({rtpmap})")
            return codec
        else:
            err = parse_www_auth(hdr2)
            print(f"  ✗ 认证仍失败。可能账号/密码错误，或连续输错被锁定（约30分钟）。挑战: {err}")
            return "auth_fail"

    # 其它状态码
    print(f"  ✗ 服务器返回 {code} —— 多半是 RTSP 路径不对（不是 /stream1 这种），或摄像头 RTSP 服务异常")
    print("  --- 服务器原文 ---")
    print("\n".join("    " + l for l in hdr.splitlines()[:8]))
    return f"http_{code}"


def main():
    if len(sys.argv) < 2:
        print("用法: python rtsp_probe.py rtsp://账号:密码@设备IP:554/stream1")
        sys.exit(1)

    url = sys.argv[1]
    p = urllib.parse.urlparse(url)
    host = p.hostname
    port = p.port or 554
    user = urllib.parse.unquote(p.username or "")
    pw = urllib.parse.unquote(p.password or "")
    path = p.path or "/"
    if not path.startswith("/"):
        path = "/" + path

    print("=" * 60)
    print(f"目标主机 : {host}:{port}")
    print(f"路径     : {path}")
    print(f"账号     : {user or '(无)'}")
    print(f"本机IP   : {', '.join(local_ips()) or '(获取失败)'}")
    print("=" * 60)

    # [1] TCP 连通性
    print("\n[1/4] TCP 连通性测试（端口 %d）..." % port)
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5)
    try:
        s.connect((host, port))
        print("  ✓ 端口开放，能建立 TCP 连接（网络层 OK）")
    except socket.timeout:
        print("  ✗ 连接超时(5s)：摄像头不可达。检查 IP 是否正确、PC/平板与摄像头是否同一网段(192.168.5.x)、网线/WiFi、防火墙")
        s.close()
        return
    except socket.error as e:
        print(f"  ✗ 连接失败: {e}。摄像头不可达或被拦截（IP错误/未开机/不在同网段/防火墙）")
        s.close()
        return

    # [2~4] 探测 stream1 与 stream2
    print("\n[2/4]+[3/4]+[4/4] 依次探测 stream1 / stream2 的认证与码流...")
    c1 = probe_path(s, host, port, "/stream1", user, pw, "主码流 stream1")
    if c1 is None:
        c2 = probe_path(s, host, port, "/stream2", user, pw, "子码流 stream2")
    else:
        # 已连接，复用 socket 再测 stream2
        c2 = probe_path(s, host, port, "/stream2", user, pw, "子码流 stream2")

    s.close()

    # 结论
    print("\n" + "=" * 60)
    print("结论与建议")
    print("=" * 60)
    if c1 == "H265":
        print("• stream1 是 H265：APK 会自动回退到 stream2(H264)，无需改代码。")
        print("  若 APK 仍黑屏，请确认 stream2 是否返回 200（见上方 stream2 结果）。")
    elif c1 == "H264":
        print("• stream1 是 H264 且可达：那 APK 应能出图。若仍失败，请把 APK 上的错误原文发我。")
    elif c1 in ("auth_fail", "need_auth_no_cred"):
        print("• 账号/密码问题：在 APK 摄像头配置里核对账号密码；连续输错会被锁约30分钟。")
    elif isinstance(c1, str) and c1.startswith("http_"):
        print("• stream1 路径可能不对：这台摄像头也许不用 /stream1。请查摄像头说明书/网页，")
        print("  常见还有 /live/ch00_0 (海康)、/cam/realmonitor?channel=1&subtype=0 (大华) 等，")
        print("  用正确路径再跑一次本脚本。")
    else:
        print("• 见上方逐项结果。把本脚本的完整输出发我，我可精准定位。")


if __name__ == "__main__":
    main()
