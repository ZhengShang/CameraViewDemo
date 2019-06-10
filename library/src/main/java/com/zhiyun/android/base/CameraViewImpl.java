package com.zhiyun.android.base;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;

import com.zhiyun.android.config.CameraConfig;
import com.zhiyun.android.controller.BroadcastController;
import com.zhiyun.android.controller.CaptureController;
import com.zhiyun.android.controller.LensController;
import com.zhiyun.android.controller.MediaRecorderController;
import com.zhiyun.android.listener.CameraCallback;
import com.zhiyun.android.listener.OnAeChangeListener;
import com.zhiyun.android.listener.OnCaptureImageCallback;
import com.zhiyun.android.listener.OnManualValueListener;
import com.zhiyun.android.listener.OnVideoOutputFileListener;

import java.util.List;

public abstract class CameraViewImpl implements ZyCamera {

    /**
     * 使用 static 来持久化当前引用 , 避免被重建
     */
    protected static CameraConfig mCameraConfig = new CameraConfig();
    /**
     * 使用 static 来持久化当前引用 , 避免被重建
     */
    private static OnVideoOutputFileListener mOnVideoOutputFileListener;
    protected OnManualValueListener mOnManualValueListener;
    protected OnAeChangeListener mOnAeChangeListener;
    protected OnCaptureImageCallback mOnCaptureImageCallback;
    protected final CameraCallback mCallback;

    protected final PreviewImpl mPreview;
    protected Context mContext;
    protected Handler mBackgroundHandler;
    /**
     * 手机当前的方向.
     */
    protected int mPhoneOrientation;
    protected CaptureController mCaptureController;
    protected long mAvailableSpace;
    protected MediaRecorderController mRecorderController;
    private HandlerThread mBackgroundThread;
    private LensController mLensController;


    public CameraViewImpl(Context context, CameraCallback callback, PreviewImpl preview) {
        mContext = context;
        mCallback = callback;
        mPreview = preview;
        mLensController = new LensController(this);
        mRecorderController = new MediaRecorderController(this, mCameraConfig);
    }

    @Override
    public void start() {
        startBackgroundThread();
    }

    @Override
    public void stop() {
        stopBackgroundThread();
    }

    public View getView() {
        return mPreview.getView();
    }

    public float getFpsWithSize(android.util.Size size) {
        return Constants.DEF_FPS;
    }

    @Override
    public int getFacing() {
        return mCameraConfig.getFacing();
    }

    @Override
    public AspectRatio getAspectRatio() {
        return mCameraConfig.getAspectRatio();
    }

    @Override
    public boolean getAutoFocus() {
        return mCameraConfig.isAutoFocus();
    }

    @Override
    public boolean isSupportedStabilize() {
        return mCameraConfig.getPhotoConfig().isStabilization() ||
                mCameraConfig.getVideoConfig().isStabilization();
    }

    @Override
    public boolean getStabilizeEnable() {
        return mCameraConfig.isStabilization();
    }

    @Override
    public void setStabilizeEnable(boolean enable) {
        mCameraConfig.setStabilization(enable);
    }

    @Override
    public int getFlash() {
        return mCameraConfig.getFlash();
    }

    /**
     * 双指在屏幕上缩放视图大小
     *
     * @param factor 缩放因子,用以判断放大和缩小
     */
    public void gestureScaleZoom(float factor) {
        mLensController.gestureScaleZoom(factor);
    }

    public void setPlaySound(boolean playSound) {
        mCameraConfig.getVideoConfig().setPlaySound(playSound);
    }

    public boolean getMuteVideo() {
        return mCameraConfig.getVideoConfig().isMute();
    }

    public void setMuteVideo(boolean muteVideo) {
        mCameraConfig.getVideoConfig().setMute(muteVideo);
    }

    public long getAvailableSpace() {
        return mAvailableSpace;
    }

    public boolean isRecordingVideo() {
        return mCameraConfig.isRecordingVideo();
    }

    public int getBitrate() {
        return mCameraConfig.getVideoConfig().getBitrate();
    }

    public void setBitrate(int bitrate) {
        //查看推荐分辨率下的当前码率，谁大用哪个。
        int recommendBitRate = mCameraConfig.getVideoConfig().getSize().getRecommendBitRateFromCamcorder(getFacing());
        if (bitrate > recommendBitRate) {
            mCameraConfig.getVideoConfig().setBitrate(bitrate);
        } else {
            mCameraConfig.getVideoConfig().setBitrate(recommendBitRate);
        }
    }

