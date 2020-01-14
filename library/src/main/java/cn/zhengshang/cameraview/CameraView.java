package cn.zhengshang.cameraview;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.media.MediaRecorder;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Range;
import android.widget.FrameLayout;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.ParcelableCompat;
import androidx.core.os.ParcelableCompatCreatorCallbacks;
import androidx.core.view.ViewCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.zhengshang.base.AspectRatio;
import cn.zhengshang.base.Constants;
import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.base.Size;
import cn.zhengshang.base.TextureViewPreview;
import cn.zhengshang.base.ZyCamera;
import cn.zhengshang.config.CameraConfig;
import cn.zhengshang.config.CaptureConfigHelper;
import cn.zhengshang.config.CaptureMode;
import cn.zhengshang.config.SPConfig;
import cn.zhengshang.controller.CameraController;
import cn.zhengshang.controller.DisplayOrientationDetector;
import cn.zhengshang.controller.SoundController;
import cn.zhengshang.listener.Callback;
import cn.zhengshang.listener.CallbackBridge;
import cn.zhengshang.listener.CameraError;
import cn.zhengshang.listener.OnAeChangeListener;
import cn.zhengshang.listener.OnCaptureImageCallback;
import cn.zhengshang.listener.OnFaceDetectListener;
import cn.zhengshang.listener.OnManualValueListener;
import cn.zhengshang.listener.OnVideoOutputFileListener;
import cn.zhengshang.listener.OnVolumeListener;
import cn.zhengshang.task.AvailableSpaceTask;
import cn.zhengshang.task.VolumeTask;
import cn.zhengshang.widget.FocusMarkerLayout;

import static cn.zhengshang.base.Constants.BROADCAST_ACTION_SWITCH_TO_NORMAL_SLOW_MOTION;

public class CameraView extends FrameLayout {

    /**
     * The camera device faces the opposite direction as the device's screen.
     */
    public static final int FACING_BACK = Constants.FACING_BACK;

    /**
     * The camera device faces the same direction as the device's screen.
     */
    public static final int FACING_FRONT = Constants.FACING_FRONT;
    public static String CAMERA_API;
    private final CallbackBridge mCallbacks;
    private final DisplayOrientationDetector mDisplayOrientationDetector;
    ZyCamera mZyCamera;

    /**
     * Flash will not be fired.
     */
    public static final int FLASH_OFF = Constants.FLASH_OFF;

    /**
     * Flash will always be fired during snapshot.
     */
    public static final int FLASH_ON = Constants.FLASH_ON;

    /**
     * Constant emission of light during preview, auto-focus and snapshot.
     */
    public static final int FLASH_TORCH = Constants.FLASH_TORCH;

    /**
     * Flash will be fired automatically when required.
     */
    public static final int FLASH_AUTO = Constants.FLASH_AUTO;

    /**
     * Flash will be fired in red-eye reduction mode.
     */
    public static final int FLASH_RED_EYE = Constants.FLASH_RED_EYE;
    private int startApi;

    /**
     * 网格线 无
     */
    public static final int GRID_NONE = Constants.GRID_NONE;
    /**
     * 网格线 网格
     */
    public static final int GRID_GRID = Constants.GRID_GRID;
    /**
     * 网格线 网格+对角线
     */
    public static final int GRID_GRID_AND_DIAGONAL = Constants.GRID_GRID_AND_DIAGONAL;
    /**
     * 网格线 中心点
     */
    public static final int GRID_CENTER_POINT = Constants.GRID_CENTER_POINT;
    /**
     * 网格线 对角线
     */
    public static final int GRID_DIAGONAL = Constants.GRID_DIAGONAL;
    /**
     * 显示对焦框、网格线的layout
     */
    private FocusMarkerLayout mFocusMarkerLayout;
    private boolean mLoadLastCaptureMode;
    private CaptureMode mCaptureMode = CaptureMode.NORMAL_VIDEO;

    private boolean mAdjustViewBounds;
    private ExecutorService mSingleThreadExecutor;

