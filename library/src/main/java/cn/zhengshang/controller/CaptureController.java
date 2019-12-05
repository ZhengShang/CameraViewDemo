package cn.zhengshang.controller;


import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.RggbChannelVector;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import cn.zhengshang.base.Constants;
import cn.zhengshang.base.Size;
import cn.zhengshang.config.CameraConfig;
import cn.zhengshang.config.ManualConfig;
import cn.zhengshang.listener.CameraCallback;
import cn.zhengshang.listener.OnCaptureImageCallback;
import cn.zhengshang.util.CameraUtil;

import static cn.zhengshang.base.Constants.FLASH_TORCH;
import static cn.zhengshang.base.Constants.ONE_SECOND;
import static cn.zhengshang.controller.SoundController.SOUND_ID_CLICK;

public class CaptureController {

    private static final int STATE_NORMAL = 0;
    private static final int STATE_WAITTING_PRECAPTURE_START = 1;
    private static final int STATE_WAITTING_PRECAPTURE_DONE = 2;

    private static final String TAG = "CaptureController";
    private int mHdrImageIndex;
    private List<Bitmap> mHdrBitmaps;
    private CameraConfig mCameraConfig;
    private ImageReader mImageReader;
    private Handler mBackgroundHandler;
    private HDRProcessor mHDRProcessor;
    private Context mContext;
    private OnCaptureImageCallback mOnCaptureImageCallback;
    private CameraCallback mCallback;
    private final Object mLock = new Object();
    private Matrix mMatrix = new Matrix();
    private Integer mLastCaptureAeState;
    private int mState = STATE_NORMAL;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest.Builder mOriBuilder;
    private Runnable mProcessCompleteTask;
    private boolean mNoTriggerFlash;


    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    try (Image image = reader.acquireNextImage()) {
                        Image.Plane[] planes = image.getPlanes();
                        if (planes.length <= 0) {
                            return;
                        }
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

                        if (mCameraConfig.getPhotoConfig().isHdr() &&
                                Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                            if (mHdrBitmaps == null) {
                                mHdrBitmaps = new ArrayList<>();
                            }
                            mHdrBitmaps.add(bitmap);

                            //最后获取到最后一张图，进行合成
                            if (mHdrImageIndex == 2) {
                                if (mHDRProcessor == null)
                                    mHDRProcessor = new HDRProcessor(mContext);
                                mHDRProcessor.processHDR(mHdrBitmaps, true, null, true);
                                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                mHdrBitmaps.get(0).compress(Bitmap.CompressFormat.JPEG, 100, stream);
                                mCallback.onPictureTaken(stream.toByteArray());
                            }
                            mHdrImageIndex++;
                        } else {
                            if (mCameraConfig.getPhotoConfig().isBurst()) {
                                SoundController.getInstance().playSound(SOUND_ID_CLICK);
                            }
                            mCallback.onPictureTaken(data);
                            BroadcastController.sendTakePhotoAction(mContext);
                            Log.d("Camera2", "onImageAvailable: send image");
                        }
                    } catch (OutOfMemoryError error) {
                        error.printStackTrace();
                        System.gc();
                    }
                }
            };

    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {

            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
            if (aeState == null) {
                mLastCaptureAeState = null;
            } else if (!aeState.equals(mLastCaptureAeState)) {
                mLastCaptureAeState = aeState;
            }

            if (mState == STATE_WAITTING_PRECAPTURE_START) {
                mState = STATE_WAITTING_PRECAPTURE_DONE;
            } else if (mState == STATE_WAITTING_PRECAPTURE_DONE) {
                mState = STATE_NORMAL;
                takeRealCapture();
            }

            processComplete(request);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.e("CaptureController", "onCaptureFailed: [session, request, failure] = ");
        }
    };

    public CaptureController(CameraConfig cameraConfig,
                             Handler backgroundHandler,
                             Context context,
                             CameraCallback callback) {
        mCameraConfig = cameraConfig;
        mBackgroundHandler = backgroundHandler;
        mContext = context;
        mCallback = callback;
    }

    /**
     * 设置当前OnCaptureImageCallback接口.
     * 当此接口不为空时,表示客户端不希望接收到拍摄的数据流,并且也不需要拍摄动画
     */
    public CaptureController setOnCaptureImageCallback(OnCaptureImageCallback onCaptureImageCallback) {
        mOnCaptureImageCallback = onCaptureImageCallback;
        return this;
    }

    /**
     * @return 返回当前的ImageReader, 在相机打开预览的时候用到
     */
    public ImageReader getImageReader() {
        return mImageReader;
    }

    /**
     * 重新准备ImageReader.
     * 每当需要拍摄不同大小的图片时,都需要重新设置ImageReader
     */
    public void prepareImageReader() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        Size picSize = mCameraConfig.getPhotoConfig().getSize();
        mImageReader = ImageReader.newInstance(picSize.getWidth(), picSize.getHeight(),
                mCameraConfig.getPhotoConfig().getFormat(), 1);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
    }


    /**
     * 拍摄普通静态图片
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
    public void captureStillPicture(CameraDevice camera,
                                    final CameraCaptureSession session,
                                    Surface surface,
                                    CaptureRequest.Builder requestBuilder,
                                    Runnable repeatingTask) {
        if (session == null || camera == null) {
            return;
        }
        mCameraDevice = camera;
        mCaptureSession = session;
        mOriBuilder = requestBuilder;
        mProcessCompleteTask = repeatingTask;
        mState = STATE_NORMAL;

        if (mCameraConfig.getFlash() == Constants.FLASH_OFF
                || mCameraConfig.getFlash() == FLASH_TORCH) {
            takeRealCapture();
        } else {
            boolean needFlash = mLastCaptureAeState != null && mLastCaptureAeState != CaptureResult.CONTROL_AE_STATE_CONVERGED;
            mNoTriggerFlash = mCameraConfig.getFlash() == Constants.FLASH_AUTO && !needFlash || mCameraConfig.isRecordingVideo();
            if (mNoTriggerFlash) {
                takeRealCapture();
            } else {
                runPreCapture(surface);
            }
        }
    }

    private void runPreCapture(Surface previewSurface) {
        if (mCaptureSession == null || mCameraDevice == null) {
            return;
        }
        try {
            final CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);

            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_IDLE);

            mState = STATE_WAITTING_PRECAPTURE_START;
            builder.addTarget(previewSurface);

            updateFlash(builder);
            synchronized (mLock) {
                mCaptureSession.capture(builder.build(), mPreviewCaptureCallback, mBackgroundHandler);
                mCaptureSession.setRepeatingRequest(builder.build(), mPreviewCaptureCallback, mBackgroundHandler);

                // now set precapture
                builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                mCaptureSession.capture(builder.build(), mPreviewCaptureCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void takeRealCapture() {
        if (mCaptureSession == null || mCameraDevice == null) {
            return;
        }
        try {
            final CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(
                    mCameraConfig.isRecordingVideo()
                            ? CameraDevice.TEMPLATE_VIDEO_SNAPSHOT
                            : CameraDevice.TEMPLATE_STILL_CAPTURE);

            CaptureRequest oriRequest = mOriBuilder.build();
            for (CaptureRequest.Key key : oriRequest.getKeys()) {
                Object value = mOriBuilder.get(key);
                if (value == null) {
                    continue;
                }
                builder.set(key, value);
            }

            builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT,
                    mCameraConfig.isRecordingVideo()
                            ? CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_SNAPSHOT
                            : CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
            if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                builder.set(CaptureRequest.CONTROL_ENABLE_ZSL, true);
            }
            updateFlash(builder);
            builder.set(CaptureRequest.JPEG_ORIENTATION, mCameraConfig.getOrientation());
            builder.setTag("CAPTURE");
            builder.addTarget(mImageReader.getSurface());
            mState = STATE_NORMAL;
            synchronized (mLock) {
                if (!mNoTriggerFlash) {
                    mCaptureSession.stopRepeating();
                }
                mCaptureSession.capture(builder.build(), mPreviewCaptureCallback, mBackgroundHandler);
            }
            SoundController.getInstance().playSound(SOUND_ID_CLICK);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

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

    private void processComplete(CaptureRequest request) {
        Object tag = request.getTag();
        if (tag == null || !TextUtils.equals("CAPTURE", (CharSequence) tag)) {
            return;
        }
        if (mNoTriggerFlash) {
            return;
        }
        if (mOriBuilder == null) {
            return;
        }
        if (mProcessCompleteTask != null) {
            mProcessCompleteTask.run();
        }
    }

    /**
     * 拍摄HDR图片, 采用OpenCamera算法,一次拍摄3张不同曝光度的照片进行合成,生成为一张图片
     */
    public void captureHdrPicture(CameraDevice camera,
                                  CameraCaptureSession session) {
        if (camera == null || session == null) {
            return;
        }
        mHdrImageIndex = 0;
        if (mHdrBitmaps != null) {
            mHdrBitmaps.clear();
        }

        try {
            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
            builder.addTarget(mImageReader.getSurface());


            ManualConfig manualConfig = mCameraConfig.getManualConfig();

            builder.set(CaptureRequest.SCALER_CROP_REGION, mCameraConfig.getRect());
            if (!mCameraConfig.isAutoFocus()) {
                builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
                builder.set(CaptureRequest.LENS_FOCUS_DISTANCE, manualConfig.getAf());
            }
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, manualConfig.getAe());

            if (manualConfig.isManual()) {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF);
                //wb
                int mannualWb = manualConfig.getWb();
                if (mannualWb != Constants.DEF_MANUAL_WB) {
                    RggbChannelVector rggbChannelVector = CameraUtil.colorTemperature(mannualWb);
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
                    builder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_TRANSFORM_MATRIX);
                    builder.set(CaptureRequest.COLOR_CORRECTION_GAINS, rggbChannelVector);
                } else {
                    builder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO);
                }
                //sec
                long sec = manualConfig.getSec();
                if (sec != Constants.DEF_MANUAL_SEC) {
//                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                    builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, sec);
                }
                //iso
                int iso = manualConfig.getIso();
                if (iso != Constants.DEF_MANUAL_ISO) {
//                    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                    builder.set(CaptureRequest.SENSOR_SENSITIVITY, iso);
                }
            } else {
                builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                builder.set(CaptureRequest.CONTROL_AWB_MODE, mCameraConfig.getAwb());
            }

            //设置防抖
            if (mCameraConfig.getPhotoConfig().isStabilization()) {
                builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON);
            }

            builder.set(CaptureRequest.JPEG_ORIENTATION, mCameraConfig.getOrientation());

            List<CaptureRequest> list = new ArrayList<>();

            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY, 800);
            builder.set(CaptureRequest.SENSOR_FRAME_DURATION, ONE_SECOND / 30);

            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ONE_SECOND / 200);
            list.add(builder.build());
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ONE_SECOND / 24);
//            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, mSec);
            list.add(builder.build());
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, ONE_SECOND / 5);
            list.add(builder.build());

            SoundController.getInstance().playSound(SOUND_ID_CLICK);

            session.stopRepeating();
            session.captureBurst(list, null, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void capturePreview(Bitmap frameBitmap) {
        try {
            BroadcastController.sendTakePhotoAction(mContext);
            SoundController.getInstance().playSound(SOUND_ID_CLICK);

            mMatrix.reset();
            mMatrix.postRotate(mCameraConfig.getOrientation());
            Bitmap rotatedBitmap = Bitmap.createBitmap(frameBitmap, 0, 0, frameBitmap.getWidth(), frameBitmap.getHeight(), mMatrix, true);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
            byte[] byteArray = stream.toByteArray();
            frameBitmap.recycle();
            mCallback.onPictureTaken(byteArray);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void captureCamera2Burst(final CaptureRequest.Builder builder,
                                    final CameraCaptureSession session) {
        try {
            builder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
            builder.addTarget(mImageReader.getSurface());
            builder.set(CaptureRequest.JPEG_ORIENTATION, mCameraConfig.getOrientation());
            List<CaptureRequest> requests = new ArrayList<>();
            requests.add(builder.build());
            session.setRepeatingBurst(requests, null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 资源释放
     */
    public void release() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mHDRProcessor != null) {
            mHDRProcessor.onDestroy();
            mHDRProcessor = null;
        }
    }
}
