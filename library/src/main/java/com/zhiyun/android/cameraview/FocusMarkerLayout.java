/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zhiyun.android.cameraview;

import static com.zhiyun.android.cameraview.CameraView.GRID_CENTER_POINT;
import static com.zhiyun.android.cameraview.CameraView.GRID_GRID;
import static com.zhiyun.android.cameraview.CameraView.GRID_GRID_AND_DIAGONAL;
import static com.zhiyun.android.cameraview.CameraView.GRID_NONE;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;

@TargetApi(14)
public class FocusMarkerLayout extends FrameLayout {

    private final Animation scaleAnimation, alphaAnimation;
    private FrameLayout mFocusMarkerContainer;
    private ImageView mOuterCircle;
    private ImageView mInnerCircle;
    private Paint mPaint;
    private int mGridType;
    private Bitmap mCenterBitmap;

    public FocusMarkerLayout(@NonNull Context context) {
        this(context, null);
    }

    public FocusMarkerLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        LayoutInflater.from(getContext()).inflate(R.layout.layout_focus_marker, this);

        initPaint();

        mFocusMarkerContainer = (FrameLayout) findViewById(R.id.focusMarkerContainer);
        mOuterCircle = (ImageView) findViewById(R.id.inner_circle);
        mInnerCircle = (ImageView) findViewById(R.id.outer_circle);

        mFocusMarkerContainer.setAlpha(0);

        alphaAnimation = AnimationUtils.loadAnimation(context, R.anim.focus_inner_circle);
        scaleAnimation = AnimationUtils.loadAnimation(context, R.anim.focus_outer_circle);

        scaleAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mFocusMarkerContainer.setAlpha(1f);
                mFocusMarkerContainer.animate().cancel();
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mFocusMarkerContainer.animate().alpha(0).setStartDelay(750).setDuration(
                        800).setListener(null).start();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    public void focus(float mx, float my) {
        int x = (int) (mx - mFocusMarkerContainer.getWidth() / 2);
        int y = (int) (my - mFocusMarkerContainer.getWidth() / 2);

        mFocusMarkerContainer.setTranslationX(x);
        mFocusMarkerContainer.setTranslationY(y);

        mInnerCircle.clearAnimation();
        mOuterCircle.clearAnimation();

        mInnerCircle.startAnimation(scaleAnimation);
        mOuterCircle.startAnimation(alphaAnimation);

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
                canvas.drawBitmap(mCenterBitmap, getWidth() / 2, getHeight() / 2, mPaint);
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
}