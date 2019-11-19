package cn.zhengshang.listener;

public enum CameraError {
    /**
     * 相机打开失败,调用端需要弹窗提示,并结束对应Activity
     */
    OPEN_FAILED,
    /**
     * 关键信息配置失败,调用端需要弹窗提示,并结束对应Activity
     */
    CONFIGURATION_FAILED,
    /**
     * 参数设置失败,不影响正常运行,由调用端决定是否提示.
     */
    PARAM_SETTING_FAILED,
}
