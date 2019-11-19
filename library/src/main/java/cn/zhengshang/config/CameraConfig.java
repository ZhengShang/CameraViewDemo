package cn.zhengshang.config;

import android.graphics.Rect;

import cn.zhengshang.base.AspectRatio;
import cn.zhengshang.base.Constants;

public class CameraConfig implements Cloneable {
    /**
     * 方向.
     */
    private int orientation = Constants.LANDSCAPE_270;
    /**
     * 镜头.分为前摄和后摄
     */
    private int facing = Constants.FACING_BACK;
    /**
     * 是否防抖
     */
    private boolean stabilization;
    /**
     * 裁剪窗口
     */
    private Rect rect;
    /**
     * flash模式
     */
    private int flash = Constants.FLASH_OFF;
    /**
     * 屏幕比例
     */
    private AspectRatio aspectRatio = Constants.DEFAULT_ASPECT_RATIO;
    /**
     * 是否正在录制视频
     */
    private boolean isRecordingVideo;
    /**
     * 自动白平衡模式
     */
    private int awb = Constants.AWB_MODE_AUTO;
    /**
     * zoom的缩放比例,不是wt的值
     */
    private float zoomRatio = 1;
    /**
     * 是否自动对焦
     */
    private volatile boolean autoFocus = true;
    /**
     * AE锁定
     */
    private boolean aeLock;
    /**
     * 人脸检测
     */
    private volatile boolean faceDetect;

    private ManualConfig mManualConfig = new ManualConfig();
    private PhotoConfig photoConfig = new PhotoConfig();
    private VideoConfig mVideoConfig = new VideoConfig();

    public int getOrientation() {
        return orientation;
    }

    public CameraConfig setOrientation(int orientation) {
        this.orientation = orientation;
        return this;
    }

    public int getFacing() {
        return facing;
    }

    public CameraConfig setFacing(int facing) {
        this.facing = facing;
        return this;
    }

    public boolean isStabilization() {
        return stabilization;
    }

    public CameraConfig setStabilization(boolean stabilization) {
        this.stabilization = stabilization;
        return this;
    }

    public Rect getRect() {
        return rect;
    }

    public CameraConfig setRect(Rect rect) {
        this.rect = rect;
        return this;
    }

    public int getFlash() {
        return flash;
    }

    public CameraConfig setFlash(int flash) {
        this.flash = flash;
        return this;
    }

    public AspectRatio getAspectRatio() {
        return aspectRatio;
    }

    public CameraConfig setAspectRatio(AspectRatio aspectRatio) {
        this.aspectRatio = aspectRatio;
        return this;
    }

    public boolean isRecordingVideo() {
        return isRecordingVideo;
    }

    public CameraConfig setRecordingVideo(boolean recordingVideo) {
        isRecordingVideo = recordingVideo;
        return this;
    }

    public int getAwb() {
        return awb;
    }

    public CameraConfig setAwb(int awb) {
        this.awb = awb;
        return this;
    }

    public float getZoomRatio() {
        return zoomRatio;
    }

    public CameraConfig setZoomRatio(float zoomRatio) {
        this.zoomRatio = zoomRatio;
        return this;
    }

    public boolean isAutoFocus() {
        return autoFocus;
    }

    public CameraConfig setAutoFocus(boolean autoFocus) {
        this.autoFocus = autoFocus;
        return this;
    }

    public ManualConfig getManualConfig() {
        return mManualConfig;
    }

    public PhotoConfig getPhotoConfig() {
        return photoConfig;
    }

    public VideoConfig getVideoConfig() {
        return mVideoConfig;
    }

    public boolean isAeLock() {
        return aeLock;
    }

    public CameraConfig setAeLock(boolean aeLock) {
        this.aeLock = aeLock;
        return this;
    }

    public boolean isFaceDetect() {
        return faceDetect;
    }

    public CameraConfig setFaceDetect(boolean faceDetect) {
        this.faceDetect = faceDetect;
        return this;
    }

    public CameraConfig copy() {
        try {
            CameraConfig cameraConfig = (CameraConfig) this.clone();
            cameraConfig.mVideoConfig = (VideoConfig) cameraConfig.mVideoConfig.clone();
            cameraConfig.photoConfig = (PhotoConfig) cameraConfig.photoConfig.clone();
            cameraConfig.mManualConfig = (ManualConfig) cameraConfig.mManualConfig.clone();
            return cameraConfig;
        } catch (CloneNotSupportedException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public String toString() {
        return "CameraConfig{" +
                "orientation=" + orientation +
                ", facing=" + facing +
                ", stabilization=" + stabilization +
                ", rect=" + rect +
                ", flash=" + flash +
                ", aspectRatio=" + aspectRatio +
                ", isRecordingVideo=" + isRecordingVideo +
                ", awb=" + awb +
                ", zoomRatio=" + zoomRatio +
                ", autoFocus=" + autoFocus +
                ", aeLock=" + aeLock +
                ", faceDetect=" + faceDetect +
                ", mManualConfig=" + mManualConfig +
                ", photoConfig=" + photoConfig +
                ", mVideoConfig=" + mVideoConfig +
                '}';
    }
}
