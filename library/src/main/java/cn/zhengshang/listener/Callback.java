package cn.zhengshang.listener;

import cn.zhengshang.cameraview.CameraView;

public class Callback {

    public void onFailed(CameraError error) {

    }

    /**
     * Called when camera is opened.
     *
     * @param cameraView The associated {@link CameraView}.
     */
    public void onCameraOpened(CameraView cameraView) {
    }

    /**
     * Called when camera is closed.
     *
     * @param cameraView The associated {@link CameraView}.
     */
    public void onCameraClosed(CameraView cameraView) {
    }

    public void onRequestBuilderCreate(CameraView cameraView) {

    }

    /**
     * Called when a picture is taken.
     *
     * @param cameraView The associated {@link CameraView}.
     * @param data       JPEG data.
     */
    public void onPictureTaken(CameraView cameraView, byte[] data) {
    }

    public void onVideoRecordingStarted(CameraView cameraView) {

    }

    public void onVideoRecordingStopped(CameraView cameraView) {

    }

    public void onVideoRecordingFailed(CameraView cameraView) {

    }
}
