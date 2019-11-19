package cn.zhengshang.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import cn.zhengshang.cameraview.R;

/**
 * Created by shangzheng on 2017/11/16
 * ☃☃☃ 16:35.
 */

public class AEsun extends View {

    /**
     * 太阳边缘距离黄线的margin
     */
    private static final int SUN_EDGE_MARGIN = 4;
    private int maxAe;
    private int mCenterX;
    private Paint mPaint;
    /**
     * 中间太阳的icon
     */
    private Bitmap mSunBitmap;
    private int mSunBitmapHeight;
    /**
     * 黄线的中间的端点距离太阳中心点的距离
     * -----------  sun  ------------
     */
    private int mHalfDistance;
    /**
     * 太阳处在线段的进度.默认50%
     */
    private int mProgress = 50;
    /**
     * 太阳的左边界坐标
     */
    private int mSunLeftCoor;
    /**
     * 太阳的右边界坐标
     */
    private int mSunTopCoor;
    /**
     * 顶部线段的两个端点的y坐标
     */
    private Point mTopLineSegment;
    /**
     * 底部线段的两个端点的y坐标
     */
    private Point mBottomLineSegment;

    private boolean mIsShowLine;

    private OnAEChangeListener mOnAEChangeListener;

    public AEsun(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        mPaint = new Paint();
        mPaint.setColor(Color.parseColor("#ffcc00"));

        mTopLineSegment = new Point();
        mBottomLineSegment = new Point();

        mSunBitmap = ((BitmapDrawable) ContextCompat.getDrawable(getContext(), R.drawable.ic_sun)).getBitmap();
        mSunBitmapHeight = mSunBitmap.getHeight();
        mHalfDistance = mSunBitmapHeight / 2 + SUN_EDGE_MARGIN;

    }

    private Runnable mHideLineTask = new Runnable() {
        @Override
        public void run() {
            if (isAttachedToWindow()) {
                hideLine();
                invalidate();
            }
        }
    };

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mCenterX = getWidth() / 2;
        updateParms();
    }

    @SuppressWarnings("SuspiciousNameCombination")
    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawBitmap(mSunBitmap, mSunLeftCoor, mSunTopCoor, mPaint);

        if (!mIsShowLine) {
            return;
        }

        if (mSunTopCoor <= mHalfDistance) {
            //only draw bottom line segment
            canvas.drawLine(mCenterX, mBottomLineSegment.x, mCenterX, mBottomLineSegment.y, mPaint);
        } else if (mSunTopCoor >= getHeight() - mHalfDistance - mSunBitmapHeight) {
            //only draw top line segment
            canvas.drawLine(mCenterX, mTopLineSegment.x, mCenterX, mTopLineSegment.y, mPaint);
        } else {
            //top line segment
            canvas.drawLine(mCenterX, mTopLineSegment.x, mCenterX, mTopLineSegment.y, mPaint);
            //bottom line segment
            canvas.drawLine(mCenterX, mBottomLineSegment.x, mCenterX, mBottomLineSegment.y, mPaint);
        }
    }

    private void showLine() {
        mIsShowLine = true;
    }

    private void hideLine() {
        mIsShowLine = false;
    }

    public void setMaxAeRange(int maxAe) {
        this.maxAe = maxAe;
    }

    public int getMaxAe() {
        return maxAe;
    }

    private void calcuCurrentAe() {
        //progress==100的时候,其实是ae最小的时候,所以如下的计算去反值
        int ae = -(mProgress - 50) * maxAe / 50;
        if (mOnAEChangeListener != null) {
            mOnAEChangeListener.onAEchanged(ae);
        }
    }

    public void resetAe() {
        setProgress(50);
        hideLine();
    }

    public void increaseAe() {
        if (mProgress < 0) {
            return;
        }
        mProgress--;
        setProgress(mProgress);
    }

    public void decreaseAe() {
        if (mProgress > 100) {
            return;
        }
        mProgress++;
        setProgress(mProgress);
    }

    public void setProgress(int progress) {
        mProgress = progress;
        updateParms();
        showLine();
        invalidate();
        calcuCurrentAe();
        removeCallbacks(mHideLineTask);
        postDelayed(mHideLineTask, 2000);
    }

    private void updateParms() {
        mSunLeftCoor = mCenterX - mSunBitmap.getWidth() / 2;
        mSunTopCoor = (getHeight() - 2 * mHalfDistance) * mProgress / 100;

        mTopLineSegment.x = mHalfDistance;
        mTopLineSegment.y = mSunTopCoor - SUN_EDGE_MARGIN;
        mBottomLineSegment.x = mSunTopCoor + mSunBitmapHeight + SUN_EDGE_MARGIN;
        mBottomLineSegment.y = getHeight() - mHalfDistance;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(2 * mHalfDistance, heightMeasureSpec);
    }

    public void setOnAEChangeListener(OnAEChangeListener onAEChangeListener) {
        mOnAEChangeListener = onAEChangeListener;
    }

    interface OnAEChangeListener {
        void onAEchanged(int ae);
    }
}