    private PreviewImpl mPreview;

    private VolumeTask mVolumeTask;

    private CameraController mCameraController;
    private BroadcastReceiver mLocalBroadcast = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (BROADCAST_ACTION_SWITCH_TO_NORMAL_SLOW_MOTION.equals(intent.getAction())) {
                mCaptureMode = CaptureMode.UNKNOWN;
                setCaptureMode(CaptureMode.SLOW_MOTION);
            }
        }
    };
    @SuppressWarnings("WrongConstant")
    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (isInEditMode()) {
            mCallbacks = null;
            mDisplayOrientationDetector = null;
            return;
        }
        SPConfig.getInstance().changeSpName(context, FACING_BACK);
        // Internal setup
        mPreview = createPreviewImpl(context);
        mCallbacks = new CallbackBridge(this);
        // Attributes
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraView);
        startApi = a.getInt(R.styleable.CameraView_cameraApi, 0);

        // Internal setup
        mCameraController = new CameraController(startApi);

        try {
            Class<? extends ZyCamera> cameraClz = mCameraController.autoInstanceCamera(context);
            mZyCamera = mCameraController.newInstanceCamera(cameraClz, context, mCallbacks, mPreview);
        } catch (RuntimeException e) {
            mCallbacks.onFailed(CameraError.OPEN_FAILED);
            e.printStackTrace();
        }
        mAdjustViewBounds = a.getBoolean(R.styleable.CameraView_android_adjustViewBounds, false);
