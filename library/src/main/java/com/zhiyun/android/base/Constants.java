package com.zhiyun.android.base;


public interface Constants {
    AspectRatio DEFAULT_ASPECT_RATIO = AspectRatio.of(16, 9);

    boolean DEFAULT_PLAY_SOUND = true;
    // Durations in nanoseconds
    long MICRO_SECOND = 1000;
    long MILLI_SECOND = MICRO_SECOND * 1000;
    long ONE_SECOND = MILLI_SECOND * 1000;

    long mOddExposure = ONE_SECOND / 33;
    long mEvenExposure = ONE_SECOND / 33;

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
}
