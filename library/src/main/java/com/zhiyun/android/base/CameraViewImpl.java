package com.zhiyun.android.base;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.Range;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import com.zhiyun.android.cameraview.FocusMarkerLayout;
import com.zhiyun.android.cameraview.R;
import com.zhiyun.android.listener.OnAeChangeListener;
import com.zhiyun.android.listener.OnCaptureImageCallback;
import com.zhiyun.android.listener.OnManualValueListener;
import com.zhiyun.android.listener.OnVolumeListener;
import com.zhiyun.android.recorder.MediaRecord;
import com.zhiyun.android.util.CameraUtil;

import java.util.List;
import java.util.Set;

public abstract class CameraViewImpl {

    private static final int FOCUS_AREA_SIZE_DEFAULT = 300;
    private static final int FOCUS_METERING_AREA_WEIGHT_DEFAULT = 500;

    protected final Callback mCallback;
    protected OnManualValueListener mOnManualValueListener;
    protected OnAeChangeListener mOnAeChangeListener;
    protected OnCaptureImageCallback mOnCaptureImageCallback;
    protected OnVolumeListener mOnVolumeListener;

    protected final PreviewImpl mPreview;
    /**
     * HDR照片模式
     */
    protected boolean mHdrMode;
    /**
     * 当前模式，是否为手动模式
     */
    protected boolean mManualMode;
    /**
     * 拍照的图片大小
     */
    protected Size mPicSize = new Size(1280, 720);

    /**
     * 拍照的图片格式
     */
    protected int mPicFormat = ImageFormat.JPEG;

    /**
     * 录制的视频的尺寸大小
     */
    protected Size mVideoSize = new Size(1280, 720);
    /**
     * 拍摄的视频输出路径
     */
    protected String mNextVideoAbsolutePath;
    /**
     * Whether the app is recording video now
     */
    protected boolean mIsRecordingVideo;
    /**
     * MediaRecorder
     */
    protected MediaRecorder mMediaRecorder;
    protected MediaRecord mMediaRecord;

    /**
     * 加载[拍照][录像]时的声音
     */
    protected static final int SOUND_ID_START = 0;
    protected static final int SOUND_ID_STOP = 1;
    protected static final int SOUND_ID_CLICK = 2;
    //    private SoundPool mSoundPool;
    private MediaActionSound mMediaActionSound;
    private boolean mPlaySound = Constants.DEFAULT_PLAY_SOUND;
    /**
     * 手机当前的方向.
     */
    protected int mPhoneOrientation;

    /**
     * 静音视频.
     * 此开关用于拍摄特殊视频的时候使用,比如延时摄影等.
     * 此种视频下,视频的播放速率为非常规速率,播放的时候声音会不对,所以不需要声音
     */
    protected boolean mMuteVideo;

    /**
     * 视频码率
     */
    protected int mBitrate;

    /**
     * 捕获帧,可实现慢动作,延迟摄影等功能
     */
    protected double mCaptureRate;
    /**
     * 视频帧率
     */
    protected int mFps;

    protected int mAwbMode;
    protected int mAe;
    protected long mSec;
    protected int mIso;
    protected int mManualWB;
    protected float mAf;
    protected float mWt = 1;
    protected float mZoomRatio = 1;
    protected long mAvailableSpace;
    private boolean mUseMediaRecord;

    private ValueAnimator mSmoothFocusAnim;

    public CameraViewImpl(Callback callback, PreviewImpl preview) {
        mCallback = callback;
        mPreview = preview;
    }

    public void addOnManualValueListener(OnManualValueListener onManualValueListener) {
        mOnManualValueListener = onManualValueListener;
    }

    public void addOnAeChangedListener(OnAeChangeListener onAeChangeListener) {
        mOnAeChangeListener = onAeChangeListener;
    }

    public void addOnCaptureImageCallback(OnCaptureImageCallback onCaptureImageCallback) {
        mOnCaptureImageCallback = onCaptureImageCallback;
    }

    public void addOnVolumeListener(OnVolumeListener onVolumeListener) {
        mOnVolumeListener = onVolumeListener;
    }

