package cn.zhengshang.cameraview;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.MotionEvent;
import android.view.Surface;

import com.huawei.emui.himedia.camera.HwCamera;
import com.huawei.emui.himedia.camera.HwCameraCaptureSession;
import com.huawei.emui.himedia.camera.HwCameraConstrainedHighSpeedCaptureSession;
import com.huawei.emui.himedia.camera.HwCameraDevice;
import com.huawei.emui.himedia.camera.HwCameraEngineDieRecipient;
import com.huawei.emui.himedia.camera.HwCameraInitSuccessCallback;
import com.huawei.emui.himedia.camera.HwCameraManager;
import com.huawei.emui.himedia.camera.HwCameraMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import cn.zhengshang.base.AspectRatio;
import cn.zhengshang.base.CameraViewImpl;
import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.base.SizeMap;
import cn.zhengshang.listener.CameraCallback;
import cn.zhengshang.listener.CameraError;
import cn.zhengshang.util.CameraUtil;

import static cn.zhengshang.util.CameraUtil.clamp;

@RequiresApi(api = Build.VERSION_CODES.M)
public class HwCameraVideo extends CameraViewImpl {

    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    private static final String TAG = "HwCameraSlowMotion";

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    long currentTimeReserve = 0;
    int fpsCount = 0;
    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    boolean firstCapture = true;
    boolean firstComplete = true;
    HwCameraCaptureSession.CaptureCallback captureCallBack = new HwCameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(HwCameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
            if (firstCapture == true) {
                firstCapture = false;
            }
        }

