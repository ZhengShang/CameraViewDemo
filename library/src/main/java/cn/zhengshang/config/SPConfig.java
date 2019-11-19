package cn.zhengshang.config;

import android.content.Context;
import android.content.SharedPreferences;

import cn.zhengshang.base.Constants;
import cn.zhengshang.base.Size;

/**
 * Created by shangzheng on 2019-07-25.
 *            🐳🐳🐳🍒           11:12 🥥
 */
public class SPConfig {

    private volatile static SPConfig INSTANCE = null;
    private SharedPreferences mSp;

    private SPConfig() {

    }

    public static SPConfig getInstance() {
        if (INSTANCE == null) {
            synchronized (SPConfig.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SPConfig();
                }
            }
        }
        return INSTANCE;
    }

    public void changeSpName(Context context, int facing) {
        String spName = "camera_sdk_inner_" + facing;
        mSp = context.getSharedPreferences(spName, Context.MODE_PRIVATE);
    }

    public void saveCaptureMode(CaptureMode captureMode) {
        SharedPreferences.Editor edit = mSp.edit();
        edit.putInt("capture_mode", captureMode.getValue());
        edit.apply();
    }

    public CaptureMode loadCaptureMode() {
        int value = mSp.getInt("capture_mode", CaptureMode.NORMAL_VIDEO.getValue());
        return CaptureMode.findByValue(value);
    }

    public void saveVideoSize(Size size) {
        SharedPreferences.Editor edit = mSp.edit();
        edit.putInt("video_size_width", size.getWidth());
        edit.putInt("video_size_height", size.getHeight());
        edit.putInt("video_size_fps", size.getFps());
        edit.apply();
    }

    public Size loadVideoSize() {
        int width = mSp.getInt("video_size_width", Constants.DEF_VIDEO_SIZE.getWidth());
        int height = mSp.getInt("video_size_height", Constants.DEF_VIDEO_SIZE.getHeight());
        int fps = mSp.getInt("video_size_fps", Constants.DEF_VIDEO_SIZE.getFps());
        return new Size(width, height, fps);
    }

    public void saveCaptureRate(double rate) {
        SharedPreferences.Editor edit = mSp.edit();
        edit.putString("video_capture_rate", String.valueOf(rate));
        edit.apply();
    }

    public double loadCaptureRate() {
        String rate = mSp.getString("video_capture_rate", String.valueOf(Constants.DEF_CAPTURERATE));
        return Double.parseDouble(rate);
    }

    public void saveBitrate(int bitrate) {
        SharedPreferences.Editor edit = mSp.edit();
        edit.putInt("video_bitrate", bitrate);
        edit.apply();
    }

    public int loadBitrate() {
        return mSp.getInt("video_bitrate", Constants.DEF_BITRATE);
    }

    /**
     * 将华为手机是否支持超级慢动作保存在这里,避免每次都去查询
     * @return true支持, false不支持
     */
    public boolean isSupportHwSuperSlow() {
        return mSp.getBoolean("is_support_hw_super_slow", true);
    }

    public void saveSupportHwSuperSlow(boolean support) {
        SharedPreferences.Editor edit = mSp.edit();
        edit.putBoolean("is_support_hw_super_slow", support);
        edit.apply();
    }
}
