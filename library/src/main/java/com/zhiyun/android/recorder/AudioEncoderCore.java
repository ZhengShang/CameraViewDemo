package com.zhiyun.android.recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author lize
 * @date 2017/12/28 下午12:03
 * @description 音频录制核心
 * version 1.0
 * Copyright (c) 2017 桂林智神信息技术有限公司. All rights reserved.
 */
public class AudioEncoderCore {

    private static final String TAG = "AudioEncoderCore";

    private int mSampleRate;
    private int mChannelCount;
    private int mBitRate;

    private AudioRecord mRecord;
    private MediaCodec mEncoder;
    private boolean isRecording;
    private int mBufferSize;

    private Callback mCallback;

    public AudioEncoderCore() {

    }

    public void setSampleRate(int sampleRate) {
        this.mSampleRate = sampleRate;
    }

    public void setChannelCount(int channelCount) {
        this.mChannelCount = channelCount;
    }

    public void setBitRate(int bitRate) {
        this.mBitRate = bitRate;
    }

    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    public void prepare() {
        if (mSampleRate <= 0 || mChannelCount <= 0 || mBitRate <= 0) {
            throw new IllegalStateException("请检查音频参数 !!! ");
        }

        final int mic = MediaRecorder.AudioSource.MIC;
        final int format = AudioFormat.ENCODING_PCM_16BIT;
        final int channelConfig = mChannelCount > 1 ?
                AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;

        mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, channelConfig, format);
        mRecord = new AudioRecord(mic, mSampleRate, channelConfig, format, mBufferSize);

        final String mime = MediaFormat.MIMETYPE_AUDIO_AAC;
        MediaFormat audioFormat = MediaFormat.createAudioFormat(mime, mSampleRate, mChannelCount);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, mBufferSize);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectERLC);

        try {
            mEncoder = MediaCodec.createEncoderByType(mime);
            mEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            isRecording = false;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        if (mRecord != null && mEncoder != null) {
            mRecord.startRecording();
            mEncoder.start();
            isRecording = true;
            new Thread(mAudioRunnable).start();
        }
    }

    public void stop() {
        isRecording = false;
    }

    private final Runnable mAudioRunnable = new Runnable() {

        @Override
        public void run() {

            final int TIMEOUT_US = 10000;
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            while (true) {
                // 输入
                int inputBufId = mEncoder.dequeueInputBuffer(TIMEOUT_US);
                if (inputBufId != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!isRecording) {
                        mEncoder.queueInputBuffer(
                                inputBufId,
                                0/*offset*/,
                                0/*size*/,
                                0,/*timeUs*/
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    } else {
                        ByteBuffer inputBuffer = mEncoder.getInputBuffer(inputBufId);
                        int readLen = mRecord.read(inputBuffer, mBufferSize);
                        readLen = readLen < 0 ? 0 : readLen;
                        long timeUs = System.nanoTime() / 1000;
                        mEncoder.queueInputBuffer(inputBufId, 0, readLen, timeUs, 0);
                    }
                }

                // 输出
                int outputBufId = mEncoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
                // 同步时间戳
                bufferInfo.presentationTimeUs = System.nanoTime() / 1000;

                if (outputBufId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                } else if (outputBufId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // 生成了 ADTS
                    MediaFormat outputFormat = mEncoder.getOutputFormat();
                    if(mCallback != null) {
                        mCallback.onAudioOutputFormatChanged(outputFormat);
                    }
                } else {
                    ByteBuffer outputBuffer = mEncoder.getOutputBuffer(outputBufId);
                    // 使用数据
                    if(mCallback != null) {
                        mCallback.onAudioOutputBufferAvailable(outputBuffer, bufferInfo);
                    }
                    mEncoder.releaseOutputBuffer(outputBufId, false);

                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break;
                    }
                }
            }
            mRecord.stop();
            mRecord.release();
            mRecord = null;
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;

        }
    };


    public interface Callback {


        void onAudioOutputFormatChanged(@NonNull MediaFormat format);


        void onAudioOutputBufferAvailable(@NonNull ByteBuffer buffer,
                                          @NonNull MediaCodec.BufferInfo info);
    }


}
