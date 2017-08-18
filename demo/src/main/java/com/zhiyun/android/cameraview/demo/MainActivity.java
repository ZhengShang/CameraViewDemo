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

package com.zhiyun.android.cameraview.demo;

import android.Manifest;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Range;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.zhiyun.android.base.AspectRatio;
import com.zhiyun.android.cameraview.CameraView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;


/**
 * This demo app saves the taken picture to a constant file.
 * $ adb pull /sdcard/Android/data/com.google.android.cameraview.demo/files/Pictures/picture.jpg
 */
public class MainActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback,
        AspectRatioFragment.Listener {

    private static final String TAG = "MainActivity";

    private static final int REQUEST_CAMERA_PERMISSION = 1;

    private static final String FRAGMENT_DIALOG = "dialog";

    private static final int[] FLASH_OPTIONS = {
            CameraView.FLASH_AUTO,
            CameraView.FLASH_OFF,
            CameraView.FLASH_ON,
    };

    private static final int[] FLASH_ICONS = {
            R.drawable.ic_flash_auto,
            R.drawable.ic_flash_off,
            R.drawable.ic_flash_on,
    };

    private static final int[] FLASH_TITLES = {
            R.string.flash_auto,
            R.string.flash_off,
            R.string.flash_on,
    };

    private boolean isManualMode;
    private int manualMode;
    private long minValue, maxValue;

    private int mCurrentFlash;

    private CameraView mCameraView;

    private Handler mBackgroundHandler;

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.take_picture:
                    if (mCameraView != null) {
                        mCameraView.takePicture();
                    }
                    break;
                case R.id.record_video:
                    if (mCameraView != null) {
                        mCameraView.setVideoOutputFilePath(getFilePath(true));
                        if (mCameraView.isRecordingVideo()) {
                            mCameraView.stopRecordingVideo();
                        } else {
                            mCameraView.startRecordingVideo();
                        }
                    }
                    break;
            }
        }
    };
    private FloatingActionButton fabVideo;
    private TextView manualTitle, manualValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCameraView = (CameraView) findViewById(R.id.camera);
        if (mCameraView != null) {
            mCameraView.addCallback(mCallback);
        }
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.take_picture);
        if (fab != null) {
            fab.setOnClickListener(mOnClickListener);
        }

        fabVideo = (FloatingActionButton) findViewById(R.id.record_video);
        fabVideo.setOnClickListener(mOnClickListener);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }

        Switch manualSwitch = (Switch) findViewById(R.id.switch_manual);
        manualSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mCameraView != null) {
                    isManualMode = isChecked;
                    mCameraView.setManualMode(isManualMode);
                }
            }
        });

        manualValue = (TextView) findViewById(R.id.manual_text_value);
        manualTitle = (TextView) findViewById(R.id.manual_text);
        SeekBar aeSeekbar = (SeekBar) findViewById(R.id.ae_seekbar);
        aeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                Log.e("MainActivity", "onProgressChanged: progress = " + (progress + min));
//                mCameraView.setAEValue(progress + min);
                double realValue = (progress * (maxValue - minValue) / 100 + minValue);
                Log.e("MainActivity", "onProgressChanged: realValue = " + realValue);
                updateChangesToCamera(realValue);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
