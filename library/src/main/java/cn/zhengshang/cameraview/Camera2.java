package cn.zhengshang.cameraview;

import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Range;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;

import com.google.gson.Gson;

import java.util.Arrays;
import java.util.Set;
import java.util.SortedSet;

import cn.zhengshang.base.AspectRatio;
import cn.zhengshang.base.CameraViewImpl;
import cn.zhengshang.base.Constants;
import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.base.Size;
import cn.zhengshang.base.SizeMap;
import cn.zhengshang.config.CameraConfig;
import cn.zhengshang.controller.SoundController;
import cn.zhengshang.controller.VideoController;
import cn.zhengshang.listener.CameraCallback;
import cn.zhengshang.listener.CameraError;
import cn.zhengshang.listener.OnCaptureImageCallback;
import cn.zhengshang.listener.PictureCaptureCallback;
import cn.zhengshang.util.CameraUtil;

import static cn.zhengshang.base.Constants.CAMERA_API_CAMERA2;
import static cn.zhengshang.base.Constants.FOCUS_AREA_SIZE_DEFAULT;
import static cn.zhengshang.base.Constants.FOCUS_METERING_AREA_WEIGHT_DEFAULT;
import static cn.zhengshang.base.Constants.MANUAL_WB_LOWER;
import static cn.zhengshang.base.Constants.MANUAL_WB_UPPER;
import static cn.zhengshang.base.Constants.MAX_PREVIEW_HEIGHT;
import static cn.zhengshang.base.Constants.MAX_PREVIEW_WIDTH;
import static cn.zhengshang.controller.SoundController.SOUND_ID_START;
import static cn.zhengshang.listener.PictureCaptureCallback.STATE_PREVIEW;

@TargetApi(21)
public class Camera2 extends CameraViewImpl {

    private static final String TAG = "Camera2";

    private static final SparseIntArray INTERNAL_FACINGS = new SparseIntArray();

