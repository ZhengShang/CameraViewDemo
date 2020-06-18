package cn.zhengshang.base;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.View;

import java.util.List;

import cn.zhengshang.cameraview.R;
import cn.zhengshang.config.CameraConfig;
import cn.zhengshang.config.SPConfig;
import cn.zhengshang.controller.CaptureController;
import cn.zhengshang.controller.LensController;
import cn.zhengshang.controller.MediaRecorderController;
import cn.zhengshang.listener.CameraCallback;
import cn.zhengshang.listener.OnAeChangeListener;
import cn.zhengshang.listener.OnCaptureImageCallback;
import cn.zhengshang.listener.OnFaceDetectListener;
import cn.zhengshang.listener.OnManualValueListener;
import cn.zhengshang.listener.OnVideoOutputFileListener;
import cn.zhengshang.util.CameraUtil;

public abstract class CameraViewImpl implements ICamera {

    /**
     * 使用 static 来持久化当前引用 , 避免被重建
     */
    protected static CameraConfig mCameraConfig = new CameraConfig();

    protected final CameraCallback mCallback;
    protected OnManualValueListener mOnManualValueListener;
    protected OnAeChangeListener mOnAeChangeListener;
    protected OnCaptureImageCallback mOnCaptureImageCallback;
    private static OnVideoOutputFileListener mOnVideoOutputFileListener;
    protected final PreviewImpl mPreview;
    protected Context mContext;
    protected OnFaceDetectListener mOnFaceDetectListener;
    protected Handler mBackgroundHandler;
    /**
     * 手机当前的方向.
     */
    protected int mPhoneOrientation;
    protected volatile boolean mRestartForRecord;
    private HandlerThread mBackgroundThread;
    private long mAvailableSpace;
    private LensController mLensController;
    protected CaptureController mCaptureController;
    protected MediaRecorderController mRecorderController;


    public CameraViewImpl(Context context, CameraCallback callback, PreviewImpl preview) {
        mContext = context;
        mCallback = callback;
        mPreview = preview;
        mLensController = new LensController(this);
    }

    @Override
    public void start() {
        startBackgroundThread();
        mRecorderController = new MediaRecorderController(this, mCameraConfig);
    }

    @Override
    public void stop() {
        stopBackgroundThread();
        if (mRecorderController != null) {
            mRecorderController.release();
        }
    }

    public View getView() {
        return mPreview.getView();
    }

    public float getFpsWithSize(android.util.Size size) {
        return Constants.DEF_FPS;
    }

    @Override
    public int getFacing() {
        return mCameraConfig.getFacing();
    }

    @Override
    public AspectRatio getAspectRatio() {
        return mCameraConfig.getAspectRatio();
    }

    @Override
    public boolean getAutoFocus() {
        return mCameraConfig.isAutoFocus();
    }

    /**
     * 手机剩余空间是否不够.空间不够的话,无法进行视频录制.
     * @return TRUE不够, FALSE够.
     */
    protected boolean lowAvailableSpace() {
        long availableSpace = CameraUtil.getAvailableSpace();
        if (availableSpace <= Constants.LOW_STORAGE_THRESHOLD_BYTES) {
            Context context = getView().getContext();
            CameraUtil.showBlackToast(context, context.getString(R.string.spaceIsLow_content), mPhoneOrientation);
            return true;
        }
        return false;
    }

    @Override
    public boolean isSupportedStabilize() {
        return mCameraConfig.getPhotoConfig().isStabilization() ||
                mCameraConfig.getVideoConfig().isStabilization();
    }

    protected int caleVideoOrientation(int sensorOrientation) {
        boolean isFacing = (getFacing() == Constants.FACING_FRONT);
        int orientation = (sensorOrientation + mPhoneOrientation * (isFacing ? -1 : 1) + 360) % 360;
        if (isFacing) {
//            orientation = FROUT_ORIENTATIONS.get(orientation);
            orientation = (360 - orientation) % 360;
        }

        return orientation;
        //distinct
    }

    @Override
    public boolean getStabilizeEnable() {
        return mCameraConfig.isStabilization();
    }

    @Override
    public void setStabilizeEnable(boolean enable) {
        mCameraConfig.setStabilization(enable);
    }

    @Override
    public int getFlash() {
        return mCameraConfig.getFlash();
    }

    public abstract void setTorch(boolean open);

    public abstract boolean isTorch();

    public abstract void takePicture();

    public abstract void takeBurstPictures();

    public abstract void stopBurstPicture();


    /**
     * 双指在屏幕上缩放视图大小
     *
     * @param factor 缩放因子,用以判断放大和缩小
     */
    public void gestureScaleZoom(float factor) {
        mLensController.gestureScaleZoom(factor);
    }

    public void setPlaySound(boolean playSound) {
        mCameraConfig.getVideoConfig().setPlaySound(playSound);
    }

    public void setMuteVideo(boolean muteVideo) {
        mCameraConfig.getVideoConfig().setMute(muteVideo);
    }

    public boolean getMuteVideo() {
        return mCameraConfig.getVideoConfig().isMute();
    }

    public long getAvailableSpace() {
        return mAvailableSpace;
    }

    @Override
    public void setAvailableSpace(long availableSpace) {
        mAvailableSpace = availableSpace;
    }

    public boolean isRecordingVideo() {
        return mCameraConfig.isRecordingVideo();
    }

    public int getBitrate() {
        return mCameraConfig.getVideoConfig().getBitrate();
    }

    public void setCaptureRate(double rate) {
        mCameraConfig.getVideoConfig().setCaptureRate(rate);
        SPConfig.getInstance().saveCaptureRate(rate);
    }

