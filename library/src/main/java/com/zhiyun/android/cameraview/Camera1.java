package com.zhiyun.android.cameraview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.os.Build;
import android.support.v4.util.SparseArrayCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.Range;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.zhiyun.android.base.AspectRatio;
import com.zhiyun.android.base.CameraViewImpl;
import com.zhiyun.android.base.Constants;
import com.zhiyun.android.base.PreviewImpl;
import com.zhiyun.android.base.Size;
import com.zhiyun.android.base.SizeMap;
import com.zhiyun.android.util.CameraUtil;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static android.hardware.Camera.Parameters.WHITE_BALANCE_INCANDESCENT;
import static com.zhiyun.android.base.Constants.AWB_MODE_AUTO;
import static com.zhiyun.android.base.Constants.AWB_MODE_CLOUDY_DAYLIGHT;
import static com.zhiyun.android.base.Constants.AWB_MODE_DAYLIGHT;
import static com.zhiyun.android.base.Constants.AWB_MODE_FLUORESCENT;
import static com.zhiyun.android.base.Constants.AWB_MODE_INCANDESCENT;
import static com.zhiyun.android.base.Constants.AWB_MODE_OFF;
import static com.zhiyun.android.base.Constants.AWB_MODE_SHADE;
import static com.zhiyun.android.base.Constants.AWB_MODE_TWILIGHT;
import static com.zhiyun.android.base.Constants.AWB_MODE_WARM_FLUORESCENT;
import static com.zhiyun.android.base.Constants.CAMERA_API_CAMERA1;


@SuppressWarnings("deprecation")
public class Camera1 extends CameraViewImpl {

    private static final int INVALID_CAMERA_ID = -1;
    private static final int DELAY_MILLIS_BEFORE_RESETTING_FOCUS = 3000;

    private static final SparseArrayCompat<String> FLASH_MODES = new SparseArrayCompat<>();

    static {
        FLASH_MODES.put(Constants.FLASH_OFF, Camera.Parameters.FLASH_MODE_OFF);
        FLASH_MODES.put(Constants.FLASH_ON, Camera.Parameters.FLASH_MODE_ON);
        FLASH_MODES.put(Constants.FLASH_TORCH, Camera.Parameters.FLASH_MODE_TORCH);
        FLASH_MODES.put(Constants.FLASH_AUTO, Camera.Parameters.FLASH_MODE_AUTO);
        FLASH_MODES.put(Constants.FLASH_RED_EYE, Camera.Parameters.FLASH_MODE_RED_EYE);
    }

    private int mCameraId;

    private final AtomicBoolean isPictureCaptureInProgress = new AtomicBoolean(false);

    private Camera mCamera;

    private Camera.Parameters mCameraParameters;

    private final Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();

    private final SizeMap mPreviewSizes = new SizeMap();

    private final SizeMap mPictureSizes = new SizeMap();

    private AspectRatio mAspectRatio;

    private boolean mShowingPreview;

    private boolean mAutoFocus;

    private int mFacing;

    private int mFlash;

    private int mDisplayOrientation;

    private int mPhoneOrientation;

    private final Object mCameraLock = new Object();
    private Choreographer.FrameCallback mZoomFrameCallback;

    Camera1(Callback callback, PreviewImpl preview, Context context) {
        super(callback, preview);
        preview.setCallback(new PreviewImpl.Callback() {
            @Override
            public void onSurfaceChanged() {
                if (mCamera != null) {
                    setUpPreview();
                    adjustCameraParameters();
                }
            }
        });
    }

    @Override
    public boolean start() {
        loadAudio();
        chooseCamera();
        openCamera();
        if (mCamera == null) {
            return false;
        }
        if (mPreview.isReady()) {
            setUpPreview();
        }
        mShowingPreview = true;
        mCamera.startPreview();
        return true;
    }

    @Override
    public void stop() {
        super.stop();

        if (mCamera != null) {
            mCamera.stopPreview();
        }
        mShowingPreview = false;
        releaseCamera();
    }

