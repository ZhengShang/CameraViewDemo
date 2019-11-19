package cn.zhengshang.util;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.view.Surface;
import android.widget.TextView;

/**
 * Created by shangzheng on 2017/8/30
 * ☃☃☃ 19:45.
 * <p>
 * 文本可以朝着4个不同方向选择的TextView
 * 默认{@link android.view.Surface#ROTATION_270}为初始方向.
 * 主要用途:
 * 在CameraActivity2中,
 * 调用ToastUtils.show(int, int)方法,让Toast始终显示在当前方向的下面,
 * 以使弹出的Toast显示的方向和预期的一致
 */

public class RotateTextView extends TextView {
    /**
     * 当前屏幕的方向
     */
    private int direction = Surface.ROTATION_270;

    public RotateTextView(Context context) {
        super(context);
    }

    public RotateTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        TextPaint textPaint = getPaint();
        textPaint.setColor(getCurrentTextColor());
        textPaint.drawableState = getDrawableState();

        canvas.save();

        if (direction == 0) {
            canvas.translate(0, getHeight());
            canvas.rotate(-90);
        } else if (direction == 1) {
            canvas.translate(getWidth(), getHeight());
            canvas.rotate(180);
        } else if (direction == 2) {
            canvas.translate(getWidth(), 0);
            canvas.rotate(90);
        }

        canvas.translate(getCompoundPaddingLeft(), getExtendedPaddingTop());

        getLayout().draw(canvas);
        canvas.restore();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (direction == 0 || direction == 2) {
            super.onMeasure(heightMeasureSpec, widthMeasureSpec);
            setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
}