    static {
        INTERNAL_FACINGS.put(Constants.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK);
        INTERNAL_FACINGS.put(Constants.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private final CameraManager mCameraManager;

    private final SizeMap mPreviewSizes = new SizeMap();
    private final SizeMap mPictureSizes = new SizeMap();
    CameraDevice mCamera;
    CaptureRequest.Builder mRequestBuilder;
    private String mCameraId;
    private CameraCharacteristics mCameraCharacteristics;
    private StreamConfigurationMap mMap;
    private CameraCaptureSession mCaptureSession;
    private SizeMap mVideoSizes = new SizeMap();

    private RectEvaluator mRectEvaluator;
    private ValueAnimator mSmoothZoomAnim;
    private VideoController mVideoController;

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
            mRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            setState(STATE_PRECAPTURE);
            try {
                mCaptureSession.capture(mRequestBuilder.build(), this, mBackgroundHandler);
            } catch (Exception e) {
                Log.e(TAG, "Failed to run precapture sequence.", e);
            }
        }

        @Override
        public synchronized void onResultCallback(CaptureResult result) {
            //实时回调当前数据

            Integer iso = result.get(CaptureResult.SENSOR_SENSITIVITY);
            if (iso != null) {
                if (mOnManualValueListener != null) {
                    mOnManualValueListener.onIsoChanged(iso);
                }
            }

            Long sec = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
            if (sec != null) {
                if (mOnManualValueListener != null) {
                    mOnManualValueListener.onSecChanged(sec);
                }
            }

            RggbChannelVector rggbChannelVector = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
            if (rggbChannelVector != null) {
                if (mOnManualValueListener != null) {
                    int temperature = CameraUtil.rgbToKelvin(rggbChannelVector);
                    try {
                        mOnManualValueListener.onTemperatureChanged(temperature);
                    } catch (Exception e) {
                        //加了判空不知为何还是有NPE
                    }
                }
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
            mCallback.onRequestBuilderCreate();
            setPreviewParamsToBuilder(mRequestBuilder);
            updatePreview();

            //保存摄像头的视频分辨率信息
            saveVideosInSp();
            mAvailableSpace = CameraUtil.getAvailableSpace();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "Failed to configure capture session.");
            mCallback.onFailed(CameraError.CONFIGURATION_FAILED);
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
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

    public Camera2(Context context, CameraCallback callback, PreviewImpl preview) {
        super(context, callback, preview);

        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mPreview.setCallback(new PreviewImpl.Callback() {
            @Override
            public void onSurfaceChanged() {
                startCaptureSession();
            }
        });
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
    public boolean isSupported60Fps() {
        Range<Integer>[] fpsRanges = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (fpsRanges == null || fpsRanges.length <= 0) {
            return false;
        } else {
            for (Range<Integer> fpsRange : fpsRanges) {
                if (fpsRange.getUpper() >= 60) {
                    return true;
                }
            }
            return false;
        }
    }

    @Override
    public SortedSet<Size> getSupportedVideoSize() {
        return mVideoSizes.sizes(mCameraConfig.getAspectRatio());
    }

    @Override
    public int[] getSupportAWBModes() {
        return mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
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
    public void setFlash(int flash) {
        if (mCameraConfig.getFlash() == flash) {
            return;
        }
        int saved = mCameraConfig.getFlash();
        mCameraConfig.setFlash(flash);
        if (mRequestBuilder != null) {
            updateFlash(mRequestBuilder);
            if (mCaptureSession != null) {
                try {
                    mCaptureSession.setRepeatingRequest(mRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
                } catch (CameraAccessException | IllegalStateException e) {
                    mCameraConfig.setFlash(saved); // Revert
                }
            }
        }
    }

    @Override
    public boolean isTorch() {
        //noinspection ConstantConditions
        return mRequestBuilder != null && mRequestBuilder.get(CaptureRequest.FLASH_MODE) == CameraMetadata.FLASH_MODE_TORCH;
    }

    @Override
    public void setTorch(boolean open) {
        if (mRequestBuilder != null) {
            mRequestBuilder.set(CaptureRequest.FLASH_MODE, open ? CameraMetadata.FLASH_MODE_TORCH : CameraMetadata.FLASH_MODE_OFF);
            updatePreview();
        }
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
    public void resetAF(MotionEvent event, final boolean lock) {
        if (mCamera != null && mCaptureSession != null) {

            if (!isMeteringAreaAFSupported()) {
                return;
            }

            mCameraConfig.setAeLock(lock);

            Rect rect = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if (rect == null) return;
            int areaSize = FOCUS_AREA_SIZE_DEFAULT;
            int right = rect.right;
            int bottom = rect.bottom;
            int viewWidth = mPreview.getView().getWidth();
            int viewHeight = mPreview.getView().getHeight();
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
                Log.e("Camera2", "resetAF failed ", e);
            }
        }
    }

    @Override
    public void lockAEandAF() {
        mRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
        mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
        updatePreview();
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
        if (triggerCallback) {
            SoundController.getInstance().playSound(SOUND_ID_START);
        }
        mRecorderController.startRecord();
        mCameraConfig.setRecordingVideo(true);
        mCallback.onVideoRecordingStarted();
    }

    @Override
    public void stopRecordingVideo(boolean triggerCallback) {

        //如果正在进行平滑af和wt,就停止
        stopSmoothFocus();
        stopSmoothZoom();

        try {
            mVideoController.stopRecording(triggerCallback);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            startCaptureSession();
        }
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
    public void stopSmoothZoom() {
        if (mSmoothZoomAnim != null) {
            mSmoothZoomAnim.cancel();
        }
    }

    @Override
    public void startSmoothZoom(final float start, final float end, long duration) {
        stopSmoothZoom();
        if (mRequestBuilder == null) {
            return;
        }
        if (end == start) {
            return;
        }
        final Rect maxRect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (maxRect == null) {
            return;
        }
        final Rect startRect = CameraUtil.getScaledRect(maxRect, start);
        final Rect destinationRect = CameraUtil.getScaledRect(maxRect, end);

        if (mCameraConfig.getRect() == null) {
            mCameraConfig.setRect(CameraUtil.getScaledRect(maxRect, 1.01f));
        }

        if (mRectEvaluator == null) {
            mRectEvaluator = new RectEvaluator(mCameraConfig.getRect());
        }
        if (mSmoothZoomAnim == null) {
            mSmoothZoomAnim = ValueAnimator.ofFloat(0.01f, 1f);
            mSmoothZoomAnim.setInterpolator(null);
        }

        mSmoothZoomAnim.removeAllUpdateListeners();
        mSmoothZoomAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                mCameraConfig.setRect(mRectEvaluator.evaluate(value, startRect, destinationRect));
                //新的范围比最大值还大,不可以设置,否则手机直接重启.
                if (maxRect.contains(mCameraConfig.getRect())) {
                    mRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, mCameraConfig.getRect());
                    updatePreview();
                    float wt;
                    wt = start + value * (end - start);
                    wt = Math.round(wt * 100) / 100f;
                    mCameraConfig.setZoomRatio(wt);
                }
            }
        });
        mSmoothZoomAnim.setDuration(duration);
        mSmoothZoomAnim.start();
    }

    @Override
    public void setDisplayOrientation(int displayOrientation) {
        mPreview.setDisplayOrientation(displayOrientation);
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

    @Override
    public String getCameraAPI() {
        return CAMERA_API_CAMERA2;
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
    public boolean isSupportedManualMode() {
        return isManualAESupported() ||
                isManualSecSupported() ||
                isManualISOSupported() ||
                isManualWBSupported() ||
                isManualAFSupported() ||
                isManualWTSupported();

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
            mRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
            mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
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
    public float getAeStep() {
        //noinspection ConstantConditions
        return mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue();
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
    public boolean isManualAFSupported() {
        if (mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) == null) {
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

    @Override
    public boolean isManualWTSupported() {
        return getMaxZoom() > 1;
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
        closePreviewSession();
        mRecorderController.release();
        mCaptureController.release();
        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }
        super.stop();
    }

    @Override
    public float getFpsWithSize(android.util.Size size) {
        long outputMinFrameDuration = mMap.getOutputMinFrameDuration(MediaRecorder.class, size);
        return 1 / (outputMinFrameDuration / 1000000000f);
    }

    @Override
    public void addOnCaptureImageCallback(OnCaptureImageCallback onCaptureImageCallback) {
        mCaptureController.setOnCaptureImageCallback(onCaptureImageCallback);
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

    /**
     * 将当前预览界面的参数设置到builder里面,主要在录制视频开始时和录制结束时调用.
     * 手动模式会用到此方法
     */
    private void setPreviewParamsToBuilder(CaptureRequest.Builder builder) {
        //屏幕的缩放
        builder.set(CaptureRequest.SCALER_CROP_REGION, mCameraConfig.getRect());
        //ae
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, mCameraConfig.getManualConfig().getAe());

        if (!mCameraConfig.getManualConfig().isManual()) {
            return;
        }

        //manual awb
        int mannualWb = mCameraConfig.getManualConfig().getWb();
        if (mannualWb != Constants.DEF_MANUAL_WB) {
            RggbChannelVector rggbChannelVector = CameraUtil.colorTemperature(mannualWb);
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector);
        }
        //sec
        long sec = mCameraConfig.getManualConfig().getSec();
        if (sec != Constants.DEF_MANUAL_SEC) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, sec);
        }
        //iso
        int iso = mCameraConfig.getManualConfig().getIso();
        if (iso != Constants.DEF_MANUAL_ISO) {
            mRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            mRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
        }
    }

    protected void closePreviewSession() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        mRecorderController.stopRecord();
        CameraUtil.deleteEmptyFIle(getVideoOutputFilePath());
    }

    private void saveVideosInSp() {
        if (mCamera == null || mCameraManager == null) {
            return;
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);

        if (sp.contains("0") && sp.contains("1")) {
            //Already put in sp.
            return;
        }

        SharedPreferences.Editor edit = sp.edit();

        try {
            String[] ids = mCameraManager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (internal == null) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                android.util.Size[] sizes = map.getOutputSizes(MediaRecorder.class);
                String value = new Gson().toJson(sizes);
                edit.putString(id, value);
                edit.apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void collectPictureSizes(SizeMap sizes, StreamConfigurationMap map) {
        for (android.util.Size size : map.getOutputSizes(ImageFormat.JPEG)) {
            mPictureSizes.add(new Size(size.getWidth(), size.getHeight()));
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
                Log.e("Camera2", "chooseCameraIdByFacing: No camera available.");
                return false;
            }
            for (String id : ids) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                if ((level == null)) {
                    return false;
                }
                Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (internal == null) {
                    Log.e("Camera2", "chooseCameraIdByFacing: Unexpected state: LENS_FACING null");
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
                Log.e("Camera2", "chooseCameraIdByFacing: Unexpected state: LENS_FACING null");
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

    @SuppressWarnings("ConstantConditions")
    private int getCameraLevel() {
        return mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
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
            Log.e("Camera2", "Failed to get configuration map: " + mCameraId);
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
        for (AspectRatio ratio : mPreviewSizes.ratios()) {
            if (!mPictureSizes.ratios().contains(ratio)) {
                mPreviewSizes.remove(ratio);
            }
        }

        if (!mPreviewSizes.ratios().contains(mCameraConfig.getAspectRatio())) {
            mCameraConfig.setAspectRatio(mPreviewSizes.ratios().iterator().next());
        }

        mVideoSizes.clear();
        collectVideoSizes(mVideoSizes, mMap);
        for (AspectRatio ratio : mPreviewSizes.ratios()) {
            if (!mVideoSizes.ratios().contains(ratio)) {
                mPreviewSizes.remove(ratio);
            }
        }

        int[] ois = mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
        boolean picStab = ois != null && Arrays.binarySearch(ois, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) >= 0;
        mCameraConfig.getPhotoConfig().setStabilization(picStab);

        int[] vsm = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
        boolean videoStab = vsm != null && Arrays.binarySearch(vsm, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) >= 0;
        mCameraConfig.getVideoConfig().setStabilization(videoStab);
    }

    protected void collectVideoSizes(SizeMap sizes, StreamConfigurationMap map) {
        for (android.util.Size size : map.getOutputSizes(MediaRecorder.class)) {
            mVideoSizes.add(new Size(size.getWidth(), size.getHeight()));
        }
    }

    /**
     * <p>Starts opening a camera device.</p>
     */
    private void startOpeningCamera() {
        try {
            mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    mCamera = camera;
                    mCallback.onCameraOpened();
                    startCaptureSession();

                    //Init VideoController
                    mVideoController = new VideoController(
                            mContext, mRecorderController, mCameraConfig, mBackgroundHandler, mCallback
                    );
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    mCallback.onCameraClosed();

                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "onError: " + camera.getId() + " (" + error + ")");
                    mCamera = null;
                    mCallback.onFailed(CameraError.OPEN_FAILED);
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException | SecurityException e) {
            Log.e("Camera2", "startOpeningCamera: [Failed to open camera] ", e);
            mCallback.onFailed(CameraError.OPEN_FAILED);
        }
    }

    /**
     * <p>Starts a capture session for camera preview.</p>
     * <p>This rewrites {@link #mRequestBuilder}.</p>
     * <p>The result will be continuously processed in {@link # mSessionCallback}.</p>
     */
    protected void startCaptureSession() {
        closePreviewSession();

        ImageReader imageReader = mCaptureController.getImageReader();
        if (!isCameraOpened() || !mPreview.isReady() || imageReader == null) {
            return;
        }
        SortedSet<Size> aspectSizes = mPreviewSizes.sizes(mCameraConfig.getAspectRatio());
        Size previewSize = CameraUtil.chooseOptimalSize(mPreview, aspectSizes);
        mPreview.setBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface surface = mPreview.getSurface();
        try {
            mRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);
            mRequestBuilder.addTarget(surface);
            mRecorderController.configForCamera2();
            mRequestBuilder.addTarget(mRecorderController.getMediaRecorder().getSurface());
            mCamera.createCaptureSession(Arrays.asList(surface, mRecorderController.getMediaRecorder().getSurface(), imageReader.getSurface()),
                    mSessionCallback, mBackgroundHandler);
        } catch (Exception e) {
            Log.e(TAG, "startCaptureSession failed", e);
            mCallback.onFailed(CameraError.OPEN_FAILED);
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

    private boolean isMeteringAreaAFSupported() {
        Integer afRegions = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        return afRegions != null && afRegions >= 1;
    }

    /**
     * Updates the internal state of flash to {@link CameraConfig#getFlash()}.
     */
    private void updateFlash(CaptureRequest.Builder builder) {
        switch (mCameraConfig.getFlash()) {
            case Constants.FLASH_OFF:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_ON:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                break;
            case Constants.FLASH_TORCH:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                break;
            case Constants.FLASH_AUTO:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_RED_EYE:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
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
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to lock focus.", e);
        } catch (IllegalStateException e2) {
            Log.d("Camera2", "lock failed", e2);
        }
    }

    /**
     * Captures a still picture.
     */
    private void captureStillPicture() {
        @SuppressWarnings("ConstantConditions")
        int sensorOrientation = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_ORIENTATION);
        int orientation = (sensorOrientation +
                mPhoneOrientation * (mCameraConfig.getFacing() == Constants.FACING_FRONT ? -1 : 1) +
                360) % 360;
        mCameraConfig.setOrientation(orientation);

        mCaptureController.captureStillPicture(mCamera, mCaptureSession);
    }

    private void captureHdrPicture() {
        @SuppressWarnings("ConstantConditions")
        int sensorOrientation = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_ORIENTATION);
        int orientation = (sensorOrientation +
                mPhoneOrientation * (mCameraConfig.getFacing() == Constants.FACING_FRONT ? -1 : 1) +
                360) % 360;
        mCameraConfig.setOrientation(orientation);

        mCaptureController.captureHdrPicture(mCamera, mCaptureSession);
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

    /**
     * 延迟3s后设置为自动对焦模式, 主要用于在非录像模式下,手动点击对焦后调用
     */
    private void delayToContinuousFocus() {
        getView().removeCallbacks(mDelayFocusTask);
        getView().postDelayed(mDelayFocusTask, 3000);
    }

    protected void updatePreview() {
        try {
            if (mCaptureSession != null) {
                mCaptureSession.setRepeatingRequest(mRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            }
        } catch (Exception e) {
            Log.e("Camera2", "updatePreview: failed = ", e);
            mCallback.onFailed(CameraError.PARAM_SETTING_FAILED);
        }
    }
}
