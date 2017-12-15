package com.zhiyun.android.util;

import android.content.Context;
import android.graphics.Color;
import android.hardware.camera2.params.RggbChannelVector;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.widget.Toast;

import com.zhiyun.android.base.PreviewImpl;
import com.zhiyun.android.base.Size;
import com.zhiyun.android.cameraview.BuildConfig;
import com.zhiyun.android.cameraview.R;

import java.util.SortedSet;

import static android.content.ContentValues.TAG;

/**
 * Created by shangzheng on 2017/8/14
 * ☃☃☃ 16:24.
 */

public class CameraUtil {

    public static int clamp(int x, int min, int max) {
        if (x < min) {
            return min;
        } else if (x > max) {
            return max;
        } else {
            return x;
        }
    }

    /**
     * Converts a white balance temperature to red, green even, green odd and blue components.
     */
    public static RggbChannelVector convertTemperatureToRggb(int temperature_kelvin) {
        float temperature = temperature_kelvin / 100.0f;
        float red;
        float green;
        float blue;

        if (temperature <= 66) {
            red = 255;
        } else {
            red = temperature - 60;
            red = (float) (329.698727446 * (Math.pow((double) red, -0.1332047592)));
            if (red < 0) {
                red = 0;
            }
            if (red > 255) {
                red = 255;
            }
        }

        if (temperature <= 66) {
            green = temperature;
            green = (float) (99.4708025861 * Math.log(green) - 161.1195681661);
            if (green < 0) {
                green = 0;
            }
            if (green > 255) {
                green = 255;
            }
        } else {
            green = temperature - 60;
            green = (float) (288.1221695283 * (Math.pow((double) green, -0.0755148492)));
            if (green < 0) {
                green = 0;
            }
            if (green > 255) {
                green = 255;
            }
        }

        if (temperature >= 66) {
            blue = 255;
        } else if (temperature <= 19) {
            blue = 0;
        } else {
            blue = temperature - 10;
            blue = (float) (138.5177312231 * Math.log(blue) - 305.0447927307);
            if (blue < 0) {
                blue = 0;
            }
            if (blue > 255) {
                blue = 255;
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "red: " + red);
            Log.d(TAG, "green: " + green);
            Log.d(TAG, "blue: " + blue);
        }
        return new RggbChannelVector((red / 255) * 2, (green / 255),
                (green / 255), (blue / 255) * 2);
    }

    public static RggbChannelVector colorTemperature(int whiteBalance) {
        float temperature = whiteBalance / 100;
        float red;
        float green;
        float blue;

        //Calculate red
        if (temperature <= 66)
            red = 255;
        else {
            red = temperature - 60;
            red = (float) (329.698727446 * (Math.pow((double) red, -0.1332047592)));
            if (red < 0)
                red = 0;
            if (red > 255)
                red = 255;
        }


        //Calculate green
        if (temperature <= 66) {
            green = temperature;
            green = (float) (99.4708025861 * Math.log(green) - 161.1195681661);
            if (green < 0)
                green = 0;
            if (green > 255)
                green = 255;
        } else {
            green = temperature - 60;
            green = (float) (288.1221695283 * (Math.pow((double) green, -0.0755148492)));
            if (green < 0)
                green = 0;
            if (green > 255)
                green = 255;
        }

        //calculate blue
        if (temperature >= 66)
            blue = 255;
        else if (temperature <= 19)
            blue = 0;
        else {
            blue = temperature - 10;
            blue = (float) (138.5177312231 * Math.log(blue) - 305.0447927307);
            if (blue < 0)
                blue = 0;
            if (blue > 255)
                blue = 255;
        }

        Log.v(TAG, "red=" + red + ", green=" + green + ", blue=" + blue);
        return new RggbChannelVector((red / 255) * 2, (green / 255), (green / 255), (blue / 255) * 2);
    }


    /**
     * Chooses the optimal preview size based on PreviewSizes and the surface size.
     *
     * @param preview
     * @param aspectSizes
     * @return The picked size for camera preview.
     */
    public static Size chooseOptimalSize(PreviewImpl preview, SortedSet<Size> aspectSizes) {
        int surfaceLonger, surfaceShorter;
        final int surfaceWidth = preview.getWidth();
        final int surfaceHeight = preview.getHeight();
        if (surfaceWidth < surfaceHeight) {
            surfaceLonger = surfaceHeight;
            surfaceShorter = surfaceWidth;
        } else {
            surfaceLonger = surfaceWidth;
            surfaceShorter = surfaceHeight;
        }

        // Pick the smallest of those big enough
        for (Size size : aspectSizes) {
            if (size.getWidth() >= surfaceLonger && size.getHeight() >= surfaceShorter) {
                return size;
            }
        }
        // If no size is big enough, pick the largest one.
        return aspectSizes.last();
    }

    private static boolean isRotation0(int orientation) {
        return orientation > 340 || orientation < 20;
    }

    private static boolean isRotation90(int orientation) {
        return orientation > 70 && orientation < 110;
    }

    private static boolean isRotation180(int orientation) {
        return orientation > 160 && orientation < 200;
    }

    private static boolean isRotation270(int orientation) {
        return orientation > 250 && orientation < 290;
    }

    /**
     * @param oriRotation 当前的朝向
     */
    public static int translate2Rotation(int orientation,int oriRotation) {
        int rotation;
        if (isRotation270(orientation)) {
            rotation = Surface.ROTATION_270;
        } else if (isRotation180(orientation)) {
            rotation = Surface.ROTATION_180;
        } else if (isRotation90(orientation)) {
            rotation = Surface.ROTATION_90;
        } else if (isRotation0(orientation)) {
            rotation = Surface.ROTATION_0;
        } else {
            //在临界值中间的角度时,
            rotation = oriRotation;
        }
        return rotation;
    }

    /**
     * 根据传入的当前方向,以一定的角度来显示Toast
     *
     * @param message  显示的文字
     * @param rotation 当前屏幕的方向,应当传入如下四个值之一
     *
     * @see android.view.Surface#ROTATION_0
     * @see android.view.Surface#ROTATION_90
     * @see android.view.Surface#ROTATION_180
     * @see android.view.Surface#ROTATION_270
     */
    public static void show(Context context,String message, int rotation) {
        RotateTextView textView = new RotateTextView(context);
        textView.setText(message);
        textView.setTextColor(Color.BLACK);
        textView.setBackgroundResource(R.drawable.shape_rectangle_yellow_16_corner);
        textView.setDirection(rotation);

        Toast toast = new Toast(context);
        toast.setView(textView);
        toast.setDuration(Toast.LENGTH_SHORT);
        if (rotation == 0) {
            toast.setGravity(Gravity.START, 300, -30);
        } else if (rotation == 1) {
            toast.setGravity(Gravity.BOTTOM, 30, 100);
        } else if (rotation == 2) {
            toast.setGravity(Gravity.END, 300, -30);
        } else {
            toast.setGravity(Gravity.TOP, 30, 100);
        }
        toast.show();
    }
}
