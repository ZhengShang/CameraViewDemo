package cn.zhengshang.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.camera2.params.RggbChannelVector;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.widget.Toast;

import java.io.File;
import java.util.List;
import java.util.SortedSet;

import cn.zhengshang.base.Constants;
import cn.zhengshang.base.PreviewImpl;
import cn.zhengshang.base.Size;
import cn.zhengshang.cameraview.BuildConfig;
import cn.zhengshang.cameraview.R;

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


    public static int rgbToKelvin(RggbChannelVector rgb) {
        float r = rgb.getRed();
        float b = rgb.getBlue();

        float temperature = 0;
        RggbChannelVector testRGB;
        float epsilon = 0.4f;
        float minTemperature = 1000;
        float maxTemperature = 40000;
        while (maxTemperature - minTemperature > epsilon) {
            temperature = (maxTemperature + minTemperature) / 2;
            testRGB = kelvinToRgb(temperature);
            if ((testRGB.getBlue() / testRGB.getRed()) >= (b / r)) {
                maxTemperature = temperature;
            } else {
                minTemperature = temperature;
            }
        }
        return Math.round(temperature);
    }

    public static RggbChannelVector kelvinToRgb(float kelvin) {
        double temperature = (kelvin / 100.0);
        double red, green, blue;
        if (temperature < 66.0) {
            red = 255;
        } else {
            // a + b x + c Log[x] /.
            // {a -> 351.97690566805693`,
            // b -> 0.114206453784165`,
            // c -> -40.25366309332127
            //x -> (kelvin/100) - 55}
            red = temperature - 55.0;
            red = 351.97690566805693 + 0.114206453784165 * red - 40.25366309332127 * Math.log(red);
            if (red < 0) red = 0;
            if (red > 255) red = 255;
        }
        /* Calculate green */
        if (temperature < 66.0) {
            // a + b x + c Log[x] /.
            // {a -> -155.25485562709179`,
            // b -> -0.44596950469579133`,
            // c -> 104.49216199393888`,
            // x -> (kelvin/100) - 2}
            green = temperature - 2;
            green = -155.25485562709179 - 0.44596950469579133 * green + 104.49216199393888 * Math.log(green);
            if (green < 0) green = 0;
            if (green > 255) green = 255;
        } else {
            // a + b x + c Log[x] /.
            // {a -> 325.4494125711974`,
            // b -> 0.07943456536662342`,
            // c -> -28.0852963507957`,
            // x -> (kelvin/100) - 50}
            green = temperature - 50.0;
            green = 325.4494125711974 + 0.07943456536662342 * green - 28.0852963507957 * Math.log(green);
            if (green < 0) green = 0;
            if (green > 255) green = 255;
        }
        /* Calculate blue */
        if (temperature >= 66.0) {
            blue = 255;
        } else {
            if (temperature <= 20.0) {
                blue = 0;
            } else {
                // a + b x + c Log[x] /.
                // {a -> -254.76935184120902`,
                // b -> 0.8274096064007395`,
                // c -> 115.67994401066147`,
                // x -> kelvin/100 - 10}
                blue = temperature - 10;
                blue = -254.76935184120902 + 0.8274096064007395 * blue + 115.67994401066147 * Math.log(blue);
                if (blue < 0) blue = 0;
                if (blue > 255) blue = 255;
            }
        }
        float r = Math.round(red);
        float g = Math.round(green);
        float b = Math.round(blue);
        return new RggbChannelVector(r, g, g, b);
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
    public static int translate2Rotation(int orientation, int oriRotation) {
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
     * @see Surface#ROTATION_0
     * @see Surface#ROTATION_90
     * @see Surface#ROTATION_180
     * @see Surface#ROTATION_270
     */
    public static void show(Context context, String message, int rotation) {
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

    /**
     * 根据传入的当前方向,以一定的角度来显示Toast
     *
     * @param message  显示的文字
     * @param rotation 当前屏幕的方向,应当传入如下四个值之一
     * @see Surface#ROTATION_0
     * @see Surface#ROTATION_90
     * @see Surface#ROTATION_180
     * @see Surface#ROTATION_270
     */
    public static void showBlackToast(Context context, String message, int rotation) {
        RotateTextView textView = new RotateTextView(context);
        textView.setText(message);
        textView.setTextColor(Color.WHITE);
        textView.setBackgroundResource(R.drawable.shape_black_toast);
        textView.setDirection(rotation);

        Toast toast = new Toast(context);
        toast.setView(textView);
        toast.setDuration(Toast.LENGTH_SHORT);
        if (rotation == 0) {
            toast.setGravity(Gravity.BOTTOM, 300, -30);
        } else if (rotation == 1) {
            toast.setGravity(Gravity.TOP, 30, 100);
        } else if (rotation == 2) {
            toast.setGravity(Gravity.START, 300, -30);
        } else {
            toast.setGravity(Gravity.BOTTOM, 30, 100);
        }
        toast.show();
    }

    /**
     * 根据手机品牌来确定是否需要调整快门速度的值
     * 因为目前测试发现,OPPO和VIVO的手机,在设置快门速度的时候,
     * 需要扩大1000倍才能设置成为显示的值.
     * 其他手机暂未测试
     * @return 是否是OPPO或者VIVO手机
     */
    public static boolean isOV() {
        String brand = Build.BRAND.toLowerCase();
        return brand.contains("oppo") || brand.contains("vivo");
    }

    /**
     * @param rect 原始大小的Rect
     * @param scale 比例
     * @return 返回一个Rect相对于中心点缩放某一比例之后的新Rect
     */
    public static Rect getScaledRect(Rect rect, float scale) {
        int halfWidth = (int) (rect.width() / scale / 2 + 0.5f);
        int halfHeight = (int) (rect.height() / scale / 2 + 0.5f);

        int l = rect.width() / 2 - halfWidth + rect.left;
        int t = rect.height() / 2 - halfHeight + rect.top;
        int r = rect.width() / 2 + halfWidth;
        int b = rect.height() / 2 + halfHeight;

        return new Rect(l, t, r, b);
    }


    //Calculate zoom area according input image size
    //Copy from OpenCamera
    public static Rect getZoomRect(float zoom, int imgWidth, int imgHeight) {
        int cropWidth = (int) (imgWidth / zoom) + 2 * 64;
        int cropHeight = (int) (imgHeight / zoom) + 2 * 64;
        // ensure crop w,h divisible by 4 (SZ requirement)
        cropWidth -= cropWidth & 3;
        cropHeight -= cropHeight & 3;
        // crop area for standard frame
        int cropWidthStd = cropWidth - 2 * 64;
        int cropHeightStd = cropHeight - 2 * 64;

        return new Rect((imgWidth - cropWidthStd) / 2, (imgHeight - cropHeightStd) / 2, (imgWidth + cropWidthStd) / 2,
                (imgHeight + cropHeightStd) / 2);
    }


    public static android.util.Size find4k(List<android.util.Size> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }

        android.util.Size largestSize = null;

        for (android.util.Size size : list) {
            if (size.equals(new android.util.Size(3840, 2160))) {
                return size;
            }

//            if (size.getWidth() >= 3840 || size.getHeight() >= 2160) {
//                largestSize = size;
//            }
        }
        return largestSize;
    }

    /**
     * 返回可用存储空间大小.（单位： MB）
     */
    public static long getAvailableSpace() {
        File root = Environment.getExternalStorageDirectory();
        StatFs statFs;
        try {
            //某些手机上(Moto G Turbo Edition..)可能会会出现No such file or directory的错误.
            //所以直接返回0. 阻止其录像.
            statFs = new StatFs(root.getAbsolutePath());
        } catch (Exception e) {
            return 0;
        }
        // 获取单个数据块的大小
        long blockSize = statFs.getBlockSizeLong();
        // 获取空闲数据块数量
        long availableBlocks = statFs.getAvailableBlocksLong();

        return (blockSize * availableBlocks) / 1024 / 1024;
    }

    /**
     * 添加记录到媒体库
     * @param context    context
     * @param path        文件绝对路径
     */
    public static void addToMediaStore(Context context, String path) {
        Intent sanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri uri = Uri.fromFile(new File(path));
        sanIntent.setData(uri);
        context.sendBroadcast(sanIntent);
    }

    /**
     * 手机剩余空间是否不够.空间不够的话,无法进行视频录制.
     * @return TRUE不够, FALSE够.
     */
    public static boolean lowAvailableSpace(final Context context, final int rotation) {
        long availableSpace = CameraUtil.getAvailableSpace();
        if (availableSpace <= Constants.LOW_STORAGE_THRESHOLD_BYTES) {
            //Show Toast in UI thread
            if (Looper.myLooper() == Looper.getMainLooper()) {
                CameraUtil.showBlackToast(context, context.getString(R.string.spaceIsLow_content), rotation);
            } else {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        CameraUtil.showBlackToast(context, context.getString(R.string.spaceIsLow_content), rotation);
                    }
                });
            }
            return true;
        }
        return false;
    }

    /**
     * 如果传入的文件为空文件(文件存在,但大小为0kb),则删除掉
     * @param filePath 文件路径
     */
    public static void deleteEmptyFIle(String filePath) {
        if (TextUtils.isEmpty(filePath)) {
            return;
        }
        File file = new File(filePath);
        //10kb以下的视频, 不要了
        if (file.isFile() && file.length() <= 10000) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }


    public static Rect convertRectFromCamera2(Rect crop_rect, Rect camera2_rect) {
        // inverse of convertRectToCamera2()
        double left_f = (camera2_rect.left - crop_rect.left) / (double) (crop_rect.width() - 1);
        double top_f = (camera2_rect.top - crop_rect.top) / (double) (crop_rect.height() - 1);
        double right_f = (camera2_rect.right - crop_rect.left) / (double) (crop_rect.width() - 1);
        double bottom_f = (camera2_rect.bottom - crop_rect.top) / (double) (crop_rect.height() - 1);
        int left = (int) (left_f * 2000) - 1000;
        int right = (int) (right_f * 2000) - 1000;
        int top = (int) (top_f * 2000) - 1000;
        int bottom = (int) (bottom_f * 2000) - 1000;

        left = Math.max(left, -1000);
        right = Math.max(right, -1000);
        top = Math.max(top, -1000);
        bottom = Math.max(bottom, -1000);
        left = Math.min(left, 1000);
        right = Math.min(right, 1000);
        top = Math.min(top, 1000);
        bottom = Math.min(bottom, 1000);

        return new Rect(left, top, right, bottom);
    }

    /**
     * Stack Blur v1.0 from
     * http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html
     * Java Author: Mario Klingemann <mario at quasimondo.com>
     * http://incubator.quasimondo.com
     *
     * created Feburary 29, 2004
     * Android port : Yahel Bouaziz <yahel at kayenko.com>
     * http://www.kayenko.com
     * ported april 5th, 2012
     *
     * This is a compromise between Gaussian Blur and Box blur
     * It creates much better looking blurs than Box Blur, but is
     * 7x faster than my Gaussian Blur implementation.
     *
     * I called it Stack Blur because this describes best how this
     * filter works internally: it creates a kind of moving stack
     * of colors whilst scanning through the image. Thereby it
     * just has to add one new block of color to the right side
     * of the stack and remove the leftmost color. The remaining
     * colors on the topmost layer of the stack are either added on
     * or reduced by one, depending on if they are on the right or
     * on the left side of the stack.
     *
     * If you are using this algorithm in your code please add
     * the following line:
     * Stack Blur Algorithm by Mario Klingemann <mario@quasimondo.com>
     */
    public static Bitmap fastblur(Bitmap sentBitmap, float scale, int radius) {

        int width = Math.round(sentBitmap.getWidth() * scale);
        int height = Math.round(sentBitmap.getHeight() * scale);
        sentBitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false);

        Bitmap bitmap = sentBitmap.copy(sentBitmap.getConfig(), true);

        if (radius < 1) {
            return (null);
        }

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        int[] pix = new int[w * h];
        Log.e("pix", w + " " + h + " " + pix.length);
        bitmap.getPixels(pix, 0, w, 0, 0, w, h);

        int wm = w - 1;
        int hm = h - 1;
        int wh = w * h;
        int div = radius + radius + 1;

        int r[] = new int[wh];
        int g[] = new int[wh];
        int b[] = new int[wh];
        int rsum, gsum, bsum, x, y, i, p, yp, yi, yw;
        int vmin[] = new int[Math.max(w, h)];

        int divsum = (div + 1) >> 1;
        divsum *= divsum;
        int dv[] = new int[256 * divsum];
        for (i = 0; i < 256 * divsum; i++) {
            dv[i] = (i / divsum);
        }

        yw = yi = 0;

        int[][] stack = new int[div][3];
        int stackpointer;
        int stackstart;
        int[] sir;
        int rbs;
        int r1 = radius + 1;
        int routsum, goutsum, boutsum;
        int rinsum, ginsum, binsum;

        for (y = 0; y < h; y++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            for (i = -radius; i <= radius; i++) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))];
                sir = stack[i + radius];
                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);
                rbs = r1 - StrictMath.abs(i);
                rsum += sir[0] * rbs;
                gsum += sir[1] * rbs;
                bsum += sir[2] * rbs;
                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }
            }
            stackpointer = radius;

            for (x = 0; x < w; x++) {

                r[yi] = dv[rsum];
                g[yi] = dv[gsum];
                b[yi] = dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm);
                }
                p = pix[yw + vmin[x]];

                sir[0] = (p & 0xff0000) >> 16;
                sir[1] = (p & 0x00ff00) >> 8;
                sir[2] = (p & 0x0000ff);

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[(stackpointer) % div];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi++;
            }
            yw += w;
        }
        for (x = 0; x < w; x++) {
            rinsum = ginsum = binsum = routsum = goutsum = boutsum = rsum = gsum = bsum = 0;
            yp = -radius * w;
            for (i = -radius; i <= radius; i++) {
                yi = Math.max(0, yp) + x;

                sir = stack[i + radius];

                sir[0] = r[yi];
                sir[1] = g[yi];
                sir[2] = b[yi];

                rbs = r1 - StrictMath.abs(i);

                rsum += r[yi] * rbs;
                gsum += g[yi] * rbs;
                bsum += b[yi] * rbs;

                if (i > 0) {
                    rinsum += sir[0];
                    ginsum += sir[1];
                    binsum += sir[2];
                } else {
                    routsum += sir[0];
                    goutsum += sir[1];
                    boutsum += sir[2];
                }

                if (i < hm) {
                    yp += w;
                }
            }
            yi = x;
            stackpointer = radius;
            for (y = 0; y < h; y++) {
                // Preserve alpha channel: ( 0xff000000 & pix[yi] )
                pix[yi] = (0xff000000 & pix[yi]) | (dv[rsum] << 16) | (dv[gsum] << 8) | dv[bsum];

                rsum -= routsum;
                gsum -= goutsum;
                bsum -= boutsum;

                stackstart = stackpointer - radius + div;
                sir = stack[stackstart % div];

                routsum -= sir[0];
                goutsum -= sir[1];
                boutsum -= sir[2];

                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w;
                }
                p = x + vmin[y];

                sir[0] = r[p];
                sir[1] = g[p];
                sir[2] = b[p];

                rinsum += sir[0];
                ginsum += sir[1];
                binsum += sir[2];

                rsum += rinsum;
                gsum += ginsum;
                bsum += binsum;

                stackpointer = (stackpointer + 1) % div;
                sir = stack[stackpointer];

                routsum += sir[0];
                goutsum += sir[1];
                boutsum += sir[2];

                rinsum -= sir[0];
                ginsum -= sir[1];
                binsum -= sir[2];

                yi += w;
            }
        }

        Log.e("pix", w + " " + h + " " + pix.length);
        bitmap.setPixels(pix, 0, w, 0, 0, w, h);

        return (bitmap);
    }
}
