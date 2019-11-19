package cn.zhengshang.listener;

public interface CameraCallback {
    void onFailed(CameraError error);

    void onCameraOpened();

    void onCameraClosed();

    void onRequestBuilderCreate();

    void onPictureTaken(byte[] data);

    void onVideoRecordingStarted();

    void onVideoRecordStoped();

    void onVideoRecordingFailed();
}
