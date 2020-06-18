package cn.zhengshang.controller;

import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

import cn.zhengshang.base.ICamera;
import cn.zhengshang.config.CameraConfig;
import cn.zhengshang.config.VideoConfig;

import static cn.zhengshang.controller.SoundController.SOUND_ID_START;

public class MediaRecorderController {

    private ICamera mICamera;
    private MediaRecorder mMediaRecorder;
    private CameraConfig mCameraConfig;
    private State mMrState = State.INIT;

    public MediaRecorderController(ICamera iCamera, CameraConfig config) {
        mICamera = iCamera;
        mCameraConfig = config;
        mMediaRecorder = new MediaRecorder();
    }

    /**
     * 获取适合Camera2视频拍摄的MediaRecorder
     *
     * @return 返回MediaRecorder
     * @throws IOException 配置失败的时候抛出此异常
     */
    public void configForCamera2() throws Exception {
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        if (mMrState != State.INIT && mMrState != State.RESET) {
            return;
        }

        VideoConfig config = mCameraConfig.getVideoConfig();

        if (!config.isMute()) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mICamera.generateVideoFilePath());
        mMediaRecorder.setVideoFrameRate(config.getFps());
        mMediaRecorder.setVideoSize(config.getSize().getWidth(), config.getSize().getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);

        if (!config.isMute()) {
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setAudioEncodingBitRate(96000);
            mMediaRecorder.setAudioSamplingRate(48000);
            mMediaRecorder.setAudioChannels(2);
        }

        mMediaRecorder.setCaptureRate(config.getCaptureRate());

        mMediaRecorder.setVideoEncodingBitRate(config.getBitrate());
        setMaxFileListener();
//        mMediaRecorder.setOrientationHint(90); 竖屏//只能在prepare之前设置一次
        //这里要根据屏幕方向，动态调节 竖屏0度  横屏左下顺时针 180度，
        mMediaRecorder.setOrientationHint(mCameraConfig.getOrientation());

        mMediaRecorder.prepare();
        mMrState = State.PREPARED;
    }

    /**
     * 获取适合Camera1视频拍摄的MediaRecorder
     *
     * @param cameraConfig 相机配置
     * @return 返回MediaRecorder
     * @throws IOException 配置失败的时候抛出此异常
     */
    public MediaRecorder configForCamera1(CameraConfig cameraConfig, Camera camera) throws IOException {
        resetRecorder();
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }

//        camera.unlock();
//        mMediaRecorder.setCamera(camera);

        SoundController.getInstance().playSound(SOUND_ID_START);

        VideoConfig config = cameraConfig.getVideoConfig();
        if (!config.isMute()) {
//            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);

        }
//        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);


        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mICamera.generateVideoFilePath());
        mMediaRecorder.setVideoFrameRate(config.getFps());
        Log.d("MediaRecorderController", "config.getSize().getWidth():" + config.getSize().getWidth());
        Log.d("MediaRecorderController", "config.getSize().getHeight():" + config.getSize().getHeight());
        mMediaRecorder.setVideoSize(config.getSize().getWidth(), config.getSize().getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoEncodingBitRate(config.getBitrate());
        //vivo手机设置30的话,会出现0.5倍的延时效果(生成的时间只有一半,且没有声音)
        if (config.getCaptureRate() != 30) {
            mMediaRecorder.setCaptureRate(config.getCaptureRate());
        }

        if (!config.isMute()) {
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setAudioEncodingBitRate(96000);
            mMediaRecorder.setAudioSamplingRate(48000);
            mMediaRecorder.setAudioChannels(2);
        }

        setMaxFileListener();
        mMediaRecorder.setOrientationHint(mCameraConfig.getOrientation());

        mMediaRecorder.prepare();
        return mMediaRecorder;
    }

    public void configForHighSpeed(CamcorderProfile profile) throws Exception {
        resetRecorder();
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setProfile(profile);
        mMediaRecorder.setOutputFile(mICamera.generateVideoFilePath());
        mMediaRecorder.setOrientationHint(mCameraConfig.getOrientation());
        setMaxFileListener();
        mMediaRecorder.prepare();
    }

    private void setMaxFileListener() {
        if (mMediaRecorder != null) {
            mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                private boolean mLock;

                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    //这个回调会连续出现2次,忽略掉一个
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED && !mLock) {
                        mLock = true;
                        new Handler(Looper.myLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mLock = false;
                            }
                        }, 1000);
                        Log.d("MediaRecorderGenerateor", "MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED");
                        mICamera.stopRecordingVideo(false);
                        mICamera.startRecordingVideo(false);
                    }
                }
            });
        }
    }

    /**
     * 开始录制
     */
    public void startRecord() throws IllegalStateException {
        if (mMrState != State.PREPARED) {
            return;
        }
        mMediaRecorder.start();
        mMrState = State.RECORDING;
    }

    /**
     * @return 返回视频录制的Surface
     */
    public Surface getSurface() {
        if (mMediaRecorder != null) {
            return mMediaRecorder.getSurface();
        }
        return null;
    }

    public MediaRecorder getMediaRecorder() {
        return mMediaRecorder;
    }

    /**
     * 停止录制
     */
    public void stopRecord() {
        resetRecorder();
    }

    private void resetRecorder() {
        if (mMediaRecorder == null) {
            return;
        }
        try {
            if (mMrState == State.RECORDING) {
                mMediaRecorder.stop();
                mMrState = State.STOPPED;
            }
            if (mMrState != State.RESET) {
                mMediaRecorder.reset();
                mMrState = State.RESET;
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("MediaRecorderController", "reset: failed.", e);
            if (mMrState != State.RESET) {
                mMediaRecorder.reset();
                mMrState = State.RESET;
            }
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        if (mMediaRecorder == null) {
            return;
        }
        try {
            if (mMrState == State.RECORDING) {
                mMediaRecorder.stop();
                mMrState = State.STOPPED;
            }
            if (mMrState == State.STOPPED) {
                mMediaRecorder.release();
            }
            mMrState = State.INIT;
            mMediaRecorder = null;
        } catch (Exception e) {
            Log.e("MediaRecorderController", "releaseMediaRecorder failed. ", e);
        }
    }

    enum State {
        INIT, PREPARED, RECORDING, STOPPED, RESET,
    }
}
