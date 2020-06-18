package cn.zhengshang.config;

import android.media.CamcorderProfile;

import cn.zhengshang.base.ICamera;
import cn.zhengshang.base.Size;

/**
 * Created by shangzheng on 2019-07-25.
 *            🐳🐳🐳🍒           14:36 🥥
 */
public class CaptureConfigHelper {

    /**
     * 判断当前的视频是不是高帧率视频
     * @return true表示是高帧率视频, false是普通视频
     */
    public static boolean isHighSpeedVideo(ICamera iCamera) {
        return iCamera.getVideoSize().getFps() > 30;
    }

    /**
     * 应用适用于慢动作的参数
     */
    public static void applySlowMotion(ICamera iCamera) {
        iCamera.setCaptureRate(120);
        iCamera.setFps(30);
        iCamera.setMuteVideo(true);
        iCamera.setVideoSize(new Size(1280, 720), false);
    }

    public static void applyNormalVideo(ICamera iCamera) {
        Size size = SPConfig.getInstance().loadVideoSize();
        iCamera.setCaptureRate(30);
        iCamera.setFps(size.getFps());
        iCamera.setMuteVideo(false);
        iCamera.setVideoSize(size, false);
    }

    public static void applyTimeLapse(ICamera iCamera) {
        int cameraId = iCamera.getFacing();
        CamcorderProfile profile = iCamera.getVideoSize().getTimeLapseProfile(cameraId);
        if (profile != null) {
            iCamera.setVideoSize(new Size(profile.videoFrameWidth, profile.videoFrameHeight), false);
            iCamera.setBitrate(profile.videoBitRate);
        } else {
            iCamera.setVideoSize(new Size(1280, 720), false);
        }
        iCamera.setFps(30);
        iCamera.setMuteVideo(true);
    }
}
