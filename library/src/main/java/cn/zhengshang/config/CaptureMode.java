package cn.zhengshang.config;

public enum CaptureMode {
    UNKNOWN(-1),
    PHOTO(1),
    VIDEO(2),
    NORMAL_VIDEO(3),
    HIGH_SPEED_VIDEO(4),
    SLOW_MOTION(5),
    TIMELAPSE(6),
    MOVING_TIMELAPSE(7),
    FOCUS_TIMELAPSE(8);

    private int value;

    CaptureMode(int value) {
        this.value = value;
    }

    public static CaptureMode findByValue(int value) {
        for (CaptureMode mode : CaptureMode.values()) {
            if (mode.getValue() == value) {
                return mode;
            }
        }
        return CaptureMode.UNKNOWN;
    }

    public int getValue() {
        return value;
    }
}
