package nl.miraclethings.instantvideorecorder.demo;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import nl.miraclethings.instantvideo.recorder.InstantVideoRecorder;
import nl.miraclethings.instantvideo.recorder.Util;


public class FFmpegRecorderActivity extends Activity implements OnClickListener {

    private final static String CLASS_LABEL = "RecordActivity";
    private final static String LOG_TAG = CLASS_LABEL;

    private PowerManager.WakeLock mWakeLock;

    long startTime = 0;

    boolean isFlashOn = false;
    TextView txtTimer, txtRecordingSize;
    ImageView recorderIcon = null;
    ImageView flashIcon = null, switchCameraIcon = null, resolutionIcon = null;

    private boolean isPreviewOn = false;

    private Camera mCamera;

    private int previewWidth = 320, screenWidth = 320;
    private int previewHeight = 240, screenHeight = 240;

    /* video data getting thread */
    private Camera cameraDevice;
    private CameraView cameraView;
    Parameters cameraParameters = null;

    int defaultCameraId = -1, defaultScreenResolution = -1, cameraSelection = 0;
    /* layout setting */

    private Button btnRecorderControl;

    private Handler mHandler = new Handler();

    private Dialog dialog = null;
    RelativeLayout topLayout = null;
    RelativeLayout previewLayout = null;

    long totalTime = 0;
    private Dialog creatingProgress;


    BroadcastReceiver mReceiver = null;

