package cn.zhengshang.cameraview;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Range;

import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.base.Size;
import cn.zhengshang.base.SizeMap;
import cn.zhengshang.listener.CameraCallback;


@TargetApi(23)
public class Camera2Api23 extends Camera2 {

    private static final String TAG = "Camera2Api23";

    public Camera2Api23(Context context, CameraCallback callback, PreviewImpl preview) {
        super(context, callback, preview);
    }

    @Override
    protected void collectPictureSizes(SizeMap sizes, StreamConfigurationMap map) {
        // Try to get hi-res output sizes
        android.util.Size[] outputSizes = map.getHighResolutionOutputSizes(ImageFormat.JPEG);
        if (outputSizes != null) {
            for (android.util.Size size : outputSizes) {
                sizes.add(new Size(size.getWidth(), size.getHeight()));
            }
        }
        if (sizes.isEmpty()) {
            super.collectPictureSizes(sizes, map);
        }
    }

    @Override
    protected void collectVideoSizes(SizeMap sizes, StreamConfigurationMap map) {
        super.collectVideoSizes(sizes, map);

        for (Range<Integer> fpsRange : map.getHighSpeedVideoFpsRanges()) {
            if (fpsRange.getLower().equals(fpsRange.getUpper())) {
                for (android.util.Size size : map.getHighSpeedVideoSizesFor(fpsRange)) {
                    Size videoSize = new Size(size.getWidth(), size.getHeight());
                    if (videoSize.hasHighSpeedCamcorder(getFacing())) {
                        if (fpsRange.getUpper() > 60) {
                            //最高只支持到60fps
                            continue;
                        }
                        videoSize.setFps(fpsRange.getUpper());
                        sizes.add(videoSize);
                        Log.d("Camera2Api23", "Support HighSpeed video recording for " + videoSize.toString());
                    }
                }
            }
        }
    }
}