    private Choreographer.FrameCallback mAvailableSpaceFrameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (!mIsRecordingVideo) {
                return;
            }
            mAvailableSpace = CameraUtil.getAvailableSpace();
            if (mAvailableSpace <= Constants.LOW_STORAGE_THRESHOLD_BYTES) {
                stopRecordingVideo();
                Context context = getView().getContext();
                CameraUtil.showBlackToast(context, context.getString(R.string.video_reach_size_limit), mPhoneOrientation);
            }
            Choreographer.getInstance().postFrameCallbackDelayed(this, 1000);
        }
    };
    private Choreographer.FrameCallback mVolumeFrameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (mIsRecordingVideo && !mMuteVideo) {
                double db;
                if (mUseMediaRecord) {
                    if (mMediaRecord == null) {
                        return;
                    }
                    db = mMediaRecord.getVolumeLevel();
                } else {
                    if (mMediaRecorder == null) {
                        return;
                    }
                    db = 20 * Math.log10(mMediaRecorder.getMaxAmplitude() / 0.1f);
                }
                if (mOnVolumeListener != null) {
                    mOnVolumeListener.onRecordingVolume((int) db);
                    Choreographer.getInstance().postFrameCallbackDelayed(this, 500);
                }
            }
        }
    };

    protected void addVolumeListener(boolean useMediaRecord) {
        mUseMediaRecord = useMediaRecord;
        Choreographer.getInstance().postFrameCallback(mVolumeFrameCallback);
    }

    public View getView() {
        return mPreview.getView();
    }

    /**
     * @return {@code true} if the implementation was able to start the camera session.
     */
    public abstract boolean start();

    /**
     * 实时监测剩余手机存储空间,如果小于一定的大小,自动停止录制.
     */
    protected void updateAvailableSpace() {
        Choreographer.getInstance().postFrameCallback(mAvailableSpaceFrameCallback);
    }


    public abstract boolean isCameraOpened();

    public abstract void setFacing(int facing);

    public abstract int getFacing();

    public abstract String getCameraId();

    public abstract Set<AspectRatio> getSupportedAspectRatios();

    public abstract List<android.util.Size> getSupportedPicSizes();

    public abstract boolean isSupported60Fps();

    public abstract List<android.util.Size> getSupportedVideoSize();

    public float getFpsWithSize(android.util.Size size) {
        return 30;
    }

    public abstract int[] getSupportAWBModes();

    /**
     * @return {@code true} if the aspect ratio was changed.
     */
    public abstract boolean setAspectRatio(AspectRatio ratio);

    public abstract AspectRatio getAspectRatio();

    public abstract void setAutoFocus(boolean autoFocus);

    public abstract boolean getAutoFocus();

    public abstract boolean isFlashAvailable();

    public abstract void setFlash(int flash);

    public abstract int getFlash();

    public abstract void setTorch(boolean open);

    public abstract boolean isTorch();

    public abstract void takePicture();

    public long getAvailableSpace() {
        return mAvailableSpace;
    }

    /**
     * 根据点击区域重新对焦
     * @param lock 对焦完成后,是否锁定AE/AF
     */
    public abstract void resetAF(MotionEvent e, boolean lock);

    /**
     * 锁定AE和AF.
     * 重新点击屏幕后解除锁定
     */
    public abstract void lockAEandAF();

    public abstract void unLockAEandAF();

    /**
     * 双指在屏幕上缩放视图大小
     *
     * @param factor 缩放因子,用以判断放大和缩小
     */
    public void gestureScaleZoom(float factor) {
        if (factor >= 1.001f) {
            if (mWt >= getMaxZoom()) {
                return;
            }
            mWt += 0.1f;
            mWt = Math.round(mWt * 10) / 10f;
            scaleZoom(mWt);
        } else if (factor <= 0.999f) {
            if (mWt <= 1) {
                return;
            }
            mWt -= 0.1f;
            mWt = Math.round(mWt * 10) / 10f;
            scaleZoom(mWt);
        }
    }

    public void setPlaySound(boolean playSound) {
        mPlaySound = playSound;
    }

    public void setMuteVideo(boolean muteVideo) {
        mMuteVideo = muteVideo;
    }

    public boolean getMuteVideo() {
        return mMuteVideo;
    }

    public abstract void startRecordingVideo();

    public abstract void stopRecordingVideo();

    public boolean isRecordingVideo() {
        return mIsRecordingVideo;
    }

    public void stop() {
        if (mMediaActionSound != null) {
            mMediaActionSound.release();
            mMediaActionSound = null;
        }

        Choreographer.getInstance().removeFrameCallback(mAvailableSpaceFrameCallback);
        Choreographer.getInstance().removeFrameCallback(mVolumeFrameCallback);
    }

    public int getBitrate() {
        return mBitrate;
    }

    public void setCaptureRate(double rate) {
        mCaptureRate = rate;
    }

    public double getCaptureRate() {
        return mCaptureRate;
    }

    public void setVideoOutputFilePath(String path) {
        mNextVideoAbsolutePath = path;
    }

    public String getVideoOutputFilePath() {
        return mNextVideoAbsolutePath;
    }

    public int getAwbMode() {
        return mAwbMode;
    }

    public abstract void setAWBMode(int mode);

    public int getAe() {
        return mAe;
    }

    public long getSec() {
        return mSec;
    }

    public int getIso() {
        return mIso;
    }

    public int getManualWB() {
        return mManualWB;
    }

    public float getAf() {
        return mAf;
    }

    public float getWt() {
        return mWt;
    }

    public float getZoomRatio() {
        return mZoomRatio;
    }

    public abstract void stopSmoothZoom();

    /**
     * 手机剩余空间是否不够.空间不够的话,无法进行视频录制.
     * @return TRUE不够, FALSE够.
     */
    protected boolean lowAvailableSpace() {
        long availableSpace = CameraUtil.getAvailableSpace();
        if (availableSpace <= Constants.LOW_STORAGE_THRESHOLD_BYTES) {
            Context context = getView().getContext();
            CameraUtil.showBlackToast(context, context.getString(R.string.spaceIsLow_content), mPhoneOrientation);
            return true;
        }
        return false;
    }

    public void setBitrate(int bitrate) {
        //查看推荐分辨率下的当前码率，谁大用哪个。
        int recommendBitRate = mVideoSize.getRecommendBitRateFromCamcorder(getFacing());
        if (bitrate > recommendBitRate) {
            mBitrate = bitrate;
        } else {
            mBitrate = recommendBitRate;
        }
    }

    public abstract void startSmoothZoom(float start, float end, long duration);

    public void stopSmoothFocus() {
        if (mSmoothFocusAnim != null) {
            mSmoothFocusAnim.cancel();
        }
    }

    public abstract void setDisplayOrientation(int displayOrientation);

    public interface Callback {

        void onCameraOpened();

        void onCameraClosed();

        void onRequestBuilderCreate();

        void onPictureTaken(byte[] data);

        void onVideoRecordingStarted();

        void onVideoRecordStoped();

        void onVideoRecordingFailed();
    }

    protected int getFocusAreaSize() {
        return FOCUS_AREA_SIZE_DEFAULT;
    }

    protected int getFocusMeteringAreaWeight() {
        return FOCUS_METERING_AREA_WEIGHT_DEFAULT;
    }

    public abstract float getMaxZoom();

    public List<Integer> getZoomRatios() {
        return null;
    }

    public abstract void scaleZoom(float scale);

    public abstract String getCameraAPI();

    public void zoomIn() {
        gestureScaleZoom(0.989f);
    }

    public void zoomOut() {
        gestureScaleZoom(1.011f);
    }

    public void foucsNear() {
        if (mAf > 0) {
            mAf -= 0.1;
            mAf = Math.round(mAf * 10) / 10f;
            setAFValue(mAf);
        }
    }

    public void focusFar() {
        if (mAf < getAFMaxValue()) {
            mAf += 0.1;
            mAf = Math.round(mAf * 10) / 10f;
            setAFValue(mAf);
        }
    }

    public Size getPicSize() {
        return mPicSize;
    }

    public abstract void setPicSize(Size size);

    public Size getVideoSize() {
        return mVideoSize;
    }

    public void setVideoSize(Size size) {
        mVideoSize = size;
    }

    public int getFps() {
        return mFps;
    }

    public void setFps(int fps) {
        mFps = fps;
    }

    public int getPicFormat() {
        return mPicFormat;
    }

    public void setPicFormat(int format) {
        mPicFormat = format;
    }

    public void setHdrMode(boolean hdr) {
        mHdrMode = hdr;
    }

    public abstract void setStabilizeEnable(boolean enable);

    /**
     * 是否支持防抖
     *
     * @return 只要支持 图片防抖 或 视频防抖 之中的一个,就返回true,反之,返回false.
     */
    public abstract boolean isSupportedStabilize();

    public abstract boolean getStabilizeEnable();

    /**
     * 只是以下6个中的一个就算支持手动模式
     * <p>
     * AE SEC ISO WB AF WT
     */
    public abstract boolean isSupportedManualMode();

    public abstract boolean isManualControlAF();

    public abstract void setManualMode(boolean manual);

    public boolean isManualMode() {
        return mManualMode;
    }

    public abstract void setIsoAuto();

    //ae
    public abstract boolean isManualAESupported();

    public abstract Range<Integer> getAERange();

    public abstract void setAEValue(int value);

    public abstract float getAeStep();

    //sec
    public abstract boolean isManualSecSupported();

    public abstract Range<Long> getSecRange();

    public abstract void setSecValue(long value);

    //ISO
    public abstract boolean isManualISOSupported();

    public abstract Object getISORange();

    public abstract void setISOValue(int value);

    //wb
    public abstract boolean isManualWBSupported();

    public abstract Range<Integer> getManualWBRange();

    public abstract void setManualWBValue(int value);

    //af
    public abstract boolean isManualAFSupported();

    public abstract Float getAFMaxValue();

    public abstract void setAFValue(float value);

    public abstract boolean isManualWTSupported();

    /**
     * 发送拍照完成广播,以便在{@link FocusMarkerLayout#mLocalBroadcast}中接收,
     * 然后播放动画
     */
    protected void sendTakePhotoAction() {
        Intent intent = new Intent();
        intent.setAction(FocusMarkerLayout.BROADCAST_ACTION_TAKE_PHOTO);
        LocalBroadcastManager.getInstance(getView().getContext()).sendBroadcast(intent);
    }

    /**
     * 用于在Camera2中录制视频完成时调用,因为锁定AE/AF录制完成后,会重置预览,此时需要清除界面上的对焦框
     */
    protected void sendRecordingStopAction() {
        Intent intent = new Intent();
        intent.setAction(FocusMarkerLayout.BROADCAST_ACTION_RECORING_STOP);
        LocalBroadcastManager.getInstance(getView().getContext()).sendBroadcast(intent);
    }

    protected void loadAudio() {
        // 美图手机这里出现内部错误
        try {
            if (mMediaActionSound == null) {
                mMediaActionSound = new MediaActionSound();
                mMediaActionSound.load(MediaActionSound.SHUTTER_CLICK);
                mMediaActionSound.load(MediaActionSound.START_VIDEO_RECORDING);
                mMediaActionSound.load(MediaActionSound.STOP_VIDEO_RECORDING);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void playSound(int soundId) {
        if (mMediaActionSound != null && mPlaySound) {
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

    protected void releaseMediaRecorder() {
        // Stop recording
        if (mMediaRecorder != null) {
            try {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
                mMediaRecorder.release();
                mMediaRecorder = null;
            } catch (Exception e) {
                Log.e("CameraViewImpl", "releaseMediaRecorder: e = " + e.getMessage());
            }
        }
        if (mMediaRecord != null) {
            try {
                mMediaRecord.stop();
                mMediaRecord.reset();
                mMediaRecord = null;
            } catch (Exception e) {
                Log.e("CameraViewImpl", "releaseMediaRecord: e = " + e.getMessage());
            }
        }
    }

    public void startSmoothFocus(final float start, final float end, long duration) {
        stopSmoothFocus();
        if (start == end) {
            return;
        }
        if (mSmoothFocusAnim == null) {
            mSmoothFocusAnim = ValueAnimator.ofFloat(0, 1);
            mSmoothFocusAnim.setInterpolator(null);
        }
        mSmoothFocusAnim.removeAllUpdateListeners();
        mSmoothFocusAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                mAf = start + value * (end - start);
                mAf = Math.round(mAf * 10) / 10f;
                setAFValue(mAf);
            }
        });
        mSmoothFocusAnim.setDuration(duration);
        mSmoothFocusAnim.start();
    }

    public void setPhoneOrientation(int orientation) {
        mPhoneOrientation = orientation;
    }
}
