package cn.zhengshang.cameraview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.util.SparseArrayCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.Range;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.zhengshang.base.AspectRatio;
import cn.zhengshang.base.CameraViewImpl;
import cn.zhengshang.base.Constants;
import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.base.Size;
import cn.zhengshang.base.SizeMap;
import cn.zhengshang.controller.BroadcastController;
import cn.zhengshang.controller.SoundController;
import cn.zhengshang.listener.CameraCallback;
import cn.zhengshang.listener.CameraError;
import cn.zhengshang.util.CameraUtil;

import static cn.zhengshang.base.Constants.AWB_MODE_AUTO;
import static cn.zhengshang.base.Constants.AWB_MODE_CLOUDY_DAYLIGHT;
import static cn.zhengshang.base.Constants.AWB_MODE_DAYLIGHT;
import static cn.zhengshang.base.Constants.AWB_MODE_FLUORESCENT;
import static cn.zhengshang.base.Constants.AWB_MODE_INCANDESCENT;
import static cn.zhengshang.base.Constants.AWB_MODE_OFF;
import static cn.zhengshang.base.Constants.AWB_MODE_SHADE;
import static cn.zhengshang.base.Constants.AWB_MODE_TWILIGHT;
import static cn.zhengshang.base.Constants.AWB_MODE_WARM_FLUORESCENT;
import static cn.zhengshang.base.Constants.CAMERA_API_CAMERA1;
import static cn.zhengshang.base.Constants.FOCUS_AREA_SIZE_DEFAULT;
import static cn.zhengshang.base.Constants.FOCUS_METERING_AREA_WEIGHT_DEFAULT;
import static cn.zhengshang.controller.SoundController.SOUND_ID_CLICK;


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

    private final AtomicBoolean isPictureCaptureInProgress = new AtomicBoolean(false);
    private final Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();
    private final SizeMap mPreviewSizes = new SizeMap();
    private final SizeMap mPictureSizes = new SizeMap();
    private final Object mCameraLock = new Object();
    private int mCameraId;
    private Camera mCamera;
    private Camera.Parameters mCameraParameters;
    private boolean mShowingPreview;
    private int mDisplayOrientation;
    private Choreographer.FrameCallback mZoomFrameCallback;
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

    public Camera1(Context context, CameraCallback callback, PreviewImpl preview) {
        super(context, callback, preview);

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

    @Override
    public void start() {
        chooseCamera();
        openCamera();
        if (mCamera == null) {
            mCallback.onFailed(CameraError.OPEN_FAILED);
            return;
        }
        if (mPreview.isReady()) {
            setUpPreview();
        }
        mShowingPreview = true;
        startPreview();
    }

    @Override
    public void stop() {
        if (mCameraConfig.isRecordingVideo()) {
            mCameraConfig.setRecordingVideo(false);
            mCallback.onVideoRecordStoped();
        }
        mRecorderController.release();
        if (mCamera != null) {
            mCamera.stopPreview();
        }
        mShowingPreview = false;
        releaseCamera();
    }

    @Override
    public AspectRatio getAspectRatio() {
        return mCameraConfig.getAspectRatio();
    }

    @Override
    public boolean getAutoFocus() {
        if (!isCameraOpened()) {
            return mCameraConfig.isAutoFocus();
        }
        String focusMode = mCameraParameters.getFocusMode();
        return focusMode != null && focusMode.contains("continuous");
    }

    @Override
    public void setAutoFocus(boolean autoFocus) {
        if (setAutoFocusInternal(autoFocus)) {
            setParameters();
        }
    }

    @Override
    public boolean isFlashAvailable() {
        return mCameraParameters.getSupportedFlashModes() != null;
    }

    @Override
    public int getFlash() {
        return mCameraConfig.getFlash();
    }

    @Override
    public void setFlash(int flash) {
        if (flash == mCameraConfig.getFlash()) {
            return;
        }
        if (setFlashInternal(flash)) {
            setParameters();
        }
    }

    @Override
    public boolean isTorch() {
        return mCameraParameters != null && Camera.Parameters.FLASH_MODE_TORCH.equals(mCameraParameters.getFlashMode());
    }

    @Override
    public void setTorch(boolean open) {
        if (mCameraParameters != null) {
            mCameraParameters.setFlashMode(open ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            setParameters();
        }
    }

    @Override
    public void takePicture() {
        if (!isCameraOpened()) {
            Log.e("Camera1", "takePicture: Camera is not ready. Call start() before takePicture().");
            return;
        }
        try {
            if (getAutoFocus()) {
                //某些手机,如(MX2)等,cancelAutoFocus有概率failed.所以用了try catch
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void takeBurstPictures() {

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
                meteringAreas.add(new Camera.Area(rect, FOCUS_METERING_AREA_WEIGHT_DEFAULT));
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
                mCameraConfig.getManualConfig().setAf(Constants.DEF_MANUAL_AF);
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
    public void startRecordingVideo(boolean triggerCallback) {
        if (CameraUtil.lowAvailableSpace(mContext, mPhoneOrientation)) {
            return;
        }
        synchronized (mCameraLock) {
            try {
                if (prepareMediaRecorder()) {
                    mRecorderController.startRecord();
                    if (triggerCallback) {
                        mCallback.onVideoRecordingStarted();
                    }
                    mCameraConfig.setRecordingVideo(true);
                } else {
                    Log.e("Camera1", "startRecordingVideo: prepre failed");
                    if (triggerCallback) {
                        mCallback.onVideoRecordingFailed();
                    }
                    mRecorderController.stopRecord();
                    mCamera.lock();
                }
            } catch (IOException | RuntimeException e) {
                Log.e("Camera1", "startRecordingVideo: failed = " + e.getMessage());
                if (triggerCallback) {
                    mCallback.onVideoRecordingFailed();
                }
                mRecorderController.stopRecord();
                mCamera.lock();
            }
        }
    }

    @Override
    public void stopRecordingVideo(boolean triggerCallback) {
        synchronized (mCameraLock) {
            if (mCameraConfig.isRecordingVideo()) {
                mRecorderController.stopRecord();
                try {
                    if (triggerCallback) {
                        mCallback.onVideoRecordStoped();
                    } else {
                        CameraUtil.addToMediaStore(mContext, mCameraConfig.getVideoConfig().getVideoAbsolutePath());
                    }
//                    playSound(SOUND_ID_STOP);
                } catch (RuntimeException e) {
                    Log.e("Camera1", "stopRecordingVideo: failed = " + e.getMessage());
                    if (triggerCallback) {
                        mCallback.onVideoRecordingFailed();
                    }
                }

                //如果正在进行平滑af和wt,就停止
                stopSmoothFocus();
                stopSmoothZoom();

                mCameraConfig.setRecordingVideo(false);
                mCamera.lock();
            }

            // 小米手机出现录制视频后无法拍照或录像的问题
            if ("xiaomi".equalsIgnoreCase(Build.MANUFACTURER)) {
                stop();
                start();
            } else {
                //回调此方法,主要的目的在于,在移动延时摄影时,可能调整了CaptureRate的参数.
                //所以用这个方法使CameraActivity2来进行相机参数的重新设置.
                //上面的小米手机通过重启相机也已经包含了这个回调.
                if (triggerCallback) {
                    mCallback.onRequestBuilderCreate();
                }
            }
        }
    }

    @Override
    public void setAWBMode(int mode) {
        mCameraConfig.setAwb(mode);
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
    public void stopSmoothZoom() {
        if (mZoomFrameCallback != null) {
            Log.d("Camera1", "stopSmoothZoom");
            Choreographer.getInstance().removeFrameCallback(mZoomFrameCallback);
        }
    }

    @Override
    public void startSmoothZoom(float start, final float end, final long duration) {

        // 2.0 ~ 3.0 2000ms

        stopSmoothZoom();

        final float[] wt = new float[1];
        wt[0] = start;

        if (end == wt[0]) {
            return;
        }

        final long delay = (long) (duration / (Math.abs(wt[0] - end) * 10));
        final boolean isShrink = wt[0] > end;
        mZoomFrameCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (mCamera == null) {
                    Choreographer.getInstance().removeFrameCallback(this);
                    return;
                }
                if (isShrink) {
                    if (wt[0] <= end) {
                        Choreographer.getInstance().removeFrameCallback(this);
                        return;
                    }
                    wt[0] -= 0.1f;
                } else {
                    if (wt[0] >= end) {
                        Choreographer.getInstance().removeFrameCallback(this);
                        return;
                    }
                    wt[0] += 0.1f;
                }
                scaleZoom(wt[0]);
                Choreographer.getInstance().postFrameCallbackDelayed(this, delay);
            }
        };
        Choreographer.getInstance().postFrameCallback(mZoomFrameCallback);
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
    public float getMaxZoom() {
        //为了保持和Camera2的单位一致,这里进行了转换.
        return mCameraParameters.getMaxZoom() / 10f - 1;
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
        mCameraConfig.getManualConfig().setWt(scale);
        int zoom = (int) (maxZoom * (scale - 1) / (maxWt - 1));
        mCameraConfig.setZoomRatio(getZoomRatios().get(zoom) / 100f);
        mCameraParameters.setZoom(zoom);
        setParameters();
    }

    @Override
    public String getCameraAPI() {
        return CAMERA_API_CAMERA1;
    }

    @Override
    public void setPicSize(Size size) {
        mCameraConfig.getPhotoConfig().setSize(size);
        mCameraParameters.setPictureSize(size.getWidth(), size.getHeight());
        setParameters();
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
        mCameraConfig.getManualConfig().setManual(manual);
        if (manual) {

        } else {
            mCameraConfig.getManualConfig().restore();
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
    public void setAEValue(int value) {
        if (mCameraParameters == null) {
            return;
        }
        if (mOnAeChangeListener != null) {
            mOnAeChangeListener.onAeChanged(value);
        }
        mCameraParameters.setExposureCompensation(value);
        setParameters();
    }

    @Override
    public float getAeStep() {
        return mCameraParameters.getExposureCompensationStep();
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
        mCameraConfig.getManualConfig().setSec(value);
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
    public void setISOValue(int value) {
        mCameraConfig.getManualConfig().setIso(value);
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
        mCameraConfig.getManualConfig().setWb(value);
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
        mCameraConfig.getManualConfig().setAf(value);
        mCameraParameters.setFocusMode("manual");
        mCameraParameters.set("manual-focus-pos-type", 2);
        mCameraParameters.set("manual-focus-position", (int) (10 * value));
        setParameters();
    }

    @Override
    public boolean isManualWTSupported() {
        return mCameraParameters.isZoomSupported();
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
        float wt = (zoom * (maxWt - 1) + maxZoom) / maxZoom;
        wt = Math.round(wt * 100) / 100f;
        mCameraConfig.getManualConfig().setWt(wt);
        mCameraConfig.setZoomRatio(getZoomRatios().get(zoom) / 100f);
        mCameraParameters.setZoom(zoom);
        setParameters();
    }

    @Override
    public int getAe() {
        return mCameraParameters.getExposureCompensation();
    }

    @Override
    public int getIso() {
        String isoStr = mCameraParameters.get("iso");
        // isoStr 有出现空的情况
        if (TextUtils.isEmpty(isoStr)) {
            return 0;
        }
        Pattern p = Pattern.compile("\\d+");
        Matcher m = p.matcher(isoStr);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group());
            } catch (NumberFormatException e) {
                return mCameraConfig.getManualConfig().getIso();
            }
        }
        return mCameraConfig.getManualConfig().getIso();
    }

    @Override
    public List<Integer> getZoomRatios() {
        return mCameraParameters.getZoomRatios();
    }

    @Override
    public void setHdrMode(boolean hdr) {
        List<String> supportedSceneModes = mCameraParameters.getSupportedSceneModes();
        if (supportedSceneModes != null) {
            if (hdr && supportedSceneModes.contains(Camera.Parameters.SCENE_MODE_HDR)) {
                mCameraParameters.setSceneMode(Camera.Parameters.SCENE_MODE_HDR);
            } else {
                mCameraParameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
            }
        }
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
    public void setStabilizeEnable(boolean enable) {
        mCameraParameters.setVideoStabilization(enable);
        setParameters();
    }

    // Suppresses Camera#setPreviewTexture
    @SuppressLint("NewApi")
    private void setUpPreview() {
        try {
            if (mPreview.getOutputClass() == SurfaceHolder.class) {
                mCamera.setPreviewDisplay(mPreview.getSurfaceHolder());
            } else {
                mCamera.setPreviewTexture(mPreview.getSurfaceTexture());
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
        if (mCameraConfig.getFacing() == facing) {
            return;
        }
        mCameraConfig.setFacing(facing);
        if (isCameraOpened()) {
            stop();
            start();
        }
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
    public SortedSet<Size> getSupportedPicSizes() {
        return mPictureSizes.sizes(mCameraConfig.getAspectRatio());
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
    public SortedSet<Size> getSupportedVideoSize() {
//        List<Camera.Size> supportedVideoSizes = mCameraParameters.getSupportedVideoSizes();
//        if (supportedVideoSizes == null) {
//            return new ArrayList<>();
//        }
//        List<android.util.Size> size = new ArrayList<>();
//        for (Camera.Size videoSize : supportedVideoSizes) {
//            android.util.Size size1 = new android.util.Size(videoSize.width, videoSize.height);
//            //去重.
//            //vivo x21A手机会出现2次4k的选项.
//            if (!size.contains(size1)) {
//                size.add(size1);
//            }
//        }
//
//        // 使用白名单的方式添加4K支持
//        // 需要等市场反馈支持良好后开放
//        // android.util.Size size4k = new android.util.Size(3840, 2160);
//        // if (Whitelist4k.isWhitelist() && !size.contains(size4k)) {
//        //     size.add(size4k);
//        // }
//
//        //排序
//        Collections.sort(size, new Comparator<android.util.Size>() {
//            @Override
//            public int compare(android.util.Size o1, android.util.Size o2) {
//                return (o1.getWidth() + o1.getHeight()) - (o2.getWidth() + o2.getHeight());
//            }
//        });
//        return size;
        return null;
    }

    @Override
    public int[] getSupportAWBModes() {
        List<String> whiteBalance = mCameraParameters.getSupportedWhiteBalance();
        if (whiteBalance == null) {
            return new int[0];
        }
        List<Integer> modes = new ArrayList<>();
        for (String s : whiteBalance) {
            if (TextUtils.equals(s, Camera.Parameters.WHITE_BALANCE_AUTO)) {
                modes.add(Constants.AWB_MODE_AUTO);
            } else if (TextUtils.equals(s, Camera.Parameters.WHITE_BALANCE_INCANDESCENT)) {
                modes.add(Constants.AWB_MODE_INCANDESCENT);
            } else if (TextUtils.equals(s, Camera.Parameters.WHITE_BALANCE_FLUORESCENT)) {
                modes.add(Constants.AWB_MODE_FLUORESCENT);
            } else if (TextUtils.equals(s, Camera.Parameters.WHITE_BALANCE_DAYLIGHT)) {
                modes.add(Constants.AWB_MODE_DAYLIGHT);
            } else if (TextUtils.equals(s, Camera.Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT)) {
                modes.add(Constants.AWB_MODE_CLOUDY_DAYLIGHT);
            }
        }
        int[] array = new int[modes.size()];
        for (int i = 0; i < modes.size(); i++) array[i] = modes.get(i);
        return array;
    }

    @Override
    public boolean setAspectRatio(AspectRatio ratio) {
        if (mCameraConfig.getAspectRatio() == null || !isCameraOpened()) {
            // Handle this later when camera is opened
            mCameraConfig.setAspectRatio(ratio);
            return true;
        } else if (!mCameraConfig.getAspectRatio().equals(ratio)) {
            final Set<Size> sizes = mPreviewSizes.sizes(ratio);
            if (sizes == null) {
                throw new UnsupportedOperationException(ratio + " is not supported");
            } else {
                mCameraConfig.setAspectRatio(ratio);
                adjustCameraParameters();
                return true;
            }
        }
        return false;
    }

    private void saveVideosInSp() {
        if (mCamera == null) {
            return;
        }

        boolean allSaved = false;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);

        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
            if (sp.contains(String.valueOf(i))) {
                allSaved = true;
                break;
            }
        }

        if (allSaved) {
            //Already put in sp.
            return;
        }

        SharedPreferences.Editor edit = sp.edit();

        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
            mCamera.release();
            mCamera = Camera.open(i);
            List<Camera.Size> cameraSizes = mCamera.getParameters().getSupportedVideoSizes();
            if (cameraSizes == null) {
                continue;
            }
            Type listType = new TypeToken<List<android.util.Size>>() {
            }.getType();
            List<android.util.Size> utilSizes = new ArrayList<>();
            for (Camera.Size size : cameraSizes) {
                utilSizes.add(new android.util.Size(size.width, size.height));
            }
            String key = String.valueOf(i);
            String value = new Gson().toJson(utilSizes, listType);
            edit.putString(key, value).apply();
        }
        openCamera();
    }

    private boolean prepareMediaRecorder() throws IOException {
        if (mCamera == null) {
            return false;
        }
        synchronized (mCameraLock) {
            mCameraConfig.setOrientation(calculateCaptureRotation());
            mRecorderController.configForCamera1(mCameraConfig, mCamera);
            return true;
        }
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
                            BroadcastController.sendTakePhotoAction(mContext);
                        }
                        SoundController.getInstance().playSound(SOUND_ID_CLICK);
                        try {
                            camera.cancelAutoFocus();
                            camera.startPreview();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
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
                mPhoneOrientation * (mCameraConfig.getFacing() == Constants.FACING_FRONT ? -1 : 1) + 360) % 360;

        return captureRotation;
    }

    /**
     * @return 返回Camera1表示的最大zoom
     * 即{@link #getZoomRatios()}的最后一个元素的index.
     */
    private int getMaxZoomByCamera1() {
        return mCameraParameters.getMaxZoom();
    }

    private int getZoom() {
        return mCameraParameters.getZoom();
    }

    /**
     * This rewrites {@link #mCameraId} and {@link #mCameraInfo}.
     */
    private void chooseCamera() {
        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
            Camera.getCameraInfo(i, mCameraInfo);
            if (mCameraInfo.facing == mCameraConfig.getFacing()) {
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
            mCameraParameters = mCamera.getParameters();
        } catch (Exception e) {
            Log.e("Camera1", "openCamera: failed = " + e.getMessage());
            mCallback.onFailed(CameraError.OPEN_FAILED);
            return;
        }
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
        if (mCameraConfig.getAspectRatio() == null) {
            mCameraConfig.setAspectRatio(Constants.DEFAULT_ASPECT_RATIO);
        }
        adjustCameraParameters();
        mCamera.setDisplayOrientation(calcDisplayOrientation(mDisplayOrientation));
        mCallback.onCameraOpened();
        mCallback.onRequestBuilderCreate();

        //保存摄像头的视频分辨率信息
        saveVideosInSp();
        mAvailableSpace = CameraUtil.getAvailableSpace();
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
        SortedSet<Size> sizes = mPreviewSizes.sizes(mCameraConfig.getAspectRatio());
        if (sizes == null) { // Not supported
            mCameraConfig.setAspectRatio(chooseAspectRatio());
            sizes = mPreviewSizes.sizes(mCameraConfig.getAspectRatio());
        }
        Size size = chooseOptimalSize(sizes);
        if (mShowingPreview) {
            mCamera.stopPreview();
        }
        mCameraParameters.setPreviewSize(size.getWidth(), size.getHeight());
        setAutoFocusInternal(mCameraConfig.isAutoFocus());
        setFlashInternal(mCameraConfig.getFlash());
        setParameters();
        if (mShowingPreview) {
            startPreview();
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
        mCameraConfig.setAutoFocus(autoFocus);
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
                mCameraConfig.setFlash(flash);
                return true;
            }
            String currentMode = FLASH_MODES.get(mCameraConfig.getFlash());
            if (modes == null || !modes.contains(currentMode)) {
                mCameraParameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                mCameraConfig.setFlash(Constants.FLASH_OFF);
                return true;
            }
            return false;
        } else {
            mCameraConfig.setFlash(flash);
            return false;
        }
    }

    private void resetToContiousFocus() {
        getView().postDelayed(mContiousFocusRunnable, DELAY_MILLIS_BEFORE_RESETTING_FOCUS);
    }

    private Rect calculateFocusArea(float x, float y) {
        int buffer = FOCUS_AREA_SIZE_DEFAULT / 2;
        int centerX = calculateCenter(x, getView().getWidth(), buffer);
        int centerY = calculateCenter(y, getView().getHeight(), buffer);
        return new Rect(
                centerX - buffer,
                centerY - buffer,
                centerX + buffer,
                centerY + buffer
        );
    }

    private void startPreview() {
        try {
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
            mCallback.onFailed(CameraError.OPEN_FAILED);
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