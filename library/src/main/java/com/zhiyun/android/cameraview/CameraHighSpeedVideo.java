package com.zhiyun.android.cameraview;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Range;
import android.view.Surface;

import com.zhiyun.android.base.PreviewImpl;
import com.zhiyun.android.base.Size;
import com.zhiyun.android.listener.CameraCallback;
import com.zhiyun.android.listener.CameraError;

import java.util.ArrayList;
import java.util.List;


@TargetApi(Build.VERSION_CODES.M)
public class CameraHighSpeedVideo extends Camera2Api23 {

    private static final String TAG = "CameraHighSpeedVideo";

    private CameraConstrainedHighSpeedCaptureSession mHighSpeedCaptureSession;


    public CameraHighSpeedVideo(Context context, CameraCallback callback, PreviewImpl preview) {
        super(context, callback, preview);
    }

    @Override
    public void takePicture() {
        //Cannot capture in highSpeed mode.
    }

    @Override
    protected void startCaptureSession() {
        closePreviewSession();

        if (!isCameraOpened() || !mPreview.isReady()) {
            return;
        }
        Size videoSize = getVideoSize();
        mPreview.setBufferSize(videoSize.getWidth(), videoSize.getHeight());
        try {
            mRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);

            mRecorderController.configForHighSpeed(getFacing(), videoSize.getCamcorderProfile());

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(mPreview.getSurface());
            surfaces.add(mRecorderController.getSurface());

            mRequestBuilder.addTarget(mPreview.getSurface());
            mRequestBuilder.addTarget(mRecorderController.getMediaRecorder().getSurface());

            mCamera.createConstrainedHighSpeedCaptureSession(surfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mHighSpeedCaptureSession = (CameraConstrainedHighSpeedCaptureSession) session;

                            setFpsBuilder();
                            updatePreview();
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


    @Override
    protected void closePreviewSession() {
        if (mHighSpeedCaptureSession != null) {
            mHighSpeedCaptureSession.close();
            mHighSpeedCaptureSession = null;
        }
        super.closePreviewSession();
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
