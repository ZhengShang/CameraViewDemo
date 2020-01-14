package cn.zhengshang.cameraview;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import android.util.Range;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import cn.zhengshang.base.AspectRatio;
import cn.zhengshang.base.CameraViewImpl;
import cn.zhengshang.base.Constants;
import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.base.Size;
import cn.zhengshang.base.SizeMap;
import cn.zhengshang.config.CameraConfig;
import cn.zhengshang.controller.BroadcastController;
import cn.zhengshang.controller.SoundController;
import cn.zhengshang.listener.CameraCallback;
import cn.zhengshang.listener.CameraError;
import cn.zhengshang.listener.OnCaptureImageCallback;
import cn.zhengshang.listener.PictureCaptureCallback;
import cn.zhengshang.util.CameraUtil;

import static cn.zhengshang.base.Constants.CAMERA_API_CAMERA2;
import static cn.zhengshang.base.Constants.FOCUS_METERING_AREA_WEIGHT_DEFAULT;
import static cn.zhengshang.base.Constants.MANUAL_WB_LOWER;
import static cn.zhengshang.base.Constants.MANUAL_WB_UPPER;
import static cn.zhengshang.controller.SoundController.SOUND_ID_START;
import static cn.zhengshang.controller.SoundController.SOUND_ID_STOP;
import static cn.zhengshang.listener.PictureCaptureCallback.STATE_PREVIEW;

@TargetApi(21)
public class Camera2 extends CameraViewImpl {

    private static final String TAG = "Camera2";

    private static final SparseIntArray INTERNAL_FACINGS = new SparseIntArray();

    static {
        INTERNAL_FACINGS.put(Constants.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK);
        INTERNAL_FACINGS.put(Constants.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT);
    }

