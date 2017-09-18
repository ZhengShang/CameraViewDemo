package com.zhiyun.android.listener;

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
    void onIsoChanged(long iso);

    /**
     * 实时返回当前的快门速度(sec)数值
     */
    void onSecChanged(long sec);
}
