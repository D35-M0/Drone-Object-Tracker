package acorl.drone.tests.flightcontrollertest;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.view.TextureView.SurfaceTextureListener;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;

import dji.common.flightcontroller.DJIVirtualStickFlightControlData;
import dji.common.util.DJICommonCallbacks;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.camera.DJICamera;
import dji.sdk.camera.DJICamera.CameraReceivedVideoDataCallback;
import dji.sdk.codec.DJICodecManager;
import dji.sdk.products.DJIAircraft;
import dji.sdk.flightcontroller.DJIFlightController;
import dji.common.product.Model;
import dji.common.error.DJIError;

public class MainActivity extends Activity implements SurfaceTextureListener {

    public static final String GO_LEFT = "Left";
    public static final String GO_RIGHT = "Right";
    public static final String GO_FORWARD = "Forward";
    public static final String GO_BACKWARD = "Backward";
    public static final String HOLD_STEADY = "Hold";

    private static final String TAG = MainActivity.class.getName();

    protected DJICamera.CameraReceivedVideoDataCallback mReceivedVideoDataCallBack = null;

    // Codec for video live view
    protected DJICodecManager mCodecManager = null;
    protected TextView mConnectStatusTextView;

    protected TextureView mVideoSurface = null;
    //private ToggleButton mGyroBtn, mStreamBtn;
    private TextView gyroInfo;
    private DJIFlightController mFlightController;
    private Timer mSendVirtualStickDataTimer;
    private SendVirtualStickDataTask mSendVirtualStickDataTask;
    private float mPitch, mRoll, mYaw, mThrottle;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);



        // When the compile and target version is higher than 22, please request the
        // following permissions at runtime to ensure the
        // SDK work well.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.VIBRATE,
                            Manifest.permission.INTERNET, Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.WAKE_LOCK, Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.CHANGE_WIFI_STATE, Manifest.permission.MOUNT_UNMOUNT_FILESYSTEMS,
                            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.SYSTEM_ALERT_WINDOW,
                            Manifest.permission.READ_PHONE_STATE,
                    }
                    , 1);
        }

        setContentView(R.layout.activity_main);

        initUI();



        /* TEMP POSITIONS */
        // Starts the tracking scheduler
        mSendVirtualStickDataTimer = new Timer();
        mSendVirtualStickDataTask = new SendVirtualStickDataTask();
        mSendVirtualStickDataTimer.schedule(mSendVirtualStickDataTask, 0, 200);
        /* TEMP POSITIONS */



        // The callback for receiving the raw H264 video data for camera live view
        mReceivedVideoDataCallBack = new CameraReceivedVideoDataCallback() {

            @Override
            public void onResult(byte[] videoBuffer, int size) {
                if (mCodecManager != null) {
                    // Send the raw H264 video data to codec manager for decoding
                    mCodecManager.sendDataToDecoder(videoBuffer, size);
                } else {
                    Log.e(TAG, "mCodecManager is null");
                }
            }
        };


        // Register the broadcast receiver for receiving the device connection's changes.
        IntentFilter filter = new IntentFilter();
        filter.addAction(SDKCoordinator.FLAG_CONNECTION_CHANGE);
        registerReceiver(mReceiver, filter);

    }

    private void initFlightController() {
        DJIAircraft aircraft = SDKCoordinator.getAircraftInstance();
        if (aircraft == null || !aircraft.isConnected()) {
            showToast("Disconnected");
            mFlightController = null;
            return;
        } else {
            mFlightController = aircraft.getFlightController();
        }
    }

    protected BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            updateTitleBar();
            onProductChange();
        }

    };

    private void updateTitleBar() {
        if (mConnectStatusTextView == null) return;
        boolean ret = false;

        /*
         * Gets the product. If it is connected, update the title bar with the model name of
         * the drone. If the product is not connected to the aircraft, but is connected to something,
         * and that something is a controller, then update the title bar that only the controller
         * is connected.
         */
        DJIBaseProduct product = SDKCoordinator.getProductInstance();
        if (product != null) {
            if (product.isConnected()) {
                //The product is connected
                mConnectStatusTextView.setText(SDKCoordinator.getProductInstance().getModel() + " Connected");
                ret = true;
            } else {
                if (product instanceof DJIAircraft) {
                    DJIAircraft aircraft = (DJIAircraft) product;
                    if (aircraft.getRemoteController() != null && aircraft.getRemoteController().isConnected()) {
                        // The product is not connected, but the remote controller is connected
                        mConnectStatusTextView.setText("only RC Connected");
                        ret = true;
                    }
                }
            }
        }

        /*
         * if product is null, or not connected to at least the controller, display
         * 'Disconnected' to the titlebar
         */
        if (!ret) {
            // The product or the remote controller are not connected.
            mConnectStatusTextView.setText("Disconnected");
        }
    }

    protected void onProductChange() {
        initPreviewer();
    }

    @Override
    public void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();
        initPreviewer();
        updateTitleBar();
        initFlightController();
        if (mVideoSurface == null) {
            Log.e(TAG, "mVideoSurface is null");
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG, "onPause");
        uninitPreviewer();
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG, "onStop");
        super.onStop();
    }

    public void onReturn(View view) {
        Log.e(TAG, "onReturn");
        this.finish();
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "onDestroy");
        uninitPreviewer();
        unregisterReceiver(mReceiver);

        super.onDestroy();
    }

    private void initUI() {
        mConnectStatusTextView = (TextView) findViewById(R.id.ConnectStatusTextView);
        // init mVideoSurface
        mVideoSurface = (TextureView) findViewById(R.id.video_previewer_surface);


        gyroInfo = (TextView) findViewById(R.id.gyro_info);
        /*mGyroBtn = (ToggleButton) findViewById(R.id.btn_gyro);
        mStreamBtn = (ToggleButton) findViewById(R.id.btn_stream);*/

        if (null != mVideoSurface) {
            mVideoSurface.setSurfaceTextureListener(this);
        }

       /* mGyroBtn.setOnClickListener(this);
        mStreamBtn.setOnClickListener(this);*/

        gyroInfo.setVisibility(View.INVISIBLE);


       /* mGyroBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {

                    startGyrations();
                }
                else
                    stopGyrations();
            }
        });

        mStreamBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startStream();
                } else {
                    stopStream();
                }
            }
        });*/
    }

    private void initPreviewer() {

        DJIBaseProduct product = SDKCoordinator.getProductInstance();

        if (product == null || !product.isConnected()) {
            showToast(getString(R.string.disconnected));
        } else {
            if (null != mVideoSurface) {
                mVideoSurface.setSurfaceTextureListener(this);
            }
            if (!product.getModel().equals(Model.UnknownAircraft)) {
                DJICamera camera = product.getCamera();
                if (camera != null) {
                    // Set the callback
                    camera.setDJICameraReceivedVideoDataCallback(mReceivedVideoDataCallBack);
                }
            }
        }
    }

    private void uninitPreviewer() {
        DJICamera camera = SDKCoordinator.getCameraInstance();
        if (camera != null) {
            // Reset the callback
            SDKCoordinator.getCameraInstance().setDJICameraReceivedVideoDataCallback(null);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureAvailable");
        if (mCodecManager == null) {
            mCodecManager = new DJICodecManager(this, surface, width, height);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "onSurfaceTextureSizeChanged");
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.e(TAG, "onSurfaceTextureDestroyed");
        if (mCodecManager != null) {
            mCodecManager.cleanSurface();
            mCodecManager = null;
        }

        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public void showToast(final String msg) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Hopefully will pop up display text
    public void debugText(String text) {
        new AlertDialog.Builder(this)
                .setTitle("Debugging")
                .setMessage(text)
                .setPositiveButton(android.R.string.yes, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();

    }

    class SendVirtualStickDataTask extends TimerTask {
        @Override
        public void run() {

            if (mFlightController != null) {
                /*
                String control = ObjectTracker.trackLoc(mVideoSurface.getBitmap());

                switch(control) {
                    case GO_LEFT:
                        mPitch = 0;
                        mRoll = -1.5;
                        mYaw = 0;
                        mThrottle = mFlightController.getCurrentState().getUltrasonicHeight();
                        break;
                    case GO_RIGHT:
                        mPitch = 0;
                        mRoll = 1.5;
                        mYaw = 0;
                        mThrottle = mFlightController.getCurrentState().getUltrasonicHeight();
                        break;
                    case GO_FORWARD:
                        mPitch = 1.5;
                        mRoll = 0;
                        mYaw = 0;
                        mThrottle = mFlightController.getCurrentState().getUltrasonicHeight();
                        break;
                    case GO_BACKWARD:
                        mPitch = -1.5;
                        mRoll = 0;
                        mYaw = 0;
                        mThrottle = mFlightController.getCurrentState().getUltrasonicHeight();
                        break;
                    case HOLD_STEADY:
                        mPitch = 0;
                        mRoll = 0;
                        mYaw = 0;
                        mThrottle = mFlightController.getCurrentState().getUltrasonicHeight();
                        break;
                }
                */

                mFlightController.sendVirtualStickFlightControlData(
                        new DJIVirtualStickFlightControlData(
                                mPitch, mRoll, mYaw, mThrottle
                        ), new DJICommonCallbacks.DJICompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                            }
                        }
                );
            }
        }
    }
}



