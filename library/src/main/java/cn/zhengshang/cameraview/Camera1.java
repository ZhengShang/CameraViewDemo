package cn.zhengshang.cameraview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.util.Range;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import androidx.collection.SparseArrayCompat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.zhengshang.base.AspectRatio;
import cn.zhengshang.base.CameraViewImpl;
import cn.zhengshang.base.Constants;
import cn.zhengshang.base.Face;
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

    private final Object mCameraLock = new Object();

    private final AtomicBoolean isPictureCaptureInProgress = new AtomicBoolean(false);
    private int mCameraId;
    private Camera mCamera;

    private final Camera.CameraInfo mCameraInfo = new Camera.CameraInfo();

    private final SizeMap mPreviewSizes = new SizeMap();

    private final SizeMap mPictureSizes = new SizeMap();

    private boolean mShowingPreview;

    private int mDisplayOrientation;
    private Camera.Parameters mCameraParameters;
    private int videoOrientation;
    private Matrix mMatrix = new Matrix();
    private boolean mBurst;

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

        preview.setCallback(() -> {
            if (mCamera != null) {
                setUpPreview();
                adjustCameraParameters();
            }
        });
    }

    @Override
    public void stop() {
        if (mCameraConfig.isRecordingVideo()) {
            mCameraConfig.setRecordingVideo(false);
            mCallback.onVideoRecordStoped();
        }
        if (mCamera != null) {
            mCamera.stopPreview();
        }
        mShowingPreview = false;
        releaseCamera();
        super.stop();
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
        super.start();
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
        prepareImageReader();
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

    @Override
    public AspectRatio getAspectRatio() {
        return mCameraConfig.getAspectRatio();
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
            return mCameraConfig.isAutoFocus();
        }
        if (mCameraParameters == null) {
            return false;
        }
        String focusMode = mCameraParameters.getFocusMode();
        return focusMode != null && focusMode.contains("continuous");
    }

    @Override
    public boolean isSupportedStabilize() {
        if (mCameraParameters == null) {
            return false;
        }
        return mCameraParameters.isVideoStabilizationSupported();
    }

    @Override
    public boolean getStabilizeEnable() {
        if (mCameraParameters == null) {
            return false;
        }
        return mCameraParameters.getVideoStabilization();
    }

    @Override
    public void setStabilizeEnable(boolean enable) {
        if (mCameraParameters == null) {
            return;
        }
        mCameraParameters.setVideoStabilization(enable);
        setParameters();
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
    public void setTorch(boolean open) {
        if (mCameraParameters != null) {
            mCameraParameters.setFlashMode(open ? Camera.Parameters.FLASH_MODE_TORCH : Camera.Parameters.FLASH_MODE_OFF);
            setParameters();
        }
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
    public boolean isTorch() {
        return mCameraParameters != null && Camera.Parameters.FLASH_MODE_TORCH.equals(mCameraParameters.getFlashMode());
    }

    @Override
    public void lockAEandAF() {
        if (mCameraParameters == null) {
            return;
        }
        getView().removeCallbacks(mContiousFocusRunnable);
        mCameraParameters.setAutoExposureLock(true);
        mCameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        setParameters();
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
    public void unLockAEandAF() {
        if (mCameraParameters == null) {
            return;
        }
        mCameraParameters.setAutoExposureLock(false);
//        mCameraParameters.setExposureCompensation(0);
        setParameters();
    }

    @Override
    public void takeBurstPictures() {
        mBurst = true;
        mBackgroundHandler.post(new Runnable() {
            Matrix matrix = new Matrix();

            @Override
            public void run() {
                Size size = mCameraConfig.getPhotoConfig().getSize();
                while (mBurst) {
                    Bitmap frameBitmap = mPreview.getFrameBitmap(size.getWidth(), size.getHeight());

                    matrix.reset();
                    matrix.postRotate(calculateCaptureRotation());
                    Bitmap rotatedBitmap = Bitmap.createBitmap(frameBitmap, 0, 0, frameBitmap.getWidth(), frameBitmap.getHeight(), matrix, true);

                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                    byte[] byteArray = stream.toByteArray();
                    frameBitmap.recycle();
                    mCallback.onPictureTaken(byteArray);
                    BroadcastController.sendTakePhotoAction(mContext);
                    SoundController.getInstance().playSound(SOUND_ID_CLICK);
                }
            }
        });
    }

    @Override
    public void setAWBMode(int mode) {
        if (mCameraParameters == null) {
            return;
        }
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
        }
        setParameters();
    }

    @Override
    public void stopBurstPicture() {
        mBurst = false;
    }

    @Override
    public void startRecordingVideo(boolean triggerCallback) {
        if (!isCameraOpened()) {
            return;
        }
        if (CameraUtil.lowAvailableSpace(mContext, mPhoneOrientation)) {
            return;
        }
        //开始录制
        int sensorOrientation = getSensorOrientation();
        videoOrientation = caleVideoOrientation(sensorOrientation);
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
        if (!isCameraOpened()) {
            return;
        }
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
    public void setDisplayOrientation(int displayOrientation) {
        if (mDisplayOrientation == displayOrientation) {
            return;
        }
        if (mCameraParameters == null) {
            return;
        }
        mDisplayOrientation = displayOrientation;
        if (isCameraOpened()) {
            mCameraParameters.setRotation(calcCameraRotation(displayOrientation));
            setParameters();
            try {
                mCamera.setDisplayOrientation(calcDisplayOrientation(displayOrientation));
            } catch (Exception ignored) {

            }
        }
    }

    @Override
    public float getMaxZoom() {
        if (mCameraParameters == null) {
            return 1;
        }
        //为了保持和Camera2的单位一致,这里进行了转换.
        return getZoomRatios().get(mCameraParameters.getMaxZoom()) / 100f;
    }

    @Override
    public void setPicSize(Size size) {
        if (mCameraParameters == null) {
            return;
        }
        mCameraConfig.getPhotoConfig().setSize(size);
        mCameraParameters.setPictureSize(size.getWidth(), size.getHeight());
        setParameters();
    }

    public Size getPreViewSize() {
        if (mCamera == null) {
            return null;
        }
        Camera.Size previewSize = mCamera.getParameters().getPreviewSize();
        return new Size(previewSize.width, previewSize.height);
    }

    @Override
    public boolean isManualControlAF() {
        if (mCameraParameters == null) {
            return false;
        }
        return TextUtils.equals("manual", mCameraParameters.getFocusMode());
    }

    @Override
    public void setManualMode(boolean manual) {
        if (mCameraParameters == null) {
            return;
        }
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
    public boolean isManualAESupported() {
        if (mCameraParameters == null) {
            return false;
        }
        return mCameraParameters.getMaxExposureCompensation() > 0;
    }

    @Override
    public void setAEValue(int value) {
        if (mCameraParameters == null) {
            return;
        }
        mCameraConfig.getManualConfig().setAe(value);
        if (mOnAeChangeListener != null) {
            mOnAeChangeListener.onAeChanged(value);
        }
        mCameraParameters.setExposureCompensation(value);
        setParameters();
    }

    @Override
    public boolean isSupportedHdr() {
        if (mCameraParameters == null) {
            return false;
        }
        if ("Mi Note 3".equals(Build.MODEL)) {
            return false;
        }
        List<String> supportedSceneModes = mCameraParameters.getSupportedSceneModes();
        return supportedSceneModes != null && supportedSceneModes.contains(Camera.Parameters.SCENE_MODE_HDR);
    }

    @Override
    public float getAeStep() {
        if (mCameraParameters == null) {
            return 0;
        }
        return mCameraParameters.getExposureCompensationStep();
    }

    @Override
    public void setHdrMode(boolean hdr) {
        if (mCameraParameters == null) {
            return;
        }
        if (!isSupportedHdr()) {
            return;
        }
        super.setHdrMode(hdr);
        if (hdr) {
            mCameraParameters.setSceneMode(Camera.Parameters.SCENE_MODE_HDR);
        } else {
            mCameraParameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
        }
    }

    @Override
    public boolean isManualSecSupported() {
        if (mCameraParameters == null) {
            return false;
        }
        String modes = mCameraParameters.get("manual-exposure-modes");
        return !TextUtils.isEmpty(modes) && modes.contains("user-setting");
    }

    @Override
    public String getCameraAPI() {
        return CAMERA_API_CAMERA1;
    }

    @Override
    public Range<Long> getSecRange() {
        if (mCameraParameters == null) {
            return new Range<>(0L, 0L);
        }
        //camera1返回的时间单位为毫秒,需要转换为纳秒,已和camera2一致.
        float min = Float.parseFloat(mCameraParameters.get("min-exposure-time"));
        float max = Float.parseFloat(mCameraParameters.get("max-exposure-time"));
        long lower = (long) (1000000 * min);
        long upper = (long) (1000000 * max);
        return new Range<>(lower, upper);
    }

    @Override
    public void setSecValue(long value) {
        if (mCameraParameters == null) {
            return;
        }
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
        if (mCameraParameters == null) {
            return false;
        }
        String isoValues = mCameraParameters.get("iso-values");
        return !TextUtils.isEmpty(isoValues) && isoValues.toUpperCase().contains("ISO");
    }

    @Override
    public List<Integer> getZoomRatios() {
        if (mCameraParameters == null) {
            return new ArrayList<>();
        }
        return mCameraParameters.getZoomRatios();
    }

    @Override
    public List<Integer> getISORange() {
        if (mCameraParameters == null) {
            return new ArrayList<>();
        }
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
    public void scaleZoom(float scale) {
        if (mCamera == null) {
            return;
        }
        if (mCameraParameters == null) {
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
    public void setISOValue(int value) {
        if (mCameraParameters == null) {
            return;
        }
        mCameraConfig.getManualConfig().setIso(value);
        mCameraParameters.set("iso", "ISO" + value);
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
        float wt = (zoom * (maxWt - 1) + maxZoom) / maxZoom;
        wt = Math.round(wt * 100) / 100f;
        mCameraConfig.getManualConfig().setWt(wt);
        mCameraConfig.setZoomRatio(getZoomRatios().get(zoom) / 100f);
        mCameraParameters.setZoom(zoom);
        setParameters();
    }

    @Override
    public boolean isManualWBSupported() {
        if (mCameraParameters == null) {
            return false;
        }
        String modes = mCameraParameters.get("whitebalance-values");
        return !TextUtils.isEmpty(modes) && modes.contains("manual");
    }

    @Override
    public Range<Integer> getManualWBRange() {
        if (mCameraParameters == null) {
            return new Range<>(0, 0);
        }
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
        if (mCameraParameters == null) {
            return;
        }
        mCameraConfig.getManualConfig().setWb(value);
        mCameraParameters.setWhiteBalance("manual");
        mCameraParameters.set("manual-wb-value", value);
        mCameraParameters.set("manual-wb-type", 0);
        setParameters();
    }

    @Override
    public boolean isManualAFSupported() {
        if (mCameraParameters == null) {
            return false;
        }
        String modes = mCameraParameters.get("focus-mode-values");
        return !TextUtils.isEmpty(modes) && modes.contains("manual");
    }

    @Override
    public Float getAFMaxValue() {
        if (mCameraParameters == null) {
            return 0f;
        }
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
        if (mCameraParameters == null) {
            return;
        }
        mCameraConfig.getManualConfig().setAf(value);
        mCameraParameters.setFocusMode("manual");
        mCameraParameters.set("manual-focus-pos-type", 2);
        mCameraParameters.set("manual-focus-position", (int) (10 * value));
        setParameters();
    }

    @Override
    public boolean isManualWTSupported() {
        if (mCameraParameters == null) {
            return false;
        }
        return mCameraParameters.isZoomSupported();
    }

    @Override
    public boolean isSupportedManualMode() {
        return true;
    }

    @Override
    public boolean isSupportFaceDetect() {
        if (mCameraParameters == null) {
            return false;
        }
        return mCameraParameters.getMaxNumDetectedFaces() > 0;
    }

    @Override
    public void setFaceDetect(boolean open) {
        mCameraConfig.setFaceDetect(open);
        try {
            if (open) {
                mCamera.startFaceDetection();
                calculateCameraToPreviewMatrix();
                mCamera.setFaceDetectionListener(new Camera.FaceDetectionListener() {

                    ArrayList<Face> mRectList = new ArrayList<>();
                    private RectF face_rect = new RectF();

                    @Override
                    public void onFaceDetection(Camera.Face[] faces, Camera camera) {
                        mRectList.clear();
                        for (Camera.Face face : faces) {
                            if (face.score > 50) {
                                Rect rect = face.rect;
                                calculateCameraToPreviewMatrix();
                                face_rect.set(rect);
                                mMatrix.mapRect(face_rect);
                                face_rect.round(rect);
                                mRectList.add(Face.valueOf(face.id, face.rect));
                            }
                        }
                        if (mOnFaceDetectListener != null) {
                            mOnFaceDetectListener.onFaceDetect(mRectList);
                        }
                    }
                });
            } else {
                mCamera.stopFaceDetection();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setIsoAuto() {
        if (mCameraParameters != null) {
            mCameraParameters.set("iso", "auto");
        }
    }

    @Override
    public SortedSet<Size> getSupportedVideoSize() {
        if (mCameraParameters == null) {
            return new TreeSet<>();
        }
        List<Camera.Size> supportedVideoSizes = mCameraParameters.getSupportedVideoSizes();
        if (supportedVideoSizes == null) {
            return new TreeSet<>();
        }
        SortedSet<Size> sizeSortedSet = new TreeSet<>();
        for (Camera.Size videoSize : supportedVideoSizes) {
            Size size = new Size(videoSize.width, videoSize.height);
            sizeSortedSet.add(size);
        }

        // 使用白名单的方式添加4K支持
        // 需要等市场反馈支持良好后开放
        // android.util.Size size4k = new android.util.Size(3840, 2160);
        // if (Whitelist4k.isWhitelist() && !size.contains(size4k)) {
        //     size.add(size4k);
        // }

        return sizeSortedSet;
    }

    @Override
    public Range<Integer> getAERange() {
        if (mCameraParameters == null) {
            return null;
        }
        return new Range<>(mCameraParameters.getMinExposureCompensation(), mCameraParameters.getMaxExposureCompensation());
    }

    @Override
    public int[] getSupportAWBModes() {
        if (mCameraParameters == null) {
            return new int[0];
        }
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
    public boolean isFlashAvailable() {
        if (mCameraParameters == null) {
            return false;
        }
        return mCameraParameters.getSupportedFlashModes() != null;
    }

    @Override
    public int getAe() {
        if (mCameraParameters == null) {
            return 0;
        }
        return mCameraParameters.getExposureCompensation();
    }

    private boolean prepareMediaRecorder() throws IOException {
        if (mCamera == null) {
            return false;
        }
        synchronized (mCameraLock) {
            mCameraConfig.setOrientation(videoOrientation);
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
                Log.e("Camera1", "takePictureInternal: take picture failed. ", e);
                mCaptureController.capturePreview(mPreview.getFrameBitmap(
                        mCameraConfig.getPhotoConfig().getSize().getWidth(),
                        mCameraConfig.getPhotoConfig().getSize().getHeight()
                ));
                isPictureCaptureInProgress.set(false);
            }
        } else {
            isPictureCaptureInProgress.set(false);
        }
    }

    private int calculateCaptureRotation() {
        int captureRotation;

        captureRotation = (mCameraInfo.orientation +
                mPhoneOrientation * (mCameraConfig.getFacing() == Constants.FACING_FRONT ? -1 : 1) + 360) % 360;

        return captureRotation;
    }

    private int getSensorOrientation() {
        int sensorOrientation = 0;
        if (mCameraConfig.getFacing() != Constants.FACING_FRONT) {
            sensorOrientation = 90;
        } else {
            sensorOrientation = 270;
        }
        return sensorOrientation;
    }

    /**
     * @return 返回Camera1表示的最大zoom
     * 即{@link #getZoomRatios()}的最后一个元素的index.
     */
    private int getMaxZoomByCamera1() {
        if (mCameraParameters == null) {
            return 1;
        }
        return mCameraParameters.getMaxZoom();
    }

    private int getZoom() {
        if (mCameraParameters == null) {
            return 1;
        }
        return mCameraParameters.getZoom();
    }

    /**
     * This rewrites {@link #mCameraId} and {@link #mCameraInfo}.
     */
    private void chooseCamera() {
        for (int i = 0, count = Camera.getNumberOfCameras(); i < count; i++) {
            try {
                Camera.getCameraInfo(i, mCameraInfo);
            } catch (Exception e) {
                break;
            }
            if (mCameraInfo.facing == mCameraConfig.getFacing()) {
                mCameraId = i;
                return;
            }
        }
        mCameraId = INVALID_CAMERA_ID;
    }

    @Override
    public int getIso() {
        if (mCameraParameters == null) {
            return 0;
        }
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

    private void openCamera() {
        if (mCamera != null) {
            releaseCamera();
        }
        try {
            synchronized (mCameraLock) {
                mCamera = Camera.open(mCameraId);
                mCameraParameters = mCamera.getParameters();
            }
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
        addManualParams();
    }

    /**
     * 大部分情况应用于小米手机录制视频后.
     * 在手动模式下, 需要重新设置这些参数, 否则会造成手动参数的显示值与相机的表现不一致.
     */
    private void addManualParams() {
        if (mCameraConfig.getManualConfig().isManual()) {
            if (mCameraConfig.getManualConfig().getWb() != Constants.DEF_MANUAL_WB) {
                setManualWBValue(mCameraConfig.getManualConfig().getWb());
            }
            if (mCameraConfig.getManualConfig().getIso() != 0) {
                setISOValue(mCameraConfig.getManualConfig().getIso());
            }
            if (mCameraConfig.getManualConfig().getSec() != 0) {
                setSecValue(mCameraConfig.getManualConfig().getSec());
            }
        }
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
        mPreview.setBufferSize(size.getWidth(), size.getHeight());
        mCameraParameters.setPreviewSize(size.getWidth(), size.getHeight());
        setAutoFocusInternal(mCameraConfig.isAutoFocus());
        setFlashInternal(mCameraConfig.getFlash());
        setAEValue(mCameraConfig.getManualConfig().getAe());
        setParameters();
        if (mShowingPreview) {
            try {
                mCamera.startPreview();
            } catch (Exception e) {
                e.printStackTrace();
                startPreview();
            }
        }
    }

    private void releaseCamera() {
        synchronized (mCameraLock) {
            if (mCamera != null) {
                try {
                    mCamera.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                mCamera = null;
                mCallback.onCameraClosed();
            }
        }
    }

    /**
     * @return {@code true} if {@link #mCameraParameters} was modified.
     */
    private boolean setAutoFocusInternal(boolean autoFocus) {
        if (mCameraParameters == null) {
            return false;
        }
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
        if (mCameraParameters == null) {
            return false;
        }
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


    private void calculateCameraToPreviewMatrix() {
        mMatrix.reset();
        // from http://developer.android.com/reference/android/hardware/Camera.Face.html#rect
        // Need mirror for front camera
        boolean mirror = getFacing() == CameraView.FACING_FRONT;
        mMatrix.setScale(mirror ? -1 : 1, 1);
        mMatrix.postRotate(0);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        mMatrix.postScale(mPreview.getWidth() / 2000f, mPreview.getHeight() / 2000f);
        mMatrix.postTranslate(mPreview.getWidth() / 2f, mPreview.getHeight() / 2f);
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


    private Rect calculateFocusArea(float x, float y) {
        int buffer = FOCUS_METERING_AREA_WEIGHT_DEFAULT / 2;
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
//            mPreview.onPreview();
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
            Log.w("Camera1", "setParameters failed.  ", e);
        }
    }


}