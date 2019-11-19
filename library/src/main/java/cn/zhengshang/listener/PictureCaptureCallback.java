package cn.zhengshang.listener;

/**
 * Created by shangzheng on 2017/8/14
 * ☃☃☃ 16:31.
 */

import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;

import androidx.annotation.NonNull;

/**
 * A {@link CameraCaptureSession.CaptureCallback} for capturing a still picture.
 */
public abstract class PictureCaptureCallback extends CameraCaptureSession.CaptureCallback {

    public static final int STATE_PREVIEW = 0;
    public static final int STATE_LOCKING = 1;
    public static final int STATE_LOCKED = 2;
    public static final int STATE_PRECAPTURE = 3;
    public static final int STATE_WAITING = 4;
    public static final int STATE_CAPTURING = 5;

    private int mState;

    private OnManualValueListener mOnManualValueListener;


    public PictureCaptureCallback() {
    }

    public void setState(int state) {
        mState = state;
    }

    @Override
    public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                    @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
        process(partialResult);
    }

    @Override
    public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                   @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
        process(result);
    }

    private void process(@NonNull CaptureResult result) {
        onResultCallback(result);

        switch (mState) {
            case STATE_LOCKING: {
                Integer af = result.get(CaptureResult.CONTROL_AF_STATE);
                if (af == null) {
                    onReady();
                    break;
                }
                if (af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                        af == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                    Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                        setState(STATE_CAPTURING);
                        onReady();
                    } else {
                        setState(STATE_LOCKED);
                        onPrecaptureRequired();
                    }
                }
                break;
            }
            case STATE_PRECAPTURE: {
                Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                if (ae == null || ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        ae == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED ||
                        ae == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    setState(STATE_WAITING);
                }
                break;
            }
            case STATE_WAITING: {
                Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
                if (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                    setState(STATE_CAPTURING);
                    onReady();
                }
                break;
            }
        }
    }

    /**
     * Called when it is ready to take a still picture.
     */
    public abstract void onReady();

    /**
     * Called when it is necessary to run the precapture sequence.
     */
    public abstract void onPrecaptureRequired();

    /**
     * 返回CaptureResult,将当前的iso,快门速度等回到给外部
     */
    public abstract void onResultCallback(CaptureResult result);
}