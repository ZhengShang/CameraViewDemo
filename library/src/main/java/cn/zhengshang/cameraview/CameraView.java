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
import cn.zhengshang.base.ICamera;
import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.base.Size;
import cn.zhengshang.base.TextureViewPreview;
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
    ICamera mICamera;

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
            Class<? extends ICamera> cameraClz = mCameraController.autoInstanceCamera(context);
            mICamera = mCameraController.newInstanceCamera(cameraClz, context, mCallbacks, mPreview);
        } catch (RuntimeException e) {
            mCallbacks.onFailed(CameraError.OPEN_FAILED);
            e.printStackTrace();
        }
        mAdjustViewBounds = a.getBoolean(R.styleable.CameraView_android_adjustViewBounds, false);
//        setFacing(a.getInt(R.styleable.CameraView_facing, FACING_BACK));
        //When RuntimeException throws, this will be null
        if (mICamera != null) {
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
                mICamera.setDisplayOrientation(displayOrientation);
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
        if (mICamera == null) {
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
            CaptureConfigHelper.applyNormalVideo(mICamera);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mICamera == null) {
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
                mICamera.getView().measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(width * ratio.getY() / ratio.getX(),
                                MeasureSpec.EXACTLY));
            } else {
                mICamera.getView().measure(
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
        mICamera.start();
        if (mICamera.getAERange() != null) {
            mFocusMarkerLayout.setMaxAeRange(mICamera.getAERange().getUpper());
        }
        mFocusMarkerLayout.setImpl(mICamera);
    }

    /**
     * Stop camera preview and close the device. This is typically called from
     * Activity#onPause().
     */
    public void stop() {
        mICamera.stop();
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
        return mICamera != null && mICamera.isCameraOpened();
    }

    public void addOnManualValueListener(OnManualValueListener onManualValueListener) {
        mICamera.addOnManualValueListener(onManualValueListener);
    }

    public void addOnAeChangedListener(OnAeChangeListener onAeChangeListener) {
        mICamera.addOnAeChangedListener(onAeChangeListener);
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
        mICamera.addOnCaptureImageCallback(onCaptureImageCallback);
    }

    public void addOnVideoOutputFileListener(OnVideoOutputFileListener onVideoOutputFileListener) {
        mICamera.addOnVideoOutputFileListener(onVideoOutputFileListener);
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
        return mICamera.getFacing();
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
        mICamera.setFacing(facing);

        SPConfig.getInstance().changeSpName(getContext(), facing);
        setCaptureMode(SPConfig.getInstance().loadCaptureMode());
        setBitrate(SPConfig.getInstance().loadBitrate());
        setCaptureRate(SPConfig.getInstance().loadCaptureRate());
        setVideoSize(SPConfig.getInstance().loadVideoSize());
    }

    public String getCameraId() {
        return mICamera.getCameraId();
    }

    /**
     * Gets all the aspect ratios supported by the current camera.
     */
    public Set<AspectRatio> getSupportedAspectRatios() {
        return mICamera.getSupportedAspectRatios();
    }

    /**
     * Gets the current aspect ratio of camera.
     *
     * @return The current {@link AspectRatio}. Can be {@code null} if no camera is opened yet.
     */
    @Nullable
    public AspectRatio getAspectRatio() {
        return mICamera.getAspectRatio();
    }

    /**
     * Sets the aspect ratio of camera.
     *
     * @param ratio The {@link AspectRatio} to be set.
     */
    public void setAspectRatio(@NonNull AspectRatio ratio) {
        if (mICamera.setAspectRatio(ratio)) {
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
        return mICamera.getAutoFocus();
    }

    /**
     * Enables or disables the continuous auto-focus mode. When the current camera doesn't support
     * auto-focus, calling this method will be ignored.
     *
     * @param autoFocus {@code true} to enable continuous auto-focus mode. {@code false} to
     *                  disable it.
     */
    public void setAutoFocus(boolean autoFocus) {
        mICamera.setAutoFocus(autoFocus);
    }

    public int[] getAvailableFlashModes() {
        if (mICamera.isFlashAvailable()) {
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
        return mICamera.getFlash();
    }

    /**
     * Sets the flash mode.
     *
     * @param flash The desired flash mode.
     */
    public void setFlash(@Flash int flash) {
        mICamera.setFlash(flash);
    }

    public boolean isTorch() {
        return mICamera.isTorch();
    }

    public void setTorch(boolean open) {
        mICamera.setTorch(open);
    }

    /**
     * 获取视频码率
     */
    public int getBitrate() {
        return mICamera.getBitrate();
    }

    /**
     * 设置视频码率，没有带单位，需要手动乘以100000
     */
    public void setBitrate(int bitrate) {
        mICamera.setBitrate(bitrate);
    }

    public double getCaptureRate() {
        return mICamera.getCaptureRate();
    }

    public void setCaptureRate(double rate) {
        mICamera.setCaptureRate(rate);
    }

    public long getAvailableSpace() {
        return mICamera.getAvailableSpace();
    }

    public void setAvailableSpace(long space) {
        mICamera.setAvailableSpace(space);
    }

    public int getPhoneOrientation() {
        return mICamera.getPhoneOrientation();
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
        mICamera.setPhoneOrientation(orientation);
        mPreview.setDisplayOrientation(orientation);
    }

    /**
     * 获取视频输出路径
     */
    public String getVideoOutputFilePath() {
        return mICamera.getVideoOutputFilePath();
    }

    public int getAwb() {
        return mICamera.getAwbMode();
    }

    public int getAe() {
        return mICamera.getAe();
    }

    public long getSec() {
        return mICamera.getSec();
    }

    public int getIso() {
        return mICamera.getIso();
    }

    public int getManualWB() {
        return mICamera.getManualWB();
    }

    public void setPlaySound(boolean playSound) {
        mICamera.setPlaySound(playSound);
    }

    public boolean isMuteVideo() {
        return mICamera.getMuteVideo();
    }

    public void setMuteVideo(boolean muteVideo) {
        mICamera.setMuteVideo(muteVideo);
    }

    /**
     * Take a picture. The result will be returned to
     * {@link Callback#onPictureTaken(CameraView, byte[])}.
     */
    public void takePicture() {
        mICamera.takePicture();
    }

    public void takeBurstPictures() {
        mICamera.takeBurstPictures();
    }

    public void stopBurstPicture() {
        mICamera.stopBurstPicture();
    }

    public void startRecordingVideo() {
        mICamera.startRecordingVideo(true);
    }

    public void stopRecordingVideo() {
        mICamera.stopRecordingVideo(true);
    }

    public boolean isRecordingVideo() {
        return mICamera.isRecordingVideo();
    }

    public Size getPicSize() {
        return mICamera.getPicSize();
    }

    public void setPicSize(Size size) {
        mICamera.setPicSize(size);
    }

    public int getPicFormat() {
        return mICamera.getPicFormat();
    }

    public void setPicFormat(int format) {
        mICamera.setPicFormat(format);
    }

    public Size getVideoSize() {
        return mICamera.getVideoSize();
    }

    public void setVideoSize(Size size) {
        if (!mICamera.getVideoSize().equals(size)) {
            mICamera.setVideoSize(size, true);

            if (size.getFps() > 30) {
                setCaptureMode(CaptureMode.HIGH_SPEED_VIDEO);
            } else {
                setCaptureMode(CaptureMode.NORMAL_VIDEO);
            }
        }
    }

    public int getFps() {
        return mICamera.getFps();
    }

    public void setFps(int fps) {
        mICamera.setFps(fps);
    }

    public SortedSet<Size> getSupportedPicSizes() {
        return mICamera.getSupportedPicSizes();
    }

    public SortedSet<Size> getSupportedVideoSize() {
        return mICamera.getSupportedVideoSize();
    }

    public float getFpsWithSize(android.util.Size size) {
        return mICamera.getFpsWithSize(size);
    }

    public float getMaxZoom() {
        return mICamera.getMaxZoom();
    }

    public List<Integer> getZoomRatios() {
        return mICamera.getZoomRatios();
    }

    public String getCameraAPI() {
        return mICamera.getCameraAPI();
    }

    public void zoomIn() {
        mICamera.zoomIn();
    }

    public void zoomOut() {
        mICamera.zoomOut();
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
        mICamera.foucsNear();
    }

    public void focusFar() {
        mICamera.focusFar();
    }

    public void setWTlen(float value) {
        mICamera.scaleZoom(value);
    }

    public int getGridType() {
        return mFocusMarkerLayout.getGridType();
    }

    public int[] getSupportAWBModes() {
        if (getFacing() == CameraView.FACING_FRONT) {
            return new int[0];
        }
        return mICamera.getSupportAWBModes();
    }

    public void setGridType(int type) {
        mFocusMarkerLayout.setGridType(type);
        mFocusMarkerLayout.postInvalidate();
    }

    public void setAWBMode(int mode) {
        mICamera.setAWBMode(mode);
    }

    public void setHdrMode(boolean hdr) {
        mICamera.setHdrMode(hdr);
    }

    public boolean isSupportedHdr() {
        return mICamera.isSupportedHdr();
    }

    public boolean isSupportedStabilize() {
        return mICamera.isSupportedStabilize();
    }

    public boolean getStabilizeEnable() {
        return mICamera.getStabilizeEnable();
    }

    public void setStabilizeEnable(boolean enable) {
        mICamera.setStabilizeEnable(enable);
    }

    public boolean isSupportedManualMode() {
        return mICamera.isSupportedManualMode();
    }

    public void setIsoAuto() {
        mICamera.setIsoAuto();
    }

    public void setManualMode(boolean manual) {
        mICamera.setManualMode(manual);
    }

    public boolean isManualAESupported() {
        return mICamera.isManualAESupported();
    }

    public Range<Integer> getAERange() {
        return mICamera.getAERange();
    }

    public void setAEValue(int value) {
        mICamera.setAEValue(value);
    }

    public float getAeStep() {
        return mICamera.getAeStep();
    }

    public boolean isManualSecSupported() {
        return mICamera.isManualSecSupported();
    }

    public Range<Long> getSecRange() {
        return mICamera.getSecRange();
    }

    public void setSecValue(long value) {
        mICamera.setSecValue(value);
    }

    public boolean isManualISOSupported() {
        return mICamera.isManualISOSupported();
    }

    public Object getISORange() {
        return mICamera.getISORange();
    }

    public void setISOValue(int value) {
        mICamera.setISOValue(value);
    }

    public boolean isManualWBSupported() {
        return mICamera.isManualWBSupported();
    }

    public Range<Integer> getManualWBRange() {
        return mICamera.getManualWBRange();
    }

    public void setManualWBValue(int value) {
        mICamera.setManualWBValue(value);
    }

    public boolean isManualAFSupported() {
        return mICamera.isManualAFSupported();
    }

    public void lockAEandAF() {
        mICamera.lockAEandAF();
    }

    public void unLockAEandAF() {
        mICamera.unLockAEandAF();
    }

    public float getAFMaxValue() {
        return mICamera.getAFMaxValue();
    }

    public float getAf() {
        return mICamera.getAf();
    }

    public float getWt() {
        return mICamera.getWt();
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
     */
    public float getZoomRatio() {
        return mICamera.getZoomRatio();
    }

    public void stopSmoothFocus() {
        mICamera.stopSmoothFocus();
    }

    public void startSmoothFocus(float start, float end, long duration) {
        mICamera.startSmoothFocus(start, end, duration);
    }

    public void stopSmoothZoom() {
        mICamera.stopSmoothZoom();
    }

    public void startSmoothZoom(float start, float end, long duration) {
        mICamera.startSmoothZoom(start, end, duration);
    }

    public void setManualAFValue(float value) {
        mICamera.setAFValue(value);
    }

    public boolean isManualWTSupported() {
        return mICamera.isManualWTSupported();
    }

    public MediaRecorder getMediaRecorder() {
        return mICamera.getMediaRecorder();
    }

    public boolean isSupportFaceDetect() {
        return mICamera.isSupportFaceDetect();
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
        mICamera.setFaceDetect(open);
    }

    public void addOnFaceDetectListener(OnFaceDetectListener onFaceDetectListener) {
        mICamera.addOnFaceDetectListener(onFaceDetectListener);
    }

    private void switchCamera(Class<? extends ICamera> clz, boolean animate) {
        if (Constants.CAMERA_API_CAMERA1.equals(mICamera.getCameraAPI())) {
            return;
        }
        boolean sameClz = mICamera.getClass().equals(clz);
        if (sameClz) {
            return;
        }
        if (animate) {
            mFocusMarkerLayout.blurPreview(mPreview.getFrameBitmap());
        }
        stop();
        mICamera = mCameraController.newInstanceCamera(clz, getContext(), mCallbacks, mPreview);
        start();
        if (animate) {
            mFocusMarkerLayout.switchCamera();
        }
    }

    private void switchCamera(Class<? extends ICamera> clz) {
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
                    CaptureConfigHelper.applySlowMotion(mICamera);
                    switchCamera(mCameraController.autoInstanceCamera(getContext()));
                    break;
                case VIDEO:
                    CaptureConfigHelper.applyNormalVideo(mICamera);
                    if (CaptureConfigHelper.isHighSpeedVideo(mICamera)) {
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
                    CaptureConfigHelper.applyTimeLapse(mICamera);
                    switchCamera(mCameraController.autoInstanceCamera(getContext()), false);
                    break;
                case FOCUS_TIMELAPSE:
                    CaptureConfigHelper.applyNormalVideo(mICamera);
                    switchCamera(mCameraController.autoInstanceCamera(getContext()), false);
                    break;
                default:
                    break;
            }
        });
    }

    public CameraConfig getCameraConfig() {
        return mICamera.getCameraConfig();
    }

    public void setCameraConfig(CameraConfig cameraConfig) {
        mICamera.setCameraConfig(cameraConfig);
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
