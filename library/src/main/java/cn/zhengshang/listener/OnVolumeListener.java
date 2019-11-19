package cn.zhengshang.listener;

/**
 * Created by shangzheng on 24/03/2018.
 *            🐳🐳🐳🍒    at 1:52 PM
 */

public interface OnVolumeListener {
    /**
     * 录制视频时的实时音量大小
     * @param volume 音量(分贝)
     */
    void onRecordingVolume(int volume);
}