    public double getCaptureRate() {
        return mCameraConfig.getVideoConfig().getCaptureRate();
    }

    public void setCaptureRate(double rate) {
        mCameraConfig.getVideoConfig().setCaptureRate(rate);
    }

    public String getVideoOutputFilePath() {
        return mCameraConfig.getVideoConfig().getVideoAbsolutePath();
    }

    public int getAwbMode() {
        return mCameraConfig.getAwb();
    }

    public int getAe() {
        return mCameraConfig.getManualConfig().getAe();
    }

    public long getSec() {
        return mCameraConfig.getManualConfig().getSec();
    }

    public int getIso() {
        return mCameraConfig.getManualConfig().getIso();
    }

    public int getManualWB() {
        return mCameraConfig.getManualConfig().getWb();
    }

    public float getAf() {
        return mCameraConfig.getManualConfig().getAf();
    }

    public float getWt() {
        return mCameraConfig.getManualConfig().getWt();
    }

    public float getZoomRatio() {
        return mCameraConfig.getZoomRatio();
    }

    public void stopSmoothFocus() {
        mLensController.stopSmoothFocus();
    }

    public List<Integer> getZoomRatios() {
        return null;
    }

    public void zoomIn() {
        gestureScaleZoom(0.989f);
    }

    public void zoomOut() {
        gestureScaleZoom(1.011f);
    }

    public void foucsNear() {
        mLensController.foucsNear();
    }

    public void focusFar() {
        mLensController.focusFar();
    }

    public Size getPicSize() {
        return mCameraConfig.getPhotoConfig().getSize();
    }

    public Size getVideoSize() {
        return mCameraConfig.getVideoConfig().getSize();
    }

    public void setVideoSize(Size size) {
        if (getVideoSize() != size) {
            mCameraConfig.getVideoConfig().setSize(size);

            if (size.getFps() > 30) {
                BroadcastController.sendSwitchToHighSpeedVideoAction(mContext);
            }
        }

    }

    public int getFps() {
        return mCameraConfig.getVideoConfig().getFps();
    }

    public void setFps(int fps) {
        mCameraConfig.getVideoConfig().setFps(fps);
    }

    public int getPicFormat() {
        return mCameraConfig.getPhotoConfig().getFormat();
    }

    public void setPicFormat(int format) {
        mCameraConfig.getPhotoConfig().setFormat(format);
    }

    public void setHdrMode(boolean hdr) {
        mCameraConfig.getPhotoConfig().setHdr(hdr);
    }

    public boolean isManualMode() {
        return mCameraConfig.getManualConfig().isManual();
    }

    public void startSmoothFocus(final float start, final float end, long duration) {
        mLensController.startSmoothFocus(start, end, duration);
    }

    public void setPhoneOrientation(int orientation) {
        mPhoneOrientation = orientation;
    }

    public int getPhoneOrientation() {
        return mPhoneOrientation;
    }

    public MediaRecorder getMediaRecorder() {
        return mRecorderController.getMediaRecorder();
    }

    public String generateVideoFilePath() {
        if (mOnVideoOutputFileListener != null) {
            mCameraConfig.getVideoConfig().setVideoAbsolutePath(mOnVideoOutputFileListener.getVideoOutputFilePath());
        } else {
            throw new RuntimeException("OnVideoOutputFileListener接口返回视频输出路径,必须要被实现");
        }
        return mCameraConfig.getVideoConfig().getVideoAbsolutePath();
    }

    public void addOnManualValueListener(OnManualValueListener onManualValueListener) {
        mOnManualValueListener = onManualValueListener;
    }

    public void addOnAeChangedListener(OnAeChangeListener onAeChangeListener) {
        mOnAeChangeListener = onAeChangeListener;
    }

    public void addOnCaptureImageCallback(OnCaptureImageCallback onCaptureImageCallback) {
        mOnCaptureImageCallback = onCaptureImageCallback;
    }

    public void addOnVideoOutputFileListener(OnVideoOutputFileListener onVideoOutputFileListener) {
        mOnVideoOutputFileListener = onVideoOutputFileListener;
    }

    protected void prepareImageReader() {
        mCaptureController = new CaptureController(mCameraConfig, mBackgroundHandler, mContext, mCallback);
        mCaptureController.prepareImageReader();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        if (mBackgroundThread == null) {
            return;
        }
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
