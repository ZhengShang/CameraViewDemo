package cn.zhengshang.config;


import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public final class SpecialDevicesList {

    /**
     *  这些设备查询到支持高帧率视频, 但是强制不使用
     * @see cn.zhengshang.cameraview.CameraHighSpeedVideo
     */
    private static final List<String> BAN_HSV_DEVICES = new ArrayList<>();

    /**
     * 这些设备强制使用Camera1
     * @see cn.zhengshang.cameraview.Camera1
     */
    private static final List<String> FORCE_USE_CAMERA1 = new ArrayList<>();

    static {
        BAN_HSV_DEVICES.add("OnePlus7Pro");
        BAN_HSV_DEVICES.add("OnePlus6T");
        BAN_HSV_DEVICES.add("judypn");
        BAN_HSV_DEVICES.add("joan");
        BAN_HSV_DEVICES.add("16thPlus");
        BAN_HSV_DEVICES.add("HWMHA");

    }

    static {
        FORCE_USE_CAMERA1.add("SM-G9300");
        FORCE_USE_CAMERA1.add("SM-G9209");
        FORCE_USE_CAMERA1.add("LM-V405");
    }

    public static boolean banHsv() {
        return BAN_HSV_DEVICES.contains(Build.DEVICE);
    }

    public static boolean isForceUseCamera1() {
        if ("xiaomi".equalsIgnoreCase(Build.MANUFACTURER)) {
            return true;
        }
        return FORCE_USE_CAMERA1.contains(Build.MODEL.toUpperCase());
    }

}
