package com.example.icuapk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * ★ 2026-10-09 PackageInstaller 安装结果回调（manifest 注册，非导出）。
 * PendingIntent 由本应用创建，以本应用身份发送，故 exported=false 也能收到。
 */
public class ApkInstallResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ApkInstaller.ACTION_INSTALL_RESULT.equals(intent.getAction())) {
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        final String message;
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                /* 系统会带上确认安装 Intent，需立即转发调起安装界面 */
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        context.startActivity(confirm);
                    } catch (Exception ignored) {
                    }
                }
                return;
            case PackageInstaller.STATUS_SUCCESS:
                message = "新版本安装完成";
                break;
            case PackageInstaller.STATUS_FAILURE_ABORTED:
                message = "已取消安装";
                break;
            default:
                String extra = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                message = "安装失败" + (extra == null ? "" : "：" + extra);
                break;
        }
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        });
    }
}
