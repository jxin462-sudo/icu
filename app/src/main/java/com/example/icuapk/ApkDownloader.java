package com.example.icuapk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * ★ 2026-10-09 移植自 test project（ApkDownloader.kt）：
 * 带 Cookie/UA 的子线程文件下载 + 进度回调。微信下载链接带时效 token，必须立即下载。
 */
public final class ApkDownloader {

    public interface Callback {
        void onProgress(long downloadedBytes, long totalBytes);
        void onComplete(File file, Exception error);
    }

    private ApkDownloader() {
    }

    public static void download(final String url, final String cookie, final String userAgent,
                                final File target, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(60000);
                    if (cookie != null) {
                        conn.setRequestProperty("Cookie", cookie);
                    }
                    if (userAgent != null) {
                        conn.setRequestProperty("User-Agent", userAgent);
                    }
                    conn.setInstanceFollowRedirects(true);
                    conn.connect();

                    if (conn.getResponseCode() < 200 || conn.getResponseCode() >= 300) {
                        throw new IOException("HTTP " + conn.getResponseCode());
                    }

                    long total = conn.getContentLengthLong();
                    if (target.getParentFile() != null) {
                        target.getParentFile().mkdirs();
                    }
                    InputStream input = conn.getInputStream();
                    FileOutputStream output = new FileOutputStream(target);
                    try {
                        byte[] buffer = new byte[64 * 1024];
                        long downloaded = 0;
                        while (true) {
                            int read = input.read(buffer);
                            if (read == -1) break;
                            output.write(buffer, 0, read);
                            downloaded += read;
                            callback.onProgress(downloaded, total);
                        }
                        output.flush();
                    } finally {
                        try {
                            output.close();
                        } catch (IOException ignored) {
                        }
                        try {
                            input.close();
                        } catch (IOException ignored) {
                        }
                    }
                    callback.onComplete(target, null);
                } catch (Exception e) {
                    target.delete();
                    callback.onComplete(null, e);
                }
            }
        }, "apk-download").start();
    }
}
