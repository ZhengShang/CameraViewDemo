package com.zhiyun.android.base;

import android.media.CamcorderProfile;
import android.support.annotation.NonNull;

/**
 * Immutable class for describing width and height dimensions in pixels.
 */
public class Size implements Comparable<Size> {

    private final int mWidth;
    private final int mHeight;

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

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
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
            return mWidth == size.mWidth && mHeight == size.mHeight;
        }
        return false;
    }

    @Override
    public String toString() {
        return mWidth + "x" + mHeight;
    }

    @Override
    public int hashCode() {
        // assuming most sizes are <2^16, doing a rotate will give us perfect hashing
        return mHeight ^ ((mWidth << (Integer.SIZE / 2)) | (mWidth >>> (Integer.SIZE / 2)));
    }

    @Override
    public int compareTo(@NonNull Size another) {
        return mWidth * mHeight - another.mWidth * another.mHeight;
    }

    public CamcorderProfile getCamcorderProfile() {
        if (mWidth == 720 && mHeight == 480) {
            return CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
        } else if (mWidth == 1280 && mHeight == 720) {
            return CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
        } else if (mWidth == 1920 && mHeight == 1080) {
            return CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
        } else if (mWidth == 3840 && mHeight == 2160) {
            return CamcorderProfile.get(CamcorderProfile.QUALITY_2160P);
        } else {
            return CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
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
            return 7000000;
        }
    }
}
