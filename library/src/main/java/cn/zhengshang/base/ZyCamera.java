package cn.zhengshang.base;

import android.media.MediaRecorder;
import android.util.Range;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import cn.zhengshang.listener.OnAeChangeListener;
import cn.zhengshang.listener.OnCaptureImageCallback;
import cn.zhengshang.listener.OnManualValueListener;
import cn.zhengshang.listener.OnVideoOutputFileListener;

/**
 * Created by shangzheng on 2019-05-09.
 * 🐳🐳🐳🍒           16:42 🥥
 */
public interface ZyCamera {

    void start();

    void stop();

    boolean isCameraOpened();

    View getView();

    int getFacing();

    void setFacing(int facing);

    String getCameraId();

    Set<AspectRatio> getSupportedAspectRatios();

    SortedSet<Size> getSupportedPicSizes();

    boolean isSupported60Fps();

    SortedSet<Size> getSupportedVideoSize();

    float getFpsWithSize(android.util.Size size);

    int[] getSupportAWBModes();

    /**
     * @return {@code true} if the aspect ratio was changed.
     */
    boolean setAspectRatio(AspectRatio ratio);

    AspectRatio getAspectRatio();

    boolean getAutoFocus();

    void setAutoFocus(boolean autoFocus);

    boolean isFlashAvailable();

    int getFlash();

    void setFlash(int flash);

    boolean isTorch();

    void setTorch(boolean open);

    void takePicture();

    /**
     * 根据点击区域重新对焦
     *
     * @param lock 对焦完成后,是否锁定AE/AF
     */
    void resetAF(MotionEvent e, boolean lock);

    /**
     * 锁定AE和AF.
     * 重新点击屏幕后解除锁定
     */
    void lockAEandAF();

    void unLockAEandAF();

    /**
     * 双指在屏幕上缩放视图大小
     *
     * @param factor 缩放因子,用以判断放大和缩小
     */
    void gestureScaleZoom(float factor);

    void setPlaySound(boolean playSound);

    boolean getMuteVideo();

    void setMuteVideo(boolean muteVideo);

    long getAvailableSpace();

    boolean isRecordingVideo();

    int getBitrate();

    void setBitrate(int bitrate);

    double getCaptureRate();

    void setCaptureRate(double rate);

    String getVideoOutputFilePath();

    void startRecordingVideo(boolean triggerCallback);

    void stopRecordingVideo(boolean triggerCallback);

    int getAwbMode();

    void setAWBMode(int mode);

    int getAe();

    long getSec();

    int getIso();

    int getManualWB();

    float getAf();

    float getWt();

    float getZoomRatio();

    void stopSmoothZoom();

    void startSmoothZoom(float start, float end, long duration);

    void stopSmoothFocus();

    void setDisplayOrientation(int displayOrientation);

    float getMaxZoom();

    List<Integer> getZoomRatios();

    void scaleZoom(float scale);

    String getCameraAPI();

    void zoomIn();

    void zoomOut();

    void foucsNear();

    void focusFar();

    Size getPicSize();

    void setPicSize(Size size);

    Size getVideoSize();

    void setVideoSize(Size size);

    int getFps();

    void setFps(int fps);

    int getPicFormat();

    void setPicFormat(int format);

    void setHdrMode(boolean hdr);

    /**
     * 是否支持防抖
     *
     * @return 只要支持 图片防抖 或 视频防抖 之中的一个,就返回true,反之,返回false.
     */
    boolean isSupportedStabilize();

    boolean getStabilizeEnable();

    void setStabilizeEnable(boolean enable);

    /**
     * 只是以下6个中的一个就算支持手动模式
     * <p>
     * AE SEC ISO WB AF WT
     */
    boolean isSupportedManualMode();

    boolean isManualControlAF();

    boolean isManualMode();

    void setManualMode(boolean manual);

    void setIsoAuto();

    //ae
    boolean isManualAESupported();

    Range<Integer> getAERange();

    void setAEValue(int value);

    float getAeStep();

    //sec
    boolean isManualSecSupported();

    Range<Long> getSecRange();

    void setSecValue(long value);

    //ISO
    boolean isManualISOSupported();

    Object getISORange();

    void setISOValue(int value);

    //wb
    boolean isManualWBSupported();

    Range<Integer> getManualWBRange();

    void setManualWBValue(int value);

    //af
    boolean isManualAFSupported();

    Float getAFMaxValue();

    void setAFValue(float value);

    boolean isManualWTSupported();

    void startSmoothFocus(final float start, final float end, long duration);

    int getPhoneOrientation();

    void setPhoneOrientation(int orientation);

    MediaRecorder getMediaRecorder();

    String generateVideoFilePath();

    void addOnManualValueListener(OnManualValueListener onManualValueListener);

    void addOnAeChangedListener(OnAeChangeListener onAeChangeListener);

    void addOnCaptureImageCallback(OnCaptureImageCallback onCaptureImageCallback);

    void addOnVideoOutputFileListener(OnVideoOutputFileListener onVideoOutputFileListener);

}