    public double getCaptureRate() {
        return mCameraConfig.getVideoConfig().getCaptureRate();
    }

    public String getVideoOutputFilePath() {
        return mCameraConfig.getVideoConfig().getVideoAbsolutePath();
    }

    @Override
    public int getCaptureOrientation() {
        return mCameraConfig.getOrientation();
    }

    public int getAwbMode() {
        return mCameraConfig.getAwb();
    }

    public int getAe() {
        return mCameraConfig.getManualConfig().getAe();
    }

    public long getSec() {
        return mCameraConfig.getManualConfig().getSec();
    }

    public int getIso() {
        return mCameraConfig.getManualConfig().getIso();
    }

    public int getManualWB() {
        return mCameraConfig.getManualConfig().getWb();
    }

    public float getAf() {
        return mCameraConfig.getManualConfig().getAf();
    }

    public float getWt() {
        return mCameraConfig.getManualConfig().getWt();
    }

    public float getZoomRatio() {
        return mCameraConfig.getZoomRatio();
    }

    @Override
    public void startSmoothZoom(float start, float end, long duration) {
        mLensController.startSmoothZoom(start, end, duration);
    }

    @Override
    public void stopSmoothZoom() {
        mLensController.stopSmoothZoom();
    }

    public void stopSmoothFocus() {
        mLensController.stopSmoothFocus();
    }

    public void setBitrate(int bitrate) {
        //查看推荐分辨率下的当前码率，谁大用哪个。
        int recommendBitRate = mCameraConfig.getVideoConfig().getSize().getRecommendBitRateFromCamcorder(getFacing());
        if (bitrate > recommendBitRate) {
            mCameraConfig.getVideoConfig().setBitrate(bitrate);
        } else {
            mCameraConfig.getVideoConfig().setBitrate(recommendBitRate);
        }
        SPConfig.getInstance().saveBitrate(bitrate);
    }

    public List<Integer> getZoomRatios() {
        return null;
    }

    public abstract void scaleZoom(float scale);

    public abstract String getCameraAPI();

    public void zoomIn() {
        gestureScaleZoom(0.989f);
    }

    public void zoomOut() {
        gestureScaleZoom(1.011f);
    }

    public void foucsNear() {
        mLensController.foucsNear();
    }

    public void focusFar() {
        mLensController.focusFar();
    }

    public Size getPicSize() {
        return mCameraConfig.getPhotoConfig().getSize();
    }

    public Size getVideoSize() {
        return mCameraConfig.getVideoConfig().getSize();
    }

    public void setVideoSize(Size size, boolean save) {
        mCameraConfig.getVideoConfig().setSize(size);
        mCameraConfig.getVideoConfig().setFps(size.getFps());
        if (save) {
            SPConfig.getInstance().saveVideoSize(size);
        }
    }


    public int getFps() {
        return mCameraConfig.getVideoConfig().getFps();
    }

    public void setFps(int fps) {
        mCameraConfig.getVideoConfig().setFps(fps);
    }

    public int getPicFormat() {
        return mCameraConfig.getPhotoConfig().getFormat();
    }

    public void setPicFormat(int format) {
        mCameraConfig.getPhotoConfig().setFormat(format);
    }

    public void setHdrMode(boolean hdr) {
        mCameraConfig.getPhotoConfig().setHdr(hdr);
    }

    @Override
    public boolean isSupportedHdr() {
        return false;
    }

    public boolean isManualMode() {
        return mCameraConfig.getManualConfig().isManual();
    }

    public void startSmoothFocus(final float start, final float end, long duration) {
        mLensController.startSmoothFocus(start, end, duration);
    }

    public void setPhoneOrientation(int orientation) {
        mPhoneOrientation = orientation;
    }

    public int getPhoneOrientation() {
        return mPhoneOrientation;
    }

    public MediaRecorder getMediaRecorder() {
        return mRecorderController.getMediaRecorder();
    }

    public String generateVideoFilePath() {
        if (mOnVideoOutputFileListener != null) {
            mCameraConfig.getVideoConfig().setVideoAbsolutePath(mOnVideoOutputFileListener.getVideoOutputFilePath());
        } else {
            throw new RuntimeException("OnVideoOutputFileListener接口返回视频输出路径,必须要被实现");
        }
        return mCameraConfig.getVideoConfig().getVideoAbsolutePath();
    }

    public void addOnManualValueListener(OnManualValueListener onManualValueListener) {
        mOnManualValueListener = onManualValueListener;
    }

    public void addOnAeChangedListener(OnAeChangeListener onAeChangeListener) {
        mOnAeChangeListener = onAeChangeListener;
    }

    public void addOnCaptureImageCallback(OnCaptureImageCallback onCaptureImageCallback) {
        mOnCaptureImageCallback = onCaptureImageCallback;
    }

    public void addOnVideoOutputFileListener(OnVideoOutputFileListener onVideoOutputFileListener) {
        mOnVideoOutputFileListener = onVideoOutputFileListener;
    }

    @Override
    public void addOnFaceDetectListener(OnFaceDetectListener onFaceDetectListener) {
        mOnFaceDetectListener = onFaceDetectListener;
    }

    protected void prepareImageReader() {
        mCaptureController = new CaptureController(mCameraConfig, mBackgroundHandler, mContext, mCallback);
        mCaptureController.prepareImageReader();
    }


    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        if (mBackgroundThread == null) {
            return;
        }
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    public CameraConfig getCameraConfig() {
        return mCameraConfig;
    }

    public void setCameraConfig(CameraConfig cameraConfig) {
        CameraViewImpl.mCameraConfig = cameraConfig;
    }
}
