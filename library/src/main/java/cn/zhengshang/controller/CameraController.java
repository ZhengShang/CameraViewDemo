package cn.zhengshang.controller;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import cn.zhengshang.base.ICamera;
import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.cameraview.Camera1;
import cn.zhengshang.cameraview.Camera2;
import cn.zhengshang.cameraview.Camera2Api23;
import cn.zhengshang.config.SpecialDevicesList;
import cn.zhengshang.listener.CameraCallback;

public class CameraController {

    private static final String TAG = "CameraController";
    private int mStartApi;
    private Class<? extends ICamera> mAutoClz;

    public CameraController(int startApi) {
        mStartApi = startApi;
    }

    /**
     * 自动选择相机.
     * @param context Context
     * @return 返回对应的相机实例Class
     */
    public Class<? extends ICamera> autoInstanceCamera(Context context) throws RuntimeException {

        if (mStartApi == 0) {
            if (mAutoClz != null) {
                return mAutoClz;
            }
            mAutoClz = autoChooseCamera(context);
            return mAutoClz;
        } else if (mStartApi == 1) {
            return Camera1.class;
        } else {
            return Camera2.class;
        }
    }

    @NonNull
    private Class<? extends ICamera> autoChooseCamera(Context context) {
        if (SpecialDevicesList.isForceUseCamera1()) {
            return Camera1.class;
        }
        int level = getBackFacingCameraLevel(context);
        if (level == -1) {
            throw new RuntimeException("获取相机等级失败");
        }
        if (level == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
                || level == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
                || level == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3) {
//            if (PhoneHelper.isSupportSamsangCamera(context)) {
//                return SamSangCamera.class;
//            }
            if (!SpecialDevicesList.banHsv()
                    && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return Camera2Api23.class;
            }
            return Camera2.class;
        } else if (level == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
            return Camera1.class;
        } else {
            throw new RuntimeException("获取相机等级失败");
        }
    }

    public ICamera newInstanceCamera(Class<? extends ICamera> clz,
                                     Context context,
                                     CameraCallback callback,
                                     PreviewImpl preview) {
        try {
            Log.v(TAG, "Instance camera class = " + clz.getSimpleName());
            return clz.getDeclaredConstructor(Context.class,
                    CameraCallback.class,
                    PreviewImpl.class)
                    .newInstance(context, callback, preview);
        } catch (Exception e) {
            return null;
        }
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
     * @param context Context
     * @return true支持, false不支持
     */
    public boolean isHwSlowMotionSupported(Context context) {
//        byte retcode = HwCamera.isDeviceSupported(context);
//        boolean sdkSupport = retcode == HwCamera.HWCAMERA_SDK_AVAILABLE;
//        boolean spSupport = SPConfig.getInstance().isSupportHwSuperSlow();
//        return sdkSupport && spSupport;

        //暂时都不支持
        return false;
    }
}
