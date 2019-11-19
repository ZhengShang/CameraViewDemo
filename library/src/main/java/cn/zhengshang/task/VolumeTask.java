package cn.zhengshang.task;

import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;

import cn.zhengshang.cameraview.CameraView;
import cn.zhengshang.listener.Callback;
import cn.zhengshang.listener.OnVolumeListener;

public class VolumeTask {

    private HandlerThread mWorkThread;
    private Handler mWorkHandler;


    private MediaRecorder mMediaRecorder;

    private OnVolumeListener mOnVolumeListener;

    private Callback mCallback = new Callback() {

        @Override
        public void onCameraOpened(CameraView cameraView) {
            startThread();
        }

        @Override
        public void onVideoRecordingStarted(CameraView cameraView) {
            mMediaRecorder = cameraView.getMediaRecorder();
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

    public void monitorVolume(CameraView cameraView) {
        cameraView.addCallback(mCallback);
    }

    public void setOnVolumeListener(OnVolumeListener onVolumeListener) {
        mOnVolumeListener = onVolumeListener;
    }

    private void startThread() {
        mWorkThread = new HandlerThread("monitorVolume");
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

    public void start() {
        mWorkHandler.post(mRunnable);
    }

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            if (mOnVolumeListener != null) {

                double db = 0;
                if (mMediaRecorder != null) {
                    db = 20 * Math.log10(mMediaRecorder.getMaxAmplitude() / 0.1f);
                }

                mOnVolumeListener.onRecordingVolume((int) db);
            }

            mWorkHandler.postDelayed(this, 1000);
        }
    };

    public void stop() {
        mWorkHandler.removeCallbacks(mRunnable);
    }
}