    /**
     * Mapping from Surface.Rotation_n to degrees.
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private final CameraManager mCameraManager;

    private String mCameraId;
    private final SizeMap mPreviewSizes = new SizeMap();

    private StreamConfigurationMap mMap;
    private final SizeMap mPictureSizes = new SizeMap();
    private final Object mLock = new Object();

    private CameraCaptureSession mCaptureSession;
    protected CameraCharacteristics mCameraCharacteristics;
    CameraDevice mCamera;
    CaptureRequest.Builder mRequestBuilder;

    private SizeMap mVideoSizes = new SizeMap();
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private Size previewSize;
    private RectF face_rect = new RectF();
    private ArrayList<cn.zhengshang.base.Face> mFacesList = new ArrayList<>();
    private Matrix mMatrix = new Matrix();
    private PictureCaptureCallback mCaptureCallback = new PictureCaptureCallback() {

        @Override
        public void onReady() {
//            captureStillPicture();
//            Lock af
            mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            updatePreview();
        }

        @Override
        public void onPrecaptureRequired() {
            if (mCaptureSession == null) {
                return;
            }
            //准备拍摄静止图像：开始准备自动曝光 告诉相机开始曝光控制长期测试
            mRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            setState(STATE_PRECAPTURE);
            try {
                mCaptureSession.capture(mRequestBuilder.build(), this, mBackgroundHandler);
            } catch (Exception e) {
                Log.e(TAG, "Failed to run precapture sequence.", e);
            }
        }

        @Override
        public void onResultCallback(CaptureResult result) {
            //实时回调当前数据

            synchronized (mLock) {
                if (mOnManualValueListener != null) {
                    try {
                        int iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
                        mOnManualValueListener.onIsoChanged(iso);
                        long sec = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);//曝光时间
                        mOnManualValueListener.onSecChanged(sec);
                    } catch (Exception e) {
                    }

                    RggbChannelVector rggbChannelVector = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
                    if (rggbChannelVector != null) {
                        int temperature = CameraUtil.rgbToKelvin(rggbChannelVector);
                        mOnManualValueListener.onTemperatureChanged(temperature);
                    }
                }
            }

            if (mOnFaceDetectListener == null) {
                return;
            }
            if (!mCameraConfig.isFaceDetect()) {
                return;
            }
            Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
            mFacesList.clear();
            if (faces != null && faces.length > 0) {
                for (Face face : faces) {
                    if (face.getScore() > Face.SCORE_MAX / 2) {

                        Rect rect = CameraUtil.convertRectFromCamera2(getViewableRect(), face.getBounds());
                        face_rect.set(rect);
                        mMatrix.mapRect(face_rect);
                        face_rect.round(rect);

                        mFacesList.add(cn.zhengshang.base.Face.valueOf(face.getId(), rect));
                    }
                }
            }
            if (mOnFaceDetectListener != null) {
                mOnFaceDetectListener.onFaceDetect(mFacesList);
            }
        }
    };
    private final CameraCaptureSession.StateCallback mSessionCallback
            = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            if (mCamera == null) {
                Log.e(TAG, "start preview onConfigured failed");
                mCallback.onFailed(CameraError.CONFIGURATION_FAILED);
                return;
            }
            mCaptureSession = session;
            updateAutoFocus();
            updateFlash(mRequestBuilder);
            updatePreview();

            mCallback.onRequestBuilderCreate();

            mCameraOpenCloseLock.release();

            if (mRestartForRecord) {
                startRecordingVideo(true);
                mRestartForRecord = false;
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "Failed to configure capture session.");
            mCallback.onFailed(CameraError.CONFIGURATION_FAILED);
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "camera session closed ");
            if (mCaptureSession != null && mCaptureSession.equals(session)) {
                mCaptureSession = null;
            }
        }

    };
    private Runnable mDelayFocusTask = new Runnable() {
        @Override
        public void run() {
            if (mCameraConfig.isRecordingVideo()) {
                return;
            }
            setContinuousFocus(mRequestBuilder);
            updatePreview();
        }
    };

    Camera2(Context context, CameraCallback callback, PreviewImpl preview) {
        super(context, callback, preview);

        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mPreview.setCallback(this::startCaptureSession);
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
            mCameraConfig.setAutoFocus(true);
        }
    }

    @Override
    public String getCameraId() {
        return mCameraId;
    }


    @Override
    public Set<AspectRatio> getSupportedAspectRatios() {
        return mPreviewSizes.ratios();
    }

    @Override
    public SortedSet<Size> getSupportedPicSizes() {
        return mPreviewSizes.sizes(mCameraConfig.getAspectRatio());
    }

    @Override
    public SortedSet<Size> getSupportedVideoSize() {
        return mVideoSizes.sizes(mCameraConfig.getAspectRatio());
    }

    @Override
    public void start() {
        super.start();
        if (!chooseCameraIdByFacing()) {
            mCallback.onFailed(CameraError.OPEN_FAILED);
            return;
        }
        collectCameraInfo();
        prepareImageReader();
        startOpeningCamera();
    }

    @Override
    public void stop() {
        if (mCameraConfig.isRecordingVideo()) {
            mCameraConfig.setRecordingVideo(false);
            mCallback.onVideoRecordStoped();
        }
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (mCaptureController != null) {
                mCaptureController.release();
            }
            if (mCamera != null) {
                mCamera.close();
                mCamera = null;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "stop: camera2 failed .Interrupted while trying to lock camera closing. ", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
        super.stop();
    }

    @Override
    public float getFpsWithSize(android.util.Size size) {
        long outputMinFrameDuration = mMap.getOutputMinFrameDuration(MediaRecorder.class, size);
        return 1 / (outputMinFrameDuration / 1000000000f);
    }

    @Override
    public boolean setAspectRatio(AspectRatio ratio) {
        if (ratio == null || ratio.equals(mCameraConfig.getAspectRatio()) ||
                !mPreviewSizes.ratios().contains(ratio)) {
            return false;
        }
        mCameraConfig.setAspectRatio(ratio);
        prepareImageReader();
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
            startCaptureSession();
        }
        return true;
    }


    @Override
    public void setAutoFocus(boolean autoFocus) {
        if (mCameraConfig.isAutoFocus() == autoFocus) {
            return;
        }
        mCameraConfig.setAutoFocus(autoFocus);
        if (mRequestBuilder != null) {
            updateAutoFocus();
            if (mCaptureSession != null) {
                try {
                    mCaptureSession.setRepeatingRequest(mRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
                } catch (Exception e) {
                    mCameraConfig.setAutoFocus(!mCameraConfig.isAutoFocus());// Revert
                }
            }
        }
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    public boolean isFlashAvailable() {
        Boolean isFlashAvailable = mCameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        return isFlashAvailable != null && isFlashAvailable == true;
    }

    @Override
    public int[] getSupportAWBModes() {
        if (mRequestBuilder == null) {
            return new int[0];
        }
        return mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
    }

    @Override
    public boolean isSupportedManualMode() {
        return isManualAESupported() ||
                isManualSecSupported() ||
                isManualISOSupported() ||
                isManualWBSupported() ||
                isManualAFSupported() ||
                isManualWTSupported();

    }

    @Override
    public void setFlash(int flash) {
        if (mCameraConfig.getFlash() == flash) {
            return;
        }
        mCameraConfig.setFlash(flash);
        if (mRequestBuilder != null) {
            updateFlash(mRequestBuilder);
            updatePreview();
        }
    }

    @Override
    public void setIsoAuto() {
        if (mRequestBuilder != null) {
            mRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        }
    }

    @Override
    public void resetAF(MotionEvent event, final boolean lock) {
        if (mCamera != null && mCaptureSession != null) {

            if (!isMeteringAreaAFSupported()) {
                return;
            }

            mCameraConfig.setAeLock(lock);

            Rect rect = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if (rect == null) return;
            int areaSize = FOCUS_METERING_AREA_WEIGHT_DEFAULT;
            int right = rect.right;
            int bottom = rect.bottom;
            int viewWidth = mPreview.getWidth();
            int viewHeight = mPreview.getHeight();
            int ll, rr;
            Rect newRect;
            int centerX = (int) event.getX();
            int centerY = (int) event.getY();
            ll = ((centerX * right) - areaSize) / viewWidth;
            rr = ((centerY * bottom) - areaSize) / viewHeight;
            int focusLeft = CameraUtil.clamp(ll, 0, right);
            int focusBottom = CameraUtil.clamp(rr, 0, bottom);
            newRect = new Rect(focusLeft, focusBottom, focusLeft + areaSize,
                    focusBottom + areaSize);
            MeteringRectangle meteringRectangle = new MeteringRectangle(newRect,
                    FOCUS_METERING_AREA_WEIGHT_DEFAULT);
            MeteringRectangle[] meteringRectangleArr = {meteringRectangle};

            try {

                //需要先把对焦模式从CONTROL_AE_PRECAPTURE_TRIGGER_START切换到CONTROL_AF_MODE_AUTO
                //且设置Trigger设置为null,此时,如下的重新设置对焦才可以完全正常显示
                mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
                mRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                updatePreview();
                //end

                //Now add a new AF trigger with focus region
                mRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRectangleArr);
                mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                mRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                if (!mCameraConfig.getManualConfig().isManual()) {
                    mRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, meteringRectangleArr);
//                    mRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                }
                mRequestBuilder.setTag("FOCUS_TAG"); //we'll capture this later for resuming the preview

                //then we ask for a single request (not repeating!)
                mCaptureSession.capture(mRequestBuilder.build(),
                        new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                                if (request.getTag() == "FOCUS_TAG") {
                                    if (lock) {
                                        lockAEandAF();
                                    } else {
                                        //the focus trigger is complete -
                                        //resume repeating (preview surface will get frames), restore AF trigger
                                        mRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                                        updatePreview();
                                        if (!mCameraConfig.isRecordingVideo()) {
                                            delayToContinuousFocus();
                                        }
                                    }
                                    mCameraConfig.getManualConfig().setAf(Constants.DEF_MANUAL_AF);
                                }
                            }
                        }, mBackgroundHandler);

            } catch (Exception e) {
                Log.e(TAG, "resetAF failed ", e);
            }
        }
    }

    @Override
    public void setTorch(boolean open) {
        if (mRequestBuilder != null) {
            mRequestBuilder.set(CaptureRequest.FLASH_MODE, open ? CameraMetadata.FLASH_MODE_TORCH : CameraMetadata.FLASH_MODE_OFF);
            updatePreview();
        }
    }

    @Override
    public void lockAEandAF() {
        mRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
        mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
        updatePreview();
    }

    @Override
    public boolean isTorch() {
        //noinspection ConstantConditions
        return mRequestBuilder != null && mRequestBuilder.get(CaptureRequest.FLASH_MODE) == CameraMetadata.FLASH_MODE_TORCH;
    }

    @Override
    public void unLockAEandAF() {
        if (mRequestBuilder == null) {
            return;
        }
        mRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
        updatePreview();
    }

    @Override
    public void startRecordingVideo(final boolean triggerCallback) {
        if (mBackgroundHandler == null) {
            return;
        }
        mBackgroundHandler.post(() -> {
            if (CameraUtil.lowAvailableSpace(mContext, mPhoneOrientation)) {
                return;
            }
            int ori = caleVideoOrientation(mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_ORIENTATION));
            if (mCameraConfig.getOrientation() != ori) {
                mRestartForRecord = true;
                mCameraConfig.setOrientation(ori);
                startCaptureSession();
                return;
            }
            try {
                if (triggerCallback) {
                    SoundController.getInstance().playSound(SOUND_ID_START);
                }
                synchronized (mLock) {
                    mRecorderController.startRecord();
                    mCameraConfig.setRecordingVideo(true);
                }
                if (triggerCallback) {
                    mCallback.onVideoRecordingStarted();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (triggerCallback) {
                    mCallback.onVideoRecordingFailed();
                }
            }
        });
    }

    @Override
    public void stopRecordingVideo(boolean triggerCallback) {

        //如果正在进行平滑af和wt,就停止
        stopSmoothFocus();
        stopSmoothZoom();

        try {
            synchronized (mLock) {
                mCameraConfig.setRecordingVideo(false);
                mRecorderController.stopRecord();
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (triggerCallback) {
                mCallback.onVideoRecordingFailed();
            }
        }
        if (triggerCallback) {
            SoundController.getInstance().playSound(SOUND_ID_STOP);
            BroadcastController.sendRecordingStopAction(mContext);
            mCallback.onVideoRecordStoped();
        } else {
            CameraUtil.addToMediaStore(mContext, mCameraConfig.getVideoConfig().getVideoAbsolutePath());
        }
        startCaptureSession();
    }

    @Override
    public boolean isSupportedHdr() {
        return true;
    }

    @Override
    public boolean isManualAESupported() {
        Range<Integer> aeRanges = mCameraCharacteristics.get(
                CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
        return aeRanges != null && !aeRanges.getLower().equals(aeRanges.getUpper());
    }

    @Override
    public Range<Integer> getAERange() {
        return mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
    }

    @Override
    public void setAWBMode(int mode) {
        if (mRequestBuilder == null) {
            return;
        }
        mCameraConfig.setAwb(mode);
        mRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, mode);
        updatePreview();
    }

    @Override
    public void setAEValue(int value) {
        if (mRequestBuilder == null) {
            return;
        }
        mCameraConfig.getManualConfig().setAe(value);
        if (mOnAeChangeListener != null) {
            mOnAeChangeListener.onAeChanged(value);
        }
//        mRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        mRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, value);
        updatePreview();
    }

    @Override
    public boolean isManualSecSupported() {
        Range<Long> range = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        return range != null && !range.getLower().equals(range.getUpper());
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public Range<Long> getSecRange() {
        Range<Long> range = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        if (range.getLower() < Constants.LOWEST_SEC) {
            return new Range<>(Constants.LOWEST_SEC, range.getUpper());
        }
        return range;
    }

    @Override
    public void setSecValue(long value) {
        if (mRequestBuilder == null) {
            return;
        }
        mCameraConfig.getManualConfig().setSec(value);
        mRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
        mRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, value);
        updatePreview();
    }

    @Override
    public boolean isManualISOSupported() {
        Range<Integer> range = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
        return range != null && !range.getLower().equals(range.getUpper());
    }

    @Override
    public Range<Integer> getISORange() {
        return mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
    }

    @Override
    public void setISOValue(int value) {
        if (mRequestBuilder == null) {
            return;
        }
        mCameraConfig.getManualConfig().setIso(value);
        mRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
        mRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, value);
        updatePreview();
    }

    @Override
    public boolean isManualWBSupported() {
//        int[] modes = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
//        return Arrays.binarySearch(modes, CameraMetadata.CONTROL_AWB_MODE_OFF) != -1;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL == getCameraLevel()
                    || CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 == getCameraLevel();
        } else {
            return CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL == getCameraLevel();
        }
    }

    @Override
    public Range<Integer> getManualWBRange() {
        return new Range<>(MANUAL_WB_LOWER, MANUAL_WB_UPPER);
    }

    @Override
    public void setManualWBValue(int value) {
        if (mRequestBuilder == null) {
            return;
        }
        mCameraConfig.getManualConfig().setWb(value);

        RggbChannelVector rggbChannelVector = CameraUtil.colorTemperature(value);
        mRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
        mRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
        mRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector);
        updatePreview();
    }

    @Override
    public void setDisplayOrientation(int displayOrientation) {
        mPreview.setDisplayOrientation(displayOrientation);
    }

    @Override
    public Float getAFMaxValue() {
        return mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
    }

    @Override
    public void setAFValue(float value) {
        if (mRequestBuilder == null) {
            return;
        }
        mCameraConfig.getManualConfig().setAf(value);
        mCameraConfig.setAutoFocus(false);
        mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
        mRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, value);
        updatePreview();
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public float getMaxZoom() {
        if (mCameraCharacteristics == null) {
            return 1;
        }
        return mCameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    }

    @Override
    public void setPicSize(Size picSize) {
        mCameraConfig.getPhotoConfig().setSize(picSize);

        //重新设置ImageReader,以使用新的Size创建
        prepareImageReader();
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
            startCaptureSession();
        }
    }

    @Override
    public Size getPreViewSize() {
        return previewSize;
    }

    @Override
    public boolean isManualControlAF() {
        try {
            Integer afMode = mRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE);
            return afMode != null && afMode == CameraMetadata.CONTROL_AF_MODE_OFF;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void setManualMode(boolean manual) {
        mCameraConfig.getManualConfig().setManual(manual);
        if (mRequestBuilder == null) {
            return;
        }
        if (manual) {
//            mRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
        } else {
            mCameraConfig.setAutoFocus(true);
            mCameraConfig.getManualConfig().restore();
            mRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            mRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            mRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, mCameraConfig.getAwb());
            mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            updatePreview();
        }
    }

    @Override
    public boolean isManualWTSupported() {
        return getMaxZoom() > 1;
    }

    @Override
    public float getAeStep() {
        //noinspection ConstantConditions
        return mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue();
    }

    @Override
    public void takePicture() {
        /*
        直接拍照.拍照时不需要lockFocus对焦了,因为OPPO R9S依然有小概率会无法对焦.
        在极大部分情况下,相机会自动对焦到目标物体上,点击拍照时也确实不需要再检测对焦状态了
         */
        if (mCameraConfig.getPhotoConfig().isHdr()
                && !mCameraConfig.isRecordingVideo()
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            captureHdrPicture();
        } else {
            captureStillPicture();
        }
    }

    @Override
    public boolean isManualAFSupported() {
        Float aFloat = mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        if (aFloat == null || aFloat <= 0) {
            return false;
        }
        int[] afModes = mCameraCharacteristics.get(
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (afModes != null) {
            Arrays.sort(afModes);
            return Arrays.binarySearch(afModes, CameraMetadata.CONTROL_AF_MODE_OFF) >= 0;
        } else {
            return false;
        }
    }

    @Override
    public void takeBurstPictures() {
        if (mCamera == null) {
            return;
        }
        mBackgroundHandler.post(() -> {
            mCameraConfig.getPhotoConfig().setBurst(true);
            mCaptureController.captureCamera2Burst(mRequestBuilder, mCaptureSession);
        });
    }

    @Override
    public boolean isSupportFaceDetect() {
        if (mCameraCharacteristics == null) {
            return false;
        }
        int[] ints = mCameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        for (int anInt : ints) {
            if (anInt == CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL
                    || anInt == CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void stopBurstPicture() {
        mCameraConfig.getPhotoConfig().setBurst(false);
        mRequestBuilder.removeTarget(mCaptureController.getImageReader().getSurface());
        mRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW);
        updatePreview();
    }

    @Override
    public void setFaceDetect(boolean open) {
        if (mRequestBuilder == null) {
            return;
        }
        mCameraConfig.setFaceDetect(open);
        int mode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE;
        int[] ints = mCameraCharacteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
        for (int anInt : ints) {
            if (anInt == CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL) {
                mode = CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL;
                break;
            }
        }
        mRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, open
                ? mode
                : CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF);
        updatePreview();

        if (open) {
            calculateCameraToPreviewMatrix();
        }
    }

    private Rect getViewableRect() {
        if (mRequestBuilder != null) {
            Rect crop_rect = mRequestBuilder.get(CaptureRequest.SCALER_CROP_REGION);
            if (crop_rect != null) {
                return crop_rect;
            }
        }
        Rect sensor_rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        sensor_rect.right -= sensor_rect.left;
        sensor_rect.left = 0;
        sensor_rect.bottom -= sensor_rect.top;
        sensor_rect.top = 0;
        return sensor_rect;
    }

    private void calculateCameraToPreviewMatrix() {
        mMatrix.reset();
        // Unfortunately the transformation for Android L API isn't documented, but this seems to work for Nexus 6.
        // This is the equivalent code for android.hardware.Camera.setDisplayOrientation, but we don't actually use setDisplayOrientation()
        // for CameraController2, so instead this is the equivalent code to https://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation(int),
        // except testing on Nexus 6 shows that we shouldn't change "result" for front facing camera.
        boolean mirror = getFacing() == CameraView.FACING_FRONT;
        mMatrix.setScale(1, mirror ? -1 : 1);
        int result = mirror ? 180 : 0;
        mMatrix.postRotate(result);
        // Camera driver coordinates range from (-1000, -1000) to (1000, 1000).
        // UI coordinates range from (0, 0) to (width, height).
        mMatrix.postScale(mPreview.getWidth() / 2000f, mPreview.getHeight() / 2000f);
        mMatrix.postTranslate(mPreview.getWidth() / 2f, mPreview.getHeight() / 2f);
    }

    protected void configMediaRecorder() throws Exception {
        mRecorderController.configForCamera2();
    }

    /**
     * 设置连续自动对焦
     * 跟第一次打开相机的时候一样
     */
    private void setContinuousFocus(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    protected void closePreviewSession() {
        synchronized (mLock) {

            if (mRecorderController != null) {
                mRecorderController.stopRecord();
            }
            if (mCaptureSession != null) {
                try {
                    mCaptureSession.abortCaptures();
                    mCaptureSession.stopRepeating();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (mRecorderController != null) {
                mRecorderController.stopRecord();
            }
            CameraUtil.deleteEmptyFIle(getVideoOutputFilePath());
        }
    }

    /**
     * <p>Chooses a camera ID by the specified camera facing ({@link CameraConfig#getFacing()}).</p>
     * <p>This rewrites {@link #mCameraId}, {@link #mCameraCharacteristics}, and optionally
     * {@link CameraConfig#getFacing()}.</p>
     */
    private boolean chooseCameraIdByFacing() {
        try {
            int internalFacing = INTERNAL_FACINGS.get(mCameraConfig.getFacing());
            final String[] ids = mCameraManager.getCameraIdList();
            if (ids.length == 0) { // No camera
                Log.e(TAG, "chooseCameraIdByFacing: No camera available.");
                return false;
            }
            for (String id : ids) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);

                if (BuildConfig.DEBUG) {
                    int[] ints = characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES);
                    Log.d(TAG, "camera " + id + ", face id = " + Arrays.toString(ints));
                }

                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                if ((level == null)) {
                    return false;
                }
                Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (internal == null) {
                    Log.e(TAG, "chooseCameraIdByFacing: Unexpected state: LENS_FACING null");
                    return false;
                }
                if (internal == internalFacing) {
                    mCameraId = id;
                    mCameraCharacteristics = characteristics;
                    return true;
                }
            }
            // Not found
            mCameraId = ids[0];
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            Integer level = mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            if (level == null) {
                return false;
            }
            Integer internal = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (internal == null) {
                Log.e(TAG, "chooseCameraIdByFacing: Unexpected state: LENS_FACING null");
                return false;
            }
            for (int i = 0, count = INTERNAL_FACINGS.size(); i < count; i++) {
                if (INTERNAL_FACINGS.valueAt(i) == internal) {
                    mCameraConfig.setFacing(INTERNAL_FACINGS.keyAt(i));
                    return true;
                }
            }
            // The operation can reach here when the only camera device is an external one.
            // We treat it as facing back.
            mCameraConfig.setFacing(Constants.FACING_BACK);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get a list of camera devices", e);
            return false;
        }
    }

    /**
     * <p>Collects some information from {@link #mCameraCharacteristics}.</p>
     * <p>This rewrites {@link #mPreviewSizes}, {@link #mPictureSizes}, and optionally,
     * {@link CameraConfig#getAspectRatio()}}.</p>
     */
    private void collectCameraInfo() {
        mMap = mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (mMap == null) {
            mCallback.onFailed(CameraError.CONFIGURATION_FAILED);
            Log.e(TAG, "Failed to get configuration map: " + mCameraId);
            return;
        }
        mPreviewSizes.clear();
        for (android.util.Size size : mMap.getOutputSizes(mPreview.getOutputClass())) {
            int width = size.getWidth();
            int height = size.getHeight();
            if (width <= MAX_PREVIEW_WIDTH && height <= MAX_PREVIEW_HEIGHT) {
                mPreviewSizes.add(new Size(width, height));
            }
        }
        mPictureSizes.clear();
        collectPictureSizes(mPictureSizes, mMap);

        mVideoSizes.clear();
        collectVideoSizes(mVideoSizes, mMap);

        int[] ois = mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
        boolean picStab = ois != null && Arrays.binarySearch(ois, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) >= 0;
        mCameraConfig.getPhotoConfig().setStabilization(picStab);

        int[] vsm = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
        boolean videoStab = vsm != null && Arrays.binarySearch(vsm, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) >= 0;
        mCameraConfig.getVideoConfig().setStabilization(videoStab);
    }

    protected void collectPictureSizes(SizeMap sizes, StreamConfigurationMap map) {
        for (android.util.Size size : map.getOutputSizes(ImageFormat.JPEG)) {
            mPictureSizes.add(new Size(size.getWidth(), size.getHeight()));
        }
    }

    protected void collectVideoSizes(SizeMap sizes, StreamConfigurationMap map) {
        for (android.util.Size size : map.getOutputSizes(MediaRecorder.class)) {
            mVideoSizes.add(new Size(size.getWidth(), size.getHeight()));
        }
    }

    @SuppressWarnings("ConstantConditions")
    private int getCameraLevel() {
        return mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    }

    /**
     * <p>Starts opening a camera device.</p>
     */
    private void startOpeningCamera() {
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                mCallback.onFailed(CameraError.OPEN_FAILED);
                return;
            }
            mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    mCamera = camera;
                    mCallback.onCameraOpened();
                    startCaptureSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    mCallback.onCameraClosed();

                }

                @Override
                public void onClosed(@NonNull CameraDevice camera) {
                    mCamera = null;
                    mCallback.onCameraClosed();
                    mCameraOpenCloseLock.release();
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "onError: " + camera.getId() + " (" + error + ")");
                    mCamera = null;
                    mCallback.onFailed(CameraError.OPEN_FAILED);
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "startOpeningCamera: [Failed to open camera] ", e);
        } catch (InterruptedException e) {
            Log.e(TAG, "startOpeningCamera: failed . ", e);
            mCallback.onFailed(CameraError.OPEN_FAILED);
        }
    }

    @Override
    public void addOnCaptureImageCallback(OnCaptureImageCallback onCaptureImageCallback) {
        mCaptureController.setOnCaptureImageCallback(onCaptureImageCallback);
    }

    /**
     * <p>Starts a capture session for camera preview.</p>
     * <p>This rewrites {@link #mRequestBuilder}.</p>
     * <p>The result will be continuously processed in {@link # mSessionCallback}.</p>
     */
    protected void startCaptureSession() {
        closePreviewSession();

        if (mCaptureController == null) {
            return;
        }
        ImageReader imageReader = mCaptureController.getImageReader();
        if (!isCameraOpened() || !mPreview.isReady() || imageReader == null) {
            return;
        }
        SortedSet<Size> aspectSizes = mPreviewSizes.sizes(mCameraConfig.getAspectRatio());
        previewSize = CameraUtil.chooseOptimalSize(mPreview, aspectSizes);
        mPreview.setBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(mPreview.getSurfaceTexture());
        try {
            synchronized (mLock) {
                configMediaRecorder();
                List<Surface> surfaceList = new ArrayList<>();
                mRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_PREVIEW);
                mRequestBuilder.addTarget(previewSurface);
                mRequestBuilder.addTarget(mRecorderController.getSurface());

                surfaceList.add(previewSurface);
                surfaceList.add(imageReader.getSurface());
                surfaceList.add(mRecorderController.getSurface());

                mCamera.createCaptureSession(surfaceList, mSessionCallback, mBackgroundHandler);
            }
        } catch (Exception e) {
            Log.e(TAG, "startCaptureSession failed", e);
            mCallback.onFailed(CameraError.OPEN_FAILED);
        }
    }

    /**
     * Updates the internal state of flash to {@link CameraConfig#getFlash()}.
     */
    private void updateFlash(CaptureRequest.Builder builder) {
        switch (mCameraConfig.getFlash()) {
            case Constants.FLASH_OFF:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_ON:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_TORCH:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);
                break;
            case Constants.FLASH_AUTO:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH);
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_RED_EYE:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
                break;
        }
    }

    /**
     * Locks the focus
     */
    private void lockFocus() {
        if (mCaptureSession == null) {
            return;
        }
        mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        mRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            mCaptureCallback.setState(PictureCaptureCallback.STATE_LOCKING);
            mCaptureSession.capture(mRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (Exception e) {
            Log.e(TAG, "Failed to lock focus.", e);
        }
    }

    /**
     * Updates the internal state of auto-focus to {@link CameraConfig#isAutoFocus()}.
     */
    private void updateAutoFocus() {
        if (mCameraConfig.isAutoFocus()) {
            int[] modes = mCameraCharacteristics.get(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            // Auto focus is not supported
            if (modes == null || modes.length == 0 ||
                    (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
                mCameraConfig.setAutoFocus(false);
                mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            } else {
                mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }
        } else {
            mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            //在非自动对焦下,第一次直接让其对焦完成,并锁定.
            //主要用于使用跟踪时,需要锁定画面.
            //ps,如果不手动锁定focus,而是调用setAutoFocus(false),则某些手机(三星等)画面会变得模糊.
            //故此需要次方法.
            lockFocus();
        }
    }

    /**
     * Captures a still picture.
     */
    private void captureStillPicture() {
        if (!isCameraOpened()) {
            return;
        }

        mBackgroundHandler.post(() -> {
            @SuppressWarnings("ConstantConditions")
            int ori = caleVideoOrientation(mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));
            mCameraConfig.setOrientation(ori);
            mCaptureController.captureStillPicture(mCamera,
                    mCaptureSession,
                    mPreview.getSurface(),
                    mRequestBuilder,
                    this::updatePreview);
        });
    }

    private boolean isMeteringAreaAFSupported() {
        Integer afRegions = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        return afRegions != null && afRegions >= 1;
    }

    private void captureHdrPicture() {
        mBackgroundHandler.post(() -> {
            @SuppressWarnings("ConstantConditions") int ori = caleVideoOrientation(mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_ORIENTATION));
            mCameraConfig.setOrientation(ori);

            mCaptureController.captureHdrPicture(mCamera, mCaptureSession);
            updatePreview();
        });
    }

    protected void updatePreview() {
        try {
            if (mCaptureSession != null) {
                mCaptureSession.setRepeatingRequest(mRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            }
        } catch (Exception e) {
            Log.w(TAG, "updatePreview: failed = ", e);
            mCallback.onFailed(CameraError.PARAM_SETTING_FAILED);
        }
    }

    /**
     * 延迟3s后设置为自动对焦模式, 主要用于在非录像模式下,手动点击对焦后调用
     */
    void delayToContinuousFocus() {
        getView().removeCallbacks(mDelayFocusTask);
        getView().postDelayed(mDelayFocusTask, 3000);
    }

    @Override
    public String getCameraAPI() {
        return CAMERA_API_CAMERA2;
    }


    @Override
    public void scaleZoom(float scale) {
        if (mRequestBuilder == null) {
            return;
        }

        Rect rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (rect == null) {
            return;
        }

        mCameraConfig.setRect(CameraUtil.getZoomRect(scale, rect.width(), rect.height()));

        //新的范围比最大值还大,不可以设置,否则手机直接重启.
        if (rect.contains(mCameraConfig.getRect())) {
            mRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, mCameraConfig.getRect());
            updatePreview();
        }

        //某些情况下,比如1+0.1f的结果可能是1.1000000476837158,
        //这将导致最大的scale会出现类似8.000000476837158这样的值,
        //最终导致crop之后的范围越界,引发异常.
        //所以这里进行只保留二位数的转换.
        scale = Math.round(scale * 100) / 100f;

        mCameraConfig.getManualConfig().setWt(scale);
        mCameraConfig.setZoomRatio(scale);
    }


    /**
     * Unlocks the auto-focus and restart camera preview. This is supposed to be called after
     * capturing a still picture.
     */
    private void unlockFocus() {
        if (!mCameraConfig.getManualConfig().isManual()) {
            setUpCaptureRequestBuilder(mRequestBuilder);
            setContinuousFocus(mRequestBuilder);
        }
        updatePreview();
        mCaptureCallback.setState(STATE_PREVIEW);
    }


}