    private InstantVideoRecorder mRecorder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.ffmpeg_recorder);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
        mWakeLock.acquire();

        DisplayMetrics displaymetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
        //Find screen dimensions
        screenWidth = displaymetrics.widthPixels;
        screenHeight = displaymetrics.heightPixels;

        mRecorder = new InstantVideoRecorder(this, "Recordings");

        initLayout();
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, CLASS_LABEL);
            mWakeLock.acquire();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mReceiver != null)
            unregisterReceiver(mReceiver);

        if (cameraView != null) {
            cameraView.stopPreview();
            if (cameraDevice != null)
                cameraDevice.release();
            cameraDevice = null;
        }
        if (mWakeLock != null) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    private void initLayout() {
        previewLayout = (RelativeLayout) (((LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.ffmpeg_recorder, null));
        btnRecorderControl = (Button) previewLayout.findViewById(R.id.stop_control);
        txtTimer = (TextView) previewLayout.findViewById(R.id.txtTimer);
        txtRecordingSize = (TextView) previewLayout.findViewById(R.id.txtRecordingSize);
        recorderIcon = (ImageView) previewLayout.findViewById(R.id.recorderIcon);
        resolutionIcon = (ImageView) previewLayout.findViewById(R.id.resolutionIcon);
        flashIcon = (ImageView) previewLayout.findViewById(R.id.flashIcon);
        switchCameraIcon = (ImageView) previewLayout.findViewById(R.id.switchCameraIcon);

        btnRecorderControl.setOnClickListener(this);

        previewLayout.findViewById(R.id.start_control).setOnClickListener(this);

        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            flashIcon.setOnClickListener(this);
            flashIcon.setVisibility(View.VISIBLE);
        }
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
            switchCameraIcon.setOnClickListener(this);
            switchCameraIcon.setVisibility(View.VISIBLE);
        }
        initCameraLayout();
    }

    private void initCameraLayout() {

        if (topLayout != null && topLayout.getChildCount() > 0)
            topLayout.removeAllViews();
        topLayout = new RelativeLayout(this);
        RelativeLayout.LayoutParams layoutParam = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

        setCamera();
        handleSurfaceChanged();

        RelativeLayout.LayoutParams layoutParam1 = new RelativeLayout.LayoutParams(screenWidth, screenHeight);
        layoutParam1.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        int margin = Util.calculateMargin(previewWidth, screenWidth);
        layoutParam1.setMargins(margin, 0, margin, 0);

        // add the camera preview
        topLayout.addView(cameraView, layoutParam1);
        // add the overlay for buttons and textviews
        topLayout.addView(previewLayout, layoutParam);
        topLayout.setLayoutParams(layoutParam);
        setContentView(topLayout);
    }

    private void setCamera() {
        try {
            // Find the total number of cameras available
            int numberOfCameras = Camera.getNumberOfCameras();
            // Find the ID of the default camera
            CameraInfo cameraInfo = new CameraInfo();
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.getCameraInfo(i, cameraInfo);
                if (cameraInfo.facing == cameraSelection) {
                    defaultCameraId = i;
                }
            }

            if (mCamera != null)
                mCamera.release();
            if (defaultCameraId >= 0)
                cameraDevice = Camera.open(defaultCameraId);
            else
                cameraDevice = Camera.open();

            cameraView = new CameraView(this, cameraDevice, cameraSelection == CameraInfo.CAMERA_FACING_FRONT ? 270 : 90);

        } catch (Exception e) {
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            videoTheEnd(false);
            return false;
        }

        return super.onKeyDown(keyCode, event);
    }

    //---------------------------------------------
    // camera thread, gets and encodes video data
    //---------------------------------------------
    class CameraView extends SurfaceView implements SurfaceHolder.Callback {

        private SurfaceHolder mHolder;


        public CameraView(Context context, Camera camera, int orientation) {
            super(context);
            mCamera = camera;
            cameraParameters = mCamera.getParameters();

            mHolder = getHolder();
            mHolder.addCallback(CameraView.this);
//            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

            mRecorder.setCamera(mCamera, orientation);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                stopPreview();
                mCamera.setPreviewDisplay(holder);
            } catch (IOException exception) {
                mCamera.release();
                mCamera = null;
            }
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (isPreviewOn)
                mCamera.stopPreview();
            handleSurfaceChanged();
            startPreview();
            mCamera.autoFocus(null);
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            try {
                mHolder.addCallback(null);
                mCamera.setPreviewCallback(null);
            } catch (RuntimeException e) {
                // The camera has probably just been released, ignore.
            }
        }

        public void startPreview() {
            if (!isPreviewOn && mCamera != null) {
                isPreviewOn = true;
                mCamera.startPreview();
            }
        }

        public void stopPreview() {
            if (isPreviewOn && mCamera != null) {
                isPreviewOn = false;
                mCamera.stopPreview();
            }
        }

    }


    private void handleSurfaceChanged() {
        List<Camera.Size> resolutionList = Util.getResolutionList(mCamera);
        resolutionIcon.setVisibility(View.GONE);
        if (resolutionList != null && resolutionList.size() > 0) {
            Collections.sort(resolutionList, new Util.ResolutionComparator());
            Camera.Size previewSize = null;
            if (defaultScreenResolution == -1) {
                int mediumResolution = resolutionList.size() / 2;
                if (mediumResolution >= resolutionList.size())
                    mediumResolution = resolutionList.size() - 1;
                previewSize = resolutionList.get(mediumResolution);
            } else {
                if (defaultScreenResolution >= resolutionList.size())
                    defaultScreenResolution = resolutionList.size() - 1;
                previewSize = resolutionList.get(defaultScreenResolution);
            }
            if (previewSize != null) {
                previewWidth = previewSize.width;
                previewHeight = previewSize.height;
                cameraParameters.setPreviewSize(previewWidth, previewHeight);

                mRecorder.setSize(previewWidth, previewHeight);

            }
        }
//		cameraParameters.setPreviewFpsRange(1000, frameRate*1000);


        mCamera.setDisplayOrientation(Util.determineDisplayOrientation(FFmpegRecorderActivity.this, defaultCameraId));
        List<String> focusModes = cameraParameters.getSupportedFocusModes();
        if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
            cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }

        mCamera.setDisplayOrientation(0);
        mCamera.setParameters(cameraParameters);
    }

    @Override
    public void onClick(View v) {

        if (v.getId() == R.id.start_control) {
            startRecording();
            findViewById(R.id.stop_control).setVisibility(View.VISIBLE);
            v.setVisibility(View.GONE);
        }
        else if (v.getId() == R.id.stop_control) {
            saveRecording();
            findViewById(R.id.stop_control).setVisibility(View.GONE);
            v.setVisibility(View.VISIBLE);
        } else if (v.getId() == R.id.flashIcon) {
            if (isFlashOn) {
                flashIcon.setImageDrawable(getResources().getDrawable(R.drawable.cameraflashoff));
                isFlashOn = false;
                cameraParameters.setFlashMode(Parameters.FLASH_MODE_OFF);
            } else {
                flashIcon.setImageDrawable(getResources().getDrawable(R.drawable.cameraflash));
                isFlashOn = true;
                cameraParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
            }
            mCamera.setParameters(cameraParameters);
        } else if (v.getId() == R.id.switchCameraIcon) {
            cameraSelection = ((cameraSelection == CameraInfo.CAMERA_FACING_BACK) ? CameraInfo.CAMERA_FACING_FRONT : CameraInfo.CAMERA_FACING_BACK);
            initCameraLayout();

            if (cameraSelection == CameraInfo.CAMERA_FACING_FRONT)
                flashIcon.setVisibility(View.GONE);
            else {
                flashIcon.setVisibility(View.VISIBLE);
                if (isFlashOn) {
                    cameraParameters.setFlashMode(Parameters.FLASH_MODE_TORCH);
                    mCamera.setParameters(cameraParameters);
                }
            }
        } else if (v.getId() == R.id.btnContinue) {
            dialog.dismiss();
        } else if (v.getId() == R.id.btnDiscard) {
            dialog.dismiss();
            videoTheEnd(false);
        }
    }

    public void videoTheEnd(boolean isSuccess) {
        mRecorder.releaseResources();
//        releaseResources();
//
        returnToCaller(isSuccess);
    }

    private Runnable mUpdateTimeTask = new Runnable() {
        public void run() {
            setTotalVideoTime();
            mHandler.postDelayed(this, 500);
        }
    };

    private void returnToCaller(boolean valid) {
        setActivityResult(valid);
        finish();
    }

    private void setActivityResult(boolean valid) {
        Intent resultIntent = new Intent();
        int resultCode;
        if (valid) {
            resultCode = RESULT_OK;
            resultIntent.setData(mRecorder.getFilePath());
        } else
            resultCode = RESULT_CANCELED;

        setResult(resultCode, resultIntent);
    }


    private void saveRecording() {

        creatingProgress = new Dialog(FFmpegRecorderActivity.this);
        creatingProgress.setCanceledOnTouchOutside(false);
        creatingProgress.setTitle(getResources().getString(R.string.finalizing));
        creatingProgress.show();
        recorderIcon.setVisibility(View.GONE);
        txtTimer.setVisibility(View.INVISIBLE);
        btnRecorderControl.setText(getResources().getString(R.string.wait));
        btnRecorderControl.setClickable(false);
        btnRecorderControl.setBackgroundResource(R.drawable.btn_shutter_normal);
        resolutionIcon.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mUpdateTimeTask);

        mRecorder.stopRecording(new Runnable() {
            @Override
            public void run() {
                creatingProgress.dismiss();
                returnToCaller(true);
            }
        });
    }


    private synchronized void setTotalVideoTime() {
        totalTime = System.currentTimeMillis() - startTime;
        if (totalTime > 0)
            txtTimer.setText(Util.getRecordingTimeFromMillis(totalTime));
    }

    private void startRecording() {
        mRecorder.startRecording();
        startTime = System.currentTimeMillis();

        txtTimer.setVisibility(View.VISIBLE);
        // Handler to show recoding duration after recording starts
        mHandler.removeCallbacks(mUpdateTimeTask);
        mHandler.postDelayed(mUpdateTimeTask, 100);
        btnRecorderControl.setVisibility(View.VISIBLE);
        btnRecorderControl.setText(getResources().getString(R.string.stop));
    }
}