package cn.zhengshang.config;

import android.media.CamcorderProfile;

import cn.zhengshang.base.Size;
import cn.zhengshang.base.ZyCamera;

/**
 * Created by shangzheng on 2019-07-25.
 *            🐳🐳🐳🍒           14:36 🥥
 */
public class CaptureConfigHelper {

    /**
     * 判断当前的视频是不是高帧率视频
     * @return true表示是高帧率视频, false是普通视频
     */
    public static boolean isHighSpeedVideo(ZyCamera zyCamera) {
        return zyCamera.getVideoSize().getFps() > 30;
    }

    /**
     * 应用适用于慢动作的参数
     */
    public static void applySlowMotion(ZyCamera zyCamera) {
        zyCamera.setCaptureRate(120);
        zyCamera.setFps(30);
        zyCamera.setMuteVideo(true);
        zyCamera.setVideoSize(new Size(1280, 720), false);
    }

    public static void applyNormalVideo(ZyCamera zyCamera) {
        Size size = SPConfig.getInstance().loadVideoSize();
        zyCamera.setCaptureRate(30);
        zyCamera.setFps(size.getFps());
        zyCamera.setMuteVideo(false);
        zyCamera.setVideoSize(size, false);
    }

    public static void applyTimeLapse(ZyCamera zyCamera) {
        int cameraId = zyCamera.getFacing();
        CamcorderProfile profile = zyCamera.getVideoSize().getTimeLapseProfile(cameraId);
        if (profile != null) {
            zyCamera.setVideoSize(new Size(profile.videoFrameWidth, profile.videoFrameHeight), false);
            zyCamera.setBitrate(profile.videoBitRate);
        } else {
            zyCamera.setVideoSize(new Size(1280, 720), false);
        }
        zyCamera.setFps(30);
        zyCamera.setMuteVideo(true);
    }
}
