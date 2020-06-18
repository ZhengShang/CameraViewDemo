package cn.zhengshang.base;

import android.media.MediaRecorder;
import android.util.Range;
import android.view.MotionEvent;
import android.view.View;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import cn.zhengshang.config.CameraConfig;
import cn.zhengshang.listener.OnAeChangeListener;
import cn.zhengshang.listener.OnCaptureImageCallback;
import cn.zhengshang.listener.OnFaceDetectListener;
import cn.zhengshang.listener.OnManualValueListener;
import cn.zhengshang.listener.OnVideoOutputFileListener;

/**
 * Created by shangzheng on 2019-05-09.
 *            🐳🐳🐳🍒           16:42 🥥
 */
public interface ICamera {

    void start();

    void stop();

    boolean isCameraOpened();

    View getView();

    void setFacing(int facing);

    int getFacing();

    String getCameraId();

    Set<AspectRatio> getSupportedAspectRatios();

    SortedSet<Size> getSupportedPicSizes();

    SortedSet<Size> getSupportedVideoSize();

    float getFpsWithSize(android.util.Size size);

    int[] getSupportAWBModes();

    /**
     * @return {@code true} if the aspect ratio was changed.
     */
    boolean setAspectRatio(AspectRatio ratio);

    AspectRatio getAspectRatio();

    void setAutoFocus(boolean autoFocus);

    boolean getAutoFocus();

    boolean isFlashAvailable();

    void setFlash(int flash);

    int getFlash();

    void setTorch(boolean open);

    boolean isTorch();

    void takePicture();

    void takeBurstPictures();

    void stopBurstPicture();

    /**
     * 根据点击区域重新对焦
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

    void setMuteVideo(boolean muteVideo);

    boolean getMuteVideo();

    long getAvailableSpace();

    void setAvailableSpace(long space);

    boolean isRecordingVideo();

    int getBitrate();

    void setCaptureRate(double rate);

    double getCaptureRate();

    int getCaptureOrientation();

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

    void setBitrate(int bitrate);

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

    void setVideoSize(Size size, boolean save);

    int getFps();

    Size getPreViewSize();

    void setFps(int fps);

    int getPicFormat();

    void setPicFormat(int format);

    boolean isSupportedHdr();

    void setHdrMode(boolean hdr);

    void setStabilizeEnable(boolean enable);

    /**
     * 是否支持防抖
     *
     * @return 只要支持 图片防抖 或 视频防抖 之中的一个,就返回true,反之,返回false.
     */
    boolean isSupportedStabilize();

    boolean getStabilizeEnable();

    /**
     * 只是以下6个中的一个就算支持手动模式
     * <p>
     * AE SEC ISO WB AF WT
     */
    boolean isSupportedManualMode();

    boolean isManualControlAF();

    void setManualMode(boolean manual);

    boolean isManualMode();

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

    void setPhoneOrientation(int orientation);

    int getPhoneOrientation();

    MediaRecorder getMediaRecorder();

    String generateVideoFilePath();

    boolean isSupportFaceDetect();

    void setFaceDetect(boolean open);

    void addOnFaceDetectListener(OnFaceDetectListener onFaceDetectListener);

    void addOnManualValueListener(OnManualValueListener onManualValueListener);

    void addOnAeChangedListener(OnAeChangeListener onAeChangeListener);

    void addOnCaptureImageCallback(OnCaptureImageCallback onCaptureImageCallback);

    void addOnVideoOutputFileListener(OnVideoOutputFileListener onVideoOutputFileListener);

    CameraConfig getCameraConfig();

    void setCameraConfig(CameraConfig cameraConfig);
}
