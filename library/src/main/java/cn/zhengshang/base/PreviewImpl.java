package cn.zhengshang.base;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;


/**
 * Encapsulates all the operations related to camera preview in a backward-compatible manner.
 */
public abstract class PreviewImpl {

    private Callback mCallback;

    private int mWidth;

    private int mHeight;

    private Runnable mDispatchCallback = () -> mCallback.onSurfaceChanged();

    void dispatchSurfaceChanged() {
        if (getView() != null) {
            getView().removeCallbacks(mDispatchCallback);
            getView().postDelayed(mDispatchCallback, 100);
        }
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public abstract Surface getSurface();

    public abstract View getView();

    public abstract Class getOutputClass();

    public abstract void setDisplayOrientation(int displayOrientation);

    public abstract boolean isReady();

    public SurfaceHolder getSurfaceHolder() {
        return null;
    }

    public SurfaceTexture getSurfaceTexture() {
        return null;
    }

    public void setBufferSize(int width, int height) {
    }

    void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    public Bitmap getFrameBitmap() {
        return null;
    }

    public Bitmap getFrameBitmap(int width, int height) {
        return null;
    }

    public interface Callback {
        void onSurfaceChanged();
    }

}
