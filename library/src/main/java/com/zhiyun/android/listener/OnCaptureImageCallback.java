package com.zhiyun.android.listener;

import com.zhiyun.android.cameraview.CameraView;

/**
 * Created by shangzheng on 2017/9/7
 * ☃☃☃ 19:13.
 *
 * 用于单独返回拍照完成后获取数据的接口
 * 区别于{@link com.zhiyun.android.cameraview.CameraView#addCallback(CameraView.Callback)},
 * 本接口只关心并返回拍照这一件事
 */

public interface OnCaptureImageCallback {
    void onPictureTaken(byte[] data);
}
