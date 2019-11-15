package cn.zhengshang.cameraview;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.media.MediaRecorder;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.ViewCompat;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Range;
import android.widget.FrameLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import cn.zhengshang.base.AspectRatio;
import cn.zhengshang.base.CameraViewImpl;
import cn.zhengshang.base.Constants;
import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.base.Size;
import cn.zhengshang.base.TextureViewPreview;
import cn.zhengshang.controller.CameraController;
import cn.zhengshang.controller.DisplayOrientationDetector;
import cn.zhengshang.controller.SoundController;
import cn.zhengshang.listener.Callback;
import cn.zhengshang.listener.CallbackBridge;
import cn.zhengshang.listener.CameraError;
import cn.zhengshang.listener.OnAeChangeListener;
import cn.zhengshang.listener.OnCaptureImageCallback;
import cn.zhengshang.listener.OnManualValueListener;
import cn.zhengshang.listener.OnVideoOutputFileListener;
import cn.zhengshang.listener.OnVolumeListener;
import cn.zhengshang.task.AvailableSpaceTask;
import cn.zhengshang.task.VolumeTask;
import cn.zhengshang.widget.FocusMarkerLayout;

import static cn.zhengshang.base.Constants.BROADCAST_ACTION_SWITCH_TO_HIGH_SPEED_VIDEO;

public class CameraView extends FrameLayout {

    /**
     * The camera device faces the opposite direction as the device's screen.
     */
    public static final int FACING_BACK = Constants.FACING_BACK;

