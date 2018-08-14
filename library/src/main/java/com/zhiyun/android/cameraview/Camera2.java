package com.zhiyun.android.cameraview;

import android.animation.RectEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Range;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;

import com.google.gson.Gson;
import com.zhiyun.android.base.AspectRatio;
import com.zhiyun.android.base.CameraViewImpl;
import com.zhiyun.android.base.Constants;
import com.zhiyun.android.base.PreviewImpl;
import com.zhiyun.android.base.Size;
import com.zhiyun.android.base.SizeMap;
import com.zhiyun.android.listener.PictureCaptureCallback;
import com.zhiyun.android.recorder.MediaRecord;
import com.zhiyun.android.util.CameraUtil;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import static com.zhiyun.android.base.Constants.CAMERA_API_CAMERA2;
import static com.zhiyun.android.base.Constants.MANUAL_WB_LOWER;
import static com.zhiyun.android.base.Constants.MANUAL_WB_UPER;
import static com.zhiyun.android.base.Constants.ONE_SECOND;

@TargetApi(21)
class Camera2 extends CameraViewImpl {

    private static final String TAG = "Camera2";

    private static final SparseIntArray INTERNAL_FACINGS = new SparseIntArray();

    static {
        INTERNAL_FACINGS.put(Constants.FACING_BACK, CameraCharacteristics.LENS_FACING_BACK);
        INTERNAL_FACINGS.put(Constants.FACING_FRONT, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private List<Bitmap> mHdrBitmaps = new ArrayList<>();

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    private final CameraManager mCameraManager;

    private final CameraDevice.StateCallback mCameraDeviceCallback
            = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCamera = camera;
            mCallback.onCameraOpened();
            startCaptureSession();
        }

        @Override
        public void onClosed(@NonNull CameraDevice camera) {
            mCallback.onCameraClosed();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCamera = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "onError: " + camera.getId() + " (" + error + ")");
            mCamera = null;
        }

    };

    private PictureCaptureCallback mCaptureCallback = new PictureCaptureCallback() {

        @Override
        public void onPrecaptureRequired() {
            if (mCaptureSession == null) {
                return;
            }
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            setState(STATE_PRECAPTURE);
            try {
                mCaptureSession.capture(mPreviewRequestBuilder.build(), this, mBackgroundHandler);
            } catch (Exception e) {
                Log.e(TAG, "Failed to run precapture sequence.", e);
            }
        }

        @Override
        public void onReady() {
//            captureStillPicture();
            //Lock af
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
            updatePreview();
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
                return;
            }
            mCaptureSession = session;
            updateAutoFocus();
            updateFlash(mPreviewRequestBuilder);
            mCallback.onRequestBuilderCreate();
            setPreviewParamsToBuilder(mPreviewRequestBuilder);
            updatePreview();

            //保存摄像头的视频分辨率信息
            saveVideosInSp();
            mAvailableSpace = CameraUtil.getAvailableSpace();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
            Log.e(TAG, "Failed to configure capture session.");
        }

        @Override
        public void onClosed(@NonNull CameraCaptureSession session) {
            if (mCaptureSession != null && mCaptureSession.equals(session)) {
                mCaptureSession = null;
            }
        }

    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            try (Image image = reader.acquireNextImage()) {
                Image.Plane[] planes = image.getPlanes();
                if (planes.length > 0) {
                    ByteBuffer buffer = planes[0].getBuffer();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);

                    /*
                     * 当此接口不为空的时候,基本表示现在在使用特殊拍照功能(移动延时摄影,全景等),
                     * 所以后面的接口回调会生成不必要的图片保存在手机里,
                     * 所以可以直接return.
                     */
                    if (mOnCaptureImageCallback != null) {
                        mOnCaptureImageCallback.onPictureTaken(data);
                        //需求说不要屏幕闪光的动画了 - -
                        //sendTakePhotoAction();
                        return;
                    }

                    if (mHdrMode && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                        mHdrBitmaps.add(bitmap);

                        //最后获取到最后一张图，进行合成
                        if (mHdrImageIndex == 2) {
                            mHDRProcessor.processHDR(mHdrBitmaps, true, null, true);
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            mHdrBitmaps.get(0).compress(Bitmap.CompressFormat.JPEG, 100, stream);
                            mCallback.onPictureTaken(stream.toByteArray());
                        }
                        mHdrImageIndex++;
                    } else {
                        mCallback.onPictureTaken(data);
                        sendTakePhotoAction();
                        Log.d("Camera2", "onImageAvailable: send image");
                    }
                }
                image.close();
            } catch (OutOfMemoryError error) {
                error.printStackTrace();
                System.gc();
            }
        }

    };

    private Context mContext;

    private String mCameraId;

    private CameraCharacteristics mCameraCharacteristics;

    private StreamConfigurationMap mMap;

    private CameraDevice mCamera;

    private CameraCaptureSession mCaptureSession;

    private CaptureRequest.Builder mPreviewRequestBuilder;

    private HDRProcessor mHDRProcessor;

    private ImageReader mImageReader;

    private final SizeMap mPreviewSizes = new SizeMap();

    private final SizeMap mPictureSizes = new SizeMap();

    private android.util.Size[] mSupportedPicSizes;
    private android.util.Size[] mSupportedVideoSize;

    private int mFacing;

    private AspectRatio mAspectRatio = Constants.DEFAULT_ASPECT_RATIO;

    private boolean mAutoFocus;

    private int mFlash;

    private int mHdrImageIndex;

    /**
     * 图片防抖
     */
    private boolean mOISEnable;

    /**
     * 视频防抖
     */
    private boolean mVSMEnable;

    private boolean mStabilizeEnable;

    private boolean mAeLock;

    private int mDisplayOrientation;
    private Rect newRect;
    private boolean mForceOpen;
    private RectEvaluator mRectEvaluator;
    private ValueAnimator mSmoothZoomAnim;

    Camera2(Callback callback, PreviewImpl preview, Context context, boolean forceOpen) {
        super(callback, preview);
        this.mContext = context;
        this.mForceOpen = forceOpen;
        mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        mPreview.setCallback(new PreviewImpl.Callback() {
            @Override
            public void onSurfaceChanged() {
                startCaptureSession();
            }
        });
    }

    @Override
    public boolean start() {
        if (!chooseCameraIdByFacing()) {
            return false;
        }
        loadAudio();
        startBackgroundThread(); // 启动后台线程
        initCameraDefParameters();
        collectCameraInfo();
        prepareImageReader();
        startOpeningCamera();
        return true;
    }

    @Override
    public void stop() {
        super.stop();
        mManualWB = 5500;

        if (mIsRecordingVideo) {
            mIsRecordingVideo = false;
            mCallback.onVideoRecordStoped();
        }
        closePreviewSession();
        releaseMediaRecorder();
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }

        if (mHDRProcessor != null) {
            mHDRProcessor.onDestroy();
        }
        if (mCamera != null) {
            mCamera.close();
            mCamera = null;
        }
        stopBackgroundThread();
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
            mAutoFocus = true;
        }
    }

    @Override
    public String getCameraId() {
        return mCameraId;
    }

    @Override
    public int getFacing() {
        return mFacing;
    }

    @Override
    public Set<AspectRatio> getSupportedAspectRatios() {
        return mPreviewSizes.ratios();
    }

    @Override
    public List<android.util.Size> getSupportedPicSizes() {
        Arrays.sort(mSupportedPicSizes, new Comparator<android.util.Size>() {
            @Override
            public int compare(android.util.Size o1, android.util.Size o2) {
                return (o1.getWidth() + o1.getHeight()) - (o2.getWidth() + o2.getHeight());
            }
        });
        return Arrays.asList(mSupportedPicSizes);
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
    public List<android.util.Size> getSupportedVideoSize() {
        Arrays.sort(mSupportedVideoSize, new Comparator<android.util.Size>() {
            @Override
            public int compare(android.util.Size o1, android.util.Size o2) {
                return (o1.getWidth() + o1.getHeight()) - (o2.getWidth() + o2.getHeight());
            }
        });
        return Arrays.asList(mSupportedVideoSize);
    }

    @Override
    public float getFpsWithSize(android.util.Size size) {
        long outputMinFrameDuration = mMap.getOutputMinFrameDuration(MediaRecorder.class, size);
        return 1 / (outputMinFrameDuration / 1000000000f);
    }

    @Override
    public int[] getSupportAWBModes() {
        return mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES);
    }

    @Override
    public void setAWBMode(int mode) {
        if (mPreviewRequestBuilder == null) {
            return;
        }
        mAwbMode = mode;
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, mode);
        updatePreview();
    }

    @Override
    public boolean setAspectRatio(AspectRatio ratio) {
        if (ratio == null || ratio.equals(mAspectRatio) ||
                !mPreviewSizes.ratios().contains(ratio)) {
            return false;
        }
        mAspectRatio = ratio;
        prepareImageReader();
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
            startCaptureSession();
        }
        return true;
    }

    @Override
    public AspectRatio getAspectRatio() {
        return mAspectRatio;
    }

    @Override
    public void setAutoFocus(boolean autoFocus) {
        if (mAutoFocus == autoFocus) {
            return;
        }
        mAutoFocus = autoFocus;
        if (mPreviewRequestBuilder != null) {
            updateAutoFocus();
            if (mCaptureSession != null) {
                try {
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
                } catch (Exception e) {
                    mAutoFocus = !mAutoFocus; // Revert
                }
            }
        }
    }

    @Override
    public boolean getAutoFocus() {
        return mAutoFocus;
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    public boolean isFlashAvailable() {
        Boolean isFlashAvailable = mCameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        return isFlashAvailable != null && isFlashAvailable == true;
    }

    @Override
    public void setFlash(int flash) {
        if (mFlash == flash) {
            return;
        }
        int saved = mFlash;
        mFlash = flash;
        if (mPreviewRequestBuilder != null) {
            updateFlash(mPreviewRequestBuilder);
            if (mCaptureSession != null) {
                try {
                    mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
                } catch (CameraAccessException | IllegalStateException e) {
                    mFlash = saved; // Revert
                }
            }
        }
    }

    @Override
    public boolean isSupportedStabilize() {
        return mOISEnable || mVSMEnable;
    }

    @Override
    public boolean getStabilizeEnable() {
        return mStabilizeEnable;
    }

    @Override
    public void setStabilizeEnable(boolean enable) {
        mStabilizeEnable = enable;
    }

    /**
     * 设置当前防抖的选项
     * 如果图片防抖和视频防抖同时支持,也不能同时都设置为ON(文档说明),需要将另一个设置为OFF
     *
     * @param imageStabilize 是否为图片防抖
     *                       true:图片防抖
     *                       false:视频防抖
     */
    private void setStabilize(CaptureRequest.Builder builder, boolean imageStabilize) {

        if (!mStabilizeEnable) {
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF);
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
            return;
        }

        if (mOISEnable) {
            builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    imageStabilize ?
                            CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON :
                            CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF);
        }
        if (mVSMEnable) {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    imageStabilize ?
                            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF :
                            CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON);
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
    public void setManualMode(boolean manual) {
        mManualMode = manual;
        if (mPreviewRequestBuilder == null) {
            return;
        }
        if (manual) {
//            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_OFF);
        } else {
            mAutoFocus = true;
            mAe = 0;
            mAf = 0;
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            updatePreview();
        }
    }

    @Override
    public void setIsoAuto() {
        if (mPreviewRequestBuilder != null) {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        }
    }

    @Override
    public int getFlash() {
        return mFlash;
    }

    @Override
    public void setTorch(boolean open) {
        if (mPreviewRequestBuilder != null) {
            mPreviewRequestBuilder.set(CaptureRequest.FLASH_MODE, open ? CameraMetadata.FLASH_MODE_TORCH : CameraMetadata.FLASH_MODE_OFF);
            updatePreview();
        }
    }

    @Override
    public boolean isTorch() {
        //noinspection ConstantConditions
        return mPreviewRequestBuilder != null && mPreviewRequestBuilder.get(CaptureRequest.FLASH_MODE) == CameraMetadata.FLASH_MODE_TORCH;
    }

    @Override
    public void setPicSize(Size picSize) {
        mPicSize = picSize;

        //重新设置ImageReader,以使用新的Size创建
        prepareImageReader();
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
            startCaptureSession();
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
    public float getAeStep() {
        //noinspection ConstantConditions
        return mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP).floatValue();
    }

    @Override
    public void setAEValue(int value) {
        if (mPreviewRequestBuilder == null) {
            return;
        }
        mAe = value;
        if (mOnAeChangeListener != null) {
            mOnAeChangeListener.onAeChanged(mAe);
        }
//        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_ON);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, value);
        updatePreview();
    }

    @Override
    public boolean isManualSecSupported() {
        Range<Long> range = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
        return range != null && !range.getLower().equals(range.getUpper());
    }

    @Override
    public Range<Long> getSecRange() {
        return mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
    }

    @Override
    public void setSecValue(long value) {
        if (mPreviewRequestBuilder == null) {
            return;
        }
        mSec = value;
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, value);
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
        if (mPreviewRequestBuilder == null) {
            return;
        }
        mIso = value;
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, value);
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
        return new Range<>(MANUAL_WB_LOWER, MANUAL_WB_UPER);
    }

    @Override
    public void setManualWBValue(int value) {
        if (mPreviewRequestBuilder == null) {
            return;
        }
        mManualWB = value;

        RggbChannelVector rggbChannelVector = CameraUtil.colorTemperature(value);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
        mPreviewRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector);
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
        if (mPreviewRequestBuilder == null) {
            return;
        }
        mAf = value;
        mAutoFocus = false;
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
        mPreviewRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, value);
        updatePreview();
    }

    @Override
    public boolean isManualControlAF() {
        try {
            Integer afMode = mPreviewRequestBuilder.get(CaptureRequest.CONTROL_AF_MODE);
            return afMode != null && afMode == CameraMetadata.CONTROL_AF_MODE_OFF;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isManualWTSupported() {
        return getMaxZoom() > 1;
    }

    @Override
    public void takePicture() {
        /*
        直接拍照.拍照时不需要lockFocus对焦了,因为OPPO R9S依然有小概率会无法对焦.
        在极大部分情况下,相机会自动对焦到目标物体上,点击拍照时也确实不需要再检测对焦状态了
         */
        if (mHdrMode
                && !mIsRecordingVideo
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            captureHdrPicture();
        } else {
            captureStillPicture();
        }

       /* if (mAutoFocus && !isRecordingVideo()) {
            if (mHdrMode) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    captureStillPicture();
                } else {
                    captureHdrPicture();
                }
            } else {
                lockFocus();
            }
        } else {
            captureStillPicture();
        }*/
    }

    @Override
    public void startRecordingVideo() {
        if (null == mCamera || !mPreview.isReady()) {
            return;
        }
        if (lowAvailableSpace()) {
            return;
        }
        try {
            setupVideoRecorder();
            mPreviewRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT,
                    CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);

            //auto white-balance
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, mAwbMode);

            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, mAeLock);

            setPreviewParamsToBuilder(mPreviewRequestBuilder);

            //设置防抖
            setStabilize(mPreviewRequestBuilder, false);

            List<Surface> surfaces = new ArrayList<>();

            // Set up Texture for the camera preview
            SurfaceTexture previewTexture = mPreview.getSurfaceTexture();
            Surface previewSurface = new Surface(previewTexture);
            surfaces.add(previewSurface);
            mPreviewRequestBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = getSurface();
            surfaces.add(recorderSurface);
            mPreviewRequestBuilder.addTarget(recorderSurface);

            // Set up Surface for the still picture
//            Surface stillPictureSurface = mImageReader.getSurface();
//            surfaces.add(stillPictureSurface);
//            mPreviewRequestBuilder.addTarget(stillPictureSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCamera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mCaptureSession = cameraCaptureSession;
                    if (null == mCamera) {
                        return;
                    }
                    updatePreview();
                    mIsRecordingVideo = true;
                    // Start recording
                    try {
                        //录制视频时,不需要一直对焦
                        lockFocus();
                        startVideoRecording();
                        mCallback.onVideoRecordingStarted();
                        addVolumeListener(useMediaRecord());
                        updateAvailableSpace();
                    } catch (IllegalStateException e) {
                        mIsRecordingVideo = false;
                        Log.e("Camera2", "onConfigured:  the camera is already in use by another app");
                        mCallback.onVideoRecordingFailed();
                        startCaptureSession();
                    }

                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mCallback.onVideoRecordingFailed();
                    startCaptureSession();
                }
            }, mBackgroundHandler);
        } catch (Exception e) {
            mCallback.onVideoRecordingFailed();
            startCaptureSession();
            Log.e("Camera2", "startRecordingVideo: failed = " + e.getMessage());
        }

    }

    /**
     * 将当前预览界面的参数设置到builder里面,主要在录制视频开始时和录制结束时调用.
     * 手动模式会用到此方法
     */
    @SuppressWarnings("ConstantConditions")
    private void setPreviewParamsToBuilder(CaptureRequest.Builder builder) {
        //屏幕的缩放
        builder.set(CaptureRequest.SCALER_CROP_REGION, newRect);
        //ae
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, mAe);

        if (!mManualMode) {
            return;
        }

        //manual awb
        if (mManualWB != 5500) {
            RggbChannelVector rggbChannelVector = CameraUtil.kelvinToRgb(mManualWB);
            builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
            builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
            builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector);
        }
        //sec
        if (mSec != 0) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mSec);
        }
        //iso
        if (mIso != 0) {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, mIso);
        }
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

    private void closePreviewSession() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        if (mBackgroundThread == null) {
            return;
        }
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stopRecordingVideo() {
        try {
            mIsRecordingVideo = false;

            // Stop recording
            releaseMediaRecorder();

            //如果正在进行平滑af和wt,就停止
            stopSmoothFocus();
            stopSmoothZoom();

            playSound(SOUND_ID_STOP);

            mCallback.onVideoRecordStoped();

            sendRecordingStopAction();

            closePreviewSession();
            startCaptureSession();
        } catch (Exception e) {
            mCallback.onVideoRecordingFailed();
            Log.e("Camera2", "stopRecordingVideo:  = " + e.getMessage());
            stop();
            start();
        }
    }

    /**
     * 三星手机在8.0以下版本使用 {@link MediaRecorder} 录制视频会出现音视频时间戳不一致的情况！
     * 在8.0以下且需要录音时使用 {@link MediaRecord}
     * @return true 使用 {@link MediaRecord}
     */
    private boolean useMediaRecord() {
        boolean isOreo = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        return !isOreo && !mMuteVideo && "samsung".equals(Build.BRAND.toLowerCase());
    }

    private void setUpMediaRecorder() throws IOException {
        mMediaRecorder = new MediaRecorder();

        playSound(SOUND_ID_START);

        if (!mMuteVideo) {
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        }
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            throw new FileNotFoundException("点击拍摄视频前，请先传入视频输出路径");
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        mMediaRecorder.setVideoFrameRate(mFps);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        if (!mMuteVideo) {
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mMediaRecorder.setAudioEncodingBitRate(96000);
            mMediaRecorder.setAudioSamplingRate(48000);
            mMediaRecorder.setAudioChannels(2);
        }

        mMediaRecorder.setCaptureRate(mCaptureRate);

        mMediaRecorder.setVideoEncodingBitRate(mBitrate);

        @SuppressWarnings("ConstantConditions")
        int sensorOrientation = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_ORIENTATION);

        mMediaRecorder.setOrientationHint((sensorOrientation +
                mPhoneOrientation * (mFacing == Constants.FACING_FRONT ? -1 : 1) +
                360) % 360);

        mMediaRecorder.prepare();
    }

    /**
     * 仍然为过渡方案，等待自己实现 慢动作和延时摄影时移除系统的 MediaRecorder
     */
    private void setUpMediaRecord() throws IOException {
        mMediaRecord = new MediaRecord();
        playSound(SOUND_ID_START);
        mMediaRecord.setVideoFrameRate(mFps);
        mMediaRecord.setCaptureRate(mFps);
        mMediaRecord.setVideoEncodingBitRate(mBitrate);
        mMediaRecord.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecord.setAudioChannels(2);
        mMediaRecord.setAudioEncodingBitRate(96000);
        mMediaRecord.setAudioSamplingRate(48000);
        mMediaRecord.containsAudio(true);
        mMediaRecord.containsVideo(true);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            throw new FileNotFoundException("点击拍摄视频前，请先传入视频输出路径");
        }
        mMediaRecord.setOutputFile(mNextVideoAbsolutePath);

        @SuppressWarnings("ConstantConditions")
        int sensorOrientation = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_ORIENTATION);

        mMediaRecord.setOrientationHint((sensorOrientation +
                mPhoneOrientation * (mFacing == Constants.FACING_FRONT ? -1 : 1) +
                360) % 360);

        mMediaRecord.prepare();
    }

    private void setupVideoRecorder() throws IOException {
        if (useMediaRecord()) {
            setUpMediaRecord();
        } else {
            setUpMediaRecorder();
        }
    }

    private void startVideoRecording() {
        if (useMediaRecord()) {
            mMediaRecord.start();
        } else {
            mMediaRecorder.start();
        }
    }

    private Surface getSurface() {
        return useMediaRecord() ? mMediaRecord.getSurface() : mMediaRecorder.getSurface();
    }

    @Override
    public void setDisplayOrientation(int displayOrientation) {
        mDisplayOrientation = displayOrientation;
        mPreview.setDisplayOrientation(mDisplayOrientation);
    }

    /**
     * <p>Chooses a camera ID by the specified camera facing ({@link #mFacing}).</p>
     * <p>This rewrites {@link #mCameraId}, {@link #mCameraCharacteristics}, and optionally
     * {@link #mFacing}.</p>
     */
    private boolean chooseCameraIdByFacing() {
        try {
            int internalFacing = INTERNAL_FACINGS.get(mFacing);
            final String[] ids = mCameraManager.getCameraIdList();
            if (ids.length == 0) { // No camera
                Log.e("Camera2", "chooseCameraIdByFacing: No camera available.");
                return false;
            }
            for (String id : ids) {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(id);
                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                if ((level == null) ||
                        level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                    continue;
                }
                Integer internal = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (internal == null) {
                    throw new NullPointerException("Unexpected state: LENS_FACING null");
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
            boolean isLegacy = level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
            if (isLegacy && !mForceOpen) {
                return false;
            }
            Integer internal = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (internal == null) {
                throw new NullPointerException("Unexpected state: LENS_FACING null");
            }
            for (int i = 0, count = INTERNAL_FACINGS.size(); i < count; i++) {
                if (INTERNAL_FACINGS.valueAt(i) == internal) {
                    mFacing = INTERNAL_FACINGS.keyAt(i);
                    return true;
                }
            }
            // The operation can reach here when the only camera device is an external one.
            // We treat it as facing back.
            mFacing = Constants.FACING_BACK;
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to get a list of camera devices");
            return false;
        }
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
                edit.putString(id + "_info", map.toString());
                edit.apply();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 初始化各项默认参数参数
     */
    private void initCameraDefParameters() {
        mHDRProcessor = new HDRProcessor(mContext);

        if (mBitrate == 0) {
            mBitrate = 7 * 1000000;
        }
        if (mCaptureRate == 0) {
            mCaptureRate = 30;
        }
        if (mVideoSize == null) {
            mVideoSize = new Size(1280, 720);
        }
        if (mFps == 0) {
            mFps = 30;
        }
        if (mAwbMode == 0) {
            mAwbMode = CameraMetadata.CONTROL_AWB_MODE_AUTO;
        }
        if (mManualWB == 0) {
            mManualWB = 5500;
        }
    }

    /**
     * <p>Collects some information from {@link #mCameraCharacteristics}.</p>
     * <p>This rewrites {@link #mPreviewSizes}, {@link #mPictureSizes}, and optionally,
     * {@link #mAspectRatio}.</p>
     */
    private void collectCameraInfo() {
        mMap = mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (mMap == null) {
            throw new IllegalStateException("Failed to get configuration map: " + mCameraId);
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

        if (!mPreviewSizes.ratios().contains(mAspectRatio)) {
            mAspectRatio = mPreviewSizes.ratios().iterator().next();
        }

        mSupportedPicSizes = mMap.getOutputSizes(mPicFormat);
        mSupportedVideoSize = mMap.getOutputSizes(MediaRecorder.class);

        int[] ois = mCameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
        mOISEnable = ois != null && Arrays.binarySearch(ois, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) >= 0;

        int[] vsm = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
        mVSMEnable = vsm != null && Arrays.binarySearch(vsm, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) >= 0;
    }

    protected void collectPictureSizes(SizeMap sizes, StreamConfigurationMap map) {
        for (android.util.Size size : map.getOutputSizes(ImageFormat.JPEG)) {
            mPictureSizes.add(new Size(size.getWidth(), size.getHeight()));
        }
    }

    @SuppressWarnings("ConstantConditions")
    private int getCameraLevel() {
        return mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
    }

    private void prepareImageReader() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mPicSize == null) {
            /*
            如果支持1280*720大小,则使用,否则使用支持中的最大
             */
            Size defSize = new Size(1280, 720);
            if (mPictureSizes.sizes(mAspectRatio) != null &&
                    !mPictureSizes.sizes(mAspectRatio).contains(defSize)) {
                mPicSize = mPictureSizes.sizes(mAspectRatio).last();
            } else {
                mPicSize = defSize;
            }
            mPictureSizes.sizes(mAspectRatio);
        }
        mImageReader = ImageReader.newInstance(mPicSize.getWidth(), mPicSize.getHeight(), mPicFormat, 1);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
    }

    /**
     * <p>Starts opening a camera device.</p>
     * <p>The result will be processed in {@link #mCameraDeviceCallback}.</p>
     */
    private void startOpeningCamera() {
        try {
            mCameraManager.openCamera(mCameraId, mCameraDeviceCallback, null);
        } catch (CameraAccessException | SecurityException e) {
            Log.e("Camera2", "startOpeningCamera: [Failed to open camera] " + e.getMessage());
        }
    }

    /**
     * <p>Starts a capture session for camera preview.</p>
     * <p>This rewrites {@link #mPreviewRequestBuilder}.</p>
     * <p>The result will be continuously processed in {@link # mSessionCallback}.</p>
     */
    private void startCaptureSession() {
        if (!isCameraOpened() || !mPreview.isReady() || mImageReader == null) {
            return;
        }
        SortedSet<Size> aspectSizes = mPreviewSizes.sizes(mAspectRatio);
        Size previewSize = CameraUtil.chooseOptimalSize(mPreview, aspectSizes);
        mPreview.setBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface surface = mPreview.getSurface();
        try {
            mPreviewRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCamera.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    mSessionCallback, mBackgroundHandler);
        } catch (Exception e) {
            Log.e("Camera2", "startCaptureSession: ", e);
            stop();
            start();
        }
    }

    /**
     * Updates the internal state of auto-focus to {@link #mAutoFocus}.
     */
    private void updateAutoFocus() {
        if (mAutoFocus) {
            int[] modes = mCameraCharacteristics.get(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
            // Auto focus is not supported
            if (modes == null || modes.length == 0 ||
                    (modes.length == 1 && modes[0] == CameraCharacteristics.CONTROL_AF_MODE_OFF)) {
                mAutoFocus = false;
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            } else {
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            }
        } else {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
            //在非自动对焦下,第一次直接让其对焦完成,并锁定.
            //主要用于使用跟踪时,需要锁定画面.
            //ps,如果不手动锁定focus,而是调用setAutoFocus(false),则某些手机(三星等)画面会变得模糊.
            //故此需要次方法.
            lockFocus();
        }
    }

    @Override
    public void resetAF(MotionEvent event, final boolean lock) {
        if (mCamera != null && mCaptureSession != null) {

            if (!isMeteringAreaAFSupported()) {
                return;
            }

            mAeLock = lock;

            Rect rect = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            if (rect == null) return;
            int areaSize = getFocusAreaSize();
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
                    getFocusMeteringAreaWeight());
            MeteringRectangle[] meteringRectangleArr = {meteringRectangle};

            try {

                //需要先把对焦模式从CONTROL_AE_PRECAPTURE_TRIGGER_START切换到CONTROL_AF_MODE_AUTO
                //且设置Trigger设置为null,此时,如下的重新设置对焦才可以完全正常显示
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                updatePreview();
                //end

                //Now add a new AF trigger with focus region
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRectangleArr);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                if (!mManualMode) {
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, meteringRectangleArr);
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                }
                mPreviewRequestBuilder.setTag("FOCUS_TAG"); //we'll capture this later for resuming the preview

                //then we ask for a single request (not repeating!)
                mCaptureSession.capture(mPreviewRequestBuilder.build(),
                        new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                                if (request.getTag() == "FOCUS_TAG") {
                                    if (lock) {
                                        lockAEandAF();
                                    } else {
                                        //the focus trigger is complete -
                                        //resume repeating (preview surface will get frames), clear AF trigger
                                        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
                                        updatePreview();
                                        if (!mIsRecordingVideo) {
                                            delayToContinuousFocus();
                                        }
                                    }
                                    mAf = 0;
                                }
                            }
                        }, mBackgroundHandler);

            } catch (Exception e) {
                Log.e("Camera2", "resetAF:  = " + e.getMessage());
            }
        }
    }

    private boolean isMeteringAreaAFSupported() {
        Integer afRegions = mCameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF);
        return afRegions != null && afRegions >= 1;
    }

    @Override
    public void lockAEandAF() {
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, true);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO);
        updatePreview();
    }

    @Override
    public void unLockAEandAF() {
        if (mPreviewRequestBuilder == null) {
            return;
        }
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_LOCK, false);
        updatePreview();
    }

    /**
     * Updates the internal state of flash to {@link #mFlash}.
     */
    private void updateFlash(CaptureRequest.Builder builder) {
        switch (mFlash) {
            case Constants.FLASH_OFF:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                break;
            case Constants.FLASH_ON:
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
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

    @Override
    public String getCameraAPI() {
        return CAMERA_API_CAMERA2;
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
        if (mPreviewRequestBuilder == null) {
            return;
        }

        Rect rect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (rect == null) {
            return;
        }

        newRect = CameraUtil.getZoomRect(scale, rect.width(), rect.height());

        //新的范围比最大值还大,不可以设置,否则手机直接重启.
        if (rect.contains(newRect)) {
            mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, newRect);
            updatePreview();
        }

        //某些情况下,比如1+0.1f的结果可能是1.1000000476837158,
        //这将导致最大的scale会出现类似8.000000476837158这样的值,
        //最终导致crop之后的范围越界,引发异常.
        //所以这里进行只保留二位数的转换.
        scale = Math.round(scale * 100) / 100f;

        mWt = scale;
        mZoomRatio = scale;
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
        if (mPreviewRequestBuilder == null) {
            return;
        }
        if (end == start) {
            return;
        }
        final Rect maxRect = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        final Rect startRect = CameraUtil.getScaledRect(maxRect, start);
        final Rect destinationRect = CameraUtil.getScaledRect(maxRect, end);

        if (newRect == null) {
            newRect = CameraUtil.getScaledRect(maxRect, 1.01f);
        }

        if (mRectEvaluator == null) {
            mRectEvaluator = new RectEvaluator(newRect);
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
                newRect = mRectEvaluator.evaluate(value, startRect, destinationRect);
                //新的范围比最大值还大,不可以设置,否则手机直接重启.
                if (maxRect.contains(newRect)) {
                    mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, newRect);
                    updatePreview();
                    mWt = start + value * (end - start);
                    mWt = Math.round(mWt * 100) / 100f;
                    mZoomRatio = mWt;
                }
            }
        });
        mSmoothZoomAnim.setDuration(duration);
        mSmoothZoomAnim.start();
    }

    /**
     * Locks the focus
     */
    private void lockFocus() {
        if (mCaptureSession == null) {
            return;
        }
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CaptureRequest.CONTROL_AF_TRIGGER_START);
        try {
            mCaptureCallback.setState(PictureCaptureCallback.STATE_LOCKING);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to lock focus.", e);
        } catch (IllegalStateException e2) {
            Log.d("Camera2", "lock failed");
        }
    }

    /**
     * Captures a still picture.
     */
    private void captureStillPicture() {
        if (mCamera == null || mCaptureSession == null) {
            return;
        }
        try {
            CaptureRequest.Builder captureRequestBuilder;
            if (mIsRecordingVideo) {
                captureRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
                captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT);
                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            } else {
                captureRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
            }

            captureRequestBuilder.addTarget(mImageReader.getSurface());

            //设置防抖
            setStabilize(mPreviewRequestBuilder, true);

            /*
             * Android版本22及以上的机器,使用系统自带的HDR情景模式来拍摄HDR照片,速度更快更稳定.
             * 而对于Android版本21的机器,使用下面的{@link #captureHdrPicture()}来实现,该方法使用了OpenCamera的算法,
             * 使用HDRProcessor方式将手动拍摄的3张图片按照一定的算法合成为一张图片,耗时1.5s
             */
            if (mHdrMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_HDR);
            }

            captureRequestBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mAf);
            setPreviewParamsToBuilder(captureRequestBuilder);
            updateFlash(captureRequestBuilder);

            // Calculate JPEG orientation.
            @SuppressWarnings("ConstantConditions")
            int sensorOrientation = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_ORIENTATION);
            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    (sensorOrientation +
                            mPhoneOrientation * (mFacing == Constants.FACING_FRONT ? -1 : 1) +
                            360) % 360);
            playSound(SOUND_ID_CLICK);
            // Stop preview and capture a still picture.
            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureRequestBuilder.build(),
                    new CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                                       @NonNull CaptureRequest request,
                                                       @NonNull TotalCaptureResult result) {
                            unlockFocus();
                        }

                        @Override
                        public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                                    @NonNull CaptureRequest request,
                                                    @NonNull CaptureFailure failure) {
                            unlockFocus();
                        }
                    }, mBackgroundHandler);

        } catch (CameraAccessException | IllegalArgumentException e) {
            Log.e(TAG, "Cannot capture a still picture." + e.getMessage());
        }
    }

    private void captureHdrPicture() {
        if (mCamera == null || mCaptureSession == null) {
            return;
        }
        mHdrImageIndex = 0;
        mHdrBitmaps.clear();

        try {
            CaptureRequest.Builder captureStillBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureStillBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
            captureStillBuilder.addTarget(mImageReader.getSurface());

            captureStillBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, mAf);
            setPreviewParamsToBuilder(captureStillBuilder);
            updateFlash(captureStillBuilder);
            //设置防抖
            setStabilize(mPreviewRequestBuilder, true);

            // Calculate JPEG orientation.
            @SuppressWarnings("ConstantConditions")
            int sensorOrientation = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_ORIENTATION);
            captureStillBuilder.set(CaptureRequest.JPEG_ORIENTATION,
                    (sensorOrientation +
                            mPhoneOrientation * (mFacing == Constants.FACING_FRONT ? -1 : 1) +
                            360) % 360);

            List<CaptureRequest> list = new ArrayList<>();

            captureStillBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            captureStillBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, 800);
            captureStillBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION, ONE_SECOND / 30);

            captureStillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ONE_SECOND / 200);
            list.add(captureStillBuilder.build());
            captureStillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ONE_SECOND / 24);