//                seekBar.setMax((int) (maxValue-minValue));
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private void updateChangesToCamera(double progress) {
        switch (manualMode) {
            case 1: //ae
                mCameraView.setAEValue((int) progress);
                break;
            case 2://sec
                mCameraView.setSecValue((long) progress);
                break;
            case 3://iso
                mCameraView.setISOValue((int) progress);
                break;
            case 4://wb
                mCameraView.setManualWBValue((int) progress);
                break;
            case 5://af
                mCameraView.setManualAFValue((float) progress);
                break;
            case 6://wt
                mCameraView.setWTlen((int) progress);
                break;
            default:
                break;
        }
        manualValue.setText(String.valueOf(progress));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            mCameraView.start();
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ConfirmationDialogFragment
                    .newInstance(R.string.camera_permission_confirmation,
                            new String[]{Manifest.permission.CAMERA},
                            REQUEST_CAMERA_PERMISSION,
                            R.string.camera_permission_not_granted)
                    .show(getSupportFragmentManager(), FRAGMENT_DIALOG);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    protected void onPause() {
        mCameraView.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBackgroundHandler != null) {
            mBackgroundHandler.getLooper().quitSafely();
            mBackgroundHandler = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION:
                if (permissions.length != 1 || grantResults.length != 1) {
                    throw new RuntimeException("Error on requesting camera permission.");
                }
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.camera_permission_not_granted,
                            Toast.LENGTH_SHORT).show();
                }
                // No need to start camera here; it is handled by onResume
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.aspect_ratio:
                FragmentManager fragmentManager = getSupportFragmentManager();
                if (mCameraView != null
                        && fragmentManager.findFragmentByTag(FRAGMENT_DIALOG) == null) {
                    final Set<AspectRatio> ratios = mCameraView.getSupportedAspectRatios();
                    final AspectRatio currentRatio = mCameraView.getAspectRatio();
                    AspectRatioFragment.newInstance(ratios, currentRatio)
                            .show(fragmentManager, FRAGMENT_DIALOG);
                }
                return true;
            case R.id.switch_flash:
                if (mCameraView != null) {
                    mCurrentFlash = (mCurrentFlash + 1) % FLASH_OPTIONS.length;
                    item.setTitle(FLASH_TITLES[mCurrentFlash]);
                    item.setIcon(FLASH_ICONS[mCurrentFlash]);
                    mCameraView.setFlash(FLASH_OPTIONS[mCurrentFlash]);
                }
                return true;
            case R.id.switch_camera:
                if (mCameraView != null) {
                    int facing = mCameraView.getFacing();
                    mCameraView.setFacing(facing == CameraView.FACING_FRONT ?
                            CameraView.FACING_BACK : CameraView.FACING_FRONT);
                }
                return true;
            case R.id.grid_none:
                mCameraView.setGridType(CameraView.GRID_NONE);
                break;
            case R.id.grid_grid:
                mCameraView.setGridType(CameraView.GRID_GRID);
                break;
            case R.id.grid_grid_duijiaoxian:
                mCameraView.setGridType(CameraView.GRID_GRID_AND_DIAGONAL);
                break;
            case R.id.grid_centere:
                mCameraView.setGridType(CameraView.GRID_CENTER_POINT);
                break;
            case R.id.awb:
                setAwb();
                break;
            case R.id.manual_ae:
                if (mCameraView.isManualAESupported()) {
                    Range<Integer> aeRange = mCameraView.getAERange();
                    maxValue = aeRange.getUpper();
                    minValue = aeRange.getLower();
                    manualTitle.setText(item.getTitle());
                    manualMode = 1;
                } else {
                    item.setEnabled(false);
                }
                break;
            case R.id.manual_sec:
                if (mCameraView.isManualSecSupported()) {
                    Range<Long> secRange = mCameraView.getSecRange();
                    maxValue = secRange.getUpper();
                    minValue = secRange.getLower();
                    manualTitle.setText(item.getTitle());
                    manualMode = 2;
                } else {
                    item.setEnabled(false);
                }
                break;
            case R.id.manual_iso:
                if (mCameraView.isManualISOSupported()) {
                    Range<Integer> isoRange = mCameraView.getISORange();
                    maxValue = isoRange.getUpper();
                    minValue = isoRange.getLower();
                    manualTitle.setText(item.getTitle());
                    manualMode = 3;
                } else {
                    item.setEnabled(false);
                }
                break;
            case R.id.manual_wb:
                if (mCameraView.isManualWBSupported()) {
                    Range<Integer> wbRange = mCameraView.getManualWBRange();
                    maxValue = wbRange.getUpper();
                    minValue = wbRange.getLower();
                    manualTitle.setText(item.getTitle());
                    manualMode = 4;
                } else {
                    item.setEnabled(false);
                }
                break;
            case R.id.manual_af:
                if (mCameraView.isManualAFSupported()) {
                    minValue = 0;
                    maxValue = (long) mCameraView.getAFMaxValue();
                    manualTitle.setText(item.getTitle());
                } else {
                    item.setEnabled(false);
                }
                manualTitle.setText(item.getTitle());
                manualMode = 5;
                break;
            case R.id.manual_wt:
                minValue = 0;
                maxValue = 200;
                manualTitle.setText(item.getTitle());
                manualMode = 6;
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    private void setAwb() {
        int[] modes = mCameraView.getSupportAWBModes();
        CharSequence[] charSequences = new CharSequence[modes.length];
        for (int i = 0; i < modes.length; i++) {
            charSequences[i] = modes[i] + "";
        }
        new AlertDialog.Builder(this)
                .setItems(
                        charSequences,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mCameraView.setAWBMode(which);
                            }
                        })
                .show();
    }

    @Override
    public void onAspectRatioSelected(@NonNull AspectRatio ratio) {
        if (mCameraView != null) {
            Toast.makeText(this, ratio.toString(), Toast.LENGTH_SHORT).show();
            mCameraView.setAspectRatio(ratio);
        }
    }

    private Handler getBackgroundHandler() {
        if (mBackgroundHandler == null) {
            HandlerThread thread = new HandlerThread("background");
            thread.start();
            mBackgroundHandler = new Handler(thread.getLooper());
        }
        return mBackgroundHandler;
    }

    private CameraView.Callback mCallback
            = new CameraView.Callback() {

        @Override
        public void onCameraOpened(CameraView cameraView) {
            Log.d(TAG, "onCameraOpened");

            Range<Integer> aerange = mCameraView.getAERange();
            final int min = aerange.getLower();

        }

        @Override
        public void onCameraClosed(CameraView cameraView) {
            Log.d(TAG, "onCameraClosed");
        }

        @Override
        public void onPictureTaken(CameraView cameraView, final byte[] data) {
            Log.d(TAG, "onPictureTaken " + data.length);
            Toast.makeText(MainActivity.this, R.string.picture_taken, Toast.LENGTH_SHORT)
                    .show();
            getBackgroundHandler().post(new Runnable() {
                @Override
                public void run() {
                    File file = new File(getFilePath(false));
                    OutputStream os = null;
                    try {
                        os = new FileOutputStream(file);
                        os.write(data);
                        os.close();
                    } catch (IOException e) {
                        Log.w(TAG, "Cannot write to " + file, e);
                    } finally {
                        if (os != null) {
                            try {
                                os.close();
                            } catch (IOException e) {
                                // Ignore
                            }
                        }
                    }
                }
            });
        }

        @Override
        public void onVideoRecordingStarted(CameraView cameraView) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    fabVideo.setColorFilter(Color.RED, PorterDuff.Mode.SRC_ATOP);
                }
            });
        }

        @Override
        public void onVideoRecordingStoped(final CameraView cameraView) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, cameraView.getVideoOutputFilePath(),
                            Toast.LENGTH_SHORT).show();
                    fabVideo.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                    Log.e("MainActivity", "run: path = " + cameraView.getVideoOutputFilePath());
                }
            });
        }

        @Override
        public void onVideoRecordingFailed(CameraView cameraView) {
            Toast.makeText(MainActivity.this, "onVideoRecordingFailed", Toast.LENGTH_SHORT).show();
        }
    };

    public static class ConfirmationDialogFragment extends DialogFragment {

        private static final String ARG_MESSAGE = "message";
        private static final String ARG_PERMISSIONS = "permissions";
        private static final String ARG_REQUEST_CODE = "request_code";
        private static final String ARG_NOT_GRANTED_MESSAGE = "not_granted_message";

        public static ConfirmationDialogFragment newInstance(@StringRes int message,
                String[] permissions, int requestCode, @StringRes int notGrantedMessage) {
            ConfirmationDialogFragment fragment = new ConfirmationDialogFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_MESSAGE, message);
            args.putStringArray(ARG_PERMISSIONS, permissions);
            args.putInt(ARG_REQUEST_CODE, requestCode);
            args.putInt(ARG_NOT_GRANTED_MESSAGE, notGrantedMessage);
            fragment.setArguments(args);
            return fragment;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Bundle args = getArguments();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(args.getInt(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String[] permissions = args.getStringArray(ARG_PERMISSIONS);
                                    if (permissions == null) {
                                        throw new IllegalArgumentException();
                                    }
                                    ActivityCompat.requestPermissions(getActivity(),
                                            permissions, args.getInt(ARG_REQUEST_CODE));
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Toast.makeText(getActivity(),
                                            args.getInt(ARG_NOT_GRANTED_MESSAGE),
                                            Toast.LENGTH_SHORT).show();
                                }
                            })
                    .create();
        }

    }

    private String getFilePath(boolean isVideo) {

        final File dcimFile = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM);
        final File camera2VideoImage = new File(dcimFile, "CameraView");
        if (!camera2VideoImage.exists()) {
            camera2VideoImage.mkdirs();
        }

        if (isVideo) {
            return camera2VideoImage.getAbsolutePath() + "/VIDEO_" + System.currentTimeMillis()
                    + ".mp4";
        } else {
            return camera2VideoImage.getAbsolutePath() + "/PICTURE_" + System.currentTimeMillis()
                    + ".jpg";
        }
    }


}
