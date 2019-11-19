package cn.zhengshang.controller;

import android.media.MediaActionSound;

import cn.zhengshang.util.MediaActionSoundBlackList;

public class SoundController {

    /**
     * 加载[拍照][录像]时的声音
     */
    public static final int SOUND_ID_START = 0;
    public static final int SOUND_ID_STOP = 1;
    public static final int SOUND_ID_CLICK = 2;

    private MediaActionSound mMediaActionSound;

    private SoundController() {

    }

    private static class SingletonHolder {
        private static final SoundController INSTANCE = new SoundController();
    }

    public static SoundController getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * 播放声音
     *
     * @param soundId 声音ID,由上面的常量定义
     */
    public void playSound(int soundId) {
        if (mMediaActionSound != null) {
            switch (soundId) {
                case SOUND_ID_CLICK:
                    mMediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
                    break;
                case SOUND_ID_START:
                    mMediaActionSound.play(MediaActionSound.START_VIDEO_RECORDING);
                    break;
                case SOUND_ID_STOP:
                    mMediaActionSound.play(MediaActionSound.STOP_VIDEO_RECORDING);
                    break;
            }
        }
    }

    /**
     * 加载声音
     */
    public void loadSound() {
        if (MediaActionSoundBlackList.isSupported() && mMediaActionSound == null) {
            try { // 部分手机无法加载快门音
                mMediaActionSound = new MediaActionSound();
                mMediaActionSound.load(MediaActionSound.SHUTTER_CLICK);
                mMediaActionSound.load(MediaActionSound.START_VIDEO_RECORDING);
                mMediaActionSound.load(MediaActionSound.STOP_VIDEO_RECORDING);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void release() {
        if (mMediaActionSound != null) {
            mMediaActionSound.release();
            mMediaActionSound = null;
        }
    }

}
