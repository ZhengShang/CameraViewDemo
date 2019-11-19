package cn.zhengshang.listener;

/**
 * Created by shangzheng on 2017/9/5
 * ☃☃☃ 10:58.
 *
 * 用于返回手动模式下,实时返回当前参数的接口
 */

public interface OnManualValueListener {
    /**
     * 实时返回当前的iso数值
     */
    void onIsoChanged(int iso);

    /**
     * 实时返回当前的快门速度(sec)数值
     */
    void onSecChanged(long sec);

    /**
     * 白平衡色温变化
     */
    void onTemperatureChanged(int temperature);
}
