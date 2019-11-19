package cn.zhengshang.controller;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.RggbChannelVector;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import cn.zhengshang.base.Constants;
import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.config.CameraConfig;
import cn.zhengshang.listener.CameraCallback;
import cn.zhengshang.util.CameraUtil;

import static cn.zhengshang.controller.SoundController.SOUND_ID_STOP;

public class VideoController {

    private Context mContext;
    private Handler mBackgroundHandler;
    private CameraConfig mCameraConfig;
    private CameraCallback mCallback;
    private MediaRecorderController mRecorderController;


    public VideoController(Context context,
                           MediaRecorderController recorderController,
                           CameraConfig cameraConfig,
                           Handler backgroundHandler,
                           CameraCallback callback) {
        mContext = context;
        mRecorderController = recorderController;
        mCameraConfig = cameraConfig;
        mBackgroundHandler = backgroundHandler;
        mCallback = callback;
    }

    /**
     * 开始录制视频
     *
     * @param camera           CameraDevice相机对象
     * @param preview          相机预览对象
     * @param phoneOrientation 当前的手机方向
     * @param triggerCallback  true触发录制回调,false不触发
     * @param recordResult     录制结果
     */
    public void startRecording(final CameraDevice camera,
                               final PreviewImpl preview,
                               final int phoneOrientation,
                               final boolean triggerCallback,
                               final Result recordResult) {

        if (null == camera || !preview.isReady()) {
            return;
        }

        if (recordResult == null) {
            throw new IllegalArgumentException("录制回调接口必须实现");
        }

        mBackgroundHandler.post(new Runnable() {
            @Override
            public void run() {

                try {

                    //Check availableSpace
                    if (CameraUtil.lowAvailableSpace(mContext, phoneOrientation)) {
                        return;
                    }

                    mRecorderController.configForCamera2();

                    final CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
                    builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT,
                            CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);

                    //auto white-balance
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, mCameraConfig.getAwb());

                    builder.set(CaptureRequest.CONTROL_AE_LOCK, mCameraConfig.isAeLock());

                    //屏幕的缩放
                    builder.set(CaptureRequest.SCALER_CROP_REGION, mCameraConfig.getRect());
                    //ae
                    builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, mCameraConfig.getManualConfig().getAe());

                    if (mCameraConfig.getManualConfig().isManual()) {
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
                            builder.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF);
                            builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
                        }
                    }


                    if (mCameraConfig.getVideoConfig().isStabilization()) {
                        //设置防抖
                        builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON);
                    }

                    List<Surface> surfaces = new ArrayList<>();

                    // Set up Texture for the camera preview
                    Surface previewSurface;
                    SurfaceTexture previewTexture = preview.getSurfaceTexture();
                    if (previewTexture == null) {
                        previewSurface = preview.getSurface();
                    } else {
                        previewSurface = new Surface(previewTexture);
                    }
                    surfaces.add(previewSurface);
                    builder.addTarget(previewSurface);

                    // Set up Surface for the MediaRecorder
                    Surface recorderSurface = mRecorderController.getSurface();
                    surfaces.add(recorderSurface);
                    builder.addTarget(recorderSurface);

                    // Set up Surface for the still picture
//            Surface stillPictureSurface = mImageReader.getSurface();
//            surfaces.add(stillPictureSurface);
//            mPreviewRequestBuilder.addTarget(stillPictureSurface);

                    camera.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            repeatRequest(cameraCaptureSession, builder.build());

                            mCameraConfig.setRecordingVideo(true);
                            try {
                                mRecorderController.startRecord();
                                if (triggerCallback) {
                                    mCallback.onVideoRecordingStarted();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                                mCameraConfig.setRecordingVideo(false);
                                Log.e("Camera2", "onConfigured:  the camera is already in use by another app", e);
                                if (triggerCallback) {
                                    mCallback.onVideoRecordingFailed();
                                }
                                //startCaptureSession();
                            }

                            recordResult.onCoonConfigured(cameraCaptureSession, builder);

                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            if (triggerCallback) {
                                mCallback.onVideoRecordingFailed();
                            }
                            recordResult.onFailed();
                        }
                    }, mBackgroundHandler);
                } catch (Exception e) {
                    if (triggerCallback) {
                        mCallback.onVideoRecordingFailed();
                    }
                    recordResult.onFailed();
                }


            }
        });
    }

    /**
     * 停止录制视频
     *
     * @param triggerCallback true触发回调, false不触发
     */
    public void stopRecording(boolean triggerCallback) {
        try {
            mCameraConfig.setRecordingVideo(false);

            // Stop recording
            mRecorderController.stopRecord();

            if (triggerCallback) {
                SoundController.getInstance().playSound(SOUND_ID_STOP);
                mCallback.onVideoRecordStoped();
            } else {
                CameraUtil.addToMediaStore(mContext, mCameraConfig.getVideoConfig().getVideoAbsolutePath());
            }

            BroadcastController.sendRecordingStopAction(mContext);
        } catch (Exception e) {
            if (triggerCallback) {
                mCallback.onVideoRecordingFailed();
            }
            Log.e("Camera2", "stopRecordingVideo failed. ", e);
        }
    }

    private void repeatRequest(CameraCaptureSession session, CaptureRequest request) {
        try {
            session.setRepeatingRequest(request, null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public interface Result {
        /**
         * 配置成功, 需要更新session和builder引用
         */
        void onCoonConfigured(CameraCaptureSession session, CaptureRequest.Builder builder);

        /**
         * 录制失败, 需要重新打开captureSession
         */
        void onFailed();
    }
}
