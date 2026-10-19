package com.example.icuapk;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.nio.ByteBuffer;

/* ★ 2026-10-19 V60：摄像头录像 MP4 封装器 —— RTSP 解出的 H264 Annex-B access unit
   （关键帧已前置 SPS/PPS，见 RtspH264Client.appendToAccessUnit）直写 MediaMuxer，
   不解码不转码：画质无损、CPU 开销可忽略；MediaMuxer 内部会把起始码转 AVCC 长度前缀。
   首个关键帧起录（否则开头花屏/无法解码），时间戳自首样本归零且单调。 */
final class CameraRecorder {
    private final File outFile;
    private final MediaFormat format;
    private MediaMuxer muxer;
    private int trackIndex = -1;
    private boolean muxerStarted;
    private boolean failed;
    private long firstPtsUs = -1;
    private long lastPtsUs;   // 已归零偏移后的最后一帧时间戳
    private int frames;

    CameraRecorder(File outFile, int width, int height, byte[] sps, byte[] pps) {
        this.outFile = outFile;
        format = MediaFormat.createVideoFormat("video/avc", Math.max(16, width), Math.max(16, height));
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
        format.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
    }

    /* 在 RTSP 读线程上回调（queueSample 分流）。MediaMuxer 非线程安全，
       写/停都经 synchronized 收口；起录在录制按钮按下时（主线程）只建对象不写文件。 */
    synchronized void onAccessUnit(byte[] sample, boolean keyFrame, long ptsUs) {
        if (failed || sample == null || sample.length <= 0) {
            return;
        }
        try {
            if (!muxerStarted) {
                if (!keyFrame) {
                    return;   // 起录点必须是关键帧
                }
                muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                trackIndex = muxer.addTrack(format);
                muxer.start();
                muxerStarted = true;
                firstPtsUs = ptsUs;
            }
            long pts = Math.max(0, ptsUs - firstPtsUs);
            if (pts < lastPtsUs) {
                pts = lastPtsUs;   // 单调保险（理论上 33333us 步进已单调）
            }
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            info.set(0, sample.length, pts, keyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0);
            muxer.writeSampleData(trackIndex, ByteBuffer.wrap(sample), info);
            lastPtsUs = pts;
            frames++;
        } catch (Exception exception) {
            failed = true;
            closeQuietly();
        }
    }

    /* 停止并封盘。返回 true=文件有效（至少写出一帧）；0 帧或失败时删除空文件返回 false。 */
    synchronized boolean stop() {
        boolean ok = muxerStarted && frames > 0 && !failed;
        closeQuietly();
        if (!ok && outFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            outFile.delete();
        }
        return ok;
    }

    int frames() {
        return frames;
    }

    long durationMs() {
        return lastPtsUs / 1000;
    }

    File outFile() {
        return outFile;
    }

    private void closeQuietly() {
        try {
            if (muxer != null) {
                if (muxerStarted && frames > 0) {
                    muxer.stop();
                }
                muxer.release();
            }
        } catch (Exception ignored) {
        }
        muxer = null;
        muxerStarted = false;
    }
}