    // Suppresses Camera#setPreviewTexture
    @SuppressLint("NewApi")
    private void setUpPreview() {
        try {
            if (mPreview.getOutputClass() == SurfaceHolder.class) {
                mCamera.setPreviewDisplay(mPreview.getSurfaceHolder());
            } else {
                mCamera.setPreviewTexture((SurfaceTexture) mPreview.getSurfaceTexture());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean isCameraOpened() {
        return mCamera != null;
    }

    @Override
    public void setFacing(int facing) {
        if (mFacing == facing) {
            return;
        }
        mFacing = facing;
        if (isCameraOpened()) {
            stop();
            start();
        }
    }

    @Override
    public int getFacing() {
        return mFacing;
    }

    @Override
    public String getCameraId() {
        return String.valueOf(mCameraId);
    }

    @Override
    public Set<AspectRatio> getSupportedAspectRatios() {
        SizeMap idealAspectRatios = mPreviewSizes;
        for (AspectRatio aspectRatio : idealAspectRatios.ratios()) {
            if (mPictureSizes.sizes(aspectRatio) == null) {
                idealAspectRatios.remove(aspectRatio);
            }
        }
        return idealAspectRatios.ratios();
    }

    @Override
    public List<android.util.Size> getSupportedPicSizes() {
        List<Camera.Size> supportedPictureSizes = mCameraParameters.getSupportedPictureSizes();
        Collections.sort(supportedPictureSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size o1, Camera.Size o2) {
                return (o1.width + o1.height) - (o2.width + o2.height);
            }
        });
        List<android.util.Size> size = new ArrayList<>();
        for (Camera.Size pictureSize : supportedPictureSizes) {
            size.add(new android.util.Size(pictureSize.width, pictureSize.height));
        }
        return size;
    }

    @Override
    public boolean isSupported60Fps() {
        final List<int[]> supportedPreviewFpsRange = mCameraParameters.getSupportedPreviewFpsRange();
        if (supportedPreviewFpsRange == null || supportedPreviewFpsRange.isEmpty()) {
            return false;
        } else {
            for (int[] fpsRange : supportedPreviewFpsRange) {
                for (int fps : fpsRange) {
                    if (fps / 1000 >= 60) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public List<android.util.Size> getSupportedVideoSize() {
        List<Camera.Size> supportedVideoSizes = mCameraParameters.getSupportedVideoSizes();
        if (supportedVideoSizes == null) {
            return new ArrayList<>();
        }
        List<android.util.Size> size = new ArrayList<>();
        for (Camera.Size videoSize : supportedVideoSizes) {
            size.add(new android.util.Size(videoSize.width, videoSize.height));
        }
        return size;
    }

    @Override
    public int[] getSupportAWBModes() {
        List<String> whiteBalance = mCameraParameters.getSupportedWhiteBalance();
        if (whiteBalance == null) {
            return new int[0];
        }
        if (whiteBalance.contains(WHITE_BALANCE_INCANDESCENT)) {
            //返回2个元素的int数组,代表支持自动白平衡模式
            return new int[2];
        }
        return new int[0];
    }

    @Override
    public boolean setAspectRatio(AspectRatio ratio) {
        if (mAspectRatio == null || !isCameraOpened()) {
            // Handle this later when camera is opened
            mAspectRatio = ratio;
            return true;
        } else if (!mAspectRatio.equals(ratio)) {
            final Set<Size> sizes = mPreviewSizes.sizes(ratio);
            if (sizes == null) {
                throw new UnsupportedOperationException(ratio + " is not supported");
            } else {
                mAspectRatio = ratio;
                adjustCameraParameters();
                return true;
            }
        }
        return false;
    }

    @Override
    public AspectRatio getAspectRatio() {
        return mAspectRatio;
    }

    @Override
    public void setAutoFocus(boolean autoFocus) {
        if (setAutoFocusInternal(autoFocus)) {
            setParameters();
        }
    }

    @Override
    public boolean getAutoFocus() {
        if (!isCameraOpened()) {
            return mAutoFocus;
        }
        String focusMode = mCameraParameters.getFocusMode();
        return focusMode != null && focusMode.contains("continuous");
    }

    @Override
    public boolean isFlashAvailable() {
        return mCameraParameters.getSupportedFlashModes() != null;
    }

    @Override
    public void setFlash(int flash) {
        if (flash == mFlash) {
            return;
        }
        if (setFlashInternal(flash)) {
            setParameters();
        }
    }

    @Override
    public int getFlash() {
        return mFlash;
    }

    @Override
    public void takePicture() {
        if (!isCameraOpened()) {
            Log.e("Camera1", "takePicture: Camera is not ready. Call start() before takePicture().");
            return;
        }
        if (getAutoFocus()) {
            mCamera.cancelAutoFocus();
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean success, Camera camera) {
                    takePictureInternal();
                }
            });
        } else {
            takePictureInternal();
        }
    }

    @Override
    public void startRecordingVideo() {
        synchronized (mCameraLock) {
            try {
                if (prepareMediaRecorder()) {
                    mMediaRecorder.start();
                    mCallback.onVideoRecordingStarted();
                    mIsRecordingVideo = true;
                } else {
                    Log.e("Camera1", "startRecordingVideo: prepre failed");
                    mCallback.onVideoRecordingFailed();
                    releaseMediaRecorder();
                }
            } catch (IOException | RuntimeException e) {
                Log.e("Camera1", "startRecordingVideo: failed = " + e.getMessage());
                mCallback.onVideoRecordingFailed();
                releaseMediaRecorder();
            }
        }
    }

    @Override
    public void stopRecordingVideo() {
        synchronized (mCameraLock) {
            if (mIsRecordingVideo) {

                try {
                    mMediaRecorder.stop();
                    mCallback.onVideoRecordStoped();
                    playSound(SOUND_ID_STOP);
                } catch (RuntimeException e) {
                    Log.e("Camera1", "stopRecordingVideo: failed = " + e.getMessage());
                    mCallback.onVideoRecordingFailed();
                }

                releaseMediaRecorder();
                mIsRecordingVideo = false;
            }

            // 小米手机出现录制视频后无法拍照或录像的问题
            if ("xiaomi".equalsIgnoreCase(Build.MANUFACTURER)) {
                stop();
                start();
            } else {
                //回调此方法,主要的目的在于,在移动延时摄影时,可能调整了CaptureRate的参数.
                //所以用这个方法使CameraActivity2来进行相机参数的重新设置.
                //上面的小米手机通过重启相机也已经包含了这个回调.
                mCallback.onRequestBuilderCreate();
            }
        }
    }

    private boolean prepareMediaRecorder() throws IOException {
        synchronized (mCameraLock) {
            mCamera.unlock();

            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setCamera(mCamera);

//            playSound(SOUND_ID_START);

            if (!mMuteVideo) {
                mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            }
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
            mMediaRecorder.setVideoFrameRate(mFps);
            mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setVideoEncodingBitRate(mBitrate);
            //vivo手机设置30的话,会出现0.5倍的延时效果(生成的时间只有一半,且没有声音)
            if (mCaptureRate != 30) {
                mMediaRecorder.setCaptureRate(mCaptureRate);
            }

            if (!mMuteVideo) {
                mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                mMediaRecorder.setAudioEncodingBitRate(96000);
                mMediaRecorder.setAudioSamplingRate(48000);
                mMediaRecorder.setAudioChannels(2);
            }


            mMediaRecorder.setOrientationHint(calculateCaptureRotation());

            try {
                mMediaRecorder.prepare();
            } catch (IllegalStateException | IOException e) {
                Log.e("Camera1", "prepareMediaRecorder: failed = " + e.getMessage());
                releaseMediaRecorder();
                return false;
            }

            return true;
        }
    }

    @Override
    protected void releaseMediaRecorder() {
        synchronized (mCameraLock) {
            if (mMediaRecorder != null) {
                mMediaRecorder.reset();
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        }
    }

    @Override
    public void setAWBMode(int mode) {
        mAwbMode = mode;
        switch (mode) {
            case AWB_MODE_OFF: //OFF， 手动模式
                break;
            case AWB_MODE_AUTO:             // 自动
                mCameraParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
                break;
            case AWB_MODE_INCANDESCENT: // 白炽灯
                mCameraParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_INCANDESCENT);
                break;
            case AWB_MODE_FLUORESCENT: // 荧光灯
                mCameraParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_FLUORESCENT);
                break;
            case AWB_MODE_WARM_FLUORESCENT: // 温暖的荧光灯
                mCameraParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_WARM_FLUORESCENT);
                break;
            case AWB_MODE_DAYLIGHT: // 晴天
                mCameraParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_DAYLIGHT);
                break;
            case AWB_MODE_CLOUDY_DAYLIGHT: // 阴天
                mCameraParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT);
                break;
            case AWB_MODE_TWILIGHT: //黄昏
                mCameraParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_TWILIGHT);
                break;
            case AWB_MODE_SHADE: //阴影
                mCameraParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_SHADE);
                break;
            default:
                mCameraParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
                break;
        }
        setParameters();
    }

    @Override
    public void setPhoneOrientation(int orientation) {
        mPhoneOrientation = orientation;
    }

    private void takePictureInternal() {
        if (!isPictureCaptureInProgress.getAndSet(true)) {

            try {
                // Set the captureRotation right before taking a picture so it's accurate
                int captureRotation = calculateCaptureRotation();
                mCameraParameters.setRotation(captureRotation);
                setParameters();

                mCamera.takePicture(null, null, null, new Camera.PictureCallback() {
                    @Override
                    public void onPictureTaken(byte[] data, Camera camera) {
                        isPictureCaptureInProgress.set(false);
                        //当此接口不为空的时候,基本表示现在在使用特殊拍照功能(移动延时摄影,全景等),
                        //所以后面的接口回调会生成不必要的图片保存在手机里,且不需要背景闪烁动画.
                        if (mOnCaptureImageCallback != null) {
                            mOnCaptureImageCallback.onPictureTaken(data);
                        } else {
                            mCallback.onPictureTaken(data);
                            sendTakePhotoAction();
                        }
                        playSound(SOUND_ID_CLICK);
                        camera.cancelAutoFocus();
                        camera.startPreview();
                    }
                });
            } catch (Exception e) {
                isPictureCaptureInProgress.set(false);
                Log.e("Camera1", "takePictureInternal: take picture failed = " + e.getMessage());
            }
        }
    }

    private int calculateCaptureRotation() {
        int captureRotation;

        captureRotation = (mCameraInfo.orientation +
                mPhoneOrientation * (mFacing == Constants.FACING_FRONT ? -1 : 1) + 360) % 360;

        return captureRotation;
    }

    @Override
    public void setDisplayOrientation(int displayOrientation) {
        if (mDisplayOrientation == displayOrientation) {
            return;
        }
        mDisplayOrientation = displayOrientation;
        if (isCameraOpened()) {
            mCameraParameters.setRotation(calcCameraRotation(displayOrientation));
            setParameters();
            mCamera.setDisplayOrientation(calcDisplayOrientation(displayOrientation));
        }
    }

    @Override
    public void setPicSize(Size size) {
        mPicSize = size;
        mCameraParameters.setPictureSize(size.getWidth(), size.getHeight());
        setParameters();
    }

    @Override
    public void setHdrMode(boolean hdr) {
        mCameraParameters.setSceneMode(hdr ? Camera.Parameters.SCENE_MODE_HDR : Camera.Parameters.SCENE_MODE_AUTO);
    }

    @Override
    public String getCameraAPI() {
        return CAMERA_API_CAMERA1;
    }

    @Override
    public float getMaxZoom() {
        //为了保持和Camera2的单位一致,这里进行了转换.
        return mCameraParameters.getMaxZoom() / 10f - 1;
    }

    /**
     * @return 返回Camera1表示的最大zoom
     * 即{@link #getZoomRatios()}的最后一个元素的index.
     */
    private int getMaxZoomByCamera1() {
        return mCameraParameters.getMaxZoom();
    }

    @Override
    public List<Integer> getZoomRatios() {
        return mCameraParameters.getZoomRatios();
    }

    @Override
    public void scaleZoom(float scale) {
        if (mCamera == null) {
            return;
        }
        //确保只有1位小数
        scale = Math.round(scale * 10) / 10f;

        float maxWt = getMaxZoom();
        int maxZoom = getMaxZoomByCamera1();

        if (scale > maxWt || scale < 1) {
            return;
        }
        mWt = scale;
        int zoom = (int) (maxZoom * (scale - 1) / (maxWt - 1));
        mZoomRatio = getZoomRatios().get(zoom) / 100f;
        mCameraParameters.setZoom(zoom);
        setParameters();
    }

    @Override
    public void gestureScaleZoom(float factor) {
        if (mCameraParameters == null) {
            return;
        }

        float maxWt = getMaxZoom();
        int maxZoom = getMaxZoomByCamera1();
        int zoom = getZoom();
        int delta = (int) ((factor - 1) * 100);
        zoom += delta;
        if (zoom > maxZoom) {
            scaleZoom(maxWt);
            return;
        }
        if (zoom < 0) {
            scaleZoom(1);
            return;
        }
        mWt = (zoom * (maxWt - 1) + maxZoom) / maxZoom;
        mWt = Math.round(mWt * 100) / 100f;
        mZoomRatio = getZoomRatios().get(zoom) / 100f;
        mCameraParameters.setZoom(zoom);
        setParameters();
    }

    private int getZoom() {
        return mCameraParameters.getZoom();
    }

    @Override
    public void stopSmoothZoom() {
        if (mZoomFrameCallback != null) {
            Log.d("Camera1", "stopSmoothZoom");
            Choreographer.getInstance().removeFrameCallback(mZoomFrameCallback);
        }
    }

    @Override
    public void startSmoothZoom(final float end, final long duration) {

        // 2.0 ~ 3.0 2000ms

        stopSmoothZoom();

        if (end == mWt) {
            return;
        }

        final long delay = (long) (duration / (Math.abs(mWt - end) * 10));
        final boolean isShrink = mWt > end;
        mZoomFrameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (mCamera == null) {
                    Choreographer.getInstance().removeFrameCallback(this);
                    return;
                }
                if (isShrink) {
                    if (mWt <= end) {
                        Choreographer.getInstance().removeFrameCallback(this);
                        return;
                    }
                    mWt -= 0.1f;
                } else {
                    if (mWt >= end) {
                        Choreographer.getInstance().removeFrameCallback(this);
                        return;
                    }
                    mWt += 0.1f;
                }
                scaleZoom(mWt);
                Choreographer.getInstance().postFrameCallbackDelayed(this, delay);
            }
        };
        Choreographer.getInstance().postFrameCallback(mZoomFrameCallback);
    }

    @Override
    public void resetAF(MotionEvent event, final boolean lock) {
        synchronized (mCameraLock) {
            if (mCamera == null) {
                return;
            }
            try {
                Camera.Parameters parameters = mCamera.getParameters();
                if (parameters == null) return;

                String focusMode = parameters.getFocusMode();
                Rect rect = calculateFocusArea(event.getX(), event.getY());
                List<Camera.Area> meteringAreas = new ArrayList<>();
                meteringAreas.add(new Camera.Area(rect, getFocusMeteringAreaWeight()));
                if (parameters.getMaxNumFocusAreas() != 0 && focusMode != null &&
                        (focusMode.equals(Camera.Parameters.FOCUS_MODE_AUTO) ||
                                focusMode.equals(Camera.Parameters.FOCUS_MODE_MACRO) ||
                                focusMode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) ||
                                focusMode.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
                        ) {
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    parameters.setFocusAreas(meteringAreas);
                    if (parameters.getMaxNumMeteringAreas() > 0) {
                        parameters.setMeteringAreas(meteringAreas);
                    }
                    if (!parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                        return; //cannot autoFocus
                    }
                    mCamera.setParameters(parameters);
                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
                            if (lock) {
                                lockAEandAF();
                            } else {
                                resetToContiousFocus();
                            }
                        }
                    });
                } else if (parameters.getMaxNumMeteringAreas() > 0) {
                    if (!parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                        return; //cannot autoFocus
                    }
                    parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    parameters.setFocusAreas(meteringAreas);
                    parameters.setMeteringAreas(meteringAreas);

                    mCamera.setParameters(parameters);
                    mCamera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera camera) {
                            if (lock) {
                                lockAEandAF();
                            } else {
                                resetToContiousFocus();
                            }
                        }
                    });
                } else {
                    mCamera.autoFocus(null);
                }
                mAf = 0;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void lockAEandAF() {
        getView().removeCallbacks(mContiousFocusRunnable);
        mCameraParameters.setAutoExposureLock(true);
        mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        setParameters();
    }

    @Override
    public void unLockAEandAF() {
        mCameraParameters.setAutoExposureLock(false);
//        mCameraParameters.setExposureCompensation(0);
        setParameters();
    }

    @Override
    public void setStabilizeEnable(boolean enable) {
        mCameraParameters.setVideoStabilization(enable);
        setParameters();
    }

    @Override
    public boolean isSupportedStabilize() {
        return mCameraParameters.isVideoStabilizationSupported();
    }

    @Override
    public boolean getStabilizeEnable() {
        return mCameraParameters.getVideoStabilization();
    }

    @Override
    public boolean isSupportedManualMode() {
        return true;
    }

    @Override
    public boolean isManualControlAF() {
        return TextUtils.equals("manual", mCameraParameters.getFocusMode());
    }

    @Override
    public void setManualMode(boolean manual) {
        if (manual) {

        } else {
            mAf = 0;
            mCameraParameters.setExposureCompensation(0);
            mCameraParameters.set("iso", "auto");
            mCameraParameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            if (setAutoFocusInternal(true)) {
                setParameters();
            }
        }
    }

    @Override
    public void setIsoAuto() {
        if (mCameraParameters != null) {
            mCameraParameters.set("iso", "auto");
        }
    }

    @Override
    public boolean isManualAESupported() {
        return mCameraParameters.getMaxExposureCompensation() > 0;
    }

    @Override
    public Range<Integer> getAERange() {
        if (mCameraParameters == null) {
            return null;
        }
        return new Range<>(mCameraParameters.getMinExposureCompensation(), mCameraParameters.getMaxExposureCompensation());
    }

    @Override
    public float getAeStep() {
        return mCameraParameters.getExposureCompensationStep();
    }

    @Override
    public int getAe() {
        return mCameraParameters.getExposureCompensation();
    }

    @Override
    public void setAEValue(int value) {
        mCameraParameters.setExposureCompensation(value);
        setParameters();
    }

    @Override
    public boolean isManualSecSupported() {
        String modes = mCameraParameters.get("manual-exposure-modes");
        return !TextUtils.isEmpty(modes) && modes.contains("user-setting");
    }

    @Override
    public Range<Long> getSecRange() {
        //camera1返回的时间单位为毫秒,需要转换为纳秒,已和camera2一致.
        float min = Float.parseFloat(mCameraParameters.get("min-exposure-time"));
        float max = Float.parseFloat(mCameraParameters.get("max-exposure-time"));
        long lower = (long) (1000000 * min);
        long upper = (long) (1000000 * max);
        return new Range<>(lower, upper);
    }

    @Override
    public void setSecValue(long value) {
        mSec = value;
        DecimalFormat df = new DecimalFormat("0.000000");
        String sec;
        if (CameraUtil.isOV()) {
            //OV的手机,只需要将纳秒的sec值除了1000倍,即可达到要求.
            sec = df.format(value / 1000f);
        } else {
            sec = df.format(value / 1000000f);
        }
        mCameraParameters.set("manual-exposure-modes", "user-setting");
        mCameraParameters.set("exposure-time", sec);
    }

    @Override
    public boolean isManualISOSupported() {
        String isoValues = mCameraParameters.get("iso-values");
        return !TextUtils.isEmpty(isoValues) && isoValues.toUpperCase().contains("ISO");
    }

    @Override
    public List<Integer> getISORange() {
        String isoValues = mCameraParameters.get("iso-values");
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher(isoValues);
        List<Integer> isoList = new ArrayList<>();
        while (m.find()) {
            try {
                isoList.add(Integer.valueOf(m.group()));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return isoList;
    }

    @Override
    public int getIso() {
        String isoStr = mCameraParameters.get("iso");
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher(isoStr);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group());
            } catch (NumberFormatException e) {
                return mIso;
            }
        }
        return mIso;
    }

    @Override
    public void setISOValue(int value) {
        mIso = value;
        mCameraParameters.set("iso", "ISO" + value);
        setParameters();
    }

    @Override
    public boolean isManualWBSupported() {
        String modes = mCameraParameters.get("whitebalance-values");
        return !TextUtils.isEmpty(modes) && modes.contains("manual");
    }

    @Override
    public Range<Integer> getManualWBRange() {
        try {
            int lower = Integer.parseInt(mCameraParameters.get("min-wb-cct"));
            int uper = Integer.parseInt(mCameraParameters.get("max-wb-cct"));
            return new Range<>(lower, uper);
        } catch (NumberFormatException e) {
            return new Range<>(2000, 8000);
        }
    }

    @Override
    public void setManualWBValue(int value) {
        mManualWB = value;
        mCameraParameters.setWhiteBalance("manual");
        mCameraParameters.set("manual-wb-value", value);
        mCameraParameters.set("manual-wb-type", 0);
        setParameters();
    }

    @Override
    public boolean isManualAFSupported() {
        String modes = mCameraParameters.get("focus-mode-values");
        return !TextUtils.isEmpty(modes) && modes.contains("manual");
    }

    @Override
    public Float getAFMaxValue() {
        try {
            //比如小米6的返回值max-focus-pos-ratio == 100;
            //降低到1/10,以便和camera2的数据一致,以便不改动代码
            return Float.valueOf(mCameraParameters.get("max-focus-pos-ratio")) / 10f;
        } catch (Exception e) {
            return 10.0f;
        }
    }

    @Override
    public void setAFValue(float value) {
        mAf = value;
        mCameraParameters.setFocusMode("manual");
        mCameraParameters.set("manual-focus-pos-type", 2);
        mCameraParameters.set("manual-focus-position", (int) (10 * value));
        setParameters();
    }

    @Override
    public boolean isManualWTSupported() {
        return mCameraParameters.isZoomSupported();
    }

    /**
     * This rewrites {@link #mCameraId} and {@link #mCameraInfo}.
     */
    private void chooseCamera() {
        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
            Camera.getCameraInfo(i, mCameraInfo);
            if (mCameraInfo.facing == mFacing) {
                mCameraId = i;
                return;
            }
        }
        mCameraId = INVALID_CAMERA_ID;
    }

    private void openCamera() {
        if (mCamera != null) {
            releaseCamera();
        }
        try {
            mCamera = Camera.open(mCameraId);
        } catch (Exception e) {
            Log.e("Camera1", "openCamera: failed = " + e.getMessage());
            mCallback.onCameraClosed();
            return;
        }
        mCameraParameters = mCamera.getParameters();
        // Supported preview sizes
        mPreviewSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPreviewSizes()) {
            mPreviewSizes.add(new Size(size.width, size.height));
        }
        // Supported picture sizes;
        mPictureSizes.clear();
        for (Camera.Size size : mCameraParameters.getSupportedPictureSizes()) {
            mPictureSizes.add(new Size(size.width, size.height));
        }
        // AspectRatio
        if (mAspectRatio == null) {
            mAspectRatio = Constants.DEFAULT_ASPECT_RATIO;
        }
        adjustCameraParameters();
        mCamera.setDisplayOrientation(calcDisplayOrientation(mDisplayOrientation));
        mCallback.onCameraOpened();
        mCallback.onRequestBuilderCreate();
    }

    private AspectRatio chooseAspectRatio() {
        AspectRatio r = null;
        for (AspectRatio ratio : mPreviewSizes.ratios()) {
            r = ratio;
            if (ratio.equals(Constants.DEFAULT_ASPECT_RATIO)) {
                return ratio;
            }
        }
        return r;
    }

    private void adjustCameraParameters() {
        SortedSet<Size> sizes = mPreviewSizes.sizes(mAspectRatio);
        if (sizes == null) { // Not supported
            mAspectRatio = chooseAspectRatio();
            sizes = mPreviewSizes.sizes(mAspectRatio);
        }
        Size size = chooseOptimalSize(sizes);
        if (mShowingPreview) {
            mCamera.stopPreview();
        }
        mCameraParameters.setPreviewSize(size.getWidth(), size.getHeight());
        setAutoFocusInternal(mAutoFocus);
        setFlashInternal(mFlash);
        setParameters();
        if (mShowingPreview) {
            mCamera.startPreview();
        }
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private Size chooseOptimalSize(SortedSet<Size> sizes) {
        if (!mPreview.isReady()) { // Not yet laid out
            return sizes.first(); // Return the smallest size
        }
        int desiredWidth;
        int desiredHeight;
        final int surfaceWidth = mPreview.getWidth();
        final int surfaceHeight = mPreview.getHeight();
        if (isLandscape(mDisplayOrientation)) {
            desiredWidth = surfaceHeight;
            desiredHeight = surfaceWidth;
        } else {
            desiredWidth = surfaceWidth;
            desiredHeight = surfaceHeight;
        }
        Size result = null;
        for (Size size : sizes) { // Iterate from small to large
            if (desiredWidth <= size.getWidth() && desiredHeight <= size.getHeight()) {
                return size;

            }
            result = size;
        }
        return result;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
            mCallback.onCameraClosed();
        }
    }

    /**
     * Calculate display orientation
     * https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int)
     * <p>
     * This calculation is used for orienting the preview
     * <p>
     * Note: This is not the same calculation as the camera rotation
     *
     * @param screenOrientationDegrees Screen orientation in degrees
     * @return Number of degrees required to rotate preview
     */
    private int calcDisplayOrientation(int screenOrientationDegrees) {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - (mCameraInfo.orientation + screenOrientationDegrees) % 360) % 360;
        } else {  // back-facing
            return (mCameraInfo.orientation - screenOrientationDegrees + 360) % 360;
        }
    }

    /**
     * Calculate camera rotation
     * <p>
     * This calculation is applied to the output JPEG either via Exif Orientation tag
     * or by actually transforming the bitmap. (Determined by vendor camera API implementation)
     * <p>
     * Note: This is not the same calculation as the display orientation
     *
     * @param screenOrientationDegrees Screen orientation in degrees
     * @return Number of degrees to rotate image in order for it to view correctly.
     */
    private int calcCameraRotation(int screenOrientationDegrees) {
        if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (mCameraInfo.orientation + screenOrientationDegrees) % 360;
        } else {  // back-facing
            final int landscapeFlip = isLandscape(screenOrientationDegrees) ? 180 : 0;
            return (mCameraInfo.orientation + screenOrientationDegrees + landscapeFlip) % 360;
        }
    }

    /**
     * Test if the supplied orientation is in landscape.
     *
     * @param orientationDegrees Orientation in degrees (0,90,180,270)
     * @return True if in landscape, false if portrait
     */
    private boolean isLandscape(int orientationDegrees) {
        return (orientationDegrees == Constants.LANDSCAPE_90 ||
                orientationDegrees == Constants.LANDSCAPE_270);
    }

    /**
     * @return {@code true} if {@link #mCameraParameters} was modified.
     */
    private boolean setAutoFocusInternal(boolean autoFocus) {
        mAutoFocus = autoFocus;
        if (isCameraOpened()) {
            final List<String> modes = mCameraParameters.getSupportedFocusModes();
            if (autoFocus && modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_FIXED)) {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            } else if (modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY)) {
                mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_INFINITY);
            } else {
                mCameraParameters.setFocusMode(modes.get(0));
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return {@code true} if {@link #mCameraParameters} was modified.
     */
    private boolean setFlashInternal(int flash) {
        if (isCameraOpened()) {
            List<String> modes = mCameraParameters.getSupportedFlashModes();
            String mode = FLASH_MODES.get(flash);
            if (modes != null && modes.contains(mode)) {
                mCameraParameters.setFlashMode(mode);
                mFlash = flash;
                return true;
            }
            String currentMode = FLASH_MODES.get(mFlash);
            if (modes == null || !modes.contains(currentMode)) {
                mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mFlash = Constants.FLASH_OFF;
                return true;
            }
            return false;
        } else {
            mFlash = flash;
            return false;
        }
    }

    private Runnable mContiousFocusRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (mCameraLock) {
                if (mCamera != null) {
                    mCamera.cancelAutoFocus();
                    Camera.Parameters parameters = mCameraParameters;
                    if (parameters == null) return;

                    if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(parameters.getFlashMode())) {
                        return;
                    }

                    if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                        parameters.setFocusAreas(null);
                        parameters.setMeteringAreas(null);
                        try {
                            mCamera.setParameters(parameters);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    };

    private void resetToContiousFocus() {
        getView().postDelayed(mContiousFocusRunnable, DELAY_MILLIS_BEFORE_RESETTING_FOCUS);
    }

    private Rect calculateFocusArea(float x, float y) {
        int buffer = getFocusAreaSize() / 2;
        int centerX = calculateCenter(x, getView().getWidth(), buffer);
        int centerY = calculateCenter(y, getView().getHeight(), buffer);
        return new Rect(
                centerX - buffer,
                centerY - buffer,
                centerX + buffer,
                centerY + buffer
        );
    }


    private static int calculateCenter(float coord, int dimen, int buffer) {
        int normalized = (int) ((coord / dimen) * 2000 - 1000);
        if (Math.abs(normalized) + buffer > 1000) {
            if (normalized > 0) {
                return 1000 - buffer;
            } else {
                return -1000 + buffer;
            }
        } else {
            return normalized;
        }
    }

    private void setParameters() {
        if (mCamera == null || mCameraParameters == null) {
            return;
        }
        try {
            mCamera.setParameters(mCameraParameters);
        } catch (Exception e) {
//            e.printStackTrace();
        }
    }


}