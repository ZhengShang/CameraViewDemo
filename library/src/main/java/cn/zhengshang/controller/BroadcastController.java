package cn.zhengshang.controller;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import static cn.zhengshang.base.Constants.BROADCAST_ACTION_RECORING_STOP;
import static cn.zhengshang.base.Constants.BROADCAST_ACTION_SWITCH_TO_HIGH_SPEED_VIDEO;
import static cn.zhengshang.base.Constants.BROADCAST_ACTION_TAKE_PHOTO;

public class BroadcastController {

    /**
     * 发送拍照完成广播,以便在{@see FocusMarkerLayout#mLocalBroadcast}中接收,
     * 然后播放动画
     */
    public static void sendTakePhotoAction(Context context) {
        Intent intent = new Intent();
        intent.setAction(BROADCAST_ACTION_TAKE_PHOTO);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }


    /**
     * 用于在Camera2中录制视频完成时调用,因为锁定AE/AF录制完成后,会重置预览,此时需要清除界面上的对焦框
     */
    public static void sendRecordingStopAction(Context context) {
        Intent intent = new Intent();
        intent.setAction(BROADCAST_ACTION_RECORING_STOP);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    /**
     * 发送切换高帧率录制视频的广播
     */
    public static void sendSwitchToHighSpeedVideoAction(Context context) {
        Intent intent = new Intent();
        intent.setAction(BROADCAST_ACTION_SWITCH_TO_HIGH_SPEED_VIDEO);
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

}
