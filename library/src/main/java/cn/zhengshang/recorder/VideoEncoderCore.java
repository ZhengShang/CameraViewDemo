package cn.zhengshang.recorder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author lize
 * @date 2017/12/28 下午4:12
 * @description 视频录制核心部分
 * version 1.0
 *
 * Copyright (c) 2017 桂林智神信息技术有限公司. All rights reserved.
 */
public class VideoEncoderCore {

    private int mWidth;
    private int mHeight;
    private int mFps;
    private int mBitRate;
    private float mCaptureRate = 30.f;

    private boolean isRecording;
    private MediaCodec mEncoder;
    private Surface mInputSurface;

    private Callback mCallback;

    public VideoEncoderCore() {

    }

    private final Runnable mRecordRunnable = new Runnable() {
        @Override
        public void run() {
            final long TIMEOUT_US = 10000;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            while (true) {

                int outputBufId = mEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
                bufferInfo.presentationTimeUs = System.nanoTime() / 1000;
                if (outputBufId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // 出现超时的处理
                    // 当 isRecording = false 时，就跳出循环
                    if (!isRecording) {
                        break;
                    }
                } else if (outputBufId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 生成了 SPS 、PPS
                    if (mCallback != null) {
                        mCallback.onVideoOutputFormatChanged(mEncoder.getOutputFormat());
                    }
                } else if (outputBufId < 0) {
                    // 其它状态
                } else {
                    // 有效数据的处理
                    ByteBuffer outputBuffer = mEncoder.getOutputBuffer(outputBufId);
                    assert outputBuffer != null;

                    // SPS、PPS, 在 INFO_OUTPUT_FORMAT_CHANGED 时处理过了，这里忽略
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0;
                    }

                    if (bufferInfo.size != 0) {
                        outputBuffer.position(bufferInfo.offset);
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size);

                        if (mCallback != null) {
                            mCallback.onVideoOutputBufferAvailable(outputBuffer, bufferInfo);
                        }
                    }
                    // 释放缓冲器
                    mEncoder.releaseOutputBuffer(outputBufId, false);

                    // 收到结束流的指令
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        if (!isRecording) {
                            break;
                        }
                    }
                }
            }

            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;

            mInputSurface.release();
            mInputSurface = null;

        }
    };

    public void setSize(int width, int height) {
        this.mWidth = width;
        this.mHeight = height;
    }

    public void setBitRate(int bitRate) {
        this.mBitRate = bitRate;
    }

    public void setCaptureRate(float captureRate) {
        this.mCaptureRate = captureRate;
    }

    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    public void setFps(int fps) {
        this.mFps = fps;
    }

    public void prepare() {

        if (mWidth <= 0 || mHeight <= 0 || mFps <= 0 || mBitRate <= 0) {
            throw new IllegalStateException("视频必要参数错误 !!! ");
        }

        String mime = MediaFormat.MIMETYPE_VIDEO_AVC;
        MediaFormat videoFormat = MediaFormat.createVideoFormat(mime, mWidth, mHeight);
        videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFps);
        videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        videoFormat.setFloat(MediaFormat.KEY_CAPTURE_RATE, mCaptureRate);

        try {
            mEncoder = MediaCodec.createEncoderByType(mime);
            mEncoder.configure(videoFormat, null, null,
                    MediaCodec.CONFIGURE_FLAG_ENCODE);
            mInputSurface = mEncoder.createInputSurface();
        } catch (IOException e) {
            e.printStackTrace();
            mEncoder.release();
            mEncoder = null;
        }
    }

    public void start() {
        isRecording = true;
        mEncoder.start();
        new Thread(mRecordRunnable).start();
    }

    public Surface getSurface() {
        if (mInputSurface == null) {
            throw new IllegalStateException("必须在 prepare 方法后调用");
        }
        return mInputSurface;
    }

    public void stop() {
        try {
            isRecording = false;
            // 这里有些机型执行该方法会抛出异常，捕获它确保不会Crash
            mEncoder.signalEndOfInputStream();
        } catch (Exception e) {
        }
    }


    public interface Callback {

        public void onVideoOutputFormatChanged(@NonNull MediaFormat format);

        public void onVideoOutputBufferAvailable(@NonNull ByteBuffer buffer,
                                                 @NonNull MediaCodec.BufferInfo info);
    }
}
