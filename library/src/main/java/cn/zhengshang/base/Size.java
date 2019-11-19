package cn.zhengshang.base;

import android.media.CamcorderProfile;

import androidx.annotation.NonNull;

import java.util.Objects;

import cn.zhengshang.util.CamcorderUtil;

/**
 * Immutable class for describing width and height dimensions in pixels.
 */
public class Size implements Comparable<Size>, Cloneable {

    private final int mWidth;
    private final int mHeight;
    private int mFps = 30;

    /**
     * Create a new immutable Size instance.
     *
     * @param width  The width of the size, in pixels
     * @param height The height of the size, in pixels
     */
    public Size(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public Size(int width, int height, int fps) {
        mWidth = width;
        mHeight = height;
        mFps = fps;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public int getFps() {
        return mFps;
    }

    public void setFps(int fps) {
        mFps = fps;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (this == o) {
            return true;
        }
        if (o instanceof Size) {
            Size size = (Size) o;
            return mWidth == size.mWidth && mHeight == size.mHeight && mFps == size.mFps;
        }
        return false;
    }

    @Override
    public String toString() {
        return mWidth + "x" + mHeight + " " + mFps + "p";
    }

    @Override
    public int hashCode() {
        return Objects.hash(getWidth(), getHeight(), getFps());
    }

    @Override
    public int compareTo(@NonNull Size another) {
        if (mWidth * mHeight == another.mWidth * another.mHeight) {
            return mFps - another.mFps;
        }
        return mWidth * mHeight - another.mWidth * another.mHeight;
    }

    public boolean hasHighSpeedCamcorder(int cameraId) {
        return CamcorderUtil.hasHighSpeedCamcorder(this, cameraId);
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public CamcorderProfile getHighSpeedCamcorderProfile(int cameraID) {
        if (mWidth == 720 && mHeight == 480) {
            return CamcorderProfile.get(cameraID, CamcorderProfile.QUALITY_HIGH_SPEED_480P);
        } else if (mWidth == 1280 && mHeight == 720) {
            return CamcorderProfile.get(cameraID, CamcorderProfile.QUALITY_HIGH_SPEED_720P);
        } else if (mWidth == 1920 && mHeight == 1080) {
            return CamcorderProfile.get(cameraID, CamcorderProfile.QUALITY_HIGH_SPEED_1080P);
        } else if (mWidth == 3840 && mHeight == 2160) {
            return CamcorderProfile.get(cameraID, CamcorderProfile.QUALITY_HIGH_SPEED_2160P);
        } else {
            return CamcorderProfile.get(cameraID, CamcorderProfile.QUALITY_HIGH_SPEED_720P);
        }
    }

    public int getRecommendBitRateFromCamcorder(int cameraId) {
        if (mWidth == 720 && mHeight == 480) {
            return getBitrate(cameraId, CamcorderProfile.QUALITY_480P);
        } else if (mWidth == 1280 && mHeight == 720) {
            return getBitrate(cameraId, CamcorderProfile.QUALITY_720P);
        } else if (mWidth == 1920 && mHeight == 1080) {
            return getBitrate(cameraId, CamcorderProfile.QUALITY_1080P);
        } else if (mWidth == 3840 && mHeight == 2160) {
            return getBitrate(cameraId, CamcorderProfile.QUALITY_2160P);
        } else {
            return getBitrate(cameraId, CamcorderProfile.QUALITY_720P);
        }
    }

    private int getBitrate(int cameraId, int quality) {
        if (CamcorderProfile.hasProfile(cameraId, quality)) {
            return CamcorderProfile.get(cameraId, quality).videoBitRate;
        } else {
            return Constants.DEF_BITRATE;
        }
    }

    public CamcorderProfile getTimeLapseProfile(int cameraId) {
        if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_TIME_LAPSE_1080P)) {
            return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_TIME_LAPSE_1080P);
        } else if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_TIME_LAPSE_720P)) {
            return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_TIME_LAPSE_720P);
        } else if (CamcorderProfile.hasProfile(cameraId, CamcorderProfile.QUALITY_TIME_LAPSE_480P)) {
            return CamcorderProfile.get(cameraId, CamcorderProfile.QUALITY_TIME_LAPSE_480P);
        }
        return null;
    }

    public boolean is720P() {
        return mWidth == 1280 && mHeight == 720;
    }

    public boolean is1080P() {
        return mWidth == 1920 && mHeight == 1080;
    }

    public boolean is4K() {
        return mWidth == 3840 && mHeight == 2160;
    }
}
