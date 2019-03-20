package com.zhiyun.android.recorder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.support.annotation.NonNull;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author lize
 * @date 2017/12/28 下午5:04
 * @description 打包音频、视频，保存为MP4
 * version 1.0
 * Copyright (c) 2017 桂林智神信息技术有限公司. All rights reserved.
 */
public class MediaRecord {

    private int mVideoWidth;
    private int mVideoHeight;
    private int mVideoFps;
    private int mVideoBitRate;
    private int mVideoDegree;
    private float mVideoCaptureRate;

    private int mAudioSampleRate;
    private int mAudioChannelCount;
    private int mAudioBitRate;

    private boolean mContainsAudio = true;
    private boolean mContainsVideo = true;

    private String mOutputFile;


    private AudioEncoderCore mAudioEncoderCore;
    private VideoEncoderCore mVideoEncoderCore;
    private MediaMuxer mMeidaMuxer;

    private int mAudioTrackId = -1;
    private int mVideoTrackId = -1;
    // 记录录制的状态
    private boolean isRecording = false;
    // 记录Muxer工作中的状态
    private boolean isMuxing = false;

    public MediaRecord() {
    }

    public void setVideoSize(int width, int height) {
        this.mVideoWidth  = width;
        this.mVideoHeight = height;
    }

    public void setVideoFrameRate(int fps) {
        this.mVideoFps = fps;
    }

    public void setVideoEncodingBitRate(int bitRate) {
        this.mVideoBitRate = bitRate;
    }

    public void setAudioSamplingRate(int sampleRate) {
        this.mAudioSampleRate = sampleRate;
    }

    public void setAudioChannels(int channels) {
        this.mAudioChannelCount = channels;
    }

    public void setAudioEncodingBitRate(int bitRate) {
        this.mAudioBitRate = bitRate;
    }

    public void setOrientationHint(int degree) {
        this.mVideoDegree = degree;
    }

    public void setCaptureRate(float captureRate) {
        this.mVideoCaptureRate = captureRate;
    }

    public void containsAudio(boolean contains) {
        this.mContainsAudio = contains;
    }

    public void containsVideo(boolean contains) {
        this.mContainsVideo = contains;
    }

    public void setOutputFile(String outputFile) {
        mOutputFile = outputFile;
    }

    private boolean canMuxing() {
        return (mContainsVideo == (mVideoTrackId != -1)) &&
                (mContainsAudio == (mAudioTrackId != -1)) &&
                isRecording;
    }

    private final AudioEncoderCore.Callback mAudioCallback = new AudioEncoderCore.Callback() {

        @Override
        public void onAudioOutputFormatChanged(@NonNull MediaFormat format) {
            mAudioTrackId = addTrack(format);
            if(canMuxing()) {
                mMeidaMuxer.start();
                isMuxing = true;
            }
        }

        @Override
        public void onAudioOutputBufferAvailable(@NonNull ByteBuffer buffer,
                                                 @NonNull MediaCodec.BufferInfo info) {

            if(isMuxing && canMuxing()) {
                mMeidaMuxer.writeSampleData(mAudioTrackId, buffer, info);
            }
        }
    };
    private final VideoEncoderCore.Callback mVideoCallback = new VideoEncoderCore.Callback() {

        @Override
        public void onVideoOutputFormatChanged(@NonNull MediaFormat format) {
            mVideoTrackId = addTrack(format);
            if(canMuxing()) {
                mMeidaMuxer.start();
                isMuxing = true;
            }
        }

        @Override
        public void onVideoOutputBufferAvailable(@NonNull ByteBuffer buffer,
                                                 @NonNull MediaCodec.BufferInfo info) {

            if(isMuxing && canMuxing()) {
                mMeidaMuxer.writeSampleData(mVideoTrackId, buffer, info);
            }
        }
    };

    private int addTrack(MediaFormat format) {
        // format 有无效的情况，但没找到原因
        int trackId;
        try {
            trackId = mMeidaMuxer.addTrack(format);
        } catch (Exception e) {
            trackId = -1;
        }
        return trackId;
    }


    public void prepare() {

        if(mContainsAudio) {
            mAudioEncoderCore = new AudioEncoderCore();
            mAudioEncoderCore.setCallback(mAudioCallback);
            mAudioEncoderCore.setSampleRate(mAudioSampleRate);
            mAudioEncoderCore.setChannelCount(mAudioChannelCount);
            mAudioEncoderCore.setBitRate(mAudioBitRate);
            mAudioEncoderCore.prepare();
        }

        if(mContainsVideo) {
            mVideoEncoderCore = new VideoEncoderCore();
            mVideoEncoderCore.setCallback(mVideoCallback);
            mVideoEncoderCore.setSize(mVideoWidth, mVideoHeight);
            mVideoEncoderCore.setFps(mVideoFps);
            mVideoEncoderCore.setBitRate(mVideoBitRate);
            mVideoEncoderCore.setCaptureRate(mVideoCaptureRate);
            mVideoEncoderCore.prepare();
        }

        try {
            mMeidaMuxer = new MediaMuxer(mOutputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            mMeidaMuxer.setOrientationHint(mVideoDegree);
        } catch (IOException e) {
            mMeidaMuxer = null;
            throw new IllegalStateException("文件数据路径错误 !!! ", e);
        }
    }

    public Surface getSurface() {
        if (mContainsVideo) {
            return mVideoEncoderCore.getSurface();
        }
        else {
            return null;
        }
    }

    public int getVolumeLevel() {
        return mAudioEncoderCore.getVolumeLevel();
    }

    public synchronized void start() {
        if(mMeidaMuxer == null) {
            throw new IllegalStateException("必须在 prepare 之后调用 !!! ");
        }

        if(mContainsAudio) {
            mAudioEncoderCore.start();
        }

        if(mContainsVideo) {
            mVideoEncoderCore.start();
        }
        isRecording = true;
    }

    public synchronized void stop() {
        isRecording = false;
        if(mAudioEncoderCore != null) {
            mAudioEncoderCore.stop();
            mAudioEncoderCore = null;
        }

        if(mVideoEncoderCore != null) {
            mVideoEncoderCore.stop();
            mVideoEncoderCore = null;
        }
        try {
            if(mMeidaMuxer != null) {
                mMeidaMuxer.stop();
                mMeidaMuxer.release();
                mMeidaMuxer = null;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized void reset() {
        stop();
        mAudioTrackId = -1;
        mVideoTrackId = -1;
        mContainsAudio = true;
        mContainsVideo = true;
    }

}