//        setFacing(a.getInt(R.styleable.CameraView_facing, FACING_BACK));
        //When RuntimeException throws, this will be null
        if (mZyCamera != null) {
            String aspectRatio = a.getString(R.styleable.CameraView_cv_aspectRatio);
            if (aspectRatio != null) {
                setAspectRatio(AspectRatio.parse(aspectRatio));
            } else {
                setAspectRatio(Constants.DEFAULT_ASPECT_RATIO);
            }
            setAutoFocus(a.getBoolean(R.styleable.CameraView_autoFocus, true));
            setFlash(a.getInt(R.styleable.CameraView_flash, Constants.FLASH_AUTO));
        }
        mLoadLastCaptureMode = a.getBoolean(R.styleable.CameraView_loadLastCaptureMode, true);
        a.recycle();

        mFocusMarkerLayout = new FocusMarkerLayout(getContext());
        addView(mFocusMarkerLayout);

        // Display orientation detector
        mDisplayOrientationDetector = new DisplayOrientationDetector(context) {
            @Override
            public void onDisplayOrientationChanged(int displayOrientation) {
                //外部传入
                mZyCamera.setDisplayOrientation(displayOrientation);
            }

            @Override
            public void onRealOrientationChanged(int rotation) {
                mFocusMarkerLayout.setRotation(rotation);
            }
        };

        AvailableSpaceTask.monitorAvailableSpace(this);
        mVolumeTask = new VolumeTask();
        mVolumeTask.monitorVolume(this);


        createConfig();

    }

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    private void createConfig() {
        CAMERA_API = getCameraAPI();
    }

    @NonNull
    private PreviewImpl createPreviewImpl(Context context) {
        TextureViewPreview preview = new TextureViewPreview(context, this);
        return preview;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mZyCamera == null) {
            return;
        }
        if (!isInEditMode()) {
            mDisplayOrientationDetector.enable(ViewCompat.getDisplay(this));
        }
        if (mSingleThreadExecutor == null) {
            mSingleThreadExecutor = Executors.newSingleThreadExecutor();
        }

        SoundController.getInstance().loadSound();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BROADCAST_ACTION_SWITCH_TO_NORMAL_SLOW_MOTION);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mLocalBroadcast, intentFilter);

        if (mLoadLastCaptureMode) {
            setCaptureMode(SPConfig.getInstance().loadCaptureMode());
        } else {
            CaptureConfigHelper.applyNormalVideo(mZyCamera);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mZyCamera == null) {
            return;
        }
        if (!isInEditMode()) {
            mDisplayOrientationDetector.disable();
        }
        SPConfig.getInstance().saveCaptureMode(mCaptureMode);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mLocalBroadcast);
        super.onDetachedFromWindow();

        if (mCallbacks != null && mCallbacks.getCallbacks() != null) {
            mCallbacks.clear();
        }
        SoundController.getInstance().release();
        if (mSingleThreadExecutor != null) {
            mSingleThreadExecutor.shutdown();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (isInEditMode()) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        // Handle android:adjustViewBounds
        if (mAdjustViewBounds) {
            if (!isCameraOpened()) {
                mCallbacks.reserveRequestLayoutOnOpen();
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                return;
            }
            final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
            if (widthMode == MeasureSpec.EXACTLY && heightMode != MeasureSpec.EXACTLY) {
                final AspectRatio ratio = getAspectRatio();
                assert ratio != null;
                int height = (int) (MeasureSpec.getSize(widthMeasureSpec) * ratio.toFloat());
                if (heightMode == MeasureSpec.AT_MOST) {
                    height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
                }
                super.onMeasure(widthMeasureSpec,
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            } else if (widthMode != MeasureSpec.EXACTLY && heightMode == MeasureSpec.EXACTLY) {
                final AspectRatio ratio = getAspectRatio();
                assert ratio != null;
                int width = (int) (MeasureSpec.getSize(heightMeasureSpec) * ratio.toFloat());
                if (widthMode == MeasureSpec.AT_MOST) {
                    width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec));
                }
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        heightMeasureSpec);
            } else {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
        // Measure the TextureView
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        AspectRatio ratio = getAspectRatio();
        if (mDisplayOrientationDetector.getLastKnownDisplayOrientation() % 180 == 0) {
            ratio = ratio.inverse();
        }

        if (ratio != null) {

            if (height < width * ratio.getY() / ratio.getX()) {
                mZyCamera.getView().measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(width * ratio.getY() / ratio.getX(),
                                MeasureSpec.EXACTLY));
            } else {
                mZyCamera.getView().measure(
                        MeasureSpec.makeMeasureSpec(height * ratio.getX() / ratio.getY(),
                                MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }
        }
    }

    /**
     * Open a camera device and start showing camera preview. This is typically called from
     * Activity#onResume().
     */
    public void start() {
        mZyCamera.start();
        if (mZyCamera.getAERange() != null) {
            mFocusMarkerLayout.setMaxAeRange(mZyCamera.getAERange().getUpper());
        }
        mFocusMarkerLayout.setImpl(mZyCamera);
    }

    /**
     * Stop camera preview and close the device. This is typically called from
     * Activity#onPause().
     */
    public void stop() {
        mZyCamera.stop();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        SavedState state = new SavedState(super.onSaveInstanceState());
        state.facing = getFacing();
        state.ratio = getAspectRatio();
        state.autoFocus = getAutoFocus();
        state.flash = getFlash();
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setFacing(ss.facing);
        setAspectRatio(ss.ratio);
        setAutoFocus(ss.autoFocus);
        setFlash(ss.flash);
    }

    /**
     * @return {@code true} if the camera is opened.
     */
    public boolean isCameraOpened() {
        return mZyCamera != null && mZyCamera.isCameraOpened();
    }

    public void addOnManualValueListener(OnManualValueListener onManualValueListener) {
        mZyCamera.addOnManualValueListener(onManualValueListener);
    }

    public void addOnAeChangedListener(OnAeChangeListener onAeChangeListener) {
        mZyCamera.addOnAeChangedListener(onAeChangeListener);
    }

    /**
     * Add a new callback.
     *
     * @param callback The {@link Callback} to add.
     * @see #removeCallback(Callback)
     */
    public void addCallback(@NonNull Callback callback) {
        mCallbacks.add(callback);
    }

    /**
     * Remove a callback.
     *
     * @param callback The {@link Callback} to remove.
     * @see #addCallback(Callback)
     */
    public void removeCallback(@NonNull Callback callback) {
        mCallbacks.remove(callback);
    }

    public void addOnCaptureImageCallback(OnCaptureImageCallback onCaptureImageCallback) {
        mZyCamera.addOnCaptureImageCallback(onCaptureImageCallback);
    }

    public void addOnVideoOutputFileListener(OnVideoOutputFileListener onVideoOutputFileListener) {
        mZyCamera.addOnVideoOutputFileListener(onVideoOutputFileListener);
    }

    /**
     * @return True when this CameraView is adjusting its bounds to preserve the aspect ratio of
     * camera.
     * @see #setAdjustViewBounds(boolean)
     */
    public boolean getAdjustViewBounds() {
        return mAdjustViewBounds;
    }

    public void addOnVolumeListener(OnVolumeListener onVolumeListener) {
        mVolumeTask.setOnVolumeListener(onVolumeListener);
    }

    /**
     * Gets the direction that the current camera faces.
     *
     * @return The camera facing.
     */
    @Facing
    public int getFacing() {
        //noinspection WrongConstant
        return mZyCamera.getFacing();
    }

    /**
     * @param adjustViewBounds {@code true} if you want the CameraView to adjust its bounds to
     *                         preserve the aspect ratio of camera.
     * @see #getAdjustViewBounds()
     */
    public void setAdjustViewBounds(boolean adjustViewBounds) {
        if (mAdjustViewBounds != adjustViewBounds) {
            mAdjustViewBounds = adjustViewBounds;
            requestLayout();
        }
    }

    /**
     * Chooses camera by the direction it faces.
     *
     * @param facing The camera facing. Must be either {@link #FACING_BACK} or
     *               {@link #FACING_FRONT}.
     */
    public void setFacing(@Facing int facing) {
        SPConfig.getInstance().saveCaptureMode(mCaptureMode);
        mZyCamera.setFacing(facing);

        SPConfig.getInstance().changeSpName(getContext(), facing);
        setCaptureMode(SPConfig.getInstance().loadCaptureMode());
        setBitrate(SPConfig.getInstance().loadBitrate());
        setCaptureRate(SPConfig.getInstance().loadCaptureRate());
        setVideoSize(SPConfig.getInstance().loadVideoSize());
    }

    public String getCameraId() {
        return mZyCamera.getCameraId();
    }

    /**
     * Gets all the aspect ratios supported by the current camera.
     */
    public Set<AspectRatio> getSupportedAspectRatios() {
        return mZyCamera.getSupportedAspectRatios();
    }

    /**
     * Gets the current aspect ratio of camera.
     *
     * @return The current {@link AspectRatio}. Can be {@code null} if no camera is opened yet.
     */
    @Nullable
    public AspectRatio getAspectRatio() {
        return mZyCamera.getAspectRatio();
    }

    /**
     * Sets the aspect ratio of camera.
     *
     * @param ratio The {@link AspectRatio} to be set.
     */
    public void setAspectRatio(@NonNull AspectRatio ratio) {
        if (mZyCamera.setAspectRatio(ratio)) {
            requestLayout();
        }
    }

    /**
     * Returns whether the continuous auto-focus mode is enabled.
     *
     * @return {@code true} if the continuous auto-focus mode is enabled. {@code false} if it is
     * disabled, or if it is not supported by the current camera.
     */
    public boolean getAutoFocus() {
        return mZyCamera.getAutoFocus();
    }

    /**
     * Enables or disables the continuous auto-focus mode. When the current camera doesn't support
     * auto-focus, calling this method will be ignored.
     *
     * @param autoFocus {@code true} to enable continuous auto-focus mode. {@code false} to
     *                  disable it.
     */
    public void setAutoFocus(boolean autoFocus) {
        mZyCamera.setAutoFocus(autoFocus);
    }

    public int[] getAvailableFlashModes() {
        if (mZyCamera.isFlashAvailable()) {
            return new int[]{FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO};
        } else {
            return new int[]{FLASH_OFF};
        }
    }

    /**
     * Gets the current flash mode.
     *
     * @return The current flash mode.
     */
    @Flash
    public int getFlash() {
        //noinspection WrongConstant
        return mZyCamera.getFlash();
    }

    /**
     * Sets the flash mode.
     *
     * @param flash The desired flash mode.
     */
    public void setFlash(@Flash int flash) {
        mZyCamera.setFlash(flash);
    }

    public boolean isTorch() {
        return mZyCamera.isTorch();
    }

    public void setTorch(boolean open) {
        mZyCamera.setTorch(open);
    }

    /**
     * 获取视频码率
     */
    public int getBitrate() {
        return mZyCamera.getBitrate();
    }

    /**
     * 设置视频码率，没有带单位，需要手动乘以100000
     */
    public void setBitrate(int bitrate) {
        mZyCamera.setBitrate(bitrate);
    }

    public double getCaptureRate() {
        return mZyCamera.getCaptureRate();
    }

    public void setCaptureRate(double rate) {
        mZyCamera.setCaptureRate(rate);
    }

    public long getAvailableSpace() {
        return mZyCamera.getAvailableSpace();
    }

    public void setAvailableSpace(long space) {
        mZyCamera.setAvailableSpace(space);
    }

    public int getPhoneOrientation() {
        return mZyCamera.getPhoneOrientation();
    }

    /**
     * 设置当前手机的真实朝向.
     * 因为在{ CameraActivity2}中,设置了强制横屏,所以{@link DisplayOrientationDetector#onDisplayOrientationChanged(int)}
     * 不会随着手机方向的改变而触发.
     * 所以单独使用一个变量 CameraViewImpl#mPhoneOrientation 来保存当前手机的方向,
     * 然后在拍摄完照片或录制完视频的时候,旋转一定的方向,以使输出的图像永远是竖直朝向的
     *
     * @param orientation 当前的手机方向,[0,90,180,270]
     */
    public void setPhoneOrientation(int orientation) {
        mZyCamera.setPhoneOrientation(orientation);
        mPreview.setDisplayOrientation(orientation);
    }

    /**
     * 获取视频输出路径
     */
    public String getVideoOutputFilePath() {
        return mZyCamera.getVideoOutputFilePath();
    }

    public int getAwb() {
        return mZyCamera.getAwbMode();
    }

    public int getAe() {
        return mZyCamera.getAe();
    }

    public long getSec() {
        return mZyCamera.getSec();
    }

    public int getIso() {
        return mZyCamera.getIso();
    }

    public int getManualWB() {
        return mZyCamera.getManualWB();
    }

    public void setPlaySound(boolean playSound) {
        mZyCamera.setPlaySound(playSound);
    }

    public boolean isMuteVideo() {
        return mZyCamera.getMuteVideo();
    }

    public void setMuteVideo(boolean muteVideo) {
        mZyCamera.setMuteVideo(muteVideo);
    }

    /**
     * Take a picture. The result will be returned to
     * {@link Callback#onPictureTaken(CameraView, byte[])}.
     */
    public void takePicture() {
        mZyCamera.takePicture();
    }

    public void takeBurstPictures() {
        mZyCamera.takeBurstPictures();
    }

    public void stopBurstPicture() {
        mZyCamera.stopBurstPicture();
    }

    public void startRecordingVideo() {
        mZyCamera.startRecordingVideo(true);
    }

    public void stopRecordingVideo() {
        mZyCamera.stopRecordingVideo(true);
    }

    public boolean isRecordingVideo() {
        return mZyCamera.isRecordingVideo();
    }

    public Size getPicSize() {
        return mZyCamera.getPicSize();
    }

    public void setPicSize(Size size) {
        mZyCamera.setPicSize(size);
    }

    public int getPicFormat() {
        return mZyCamera.getPicFormat();
    }

    public void setPicFormat(int format) {
        mZyCamera.setPicFormat(format);
    }

    public Size getVideoSize() {
        return mZyCamera.getVideoSize();
    }

    public void setVideoSize(Size size) {
        if (!mZyCamera.getVideoSize().equals(size)) {
            mZyCamera.setVideoSize(size, true);

            if (size.getFps() > 30) {
                setCaptureMode(CaptureMode.HIGH_SPEED_VIDEO);
            } else {
                setCaptureMode(CaptureMode.NORMAL_VIDEO);
            }
        }
    }

    public int getFps() {
        return mZyCamera.getFps();
    }

    public void setFps(int fps) {
        mZyCamera.setFps(fps);
    }

    public SortedSet<Size> getSupportedPicSizes() {
        return mZyCamera.getSupportedPicSizes();
    }

    public SortedSet<Size> getSupportedVideoSize() {
        return mZyCamera.getSupportedVideoSize();
    }

    public float getFpsWithSize(android.util.Size size) {
        return mZyCamera.getFpsWithSize(size);
    }

    @Deprecated
    public float getMaxZoom() {
        return mZyCamera.getMaxZoom();
    }

    @Deprecated
    public List<Integer> getZoomRatios() {
        return mZyCamera.getZoomRatios();
    }

    public String getCameraAPI() {
        return mZyCamera.getCameraAPI();
    }

    public void zoomIn() {
        mZyCamera.zoomIn();
    }

    public void zoomOut() {
        mZyCamera.zoomOut();
    }

    /**
     * 连续焦距放大
     *
     * @param begin true 开始, false 停止
     */
    public void continueZoomIn(boolean begin) {

    }

    /**
     * 连续焦距缩小
     *
     * @param begin true 开始, false 停止
     */
    public void continueZoomOut(boolean begin) {

    }

    public void focusNear() {
        mZyCamera.foucsNear();
    }

    public void focusFar() {
        mZyCamera.focusFar();
    }

    public void setWTlen(float value) {
        mZyCamera.scaleZoom(value);
    }

    public int getGridType() {
        return mFocusMarkerLayout.getGridType();
    }

    public int[] getSupportAWBModes() {
        if (getFacing() == CameraView.FACING_FRONT) {
            return new int[0];
        }
        return mZyCamera.getSupportAWBModes();
    }

    public void setGridType(int type) {
        mFocusMarkerLayout.setGridType(type);
        mFocusMarkerLayout.postInvalidate();
    }

    public void setAWBMode(int mode) {
        mZyCamera.setAWBMode(mode);
    }

    public void setHdrMode(boolean hdr) {
        mZyCamera.setHdrMode(hdr);
    }

    public boolean isSupportedHdr() {
        return mZyCamera.isSupportedHdr();
    }

    public boolean isSupportedStabilize() {
        return mZyCamera.isSupportedStabilize();
    }

    public boolean getStabilizeEnable() {
        return mZyCamera.getStabilizeEnable();
    }

    public void setStabilizeEnable(boolean enable) {
        mZyCamera.setStabilizeEnable(enable);
    }

    public boolean isSupportedManualMode() {
        return mZyCamera.isSupportedManualMode();
    }

    public void setIsoAuto() {
        mZyCamera.setIsoAuto();
    }

    public void setManualMode(boolean manual) {
        mZyCamera.setManualMode(manual);
    }

    public boolean isManualAESupported() {
        return mZyCamera.isManualAESupported();
    }

    public Range<Integer> getAERange() {
        return mZyCamera.getAERange();
    }

    public void setAEValue(int value) {
        mZyCamera.setAEValue(value);
    }

    public float getAeStep() {
        return mZyCamera.getAeStep();
    }

    public boolean isManualSecSupported() {
        return mZyCamera.isManualSecSupported();
    }

    public Range<Long> getSecRange() {
        return mZyCamera.getSecRange();
    }

    public void setSecValue(long value) {
        mZyCamera.setSecValue(value);
    }

    public boolean isManualISOSupported() {
        return mZyCamera.isManualISOSupported();
    }

    public Object getISORange() {
        return mZyCamera.getISORange();
    }

    public void setISOValue(int value) {
        mZyCamera.setISOValue(value);
    }

    public boolean isManualWBSupported() {
        return mZyCamera.isManualWBSupported();
    }

    public Range<Integer> getManualWBRange() {
        return mZyCamera.getManualWBRange();
    }

    public void setManualWBValue(int value) {
        mZyCamera.setManualWBValue(value);
    }

    public boolean isManualAFSupported() {
        return mZyCamera.isManualAFSupported();
    }

    public void lockAEandAF() {
        mZyCamera.lockAEandAF();
    }

    public void unLockAEandAF() {
        mZyCamera.unLockAEandAF();
    }

    public float getAFMaxValue() {
        return mZyCamera.getAFMaxValue();
    }

    public float getAf() {
        return mZyCamera.getAf();
    }

    public float getWt() {
        return mZyCamera.getWt();
    }

    /**
     * zoomRatio不同于WT.
     * WT表示的是,当前的缩放系数在总缩放范围的比例.比如,在PIXEL XL手机中.
     * 缩放的Zoom的范围为[1,4],如果此时WT的值为3.9,即表示在倒数第二个范围,
     * 此时的缩放比例zoomRatio为放大3.9倍(camera2中直接根据比例计算对应的rect范围,即与zoomRatio一致).
     * 而在OPPO R9S手机中,缩放范围的Zoom为[1,8](已转换为与camera2一致的表示方法,用camera1的表示方法则是[0,90]),
     * 此时,如果WT的值为7.9,也表示为倒数第二个范围值,但是,此时的缩放比例zoomRatio的不是7.9,因为要根据
     * camera1自己的规则和给出的ZoomRatios数组来计算.经过计算,此时对应的缩放比例zoomRatio为
     * {@link #getZoomRatios()}数组的倒数第二个item值(需要除以100,以小数表示,此例子中,该值为7.81).此时的
     * zoomRatio则不一定与WT的值一致.
     * 为了保持在外层调用一致的原则,特意添加此方法.
     *
     * @return 返回当前的缩放比例
     * @deprecated
     */
    public float getZoomRatio() {
        return mZyCamera.getZoomRatio();
    }

    public void stopSmoothFocus() {
        mZyCamera.stopSmoothFocus();
    }

    public void startSmoothFocus(float start, float end, long duration) {
        mZyCamera.startSmoothFocus(start, end, duration);
    }

    public void stopSmoothZoom() {
        mZyCamera.stopSmoothZoom();
    }

    public void startSmoothZoom(float start, float end, long duration) {
        mZyCamera.startSmoothZoom(start, end, duration);
    }

    public void setManualAFValue(float value) {
        mZyCamera.setAFValue(value);
    }

    public boolean isManualWTSupported() {
        return mZyCamera.isManualWTSupported();
    }

    public MediaRecorder getMediaRecorder() {
        return mZyCamera.getMediaRecorder();
    }

    public boolean isSupportFaceDetect() {
        return mZyCamera.isSupportFaceDetect();
    }

    public PreviewImpl getPreview() {
        return mPreview;
    }

    public Bitmap getPreviewFrameBitmap() {
        return mPreview.getFrameBitmap();
    }

    public Bitmap getPreviewFrameBitmap(int width, int height) {
        return mPreview.getFrameBitmap(width, height);
    }

    public void setEnableScaleZoom(boolean enableScaleZoom) {
        mFocusMarkerLayout.setEnableScaleZoom(enableScaleZoom);
    }

    public void increaseAe() {
        mFocusMarkerLayout.increaseAe();
    }

    public void decreaseAe() {
        mFocusMarkerLayout.decreaseAe();
    }

    public void showAeAdjust() {
        mFocusMarkerLayout.showAeAdjust();
    }

    public void setFaceDetect(boolean open) {
        mZyCamera.setFaceDetect(open);
    }

    public void addOnFaceDetectListener(OnFaceDetectListener onFaceDetectListener) {
        mZyCamera.addOnFaceDetectListener(onFaceDetectListener);
    }

    private void switchCamera(Class<? extends ZyCamera> clz, boolean animate) {
        if (Constants.CAMERA_API_CAMERA1.equals(mZyCamera.getCameraAPI())) {
            return;
        }
        boolean sameClz = mZyCamera.getClass().equals(clz);
        if (sameClz) {
            return;
        }
        if (animate) {
            mFocusMarkerLayout.blurPreview(mPreview.getFrameBitmap());
        }
        stop();
        mZyCamera = mCameraController.newInstanceCamera(clz, getContext(), mCallbacks, mPreview);
        start();
        if (animate) {
            mFocusMarkerLayout.switchCamera();
        }
    }

    private void switchCamera(Class<? extends ZyCamera> clz) {
        switchCamera(clz, true);
    }

    public CaptureMode getCaptureMode() {
        return mCaptureMode;
    }

    public void setCaptureMode(CaptureMode captureMode) {
        if (mSingleThreadExecutor == null) return;
        mSingleThreadExecutor.execute(() -> {
            if (captureMode == mCaptureMode) {
                return;
            }
            mCaptureMode = captureMode;

            switch (captureMode) {
                case SLOW_MOTION:
                    CaptureConfigHelper.applySlowMotion(mZyCamera);
                    switchCamera(mCameraController.autoInstanceCamera(getContext()));
                    break;
                case VIDEO:
                    CaptureConfigHelper.applyNormalVideo(mZyCamera);
                    if (CaptureConfigHelper.isHighSpeedVideo(mZyCamera)) {
                        setCaptureMode(CaptureMode.HIGH_SPEED_VIDEO);
                    } else {
                        setCaptureMode(CaptureMode.NORMAL_VIDEO);
                    }
                    break;
                case HIGH_SPEED_VIDEO:
                    //由于DC相机界面固定使用Camera1, 而那里又重新设置了视频大小为30fps,所以在
                    //高帧率的情况下切换过去,再返回会造成视频只有30fps的情况, 需要避免.
                    if (getVideoSize().getFps() == 30) {
                        setCaptureMode(CaptureMode.NORMAL_VIDEO);
                        return;
                    }
                    switchCamera(CameraHighSpeedVideo.class);
                    break;
                case NORMAL_VIDEO:
                    switchCamera(mCameraController.autoInstanceCamera(getContext()));
                    break;
                case TIMELAPSE:
                case MOVING_TIMELAPSE:
                    CaptureConfigHelper.applyTimeLapse(mZyCamera);
                    switchCamera(mCameraController.autoInstanceCamera(getContext()), false);
                    break;
                case FOCUS_TIMELAPSE:
                    CaptureConfigHelper.applyNormalVideo(mZyCamera);
                    switchCamera(mCameraController.autoInstanceCamera(getContext()), false);
                    break;
                default:
                    break;
            }
        });
    }

    public CameraConfig getCameraConfig() {
        return mZyCamera.getCameraConfig();
    }

    public void setCameraConfig(CameraConfig cameraConfig) {
        mZyCamera.setCameraConfig(cameraConfig);
    }

    /**
     * Direction the camera faces relative to device screen.
     */
    @IntDef({FACING_BACK, FACING_FRONT})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Facing {
    }

    /**
     * The mode for for the camera device's flash control
     */
    @IntDef({FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO, FLASH_RED_EYE})
    public @interface Flash {
    }

    protected static class SavedState extends BaseSavedState {

        @Facing
        int facing;

        AspectRatio ratio;

        boolean autoFocus;

        @Flash
        int flash;

        @SuppressWarnings("WrongConstant")
        public SavedState(Parcel source, ClassLoader loader) {
            super(source);
            facing = source.readInt();
            ratio = source.readParcelable(loader);
            autoFocus = source.readByte() != 0;
            flash = source.readInt();
        }

        public SavedState(Parcelable superState) {
            super(superState);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(facing);
            out.writeParcelable(ratio, 0);
            out.writeByte((byte) (autoFocus ? 1 : 0));
            out.writeInt(flash);
        }

        public static final Creator<SavedState> CREATOR
                = ParcelableCompat.newCreator(new ParcelableCompatCreatorCallbacks<SavedState>() {

            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in, loader);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }

        });

    }
}
