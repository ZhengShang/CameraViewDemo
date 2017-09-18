package com.zhiyun.android.base;

import android.util.Range;
import android.view.View;

import com.zhiyun.android.listener.OnCaptureImageCallback;
import com.zhiyun.android.listener.OnManualValueListener;

import java.util.Set;

public abstract class CameraViewImpl {

    private static final int FOCUS_AREA_SIZE_DEFAULT = 200;
    private static final int FOCUS_METERING_AREA_WEIGHT_DEFAULT = 200;

    protected final Callback mCallback;
    protected OnManualValueListener mOnManualValueListener;
    protected OnCaptureImageCallback mOnCaptureImageCallback;

    protected final PreviewImpl mPreview;

    public CameraViewImpl(Callback callback, PreviewImpl preview) {
        mCallback = callback;
        mPreview = preview;
    }

    public void addOnManualValueListener(OnManualValueListener onManualValueListener) {
        mOnManualValueListener = onManualValueListener;
    }

    public void addOnCaptureImageCallback(OnCaptureImageCallback onCaptureImageCallback) {
        mOnCaptureImageCallback = onCaptureImageCallback;
    }

    public View getView() {
        return mPreview.getView();
    }

    /**
     * @return {@code true} if the implementation was able to start the camera session.
     */
    public abstract boolean start();

    public abstract void stop();

    public abstract boolean isCameraOpened();

    public abstract void setFacing(int facing);

    public abstract int getFacing();

    public abstract String getCameraId();

    public abstract Set<AspectRatio> getSupportedAspectRatios();

    public abstract android.util.Size[] getSupportedPicSizes();

    public abstract boolean isSupported60Fps();

    public abstract android.util.Size[] getSupportedVideoSize();

    public abstract int[] getSupportAWBModes();

    /**
     * @return {@code true} if the aspect ratio was changed.
     */
    public abstract boolean setAspectRatio(AspectRatio ratio);

    public abstract AspectRatio getAspectRatio();

    public abstract void setAutoFocus(boolean autoFocus);

    public abstract boolean getAutoFocus();

    public abstract boolean isFlashAvailable();

    public abstract void setFlash(int flash);

    public abstract int getFlash();

    public abstract void takePicture();

    public abstract void setPlaySound(boolean playSound);

    public abstract void startRecordingVideo();

    public abstract void stopRecordingVideo();

    public abstract boolean isRecordingVideo();

    public abstract void setBitrate(int bitrate);

    public abstract int getBitrate();

    public abstract void setCaptureRate(double rate);

    public abstract double getCaptureRate();

    public abstract void setVideoOutputFilePath(String path);

    public abstract String getVideoOutputFilePath();

    public abstract int getAwbMode();

    public abstract void setAWBMode(int mode);

    public abstract int getAe();

    public abstract long getSec();

    public abstract int getIso();

    public abstract int getManualWB();

    public abstract float getAf();

    public abstract float getWt();

    public abstract void setPhoneOrientation(int orientation);

    public abstract void setDisplayOrientation(int displayOrientation);

    public interface Callback {

        void onCameraOpened();

        void onCameraClosed();

        void onRequestBuilderCreate();

        void onPictureTaken(byte[] data);

        void onVideoRecordingStarted();

        void onVideoRecordStoped();

        void onVideoRecordingFailed();
    }

    public int getFocusAreaSize() {
        return FOCUS_AREA_SIZE_DEFAULT;
    }

    public int getFocusMeteringAreaWeight() {
        return FOCUS_METERING_AREA_WEIGHT_DEFAULT;
    }

    protected void detachFocusTapListener() {
        mPreview.getView().setOnTouchListener(null);
    }

    public abstract float getMaxZoom();

    public abstract void scaleZoom(float scale);

    public abstract Size getPicSize();

    public abstract void setPicSize(Size size);

    public abstract Size getVideoSize();

    public abstract void setVideoSize(Size size);

    public abstract int getFps();

    public abstract void setFps(int fps);

    public abstract int getPicFormat();

    public abstract void setPicFormat(int format);

    public abstract void setHdrMode(boolean hdr);

    public abstract void setStabilizeEnable(boolean enable);
    /**
     * 是否支持防抖
     *
     * @return 只要支持 图片防抖 或 视频防抖 之中的一个,就返回true,反之,返回false.
     */
    public abstract boolean isSupportedStabilize();

    public abstract boolean getStabilizeEnable();

    public abstract boolean isSupportedManualMode();

    public abstract void setManualMode(boolean manual);

    //ae
    public abstract boolean isManualAESupported();

    public abstract Range<Integer> getAERange();

    public abstract void setAEValue(int value);

    //sec
    public abstract boolean isManualSecSupported();

    public abstract Range<Long> getSecRange();

    public abstract void setSecValue(long value);

    //ISO
    public abstract boolean isManualISOSupported();

    public abstract Range<Integer> getISORange();

    public abstract void setISOValue(int value);

    //wb
    public abstract boolean isManualWBSupported();

    public abstract void setManualWBValue(int value);

    //af
    public abstract boolean isManualAFSupported();

    public abstract Float getAFMaxValue();

    public abstract void setAFValue(float value);
}
