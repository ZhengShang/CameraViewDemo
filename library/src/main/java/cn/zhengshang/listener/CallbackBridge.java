package cn.zhengshang.listener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import cn.zhengshang.cameraview.CameraView;

public class CallbackBridge implements CameraCallback {
    private final ArrayList<Callback> mCallbacks = new ArrayList<>();

    private boolean mRequestLayoutOnOpen;

    protected WeakReference<CameraView> cameraView;

    public CallbackBridge(CameraView cameraView) {
        this.cameraView = new WeakReference<>(cameraView);
    }

    public CallbackBridge() {
    }

    public void add(Callback callback) {
        mCallbacks.add(callback);
    }

    public void remove(Callback callback) {
        mCallbacks.remove(callback);
    }

    @Override
    public void onFailed(CameraError error) {
        for (Callback callback : mCallbacks) {
            callback.onFailed(error);
        }
    }

    @Override
    public void onCameraOpened() {
        if (mRequestLayoutOnOpen) {
            mRequestLayoutOnOpen = false;
            if (cameraView.get() != null) {
                cameraView.get().post(() -> cameraView.get().requestLayout());
            }
        }
        for (Callback callback : mCallbacks) {
            callback.onCameraOpened(cameraView.get());
        }
    }

    @Override
    public void onCameraClosed() {
        for (Callback callback : mCallbacks) {
            callback.onCameraClosed(cameraView.get());
        }
    }

    @Override
    public void onRequestBuilderCreate() {
        for (Callback callback : mCallbacks) {
            callback.onRequestBuilderCreate(cameraView.get());
        }
    }

    @Override
    public void onPictureTaken(byte[] data) {
        for (Callback callback : mCallbacks) {
            callback.onPictureTaken(cameraView.get(), data);
        }
    }

    @Override
    public void onVideoRecordingStarted() {
        for (Callback callback : mCallbacks) {
            callback.onVideoRecordingStarted(cameraView.get());
        }
    }

    @Override
    public void onVideoRecordStoped() {
        for (Callback callback : mCallbacks) {
            callback.onVideoRecordingStopped(cameraView.get());
        }
    }

    @Override
    public void onVideoRecordingFailed() {
        for (Callback callback : mCallbacks) {
            callback.onVideoRecordingFailed(cameraView.get());
        }
    }

    public ArrayList<Callback> getCallbacks() {
        return mCallbacks;
    }

    public void reserveRequestLayoutOnOpen() {
        mRequestLayoutOnOpen = true;
    }

    public void clear() {
        mCallbacks.clear();
    }
}
