package cn.zhengshang.controller;


import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.RggbChannelVector;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import cn.zhengshang.base.Constants;
import cn.zhengshang.base.Size;
import cn.zhengshang.config.CameraConfig;
import cn.zhengshang.config.ManualConfig;
import cn.zhengshang.config.PhotoConfig;
import cn.zhengshang.listener.CameraCallback;
import cn.zhengshang.listener.OnCaptureImageCallback;
import cn.zhengshang.util.CameraUtil;

import static cn.zhengshang.base.Constants.ONE_SECOND;
import static cn.zhengshang.controller.SoundController.SOUND_ID_CLICK;

public class CaptureController {

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
                                    final CameraCaptureSession session) {
        if (camera == null || session == null) {
            return;
        }
        try {
            CaptureRequest.Builder builder;
            if (mCameraConfig.isRecordingVideo()) {
                builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            } else {
                builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            }

            builder.addTarget(mImageReader.getSurface());

            ManualConfig manualConfig = mCameraConfig.getManualConfig();
            PhotoConfig photoConfig = mCameraConfig.getPhotoConfig();

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

            if (photoConfig.isStabilization()) {
                //设置防抖
                builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON);
            }

            if (photoConfig.isHdr()) {
                builder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_HDR);
            }

            if (mCameraConfig.getFlash() == Constants.FLASH_ON) {
                builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
                builder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
            }

            builder.set(CaptureRequest.JPEG_ORIENTATION, mCameraConfig.getOrientation());
//            SoundController.getInstance().playSound(SOUND_ID_CLICK);
            // Stop preview and capture a still picture.
//            session.stopRepeating();
            session.capture(builder.build(), null, mBackgroundHandler);

        } catch (CameraAccessException | IllegalArgumentException e) {
            Log.e(TAG, "Cannot capture a still picture.", e);
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
