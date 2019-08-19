package cn.zhengshang.controller;

import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;

import cn.zhengshang.base.CameraViewImpl;
import cn.zhengshang.config.CameraConfig;
import cn.zhengshang.config.VideoConfig;

import static cn.zhengshang.controller.SoundController.SOUND_ID_START;

public class MediaRecorderController {

    private CameraViewImpl mCamera;
    private MediaRecorder mMediaRecorder;
    private CameraConfig mCameraConfig;
    private boolean mLock;

    public MediaRecorderController(CameraViewImpl camera, CameraConfig config) {
        mCamera = camera;
        mCameraConfig = config;
        mMediaRecorder = new MediaRecorder();
    }

    /**
     * 获取适合Camera2视频拍摄的MediaRecorder
     *
     * @return 返回MediaRecorder
     * @throws IOException 配置失败的时候抛出此异常
     */
    public void configForCamera2() throws IOException {
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }

        VideoConfig config = mCameraConfig.getVideoConfig();

        if (!config.isMute()) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mCamera.generateVideoFilePath());
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

        mMediaRecorder.setOrientationHint(mCameraConfig.getOrientation());

        mMediaRecorder.prepare();
    }

    /**
     * 获取适合Camera1视频拍摄的MediaRecorder
     *
     * @param cameraConfig 相机配置
     * @return 返回MediaRecorder
     * @throws IOException 配置失败的时候抛出此异常
     */
    public MediaRecorder configForCamera1(CameraConfig cameraConfig, Camera camera) throws IOException {
        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }

        camera.unlock();
        mMediaRecorder.setCamera(camera);

        SoundController.getInstance().playSound(SOUND_ID_START);

        VideoConfig config = cameraConfig.getVideoConfig();
        if (!config.isMute()) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mCamera.generateVideoFilePath());
        mMediaRecorder.setVideoFrameRate(config.getFps());
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
        mMediaRecorder.setOrientationHint(cameraConfig.getOrientation());

        mMediaRecorder.prepare();
        return mMediaRecorder;
    }

    public void configForHighSpeed(int cameraId, CamcorderProfile profile) throws Exception {

        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        }
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setProfile(profile);
        mMediaRecorder.setOutputFile(mCamera.generateVideoFilePath());
        mMediaRecorder.setOrientationHint(mCameraConfig.getOrientation());
        setMaxFileListener();
        mMediaRecorder.prepare();
    }

    private void setMaxFileListener() {
        if (mMediaRecorder != null) {
            mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
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
                        mCamera.stopRecordingVideo(false);
                        mCamera.startRecordingVideo(false);
                    }
                }
            });
        }
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
     * 开始录制
     */
    public void startRecord() throws IllegalStateException {
        mMediaRecorder.start();
    }

    /**
     * 停止录制
     */
    public void stopRecord() {
        if (mMediaRecorder == null) {
            return;
        }
        try {
            if (mCameraConfig.isRecordingVideo()) {
                mMediaRecorder.stop();
            }
            mMediaRecorder.reset();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Log.e("MediaRecorderController", "stopRecord: failed.", e);
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
            if (mCameraConfig.isRecordingVideo()) {
                mMediaRecorder.stop();
            }
            mMediaRecorder.release();
            mMediaRecorder = null;
        } catch (Exception e) {
            Log.e("MediaRecorderController", "releaseMediaRecorder failed. ", e);
        }
    }
}
