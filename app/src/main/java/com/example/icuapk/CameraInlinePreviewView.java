package com.example.icuapk;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class CameraInlinePreviewView extends FrameLayout {
    private final TextureView textureView;
    private final TextView statusText;
    /* ★ 2026-10-15 V52：失败操作条（重试/重新配置）——此前失败后只有一行错误文字，无法重新输入配置 */
    private final LinearLayout failureBar;
    private final MainActivity host;
    private String[] streamUrls = new String[0];
    private String currentKey = "";
    private int streamIndex;
    private boolean active;
    private boolean surfaceReady;
    private Surface previewSurface;
    private Bitmap lastFrameBitmap;
    private CameraStreamActivity.RtspH264Client rtspClient;
    /* ★ 2026-10-16 V59：用户点「取消连接」后的闩锁——停留在本页期间不再自动拉起预览，
       离开页面（H5 上报 visible=false）或改地址/点重新连接时解除（见 MainActivity 调用点） */
    private boolean cancelledByUser;
    /* ★ 2026-10-16 V59：「取消连接」回调——内嵌预览默认自行隐藏；全屏实例由宿主改为退出全屏 */
    interface CancelListener { void onCancel(); }
    private CancelListener cancelListener;

    /* ★ 2026-10-19 V60：画面翻转（纯显示层 TextureView 变换，不需要摄像头支持）——
       0 正常 / 1 水平镜像 / 2 垂直翻转 / 3 旋转180°（=水平+垂直）；截图/原图同步跟随（WYSIWYG） */
    private int flipMode;

    /* ★ 2026-10-19 V60：录制状态——绑定当前 RTSP 客户端（预览停止即自动停录保存） */
    interface RecordingListener {
        void onAutoSaved(File file, int frames, long durationMs);
        void onFailed(String reason);
    }
    private CameraRecorder cameraRecorder;
    private long recordingStartedAt;
    private RecordingListener recordingListener;

    void setCancelListener(CancelListener listener) { cancelListener = listener; }
    boolean isCancelledByUser() { return cancelledByUser; }
    void clearCancelledByUser() { cancelledByUser = false; }

    /* ★ 2026-10-19 V60：翻转模式（宿主 MainActivity 统一下发到内嵌+全屏两个实例） */
    void setFlipMode(int mode) {
        int normalized = mode < 0 ? 0 : mode % 4;
        if (flipMode == normalized) {
            return;
        }
        flipMode = normalized;
        applyFlipTransform();
    }

    int getFlipMode() { return flipMode; }

    private void applyFlipTransform() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float sx = (flipMode == 1 || flipMode == 3) ? -1f : 1f;
        float sy = (flipMode == 2 || flipMode == 3) ? -1f : 1f;
        Matrix matrix = new Matrix();
        matrix.setScale(sx, sy, w / 2f, h / 2f);
        textureView.setTransform(matrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyFlipTransform();   // 变换带中心点，尺寸变化（内嵌↔全屏边界更新）后须重设
    }

    /* ★ 2026-10-19 V60：截图/原图与显示一致（TextureView.getBitmap 不含 setTransform，需手工翻转） */
    private Bitmap applyFlipToBitmap(Bitmap src) {
        if (flipMode == 0 || src == null) {
            return src;
        }
        float sx = (flipMode == 1 || flipMode == 3) ? -1f : 1f;
        float sy = (flipMode == 2 || flipMode == 3) ? -1f : 1f;
        Matrix matrix = new Matrix();
        matrix.setScale(sx, sy, src.getWidth() / 2f, src.getHeight() / 2f);
        Bitmap out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), matrix, false);
        if (out != src && !src.isRecycled()) {
            src.recycle();
        }
        return out;
    }

    /* ★ 2026-10-19 V60：录制控制（false=预览未出画面/码流非H264，由调用方提示） */
    void setRecordingListener(RecordingListener listener) { recordingListener = listener; }
    boolean isRecording() { return cameraRecorder != null; }
    long recordingStartedAt() { return recordingStartedAt; }

    boolean startRecording(File outFile) {
        if (cameraRecorder != null) {
            return true;
        }
        if (rtspClient == null || !rtspClient.supportsRecording()) {
            return false;
        }
        int[] size = rtspClient.recordingSize();
        cameraRecorder = new CameraRecorder(outFile, size[0], size[1],
                rtspClient.recordingSps(), rtspClient.recordingPps());
        recordingStartedAt = System.currentTimeMillis();
        rtspClient.setRecordingSink(new CameraStreamActivity.RtspH264Client.RecordingSink() {
            @Override
            public void onAccessUnit(byte[] sample, boolean keyFrame, long ptsUs) {
                CameraRecorder recorder = cameraRecorder;
                if (recorder != null) {
                    recorder.onAccessUnit(sample, keyFrame, ptsUs);
                }
            }
        });
        return true;
    }

    /* 停止录制并封盘；返回的 CameraRecorder 可读取 frames/durationMs/outFile（0 帧时文件已自删） */
    CameraRecorder stopRecording() {
        CameraRecorder recorder = cameraRecorder;
        if (recorder == null) {
            return null;
        }
        cameraRecorder = null;
        if (rtspClient != null) {
            rtspClient.setRecordingSink(null);
        }
        recorder.stop();
        return recorder;
    }

    CameraInlinePreviewView(MainActivity activity) {
        super(activity);
        host = activity;
        /* ★ 2026-10-16 V59：背景由纯黑改设计稿视频区深灰(#4d4d4d)——连接中不再是大黑画面（对齐 9.png） */
        setBackgroundColor(Color.rgb(0x4d, 0x4d, 0x4d));
        setVisibility(View.GONE);
        setClickable(false);

        textureView = new TextureView(activity);
        textureView.setKeepScreenOn(true);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                surfaceReady = true;
                previewSurface = new Surface(surface);
                maybeStart();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                surfaceReady = false;
                stopPreview();
                releasePreviewSurface();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            }
        });
        addView(textureView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        statusText = new TextView(activity);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(12);
        statusText.setSingleLine(false);
        statusText.setPadding(dp(10), dp(6), dp(10), dp(6));
        statusText.setBackgroundColor(Color.argb(150, 0, 0, 0));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        addView(statusText, statusParams);

        /* ★ 2026-10-15 V52：失败操作条 —— 「重试」重连当前配置；「重新配置」直接打开摄像头配置弹窗重新输入 */
        /* ★ 2026-10-15 V52：失败操作条 —— 「重试」重连当前配置；「重新配置」直接打开摄像头配置弹窗重新输入
           ★ 2026-10-16 V59：扩为三键「重新连接/取消连接/重新配置」，且连接中也常驻（此前仅失败才露出，
           连接卡住时用户无任何可操作入口）；收到首帧（解码器启动）后自动收起 */
        failureBar = new LinearLayout(activity);
        failureBar.setOrientation(LinearLayout.HORIZONTAL);
        failureBar.setGravity(Gravity.CENTER);
        failureBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        failureBar.setBackgroundColor(Color.argb(170, 0, 0, 0));
        failureBar.setVisibility(View.GONE);
        Button retryButton = new Button(activity);
        retryButton.setText("重新连接");
        retryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelledByUser = false;
                restartPreview(streamUrls);
            }
        });
        failureBar.addView(retryButton, new LinearLayout.LayoutParams(dp(118), dp(40)));
        Button cancelButton = new Button(activity);
        cancelButton.setText("取消连接");
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelledByUser = true;
                stopPreview();
                if (cancelListener != null) {
                    cancelListener.onCancel();   // 全屏实例：退出全屏回页面
                } else {
                    setActive(false);            // 内嵌实例：隐藏预览，露出 H5 灰色鱼眼占位
                }
            }
        });
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(118), dp(40));
        cancelParams.leftMargin = dp(12);
        failureBar.addView(cancelButton, cancelParams);
        Button configButton = new Button(activity);
        configButton.setText("重新配置");
        configButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelledByUser = false;
                host.openCameraConfigDialog();
            }
        });
        LinearLayout.LayoutParams configParams = new LinearLayout.LayoutParams(dp(118), dp(40));
        configParams.leftMargin = dp(12);
        failureBar.addView(configButton, configParams);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        addView(failureBar, barParams);
    }

    private void showFailureActions() {
        failureBar.setVisibility(View.VISIBLE);
        failureBar.bringToFront();
    }

    void setPreview(String[] urls) {
        String[] cleaned = sanitizeUrls(urls);
        String nextKey = join(cleaned);
        if (nextKey.equals(currentKey)) {
            return;
        }
        cancelledByUser = false;   // V59：地址变更（重新配置保存）自动解除取消闩锁
        currentKey = nextKey;
        streamUrls = cleaned;
        streamIndex = 0;
        stopPreview();
        maybeStart();
    }

    void restartPreview(String[] urls) {
        currentKey = "";
        streamUrls = new String[0];
        streamIndex = 0;
        stopPreview();
        setPreview(urls);
    }

    void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
        setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) {
            bringToFront();
            maybeStart();
        } else {
            cacheCurrentFrame();
            stopPreview();
        }
    }

    void captureFrame(IcuDashboardView.CameraFrameCallback callback) {
        Bitmap bitmap = null;
        if (textureView.isAvailable() && getWidth() > 0 && getHeight() > 0) {
            bitmap = textureView.getBitmap();
        }
        if (bitmap == null && lastFrameBitmap != null && !lastFrameBitmap.isRecycled()) {
            bitmap = lastFrameBitmap.copy(Bitmap.Config.ARGB_8888, false);
        }
        if (bitmap == null) {
            callback.onError("还没有可截图的摄像头画面，请先停留在实况预览几秒");
            return;
        }
        callback.onFrame(applyFlipToBitmap(bitmap));   // V60：截图与显示方向一致
    }

    void stopPreview() {
        /* ★ 2026-10-19 V60：预览停止（切页/取消/打印暂停/全屏关闭）时若在录制，
           自动停录封盘并回调宿主登记元数据——不让录像无声丢失 */
        if (cameraRecorder != null) {
            CameraRecorder recorder = stopRecording();
            if (recordingListener != null && recorder != null) {
                if (recorder.frames() > 0) {
                    recordingListener.onAutoSaved(recorder.outFile(), recorder.frames(), recorder.durationMs());
                } else {
                    recordingListener.onFailed("未收到视频帧");
                }
            }
        }
        if (rtspClient != null) {
            rtspClient.stop();
            rtspClient = null;
        }
    }

    private void maybeStart() {
        if (!active || !surfaceReady || rtspClient != null) {
            return;
        }
        if (streamUrls.length == 0) {
            statusText.setText("未配置摄像头地址");
            showFailureActions();   // V52：未配置也给「重新配置」入口
            return;
        }
        startPlayback();
    }

    private void startPlayback() {
        /* ★ 2026-10-16 V59：连接中也露出操作条（重新连接/取消连接/重新配置），
           不再等全部失败才出现；收到首帧（解码器启动）后收起 */
        showFailureActions();
        if (streamIndex >= streamUrls.length) {
            statusText.setText("摄像头候选地址都无法播放");
            showFailureActions();
            return;
        }
        if (previewSurface == null || !previewSurface.isValid()) {
            statusText.setText("摄像头画布未准备好");
            return;
        }
        final String url = streamUrls[streamIndex];
        statusText.setVisibility(View.VISIBLE);   // V59：上次成功播放时被隐藏，重连要恢复
        statusText.setText("内嵌预览连接中 " + (streamIndex + 1) + "/" + streamUrls.length + ": " + maskPassword(url));
        stopPreview();
        rtspClient = new CameraStreamActivity.RtspH264Client(url, previewSurface, new CameraStreamActivity.RtspH264Client.Listener() {
            @Override
            public void onStatus(final String message) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText(message);
                        /* ★ V59：解码器已启动 = 视频帧即将到来，收起操作条与状态文字 */
                        if (message != null && message.contains("解码器已启动")) {
                            failureBar.setVisibility(View.GONE);
                            statusText.setVisibility(View.GONE);
                        }
                    }
                });
            }

            @Override
            public void onError(final String message) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setVisibility(View.VISIBLE);   // V59：失败时确保状态文字可见
                        if (!tryNextUrl(message)) {
                            statusText.setText("内嵌预览失败: " + message + "\n当前地址: " + maskPassword(url));
                            showFailureActions();   // V52：全部地址失败 → 露出 重试/重新配置
                        }
                    }
                });
            }
        });
        new Thread(rtspClient, "tp-link-inline-rtsp").start();
    }

    private boolean tryNextUrl(String reason) {
        stopPreview();
        if (streamIndex + 1 >= streamUrls.length) {
            return false;
        }
        streamIndex++;
        statusText.setText("正在切换备用摄像头地址 " + (streamIndex + 1) + "/" + streamUrls.length + "\n" + reason);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                maybeStart();
            }
        }, 700);
        return true;
    }

    private void cacheCurrentFrame() {
        if (!textureView.isAvailable() || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        Bitmap bitmap = textureView.getBitmap();
        if (bitmap == null) {
            return;
        }
        if (lastFrameBitmap != null && !lastFrameBitmap.isRecycled()) {
            lastFrameBitmap.recycle();
        }
        lastFrameBitmap = bitmap;
    }

    private void releasePreviewSurface() {
        if (previewSurface != null) {
            previewSurface.release();
            previewSurface = null;
        }
    }

    private String[] sanitizeUrls(String[] urls) {
        List<String> cleaned = new ArrayList<>();
        if (urls != null) {
            for (String url : urls) {
                if (TextUtils.isEmpty(url)) {
                    continue;
                }
                String trimmed = url.trim();
                if (trimmed.length() > 0 && !cleaned.contains(trimmed)) {
                    cleaned.add(trimmed);
                }
            }
        }
        return cleaned.toArray(new String[0]);
    }

    private String join(String[] urls) {
        StringBuilder builder = new StringBuilder();
        for (String url : urls) {
            builder.append(url).append('\n');
        }
        return builder.toString();
    }

    private String maskPassword(String url) {
        if (TextUtils.isEmpty(url)) {
            return "-";
        }
        int schemeEnd = url.indexOf("://");
        int at = url.indexOf('@');
        int colon = schemeEnd >= 0 ? url.indexOf(':', schemeEnd + 3) : -1;
        if (schemeEnd >= 0 && colon > schemeEnd && at > colon) {
            return url.substring(0, colon + 1) + "******" + url.substring(at);
        }
        return url;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
