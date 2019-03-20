package com.zhiyun.android.util;


import android.os.Build;

/**
 * MediaActionSound黑名单
 * 因为一些手机调用MediaActionSound API会出现SDK代码层面的异常， 固使用该方式处理
 * Created by Lize on 2019/1/20.
 */
public class MediaActionSoundBlackList {

    public static boolean isSupported() {
        String brand = Build.BRAND.toLowerCase();
        return !"meitu".equals(brand);
    }
}
