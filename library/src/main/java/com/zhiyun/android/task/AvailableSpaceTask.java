package com.zhiyun.android.task;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import com.zhiyun.android.base.Constants;
import com.zhiyun.android.cameraview.CameraView;
import com.zhiyun.android.cameraview.R;
import com.zhiyun.android.listener.Callback;
import com.zhiyun.android.util.CameraUtil;

public class AvailableSpaceTask {

    private HandlerThread mWorkThread;
    private Handler mWorkHandler;

    private CameraView mCameraView;
    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            long availableSpace = CameraUtil.getAvailableSpace();
            if (availableSpace <= Constants.LOW_STORAGE_THRESHOLD_BYTES) {
                tintAndStopRecording();
            } else {
                mWorkHandler.postDelayed(this, 1000);
            }
        }
    };
    private Callback mCallback = new Callback() {

        @Override
        public void onCameraOpened(CameraView cameraView) {
            startThread();
        }

        @Override
        public void onVideoRecordingStarted(CameraView cameraView) {
            start();
        }

        @Override
        public void onVideoRecordingStopped(CameraView cameraView) {
            stop();
        }

        @Override
        public void onVideoRecordingFailed(CameraView cameraView) {
            stop();
        }

        @Override
        public void onCameraClosed(CameraView cameraView) {
            quitThread();
        }
    };

    private AvailableSpaceTask(CameraView cameraView) {
        mCameraView = cameraView;
        cameraView.addCallback(mCallback);
    }

    /**
     * 初始化监控剩余空间Task
     *
     * @param cameraView 依赖的CaemraView
     */
    public static void monitorAvailableSpace(CameraView cameraView) {
        new AvailableSpaceTask(cameraView);
    }

    private void startThread() {
        mWorkThread = new HandlerThread("getAvailableSpace");
        mWorkThread.start();
        mWorkHandler = new Handler(mWorkThread.getLooper());
    }

    private void quitThread() {
        if (mWorkThread != null) {
            mWorkThread.quitSafely();
            try {
                mWorkThread.join();
                mWorkThread = null;
                mWorkHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void start() {
        mWorkHandler.post(mRunnable);
    }

    private void stop() {
        mWorkHandler.removeCallbacks(mRunnable);
    }

    private void tintAndStopRecording() {
        new Handler(Looper.getMainLooper())
                .post(new Runnable() {
                    @Override
                    public void run() {

                        mCameraView.stopRecordingVideo();

                        Context context = mCameraView.getContext();
                        CameraUtil.showBlackToast(context,
                                context.getString(R.string.video_reach_size_limit),
                                mCameraView.getPhoneOrientation());
                    }
                });
    }
}