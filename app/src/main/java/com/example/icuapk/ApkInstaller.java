package com.example.icuapk;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * ★ 2026-10-09 移植自 test project（ApkInstaller.kt），有一处关键改编：
 * 本项目离线构建、没有 androidx.core（FileProvider 不可用），
 * 安装改用 framework 自带的 PackageInstaller Session API（API 21+），
 * 结果通过 manifest 注册的 ApkInstallResultReceiver 回调。
 */
public final class ApkInstaller {

    public static final String ACTION_INSTALL_RESULT = "com.example.icuapk.INSTALL_RESULT";

    public static final int CHECK_NEWER = 0;
    public static final int CHECK_NOT_NEWER = 1;
    public static final int CHECK_WRONG_PACKAGE = 2;
    public static final int CHECK_CORRUPT = 3;

    public static final class CheckResult {
        public final int code;
        public final String newVersionName;
        public final String currentVersionName;
        public final String apkPackage;

        CheckResult(int code, String newVersionName, String currentVersionName, String apkPackage) {
            this.code = code;
            this.newVersionName = newVersionName;
            this.currentVersionName = currentVersionName;
            this.apkPackage = apkPackage;
        }
    }

    private ApkInstaller() {
    }

    /** 解析 APK 并与当前应用比对：包名必须一致，versionCode 必须更高。 */
    @SuppressWarnings("deprecation")
    public static CheckResult checkUpdate(Context context, File apk) {
        PackageManager pm = context.getPackageManager();
        PackageInfo archive = pm.getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (archive == null) {
            return new CheckResult(CHECK_CORRUPT, null, null, null);
        }
        archive.applicationInfo.sourceDir = apk.getAbsolutePath();
        archive.applicationInfo.publicSourceDir = apk.getAbsolutePath();

        if (!context.getPackageName().equals(archive.packageName)) {
            return new CheckResult(CHECK_WRONG_PACKAGE, null, null, archive.packageName);
        }
        PackageInfo current;
        try {
            current = pm.getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            return new CheckResult(CHECK_CORRUPT, null, null, null);
        }
        long newCode = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
        long curCode = Build.VERSION.SDK_INT >= 28 ? current.getLongVersionCode() : current.versionCode;
        if (newCode > curCode) {
            return new CheckResult(CHECK_NEWER, archive.versionName, current.versionName, null);
        }
        return new CheckResult(CHECK_NOT_NEWER, archive.versionName, current.versionName, null);
    }

    /** 通过 PackageInstaller Session 写入 APK 并提交，系统弹出安装确认。 */
    public static void install(Context context, File apk) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (Build.VERSION.SDK_INT >= 31) {
            params.setInstallScenario(PackageManager.INSTALL_SCENARIO_FAST);
        }
        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);
        try {
            OutputStream out = session.openWrite("base.apk", 0, apk.length());
            InputStream in = new FileInputStream(apk);
            try {
                byte[] buffer = new byte[64 * 1024];
                while (true) {
                    int read = in.read(buffer);
                    if (read == -1) break;
                    out.write(buffer, 0, read);
                }
                session.fsync(out);
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
            Intent intent = new Intent(ACTION_INSTALL_RESULT);
            intent.setPackage(context.getPackageName());
            int flags = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
            PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent, flags);
            session.commit(pending.getIntentSender());
        } finally {
            session.close();
        }
    }
}
