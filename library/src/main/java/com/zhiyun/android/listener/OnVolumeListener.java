package com.zhiyun.android.listener;

public interface OnVolumeListener {
    /**
     * 录制视频时的实时音量大小
     * @param volume 音量(分贝)
     */
    void onRecordingVolume(int volume);
}