        @Override
        public void onCaptureCompleted(HwCameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Log.d(TAG, "onCaptureCompleted: 120fps test");
            if (firstComplete == true) {
                firstComplete = false;
            }
            if (currentTimeReserve == 0) {
                currentTimeReserve = System.currentTimeMillis();
            } else if (fpsCount % 15 == 0) {
                long currentMillis = System.currentTimeMillis();
                long captureInterval = currentMillis - currentTimeReserve;
                if (captureInterval != 0) {
                    double fpsRate = 1000.0 * 15 / (captureInterval);
                    final String strFpaRate = String.format("%6.2f", fpsRate);
                    Log.e("HwCameraVideo", "Streamfps = " + strFpaRate);
                }

                currentTimeReserve = currentMillis;
            }
            fpsCount++;
        }
    };
    private String mCameraId;
    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private HwCameraDevice mCameraDevice;
    /**
     * A reference to the current {@link android.hardware.camera2.CameraCaptureSession} for
     * preview.
     */
    private HwCameraCaptureSession mPreviewSession;
    private HwCameraConstrainedHighSpeedCaptureSession mPreviewHighSpeedSession;
    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;
    /**
     * The {@link Size} of video recording.
     */
    private Size mVideoSize;
    private com.huawei.emui.himedia.camera.HwCamera hwCamera;
    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    private int mDisplayOrientation;
    private Integer mSensorOrientation;
    private CaptureRequest.Builder mPreviewBuilder, mCaputreBuilder;
    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private HwCameraDevice.StateCallback mStateCallback = new HwCameraDevice.StateCallback() {

        @Override
        public void onOpened(HwCameraDevice hwCameraDevice) {
            mCameraDevice = hwCameraDevice;

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    mCallback.onCameraOpened();
                }
            });

            startPreview();
            mCameraOpenCloseLock.release();
        }

        @Override
        public void onClosed(HwCameraDevice camera) {
            super.onClosed(camera);
            mCallback.onCameraClosed();
        }

        @Override
        public void onDisconnected(HwCameraDevice hwCameraDevice) {

        }

        @Override
        public void onError(HwCameraDevice hwCameraDevice, int i) {

        }
    };
    private SizeMap mPictureSizes = new SizeMap();
    private HwCameraManager manager;
    private String[] mCameraIdList;
    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    @SuppressWarnings("MissingPermission")
    private
    CameraCharacteristics characteristics;

    public HwCameraVideo(Context context, CameraCallback callback, PreviewImpl preview) {
        super(context, callback, preview);

        mPreview.setCallback(new PreviewImpl.Callback() {
            @Override
            public void onSurfaceChanged() {
                startPreview();
            }
        });
        CameraUtil.show(context, "Useing huawei high fps video", mDisplayOrientation);
    }

    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            Log.d(TAG, "chooseVideoSize: width = " + size.getWidth() + " height = " + size.getHeight());
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    private static Size chooseHighSpeedVideoSize(Size[] choices) {
        Size biggestSize = choices[0];
        for (Size size : choices) {
            if (size.getWidth() > biggestSize.getWidth()) {
                biggestSize = size;
            }
            Log.d(TAG, "chooseHighSpeedVideoSize: width = " + size.getWidth() + " height = " + size.getHeight());
        }
        Log.d(TAG, "chooseHighSpeedVideoSize: width = " + biggestSize.getWidth() + " height = " + biggestSize.getHeight());
        return biggestSize;
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            Log.d(TAG, "chooseOptimalSize: width = " + option.getWidth() + " height = " + option.getHeight());
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private void focusOnTouch(double x, double y) {
        float focusAreaSize = 200;
        int areaSize = Float.valueOf(focusAreaSize * (float) 1).intValue();
        Log.i("calculateTapArea", "areaSize---> " + areaSize);//300
        Log.i("calculateTapArea", "x---> " + x + ",,,y---> " + y);//对的
        int displayRotation = mDisplayOrientation;


        int realWidth = mPreviewSize.getWidth();
        int realHeight = mPreviewSize.getHeight();
        if (Surface.ROTATION_90 == displayRotation || Surface.ROTATION_270 == displayRotation) {
            realWidth = mPreviewSize.getHeight();
            realHeight = mPreviewSize.getWidth();
        }
        double imageScale = 1.0, verticalOffset = 0.0, horizontalOffset = 0.0, tmp;
        int viewWidth = mPreview.getWidth();
        int viewHeight = mPreview.getHeight();

        if (realHeight * viewWidth > realWidth * viewHeight) {
            imageScale = viewWidth * 1.0 / realWidth;
            verticalOffset = (realHeight - viewHeight / imageScale) / 2;
        } else {
            imageScale = viewHeight * 1.0 / realHeight;
            horizontalOffset = (realWidth - viewWidth / imageScale) / 2;
        }

        x = x / imageScale + horizontalOffset;
        y = y / imageScale + verticalOffset;
        if (Surface.ROTATION_90 == displayRotation) {
            tmp = x;
            x = y;
            y = mPreviewSize.getHeight() - tmp;
        } else if (Surface.ROTATION_270 == displayRotation) {
            tmp = x;
            x = mPreviewSize.getWidth() - y;
            y = tmp;
        }

        CaptureRequest cr = mPreviewBuilder.build();
        Rect cropRegion = cr.get(CaptureRequest.SCALER_CROP_REGION);
        if (null == cropRegion) {
            Log.e(TAG, "can't get crop region");
            cropRegion = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        }
        Log.d("calculateTapArea", "crop region " + "left " + cropRegion.left + " right " + cropRegion.right + " top " + cropRegion.top + " bottom " + cropRegion.bottom);
        int cropWidth = cropRegion.width(), cropHeight = cropRegion.height();
        if (mPreviewSize.getHeight() * cropWidth > mPreviewSize.getWidth() * cropHeight) {
            imageScale = cropHeight * 1.0 / mPreviewSize.getHeight();
            verticalOffset = 0;
            horizontalOffset = (cropWidth - imageScale * mPreviewSize.getWidth()) / 2;
        } else {
            imageScale = cropWidth * 1.0 / mPreviewSize.getWidth();
            horizontalOffset = 0;
            verticalOffset = (cropHeight - imageScale * mPreviewSize.getHeight()) / 2;
        }

        x = x * imageScale + horizontalOffset + cropRegion.left;
        y = y * imageScale + verticalOffset + cropRegion.top;

        double tapAreaRatio = 0.1;
        Rect rect = new Rect();
        rect.left = clamp((int) (x - tapAreaRatio / 2 * cropRegion.width()), 0, cropRegion.width());
        rect.right = clamp((int) (x + tapAreaRatio / 2 * cropRegion.width()), 0, cropRegion.width());
        rect.top = clamp((int) (y - tapAreaRatio / 2 * cropRegion.height()), 0, cropRegion.height());
        rect.bottom = clamp((int) (y + tapAreaRatio / 2 * cropRegion.height()), 0, cropRegion.height());

        Log.d("calculateTapArea", "tap region " + "left " + rect.left + " right " + rect.right + " top " + rect.top + " bottom " + rect.bottom);

        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{new MeteringRectangle(rect, 1000)});
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{new MeteringRectangle(rect, 1000)});
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);


        try {
            List<CaptureRequest> speedRequestList = mPreviewHighSpeedSession.createHighSpeedRequestList(mPreviewBuilder.build());
            mPreviewHighSpeedSession.captureBurst(speedRequestList, null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void start() {
        super.start();
        openCamera(mPreview.getWidth(), mPreview.getHeight());
    }

    @Override
    public void stop() {
        closeCamera();
        super.stop();
    }

    @Override
    public int getFacing() {
        return CameraMetadata.LENS_FACING_BACK;
    }

    @Override
    public void setFacing(int facing) {

    }

    @Override
    public String getCameraId() {
        return null;
    }

    @Override
    public Set<AspectRatio> getSupportedAspectRatios() {
        return null;
    }

    @Override
    public SortedSet<cn.zhengshang.base.Size> getSupportedPicSizes() {
        return null;
    }

    @Override
    public boolean isSupported60Fps() {
        return false;
    }

    @Override
    public SortedSet<cn.zhengshang.base.Size> getSupportedVideoSize() {
        return null;
    }

    @Override
    public int[] getSupportAWBModes() {
        return new int[0];
    }

    @Override
    public boolean setAspectRatio(AspectRatio ratio) {
        return false;
    }

    @Override
    public AspectRatio getAspectRatio() {
        return mCameraConfig.getAspectRatio();
    }

    @Override
    public boolean getAutoFocus() {
        return false;
    }

    @Override
    public void setAutoFocus(boolean autoFocus) {

    }

    @Override
    public boolean isFlashAvailable() {
        return false;
    }

    @Override
    public int getFlash() {
        return 0;
    }

    @Override
    public void setFlash(int flash) {

    }

    @Override
    public boolean isTorch() {
        return false;
    }

    @Override
    public void setTorch(boolean open) {

    }

    @Override
    public void takePicture() {

    }

    @Override
    public void resetAF(MotionEvent e, boolean lock) {
        focusOnTouch(e.getX(), e.getY());
    }

    @Override
    public void lockAEandAF() {

    }

    @Override
    public void unLockAEandAF() {

    }

    @Override
    public void startRecordingVideo(boolean triggerCallback) {
        try {
            mRecorderController.startRecord();
            mCameraConfig.setRecordingVideo(true);
            mCallback.onVideoRecordingStarted();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            mCallback.onVideoRecordingFailed();
        }
    }

    @Override
    public void stopRecordingVideo(boolean triggerCallback) {
        mRecorderController.stopRecord();
        mCameraConfig.setRecordingVideo(false);
        mCallback.onVideoRecordStoped();
        startPreview();
    }

    @Override
    public void setAWBMode(int mode) {

    }

    @Override
    public void stopSmoothZoom() {

    }

    @Override
    public void startSmoothZoom(float start, float end, long duration) {

    }

    @Override
    public void setDisplayOrientation(int displayOrientation) {
        mDisplayOrientation = displayOrientation;
        mPreview.setDisplayOrientation(displayOrientation);
    }

    @Override
    public float getMaxZoom() {
        return characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
    }

    @Override
    public void scaleZoom(float scale) {
        if (mPreviewBuilder == null) {
            return;
        }

        Rect rect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (rect == null) {
            return;
        }

        mCameraConfig.setRect(CameraUtil.getZoomRect(scale, rect.width(), rect.height()));

        //新的范围比最大值还大,不可以设置,否则手机直接重启.
        if (rect.contains(mCameraConfig.getRect())) {
            mPreviewBuilder.set(CaptureRequest.SCALER_CROP_REGION, mCameraConfig.getRect());

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
        return null;
    }

    @Override
    public void setPicSize(cn.zhengshang.base.Size size) {

    }

    @Override
    public boolean isSupportedManualMode() {
        return false;
    }

    @Override
    public boolean isManualControlAF() {
        return false;
    }

    @Override
    public void setManualMode(boolean manual) {

    }

    @Override
    public void setIsoAuto() {

    }

    @Override
    public boolean isManualAESupported() {
        return false;
    }

    @Override
    public Range<Integer> getAERange() {
        return null;
    }

    @Override
    public void setAEValue(int value) {

    }

    @Override
    public float getAeStep() {
        return 0;
    }

    @Override
    public boolean isManualSecSupported() {
        return false;
    }

    @Override
    public Range<Long> getSecRange() {
        return null;
    }

    @Override
    public void setSecValue(long value) {

    }

    @Override
    public boolean isManualISOSupported() {
        return false;
    }

    @Override
    public Object getISORange() {
        return null;
    }

    @Override
    public void setISOValue(int value) {

    }

    @Override
    public boolean isManualWBSupported() {
        return false;
    }

    @Override
    public Range<Integer> getManualWBRange() {
        return null;
    }

    @Override
    public void setManualWBValue(int value) {

    }

    @Override
    public boolean isManualAFSupported() {
        return false;
    }

    @Override
    public Float getAFMaxValue() {
        return null;
    }

    @Override
    public void setAFValue(float value) {

    }

    @Override
    public boolean isManualWTSupported() {
        return getMaxZoom() > 1;
    }

    @Override
    public boolean isSupportedStabilize() {
        return false;
    }

    @Override
    public boolean getStabilizeEnable() {
        return false;
    }

    @Override
    public void setStabilizeEnable(boolean enable) {

    }

    @Override
    public boolean isCameraOpened() {
        return mCameraDevice != null;
    }

    private void openCamera(int width, int height) {
        Log.d(TAG, "openCamera: brand " + android.os.Build.BRAND);
        Log.d(TAG, "openCamera: model " + android.os.Build.MODEL);
        hwCamera = new HwCamera();
        hwCamera.setInitSuccessCallback(new HwCameraInitSuccessCallback() {
            @Override
            public void onInitSuccess() {

                hwCamera.setHwCameraEngineDieCallBack(new EngineDieCallback());
                hwCamera.getHwCameraManager();
                manager = hwCamera.getHwCameraManager();


                try {
                    Log.d(TAG, "tryAcquire");
                    if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                        throw new RuntimeException("Time out waiting to lock camera opening.");
                    }
                    String cameraId;
                    int cameraIndex = -1;
                    mCameraIdList = manager.getCameraIdList();
                    //get backCameraId
                    for (int i = 0; i < mCameraIdList.length; i++) {
                        characteristics = manager.getCameraCharacteristics(mCameraIdList[i]);
                        if (getFacing() == characteristics.get(CameraCharacteristics.LENS_FACING)) {
                            Byte ret = manager.isFeatureSupported(i, HwCameraMetadata.CharacteristicKey.HUAWEI_IS_120FPS_SUPPORTED);
                            if (ret != 1) {
                                //getActivity().onBackPressed();
                                return;
                            }
                            cameraIndex = i;
                            break;
                        }
                    }
                    assert (characteristics != null && cameraIndex != -1);
                    // Choose the sizes for camera preview and video recording
                    cameraId = mCameraIdList[cameraIndex];
                    StreamConfigurationMap map = characteristics
                            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                    characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
                    Log.d(TAG, "camera faceing : " + characteristics.get(CameraCharacteristics.LENS_FACING));
                    mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    if (map == null) {
                        throw new RuntimeException("Cannot get available preview/video sizes");
                    }

                    mVideoSize = chooseHighSpeedVideoSize(map.getHighSpeedVideoSizes());
                    //mVideoSize = new Size(1280, 720);
                    mPreviewSize = mVideoSize;
                    mPictureSizes = new SizeMap();
                    mPictureSizes.add(new cn.zhengshang.base.Size(mVideoSize.getWidth(), mVideoSize.getHeight()));
                    prepareImageReader();

                    int orientation = mDisplayOrientation;
                    if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        setAspectRatio(AspectRatio.of(mPreviewSize.getWidth(), mPreviewSize.getHeight()));
                    } else {
                        setAspectRatio(AspectRatio.of(mPreviewSize.getHeight(), mPreviewSize.getWidth()));
                    }
                    manager.openCamera(cameraId, mStateCallback, null, HwCameraManager.HIGH_SPEED_MODE);
                } catch (CameraAccessException e) {
                    mCallback.onFailed(CameraError.OPEN_FAILED);
                } catch (NullPointerException e) {
                    mCallback.onFailed(CameraError.OPEN_FAILED);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while trying to lock camera opening.");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        hwCamera.initialize(mContext);

    }

    private void closeCamera() {
        Log.d(TAG, "closeCamera: ");
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            mRecorderController.release();
            if (hwCamera != null) {
                hwCamera.deInitialize();
            }
            mCallback.onCameraClosed();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void startPreview() {
        if (null == mCameraDevice || !mPreview.isReady() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();

            SurfaceTexture texture = mPreview.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            mRecorderController.configForHighSpeed(getFacing(), CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH_SPEED_1080P));

            Surface previewSurface = new Surface(texture);
            mPreviewBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_PREVIEW);
            mPreviewBuilder.addTarget(previewSurface);
            mPreviewBuilder.addTarget(mRecorderController.getSurface());
            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            surfaces.add(mRecorderController.getSurface());

            mCameraDevice.createConstrainedHighSpeedCaptureSession(surfaces,
                    //mCameraDevice.createCaptureSession(surfaces,
                    new HwCameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull HwCameraCaptureSession session) {
                            mPreviewHighSpeedSession = (HwCameraConstrainedHighSpeedCaptureSession) session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull HwCameraCaptureSession session) {

                        }
                    }, mBackgroundHandler);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void capture(HwCameraCaptureSession.CaptureCallback callback, CaptureRequest request) throws CameraAccessException {

        mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

        //for 60fps
        //mPreviewBuilder.set(Key.HUAWEI_HIGH_VIDEO_FPS, VIDEO_60FPS);

        //for 120fps
        mPreviewBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(120, 120));
        List<CaptureRequest> requestList = mPreviewHighSpeedSession.createHighSpeedRequestList(mPreviewBuilder.build());
        mPreviewHighSpeedSession.setRepeatingBurst(requestList, captureCallBack, mBackgroundHandler);
    }

    private void updatePreview() {
        if (null == mCameraDevice) {
            Log.d(TAG, "updatePreview: mCameraDevice null!");
            return;
        }
        Log.d(TAG, "updatePreview: ");
        try {
            capture(captureCallBack, mPreviewBuilder.build());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }


    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }

        if (mPreviewHighSpeedSession != null) {
            mPreviewHighSpeedSession.close();
            mPreviewHighSpeedSession = null;
        }

        mRecorderController.stopRecord();
        CameraUtil.deleteEmptyFIle(getVideoOutputFilePath());
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    class EngineDieCallback implements HwCameraEngineDieRecipient {

        @Override
        public void onEngineDie() {
            Log.e("EngineDieCallback", "engine die ");
            closeCamera();
            mPreviewSession = null;
        }
    }
}
