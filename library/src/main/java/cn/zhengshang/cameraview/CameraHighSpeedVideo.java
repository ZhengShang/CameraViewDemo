package cn.zhengshang.cameraview;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.Build;
import android.util.Log;
import android.util.Range;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import cn.zhengshang.base.Constants;
import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.base.Size;
import cn.zhengshang.listener.CameraCallback;
import cn.zhengshang.listener.CameraError;

import static cn.zhengshang.util.CameraUtil.clamp;


@TargetApi(Build.VERSION_CODES.M)
public class CameraHighSpeedVideo extends Camera2Api23 {

    private static final String TAG = "CameraHighSpeedVideo";

    private CameraConstrainedHighSpeedCaptureSession mHighSpeedCaptureSession;
    private Matrix mMatrix = new Matrix();

    public CameraHighSpeedVideo(Context context, CameraCallback callback, PreviewImpl preview) {
        super(context, callback, preview);
    }

    @Override
    public boolean isFlashAvailable() {
        return false;
    }

    @Override
    public void resetAF(MotionEvent event, final boolean lock) {
        double x = event.getX();
        double y = event.getY();
        float focusAreaSize = 200;
        int areaSize = Float.valueOf(focusAreaSize * (float) 1).intValue();
        Log.i("calculateTapArea", "areaSize---> " + areaSize);//300
        Log.i("calculateTapArea", "x---> " + x + ",,,y---> " + y);//对的
        int displayRotation = mPhoneOrientation;

        int realWidth = mPreview.getWidth();
        int realHeight = mPreview.getHeight();
        if (Surface.ROTATION_90 == displayRotation || Surface.ROTATION_270 == displayRotation) {
            realWidth = mPreview.getHeight();
            realHeight = mPreview.getWidth();
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
            y = mPreview.getHeight() - tmp;
        } else if (Surface.ROTATION_270 == displayRotation) {
            tmp = x;
            x = mPreview.getWidth() - y;
            y = tmp;
        }

        CaptureRequest cr = mRequestBuilder.build();
        Rect cropRegion = cr.get(CaptureRequest.SCALER_CROP_REGION);
        if (null == cropRegion) {
            Log.e(TAG, "can't get crop region");
            cropRegion = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        }
        Log.d("calculateTapArea", "crop region " + "left " + cropRegion.left + " right " + cropRegion.right + " top " + cropRegion.top + " bottom " + cropRegion.bottom);
        int cropWidth = cropRegion.width(), cropHeight = cropRegion.height();
        if (mPreview.getHeight() * cropWidth > mPreview.getWidth() * cropHeight) {
            imageScale = cropHeight * 1.0 / mPreview.getHeight();
            verticalOffset = 0;
            horizontalOffset = (cropWidth - imageScale * mPreview.getWidth()) / 2;
        } else {
            imageScale = cropWidth * 1.0 / mPreview.getWidth();
            horizontalOffset = 0;
            verticalOffset = (cropHeight - imageScale * mPreview.getHeight()) / 2;
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

        mRequestBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{new MeteringRectangle(rect, 1000)});
        mRequestBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{new MeteringRectangle(rect, 1000)});
        mRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        mRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
//        mRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);

        try {
            List<CaptureRequest> speedRequestList = mHighSpeedCaptureSession.createHighSpeedRequestList(mRequestBuilder.build());
            mHighSpeedCaptureSession.captureBurst(speedRequestList, new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
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
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void takePicture() {
        mBackgroundHandler.post(() -> {
            @SuppressWarnings("ConstantConditions")
            int sensorOrientation = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_ORIENTATION);
            int orientation = (sensorOrientation + mPhoneOrientation * (getFacing() == Constants.FACING_FRONT ? -1 : 1) +
                    360) % 360;
            mCameraConfig.setOrientation(orientation);
            mCaptureController.capturePreview(
                    mPreview.getFrameBitmap(
                            mCameraConfig.getPhotoConfig().getSize().getWidth(),
                            mCameraConfig.getPhotoConfig().getSize().getHeight()
                    ));
        });
    }

    @Override
    protected void configMediaRecorder() throws Exception {
        Size videoSize = getVideoSize();
        mRecorderController.configForHighSpeed(videoSize.getHighSpeedCamcorderProfile(getFacing()));
    }

    @Override
    protected void closePreviewSession() {
        if (mHighSpeedCaptureSession != null) {
            try {
                mHighSpeedCaptureSession.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mHighSpeedCaptureSession = null;
        }
        super.closePreviewSession();
    }

    @Override
    protected void startCaptureSession() {
        closePreviewSession();

        if (!isCameraOpened() || !mPreview.isReady()) {
            return;
        }
        Size videoSize = getVideoSize();
        mPreview.setBufferSize(videoSize.getWidth(), videoSize.getHeight());
        List<Surface> surfaces = new ArrayList<>();
        Surface previewSurface = mPreview.getSurface();
        try {
            mRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);

            surfaces.add(previewSurface);
            mRequestBuilder.addTarget(previewSurface);

            mCamera.createConstrainedHighSpeedCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mHighSpeedCaptureSession = (CameraConstrainedHighSpeedCaptureSession) session;

                            setFpsBuilder();
                            updatePreview();
                            mCallback.onRequestBuilderCreate();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Failed to configure capture session.");
                            mCallback.onFailed(CameraError.CONFIGURATION_FAILED);
                        }

                        @Override
                        public void onClosed(@NonNull CameraCaptureSession session) {
                            if (mHighSpeedCaptureSession != null && mHighSpeedCaptureSession.equals(session)) {
                                mHighSpeedCaptureSession = null;
                            }
                        }
                    }, mBackgroundHandler);
        } catch (Exception e) {
            Log.e(TAG, "startCaptureSession failed", e);
            mCallback.onFailed(CameraError.OPEN_FAILED);
        }
    }

    private void setFpsBuilder() {
        Range<Integer> fpsRange = new Range<>(getVideoSize().getFps(), getVideoSize().getFps());
        mRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
    }

    @Override
    protected void updatePreview() {
        if (null == mCamera) {
            return;
        }
        if (mHighSpeedCaptureSession == null) {
            return;
        }
        try {


            List<CaptureRequest> mPreviewBuilderBurst = mHighSpeedCaptureSession.
                    createHighSpeedRequestList(mRequestBuilder.build());
            mHighSpeedCaptureSession.setRepeatingBurst(mPreviewBuilderBurst, null, mBackgroundHandler);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
