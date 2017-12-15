package com.zhiyun.android.base;


import android.content.res.Resources;

public interface Constants {

    String CAMERA_API_CAMERA1 = "camera1";
    String CAMERA_API_CAMERA2 = "camera2";

    AspectRatio DEFAULT_ASPECT_RATIO = AspectRatio.of(16, 9);

    AspectRatio FULL_SCREEN_RATIO = AspectRatio.of(
            Resources.getSystem().getDisplayMetrics().widthPixels,
            Resources.getSystem().getDisplayMetrics().heightPixels
    );

    boolean DEFAULT_PLAY_SOUND = true;
    // Durations in nanoseconds
    long MICRO_SECOND = 1000;
    long MILLI_SECOND = MICRO_SECOND * 1000;
    long ONE_SECOND = MILLI_SECOND * 1000;

    int MANUAL_WB_LOWER = 2000;
    int MANUAL_WB_UPER = 12000;

    int FACING_BACK = 0;
    int FACING_FRONT = 1;

    int FLASH_OFF = 0;
    int FLASH_ON = 1;
    int FLASH_TORCH = 2;
    int FLASH_AUTO = 3;
    int FLASH_RED_EYE = 4;

    int GRID_NONE = 0;//网格线 无
    int GRID_GRID = 1;//网格线 网格
    int GRID_GRID_AND_DIAGONAL = 2;//网格线 网格+对角线
    int GRID_CENTER_POINT = 3; //网格线 中心点

    int LANDSCAPE_90 = 90;
    int LANDSCAPE_270 = 270;

    int AWB_MODE_OFF = 0;
    int AWB_MODE_AUTO = 1;
    int AWB_MODE_INCANDESCENT = 2;
    int AWB_MODE_FLUORESCENT = 3;
    int AWB_MODE_WARM_FLUORESCENT = 4;
    int AWB_MODE_DAYLIGHT = 5;
    int AWB_MODE_CLOUDY_DAYLIGHT = 6;
    int AWB_MODE_TWILIGHT = 7;
    int AWB_MODE_SHADE = 8;
}
