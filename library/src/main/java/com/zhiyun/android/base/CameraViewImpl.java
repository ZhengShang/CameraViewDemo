/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhiyun.android.base;

import android.util.Range;
import android.view.View;

import java.util.Set;

public abstract class CameraViewImpl {

    private static final int FOCUS_AREA_SIZE_DEFAULT = 200;
    private static final int FOCUS_METERING_AREA_WEIGHT_DEFAULT = 1000;
    private static final int DELAY_MILLIS_BEFORE_RESETTING_FOCUS = 3000;

    protected final Callback mCallback;

    protected final PreviewImpl mPreview;

    public CameraViewImpl(Callback callback, PreviewImpl preview) {
        mCallback = callback;
        mPreview = preview;
    }

    public View getView() {
        return mPreview.getView();
    }

    /**
     * @return {@code true} if the implementation was able to start the camera session.
     */
    public abstract boolean start();

    public abstract void stop();

    public abstract boolean isCameraOpened();

    public abstract void setFacing(int facing);

    public abstract int getFacing();

    public abstract Set<AspectRatio> getSupportedAspectRatios();

    public abstract android.util.Size[] getSupportedPicSizes();

    public abstract int[] getSupportAWBModes();

    /**
     * @return {@code true} if the aspect ratio was changed.
     */
    public abstract boolean setAspectRatio(AspectRatio ratio);

    public abstract AspectRatio getAspectRatio();

    public abstract void setAutoFocus(boolean autoFocus);

    public abstract boolean getAutoFocus();

    public abstract void setFlash(int flash);

    public abstract int getFlash();

    public abstract void takePicture();

    public abstract void startRecordingVideo();

    public abstract void stopRecordingVideo();

    public abstract boolean isRecordingVideo();

    public abstract void setBitrate(int bitrate);

    public abstract int getBitrate();

    public abstract void setVideoOutputFilePath(String path);

    public abstract String getVideoOutputFilePath();

    public abstract int getAwbMode();

    public abstract void setAWBMode(int mode);

    public abstract int getAe();

    public abstract long getSec();

    public abstract int getIso();

    public abstract int getManualWB();

    public abstract void setDisplayOrientation(int displayOrientation);

    public interface Callback {

        void onCameraOpened();

        void onCameraClosed();

        void onPictureTaken(byte[] data);

        void onVideoRecordingStarted();

        void onVideoRecordStoped();

        void onVideoRecordingFailed();
    }

    public int getFocusAreaSize() {
        return FOCUS_AREA_SIZE_DEFAULT;
    }

    public int getFocusMeteringAreaWeight() {
        return FOCUS_METERING_AREA_WEIGHT_DEFAULT;
    }

    protected void detachFocusTapListener() {
        mPreview.getView().setOnTouchListener(null);
    }

    public abstract void setLensFocalLength(int distance);

    public abstract Size getPicSize();

    public abstract void setPicSize(Size size);

    public abstract Size getVideoSize();

    public abstract void setVideoSize(Size size);

    public abstract int getPicFormat();

    public abstract void setPicFormat(int format);

    public abstract void setManualMode(boolean manual);

    //ae
    public abstract boolean isManualAESupported();

    public abstract Range<Integer> getAERange();

    public abstract void setAEValue(int value);

    //sec
    public abstract boolean isManualSecSupported();

    public abstract Range<Long> getSecRange();

    public abstract void setSecValue(long value);

    //ISO
    public abstract boolean isManualISOSupported();

    public abstract Range<Integer> getISORange();

    public abstract void setISOValue(int value);

    //wb
    public abstract boolean isManualWBSupported();

    public abstract void setManualWBValue(int value);

    //af
    public abstract boolean isManualAFSupported();

    public abstract Float getAFMaxValue();

    public abstract void setAFValue(float value);
}
