package com.zhiyun.android.cameraview;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

import com.zhiyun.android.base.CameraViewImpl;
import com.zhiyun.android.util.CameraUtil;

import static com.zhiyun.android.cameraview.CameraView.GRID_CENTER_POINT;
import static com.zhiyun.android.cameraview.CameraView.GRID_GRID;
import static com.zhiyun.android.cameraview.CameraView.GRID_GRID_AND_DIAGONAL;
import static com.zhiyun.android.cameraview.CameraView.GRID_NONE;

@TargetApi(14)
public class FocusMarkerLayout extends FrameLayout {

    public static final String BROADCAST_ACTION_TAKE_PHOTO = "zyplay.action.take.photo";
    public static final String BROADCAST_ACTION_RECORING_STOP = "zyplay.action.recording.stop";

    private CameraViewImpl mImpl;
    private View mFocusMarkerContainer;
    private View mFocusLayout;
    private AEsun mAEsun;
    private Paint mPaint;
    private int mGridType;
    private Bitmap mCenterBitmap;
    private ArgbEvaluator argbEvaluator = new ArgbEvaluator();

    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetector mGestureDetector;

    private boolean mLocked; //ae/af lock
    private int mRotation;

    private boolean mEnableScaleZoom = true;

    public FocusMarkerLayout(@NonNull Context context) {
        this(context, null);
    }

    public FocusMarkerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        LayoutInflater.from(getContext()).inflate(R.layout.layout_focus_marker, this);

        initPaint();

        mFocusMarkerContainer = findViewById(R.id.focusContainer);
        mFocusLayout = findViewById(R.id.focusLayout);
        mAEsun = findViewById(R.id.aeSun);

        mFocusMarkerContainer.setAlpha(0);

        mGestureDetector = new GestureDetector(context, new MyGestureListener());
        mScaleGestureDetector = new ScaleGestureDetector(context, new ScaleGestureListener());

