package com.example.icuapk;

import android.text.TextUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ★ 任务28（V1.02 R25/R42 方案A）：局域网报告分享 HTTP 服务。
 *
 * 文件助手二维码的真实链路：导出报告 PDF → 本服务在 Pad 局域网地址上托管该文件 →
 * H5 把下载页 URL 渲染成二维码 → 手机微信扫码打开下载页 → 用户下载/打开后转发到
 * 「文件传输助手」（微信无扫码直传接口，最后一步必须用户手动转发，下载页有图文指引）。
 *
 * 设计要点：
 * - 单例懒启动，端口 {@link #PORT}；每次导出仅保留最新一份（一次性 token，防遍历）。
 * - 路由：GET /r/<token> → 下载落地页（HTML）；GET /d/<token> → PDF 文件流。
 */
final class ShareHttpServer {

    static final int PORT = 8765;

    private static final ShareHttpServer INSTANCE = new ShareHttpServer();

    static ShareHttpServer get() {
        return INSTANCE;
    }

    private static final class ShareItem {
        final File file;
        final String displayName;
        final long createdAt;

        ShareItem(File file, String displayName) {
            this.file = file;
            this.displayName = displayName;
            this.createdAt = System.currentTimeMillis();
        }
    }

    /** token → 分享文件；只保留最近一次导出 */
    private final Map<String, ShareItem> items = new ConcurrentHashMap<>();
    private ServerSocket serverSocket;
    private Thread thread;

    private ShareHttpServer() {
    }

    /** 注册一份待分享文件，返回一次性 token；每次调用清空旧分享 */
    synchronized String register(File file, String displayName) throws IOException {
        ensureStarted();
        items.clear();
        String token = UUID.randomUUID().toString().replace("-", "");
        items.put(token, new ShareItem(file, displayName));
        return token;
    }

    synchronized void clear() {
        items.clear();
    }

    private void ensureStarted() throws IOException {
        if (thread != null && thread.isAlive() && serverSocket != null && !serverSocket.isClosed()) {
            return;
        }
        serverSocket = new ServerSocket(PORT);
        thread = new Thread(this::acceptLoop, "icu-share-http");
        thread.setDaemon(true);
        thread.start();
    }

    private void acceptLoop() {
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                Thread worker = new Thread(() -> handle(socket), "icu-share-req");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                return; // socket 关闭则退出
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(8000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String requestLine = reader.readLine();
            if (requestLine == null) return;
            // 读完请求头，避免客户端 reset
            while (true) {
                String line = reader.readLine();
                if (line == null || line.isEmpty()) break;
            }
            String[] parts = requestLine.split(" ");
            String path = parts.length >= 2 ? parts[1] : "/";
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            OutputStream out = s.getOutputStream();
            if (path.startsWith("/r/")) {
                ShareItem item = items.get(path.substring(3));
                if (item == null || !item.file.exists()) { write404(out); return; }
                writeHtml(out, landingPage(item, path.substring(3)));
            } else if (path.startsWith("/d/")) {
                ShareItem item = items.get(path.substring(3));
                if (item == null || !item.file.exists()) { write404(out); return; }
                writeFile(out, item);
            } else {
                write404(out);
            }
        } catch (IOException ignored) {
        }
    }

    /** 下载落地页：报告名 + 下载按钮 + 转发到文件传输助手的图文指引 */
    private String landingPage(ShareItem item, String token) {
        String name = escapeHtml(item.displayName);
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>动物ICU治疗报告</title>"
                + "<style>body{font-family:sans-serif;background:#f2f5fa;margin:0;padding:32px 20px;color:#1c2430}"
                + ".card{background:#fff;border-radius:12px;padding:28px 22px;box-shadow:0 2px 12px rgba(0,0,0,.08)}"
                + ".name{font-size:15px;color:#575f6b;word-break:break-all;margin-bottom:22px}"
                + "a.btn{display:block;background:#2c9aff;color:#fff;text-align:center;text-decoration:none;"
                + "font-size:18px;padding:14px 0;border-radius:8px}"
                + ".steps{margin-top:26px;font-size:14px;color:#575f6b;line-height:2}"
                + ".steps b{color:#1c2430}</style></head><body><div class=\"card\">"
                + "<div class=\"name\">报告文件：" + name + "</div>"
                + "<a class=\"btn\" href=\"/d/" + token + "\">下载 / 打开报告</a>"
                + "<div class=\"steps\"><b>发送到「文件传输助手」：</b><br>"
                + "1. 点击上方按钮下载并打开报告<br>"
                + "2. 在文件预览页点右上角「…」（或分享按钮）<br>"
                + "3. 选择「发送给朋友」→「文件传输助手」</div>"
                + "</div></body></html>";
    }

    private void writeHtml(OutputStream out, String html) throws IOException {
        byte[] body = html.getBytes("UTF-8");
        writeHead(out, 200, "OK", "text/html; charset=utf-8", body.length, null);
        out.write(body);
        out.flush();
    }

    private void writeFile(OutputStream out, ShareItem item) throws IOException {
        long len = item.file.length();
        String encoded = URLEncoder.encode(item.displayName, "UTF-8").replace("+", "%20");
        String disposition = "attachment; filename*=UTF-8''" + encoded;
        writeHead(out, 200, "OK", "application/pdf", len, disposition);
        FileInputStream in = new FileInputStream(item.file);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            in.close();
        }
    }

    private void write404(OutputStream out) throws IOException {
        byte[] body = "404 Not Found".getBytes("UTF-8");
        writeHead(out, 404, "Not Found", "text/plain; charset=utf-8", body.length, null);
        out.write(body);
        out.flush();
    }

    private void writeHead(OutputStream out, int code, String status, String contentType,
                           long contentLength, String disposition) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(' ').append(status).append("\r\n");
        sb.append("Content-Type: ").append(contentType).append("\r\n");
        sb.append("Content-Length: ").append(contentLength).append("\r\n");
        if (disposition != null) sb.append("Content-Disposition: ").append(disposition).append("\r\n");
        sb.append("Cache-Control: no-store\r\nConnection: close\r\n\r\n");
        out.write(sb.toString().getBytes("UTF-8"));
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** 取本机局域网 IPv4 地址；无可用网络返回 null */
    static String getLanIp() {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress a = addrs.nextElement();
                    if (!a.isLoopbackAddress() && a instanceof Inet4Address) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
