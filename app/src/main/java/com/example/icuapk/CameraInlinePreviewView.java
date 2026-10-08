package com.example.icuapk;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

final class CameraInlinePreviewView extends FrameLayout {
    private final TextureView textureView;
    private final TextView statusText;
    private String[] streamUrls = new String[0];
    private String currentKey = "";
    private int streamIndex;
    private boolean active;
    private boolean surfaceReady;
    private Surface previewSurface;
    private Bitmap lastFrameBitmap;
    private CameraStreamActivity.RtspH264Client rtspClient;

    CameraInlinePreviewView(MainActivity activity) {
        super(activity);
        setBackgroundColor(Color.BLACK);
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
    }

    void setPreview(String[] urls) {
        String[] cleaned = sanitizeUrls(urls);
        String nextKey = join(cleaned);
        if (nextKey.equals(currentKey)) {
            return;
        }
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
        callback.onFrame(bitmap);
    }

    void stopPreview() {
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
            return;
        }
        startPlayback();
    }

    private void startPlayback() {
        if (streamIndex >= streamUrls.length) {
            statusText.setText("摄像头候选地址都无法播放");
            return;
        }
        if (previewSurface == null || !previewSurface.isValid()) {
            statusText.setText("摄像头画布未准备好");
            return;
        }
        final String url = streamUrls[streamIndex];
        statusText.setText("内嵌预览连接中 " + (streamIndex + 1) + "/" + streamUrls.length + ": " + maskPassword(url));
        stopPreview();
        rtspClient = new CameraStreamActivity.RtspH264Client(url, previewSurface, new CameraStreamActivity.RtspH264Client.Listener() {
            @Override
            public void onStatus(final String message) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText(message);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (!tryNextUrl(message)) {
                            statusText.setText("内嵌预览失败: " + message + "\n当前地址: " + maskPassword(url));
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
