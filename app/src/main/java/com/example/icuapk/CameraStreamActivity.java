package com.example.icuapk;

import android.app.Activity;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CameraStreamActivity extends Activity {
    public static final String EXTRA_STREAM_URL = "stream_url";
    public static final String EXTRA_STREAM_URLS = "stream_urls";

    private SurfaceView surfaceView;
    private TextView statusText;
    private String streamUrl;
    private String[] streamUrls;
    private int streamIndex;
    private boolean surfaceReady;
    private RtspH264Client rtspClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        streamUrl = getIntent().getStringExtra(EXTRA_STREAM_URL);
        streamUrls = sanitizeUrls(getIntent().getStringArrayExtra(EXTRA_STREAM_URLS), streamUrl);
        setContentView(createContentView());
        if (streamUrls.length == 0) {
            statusText.setText("没有配置摄像头视频地址");
            return;
        }
        statusText.setText("正在准备摄像头画面...");
    }

    private View createContentView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        surfaceView = new SurfaceView(this);
        surfaceView.setKeepScreenOn(true);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surfaceReady = true;
                if (streamUrls != null && streamUrls.length > 0) {
                    startPlayback();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                surfaceReady = false;
                stopRtspClient();
            }
        });
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(14), dp(8), dp(14), dp(8));
        topBar.setBackgroundColor(Color.argb(165, 0, 0, 0));

        Button close = new Button(this);
        close.setText("返回");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        topBar.addView(close, new LinearLayout.LayoutParams(dp(82), dp(42)));

        Button retry = new Button(this);
        retry.setText("重连");
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                streamIndex = 0;
                startPlayback();
            }
        });
        topBar.addView(retry, new LinearLayout.LayoutParams(dp(82), dp(42)));

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(15);
        statusText.setSingleLine(false);
        topBar.addView(statusText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        root.addView(topBar, barParams);
        return root;
    }

    private void startPlayback() {
        if (!surfaceReady) {
            statusText.setText("正在等待视频画布...");
            return;
        }
        if (streamIndex >= streamUrls.length) {
            statusText.setText("全部候选地址都无法播放");
            return;
        }
        streamUrl = streamUrls[streamIndex];
        statusText.setText("正在连接 TP-LINK 摄像头 (" + (streamIndex + 1) + "/" + streamUrls.length + "): "
                + maskPassword(streamUrl));
        stopRtspClient();
        rtspClient = new RtspH264Client(streamUrl, surfaceView.getHolder().getSurface(), new RtspH264Client.Listener() {
            @Override
            public void onStatus(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText(message);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!tryNextUrl(message)) {
                            statusText.setText("播放失败: " + message + "\n当前地址: " + maskPassword(streamUrl));
                            Toast.makeText(CameraStreamActivity.this, "摄像头视频播放失败", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
        new Thread(rtspClient, "tp-link-rtsp-h264").start();
    }

    private boolean tryNextUrl(String reason) {
        if (streamUrls == null || streamIndex + 1 >= streamUrls.length) {
            return false;
        }
        streamIndex++;
        statusText.setText("当前地址失败，正在尝试备用地址 (" + (streamIndex + 1) + "/" + streamUrls.length + ")\n"
                + reason);
        surfaceView.postDelayed(new Runnable() {
            @Override
            public void run() {
                startPlayback();
            }
        }, 700);
        return true;
    }

    private void stopRtspClient() {
        if (rtspClient != null) {
            rtspClient.stop();
            rtspClient = null;
        }
    }

    private String[] sanitizeUrls(String[] urls, String fallback) {
        List<String> cleaned = new ArrayList<>();
        if (urls != null) {
            for (String url : urls) {
                addUrl(cleaned, url);
            }
        }
        addUrl(cleaned, fallback);
        return cleaned.toArray(new String[0]);
    }

    private void addUrl(List<String> urls, String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        String trimmed = url.trim();
        if (trimmed.length() == 0 || urls.contains(trimmed)) {
            return;
        }
        urls.add(trimmed);
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

    @Override
    protected void onPause() {
        super.onPause();
        stopRtspClient();
    }

    @Override
    protected void onDestroy() {
        stopRtspClient();
        super.onDestroy();
    }

    static final class RtspH264Client implements Runnable {
        interface Listener {
            void onStatus(String message);

            void onError(String message);
        }

        private static final byte[] START_CODE = new byte[]{0, 0, 0, 1};

        private final String sourceUrl;
        private final Surface surface;
        private final Listener listener;
        private volatile boolean stopped;
        private Socket socket;
        private InputStream input;
        private OutputStream output;
        private int cSeq = 1;
        private String session = "";
        private String requestUrl;
        private String authorization = "";
        private byte[] sps;
        private byte[] pps;
        private MediaCodec decoder;
        private boolean decoderStarted;
        private long presentationTimeUs;
        private final ByteArrayOutputStream fuBuffer = new ByteArrayOutputStream(64 * 1024);
        private final ByteArrayOutputStream accessUnitBuffer = new ByteArrayOutputStream(256 * 1024);
        private boolean accessUnitKeyFrame;

        RtspH264Client(String sourceUrl, Surface surface, Listener listener) {
            this.sourceUrl = sourceUrl;
            this.surface = surface;
            this.listener = listener;
        }

        @Override
        public void run() {
            try {
                URI uri = URI.create(sourceUrl);
                String host = uri.getHost();
                if (TextUtils.isEmpty(host)) {
                    throw new IOException("RTSP地址没有主机IP");
                }
                int port = uri.getPort() > 0 ? uri.getPort() : 554;
                requestUrl = buildRequestUrl(uri, host, port);
                authorization = buildAuthorization(uri);
                notifyStatus("正在建立RTSP连接: " + host + ":" + port);
                socket = new Socket(host, port);
                socket.setSoTimeout(12000);
                input = socket.getInputStream();
                output = socket.getOutputStream();

                RtspResponse describe = sendRequest("DESCRIBE", requestUrl, "Accept: application/sdp\r\n");
                if (describe.code == 401) {
                    throw new IOException("账号或密码认证失败");
                }
                if (describe.code != 200) {
                    throw new IOException("DESCRIBE失败: " + describe.statusLine);
                }
                String sdp = new String(describe.body, "UTF-8");
                String codec = parseCodec(sdp);
                if (codec != null && codec.toUpperCase(Locale.US).contains("H265")) {
                    throw new IOException("当前码流是H265，请使用子码流stream2的H264");
                }
                parseSpropParameterSets(sdp);
                String controlUrl = resolveControlUrl(sdp, describe.headers.get("content-base"), requestUrl);
                if (TextUtils.isEmpty(controlUrl)) {
                    throw new IOException("没有找到视频轨道control地址");
                }

                RtspResponse setup = sendRequest("SETUP", controlUrl,
                        "Transport: RTP/AVP/TCP;unicast;interleaved=0-1\r\n");
                if (setup.code != 200) {
                    throw new IOException("SETUP失败: " + setup.statusLine);
                }
                session = parseSession(setup.headers.get("session"));
                if (TextUtils.isEmpty(session)) {
                    throw new IOException("SETUP没有返回Session");
                }

                RtspResponse play = sendRequest("PLAY", requestUrl, "Range: npt=0.000-\r\n");
                if (play.code != 200) {
                    throw new IOException("PLAY失败: " + play.statusLine);
                }
                notifyStatus("RTSP已连接，正在接收H264视频...");
                readInterleavedFrames();
            } catch (Exception exception) {
                if (!stopped) {
                    listener.onError(exception.getMessage() == null ? exception.toString() : exception.getMessage());
                }
            } finally {
                closeQuietly();
            }
        }

        void stop() {
            stopped = true;
            closeQuietly();
        }

        private RtspResponse sendRequest(String method, String url, String extraHeaders) throws IOException {
            StringBuilder request = new StringBuilder();
            request.append(method).append(' ').append(url).append(" RTSP/1.0\r\n");
            request.append("CSeq: ").append(cSeq++).append("\r\n");
            request.append("User-Agent: ICU-TPLink-RTSP\r\n");
            if (!TextUtils.isEmpty(authorization)) {
                request.append("Authorization: ").append(authorization).append("\r\n");
            }
            if (!TextUtils.isEmpty(session)) {
                request.append("Session: ").append(session).append("\r\n");
            }
            if (!TextUtils.isEmpty(extraHeaders)) {
                request.append(extraHeaders);
            }
            request.append("\r\n");
            output.write(request.toString().getBytes("UTF-8"));
            output.flush();
            return readRtspResponse();
        }

        private RtspResponse readRtspResponse() throws IOException {
            String statusLine = readLine();
            while (statusLine != null && statusLine.length() == 0) {
                statusLine = readLine();
            }
            if (statusLine == null) {
                throw new IOException("RTSP连接被关闭");
            }
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine()) != null && line.length() > 0) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US), line.substring(colon + 1).trim());
                }
            }
            int contentLength = parseInt(headers.get("content-length"), 0);
            byte[] body = new byte[contentLength];
            readFully(body, 0, contentLength);
            return new RtspResponse(statusLine, headers, body);
        }

        private String readLine() throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream(128);
            int previous = -1;
            while (true) {
                int value = input.read();
                if (value == -1) {
                    if (line.size() == 0) {
                        return null;
                    }
                    break;
                }
                if (previous == '\r' && value == '\n') {
                    break;
                }
                if (previous != -1) {
                    line.write(previous);
                }
                previous = value;
            }
            return line.toString("UTF-8");
        }

        private void readInterleavedFrames() throws IOException {
            byte[] header = new byte[4];
            while (!stopped) {
                int marker = input.read();
                if (marker == -1) {
                    throw new IOException("RTSP视频流断开");
                }
                if (marker != '$') {
                    continue;
                }
                header[0] = (byte) marker;
                readFully(header, 1, 3);
                int channel = header[1] & 0xFF;
                int length = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
                if (length <= 0 || length > 262144) {
                    throw new IOException("RTP包长度异常: " + length);
                }
                byte[] packet = new byte[length];
                readFully(packet, 0, length);
                if (channel == 0) {
                    handleRtpPacket(packet);
                }
            }
        }

        private void handleRtpPacket(byte[] packet) throws IOException {
            if (packet.length < 13) {
                return;
            }
            boolean marker = (packet[1] & 0x80) != 0;
            int offset = 12 + ((packet[0] & 0x0F) * 4);
            if ((packet[0] & 0x10) != 0) {
                if (packet.length < offset + 4) {
                    return;
                }
                int extLength = ((packet[offset + 2] & 0xFF) << 8) | (packet[offset + 3] & 0xFF);
                offset += 4 + extLength * 4;
            }
            if (offset >= packet.length) {
                return;
            }
            int nalType = packet[offset] & 0x1F;
            if (nalType >= 1 && nalType <= 23) {
                feedNal(packet, offset, packet.length - offset);
            } else if (nalType == 24) {
                handleStapA(packet, offset + 1, packet.length);
            } else if (nalType == 28) {
                handleFuA(packet, offset, packet.length);
            }
            if (marker) {
                flushAccessUnit();
            }
        }

        private void handleStapA(byte[] packet, int offset, int end) throws IOException {
            int cursor = offset;
            while (cursor + 2 <= end) {
                int size = ((packet[cursor] & 0xFF) << 8) | (packet[cursor + 1] & 0xFF);
                cursor += 2;
                if (size <= 0 || cursor + size > end) {
                    return;
                }
                feedNal(packet, cursor, size);
                cursor += size;
            }
        }

        private void handleFuA(byte[] packet, int offset, int end) throws IOException {
            if (offset + 2 > end) {
                return;
            }
            int fuIndicator = packet[offset] & 0xFF;
            int fuHeader = packet[offset + 1] & 0xFF;
            boolean start = (fuHeader & 0x80) != 0;
            boolean finish = (fuHeader & 0x40) != 0;
            int originalNalHeader = (fuIndicator & 0xE0) | (fuHeader & 0x1F);
            if (start) {
                fuBuffer.reset();
                fuBuffer.write(originalNalHeader);
            }
            if (fuBuffer.size() == 0) {
                return;
            }
            fuBuffer.write(packet, offset + 2, end - offset - 2);
            if (finish) {
                byte[] nal = fuBuffer.toByteArray();
                fuBuffer.reset();
                feedNal(nal, 0, nal.length);
            }
        }

        private void feedNal(byte[] data, int offset, int length) throws IOException {
            if (length <= 0) {
                return;
            }
            int nalOffset = skipStartCode(data, offset, length);
            int nalLength = length - (nalOffset - offset);
            if (nalLength <= 0) {
                return;
            }
            int nalType = data[nalOffset] & 0x1F;
            if (nalType == 7) {
                sps = copyBytes(data, nalOffset, nalLength);
                startDecoderIfReady();
                return;
            }
            if (nalType == 8) {
                pps = copyBytes(data, nalOffset, nalLength);
                startDecoderIfReady();
                return;
            }
            startDecoderIfReady();
            if (!decoderStarted) {
                return;
            }
            appendToAccessUnit(data, nalOffset, nalLength, nalType == 5);
        }

        private void appendToAccessUnit(byte[] data, int offset, int length, boolean keyFrame) throws IOException {
            if (keyFrame && accessUnitBuffer.size() == 0 && sps != null && pps != null) {
                accessUnitBuffer.write(annexB(sps, 0, sps.length));
                accessUnitBuffer.write(annexB(pps, 0, pps.length));
            }
            accessUnitBuffer.write(START_CODE);
            accessUnitBuffer.write(data, offset, length);
            accessUnitKeyFrame = accessUnitKeyFrame || keyFrame;
            if (accessUnitBuffer.size() > 512 * 1024) {
                flushAccessUnit();
            }
        }

        private void flushAccessUnit() throws IOException {
            if (!decoderStarted || accessUnitBuffer.size() == 0) {
                accessUnitBuffer.reset();
                accessUnitKeyFrame = false;
                return;
            }
            byte[] sample = accessUnitBuffer.toByteArray();
            boolean keyFrame = accessUnitKeyFrame;
            accessUnitBuffer.reset();
            accessUnitKeyFrame = false;
            queueSample(sample, keyFrame);
        }

        private void startDecoderIfReady() throws IOException {
            if (decoderStarted || sps == null || pps == null) {
                return;
            }
            int[] size = parseSpsSize(sps);
            MediaFormat format = MediaFormat.createVideoFormat("video/avc", size[0], size[1]);
            format.setByteBuffer("csd-0", ByteBuffer.wrap(annexB(sps, 0, sps.length)));
            format.setByteBuffer("csd-1", ByteBuffer.wrap(annexB(pps, 0, pps.length)));
            decoder = MediaCodec.createDecoderByType("video/avc");
            decoder.configure(format, surface, null, 0);
            decoder.start();
            decoderStarted = true;
            notifyStatus("H264解码器已启动: " + size[0] + "x" + size[1]);
        }

        private void queueSample(byte[] sample, boolean keyFrame) throws IOException {
            try {
                int inputIndex = decoder.dequeueInputBuffer(10000);
                if (inputIndex >= 0) {
                    ByteBuffer buffer = decoder.getInputBuffer(inputIndex);
                    if (buffer != null) {
                        buffer.clear();
                        buffer.put(sample);
                        int flags = keyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                        decoder.queueInputBuffer(inputIndex, 0, sample.length, presentationTimeUs, flags);
                        presentationTimeUs += 33333;
                    }
                }
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                int outputIndex;
                while ((outputIndex = decoder.dequeueOutputBuffer(info, 0)) >= 0) {
                    decoder.releaseOutputBuffer(outputIndex, true);
                }
            } catch (IllegalStateException exception) {
                throw new IOException("H264硬解码失败: " + exception.getMessage());
            }
        }

        private String buildRequestUrl(URI uri, String host, int port) {
            StringBuilder builder = new StringBuilder("rtsp://");
            builder.append(host);
            if (port != 554) {
                builder.append(':').append(port);
            }
            String rawPath = uri.getRawPath();
            builder.append(TextUtils.isEmpty(rawPath) ? "/" : rawPath);
            if (!TextUtils.isEmpty(uri.getRawQuery())) {
                builder.append('?').append(uri.getRawQuery());
            }
            return builder.toString();
        }

        private String buildAuthorization(URI uri) {
            String userInfo = uri.getRawUserInfo();
            if (TextUtils.isEmpty(userInfo)) {
                return "";
            }
            return "Basic " + Base64.encodeToString(userInfo.getBytes(), Base64.NO_WRAP);
        }

        private void parseSpropParameterSets(String sdp) {
            String key = "sprop-parameter-sets=";
            int start = sdp.indexOf(key);
            if (start < 0) {
                return;
            }
            start += key.length();
            int end = start;
            while (end < sdp.length()) {
                char c = sdp.charAt(end);
                if (c == ';' || c == '\r' || c == '\n') {
                    break;
                }
                end++;
            }
            String[] parts = sdp.substring(start, end).split(",");
            if (parts.length >= 2) {
                sps = stripStartCode(Base64.decode(parts[0].trim(), Base64.DEFAULT));
                pps = stripStartCode(Base64.decode(parts[1].trim(), Base64.DEFAULT));
            }
        }

        private String parseCodec(String sdp) {
            for (String line : sdp.split("\\r?\\n")) {
                if (line.startsWith("a=rtpmap:") && (line.contains("H264") || line.contains("H265"))) {
                    return line;
                }
            }
            return "";
        }

        private String resolveControlUrl(String sdp, String contentBase, String fallbackBase) {
            String control = "";
            boolean inVideo = false;
            for (String line : sdp.split("\\r?\\n")) {
                if (line.startsWith("m=")) {
                    inVideo = line.startsWith("m=video");
                } else if (inVideo && line.startsWith("a=control:")) {
                    control = line.substring("a=control:".length()).trim();
                    break;
                }
            }
            if (TextUtils.isEmpty(control)) {
                return "";
            }
            if (control.startsWith("rtsp://")) {
                return control;
            }
            String base = TextUtils.isEmpty(contentBase) ? fallbackBase : contentBase;
            if (control.startsWith("/")) {
                URI uri = URI.create(fallbackBase);
                int port = uri.getPort() > 0 ? uri.getPort() : 554;
                return "rtsp://" + uri.getHost() + (port == 554 ? "" : ":" + port) + control;
            }
            return base.endsWith("/") ? base + control : base + "/" + control;
        }

        private String parseSession(String raw) {
            if (TextUtils.isEmpty(raw)) {
                return "";
            }
            int semicolon = raw.indexOf(';');
            return semicolon >= 0 ? raw.substring(0, semicolon).trim() : raw.trim();
        }

        private void readFully(byte[] buffer, int offset, int length) throws IOException {
            int read = 0;
            while (read < length) {
                int count = input.read(buffer, offset + read, length - read);
                if (count < 0) {
                    throw new IOException("网络读取失败");
                }
                read += count;
            }
        }

        private int parseInt(String value, int fallback) {
            if (TextUtils.isEmpty(value)) {
                return fallback;
            }
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private void notifyStatus(String message) {
            if (!stopped) {
                listener.onStatus(message);
            }
        }

        private void closeQuietly() {
            try {
                if (decoder != null) {
                    decoder.stop();
                    decoder.release();
                }
            } catch (Exception ignored) {
            }
            decoder = null;
            decoderStarted = false;
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (Exception ignored) {
            }
            socket = null;
        }

        private byte[] annexB(byte[] data, int offset, int length) {
            byte[] sample = new byte[START_CODE.length + length];
            System.arraycopy(START_CODE, 0, sample, 0, START_CODE.length);
            System.arraycopy(data, offset, sample, START_CODE.length, length);
            return sample;
        }

        private byte[] copyBytes(byte[] data, int offset, int length) {
            byte[] copy = new byte[length];
            System.arraycopy(data, offset, copy, 0, length);
            return copy;
        }

        private byte[] stripStartCode(byte[] data) {
            int offset = skipStartCode(data, 0, data.length);
            return offset == 0 ? data : copyBytes(data, offset, data.length - offset);
        }

        private int skipStartCode(byte[] data, int offset, int length) {
            if (length >= 4 && data[offset] == 0 && data[offset + 1] == 0
                    && data[offset + 2] == 0 && data[offset + 3] == 1) {
                return offset + 4;
            }
            if (length >= 3 && data[offset] == 0 && data[offset + 1] == 0 && data[offset + 2] == 1) {
                return offset + 3;
            }
            return offset;
        }

        private int[] parseSpsSize(byte[] sps) {
            try {
                byte[] rbsp = removeEmulationBytes(sps);
                BitReader bits = new BitReader(rbsp);
                bits.readBits(8);
                int profileIdc = bits.readBits(8);
                bits.readBits(8);
                bits.readBits(8);
                bits.readUE();
                if (profileIdc == 100 || profileIdc == 110 || profileIdc == 122 || profileIdc == 244
                        || profileIdc == 44 || profileIdc == 83 || profileIdc == 86 || profileIdc == 118
                        || profileIdc == 128 || profileIdc == 138 || profileIdc == 144) {
                    int chromaFormatIdc = bits.readUE();
                    if (chromaFormatIdc == 3) {
                        bits.readBits(1);
                    }
                    bits.readUE();
                    bits.readUE();
                    bits.readBits(1);
                    boolean scalingMatrix = bits.readBits(1) == 1;
                    if (scalingMatrix) {
                        int count = chromaFormatIdc == 3 ? 12 : 8;
                        for (int i = 0; i < count; i++) {
                            if (bits.readBits(1) == 1) {
                                skipScalingList(bits, i < 6 ? 16 : 64);
                            }
                        }
                    }
                }
                bits.readUE();
                int picOrderCntType = bits.readUE();
                if (picOrderCntType == 0) {
                    bits.readUE();
                } else if (picOrderCntType == 1) {
                    bits.readBits(1);
                    bits.readSE();
                    bits.readSE();
                    int cycle = bits.readUE();
                    for (int i = 0; i < cycle; i++) {
                        bits.readSE();
                    }
                }
                bits.readUE();
                bits.readBits(1);
                int widthMbs = bits.readUE() + 1;
                int heightMapUnits = bits.readUE() + 1;
                int frameMbsOnly = bits.readBits(1);
                if (frameMbsOnly == 0) {
                    bits.readBits(1);
                }
                bits.readBits(1);
                int cropLeft = 0;
                int cropRight = 0;
                int cropTop = 0;
                int cropBottom = 0;
                if (bits.readBits(1) == 1) {
                    cropLeft = bits.readUE();
                    cropRight = bits.readUE();
                    cropTop = bits.readUE();
                    cropBottom = bits.readUE();
                }
                int width = widthMbs * 16 - (cropLeft + cropRight) * 2;
                int height = (2 - frameMbsOnly) * heightMapUnits * 16 - (cropTop + cropBottom) * 2;
                if (width > 0 && height > 0) {
                    return new int[]{width, height};
                }
            } catch (Exception ignored) {
            }
            return new int[]{1280, 720};
        }

        private void skipScalingList(BitReader bits, int size) {
            int lastScale = 8;
            int nextScale = 8;
            for (int i = 0; i < size; i++) {
                if (nextScale != 0) {
                    int deltaScale = bits.readSE();
                    nextScale = (lastScale + deltaScale + 256) % 256;
                }
                lastScale = nextScale == 0 ? lastScale : nextScale;
            }
        }

        private byte[] removeEmulationBytes(byte[] data) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
            for (int i = 0; i < data.length; i++) {
                if (i > 1 && data[i] == 0x03 && data[i - 1] == 0 && data[i - 2] == 0) {
                    continue;
                }
                out.write(data[i]);
            }
            return out.toByteArray();
        }

        private static final class RtspResponse {
            final String statusLine;
            final int code;
            final Map<String, String> headers;
            final byte[] body;

            RtspResponse(String statusLine, Map<String, String> headers, byte[] body) {
                this.statusLine = statusLine;
                this.headers = headers;
                this.body = body;
                int parsed = 0;
                String[] parts = statusLine.split(" ");
                if (parts.length > 1) {
                    try {
                        parsed = Integer.parseInt(parts[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                this.code = parsed;
            }
        }

        private static final class BitReader {
            private final byte[] data;
            private int bitOffset;

            BitReader(byte[] data) {
                this.data = data;
            }

            int readBits(int count) {
                int value = 0;
                for (int i = 0; i < count; i++) {
                    value <<= 1;
                    if (bitOffset / 8 < data.length) {
                        value |= (data[bitOffset / 8] >> (7 - (bitOffset % 8))) & 1;
                    }
                    bitOffset++;
                }
                return value;
            }

            int readUE() {
                int zeros = 0;
                while (readBits(1) == 0 && zeros < 32) {
                    zeros++;
                }
                int suffix = zeros == 0 ? 0 : readBits(zeros);
                return (1 << zeros) - 1 + suffix;
            }

            int readSE() {
                int value = readUE();
                return ((value & 1) == 1) ? (value + 1) / 2 : -(value / 2);
            }
        }
    }
}
