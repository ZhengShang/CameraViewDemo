package cn.zhengshang.controller;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.huawei.emui.himedia.camera.HwCamera;

import cn.zhengshang.base.CameraViewImpl;
import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.cameraview.Camera1;
import cn.zhengshang.cameraview.Camera2;
import cn.zhengshang.cameraview.Camera2Api23;
import cn.zhengshang.cameraview.CameraHighSpeedVideo;
import cn.zhengshang.listener.CameraCallback;

public class CameraController {

    private int mStartApi;

    public CameraController(int startApi) {
        mStartApi = startApi;
    }

    /**
     * 自动选择相机.
     *
     * @param context  Context
     * @param callback 回调
     * @param preview  预览
     * @return 返回对应的相机实例
     */
    public CameraViewImpl openCamera(Context context,
                                     CameraCallback callback,
                                     PreviewImpl preview) throws RuntimeException {

        if (mStartApi == 0) {
            return autoChooseCamera(context, callback, preview);
        } else if (mStartApi == 1) {
            return new Camera1(context, callback, preview);
        } else {
            return new Camera2(context, callback, preview);
        }
    }

    @NonNull
    private CameraViewImpl autoChooseCamera(Context context, CameraCallback callback, PreviewImpl preview) {
        int level = getBackFacingCameraLevel(context);
        if (level == -1) {
            throw new RuntimeException("获取相机等级失败");
        }
        if (level == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
                || level == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                || level == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3) {
//            if (isHwSlowMotionSupported(context)) {
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                    return new HwCameraVideo(callback, preview, context);
//                }
//            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return new Camera2Api23(context, callback, preview);
            }
            return new Camera2(context, callback, preview);
        } else if (level == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return new Camera1(context, callback, preview);
        } else {
            throw new RuntimeException("获取相机等级失败");
        }
    }

    /**
     * 切换到高帧率录制相机
     */
    public CameraViewImpl switchToHighSpeedCamera(Context context,
                                                  CameraCallback callback,
                                                  PreviewImpl preview) {
        return new CameraHighSpeedVideo(context, callback, preview);
    }

    private int getBackFacingCameraLevel(Context context) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cameraManager == null) {
            return -1;
        }
        try {
            String[] ids = cameraManager.getCameraIdList();
            if (ids.length == 0) {
                return -1;
            }
            for (String id : ids) {
                //只获取后置摄像头
                if (!TextUtils.equals(id, String.valueOf(CameraMetadata.LENS_FACING_BACK))) {
                    continue;
                }
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer level = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
                if (level == null) {
                    return -1;
                } else {
                    return level;
                }
            }
        } catch (CameraAccessException e) {
            return -1;
        }
        return -1;
    }

    /**
     * 是否支持华为慢动作
     *
     * @param context Context
     * @return true支持, false不支持
     */
    private boolean isHwSlowMotionSupported(Context context) {
        byte retcode = HwCamera.isDeviceSupported(context);
        return retcode == HwCamera.HWCAMERA_SDK_AVAILABLE;
    }
}
