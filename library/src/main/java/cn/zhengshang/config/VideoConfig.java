package cn.zhengshang.config;

import cn.zhengshang.base.Constants;
import cn.zhengshang.base.Size;

public class VideoConfig implements Cloneable {
    /**
     * 视频录制开始和结束的声音
     */
    private boolean playSound = Constants.DEFAULT_PLAY_SOUND;
    /**
     * 静音视频.
     * 此开关用于拍摄特殊视频的时候使用,比如延时摄影等.
     * 此种视频下,视频的播放速率为非常规速率,播放的时候声音会不对,所以不需要声音
     */
    private boolean mute = !playSound;
    /**
     * 视频帧率
     */
    private int fps = Constants.DEF_FPS;
    /**
     * 录制的视频的尺寸大小
     */
    private Size size = Constants.DEF_VIDEO_SIZE;
    /**
     * 捕获帧,可实现慢动作,延迟摄影等功能
     */
    private double captureRate = Constants.DEF_CAPTURERATE;
    /**
     * 视频码率
     */
    private int bitrate = Constants.DEF_BITRATE;
    /**
     * 视频的输出保存路径
     */
    private String videoAbsolutePath;

    private boolean stabilization = false;

    public boolean isPlaySound() {
        return playSound;
    }

    public VideoConfig setPlaySound(boolean playSound) {
        this.playSound = playSound;
        return this;
    }

    public boolean isMute() {
        return mute;
    }

    public VideoConfig setMute(boolean mute) {
        this.mute = mute;
        return this;
    }

    public int getFps() {
        return fps;
    }

    public VideoConfig setFps(int fps) {
        this.fps = fps;
        return this;
    }

    public Size getSize() {
        return size;
    }

    public VideoConfig setSize(Size size) {
        this.size = size;
        return this;
    }

    public double getCaptureRate() {
        return captureRate;
    }

    public VideoConfig setCaptureRate(double captureRate) {
        this.captureRate = captureRate;
        return this;
    }

    public int getBitrate() {
        return bitrate;
    }

    public VideoConfig setBitrate(int bitrate) {
        this.bitrate = bitrate;
        return this;
    }

    public String getVideoAbsolutePath() {
        return videoAbsolutePath;
    }

    public VideoConfig setVideoAbsolutePath(String videoAbsolutePath) {
        this.videoAbsolutePath = videoAbsolutePath;
        return this;
    }

    public boolean isStabilization() {
        return stabilization;
    }

    public VideoConfig setStabilization(boolean stabilization) {
        this.stabilization = stabilization;
        return this;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