//            captureStillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mSec);
            list.add(captureStillBuilder.build());
            captureStillBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ONE_SECOND / 5);
            list.add(captureStillBuilder.build());

            playSound(SOUND_ID_CLICK);

            mCaptureSession.stopRepeating();
            mCaptureSession.captureBurst(list, new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }

                @Override
                public void onCaptureFailed(@NonNull CameraCaptureSession session,
                                            @NonNull CaptureRequest request,
                                            @NonNull CaptureFailure failure) {
                    unlockFocus();
                }
            }, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlocks the auto-focus and restart camera preview. This is supposed to be called after
     * capturing a still picture.
     */
    private void unlockFocus() {
        if (!mManualMode) {
            setUpCaptureRequestBuilder(mPreviewRequestBuilder);
            setContinuousFocus(mPreviewRequestBuilder);
        }
        updatePreview();
        mCaptureCallback.setState(PictureCaptureCallback.STATE_PREVIEW);
    }

    private void updatePreview() {
        try {
            if (mCaptureSession != null) {
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            }
        } catch (Exception e) {
            Log.e("Camera2", "updatePreview: failed = " + e.getMessage());
        }
    }

    /**
     * 延迟3s后设置为自动对焦模式, 主要用于在非录像模式下,手动点击对焦后调用
     */
    private void delayToContinuousFocus() {
        getView().removeCallbacks(mDelayFocusTask);
        getView().postDelayed(mDelayFocusTask, 3000);
    }

    private Runnable mDelayFocusTask = new Runnable() {
        @Override
        public void run() {
            if (mIsRecordingVideo) {
                return;
            }
            setContinuousFocus(mPreviewRequestBuilder);
            updatePreview();
        }
    };
}
