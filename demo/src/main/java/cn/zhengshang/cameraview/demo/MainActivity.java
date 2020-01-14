

package cn.zhengshang.cameraview.demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Rational;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;

import cn.zhengshang.base.AspectRatio;
import cn.zhengshang.cameraview.CameraView;
import cn.zhengshang.listener.Callback;
import cn.zhengshang.listener.CameraError;
import cn.zhengshang.listener.OnVideoOutputFileListener;
import cn.zhengshang.util.CameraUtil;


/**
 * This demo app saves the taken picture to a constant file.
 * $ adb pull /sdcard/Android/data/com.google.android.cameraview.demo/files/Pictures/picture.jpg
 */
public class MainActivity extends AppCompatActivity implements
        ActivityCompat.OnRequestPermissionsResultCallback,
        cn.zhengshang.cameraview.demo.AspectRatioFragment.Listener {

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

    private boolean isHdrMode;
    private int manualMode;
    private long minValue, maxValue;
    private float mMaxZoom;

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
    private Callback mCallback
            = new Callback() {
        @Override
        public void onFailed(CameraError error) {
            super.onFailed(error);
            Log.e("MainActivity", "onFailed: [error] = " + error);
        }

        @Override
        public void onRequestBuilderCreate(CameraView cameraView) {

        }

        @Override
        public void onCameraOpened(CameraView cameraView) {
            Log.d(TAG, "onCameraOpened");

            Range<Integer> aerange = mCameraView.getAERange();
//            final int min = aerange.getLower();

        }

        @Override
        public void onCameraClosed(CameraView cameraView) {
            Log.d(TAG, "onCameraClosed");
        }

        @Override
        public void onPictureTaken(CameraView cameraView, final byte[] data) {
            Log.d(TAG, "onPictureTaken " + data.length);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, R.string.picture_taken, Toast.LENGTH_SHORT).show();
                }
            });
            getBackgroundHandler().post(new Runnable() {
                @Override
                public void run() {
                    File file = new File(getFilePath(false));
                    try (OutputStream os = new FileOutputStream(file)) {
                        os.write(data);
                        CameraUtil.addToMediaStore(MainActivity.this, file.getPath());
                    } catch (IOException e) {
                        Log.w(TAG, "Cannot write to " + file, e);
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
        public void onVideoRecordingStopped(final CameraView cameraView) {
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

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCameraView = findViewById(R.id.camera);
        if (mCameraView != null) {
            mCameraView.addCallback(mCallback);
        }
        FloatingActionButton fab = findViewById(R.id.take_picture);
        if (fab != null) {
            fab.setOnClickListener(mOnClickListener);
        }

        fabVideo = findViewById(R.id.record_video);
        fabVideo.setOnClickListener(mOnClickListener);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
        }

        Switch hdrSwitch = findViewById(R.id.switch_hdr);
        hdrSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mCameraView != null) {
                    isHdrMode = isChecked;
                    mCameraView.setHdrMode(isHdrMode);
                }
            }
        });

        manualValue = findViewById(R.id.manual_text_value);
        manualTitle = findViewById(R.id.manual_text);
        SeekBar aeSeekbar = findViewById(R.id.ae_seekbar);
        aeSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

                if (manualMode == 6) {
                    float zoom = progress * (mMaxZoom - 1) / 100f + 1;
                    mCameraView.setWTlen(zoom);
                    manualValue.setText(String.valueOf(zoom));
                    return;
                }

                double realValue = (progress * (maxValue - minValue) / 100 + minValue);
                Log.v("MainActivity", "onProgressChanged: realValue = " + realValue);
                if (manualMode != 0) {
                    mCameraView.setManualMode(true);
                } else {
                    mCameraView.setManualMode(false);
                }
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

        mCameraView.addOnVideoOutputFileListener(new OnVideoOutputFileListener() {
            @Override
            public String getVideoOutputFilePath() {
                return getFilePath(true);
            }
        });
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
    protected void onPause() {
        mCameraView.stop();
        super.onPause();
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
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.CAMERA,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION:
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
                    cn.zhengshang.cameraview.demo.AspectRatioFragment.newInstance(ratios, currentRatio)
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
                    item.setTitle(item.getTitle() + "(不支持)");
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
                    item.setTitle(item.getTitle() + "(不支持)");
                }
                break;
            case R.id.manual_iso:
                if (mCameraView.isManualISOSupported()) {
                    Object range = mCameraView.getISORange();
                    if (range instanceof Range) {
                        Range<Integer> isoRange = (Range<Integer>) range;
                        maxValue = isoRange.getUpper();
                        minValue = isoRange.getLower();
                    } else if (range instanceof List) {
                        maxValue = (long) ((List) range).get(((List) range).size() - 1);
                        minValue = (long) ((List) range).get(0);
                    }
                    manualTitle.setText(item.getTitle());
                    manualMode = 3;
                } else {
                    item.setEnabled(false);
                    item.setTitle(item.getTitle() + "(不支持)");
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
                    item.setTitle(item.getTitle() + "(不支持)");
                }
                break;
            case R.id.manual_af:
                if (mCameraView.isManualAFSupported()) {
                    minValue = 0;
                    maxValue = (long) mCameraView.getAFMaxValue();
                    manualTitle.setText(item.getTitle());
                    manualTitle.setText(item.getTitle());
                    manualMode = 5;
                } else {
                    item.setEnabled(false);
                    item.setTitle(item.getTitle() + "(不支持)");
                }
                break;
            case R.id.manual_wt:
                mMaxZoom = mCameraView.getMaxZoom();
                minValue = 0;
                maxValue = 100;
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

    private void updateChangesToCamera(double progress) {
        switch (manualMode) {
            case 1: //ae
                mCameraView.setAEValue((int) progress);
                break;
            case 2://sec
                mCameraView.setSecValue((long) progress);
                int denominator = (int) Math.round(1000000000f / progress);
                Rational rational = new Rational(1, denominator);
                manualValue.setText(rational.toString());
                return;
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


}
