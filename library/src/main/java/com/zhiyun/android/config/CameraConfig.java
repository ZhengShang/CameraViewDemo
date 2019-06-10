package com.zhiyun.android.config;

import android.graphics.Rect;

import com.zhiyun.android.base.AspectRatio;
import com.zhiyun.android.base.Constants;

public class CameraConfig {
    /**
     * 方向.
     */
    private int orientation;
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
    private boolean autoFocus = true;
    /**
     * AE锁定
     */
    private boolean aeLock;

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
}
