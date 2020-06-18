package cn.zhengshang.controller;

import android.animation.ValueAnimator;

import cn.zhengshang.base.ICamera;

public class LensController {

    private ICamera mCamera;
    private ValueAnimator mSmoothFocusAnim;
    private ValueAnimator mSmoothZoomAnim;

    public LensController(ICamera camera) {
        mCamera = camera;
    }

    /**
     * 开始顺滑对焦
     * @param start 起始焦点af
     * @param end 结束焦点af
     * @param duration 变化的时间
     */
    public void startSmoothFocus(final float start, final float end, long duration) {
        stopSmoothFocus();
        if (start == end) {
            return;
        }
        if (mSmoothFocusAnim == null) {
            mSmoothFocusAnim = ValueAnimator.ofFloat(0, 1);
            mSmoothFocusAnim.setInterpolator(null);
        }
        mSmoothFocusAnim.removeAllUpdateListeners();
        mSmoothFocusAnim.addUpdateListener(animation -> {
            float value = (float) animation.getAnimatedValue();
            float af;
            af = start + value * (end - start);
            af = Math.round(af * 10) / 10f;
            mCamera.setAFValue(af);
        });
        mSmoothFocusAnim.setDuration(duration);
        mSmoothFocusAnim.start();
    }

    public void startSmoothZoom(float start, float end, long duration) {
        stopSmoothZoom();
        if (start == end) {
            return;
        }

        mSmoothZoomAnim = ValueAnimator.ofFloat(start, end);
        mSmoothZoomAnim.setInterpolator(null);
        mSmoothZoomAnim.removeAllUpdateListeners();
        mSmoothZoomAnim.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            mCamera.scaleZoom(scale);
        });
        mSmoothZoomAnim.setDuration(duration);
        mSmoothZoomAnim.start();
    }

    public void stopSmoothZoom() {
        if (mSmoothZoomAnim != null) {
            mSmoothZoomAnim.cancel();
        }
    }

    /**
     * 结束顺滑对焦
     */
    public void stopSmoothFocus() {
        if (mSmoothFocusAnim != null) {
            mSmoothFocusAnim.cancel();
        }
    }


    /**
     * AF拉近一个单位
     */
    public void foucsNear() {
        float af = mCamera.getAf();
        if (af > 0) {
            af -= 0.1;
            af = Math.round(af * 10) / 10f;
            mCamera.setAFValue(af);
        }
    }

    /**
     * AF推远一个单位
     */
    public void focusFar() {
        float af = mCamera.getAf();
        if (af < mCamera.getAFMaxValue()) {
            af += 0.1;
            af = Math.round(af * 10) / 10f;
            mCamera.setAFValue(af);
        }
    }

    /**
     * 双指在屏幕上缩放视图大小
     *
     * @param factor 缩放因子,用以判断放大和缩小
     */
    public void gestureScaleZoom(float factor) {
        float wt = mCamera.getWt();
        if (factor >= 1.001f) {
            if (wt >= mCamera.getMaxZoom()) {
                return;
            }
            wt += 0.1f;
            wt = Math.round(wt * 10) / 10f;
            mCamera.scaleZoom(wt);
        } else if (factor <= 0.999f) {
            if (wt <= 1) {
                return;
            }
            wt -= 0.1f;
            wt = Math.round(wt * 10) / 10f;
            mCamera.scaleZoom(wt);
        }
    }
}