        mAEsun.setOnAEChangeListener(new AEsun.OnAEChangeListener() {
            @Override
            public void onAEchanged(int ae) {
                mImpl.setAEValue(ae);
            }
        });
    }

    public void setImpl(CameraViewImpl impl) {
        mImpl = impl;
    }

    public void setRotation(int rotation) {
        mRotation = rotation;
        mFocusMarkerContainer.setRotation(90 * (3 - rotation));
    }

    public void setMaxAeRange(int maxAe) {
        mAEsun.setMaxAeRange(maxAe);
    }

    public FocusMarkerLayout setEnableScaleZoom(boolean enableScaleZoom) {
        mEnableScaleZoom = enableScaleZoom;
        return this;
    }

    /**
     * 是否可以滑动屏幕来调节ae
     */
    private boolean canScrollAEchanged() {
        return mFocusMarkerContainer.getAlpha() != 0;
    }

    public void increaseAe() {
        if (!canScrollAEchanged()) {
            return;
        }
        mAEsun.increaseAe();
        dilutedFocusToDismiss();
    }

    public void decreaseAe() {
        if (!canScrollAEchanged()) {
            return;
        }
        mAEsun.decreaseAe();
        dilutedFocusToDismiss();
    }

    public void showAeAdjust() {
        int x = Resources.getSystem().getDisplayMetrics().widthPixels / 2 - mFocusMarkerContainer.getWidth() / 2;
        int y = Resources.getSystem().getDisplayMetrics().heightPixels / 2 - mFocusMarkerContainer.getHeight() / 2;

        mFocusMarkerContainer.setTranslationX(x);
        mFocusMarkerContainer.setTranslationY(y);

        dilutedFocusToDismiss();
    }

    /**
     * 先减淡对焦框的颜色,然后5000ms后消失
     */
    private void dilutedFocusToDismiss() {
        if (mLocked) {
            return;
        }
        mFocusMarkerContainer.setAlpha(1);
        mFocusMarkerContainer
                .animate()
                .alpha(0.4f)
                .setStartDelay(750)
                .setDuration(800)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mFocusMarkerContainer.animate().alpha(0).setStartDelay(5000);
                    }
                });
    }

    /**
     * 作为拍摄照片完成时的屏幕闪烁白色动画
     */
    private void capture() {
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1f);
        animator.setDuration(200);
        animator.setInterpolator(new AccelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float fraction = (float) animation.getAnimatedValue();

                post(new Runnable() {
                    @Override
                    public void run() {
                        setBackgroundColor(
                                (Integer) argbEvaluator.evaluate(fraction,
                                        Color.WHITE,
                                        Color.TRANSPARENT));
                    }
                });
            }
        });
        animator.start();
    }

    private void focus(float mx, float my) {
        int x = (int) (mx - mFocusMarkerContainer.getWidth() / 2);
        int y = (int) (my - mFocusMarkerContainer.getWidth() / 2);

        mFocusMarkerContainer.setTranslationX(x);
        mFocusMarkerContainer.setTranslationY(y);

        mFocusMarkerContainer.setAlpha(1);

        mFocusLayout.setScaleX(1.3f);
        mFocusLayout.setScaleY(1.3f);
        mFocusLayout.setAlpha(0.6f);
        mFocusLayout.animate().scaleX(1).scaleY(1).alpha(1)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        dilutedFocusToDismiss();
                    }
                });

    }

    private void unLockAEandAF() {
        mLocked = false;
        mAEsun.resetAe();
    }

    private void lockAEandAF() {
        CameraUtil.show(getContext(), getContext().getString(R.string.ae_af_lock), mRotation);
        mFocusMarkerContainer.animate().cancel();
        mFocusMarkerContainer.setAlpha(1);
        mLocked = true;
    }

    public int getGridType() {
        return mGridType;
    }

    public void setGridType(int gridType) {
        mGridType = gridType;
    }

    private void initPaint() {
        mPaint = new Paint();
        mPaint.setColor(Color.WHITE);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(3);
        mPaint.setAntiAlias(true);

        mCenterBitmap = ((BitmapDrawable) ContextCompat.getDrawable(getContext(),
                R.drawable.ic_camera_photography_centel)).getBitmap();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        switch (mGridType) {
            case GRID_NONE:
                break;
            case GRID_GRID:
                drawGridLines(canvas);
                break;
            case GRID_GRID_AND_DIAGONAL:
                drawGridLines(canvas);
                //对角线
                canvas.drawLine(0, 0, getWidth(), getHeight(), mPaint);
                canvas.drawLine(getWidth(), 0, 0, getHeight(), mPaint);
                break;
            case GRID_CENTER_POINT:
                canvas.drawBitmap(mCenterBitmap,
                        getWidth() / 2 - mCenterBitmap.getWidth() / 2,
                        getHeight() / 2 - mCenterBitmap.getHeight() / 2,
                        mPaint);
                break;
        }
    }

    private void drawGridLines(Canvas canvas) {
        //横线
        canvas.drawLine(0, getHeight() / 3, getWidth(), getHeight() / 3, mPaint);
        canvas.drawLine(0, 2 * getHeight() / 3, getWidth(), 2 * getHeight() / 3, mPaint);
        //纵线
        canvas.drawLine(getWidth() / 3, 0, getWidth() / 3, getHeight(), mPaint);
        canvas.drawLine(2 * getWidth() / 3, 0, 2 * getWidth() / 3, getHeight(), mPaint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BROADCAST_ACTION_TAKE_PHOTO);
        intentFilter.addAction(BROADCAST_ACTION_RECORING_STOP);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mLocalBroadcast, intentFilter);
    }

    @Override
    protected void onDetachedFromWindow() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mLocalBroadcast);
        super.onDetachedFromWindow();
    }

    /**
     * 使用广播接收{@link Camera2#mOnImageAvailableListener}拍照完成的回调,以便调用本来的capture()方法.
     * 播放背景色闪烁动画.
     * <p>
     * 之所以使用广播的方式,是因为在{@link CameraView}中的CallbackBridge是静态方法,无法直接调用本类的{@link #capture()}
     */
    private BroadcastReceiver mLocalBroadcast = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.isEmpty(intent.getAction())) {
                return;
            }
            switch (intent.getAction()) {
                case BROADCAST_ACTION_TAKE_PHOTO:
                    capture();
                    break;
                case BROADCAST_ACTION_RECORING_STOP:
                    mFocusMarkerContainer.setAlpha(0);
                    break;
            }
        }
    };

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mImpl == null) {
            return true;
        }
        if (mImpl.isManualMode()) {
            return true;
        }
        mGestureDetector.onTouchEvent(event);
        if (mEnableScaleZoom) {
            mScaleGestureDetector.onTouchEvent(event);
        }
        return true;
    }

    private class ScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (mImpl.isManualWTSupported()) {
                mImpl.gestureScaleZoom(detector.getScaleFactor());
            }
            return true;
        }
    }

    /**
     * 处理:
     * 1.点击对焦
     * 2.长按锁定AE/AF
     * 3.上下滑动调节AE
     */
    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public void onLongPress(MotionEvent e) {
            unLockAEandAF();
            mImpl.unLockAEandAF();
            focus(e.getX(), e.getY());
            lockAEandAF();
            mImpl.resetAF(e, true);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            unLockAEandAF();
            focus(e.getX(), e.getY());
            mImpl.unLockAEandAF();
            mImpl.resetAF(e, false);
            return super.onSingleTapUp(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (e2.getPointerCount() > 1) {
                return true;
            }
            if (!canScrollAEchanged()) {
                return true;
            }
            if (mAEsun.getMaxAe() == 0) {
                if (mImpl.getAERange() == null) {
                    return true;
                }
                mAEsun.setMaxAeRange(mImpl.getAERange().getUpper());
            }
            if (mRotation == 0) {
                adjustAe((int) distanceX);
            } else if (mRotation == 2) {
                adjustAe((int) -distanceX);
            } else if (mRotation == 1) {
                adjustAe((int) -distanceY);
            } else if (mRotation == 3) {
                adjustAe((int) distanceY);
            }
            return true;
        }
    }

    private void adjustAe(int distance) {
        if (distance > 0) {
            increaseAe();
        } else if (distance < 0) {
            decreaseAe();
        }
    }
}