    /**
     * The camera device faces the same direction as the device's screen.
     */
    public static final int FACING_FRONT = Constants.FACING_FRONT;
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
    private final CallbackBridge mCallbacks;
    private final DisplayOrientationDetector mDisplayOrientationDetector;
    CameraViewImpl mImpl;
    /**
     * 显示对焦框、网格线的layout
     */
    private FocusMarkerLayout mFocusMarkerLayout;
    private boolean mAdjustViewBounds;
    private PreviewImpl mPreview;
    private VolumeTask mVolumeTask;
    private CameraController mCameraController;
    private BroadcastReceiver mLocalBroadcast = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.isEmpty(intent.getAction())) {
                return;
            }
            if (BROADCAST_ACTION_SWITCH_TO_HIGH_SPEED_VIDEO.equals(intent.getAction())) {
                stop();
                mImpl = mCameraController.switchToHighSpeedCamera(getContext(), mCallbacks, mPreview);
                start();
            }
        }
    };

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    @SuppressWarnings("WrongConstant")
    public CameraView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (isInEditMode()) {
            mCallbacks = null;
            mDisplayOrientationDetector = null;
            return;
        }
        // Internal setup
        mPreview = createPreviewImpl(context);
        mCallbacks = new CallbackBridge(this);
        // Attributes
        final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CameraView,
                defStyleAttr,
                R.style.Widget_CameraView);
        int startApi = a.getInt(R.styleable.CameraView_cameraApi, 0);

        mCameraController = new CameraController(startApi);
        try {
            mImpl = mCameraController.openCamera(context, mCallbacks, mPreview);
        } catch (RuntimeException e) {
            mCallbacks.onFailed(CameraError.OPEN_FAILED);
            e.printStackTrace();
        }

        mAdjustViewBounds = a.getBoolean(R.styleable.CameraView_android_adjustViewBounds, false);
        setFacing(a.getInt(R.styleable.CameraView_facing, FACING_BACK));
        String aspectRatio = a.getString(R.styleable.CameraView_aspectRatio);
        if (aspectRatio != null) {
            setAspectRatio(AspectRatio.parse(aspectRatio));
        } else {
            setAspectRatio(Constants.DEFAULT_ASPECT_RATIO);
        }
        setAutoFocus(a.getBoolean(R.styleable.CameraView_autoFocus, true));
        setFlash(a.getInt(R.styleable.CameraView_flash, Constants.FLASH_AUTO));
        a.recycle();

        mFocusMarkerLayout = new FocusMarkerLayout(getContext());
        addView(mFocusMarkerLayout);

        // Display orientation detector
        mDisplayOrientationDetector = new DisplayOrientationDetector(context) {
            @Override
            public void onDisplayOrientationChanged(int displayOrientation) {
                mImpl.setDisplayOrientation(displayOrientation);
            }

            @Override
            public void onRealOrientationChanged(int rotation) {
                mFocusMarkerLayout.setRotation(rotation);
            }
        };

        AvailableSpaceTask.monitorAvailableSpace(this);
        mVolumeTask = new VolumeTask();
        mVolumeTask.monitorVolume(this);
    }

    @NonNull
    private PreviewImpl createPreviewImpl(Context context) {
        PreviewImpl preview;
        preview = new TextureViewPreview(context, this);
//        preview = new SurfaceViewPreview(context, this);
        return preview;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!isInEditMode()) {
            mDisplayOrientationDetector.enable(ViewCompat.getDisplay(this));
        }
        SoundController.getInstance().loadSound();

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BROADCAST_ACTION_SWITCH_TO_HIGH_SPEED_VIDEO);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mLocalBroadcast, intentFilter);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (!isInEditMode()) {
            mDisplayOrientationDetector.disable();
        }
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mLocalBroadcast);

        super.onDetachedFromWindow();

        if (mCallbacks != null) {
            mCallbacks.clear();
        }
        SoundController.getInstance().release();
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
                mImpl.getView().measure(
                        MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(width * ratio.getY() / ratio.getX(),
                                MeasureSpec.EXACTLY));
            } else {
                mImpl.getView().measure(
                        MeasureSpec.makeMeasureSpec(height * ratio.getX() / ratio.getY(),
                                MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            }
        }
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
     * Open a camera device and start showing camera preview. This is typically called from
     * {@link Activity#onResume()}.
     */
    public void start() {
        mImpl.start();
        post(new Runnable() {
            @Override
            public void run() {
                if (mImpl.getAERange() != null) {
                    mFocusMarkerLayout.setMaxAeRange(mImpl.getAERange().getUpper());
                }
                mFocusMarkerLayout.setImpl(mImpl);
            }
        });
    }

    /**
     * Stop camera preview and close the device. This is typically called from
     * {@link Activity#onPause()}.
     */
    public void stop() {
        mImpl.stop();
    }

    /**
     * @return {@code true} if the camera is opened.
     */
    public boolean isCameraOpened() {
        return mImpl.isCameraOpened();
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

    public void addOnManualValueListener(OnManualValueListener onManualValueListener) {
        mImpl.addOnManualValueListener(onManualValueListener);
    }

    public void addOnAeChangedListener(OnAeChangeListener onAeChangeListener) {
        mImpl.addOnAeChangedListener(onAeChangeListener);
    }

    public void addOnCaptureImageCallback(OnCaptureImageCallback onCaptureImageCallback) {
        mImpl.addOnCaptureImageCallback(onCaptureImageCallback);
    }

    public void addOnVolumeListener(OnVolumeListener onVolumeListener) {
        mVolumeTask.setOnVolumeListener(onVolumeListener);
    }

    public void addOnVideoOutputFileListener(OnVideoOutputFileListener onVideoOutputFileListener) {
        mImpl.addOnVideoOutputFileListener(onVideoOutputFileListener);
    }

    /**
     * @return True when this CameraView is adjusting its bounds to preserve the aspect ratio of
     * camera.
     * @see #setAdjustViewBounds(boolean)
     */
    public boolean getAdjustViewBounds() {
        return mAdjustViewBounds;
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
     * Gets the direction that the current camera faces.
     *
     * @return The camera facing.
     */
    @Facing
    public int getFacing() {
        //noinspection WrongConstant
        return mImpl.getFacing();
    }

    /**
     * Chooses camera by the direction it faces.
     *
     * @param facing The camera facing. Must be either {@link #FACING_BACK} or
     *               {@link #FACING_FRONT}.
     */
    public void setFacing(@Facing int facing) {
        mImpl.setFacing(facing);
    }

    public String getCameraId() {
        return mImpl.getCameraId();
    }

    /**
     * Gets all the aspect ratios supported by the current camera.
     */
    public Set<AspectRatio> getSupportedAspectRatios() {
        return mImpl.getSupportedAspectRatios();
    }

    /**
     * Gets the current aspect ratio of camera.
     *
     * @return The current {@link AspectRatio}. Can be {@code null} if no camera is opened yet.
     */
    @Nullable
    public AspectRatio getAspectRatio() {
        return mImpl.getAspectRatio();
    }

    /**
     * Sets the aspect ratio of camera.
     *
     * @param ratio The {@link AspectRatio} to be set.
     */
    public void setAspectRatio(@NonNull AspectRatio ratio) {
        if (mImpl.setAspectRatio(ratio)) {
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
        return mImpl.getAutoFocus();
    }

    /**
     * Enables or disables the continuous auto-focus mode. When the current camera doesn't support
     * auto-focus, calling this method will be ignored.
     *
     * @param autoFocus {@code true} to enable continuous auto-focus mode. {@code false} to
     *                  disable it.
     */
    public void setAutoFocus(boolean autoFocus) {
        mImpl.setAutoFocus(autoFocus);
    }

    public int[] getAvailableFlashModes() {
        if (mImpl.isFlashAvailable()) {
            return new int[]{FLASH_OFF, FLASH_ON, FLASH_TORCH, FLASH_AUTO};
        } else if (mImpl.getFacing() == FACING_FRONT) {
            return new int[]{FLASH_OFF};
        }
        return new int[]{};
    }

    /**
     * Gets the current flash mode.
     *
     * @return The current flash mode.
     */
    @Flash
    public int getFlash() {
        //noinspection WrongConstant
        return mImpl.getFlash();
    }

    /**
     * Sets the flash mode.
     *
     * @param flash The desired flash mode.
     */
    public void setFlash(@Flash int flash) {
        mImpl.setFlash(flash);
    }

    public boolean isTorch() {
        return mImpl.isTorch();
    }

    public void setTorch(boolean open) {
        mImpl.setTorch(open);
    }

    /**
     * 获取视频码率
     */
    public int getBitrate() {
        return mImpl.getBitrate();
    }

    /**
     * 设置视频码率，没有带单位，需要手动乘以100000
     */
    public void setBitrate(int bitrate) {
        mImpl.setBitrate(bitrate);
    }

    public double getCaptureRate() {
        return mImpl.getCaptureRate();
    }

    public void setCaptureRate(double rate) {
        mImpl.setCaptureRate(rate);
    }

    public long getAvailableSpace() {
        return mImpl.getAvailableSpace();
    }

    public int getPhoneOrientation() {
        return mImpl.getPhoneOrientation();
    }

    /**
     * 设置当前手机的真实朝向.
     * 因为在{ CameraActivity2}中,设置了强制横屏,所以{@link DisplayOrientationDetector#onDisplayOrientationChanged(int)}
     * 不会随着手机方向的改变而触发.
     * 所以单独使用一个变量{@link CameraViewImpl#mPhoneOrientation}来保存当前手机的方向,
     * 然后在拍摄完照片或录制完视频的时候,旋转一定的方向,以使输出的图像永远是竖直朝向的
     *
     * @param orientation 当前的手机方向,[0,90,180,270]
     */
    public void setPhoneOrientation(int orientation) {
        mImpl.setPhoneOrientation(orientation);
    }

    /**
     * 获取视频输出路径
     */
    public String getVideoOutputFilePath() {
        return mImpl.getVideoOutputFilePath();
    }

    public int getAwb() {
        return mImpl.getAwbMode();
    }

    public int getAe() {
        return mImpl.getAe();
    }

    public long getSec() {
        return mImpl.getSec();
    }

    public int getIso() {
        return mImpl.getIso();
    }

    public int getManualWB() {
        return mImpl.getManualWB();
    }

    public void setPlaySound(boolean playSound) {
        mImpl.setPlaySound(playSound);
    }

    public boolean isMuteVideo() {
        return mImpl.getMuteVideo();
    }

    public void setMuteVideo(boolean muteVideo) {
        mImpl.setMuteVideo(muteVideo);
    }

    /**
     * Take a picture. The result will be returned to
     * {@link Callback#onPictureTaken(CameraView, byte[])}.
     */
    public void takePicture() {
        mImpl.takePicture();
    }

    public void takeBurstPicture() {
        mImpl.takeBurstPictures();
    }

    public void stopBurstPicture() {
        mImpl.stopBurstPicture();
    }

    public void startRecordingVideo() {
        mImpl.startRecordingVideo(true);
    }

    public void stopRecordingVideo() {
        mImpl.stopRecordingVideo(true);
    }

    public boolean isRecordingVideo() {
        return mImpl.isRecordingVideo();
    }

    public Size getPicSize() {
        return mImpl.getPicSize();
    }

    public void setPicSize(Size size) {
        mImpl.setPicSize(size);
    }

    public int getPicFormat() {
        return mImpl.getPicFormat();
    }

    public void setPicFormat(int format) {
        mImpl.setPicFormat(format);
    }

    public Size getVideoSize() {
        return mImpl.getVideoSize();
    }

    public void setVideoSize(Size size) {
        mImpl.setVideoSize(size);
    }

    public int getFps() {
        return mImpl.getFps();
    }

    public void setFps(int fps) {
        mImpl.setFps(fps);
    }

    public SortedSet<Size> getSupportedPicSizes() {
        return mImpl.getSupportedPicSizes();
    }

    public boolean isSupported60Fps() {
        return mImpl.isSupported60Fps();
    }

    public SortedSet<Size> getSupportedVideoSize() {
        return mImpl.getSupportedVideoSize();
    }

    public float getFpsWithSize(android.util.Size size) {
        return mImpl.getFpsWithSize(size);
    }

    public float getMaxZoom() {
        return mImpl.getMaxZoom();
    }

    public List<Integer> getZoomRatios() {
        return mImpl.getZoomRatios();
    }

    public String getCameraAPI() {
        return mImpl.getCameraAPI();
    }

    public void zoomIn() {
        mImpl.zoomIn();
    }

    public void zoomOut() {
        mImpl.zoomOut();
    }

    public void focusNear() {
        mImpl.foucsNear();
    }

    public void focusFar() {
        mImpl.focusFar();
    }

    public void setWTlen(float value) {
        mImpl.scaleZoom(value);
    }

    public int getGridType() {
        return mFocusMarkerLayout.getGridType();
    }

    public void setGridType(int type) {
        mFocusMarkerLayout.setGridType(type);
        mFocusMarkerLayout.postInvalidate();
    }

    public int[] getSupportAWBModes() {
        return mImpl.getSupportAWBModes();
    }

    public void setAWBMode(int mode) {
        mImpl.setAWBMode(mode);
    }

    public void setHdrMode(boolean hdr) {
        mImpl.setHdrMode(hdr);
    }

    public boolean isSupportedStabilize() {
        return mImpl.isSupportedStabilize();
    }

    public boolean getStabilizeEnable() {
        return mImpl.getStabilizeEnable();
    }

    public void setStabilizeEnable(boolean enable) {
        mImpl.setStabilizeEnable(enable);
    }

    public boolean isSupportedManualMode() {
        return mImpl.isSupportedManualMode();
    }

    public void setIsoAuto() {
        mImpl.setIsoAuto();
    }

    public void setManualMode(boolean manual) {
        mImpl.setManualMode(manual);
    }

    public boolean isManualAESupported() {
        return mImpl.isManualAESupported();
    }

    public Range<Integer> getAERange() {
        return mImpl.getAERange();
    }

    public void setAEValue(int value) {
        mImpl.setAEValue(value);
    }

    public float getAeStep() {
        return mImpl.getAeStep();
    }

    public boolean isManualSecSupported() {
        return mImpl.isManualSecSupported();
    }

    public Range<Long> getSecRange() {
        return mImpl.getSecRange();
    }

    public void setSecValue(long value) {
        mImpl.setSecValue(value);
    }

    public boolean isManualISOSupported() {
        return mImpl.isManualISOSupported();
    }

    public Object getISORange() {
        return mImpl.getISORange();
    }

    public void setISOValue(int value) {
        mImpl.setISOValue(value);
    }

    public boolean isManualWBSupported() {
        return mImpl.isManualWBSupported();
    }

    public Range<Integer> getManualWBRange() {
        return mImpl.getManualWBRange();
    }

    public void setManualWBValue(int value) {
        mImpl.setManualWBValue(value);
    }

    public boolean isManualAFSupported() {
        return mImpl.isManualAFSupported();
    }

    public float getAFMaxValue() {
        return mImpl.getAFMaxValue();
    }

    public float getAf() {
        return mImpl.getAf();
    }

    public float getWt() {
        return mImpl.getWt();
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
        return mImpl.getZoomRatio();
    }

    public void stopSmoothFocus() {
        mImpl.stopSmoothFocus();
    }

    public void startSmoothFocus(float start, float end, long duration) {
        mImpl.startSmoothFocus(start, end, duration);
    }

    public void stopSmoothZoom() {
        mImpl.stopSmoothZoom();
    }

    public void startSmoothZoom(float start, float end, long duration) {
        mImpl.startSmoothZoom(start, end, duration);
    }

    public void setManualAFValue(float value) {
        mImpl.setAFValue(value);
    }

    public boolean isManualWTSupported() {
        return mImpl.isManualWTSupported();
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

    public MediaRecorder getMediaRecorder() {
        return mImpl.getMediaRecorder();
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

    }
}