package acorl.drone.tests.flightcontrollertest;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import dji.sdk.camera.DJICamera;
import dji.sdk.products.DJIAircraft;
import dji.sdk.products.DJIHandHeld;
import dji.sdk.sdkmanager.DJISDKManager;
import dji.sdk.base.DJIBaseComponent;
import dji.sdk.base.DJIBaseComponent.DJIComponentListener;
import dji.sdk.base.DJIBaseProduct;
import dji.sdk.base.DJIBaseProduct.DJIBaseProductListener;
import dji.sdk.base.DJIBaseProduct.DJIComponentKey;
import dji.common.error.DJIError;
import dji.common.error.DJISDKError;
/**
 * Created by cameron on 7/11/16.
 */
public class SDKCoordinator extends Application{

    public static final String FLAG_CONNECTION_CHANGE = "flight_test_connection_change";

    private static DJIBaseProduct mProduct;

    private Handler mHandler;

    /**
     * This function is used to get the instance of DJIBaseProduct.
     * If no product is connected, it returns null.
     */
    public static synchronized DJIBaseProduct getProductInstance() {
        if (null == mProduct) {
            mProduct = DJISDKManager.getInstance().getDJIProduct();
        }
        return mProduct;
    }

    public static synchronized  DJIAircraft getAircraftInstance() {
        DJIAircraft aircraft = null;

        DJIBaseProduct product = SDKCoordinator.getProductInstance();
        if (product != null && product.isConnected()) {
            if (product instanceof DJIAircraft) {
                aircraft = (DJIAircraft) product;
            }
        }

        return aircraft;
    }

    /*
     * Checks to see if the aircraft is successfully connected by checking if the base product
     * is not null and if the product is an instance of an aircraft
     */
    public static boolean isAircraftConnected() {
        return getProductInstance() != null && getProductInstance() instanceof DJIAircraft;
    }

    /*
     * Checks to see if the handheld is connected properyl by checking if the base product
     * exists and if it is an instance of a handheld.
     */
    public static boolean isHandHeldConnected() {
        return getProductInstance() != null && getProductInstance() instanceof DJIHandHeld;
    }

    /*
     * Returns the camera object of the aircraft if their is a proper connection
     */
    public static synchronized DJICamera getCameraInstance() {

        if (getProductInstance() == null) return null;
        return getProductInstance().getCamera();

    }

    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler(Looper.getMainLooper());

        DJISDKManager.getInstance().initSDKManager(this, mDJISDKManagerCallback);
    }

    /**
     * When starting SDK services, an instance of interface DJISDKManager.DJISDKManagerCallback will be used to listen to
     * the SDK Registration result and the product changing.
     */
    private DJISDKManager.DJISDKManagerCallback mDJISDKManagerCallback = new DJISDKManager.DJISDKManagerCallback() {

        /*
         * Listens to the SDK registration result
         */
        @Override
        public void onGetRegisteredResult(DJIError error) {
            //Checks if registering the product was a success or not
            if(error == DJISDKError.REGISTRATION_SUCCESS) {

                //Starts connection to product only once the sdk has been registered
                DJISDKManager.getInstance().startConnectionToProduct();

                //Logs that there was a success
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Success", Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                //Logs that there was failure to register
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {

                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "register sdk fails, check network is available", Toast.LENGTH_LONG).show();
                    }
                });

            }
            Log.e("TAG", error.toString());
        }

        //Listens to the connected product changing, including two parts, component changing or product connection changing.
        @Override
        public void onProductChanged(DJIBaseProduct oldProduct, DJIBaseProduct newProduct) {

            mProduct = newProduct;
            if(mProduct != null) {
                mProduct.setDJIBaseProductListener(mDJIBaseProductListener);
            }

            notifyStatusChange();
        }
    };

    /*
     * Receives notifications of component and product connectivity changes
     */
    private DJIBaseProductListener mDJIBaseProductListener = new DJIBaseProductListener() {

        /*
         * Connects the new component to the DJI SDK when changed (or on startup)
         */
        @Override
        public void onComponentChange(DJIComponentKey key, DJIBaseComponent oldComponent, DJIBaseComponent newComponent) {

            if(newComponent != null) {
                newComponent.setDJIComponentListener(mDJIComponentListener);
            }
            notifyStatusChange();
        }

        /*
         * Notifies when the status of the connectivity has changed
         */
        @Override
        public void onProductConnectivityChanged(boolean isConnected) {

            notifyStatusChange();
        }

    };

    /*
     * Receives notification of component connectivity changed
     */
    private DJIComponentListener mDJIComponentListener = new DJIComponentListener() {

        @Override
        public void onComponentConnectivityChanged(boolean isConnected) {
            notifyStatusChange();
        }

    };

    private void notifyStatusChange() {
        mHandler.removeCallbacks(updateRunnable);
        mHandler.postDelayed(updateRunnable, 500);
    }

    /*
     * This somehow interacts with MainActivity's BroadcastReceiver to to update the titlebar
     * when the connection has changed (i.e. it found/lost an aircraft).
     */
    private Runnable updateRunnable = new Runnable() {

        @Override
        public void run() {
            Intent intent = new Intent(FLAG_CONNECTION_CHANGE);
            sendBroadcast(intent);
        }
    };
}
