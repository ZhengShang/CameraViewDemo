package cn.zhengshang.util;

import android.media.CamcorderProfile;

import cn.zhengshang.base.Size;

public class CamcorderUtil {

    public static boolean hasHighSpeedCamcorder(Size size, int cameraID) {
        if (size.getWidth() == 720 && size.getHeight() == 480) {
            return CamcorderProfile.hasProfile(cameraID, CamcorderProfile.QUALITY_HIGH_SPEED_480P);
        } else if (size.getWidth() == 1280 && size.getHeight() == 720) {
            return CamcorderProfile.hasProfile(cameraID, CamcorderProfile.QUALITY_HIGH_SPEED_720P);
        } else if (size.getWidth() == 1920 && size.getHeight() == 1080) {
            return CamcorderProfile.hasProfile(cameraID, CamcorderProfile.QUALITY_HIGH_SPEED_1080P);
        } else if (size.getWidth() == 3840 && size.getHeight() == 2160) {
            return CamcorderProfile.hasProfile(cameraID, CamcorderProfile.QUALITY_HIGH_SPEED_2160P);
        } else {
            return false;
        }
    }


}
      
