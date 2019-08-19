package cn.zhengshang.base;


import android.content.res.Resources;

import cn.zhengshang.config.CaptureMode;

public class Constants {

    public static final CaptureMode DEF_CAPTURE_MODE = CaptureMode.PHOTO;

    public static final String CAMERA_API_CAMERA1 = "camera1";
    public static final String CAMERA_API_CAMERA2 = "camera2";

    public static final String BROADCAST_ACTION_TAKE_PHOTO = "zyplay.action.take.photo";
    public static final String BROADCAST_ACTION_RECORING_STOP = "zyplay.action.recording.stop";
    public static final String BROADCAST_ACTION_SWITCH_TO_HIGH_SPEED_VIDEO = "zyplay.action.switch.to.high.speed.video";

    public static final int FOCUS_AREA_SIZE_DEFAULT = 300;
    public static final int FOCUS_METERING_AREA_WEIGHT_DEFAULT = 500;


    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    public static final int MAX_PREVIEW_WIDTH = 1920;
    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    public static final int MAX_PREVIEW_HEIGHT = 1080;

    public static final AspectRatio DEFAULT_ASPECT_RATIO = AspectRatio.of(16, 9);

    public static final AspectRatio FULL_SCREEN_RATIO = AspectRatio.of(
            Resources.getSystem().getDisplayMetrics().widthPixels,
            Resources.getSystem().getDisplayMetrics().heightPixels
    );

    public static final boolean DEFAULT_PLAY_SOUND = true;

    // Durations in nanoseconds
    public static final long MICRO_SECOND = 1000;
    public static final long MILLI_SECOND = MICRO_SECOND * 1000;
    public static final long ONE_SECOND = MILLI_SECOND * 1000;

    public static final int MANUAL_WB_LOWER = 2000;
    public static final int MANUAL_WB_UPPER = 12000;

    public static final int FACING_BACK = 0;
    public static final int FACING_FRONT = 1;

    public static final int FLASH_OFF = 0;
    public static final int FLASH_ON = 1;
    public static final int FLASH_TORCH = 2;
    public static final int FLASH_AUTO = 3;
    public static final int FLASH_RED_EYE = 4;

    public static final int GRID_NONE = 0;//网格线 无
    public static final int GRID_GRID = 1;//网格线 网格
    public static final int GRID_GRID_AND_DIAGONAL = 2;//网格线 网格+对角线
    public static final int GRID_CENTER_POINT = 3; //网格线 中心点
    public static final int GRID_DIAGONAL = 4;//对角线
    public static final int GRID_GRID_6x4 = 5;//网格线6x4
    public static final int GRID_FIBONACCI = 6;//斐波拉契黄金线

    public static final int LANDSCAPE_90 = 90;
    public static final int LANDSCAPE_270 = 270;

    public static final int AWB_MODE_OFF = 0;
    public static final int AWB_MODE_AUTO = 1;
    public static final int AWB_MODE_INCANDESCENT = 2;
    public static final int AWB_MODE_FLUORESCENT = 3;
    public static final int AWB_MODE_WARM_FLUORESCENT = 4;
    public static final int AWB_MODE_DAYLIGHT = 5;
    public static final int AWB_MODE_CLOUDY_DAYLIGHT = 6;
    public static final int AWB_MODE_TWILIGHT = 7;
    public static final int AWB_MODE_SHADE = 8;

    //手机剩余容量低于200M则不可以录制.
    public static final long LOW_STORAGE_THRESHOLD_BYTES = 200;

    public static final int DEF_BITRATE = 7 * 1000000;
    public static final int DEF_FPS = 30;
    public static final int DEF_CAPTURERATE = 30;
    public static final Size DEF_VIDEO_SIZE = new Size(1280, 720);
    public static final Size DEF_PIC_SIZE = new Size(1280, 720);
    public static final int DEF_MANUAL_AE = 0;
    public static final long DEF_MANUAL_SEC = 40000000L;
    public static final int DEF_MANUAL_ISO = 100;
    public static final int DEF_MANUAL_WB = 5500;
    public static final float DEF_MANUAL_AF = 0.0f;
    public static final float DEF_MANUAL_WT = 1.0f;

    /**
     * 最低的sec值.
     * 这个值对应着的快门时间是 1/8000, 低于这个值得曝光时间没有意义,因为图片全黑.
     */
    public static final long LOWEST_SEC = 125000;
}
