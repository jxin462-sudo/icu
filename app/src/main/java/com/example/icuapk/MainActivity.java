package com.example.icuapk;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Build;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Base64;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.URLUtil;
import android.os.Environment;
import android.os.Message;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.FrameLayout;
import android.view.TextureView;
import android.view.Surface;
import android.graphics.SurfaceTexture;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class MainActivity extends Activity implements BleManager.Listener, Am4100Manager.Listener, ThermalBleManager.Listener {
    private static final int REQUEST_BLE_PERMISSIONS = 1001;
    private static final int REQUEST_VIDEO_PERMISSION = 1002;
    private static final int REQUEST_IMAGE_PERMISSION = 1003;
    private static final int REQUEST_TUTORIAL_PERMISSION = 1004;
    /* ★ 2026-10-09 教程页槽位媒体选择 / 升级安装未知来源授权 */
    private static final int REQUEST_TUTORIAL_MEDIA_PICK = 1005;
    private static final int REQUEST_UNKNOWN_SOURCES = 1006;
    private int pendingTutorialSlot = -1;
    private static final String HOST_CONNECTION_PREFS = "host_connection";
    private static final String KEY_LAST_HOST_DEVICE_ID = "last_host_device_id";
    private static final String KEY_TUTORIAL_PERMISSION_ASKED = "tutorial_permission_asked";

    private BleManager bleManager;
    private Am4100Manager am4100Manager;
    private ThermalBleManager thermalBleManager;
    private IcuDashboardView dashboardView;
    private WebView lanhuWebView;
    private FrameLayout rootView;
    private FrameLayout bootOverlay;
    private TextureView bootTexture;
    private MediaPlayer bootPlayer;
    private CameraInlinePreviewView cameraInlinePreviewView;
    private FrameLayout cameraFullscreenOverlay;
    private CameraInlinePreviewView cameraFullscreenPreviewView;
    private RectF lastInlineCameraBounds;
    private String[] lastCameraPreviewUrls = new String[0];
    private boolean lastInlineCameraVisible;
    private boolean cameraFullscreenVisible;
    private boolean cameraPreviewPausedForPrintPreview;
    private boolean thermalFullscreenVisible;
    private boolean thermalConnectRequested;
    private boolean hostAutoConnectPending;
    private boolean lastImeVisible;
    private boolean imePollStarted;
    private final Runnable imePollRunnable = new Runnable() {
        @Override
        public void run() {
            if (rootView == null) {
                return;
            }
            checkImeAndNotify();
            rootView.postDelayed(this, 220L);
        }
    };
    private String pendingThermalDeviceId = "";
    private volatile String thermalConnectionState = "等待蓝牙权限";
    // 上次推送红外帧到 WebView 的时间戳（ms），用于 50ms 节流避免 main thread 堆积
    private long lastThermalJsDispatchMs = 0L;
    // 教程视频权限申请后要执行的回调
    private Runnable pendingVideoAction = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyImmersiveMode();
        // ★ 修复问题4: 样本信息输入界面被键盘遮挡
        // 从 SOFT_INPUT_ADJUST_NOTHING 改为 SOFT_INPUT_ADJUST_RESIZE，让窗口自动调整大小
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        bleManager = new BleManager(this);
        bleManager.setListener(this);
        am4100Manager = new Am4100Manager(this);
        am4100Manager.setListener(this);
        thermalBleManager = new ThermalBleManager(this);
        thermalBleManager.setListener(this);
        dashboardView = new IcuDashboardView(this, bleManager, am4100Manager);
        /* ★ 2026-10-08 日志页·堆栈信息：未捕获异常落库（错误记录 + 堆栈），再交还原默认处理器 */
        installCrashLogger();
        dashboardView.setVisibility(View.INVISIBLE);
        rootView = new FrameLayout(this);
        cameraInlinePreviewView = new CameraInlinePreviewView(this);
        dashboardView.setCameraPreviewHost(new IcuDashboardView.CameraPreviewHost() {
            @Override
            public void updateCameraPreview(RectF bounds, boolean visible, String[] urls) {
                updateInlineCameraPreview(bounds, visible, urls);
            }

            @Override
            public void captureCameraFrame(IcuDashboardView.CameraFrameCallback callback) {
                captureInlineCameraFrame(callback);
            }

            @Override
            public boolean toggleCameraFullscreen() {
                return openInlineCameraStream();
            }
        });
        try {
            lanhuWebView = createLanhuWebView();
            rootView.addView(lanhuWebView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        } catch (Throwable throwable) {
            Toast.makeText(this, "WebView 启动失败", Toast.LENGTH_LONG).show();
        }
        rootView.addView(cameraInlinePreviewView, new FrameLayout.LayoutParams(1, 1));
        cameraFullscreenOverlay = createCameraFullscreenOverlay();
        rootView.addView(cameraFullscreenOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setupBootVideo(); // ★ 方案A：原生开机动画视频覆盖层
        setContentView(rootView);
        installImeVisibilityListener();
        reconnectLastHostIfAvailable();
    }

    /* ★ 2026-10-10 方案A（修订 2）：开机动画改用 TextureView + MediaPlayer 播放 res/raw/boot.mp4。
       关键：不能用 VideoView——其内部是 SurfaceView，Z-order 默认在宿主窗口之下（"打洞"机制），
       会被同层的 WebView 遮挡，表现为"黑屏"；TextureView 是普通 View，正常参与 View 层级。
       数据源用 AssetFileDescriptor 直接读 raw（比 android.resource:// URI 更可靠）。 */
    private void setupBootVideo() {
        try {
            bootOverlay = new FrameLayout(this);
            bootOverlay.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            bootOverlay.setBackgroundColor(0xFF000000);

            bootTexture = new TextureView(this);
            bootTexture.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            bootOverlay.addView(bootTexture);

            bootTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
                    startBootPlayer(new Surface(st));
                }
                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
                    releaseBootPlayer();
                    return true;
                }
                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture st) {}
            });
            bootOverlay.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) { finishBootVideo(); }
            });
            rootView.addView(bootOverlay); // 最后添加 = 最上层覆盖 WebView
        } catch (Throwable t) {
            // 视频异常绝不影响主流程
        }
    }

    private void startBootPlayer(Surface surface) {
        try {
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.boot);
            bootPlayer = new MediaPlayer();
            bootPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            try { afd.close(); } catch (Throwable ignore) {}
            bootPlayer.setSurface(surface);
            bootPlayer.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            bootPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) { finishBootVideo(); }
            });
            bootPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) { finishBootVideo(); return true; }
            });
            bootPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) { try { mp.start(); } catch (Throwable ignore) {} }
            });
            bootPlayer.prepareAsync();
        } catch (Throwable t) {
            finishBootVideo();
        }
    }

    private void releaseBootPlayer() {
        if (bootPlayer != null) {
            try { bootPlayer.release(); } catch (Throwable ignore) {}
            bootPlayer = null;
        }
    }

    private void finishBootVideo() {
        if (bootOverlay == null || bootOverlay.getVisibility() == View.GONE) return;
        if (lanhuWebView != null) {
            lanhuWebView.evaluateJavascript("if(window.finishSplash)window.finishSplash();", null);
        }
        bootOverlay.animate().alpha(0f).setDuration(400).withEndAction(new Runnable() {
            @Override
            public void run() {
                bootOverlay.setVisibility(View.GONE);
                releaseBootPlayer();
            }
        });
    }

    /* ★ 2026-10-08 日志页：未捕获异常 → 错误记录 + 堆栈信息，随后交还原默认处理器（不改变崩溃行为） */
    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable throwable) {
                try {
                    if (dashboardView != null) {
                        dashboardView.appendErrorLog("崩溃", String.valueOf(throwable));
                        dashboardView.appendStackLog(android.util.Log.getStackTraceString(throwable));
                    }
                } catch (Throwable ignored) {
                }
                if (previous != null) {
                    previous.uncaughtException(thread, throwable);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }
        });
    }

    private void applyImmersiveMode() {
        Window window = getWindow();
        int immersiveFlags = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        window.getDecorView().setSystemUiVisibility(immersiveFlags);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }


    // ★ V1.02 UI 改版：H5 切换为 v2 单页应用（重症监护仓UI复刻），按 #屏幕id 路由。
    private static final String LANHU_LOGIN_URL = "file:///android_asset/lanhu/v2/index.html#login";
    private static final String LANHU_HOME_URL = "file:///android_asset/lanhu/v2/index.html";
    private static final String LANHU_CAMERA_MONITOR_URL = "file:///android_asset/lanhu/v2/index.html#video";
    private static final String LANHU_HOSPITAL_SETTINGS_URL = "file:///android_asset/lanhu/v2/index.html#set-print";

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private WebView createLanhuWebView() {
        prepareWebViewCacheDirs();
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        // ⭐ 关键修复：每次都从 assets 重新加载，不使用缓存的 JS/CSS
        // 否则修改后重新安装 APK 不会生效（用户看到的一直是旧版本）
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        // ★ P1-3 修复:激进清除所有 WebView 缓存,确保新 assets 代码生效
        //   WebView 在某些情况下(尤其跨版本)会顽固缓存 file:// 资源
        clearWebViewCaches(webView);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                notifyLanhuStateChanged();
            }
        });
        webView.addJavascriptInterface(new LanhuNativeBridge(), "IcuNative");
        webView.loadUrl(LANHU_HOME_URL);
        return webView;
    }

    private void prepareWebViewCacheDirs() {
        File wasmCacheDir = new File(getCacheDir(), "WebView/Default/HTTP Cache/Code Cache/wasm");
        if (!wasmCacheDir.exists()) {
            wasmCacheDir.mkdirs();
        }
    }

    /**
     * ★ P1-3 修复:激进清除 WebView 所有缓存,确保新 APK 的 assets 代码生效
     *   WebView 在跨版本升级、Android 7-13 等情况下会顽固缓存 file:// 资源,
     *   即便设置了 LOAD_NO_CACHE 也不一定彻底生效。
     *   这里把内存缓存、磁盘缓存、HTTP 缓存、IndexedDB、localStorage 全部清掉。
     */
    @SuppressLint("ApplySharedPref")
    private void clearWebViewCaches(WebView webView) {
        try {
            webView.clearCache(true);
            webView.clearHistory();
            webView.clearFormData();
            webView.clearMatches();
            webView.clearSslPreferences();
            try {
                android.webkit.WebStorage.getInstance().deleteAllData();
            } catch (Throwable ignored) {}
            File cacheDir = getCacheDir();
            File[] cacheRoots = {
                new File(cacheDir, "WebView"),
                new File(cacheDir, "WebView/Default/HTTP Cache"),
                new File(cacheDir, "WebView/Default/HTTP Cache/Code Cache"),
                new File(cacheDir, "WebView/Default/HTTP Cache/Code Cache/wasm")
            };
            for (File root : cacheRoots) {
                deleteRecursive(root);
            }
            android.util.Log.i("ICU-WebView", "Cleared WebView caches");
        } catch (Exception e) {
            android.util.Log.w("ICU-WebView", "clearWebViewCaches failed", e);
        }
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        // 不删除当前 wasmCacheDir(可能在用),只删里面的内容
        if (file.getName().equals("wasm") && file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
            return;
        }
        file.delete();
    }

    /**
     * 把蓝湖 WebView 跳转到网页登录页。供账号菜单"退出登录"等场景调用。
     * 加载前先 fade-out，加载后 fade-in，避免按钮闪烁。
     */
    public void navigateLanhuToLogin() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (lanhuWebView != null) {
                    lanhuWebView.animate().alpha(0f).setDuration(80).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (lanhuWebView != null) {
                                lanhuWebView.loadUrl(LANHU_LOGIN_URL);
                                lanhuWebView.animate().alpha(1f).setDuration(150).start();
                            }
                        }
                    }).start();
                }
            }
        });
    }

    /**
     * 把蓝湖 WebView 跳转到主入口（index.html 的强制启动逻辑会再次校验登录态）。
     * 加载前先 fade-out，加载后 fade-in，避免按钮闪烁。
     */
    public void navigateLanhuToHome() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (lanhuWebView != null) {
                    lanhuWebView.animate().alpha(0f).setDuration(80).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            if (lanhuWebView != null) {
                                lanhuWebView.loadUrl(LANHU_HOME_URL);
                                lanhuWebView.animate().alpha(1f).setDuration(150).start();
                            }
                        }
                    }).start();
                }
            }
        });
    }

    /**
     * 把蓝湖 WebView 跳转到"摄像监控"页面。
     * 用于：native 切到 tab 3 时同步 WebView 页面（否则 WebView 还停留在 home，
     * 用户视觉上感觉 native 切了 tab 但屏幕没变）。
     * 加载前先 fade-out，加载后 fade-in，避免切换瞬间的按钮闪烁。
     */
    public void navigateLanhuToCameraMonitor() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // ★ V1.02：v2 单页应用，切到视频屏用 hash 路由（不整页重载，避免 boot 路由覆盖）。
                if (lanhuWebView != null) {
                    lanhuWebView.evaluateJavascript("if(location.hash!=='#video')location.hash='#video';", null);
                }
            }
        });
    }

    /**
     * 把蓝湖 WebView 跳转到“医院信息”设置页（lanhu_81shezhi）。
     * 供"切换机构"弹窗里"编辑当前机构"使用，避免再弹一个原生对话框。
     */
    public void navigateLanhuToHospitalSettings() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // ★ V1.02：v2 单页应用，切到打印设置屏用 hash 路由（不整页重载）。
                if (lanhuWebView != null) {
                    lanhuWebView.evaluateJavascript("if(location.hash!=='#set-print')location.hash='#set-print';", null);
                }
            }
        });
    }

    public void resetCameraPreviewFailure() {
        if (dashboardView == null) {
            return;
        }
        String[] urls = sanitizeCameraPreviewUrls(dashboardView.getCameraPreviewUrlsForWeb());
        lastCameraPreviewUrls = urls;
        if (cameraInlinePreviewView != null) {
            cameraInlinePreviewView.restartPreview(urls);
        }
        if (cameraFullscreenPreviewView != null && cameraFullscreenVisible) {
            cameraFullscreenPreviewView.restartPreview(urls);
        }
    }

    private void updateLanhuCameraPreview(float left, float top, float width, float height,
                                          float viewportWidth, float viewportHeight, boolean visible) {
        if (!visible || lanhuWebView == null || width <= 2 || height <= 2) {
            updateInlineCameraPreview(null, false, null);
            return;
        }
        float scaleX = viewportWidth <= 0 ? 1f : lanhuWebView.getWidth() / viewportWidth;
        float scaleY = viewportHeight <= 0 ? 1f : lanhuWebView.getHeight() / viewportHeight;
        RectF bounds = new RectF(
                lanhuWebView.getLeft() + left * scaleX,
                lanhuWebView.getTop() + top * scaleY,
                lanhuWebView.getLeft() + (left + width) * scaleX,
                lanhuWebView.getTop() + (top + height) * scaleY);
        updateInlineCameraPreview(bounds, true, dashboardView.getCameraPreviewUrlsForWeb());
    }

    void refreshLanhuCameraPreview() {
        if (dashboardView != null) {
            String[] urls = sanitizeCameraPreviewUrls(dashboardView.getCameraPreviewUrlsForWeb());
            lastCameraPreviewUrls = urls;
            if (cameraInlinePreviewView != null) {
                cameraInlinePreviewView.restartPreview(urls);
            }
            if (cameraFullscreenPreviewView != null && cameraFullscreenVisible) {
                cameraFullscreenPreviewView.restartPreview(urls);
            }
        }
        notifyLanhuStateChanged();
    }

    private void updateInlineCameraPreview(RectF bounds, boolean visible, String[] urls) {
        if (cameraInlinePreviewView == null) {
            return;
        }
        String[] cleanedUrls = sanitizeCameraPreviewUrls(urls);
        lastCameraPreviewUrls = cleanedUrls;
        // 任务9：未配置摄像头地址时不再显示原生黑块（遮挡视频页），让 H5 灰色占位露出
        lastInlineCameraVisible = visible && bounds != null && bounds.width() > 2 && bounds.height() > 2
                && cleanedUrls.length > 0;
        lastInlineCameraBounds = lastInlineCameraVisible ? new RectF(bounds) : null;
        if (cameraPreviewPausedForPrintPreview) {
            cameraInlinePreviewView.stopPreview();
            cameraInlinePreviewView.setVisibility(View.GONE);
            return;
        }
        if (thermalFullscreenVisible) {
            cameraInlinePreviewView.setActive(false);
            cameraInlinePreviewView.setVisibility(View.GONE);
            return;
        }
        if (cameraFullscreenVisible) {
            cameraInlinePreviewView.setActive(false);
            if (cameraFullscreenPreviewView != null) {
                cameraFullscreenPreviewView.setPreview(cleanedUrls);
            }
            return;
        }
        if (!lastInlineCameraVisible || lastInlineCameraBounds == null) {
            cameraInlinePreviewView.setActive(false);
            return;
        }
        applyCameraPreviewBounds(cameraInlinePreviewView, lastInlineCameraBounds);
        cameraInlinePreviewView.setPreview(cleanedUrls);
        cameraInlinePreviewView.setActive(true);
    }

    private void pauseCameraPreviewForPrintPreview() {
        cameraPreviewPausedForPrintPreview = true;
        if (cameraInlinePreviewView != null) {
            cameraInlinePreviewView.stopPreview();
            cameraInlinePreviewView.setVisibility(View.GONE);
        }
        if (cameraFullscreenPreviewView != null) {
            cameraFullscreenPreviewView.stopPreview();
            cameraFullscreenPreviewView.setVisibility(View.GONE);
        }
    }

    private void resumeCameraPreviewAfterPrintPreview() {
        cameraPreviewPausedForPrintPreview = false;
        if (cameraFullscreenVisible) {
            if (cameraFullscreenPreviewView != null) {
                cameraFullscreenPreviewView.setVisibility(View.VISIBLE);
                cameraFullscreenPreviewView.restartPreview(lastCameraPreviewUrls);
            }
            return;
        }
        if (cameraInlinePreviewView != null) {
            cameraInlinePreviewView.setVisibility(View.VISIBLE);
            restoreInlineCameraPreview();
        }
    }

    private void captureInlineCameraFrame(IcuDashboardView.CameraFrameCallback callback) {
        CameraInlinePreviewView target = cameraFullscreenVisible && cameraFullscreenPreviewView != null
                ? cameraFullscreenPreviewView
                : cameraInlinePreviewView;
        if (target == null) {
            callback.onError("摄像头预览未初始化");
            return;
        }
        target.captureFrame(callback);
    }

    private boolean openInlineCameraStream() {
        if (dashboardView == null) {
            return false;
        }
        String[] urls = sanitizeCameraPreviewUrls(dashboardView.getCameraPreviewUrlsForWeb());
        if (urls == null || urls.length == 0) {
            return false;
        }
        lastCameraPreviewUrls = urls;
        showCameraFullscreenOverlay(urls);
        return true;
    }

    private FrameLayout createCameraFullscreenOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        overlay.setBackgroundColor(Color.BLACK);
        overlay.setClickable(true);

        cameraFullscreenPreviewView = new CameraInlinePreviewView(this);
        overlay.addView(cameraFullscreenPreviewView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        TextView titleView = new TextView(this);
        titleView.setText("摄像头全屏");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(14);
        titleView.setPadding(dp(14), dp(10), dp(14), dp(10));
        titleView.setBackgroundColor(Color.argb(150, 0, 0, 0));
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START | Gravity.TOP);
        titleParams.leftMargin = dp(12);
        titleParams.topMargin = dp(12);
        overlay.addView(titleView, titleParams);

        TextView closeView = new TextView(this);
        closeView.setText("退出全屏");
        closeView.setTextColor(Color.WHITE);
        closeView.setTextSize(14);
        closeView.setPadding(dp(14), dp(10), dp(14), dp(10));
        closeView.setBackgroundColor(Color.argb(170, 0, 0, 0));
        closeView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissCameraFullscreenOverlay();
            }
        });
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.TOP);
        closeParams.rightMargin = dp(12);
        closeParams.topMargin = dp(12);
        overlay.addView(closeView, closeParams);
        return overlay;
    }

    private void showCameraFullscreenOverlay(String[] urls) {
        if (cameraFullscreenOverlay == null || cameraFullscreenPreviewView == null) {
            return;
        }
        cameraFullscreenVisible = true;
        if (cameraInlinePreviewView != null) {
            cameraInlinePreviewView.setActive(false);
        }
        cameraFullscreenOverlay.setVisibility(View.VISIBLE);
        cameraFullscreenOverlay.bringToFront();
        cameraFullscreenPreviewView.setPreview(urls);
        cameraFullscreenPreviewView.setActive(true);
    }

    private boolean dismissCameraFullscreenOverlay() {
        if (!cameraFullscreenVisible) {
            return false;
        }
        cameraFullscreenVisible = false;
        if (cameraFullscreenPreviewView != null) {
            cameraFullscreenPreviewView.setActive(false);
        }
        if (cameraFullscreenOverlay != null) {
            cameraFullscreenOverlay.setVisibility(View.GONE);
        }
        restoreInlineCameraPreview();
        return true;
    }

    private void restoreInlineCameraPreview() {
        if (cameraInlinePreviewView == null) {
            return;
        }
        if (thermalFullscreenVisible) {
            cameraInlinePreviewView.setActive(false);
            cameraInlinePreviewView.setVisibility(View.GONE);
            return;
        }
        if (!lastInlineCameraVisible || lastInlineCameraBounds == null) {
            cameraInlinePreviewView.setActive(false);
            return;
        }
        applyCameraPreviewBounds(cameraInlinePreviewView, lastInlineCameraBounds);
        cameraInlinePreviewView.setPreview(lastCameraPreviewUrls);
        cameraInlinePreviewView.setActive(true);
    }

    private void setThermalFullscreenVisible(boolean visible) {
        thermalFullscreenVisible = visible;
        if (cameraInlinePreviewView == null) {
            return;
        }
        if (visible) {
            cameraInlinePreviewView.setActive(false);
            cameraInlinePreviewView.setVisibility(View.GONE);
        } else {
            cameraInlinePreviewView.setVisibility(View.VISIBLE);
            restoreInlineCameraPreview();
        }
    }

    private void applyCameraPreviewBounds(CameraInlinePreviewView previewView, RectF bounds) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) previewView.getLayoutParams();
        params.leftMargin = Math.round(bounds.left);
        params.topMargin = Math.round(bounds.top);
        params.width = Math.round(bounds.width());
        params.height = Math.round(bounds.height());
        previewView.setLayoutParams(params);
    }

    private String[] sanitizeCameraPreviewUrls(String[] urls) {
        ArrayList<String> cleaned = new ArrayList<>();
        if (urls != null) {
            for (String url : urls) {
                if (TextUtils.isEmpty(url)) {
                    continue;
                }
                String trimmed = url.trim();
                if (!trimmed.isEmpty() && !cleaned.contains(trimmed)) {
                    cleaned.add(trimmed);
                }
            }
        }
        return cleaned.toArray(new String[0]);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

        // #12 #21 修复：去抖(throttle) WebView 状态推送,避免 BLE 短时间多次触发导致卡顿
    private long lastNotifyLanhuStateAt = 0L;
    /* ★ 2026-10-08 日志页·通信记录：native→H5 推送的日志节流时间戳 */
    private long lastCommLogNotifyAt = 0L;
    private boolean pendingNotify = false;
    private final Runnable trailingNotifyRun = new Runnable() {
        @Override
        public void run() {
            pendingNotify = false;
            if (lanhuWebView != null) {
                lanhuWebView.evaluateJavascript(
                        "window.dispatchEvent(new Event('icu-native-state'));", null);
            }
        }
    };

    void notifyLanhuStateChanged() {
        if (lanhuWebView == null) {
            return;
        }
        final long now = android.os.SystemClock.uptimeMillis();
        // ★ 修复 P0-3:补 trailing edge。前沿 50ms 节流避免高频推送,
        //   但窗口内最后一次必须补发一次,否则会被永久丢弃。
        if (now - lastNotifyLanhuStateAt < 50L) {
            if (!pendingNotify) {
                pendingNotify = true;
                lanhuWebView.postDelayed(trailingNotifyRun,
                        50L - (now - lastNotifyLanhuStateAt));
            }
            return;
        }
        lastNotifyLanhuStateAt = now;
        /* ★ 2026-10-08 日志页·通信记录：native→H5 方向，5s 节流防止状态风暴刷爆日志 */
        if (dashboardView != null && now - lastCommLogNotifyAt > 5000L) {
            lastCommLogNotifyAt = now;
            dashboardView.appendCommLog("native→H5", "state", "icu-native-state");
        }
        lanhuWebView.post(new Runnable() {
            @Override
            public void run() {
                lanhuWebView.evaluateJavascript(
                        "window.dispatchEvent(new Event('icu-native-state'));", null);
            }
        });
    }

    /**
     * “新建样本”在 native 建好新治疗记录后调用：等状态回推完成后，
     * 直接让网页打开样本信息录入框（避免 JS 定时器在列表刷新前抢跑，
     * 导致“保存没效果/覆盖旧样本”）。
     */
    void openLanhuPatientEditorAfterNew() {
        if (lanhuWebView == null) {
            return;
        }
        lanhuWebView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (lanhuWebView != null) {
                    lanhuWebView.evaluateJavascript(
                            "if (window.IcuPatientTemp && window.IcuPatientTemp.openAfterNew) { window.IcuPatientTemp.openAfterNew(); }",
                            null);
                }
            }
        }, 120L);
    }

    /**
     * 监听软键盘可见性（不依赖 WebView 的 visualViewport/innerHeight），
     * 键盘弹出/收起时通知 H5：新建样本弹窗贴顶或滑回居中。
     */
    private void installImeVisibilityListener() {
        if (rootView == null) {
            return;
        }
        startImePolling();
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                checkImeAndNotify();
            }
        });
    }

    private void startImePolling() {
        if (imePollStarted || rootView == null) {
            return;
        }
        imePollStarted = true;
        rootView.postDelayed(imePollRunnable, 250L);
    }

    private void checkImeAndNotify() {
        if (rootView == null) {
            return;
        }
        Rect frame = new Rect();
        rootView.getWindowVisibleDisplayFrame(frame);
        // 注意：ADJUST_RESIZE 下 rootView 会随键盘一起被压缩，
        // 必须用屏幕总高度(DisplayMetrics)对比可见区域，才能识别键盘占用。
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int visibleHeight = frame.bottom - frame.top;
        boolean imeVisible = visibleHeight < screenHeight - Math.max(150, screenHeight / 8);
        if (imeVisible == lastImeVisible) {
            return;
        }
        lastImeVisible = imeVisible;
        if (lanhuWebView != null) {
            lanhuWebView.evaluateJavascript(
                    "try{if(window.IcuPatientTemp&&window.IcuPatientTemp.notifyIme)" +
                            "window.IcuPatientTemp.notifyIme(" + imeVisible + ");}catch(e){}",
                    null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (lanhuWebView != null) {
            lanhuWebView.onPause();
        }
        if (cameraInlinePreviewView != null) {
            cameraInlinePreviewView.setActive(false);
        }
        if (cameraFullscreenPreviewView != null) {
            cameraFullscreenPreviewView.setActive(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lanhuWebView != null) {
            lanhuWebView.onResume();
        }
        if (dashboardView != null) {
            dashboardView.syncCurrentTreatmentEntryStatesFromDevice();
            dashboardView.invalidate();
        }
        if (cameraFullscreenVisible) {
            if (cameraFullscreenPreviewView != null) {
                cameraFullscreenPreviewView.setPreview(lastCameraPreviewUrls);
                cameraFullscreenPreviewView.setActive(true);
            }
        } else {
            restoreInlineCameraPreview();
        }
        notifyLanhuStateChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (rootView != null) {
            rootView.removeCallbacks(imePollRunnable);
        }
        if (cameraInlinePreviewView != null) {
            cameraInlinePreviewView.stopPreview();
            cameraInlinePreviewView.setVisibility(View.GONE);
            cameraInlinePreviewView = null;
        }
        if (cameraFullscreenPreviewView != null) {
            cameraFullscreenPreviewView.stopPreview();
            cameraFullscreenPreviewView.setVisibility(View.GONE);
            cameraFullscreenPreviewView = null;
        }
        cameraFullscreenOverlay = null;
        // ★ 修复 P1-6:销毁前先从父容器移除 WebView,避免内部 ViewTree 引用泄漏
        if (lanhuWebView != null) {
            try {
                rootView.removeView(lanhuWebView);
            } catch (Throwable ignored) {}
            lanhuWebView.destroy();
            lanhuWebView = null;
        }
        bleManager.stopScan();
        bleManager.disconnect();
        am4100Manager.stopScan();
        am4100Manager.disconnect();
        if (thermalBleManager != null) {
            thermalBleManager.stop();
        }
        // ★ 修复 P1-6:置空三个 Manager 的 listener,断开对 Activity 的强引用
        if (bleManager != null) {
            bleManager.setListener(null);
        }
        if (am4100Manager != null) {
            am4100Manager.setListener(null);
        }
        if (thermalBleManager != null) {
            thermalBleManager.setListener(null);
        }
    }

    @Override
    public void onStateChanged() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                rememberConnectedHost();
                // ★ 任务22：从主机上行报文中提取 BPM 血压仪状态（内部按内容去重，非 BPM 报文直接忽略）
                dashboardView.updateBpmFromJson(bleManager.getLastReceived());
                dashboardView.syncMonitorLevelColorFromDevice();
                dashboardView.syncCurrentPatientMonitorSnapshot();
                dashboardView.syncCurrentTreatmentEntryStatesFromDevice();
                // CO2 超过固定换气阀值时自动开启外循环（30 秒内不重复提示）。
                // 按需求不使用任何声音或震动提醒，仅 Toast 文字提示。
                String co2Alarm = bleManager.checkCo2Alarm();
                if (co2Alarm != null) {
                    android.widget.Toast.makeText(MainActivity.this, co2Alarm, android.widget.Toast.LENGTH_LONG).show();
                }
                // ★ P1-4 修复:监护等级等命令 3 秒未收到回执,Toast 提示用户
                String lastError = bleManager.getLastError();
                if (lastError != null && lastError.contains("3 秒未确认")) {
                    android.widget.Toast.makeText(MainActivity.this,
                        "⚠️ " + lastError, android.widget.Toast.LENGTH_LONG).show();
                }
                dashboardView.invalidate();
                notifyLanhuStateChanged();
            }
        });
    }

    @Override
    public void onThermalFrame(short[] centiDegrees, int width, int height, long timestampMs) {
        if (lanhuWebView == null || centiDegrees == null || centiDegrees.length != width * height) {
            return;
        }
        // 兜底节流：如果上一帧 JS 推送未消费（>50ms），跳过本帧
        // 避免 WebView 渲染慢时 evaluateJavascript 在 main thread 堆积
        long now = SystemClock.uptimeMillis();
        if (now - lastThermalJsDispatchMs < 50L) {
            return;
        }
        lastThermalJsDispatchMs = now;
        byte[] payload = new byte[centiDegrees.length * 2];
        for (int i = 0; i < centiDegrees.length; i++) {
            int offset = i * 2;
            payload[offset] = (byte) (centiDegrees[i] & 0xff);
            payload[offset + 1] = (byte) ((centiDegrees[i] >> 8) & 0xff);
        }
        final String base64 = Base64.encodeToString(payload, Base64.NO_WRAP);
        final String script = "window.IcuThermal&&window.IcuThermal.receiveFrame('" + base64
                + "'," + width + "," + height + "," + timestampMs + ");";
        lanhuWebView.post(new Runnable() {
            @Override
            public void run() {
                if (lanhuWebView != null) {
                    lanhuWebView.evaluateJavascript(script, null);
                }
            }
        });
    }

    @Override
    public void onThermalConnectionChanged(final String state) {
        thermalConnectionState = state == null ? "等待红外测温桥" : state;
        if (lanhuWebView == null) {
            return;
        }
        final String escaped = JSONObject.quote(thermalConnectionState);
        lanhuWebView.post(new Runnable() {
            @Override
            public void run() {
                if (lanhuWebView != null) {
                    lanhuWebView.evaluateJavascript(
                            "window.IcuThermal&&window.IcuThermal.setConnectionState(" + escaped + ");"
                            + "window.IcuThermal&&window.IcuThermal.addLog&&window.IcuThermal.addLog(\"onThermalConnectionChanged: \"+" + escaped + ");",
                            null);
                }
            }
        });
    }

    @Override
    public void onThermalDevicesChanged(ArrayList<ThermalBleManager.DeviceItem> devices) {
        if (lanhuWebView != null) {
            lanhuWebView.post(new Runnable() {
                @Override
                public void run() {
                    if (lanhuWebView != null) {
                        lanhuWebView.evaluateJavascript(
                                "window.dispatchEvent(new Event('icu-thermal-devices'));", null);
                    }
                }
            });
        }
    }

    public boolean hasAllBlePermissions() {
        for (String permission : getRequiredPermissions()) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public void requestBlePermissionsFromUi() {
        ArrayList<String> pending = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                pending.add(permission);
            }
        }
        if (pending.isEmpty()) {
            Toast.makeText(this, "权限已授权，可以开始扫描", Toast.LENGTH_SHORT).show();
            dashboardView.invalidate();
            return;
        }
        requestPermissions(pending.toArray(new String[0]), REQUEST_BLE_PERMISSIONS);
    }

    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= 31) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            };
        }
        return new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_TUTORIAL_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            notifyTutorialPermissionResult(allGranted);
            dashboardView.invalidate();
            notifyLanhuStateChanged();
            return;
        }
        if (requestCode == REQUEST_IMAGE_PERMISSION) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (!granted) {
                Toast.makeText(this, "需要相册图片权限才能显示教程封面", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode == REQUEST_VIDEO_PERMISSION) {
            Runnable action = pendingVideoAction;
            pendingVideoAction = null;
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                if (action != null) action.run();
            } else {
                Toast.makeText(this, "需要相册视频权限才能打开视频", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        if (requestCode != REQUEST_BLE_PERMISSIONS) {
            return;
        }
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }
        Toast.makeText(this, allGranted ? "权限申请成功" : "权限没有全部通过", Toast.LENGTH_SHORT).show();
        if (allGranted && thermalConnectRequested && thermalBleManager != null) {
            thermalConnectRequested = false;
            if (TextUtils.isEmpty(pendingThermalDeviceId)) {
                thermalBleManager.start();
            } else {
                String deviceId = pendingThermalDeviceId;
                pendingThermalDeviceId = "";
                thermalBleManager.connect(deviceId);
            }
        }
        if (allGranted && hostAutoConnectPending) {
            reconnectLastHostIfAvailable();
        }
        dashboardView.invalidate();
        notifyLanhuStateChanged();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        /* ★ 2026-10-09 教程槽位媒体选择：SAF 选择器无需存储权限，取消时只需复位 pendingSlot */
        if (requestCode == REQUEST_TUTORIAL_MEDIA_PICK) {
            int slot = pendingTutorialSlot;
            pendingTutorialSlot = -1;
            if (resultCode == RESULT_OK && data != null && data.getData() != null && slot >= 0) {
                dashboardView.onTutorialMediaSelected(slot, data.getData());
                notifyLanhuStateChanged();
            }
            return;
        }
        /* ★ 2026-10-09 未知来源授权返回后继续安装 */
        if (requestCode == REQUEST_UNKNOWN_SOURCES) {
            resumePendingApkInstall();
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (requestCode == IcuDashboardView.REQUEST_ORGANIZATION_LOGO) {
            dashboardView.onOrganizationLogoSelected(data.getData());
            notifyLanhuStateChanged();
            return;
        }
        // ★ 任务31：页脚LOGO 选择结果（设置项经 pushSettingsToLanhu 回推，无需全量 state）
        if (requestCode == IcuDashboardView.REQUEST_FOOTER_LOGO) {
            dashboardView.onFooterLogoSelected(data.getData());
            return;
        }
    }

    /** ★ 任务13：改为返回"是否真的发起了连接"，供设置-连接页的开关给出准确提示 */
    boolean reconnectLastHostIfAvailable() {
        if (bleManager == null || bleManager.isConnected()) {
            return false;
        }
        String deviceId = getSharedPreferences(HOST_CONNECTION_PREFS, MODE_PRIVATE)
                .getString(KEY_LAST_HOST_DEVICE_ID, "");
        if (TextUtils.isEmpty(deviceId)) {
            hostAutoConnectPending = false;
            return false;
        }
        if (!hasAllBlePermissions()) {
            hostAutoConnectPending = true;
            requestBlePermissionsFromUi();
            return true;
        }
        hostAutoConnectPending = false;
        bleManager.connect(deviceId);
        return true;
    }

    private void rememberConnectedHost() {
        if (bleManager == null || !bleManager.isProtocolReady()) {
            return;
        }
        String deviceId = bleManager.getConnectedDeviceId();
        if (TextUtils.isEmpty(deviceId)) {
            return;
        }
        SharedPreferences preferences = getSharedPreferences(HOST_CONNECTION_PREFS, MODE_PRIVATE);
        if (!TextUtils.equals(deviceId, preferences.getString(KEY_LAST_HOST_DEVICE_ID, ""))) {
            preferences.edit().putString(KEY_LAST_HOST_DEVICE_ID, deviceId).apply();
        }
    }

    /**
     * ★ 2026-10-09 教程页槽位选择：调起系统文档选择器挑照片/视频。
     * SAF ACTION_OPEN_DOCUMENT 由用户显式授权，无需 READ_MEDIA_* 权限。
     */
    public void pickTutorialMedia(int slot) {
        pendingTutorialSlot = slot;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_TUTORIAL_MEDIA_PICK);
        } catch (Exception e) {
            pendingTutorialSlot = -1;
            Toast.makeText(this, "无法打开文件选择器: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 打开平板相册中按日期降序排序后的第 index 个视频（用系统播放器）。
     * 用于"使用教程"页 6 个区域点击。
     * index 从 0 开始（0=最新，1=次新，以此类推）。
     */
    public void openAlbumVideo(final int index) {
        Runnable task = new Runnable() {
            @Override
            public void run() {
                doOpenAlbumVideo(index);
            }
        };
        // 检查视频读取权限
        String permission;
        if (Build.VERSION.SDK_INT >= 33) {
            permission = Manifest.permission.READ_MEDIA_VIDEO;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            pendingVideoAction = task;
            requestPermissions(new String[]{permission}, REQUEST_VIDEO_PERMISSION);
            return;
        }
        task.run();
    }

    @SuppressLint("MissingPermission")
    private void doOpenAlbumVideo(int index) {
        try {
            Uri collection;
            if (Build.VERSION.SDK_INT >= 29) {
                collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else {
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            }
            String[] projection = {
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME
            };
            String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";
            Cursor cursor = getContentResolver().query(collection, projection, null, null, sortOrder);
            if (cursor == null) {
                Toast.makeText(this, "无法读取相册", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int count = cursor.getCount();
                if (count <= index) {
                    Toast.makeText(this, "相册视频不足 " + (index + 1) + " 个（当前 " + count + " 个）", Toast.LENGTH_LONG).show();
                    return;
                }
                cursor.moveToPosition(index);
                int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                long id = cursor.getLong(idCol);
                Uri uri = ContentUris.withAppendedId(collection, id);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "video/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } finally {
                cursor.close();
            }
        } catch (Exception error) {
            Toast.makeText(this, "打开视频失败: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 是否已授予读取相册图片的权限。Android 13+ 使用 READ_MEDIA_IMAGES，旧版本使用 READ_EXTERNAL_STORAGE。
     */
    private boolean hasImageReadPermission() {
        String permission;
        if (Build.VERSION.SDK_INT >= 33) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 若未授予图片读取权限，则弹出系统权限对话框。
     */
    private void requestImageReadPermissionIfNeeded() {
        String permission;
        if (Build.VERSION.SDK_INT >= 33) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, REQUEST_IMAGE_PERMISSION);
        }
    }

    /**
     * 教程页需要的媒体权限。封面缩略图要 IMAGES，打开视频要 VIDEO —— 两者都要，
     * 分开申请会让用户被系统对话框打断两次，还容易只拿到一半。
     */
    private String[] tutorialMediaPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            };
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private boolean hasTutorialMediaPermission() {
        for (String permission : tutorialMediaPermissions()) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 系统权限对话框还能不能弹出来。
     * 勾选"不再询问"或永久拒绝后 shouldShowRequestPermissionRationale 恒为 false，
     * 但"从未申请过"也是 false，所以用一个 SP 标记区分这两种情况。
     */
    private boolean canAskTutorialPermission() {
        for (String permission : tutorialMediaPermissions()) {
            if (shouldShowRequestPermissionRationale(permission)) {
                return true;
            }
        }
        boolean asked = getSharedPreferences(HOST_CONNECTION_PREFS, 0)
                .getBoolean(KEY_TUTORIAL_PERMISSION_ASKED, false);
        return !asked;
    }

    private void requestTutorialPermission() {
        getSharedPreferences(HOST_CONNECTION_PREFS, 0).edit()
                .putBoolean(KEY_TUTORIAL_PERMISSION_ASKED, true)
                .apply();
        requestPermissions(tutorialMediaPermissions(), REQUEST_TUTORIAL_PERMISSION);
    }

    /** 权限被永久拒绝时，把用户送到本应用的系统设置页自行开启。 */
    private void openAppPermissionSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "无法打开系统设置", Toast.LENGTH_SHORT).show();
        }
    }

    /** 把权限结果回传给教程页，让它关掉弹窗并重新拉取缩略图。 */
    private void notifyTutorialPermissionResult(final boolean granted) {
        if (lanhuWebView == null) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lanhuWebView.evaluateJavascript(
                        "if (window.IcuOnTutorialPermission) { window.IcuOnTutorialPermission(" + granted + "); }",
                        null);
            }
        });
    }

    /**
     * 取相册前 6 张图片的 content URI 字符串数组（按 DATE_ADDED DESC 排序）。
     * 用于教程页缩略图展示。Android 上 MediaStore.Images.Media 会自动包含视频首帧缩略图。
     */
    private JSONArray queryTutorialThumbnails() {
        JSONArray array = new JSONArray();
        Uri collection;
        if (Build.VERSION.SDK_INT >= 29) {
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }
        String[] projection = {MediaStore.Images.Media._ID};
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(collection, projection, null, null, sortOrder);
            if (cursor == null) return array;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            int limit = Math.min(6, cursor.getCount());
            for (int i = 0; i < limit; i += 1) {
                cursor.moveToPosition(i);
                long id = cursor.getLong(idCol);
                array.put(ContentUris.withAppendedId(collection, id).toString());
            }
        } catch (Exception e) {
            android.util.Log.e("ICU-Native", "queryTutorialThumbnails failed", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return array;
    }

    /**
     * 取相册前 6 段视频的 _ID 数组（按 DATE_ADDED DESC 排序）。
     * 用于教程页检测"视频列表是否变化"，从而决定是否弹"使用教程已更新"。
     */
    private JSONArray queryTutorialVideoIds() {
        JSONArray array = new JSONArray();
        Uri collection;
        if (Build.VERSION.SDK_INT >= 29) {
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }
        String[] projection = {MediaStore.Video.Media._ID};
        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(collection, projection, null, null, sortOrder);
            if (cursor == null) return array;
            int idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
            int limit = Math.min(6, cursor.getCount());
            for (int i = 0; i < limit; i += 1) {
                cursor.moveToPosition(i);
                array.put(cursor.getLong(idCol));
            }
        } catch (Exception e) {
            android.util.Log.e("ICU-Native", "queryTutorialVideoIds failed", e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return array;
    }

    @Override
    public void onBackPressed() {
        if (updateOverlay != null && updateOverlay.getVisibility() == View.VISIBLE) {
            closeApkUpdatePage();
            return;
        }
        if (dismissCameraFullscreenOverlay()) {
            return;
        }
        if (lanhuWebView != null && lanhuWebView.canGoBack()) {
            lanhuWebView.goBack();
            return;
        }
        super.onBackPressed();
    }

    private String buildBleStateJson() {
        try {
            JSONObject root = new JSONObject();
            root.put("host", buildHostStateJson());
            root.put("monitor", buildMonitorStateJson());
            root.put("wifi", buildWifiStateJson());
            return root.toString();
        } catch (JSONException exception) {
            return "{}";
        }
    }

    JSONObject buildHostStateJson() throws JSONException {
        JSONObject host = new JSONObject();
        host.put("connected", bleManager.isConnected());
        host.put("scanning", bleManager.isScanning());
        host.put("ready", bleManager.isProtocolReady());
        host.put("state", bleManager.getConnectionStateText());
        host.put("deviceName", safeJsonText(bleManager.getConnectedDeviceName()));
        host.put("deviceId", safeJsonText(bleManager.getConnectedDeviceId()));
        host.put("devices", buildHostDevicesJson());
        host.put("zone", safeJsonText(bleManager.getCurrentZone()));
        host.put("temp", safeJsonText(bleManager.getCabinTempText()));
        host.put("tempSet", safeJsonText(bleManager.getLastSetCabinTempText()));
        host.put("oxygen", safeJsonText(bleManager.getOxygenText()));
        host.put("oxygenSet", safeJsonText(bleManager.getLastSetOxygenText()));
        host.put("humidity", safeJsonText(bleManager.getHumidityText()));
        host.put("co2", safeJsonText(bleManager.getCo2Text()));
        JSONObject zones = new JSONObject();
        zones.put("left", buildEnvironmentZoneJson("left"));
        zones.put("right", buildEnvironmentZoneJson("right"));
        host.put("zones", zones);
        // ★ P1-1 + P2-1:把 CO2 预警阀值/状态/历史推给 JS
        // co2AlarmThreshold 保留为兼容别名（= 自动预警阀值）；与目标值独立输出。
        host.put("co2AlarmThreshold", bleManager.getCo2AlarmThreshold());
        host.put("co2AutoVentThreshold", bleManager.getCo2AutoVentThreshold());
        host.put("co2Target", bleManager.getLastSetCo2Value() == null
                ? 0 : bleManager.getLastSetCo2Value());
        host.put("co2AlarmActive", bleManager.isInCo2Alarm());
        host.put("co2AlarmHistory", safeJsonText(bleManager.getCo2AlarmHistory()));
        host.put("statusLightColor", safeJsonText(dashboardView.getResolvedMonitorLevelColor()));
        host.put("monitorLevel", safeJsonText(dashboardView.getResolvedMonitorLevelLabel()));
        // ★ P1-2:把 pending 控制命令列表推给 JS,UI 加"确认中"过渡态
        host.put("pendingStatusLightColor", safeJsonText(bleManager.getPendingStatusLightColor()));
        host.put("pendingControls", safeJsonText(bleManager.getPendingControlNames()));
        host.put("infraredTemp", safeJsonText(bleManager.getInfraredTempText()));
        host.put("treatmentTime", safeJsonText(bleManager.getTreatmentTimeText()));
        host.put("treatmentMinutes", bleManager.getTreatmentMinutesValue() == null
                ? 0 : bleManager.getTreatmentMinutesValue());
        host.put("treatmentRemainingMs", bleManager.getCountdownRemainingMs(15));
        host.put("lastSent", safeJsonText(bleManager.getLastSent()));
        host.put("lastReceived", safeJsonText(bleManager.getLastReceived()));
        host.put("lastError", safeJsonText(bleManager.getLastError()));
        host.put("summary", safeJsonText(bleManager.getLastParsedStatus()));

        JSONObject controls = new JSONObject();
        putControl(controls, "temp", 0);
        putControl(controls, "oxygen", 1);
        putControl(controls, "statusLight", 2);
        putControl(controls, "co2", 3);
        putControl(controls, "coldLight", 4);
        putControl(controls, "warmLight", 5);
        putControl(controls, "redTherapy", 6);
        putControl(controls, "blueTherapy", 7);
        putControl(controls, "outerCycle", 8);
        putControl(controls, "innerCycle", 9);
        putControl(controls, "nebulizer", 10);
        putControl(controls, "anion", 11);
        putControl(controls, "uv", 12);
        putControl(controls, "o2Enabled", 13);
        putControl(controls, "humidity", 14);
        putControl(controls, "treatmentTime", 15);
        controls.put("redTherapyRemainingMs", bleManager.getCountdownRemainingMs(6));
        controls.put("blueTherapyRemainingMs", bleManager.getCountdownRemainingMs(7));
        controls.put("nebulizerRemainingMs", bleManager.getCountdownRemainingMs(10));
        controls.put("anionRemainingMs", bleManager.getCountdownRemainingMs(11));
        controls.put("uvRemainingMs", bleManager.getCountdownRemainingMs(12));
        // ★ 不限时(红外/蓝光 65536)没有倒计时终点，前端据此显示“常开”，
        //   不能再靠分钟数猜测（65536 会被误判成 24h）。
        controls.put("redTherapyUnlimited", bleManager.isTimedControlUnlimited(6));
        controls.put("blueTherapyUnlimited", bleManager.isTimedControlUnlimited(7));
        controls.put("nebulizerUnlimited", bleManager.isTimedControlUnlimited(10));
        controls.put("anionUnlimited", bleManager.isTimedControlUnlimited(11));
        controls.put("uvUnlimited", bleManager.isTimedControlUnlimited(12));
        host.put("controls", controls);
        return host;
    }

    private JSONObject buildEnvironmentZoneJson(String zone) throws JSONException {
        JSONObject environment = new JSONObject();
        environment.put("temp", safeJsonText(bleManager.getCabinTempText(zone)));
        environment.put("oxygen", safeJsonText(bleManager.getOxygenText(zone)));
        environment.put("humidity", safeJsonText(bleManager.getHumidityText(zone)));
        environment.put("co2", safeJsonText(bleManager.getCo2Text(zone)));
        environment.put("updatedAt", bleManager.getEnvironmentUpdatedAt(zone));
        return environment;
    }

    private void putControl(JSONObject controls, String key, int index) throws JSONException {
        if (index == 2) {
            controls.put(key, safeJsonText(dashboardView.getResolvedMonitorLevelLabel()));
        } else {
            controls.put(key, safeJsonText(bleManager.getControlValue(index)));
        }
        controls.put(key + "On", bleManager.isControlOn(index));
    }

    private JSONArray buildHostDevicesJson() throws JSONException {
        JSONArray array = new JSONArray();
        List<BleManager.DeviceItem> devices = bleManager.getDevices();
        for (BleManager.DeviceItem item : devices) {
            JSONObject device = new JSONObject();
            device.put("id", item.deviceId);
            device.put("deviceId", item.deviceId);
            device.put("name", item.deviceName);
            device.put("deviceName", item.deviceName);
            device.put("rssi", item.rssi);
            array.put(device);
        }
        return array;
    }

    private JSONObject buildMonitorStateJson() throws JSONException {
        JSONObject monitor = new JSONObject();
        monitor.put("connected", am4100Manager.isConnected());
        monitor.put("scanning", am4100Manager.isScanning());
        monitor.put("ready", am4100Manager.isProtocolReady());
        monitor.put("state", am4100Manager.getStateText());
        monitor.put("deviceName", safeJsonText(am4100Manager.getConnectedDeviceName()));
        monitor.put("deviceId", safeJsonText(am4100Manager.getConnectedDeviceId()));
        monitor.put("devices", buildMonitorDevicesJson());
        monitor.put("heartRate", safeJsonText(am4100Manager.getHeartRateText()));
        monitor.put("bloodPressure", safeJsonText(am4100Manager.getBloodPressureText()));
        monitor.put("map", safeJsonText(am4100Manager.getMapText()));
        monitor.put("spo2", safeJsonText(am4100Manager.getSpo2Text()));
        monitor.put("pulse", safeJsonText(am4100Manager.getPulseRateText()));
        monitor.put("pulseRate", safeJsonText(am4100Manager.getPulseRateText()));
        monitor.put("bodyTemp", safeJsonText(am4100Manager.getBodyTempText()));
        monitor.put("resp", safeJsonText(am4100Manager.getRespText()));
        monitor.put("ambientTemp", safeJsonText(am4100Manager.getAmbientTempText()));
        monitor.put("objectTemp", safeJsonText(am4100Manager.getObjectTempText()));
        monitor.put("summary", safeJsonText(am4100Manager.getLastFrameSummary()));
        monitor.put("lastError", safeJsonText(am4100Manager.getLastError()));
        return monitor;
    }

    /** ★ 2026-10-08：改包内可见，供 IcuDashboardView 推 ble.monitor.devices 给 H5 蓝牙选择弹窗 */
    JSONArray buildMonitorDevicesJson() throws JSONException {
        JSONArray array = new JSONArray();
        List<Am4100Manager.DeviceItem> devices = am4100Manager.getDevices();
        for (Am4100Manager.DeviceItem item : devices) {
            JSONObject device = new JSONObject();
            device.put("id", item.deviceId);
            device.put("deviceId", item.deviceId);
            device.put("name", item.deviceName);
            device.put("deviceName", item.deviceName);
            device.put("rssi", item.rssi);
            array.put(device);
        }
        return array;
    }

    /** ★ 任务13：改为包内可见，供 firstPhaseState 输出 wifi 状态给 H5（R53） */
    JSONObject buildWifiStateJson() throws JSONException {
        JSONObject wifi = new JSONObject();
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wifiManager == null) {
            wifi.put("enabled", false);
            wifi.put("current", "");
            wifi.put("networks", new JSONArray());
            return wifi;
        }
        wifi.put("enabled", wifiManager.isWifiEnabled());
        WifiInfo info = wifiManager.getConnectionInfo();
        String current = info == null ? "" : cleanSsid(info.getSSID());
        // ★ 2026-10-08：Android 10+ 读 SSID 需「定位权限已授权 + 系统定位开启」，否则恒为 <unknown ssid>
        if (wifiManager.isWifiEnabled() && current.isEmpty()) {
            boolean fineLoc = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            if (!fineLoc) {
                wifi.put("needLocPerm", true);
                maybeRequestLocationPermission();
            } else {
                LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                if (lm != null && !lm.isLocationEnabled()) {
                    wifi.put("locOff", true);
                }
            }
        }
        wifi.put("current", current);
        wifi.put("networks", new JSONArray());
        return wifi;
    }

    /** WiFi SSID 读不到时补弹定位权限（60s 节流，避免每次状态构建都弹窗） */
    private long lastWifiLocPermReqAt = 0L;

    private void maybeRequestLocationPermission() {
        long now = System.currentTimeMillis();
        if (now - lastWifiLocPermReqAt < 60000L) {
            return;
        }
        lastWifiLocPermReqAt = now;
        if (Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_BLE_PERMISSIONS);
        }
    }

    private String cleanSsid(String ssid) {
        if (ssid == null) {
            return "";
        }
        if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length() > 1) {
            return ssid.substring(1, ssid.length() - 1);
        }
        return "<unknown ssid>".equals(ssid) ? "" : ssid;
    }

    private String safeJsonText(String value) {
        return value == null ? "" : value;
    }

    private final class LanhuNativeBridge {
        @JavascriptInterface
        public boolean isLoggedIn() {
            return dashboardView.isLoggedIn();
        }

        @JavascriptInterface
        public boolean login(final String name, final String password) {
            return dashboardView.webLogin(name, password);
        }

        @JavascriptInterface
        public String register(final String name, final String password, final String organization) {
            return dashboardView.webRegister(name, password, organization);
        }

        @JavascriptInterface
        public void logout() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dashboardView.webLogout();
                }
            });
        }

        @JavascriptInterface
        public String bleState() {
            return buildBleStateJson();
        }

        @JavascriptInterface
        public String thermalState() {
            return thermalConnectionState;
        }

        @JavascriptInterface
        public String thermalDevices() {
            return buildThermalDevicesJson();
        }

        @JavascriptInterface
        public void thermalFullscreen(final boolean visible) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setThermalFullscreenVisible(visible);
                }
            });
        }

        @JavascriptInterface
        public void thermalAction(final String action, final String value) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if ("scan".equals(action)) {
                        connectThermalBridge();
                    } else if ("connect".equals(action)) {
                        connectThermalDevice(value);
                    } else if ("disconnect".equals(action)) {
                        thermalConnectRequested = false;
                        thermalBleManager.stop();
                        onThermalConnectionChanged("红外测温已手动断开");
                    }
                }
            });
        }

        @JavascriptInterface
        public void bleAction(final String target, final String action, final String value) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleBleAction(target, action, value);
                    notifyLanhuStateChanged();
                }
            });
        }

        @JavascriptInterface
        public String firstPhaseState() {
            return dashboardView.buildFirstPhaseStateJson();
        }

        @JavascriptInterface
        public String patientState() {
            return dashboardView.buildPatientStateJson();
        }

        @JavascriptInterface
        public void syncTempPatient(final String payload) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dashboardView.syncTempPatientFromWeb(payload);
                    notifyLanhuStateChanged();
                }
            });
        }

        @JavascriptInterface
        public void action(final String action, final String text, final String path) {
            android.util.Log.d("ICU-Native", "action='" + action + "' text='" + text + "' path='" + path + "'");
            /* ★ 2026-10-08 日志页·通信记录：H5→native 方向（只读记录，不影响分发逻辑） */
            if (dashboardView != null) {
                dashboardView.appendCommLog("H5→native", action, text);
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // “保存医院信息”需要把页面内三个 input 的当前值读上来交给 native，
                    // 这里直接处理，避免走 performNativeAction 后还要回 WebView 拿值。
                    if ("organization_save".equals(action)) {
                        handleOrganizationSaveFromLanhu();
                        return;
                    }
                    if ("settings_save".equals(action)) {
                        dashboardView.applyS5SettingsFromText(text);
                        return;
                    }
                    if ("camera_preview_pause".equals(action)) {
                        pauseCameraPreviewForPrintPreview();
                        return;
                    }
                    if ("camera_preview_resume".equals(action)) {
                        resumeCameraPreviewAfterPrintPreview();
                        return;
                    }
                    // ★ V1.02 S8 退出软件（管理员可用）：退出 App 回到系统桌面。
                    if ("exit_app".equals(action)) {
                        dashboardView.appendUserLog("系统", "退出软件", "用户点击退出");
                        finishAffinity();
                        return;
                    }
                    // ★ V1.02 S3 回顾组合查询：H5 通过 text 传 JSON 筛选条件（住院号/宠物名/宠物主人/
                    //   联系电话/物种/主治医生/护疗时间范围），空条件或空 payload 视为重置。
                    if ("review_query".equals(action)) {
                        dashboardView.applyReviewQuery(text == null ? "" : text);
                        return;
                    }
                    if ("review_query_reset".equals(action)) {
                        dashboardView.resetReviewQuery();
                        return;
                    }
                    // ★ 任务13 用户管理（R71-R73）：payload 走 text
                    if ("account_save".equals(action)) {
                        String msg = dashboardView.saveAccountFromText(text == null ? "" : text);
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        notifyLanhuStateChanged();
                        return;
                    }
                    if ("account_delete".equals(action)) {
                        String msg = dashboardView.deleteAccountFromText(text == null ? "" : text);
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        notifyLanhuStateChanged();
                        return;
                    }
                    // ★ 任务13 连接开关（R53-R55）
                    if ("wifi_toggle".equals(action)) {
                        dashboardView.toggleWifiFromText(text == null ? "" : text);
                        notifyLanhuStateChanged(); // wifi 开关结果要回推，H5 开关态才跟手
                        return;
                    }
                    if ("host_ble_toggle".equals(action)) {
                        dashboardView.toggleHostBleFromText(text == null ? "" : text);
                        notifyLanhuStateChanged();
                        return;
                    }
                    if ("monitor_ble_toggle".equals(action)) {
                        dashboardView.toggleMonitorBleFromText(text == null ? "" : text);
                        notifyLanhuStateChanged();
                        return;
                    }
                    // ★ 任务22：监护页血压「实时⇄物理」→ BPM 血压仪 BLE 命令（manual/auto）
                    if ("bpm_mode".equals(action)) {
                        dashboardView.handleBpmMode(text == null ? "" : text);
                        notifyLanhuStateChanged();
                        return;
                    }
                    // ★ 任务29（#26c）：治疗记录单编辑保存，payload 走 text（JSON）
                    if ("update_record".equals(action)) {
                        String msg = dashboardView.updateRecordFromText(text == null ? "" : text);
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                        notifyLanhuStateChanged();
                        return;
                    }
                    dashboardView.setCurrentLanhuPath(path);
                    if (!dashboardView.performNativeAction(action)) {
                        Toast.makeText(MainActivity.this, safeJsonText(text), Toast.LENGTH_SHORT).show();
                    }
                    notifyLanhuStateChanged();
                }
            });
        }

        @JavascriptInterface
        public void cameraPreview(final float left, final float top, final float width, final float height,
                                  final float viewportWidth, final float viewportHeight, final boolean visible) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateLanhuCameraPreview(left, top, width, height, viewportWidth, viewportHeight, visible);
                }
            });
        }

        @JavascriptInterface
        public boolean cameraConfigured() {
            return dashboardView.isCameraConfiguredForWeb();
        }

        /**
         * 返回教程页所需的相册状态 JSON：{permissionGranted, thumbnails[<=6], videoIds[<=6], firstLoad}。
         */
        @JavascriptInterface
        public String tutorialState() {
            try {
                JSONObject root = new JSONObject();
                root.put("permissionGranted", hasTutorialMediaPermission());
                root.put("canAskAgain", canAskTutorialPermission());
                root.put("thumbnails", queryTutorialThumbnails());
                root.put("videoIds", queryTutorialVideoIds());
                root.put("slots", dashboardView.getTutorialSlotsJson());
                root.put("firstLoad", !dashboardView.hasRecordedTutorialFingerprint());
                return root.toString();
            } catch (Exception exception) {
                android.util.Log.e("ICU-Native", "tutorialState failed", exception);
                return "{}";
            }
        }

        /**
         * 由 JS 在第一次成功拿到教程页状态后调用，把视频列表指纹写入 SharedPreferences，
         * 用于后续 tutorialState().firstLoad 的判断（避免冷启动后误弹 Toast）。
         */
        @JavascriptInterface
        public void recordTutorialFingerprint(final String fingerprint) {
            if (TextUtils.isEmpty(fingerprint)) return;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dashboardView.recordTutorialFingerprint(fingerprint);
                }
            });
        }

        /**
         * 由教程页弹窗的"确认"按钮调用：一次性申请图片 + 视频读取权限。
         * 只有用户先点了确认才会走到这里，不再进页面就静默弹系统对话框。
         */
        @JavascriptInterface
        public void requestTutorialPermission() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.requestTutorialPermission();
                }
            });
        }

        /** 由弹窗的"去设置"按钮调用：跳到本应用的系统权限设置页。 */
        @JavascriptInterface
        public void openAppPermissionSettings() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.openAppPermissionSettings();
                }
            });
        }

        /**
         * ★ 新加：仪器状态保存。native 端会 evaluateJavascript 读 5 个 input，写 SP。
         */
        @JavascriptInterface
        public void deviceProfileSave() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleDeviceProfileSaveFromLanhu();
                }
            });
        }

        /**
         * ★ 新加：关于保存。native 端会 evaluateJavascript 读 textarea 值，写 SP。
         */
        @JavascriptInterface
        public void aboutSave() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleAboutSaveFromLanhu();
                }
            });
        }

        /**
         * ★ 新加：关于文本读取。供 JS 在打开页面时拉取已保存的内容。
         */
        @JavascriptInterface
        public String aboutText() {
            android.content.SharedPreferences prefs = getSharedPreferences("icu_about_settings", MODE_PRIVATE);
            return prefs.getString("about_text", "");
        }

        /**
         * ★ 新加：其他设置保存（A1~A4 升级配置）。native 端读 4 个 input，按 a1/a2 真下发 BLE，
         *   a3/a4 弹 Toast "暂未启用"。
         */
        @JavascriptInterface
        public void otherSettingsSave() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleOtherSettingsSaveFromLanhu();
                }
            });
        }

        /**
         * ★ 新加：管理员登录。native 端读用户名/密码，校验 LocalAccountStore，写 SP。
         */
        @JavascriptInterface
        public void adminLogin() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleAdminLoginFromLanhu();
                }
            });
        }
    }

    private void handleBleAction(String target, String action, String value) {
        if ("wifi".equals(target)) {
            if ("settings".equals(action) || "scan".equals(action)) {
                try {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                } catch (Exception exception) {
                    Toast.makeText(this, "无法打开系统 WiFi 设置", Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }
        if ("host".equals(target)) {
            handleHostBleAction(action, value);
            return;
        }
        if ("monitor".equals(target)) {
            handleMonitorBleAction(action, value);
        }
    }

    private void connectThermalBridge() {
        if (thermalBleManager == null) {
            return;
        }
        Toast.makeText(this, "正在手动连接红外测温桥", Toast.LENGTH_SHORT).show();
        if (!hasAllBlePermissions()) {
            thermalConnectRequested = true;
            requestBlePermissionsFromUi();
            return;
        }
        thermalConnectRequested = false;
        pendingThermalDeviceId = "";
        thermalBleManager.stop();
        thermalBleManager.start();
    }

    private void connectThermalDevice(String deviceId) {
        if (thermalBleManager == null || TextUtils.isEmpty(deviceId)) {
            return;
        }
        Toast.makeText(this, "正在连接所选红外测温桥", Toast.LENGTH_SHORT).show();
        if (!hasAllBlePermissions()) {
            pendingThermalDeviceId = deviceId;
            thermalConnectRequested = true;
            requestBlePermissionsFromUi();
            return;
        }
        thermalConnectRequested = false;
        pendingThermalDeviceId = "";
        thermalBleManager.connect(deviceId);
    }

    private String buildThermalDevicesJson() {
        JSONArray array = new JSONArray();
        if (thermalBleManager != null) {
            for (ThermalBleManager.DeviceItem device : thermalBleManager.getDevices()) {
                try {
                    JSONObject item = new JSONObject();
                    item.put("id", device.address);
                    item.put("name", device.name);
                    item.put("rssi", device.rssi);
                    array.put(item);
                } catch (JSONException ignored) {
                }
            }
        }
        return array.toString();
    }

    private void handleHostBleAction(String action, String value) {
        if ("scan".equals(action)) {
            if (hasAllBlePermissions()) {
                bleManager.startScan();
            } else {
                requestBlePermissionsFromUi();
            }
            return;
        }
        if ("stop".equals(action)) {
            bleManager.stopScan();
            return;
        }
        if ("connect".equals(action) && value != null && value.length() > 0) {
            bleManager.connect(value);
            return;
        }
        if ("disconnect".equals(action)) {
            bleManager.disconnect();
            return;
        }
        if ("readAll".equals(action)) {
            bleManager.readAllStatus();
        }
    }

    private void handleMonitorBleAction(String action, String value) {
        if ("scan".equals(action)) {
            if (hasAllBlePermissions()) {
                am4100Manager.startScan();
            } else {
                requestBlePermissionsFromUi();
            }
            return;
        }
        if ("stop".equals(action)) {
            am4100Manager.stopScan();
            return;
        }
        if ("connect".equals(action) && value != null && value.length() > 0) {
            am4100Manager.connect(value);
            return;
        }
        if ("disconnect".equals(action)) {
            am4100Manager.disconnect();
        }
    }

    /**
     * 在主 Activity 上跑一段 WebView JS 脚本，结果通过 callback 回传（异步）。
     * 供 IcuDashboardView 等 native 端组件读取 WebView 页面内的实时状态。
     */
    public void evaluateLanhuJs(String script, ValueCallback<String> callback) {
        if (lanhuWebView == null) {
            if (callback != null) callback.onReceiveValue(null);
            return;
        }
        lanhuWebView.evaluateJavascript(script, callback);
    }

    /**
     * 处理“保存医院信息”：从 WebView 里读出 .group_4/.group_5/.group_6 三个 input 当前值，
     * 交给 dashboardView 写入当前机构并落库。
     */
    private void handleOrganizationSaveFromLanhu() {
        if (lanhuWebView == null) {
            return;
        }
        String script =
                "(function(){" +
                // ★ 任务14：优先读 v2 SPA 的 data-org 输入框；取不到再回退 v1 选择器
                "var v2=function(k){var n=document.querySelector('input[data-org=\"'+k+'\"]');return n&&n.value?n.value:'';};" +
                "var v1=function(s){var n=document.querySelector(s);return n&&n.value?n.value:'';};" +
                "var pick=function(k,s){var a=v2(k);return a?a:v1(s);};" +
                "return JSON.stringify({name:pick('name','.group_4 input'),address:pick('address','.group_5 input'),phone:pick('phone','.group_6 input')});" +
                "})()";
        evaluateLanhuJs(script, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                final String name;
                final String address;
                final String phone;
                if (value == null || value.equals("null")) {
                    name = "";
                    address = "";
                    phone = "";
                } else {
                    try {
                        // Android WebView 会把 JS 返回的字符串包成 JSON 字符串
                        // (外层加引号、内部 " 转义为 \")。
                        // 先用 JSONTokener 解一次再判断类型,避免直接 new JSONObject 抛 "cannot be converted"。
                        JSONTokener tokener = new JSONTokener(value);
                        Object firstValue = tokener.nextValue();
                        JSONObject obj;
                        if (firstValue instanceof JSONObject) {
                            obj = (JSONObject) firstValue;
                        } else if (firstValue instanceof String) {
                            // Android 包了一层 JSON 字符串引号,内部才是真正的 JSON
                            obj = new JSONObject((String) firstValue);
                        } else {
                            android.util.Log.e("ICU-Native", "parse org inputs failed: unexpected type "
                                    + (firstValue == null ? "null" : firstValue.getClass().getName()));
                            return;
                        }
                        name = obj.optString("name", "");
                        address = obj.optString("address", "");
                        phone = obj.optString("phone", "");
                    } catch (JSONException e) {
                        android.util.Log.e("ICU-Native", "parse org inputs failed", e);
                        return;
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String result = dashboardView.webUpdateOrganization(name, address, phone);
                        Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                        notifyLanhuStateChanged();
                    }
                });
            }
        });
    }

    /**
     * ★ 新加：仪器状态保存。从 WebView 读 5 个 input（产品型号/机型/机号/生产日期/程序版本），
     *   调用 dashboardView.webUpdateDeviceProfile 写 SP + 触发 notifyLanhuStateChanged。
     */
    void handleDeviceProfileSaveFromLanhu() {
        if (lanhuWebView == null) {
            return;
        }
        String script =
                "(function(){" +
                "var v=function(s){var n=document.querySelector(s);var i=n&&n.querySelector('input');return i?i.value:'';};" +
                "return JSON.stringify({productModel:v('.text_14'),machineType:v('.box_3'),serialNo:v('.box_5'),manufactureDate:v('.section_2'),softwareVersion:v('.section_3')});" +
                "})()";
        evaluateLanhuJs(script, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                final String productModel, machineType, serialNo, manufactureDate, softwareVersion;
                if (value == null || value.equals("null")) {
                    productModel = machineType = serialNo = manufactureDate = softwareVersion = "";
                } else {
                    try {
                        JSONTokener tokener = new JSONTokener(value);
                        Object firstValue = tokener.nextValue();
                        JSONObject obj;
                        if (firstValue instanceof JSONObject) {
                            obj = (JSONObject) firstValue;
                        } else if (firstValue instanceof String) {
                            obj = new JSONObject((String) firstValue);
                        } else {
                            return;
                        }
                        productModel = obj.optString("productModel", "");
                        machineType = obj.optString("machineType", "");
                        serialNo = obj.optString("serialNo", "");
                        manufactureDate = obj.optString("manufactureDate", "");
                        softwareVersion = obj.optString("softwareVersion", "");
                    } catch (JSONException e) {
                        android.util.Log.e("ICU-Native", "parse device profile inputs failed", e);
                        return;
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String result = dashboardView.webUpdateDeviceProfile(productModel, machineType, serialNo, manufactureDate, softwareVersion);
                        Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                        notifyLanhuStateChanged();
                    }
                });
            }
        });
    }

    /**
     * ★ 新加：关于保存。从 WebView 读 textarea 值，写入 SP icu_about_settings。
     */
    void handleAboutSaveFromLanhu() {
        if (lanhuWebView == null) {
            return;
        }
        String script =
                "(function(){" +
                "var n=document.querySelector('.text-wrapper_2 textarea');" +
                "return n?JSON.stringify({text:n.value||''}):'null';" +
                "})()";
        evaluateLanhuJs(script, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                final String text;
                if (value == null || value.equals("null")) {
                    text = "";
                } else {
                    try {
                        JSONTokener tokener = new JSONTokener(value);
                        Object firstValue = tokener.nextValue();
                        JSONObject obj;
                        if (firstValue instanceof JSONObject) {
                            obj = (JSONObject) firstValue;
                        } else if (firstValue instanceof String) {
                            obj = new JSONObject((String) firstValue);
                        } else {
                            return;
                        }
                        text = obj.optString("text", "");
                    } catch (JSONException e) {
                        android.util.Log.e("ICU-Native", "parse about textarea failed", e);
                        return;
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        getSharedPreferences("icu_about_settings", MODE_PRIVATE)
                                .edit()
                                .putString("about_text", text)
                                .apply();
                        Toast.makeText(MainActivity.this, "关于已保存", Toast.LENGTH_SHORT).show();
                        notifyLanhuStateChanged();
                    }
                });
            }
        });
    }

    /**
     * ★ 新加：其他设置保存（A1~A4 升级配置）。a1/a2 真下发 BLE，a3/a4 占位 Toast。
     */
    void handleOtherSettingsSaveFromLanhu() {
        if (lanhuWebView == null) {
            return;
        }
        String script =
                "(function(){" +
                "var v=function(s){var n=document.querySelector(s);var i=n&&n.querySelector('input');return i?i.value:'';};" +
                "return JSON.stringify({a1:v('.text-wrapper_2'),a2:v('.box_4'),a3:v('.group_5'),a4:v('.group_7')});" +
                "})()";
        evaluateLanhuJs(script, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                final String a1, a2, a3, a4;
                if (value == null || value.equals("null")) {
                    a1 = a2 = a3 = a4 = "";
                } else {
                    try {
                        JSONTokener tokener = new JSONTokener(value);
                        Object firstValue = tokener.nextValue();
                        JSONObject obj;
                        if (firstValue instanceof JSONObject) {
                            obj = (JSONObject) firstValue;
                        } else if (firstValue instanceof String) {
                            obj = new JSONObject((String) firstValue);
                        } else {
                            return;
                        }
                        a1 = obj.optString("a1", "");
                        a2 = obj.optString("a2", "");
                        a3 = obj.optString("a3", "");
                        a4 = obj.optString("a4", "");
                    } catch (JSONException e) {
                        android.util.Log.e("ICU-Native", "parse other settings inputs failed", e);
                        return;
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean a1Sent = false, a2Sent = false;
                        if (!TextUtils.isEmpty(a1)) {
                            a1Sent = dashboardView.bleManager().upgradeMainBoard();
                            Toast.makeText(MainActivity.this, "A1 升级: " + a1 + " 已下发", Toast.LENGTH_SHORT).show();
                        }
                        if (!TextUtils.isEmpty(a2)) {
                            a2Sent = dashboardView.bleManager().upgradeControlBoard();
                            Toast.makeText(MainActivity.this, "A2 升级: " + a2 + " 已下发", Toast.LENGTH_SHORT).show();
                        }
                        if (!TextUtils.isEmpty(a3)) {
                            Toast.makeText(MainActivity.this, "A3 升级暂未启用（已保存）", Toast.LENGTH_SHORT).show();
                            getSharedPreferences("icu_other_settings", MODE_PRIVATE).edit().putString("a3_path", a3).apply();
                        }
                        if (!TextUtils.isEmpty(a4)) {
                            Toast.makeText(MainActivity.this, "A4 升级暂未启用（已保存）", Toast.LENGTH_SHORT).show();
                            getSharedPreferences("icu_other_settings", MODE_PRIVATE).edit().putString("a4_path", a4).apply();
                        }
                        notifyLanhuStateChanged();
                    }
                });
            }
        });
    }

    /**
     * ★ 新加：管理员登录。从 WebView 读用户名/密码，调用 LocalAccountStore 校验，写入 SP。
     */
    void handleAdminLoginFromLanhu() {
        if (lanhuWebView == null) {
            return;
        }
        String script =
                "(function(){" +
                "var v=function(s){var n=document.querySelector(s);var i=n&&n.querySelector('input');return i?i.value:'';};" +
                "return JSON.stringify({name:v('.group_2'),password:v('.box_7')});" +
                "})()";
        evaluateLanhuJs(script, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                final String name, password;
                if (value == null || value.equals("null")) {
                    name = password = "";
                } else {
                    try {
                        JSONTokener tokener = new JSONTokener(value);
                        Object firstValue = tokener.nextValue();
                        JSONObject obj;
                        if (firstValue instanceof JSONObject) {
                            obj = (JSONObject) firstValue;
                        } else if (firstValue instanceof String) {
                            obj = new JSONObject((String) firstValue);
                        } else {
                            return;
                        }
                        name = obj.optString("name", "");
                        password = obj.optString("password", "");
                    } catch (JSONException e) {
                        android.util.Log.e("ICU-Native", "parse admin login inputs failed", e);
                        return;
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean ok = dashboardView.webAdminLogin(name, password);
                        Toast.makeText(MainActivity.this,
                                ok ? "✅ 管理员已登录" : "❌ 账号或密码错误",
                                Toast.LENGTH_SHORT).show();
                        if (ok) {
                            getSharedPreferences("icu_admin_state", MODE_PRIVATE)
                                    .edit()
                                    .putString("admin_logged_in", name)
                                    .apply();
                        }
                        notifyLanhuStateChanged();
                    }
                });
            }
        });
    }

    /* ======================================================================
       ★ 2026-10-09 微信文件传输助手 APK 升级（移植自 test project 的 Kotlin demo）
       流程：全屏 WebView 打开 filehelper.weixin.qq.com（伪装桌面 UA）→
       拦截下载（DownloadListener / window.open 接管 / Blob JS hook 三路）→
       带 Cookie 子线程下载 → 校验包名+versionCode → PackageInstaller 调起安装。
       ====================================================================== */

    private static final String FILE_HELPER_URL = "https://filehelper.weixin.qq.com/";

    /* 传输助手网页版只放行电脑浏览器，需伪装成桌面版 Chrome */
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /* 网页可能用 JS 生成 Blob 触发下载（不经过 DownloadListener），
       注入该脚本把 Blob 内容转成 base64 交给原生层 */
    private static final String BLOB_HOOK_JS =
            "(function() {" +
            "  if (window.__apkHookInstalled) return 'already';" +
            "  window.__apkHookInstalled = true;" +
            "  var blobMap = {};" +
            "  var origCreate = URL.createObjectURL.bind(URL);" +
            "  URL.createObjectURL = function(obj) {" +
            "    var url = origCreate(obj);" +
            "    try { if (obj instanceof Blob) blobMap[url] = obj; } catch (e) {}" +
            "    return url;" +
            "  };" +
            "  function sendBlob(blob, name) {" +
            "    var reader = new FileReader();" +
            "    reader.onload = function() {" +
            "      AndroidUpdateBridge.onBlobDownload(String(reader.result), name || ('file_' + Date.now()));" +
            "    };" +
            "    reader.onerror = function() { AndroidUpdateBridge.onJsError('FileReader 读取失败'); };" +
            "    reader.readAsDataURL(blob);" +
            "  }" +
            "  function handleAnchor(a) {" +
            "    try {" +
            "      var href = a.href || '';" +
            "      if (href.indexOf('blob:') !== 0) return;" +
            "      var name = a.getAttribute('download') || '';" +
            "      AndroidUpdateBridge.onJsLog('捕获到 Blob 下载: ' + (name || '(无文件名)'));" +
            "      var blob = blobMap[href];" +
            "      if (blob) { sendBlob(blob, name); }" +
            "      else {" +
            "        fetch(href).then(function(r){ return r.blob(); }).then(function(b){ sendBlob(b, name); })" +
            "          .catch(function(err){ AndroidUpdateBridge.onJsError('Blob 读取失败: ' + err); });" +
            "      }" +
            "    } catch (e) { AndroidUpdateBridge.onJsError('handleAnchor: ' + e); }" +
            "  }" +
            "  document.addEventListener('click', function(e) {" +
            "    var el = e.target;" +
            "    while (el && el.tagName !== 'A') el = el.parentElement;" +
            "    if (el) handleAnchor(el);" +
            "  }, true);" +
            "  var origClick = HTMLAnchorElement.prototype.click;" +
            "  HTMLAnchorElement.prototype.click = function() {" +
            "    handleAnchor(this);" +
            "    return origClick.apply(this, arguments);" +
            "  };" +
            "  return 'ok';" +
            "})();";

    private LinearLayout updateOverlay;
    private WebView updateWebView;
    private TextView updateStatus;
    private ProgressBar updateProgress;
    private File pendingInstallApk;

    /** 由 upgrade-sw 页「选择文件」按钮触发（action = apk_update_open）。 */
    public void openApkUpdatePage() {
        if (updateOverlay == null) {
            buildUpdateOverlay();
        }
        updateOverlay.setVisibility(View.VISIBLE);
        updateWebView.loadUrl(FILE_HELPER_URL);
        setUpdateStatus("正在打开微信文件传输助手…");
    }

    void closeApkUpdatePage() {
        if (updateOverlay != null) {
            updateOverlay.setVisibility(View.GONE);
        }
        if (updateWebView != null) {
            updateWebView.stopLoading();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void buildUpdateOverlay() {
        updateOverlay = new LinearLayout(this);
        updateOverlay.setOrientation(LinearLayout.VERTICAL);
        updateOverlay.setBackgroundColor(Color.WHITE);
        updateOverlay.setVisibility(View.GONE);

        /* 标题栏：标题 + 状态 + 关闭 */
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.rgb(245, 247, 251));
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        header.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("检查更新 · 微信文件传输助手");
        title.setTextSize(16);
        title.setTextColor(Color.rgb(28, 36, 48));
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button close = new Button(this);
        close.setText("关闭");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeApkUpdatePage();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        updateOverlay.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        updateStatus = new TextView(this);
        updateStatus.setTextSize(13);
        updateStatus.setTextColor(Color.rgb(90, 100, 120));
        updateStatus.setPadding(pad, pad / 2, pad, pad / 2);
        updateOverlay.addView(updateStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        updateProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        updateProgress.setVisibility(View.GONE);
        updateOverlay.addView(updateProgress, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        updateWebView = new WebView(this);
        setupUpdateWebView(updateWebView);
        updateOverlay.addView(updateWebView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        rootView.addView(updateOverlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupUpdateWebView(final WebView webView) {
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUserAgentString(DESKTOP_UA);
        /* 桌面版网页在平板上的显示适配 */
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        /* 网页可能用 window.open 触发下载，需要接管新窗口请求 */
        settings.setSupportMultipleWindows(true);

        webView.addJavascriptInterface(new UpdateJsBridge(), "AndroidUpdateBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.evaluateJavascript(BLOB_HOOK_JS, null);
                setUpdateStatus("已打开文件传输助手。扫码登录后发送 APK，并在页面中点击该文件的下载按钮。");
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage msg) {
                if (msg.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    setUpdateStatus("页面 JS 错误：" + msg.message());
                }
                return true;
            }

            /* 接管 window.open / target=_blank：下载链接自己处理，普通链接在主 WebView 打开 */
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView temp = new WebView(view.getContext());
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                        String u = request.getUrl().toString();
                        String name = extractApkName(u);
                        if (name != null) {
                            startApkDownload(u, name);
                        } else {
                            setUpdateStatus("捕获到新窗口请求：" + u);
                            if (u.startsWith("http")) {
                                webView.loadUrl(u);
                            }
                        }
                        v.destroy();
                        return true;
                    }
                });
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }
        });

        webView.setDownloadListener(new android.webkit.DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                String rawName = URLUtil.guessFileName(url, contentDisposition, mimetype);
                String fileName = normalizeApkName(rawName);
                if (fileName != null) {
                    startApkDownload(url, fileName);
                } else {
                    Toast.makeText(MainActivity.this, "已忽略非 APK 文件：" + rawName, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public class UpdateJsBridge {
        @JavascriptInterface
        public void onBlobDownload(final String dataUrl, final String fileName) {
            final String normalized = normalizeApkName(fileName);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (normalized == null) {
                        Toast.makeText(MainActivity.this, "已忽略非 APK 文件：" + fileName, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "已拦截到 APK 下载：" + normalized, Toast.LENGTH_SHORT).show();
                        setUpdateStatus("已捕获网页内文件 " + normalized + "，正在保存…");
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                saveBlobApk(dataUrl, normalized);
                            }
                        }, "apk-blob-save").start();
                    }
                }
            });
        }

        @JavascriptInterface
        public void onJsError(final String msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setUpdateStatus("JS 异常：" + msg);
                }
            });
        }

        @JavascriptInterface
        public void onJsLog(final String msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setUpdateStatus(msg);
                }
            });
        }
    }

    private void saveBlobApk(String dataUrl, String fileName) {
        try {
            String base64 = dataUrl.substring(dataUrl.indexOf("base64,") < 0 ? 0 : dataUrl.indexOf("base64,"));
            base64 = base64.replace("base64,", "");
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            File target = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);
            if (target.getParentFile() != null) {
                target.getParentFile().mkdirs();
            }
            java.io.FileOutputStream fos = new java.io.FileOutputStream(target);
            fos.write(bytes);
            fos.close();
            final long size = bytes.length;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "下载完成（" + formatSize(size) + "）", Toast.LENGTH_SHORT).show();
                    onApkDownloaded(target);
                }
            });
        } catch (final Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setUpdateStatus("保存失败：" + e.getMessage());
                }
            });
        }
    }

    private void startApkDownload(String url, final String fileName) {
        Toast.makeText(this, "已拦截到 APK 下载：" + fileName, Toast.LENGTH_SHORT).show();
        setUpdateStatus("拦截到 APK：" + fileName + "，开始下载…");
        updateProgress.setVisibility(View.VISIBLE);
        updateProgress.setIndeterminate(true);
        updateProgress.setProgress(0);

        String cookie = CookieManager.getInstance().getCookie(url);
        String userAgent = updateWebView.getSettings().getUserAgentString();
        final File target = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName);

        ApkDownloader.download(url, cookie, userAgent, target, new ApkDownloader.Callback() {
            @Override
            public void onProgress(final long downloadedBytes, final long totalBytes) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (totalBytes > 0) {
                            updateProgress.setIndeterminate(false);
                            updateProgress.setProgress((int) ((downloadedBytes * 100) / totalBytes));
                            setUpdateStatus("正在下载 " + fileName + "：" + formatSize(downloadedBytes) + " / " + formatSize(totalBytes));
                        } else {
                            updateProgress.setIndeterminate(true);
                            setUpdateStatus("正在下载 " + fileName + "：已下载 " + formatSize(downloadedBytes));
                        }
                    }
                });
            }

            @Override
            public void onComplete(final File file, final Exception error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateProgress.setVisibility(View.GONE);
                        updateProgress.setIndeterminate(false);
                        if (error != null) {
                            setUpdateStatus("下载失败：" + error.getMessage());
                            Toast.makeText(MainActivity.this, "APK 下载失败，请重试", Toast.LENGTH_LONG).show();
                        } else if (file != null) {
                            Toast.makeText(MainActivity.this, "下载完成：" + fileName, Toast.LENGTH_SHORT).show();
                            onApkDownloaded(file);
                        }
                    }
                });
            }
        });
    }

    /* 微信会把 APK 改名为 xxx.apk.1 甚至 xxx.apk.1.1，保存前剥掉所有数字后缀 */
    private String normalizeApkName(String raw) {
        if (raw == null) return null;
        if (raw.toLowerCase().endsWith(".apk")) return raw;
        String stripped = raw.replaceAll("(?i)(\\.apk)(\\.\\d+)+$", "$1");
        return stripped.toLowerCase().endsWith(".apk") ? stripped : null;
    }

    /* 微信媒体下载链接的文件名在 encryfilename 参数里，退而求其次再从 URL 猜测 */
    private String extractApkName(String url) {
        if (url == null || !url.startsWith("http")) return null;
        String encry = Uri.parse(url).getQueryParameter("encryfilename");
        if (encry != null) {
            String n = normalizeApkName(encry);
            if (n != null) return n;
        }
        return normalizeApkName(URLUtil.guessFileName(url, null, null));
    }

    private void onApkDownloaded(File file) {
        ApkInstaller.CheckResult result = ApkInstaller.checkUpdate(this, file);
        switch (result.code) {
            case ApkInstaller.CHECK_NEWER:
                setUpdateStatus("发现新版本 v" + result.newVersionName + "（当前 v" + result.currentVersionName + "），开始安装…");
                requestApkInstall(file);
                break;
            case ApkInstaller.CHECK_NOT_NEWER:
                setUpdateStatus("下载的 APK（v" + result.newVersionName + "）不高于当前版本，已忽略。");
                break;
            case ApkInstaller.CHECK_WRONG_PACKAGE:
                setUpdateStatus("APK 包名（" + result.apkPackage + "）与本应用不一致，已忽略。");
                break;
            default:
                setUpdateStatus("APK 文件无法解析，可能下载不完整，请重试。");
                break;
        }
    }

    private void requestApkInstall(File file) {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            pendingInstallApk = file;
            Toast.makeText(this, "请允许本应用安装未知来源应用", Toast.LENGTH_LONG).show();
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())), REQUEST_UNKNOWN_SOURCES);
        } else {
            doApkInstall(file);
        }
    }

    /* 未知来源授权返回后继续（onActivityResult REQUEST_UNKNOWN_SOURCES） */
    void resumePendingApkInstall() {
        File apk = pendingInstallApk;
        pendingInstallApk = null;
        if (apk == null) return;
        if (Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls()) {
            doApkInstall(apk);
        } else {
            Toast.makeText(this, "未授予安装权限，无法更新", Toast.LENGTH_LONG).show();
        }
    }

    private void doApkInstall(File file) {
        try {
            ApkInstaller.install(this, file);
            setUpdateStatus("已提交安装，请在系统弹窗中确认。");
        } catch (Exception e) {
            setUpdateStatus("调起安装失败：" + e.getMessage());
        }
    }

    private void setUpdateStatus(final String msg) {
        if (updateStatus != null) {
            updateStatus.setText(msg);
        }
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024 * 1024) return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        if (bytes >= 1024) return String.format("%.1f KB", bytes / 1024.0);
        return bytes + " B";
    }
}
