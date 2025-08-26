/*
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.imnadev.pt30;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.pt.sdk.BleuManager;
import com.pt.sdk.SerialManagerCallbacks;

import no.nordicsemi.android.ble.BleManager;
import no.nordicsemi.android.ble.observer.ConnectionObserver;
import no.nordicsemi.android.ble.utils.ILogger;
import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.Logger;


@SuppressWarnings("unused")
public abstract class BleProfileService extends Service implements ConnectionObserver, SerialManagerCallbacks {
    @SuppressWarnings("unused")
    private static final String TAG = AppModel.TAG;

    public static final String BROADCAST_CONNECTION_STATE = "no.nordicsemi.android.nrftoolbox.BROADCAST_CONNECTION_STATE";
    public static final String BROADCAST_SERVICES_DISCOVERED = "no.nordicsemi.android.nrftoolbox.BROADCAST_SERVICES_DISCOVERED";
    public static final String BROADCAST_DEVICE_READY = "no.nordicsemi.android.nrftoolbox.DEVICE_READY";
    public static final String BROADCAST_BOND_STATE = "no.nordicsemi.android.nrftoolbox.BROADCAST_BOND_STATE";
    public static final String BROADCAST_FAILED_TO_CONNECT = "com.pt.BROADCAST_FAILED_TO_CONNECT";

    @Deprecated
    public static final String BROADCAST_BATTERY_LEVEL = "no.nordicsemi.android.nrftoolbox.BROADCAST_BATTERY_LEVEL";
    public static final String BROADCAST_ERROR = "no.nordicsemi.android.nrftoolbox.BROADCAST_ERROR";

    /**
     * The parameter passed when creating the service. Must contain the address of the sensor that we want to connect to
     */
    public static final String EXTRA_DEVICE_ADDRESS = "no.nordicsemi.android.nrftoolbox.EXTRA_DEVICE_ADDRESS";
    /**
     * The key for the device name that is returned in {@link #BROADCAST_CONNECTION_STATE} with state {@link #STATE_CONNECTED}.
     */
    public static final String EXTRA_DEVICE_NAME = "no.nordicsemi.android.nrftoolbox.EXTRA_DEVICE_NAME";
    public static final String EXTRA_DEVICE = "no.nordicsemi.android.nrftoolbox.EXTRA_DEVICE";
    public static final String EXTRA_LOG_URI = "no.nordicsemi.android.nrftoolbox.EXTRA_LOG_URI";
    public static final String EXTRA_CONNECTION_STATE = "no.nordicsemi.android.nrftoolbox.EXTRA_CONNECTION_STATE";
    public static final String EXTRA_BOND_STATE = "no.nordicsemi.android.nrftoolbox.EXTRA_BOND_STATE";
    public static final String EXTRA_SERVICE_PRIMARY = "no.nordicsemi.android.nrftoolbox.EXTRA_SERVICE_PRIMARY";
    public static final String EXTRA_SERVICE_SECONDARY = "no.nordicsemi.android.nrftoolbox.EXTRA_SERVICE_SECONDARY";
    public static final String EXTRA_REASON = "no.nordicsemi.android.nrftoolbox.EXTRA_REASON";

    public static final String EXTRA_DEVICE_PORTNUM = "com.pt.android.EXTRA_DEVICE_PORTNUM";
    //public static final String INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB";


    @Deprecated
    public static final String EXTRA_BATTERY_LEVEL = "no.nordicsemi.android.nrftoolbox.EXTRA_BATTERY_LEVEL";
    public static final String EXTRA_ERROR_MESSAGE = "no.nordicsemi.android.nrftoolbox.EXTRA_ERROR_MESSAGE";
    public static final String EXTRA_ERROR_CODE = "no.nordicsemi.android.nrftoolbox.EXTRA_ERROR_CODE";

    public static final int STATE_DEVICE_NOT_SUPPORTED = -2;
    public static final int STATE_LINK_LOSS = -1;
    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_DISCONNECTING = 3;

    private BleuManager mBleManager;
    private Handler mHandler;

    protected boolean mBound;
    private boolean mActivityIsChangingConfiguration;
    private BluetoothDevice mBluetoothDevice;
    private String mDeviceName;
    private ILogSession mLogSession;

    private final BroadcastReceiver mBluetoothStateBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
            final ILogger logger = getBinder();

            final String stateString = "[Broadcast] Action received: " + BluetoothAdapter.ACTION_STATE_CHANGED + ", state changed to " + state2String(state);
            logger.log(Log.DEBUG, stateString);
            Log.d(TAG, stateString);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    onBluetoothEnabled();
                    break;
                case BluetoothAdapter.STATE_TURNING_OFF:
                case BluetoothAdapter.STATE_OFF:
                    onBluetoothDisabled();
                    break;
            }
        }

        private String state2String(final int state) {
            switch (state) {
                case BluetoothAdapter.STATE_TURNING_ON:
                    return "TURNING ON";
                case BluetoothAdapter.STATE_ON:
                    return "ON";
                case BluetoothAdapter.STATE_TURNING_OFF:
                    return "TURNING OFF";
                case BluetoothAdapter.STATE_OFF:
                    return "OFF";
                default:
                    return "UNKNOWN (" + state + ")";
            }
        }
    };

    // The service's life cycle is intrinsically tied with the device connection. A connection is made, on the start of the service onStartCommand
    public class LocalBinder extends Binder implements ILogger {
        /**
         * Disconnects from the device.
         * For connect, see onStartCommand
         */
        public final void disconnect(Context context) {

            if (AppModel.MODE_USB) {
                mBleManager.usbDisconnect(context);
                onSerialDisconnected(mBleManager.getUsbDevice());
            } else {
                final int state = mBleManager.getConnectionState();
                // Force disconnect, if the device is already in disconnection phase
                if (state == BluetoothGatt.STATE_DISCONNECTED || state == BluetoothGatt.STATE_DISCONNECTING) {
                    mBleManager.close();
                    onDeviceDisconnected(mBluetoothDevice, ConnectionObserver.REASON_TERMINATE_LOCAL_HOST);
                    return;
                }

                mBleManager.disconnect().enqueue();
            }
        }

        /**
         * Sets whether the bound activity if changing configuration or not.
         * If <code>false</code>, we will turn off battery level notifications in onUnbind(..) method below.
         *
         * @param changing true if the bound activity is finishing
         */
        public void setActivityIsChangingConfiguration(final boolean changing) {
            mActivityIsChangingConfiguration = changing;
        }

        /**
         * Returns the device address
         *
         * @return device address
         */
        public String getDeviceAddress() {
            if (AppModel.MODE_USB) {
                // FIXME: What should be the address for a USB device ?
                return mBleManager.getUsbDevice().getDeviceName();
            } else {
                return (mBluetoothDevice != null) ? mBluetoothDevice.getAddress() : "n/a";
            }
        }

        /**
         * Returns the device name
         *
         * @return the device name
         */
        public String getDeviceName() {
            return (mDeviceName != null) ? mDeviceName : "n/a";
        }

        /**
         * Returns the Bluetooth device
         *
         * @return the Bluetooth device
         */
        public BluetoothDevice getBluetoothDevice() {
            return mBluetoothDevice;
        }

        /**
         * Returns <code>true</code> if the device is connected to the sensor.
         *
         * @return <code>true</code> if device is connected to the sensor, <code>false</code> otherwise
         */
        public boolean isConnected() {
            if (AppModel.MODE_USB) {
                return mBleManager.isSerialConnected();
            } else {
                return mBleManager != null && mBleManager.isConnected();

            }
        }


        /**
         * Returns the connection state of given device.
         *
         * @return the connection state, as in {@link BleManager#getConnectionState()}.
         */
        public int getConnectionState() {
            return (mBleManager != null) ? mBleManager.getConnectionState() : BleProfileService.STATE_DISCONNECTED;
        }

        /**
         * Returns the log session that can be used to append log entries.
         * The log session is created when the service is being created.
         * The method returns <code>null</code> if the nRF Logger app was not installed.
         *
         * @return the log session
         */
        public ILogSession getLogSession() {
            return mLogSession;
        }

        @Override
        public int getMinLogPriority() {
            return Log.INFO;
        }

        @Override
        public void log(final int level, @NonNull final String message) {
            Logger.log(mLogSession, level, message);
        }

        @Override
        public void log(final int level, final @StringRes int messageRes, final Object... params) {
            Logger.log(mLogSession, level, messageRes, params);
        }
    }

    /**
     * Returns a handler that is created in onCreate().
     * The handler may be used to postpone execution of some operations or to run them in UI thread.
     */
    protected Handler getHandler() {
        return mHandler;
    }

    /**
     * Returns the binder implementation. This must return class implementing the additional manager interface that may be used in the bound activity.
     *
     * @return the service binder
     */
    protected LocalBinder getBinder() {
        // default implementation returns the basic binder. You can overwrite the LocalBinder with your own, wider implementation
        return new LocalBinder();
    }

    @Override
    public IBinder onBind(final Intent intent) {
        Log.d(TAG, "BS: onBind: ...");
        mBound = true;
        return getBinder();
    }

    @Override
    public final void onRebind(final Intent intent) {
        mBound = true;
        Log.d(TAG, "BS: onRebind: ...");
        if (!mActivityIsChangingConfiguration)
            onRebind();
    }

    /**
     * Called when the activity has rebound to the service after being recreated.
     * This method is not called when the activity was killed to be recreated when the phone orientation changed
     * if prior to being killed called {@link LocalBinder#setActivityIsChangingConfiguration(boolean)} with parameter true.
     */
    protected void onRebind() {
        // empty default implementation
        Log.d(TAG, "BS: onRebind: ...");
    }

    @Override
    public boolean onUnbind(final Intent intent) {
        mBound = false;
        Log.d(TAG, "BS: onUnbind: ...");

        if (!mActivityIsChangingConfiguration)
            onUnbind();

        // We want the onRebind method be called if anything else binds to it again
        return true;
    }

    /**
     * Called when the activity has unbound from the service before being finished.
     * This method is not called when the activity is killed to be recreated when the phone orientation changed.
     */
    protected void onUnbind() {
        // empty default implementation
        Log.d(TAG, "BS: onUnbind: ...");
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler();

        // Initialize the manager
        mBleManager = initializeManager();
        // <aj-0920-2.2-mig>
        //mBleManager.setGattCallbacks(this);
        mBleManager.setConnectionObserver(this);

        // Register broadcast receivers
        registerReceiver(mBluetoothStateBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        // Service has now been created
        onServiceCreated();

        // Call onBluetoothEnabled if Bluetooth enabled
        final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter.isEnabled()) {
            onBluetoothEnabled();
        }
    }

    /**
     * Called when the service has been created, before the {@link #onBluetoothEnabled()} is called.
     */
    protected void onServiceCreated() {
        // empty default implementation
    }

    /**
     * Initializes the Ble Manager responsible for connecting to a single device.
     *
     * @return a new BleManager object
     */
    @SuppressWarnings("rawtypes")
    protected abstract BleuManager initializeManager();

    /**
     * This method returns whether autoConnect option should be used.
     *
     * @return true to use autoConnect feature, false (default) otherwise.
     */
    protected boolean shouldAutoConnect() {
        return true;
    }


    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (intent == null || !intent.hasExtra(EXTRA_DEVICE_ADDRESS)) {
            return START_REDELIVER_INTENT;
        }
        Log.d(TAG, "BS: onStartCommand: Start service ...");
        if (AppModel.MODE_USB) {
            final Integer deviceId = intent.getIntExtra(EXTRA_DEVICE_ADDRESS, 0);
            final Integer portnum = intent.getIntExtra(EXTRA_DEVICE_PORTNUM, 0);
            mDeviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);
            onServiceStarted();
            mBleManager.usbConnect(this, deviceId, portnum, this);

        } else {
            final Uri logUri = intent.getParcelableExtra(EXTRA_LOG_URI);
            mLogSession = Logger.openSession(getApplicationContext(), logUri);
            mDeviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);

            Logger.i(mLogSession, "Service started");

            final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            final String deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
            mBluetoothDevice = adapter.getRemoteDevice(deviceAddress);

            mBleManager.setLogger(mLogSession);
            onServiceStarted();
            mBleManager.connect(mBluetoothDevice)
                    .useAutoConnect(shouldAutoConnect())
                    .retry(3, 100)
                    .enqueue();
        }

        return START_REDELIVER_INTENT;
    }

    /**
     * Called when the service has been started. The device name and address are set.
     * The BLE Manager will try to connect to the device after this method finishes.
     */
    protected void onServiceStarted() {
        // empty default implementation
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // This method is called when user removed the app from Recents.
        // By default, the service will be killed and recreated immediately after that.
        // However, all managed devices will be lost and devices will be disconnected.
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "BS: service destroyed");
        // Unregister broadcast receivers
        unregisterReceiver(mBluetoothStateBroadcastReceiver);

        // shutdown the manager
        mBleManager.close();
        Logger.i(mLogSession, "Service destroyed");
        mBleManager = null;
        mBluetoothDevice = null;
        mDeviceName = null;
        mLogSession = null;
        mHandler = null;
    }


    /**
     * Method called when Bluetooth Adapter has been disabled.
     */
    protected void onBluetoothDisabled() {
        // empty default implementation
    }

    /**
     * This method is called when Bluetooth Adapter has been enabled and
     * after the service was created if Bluetooth Adapter was enabled at that moment.
     * This method could initialize all Bluetooth related features, for example open the GATT server.
     */
    protected void onBluetoothEnabled() {
        // empty default implementation
    }

    @Override
    public void onDeviceConnecting(@NonNull final BluetoothDevice device) {
        Log.v(TAG, "BS: onDeviceConnecting: " + device.getAddress());

        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_CONNECTING);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        onConnection("connecting");
    }

    public void onSerialConnectError(Exception e) {
        onSerialDisconnected(mBleManager.getUsbDevice());
    }


    public void
    onSerialConnected(@NonNull final UsbDevice device) {
        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_CONNECTED);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_DEVICE_NAME, mDeviceName);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

    public abstract void onConnection(String state);

    @Override
    public void onDeviceConnected(@NonNull final BluetoothDevice device) {
        Log.v(TAG, "BS: onDeviceConnected: " + device.getAddress());
        AppModel.getInstance().mTrackerLostLink = false;

        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_CONNECTED);
        broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
        broadcast.putExtra(EXTRA_DEVICE_NAME, mDeviceName);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        onConnection("connected");
    }

    @Override
    public void onDeviceDisconnecting(@NonNull final BluetoothDevice device) {

        Log.v(TAG, "BS: onDeviceDisconnecting: " + device.getAddress());

        // Notify user about changing the state to DISCONNECTING
        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_DISCONNECTING);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        onConnection("disconnecting");
    }

    /**
     * This method should return false if the service needs to do some asynchronous work after if has disconnected from the device.
     * In that case the {@link #stopService()} method must be called when done.
     *
     * @return true (default) to automatically stop the service when device is disconnected. False otherwise.
     */
    protected boolean stopWhenDisconnected() {
        return true;
    }


    public void onSerialDisconnected(@NonNull final UsbDevice device) {

        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_DISCONNECTED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        if (stopWhenDisconnected())
            stopService();
    }


    @Override
    public void onDeviceFailedToConnect(@NonNull final BluetoothDevice device, final int reason) {
        Log.e(TAG, "BS: onDeviceFailedToConnect: rc =" + reason);
        final Intent broadcast = new Intent(BROADCAST_FAILED_TO_CONNECT);
        broadcast.putExtra(EXTRA_DEVICE, device);
        broadcast.putExtra(EXTRA_REASON, reason);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
        onConnection("failed");
    }

    @Override
    public void onDeviceDisconnected(@NonNull final BluetoothDevice device, final int reason) {
        // Note 1: Do not use the device argument here unless you change calling onDeviceDisconnected from the binder above

        // Note 2: if BleManager#shouldAutoConnect() for this device returned true, this callback will be
        // invoked ONLY when user requested disconnection (using Disconnect button).
        Log.v(TAG, "BS: onDeviceDisconnected: for the reason =" + reason);
        AppModel.getInstance().mTrackerLostLink = false;

        final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
        broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);

        if (reason == ConnectionObserver.REASON_LINK_LOSS) {
            Log.i(TAG, "BS: Link Loss Occurred: " + device.getAddress());
            AppModel.getInstance().mTrackerLostLink = true;
            broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_LINK_LOSS);
        } else if (reason == ConnectionObserver.REASON_NOT_SUPPORTED) {
            Log.w(TAG, "BS: Device not supported : " + device.getAddress());
            broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_DEVICE_NOT_SUPPORTED);
        } else {
            broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_DISCONNECTED);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);

        AppModel.getInstance().invalidate();

        if (stopWhenDisconnected() && !AppModel.getInstance().mTrackerLostLink)
            stopService();

        onConnection("disconnected");
    }

    protected void stopService() {
        // user requested disconnection. We must stop the service
        Log.i(TAG, "BS: Stopping service, device disconnected.");
        Logger.v(mLogSession, "Stopping service...");
        stopSelf();
    }

//	@Override
//	public void onLinkLossOccurred(@NonNull final BluetoothDevice device) {
//		Log.v(TAG, "BS: onLinkLossOccurred: "+device.getAddress());
//		AppModel.getInstance().mTrackerLostLink = true;
//
//		final Intent broadcast = new Intent(BROADCAST_CONNECTION_STATE);
//		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
//		broadcast.putExtra(EXTRA_CONNECTION_STATE, STATE_LINK_LOSS);
//		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
//	}
//
//	@Override
//	public void onServicesDiscovered(@NonNull final BluetoothDevice device, final boolean optionalServicesFound) {
//		Log.v(TAG, "BS: onServicesDiscovered: "+device.getAddress());
//		final Intent broadcast = new Intent(BROADCAST_SERVICES_DISCOVERED);
//		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
//		broadcast.putExtra(EXTRA_SERVICE_PRIMARY, true);
//		broadcast.putExtra(EXTRA_SERVICE_SECONDARY, optionalServicesFound);
//		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
//	}

    @Override
    public void onDeviceReady(@NonNull final BluetoothDevice device) {
        Log.v(TAG, "BS: onDeviceReady: " + device.getAddress());

        final Intent broadcast = new Intent(BROADCAST_DEVICE_READY);
        broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
    }

//	@Override
//	public void onDeviceNotSupported(@NonNull final BluetoothDevice device) {
//		Log.w(TAG, "BS: onDeviceNotSupported: "+device.getAddress());
//
//		final Intent broadcast = new Intent(BROADCAST_SERVICES_DISCOVERED);
//		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
//		broadcast.putExtra(EXTRA_SERVICE_PRIMARY, false);
//		broadcast.putExtra(EXTRA_SERVICE_SECONDARY, false);
//		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
//
//		// no need for disconnecting, it will be disconnected by the manager automatically
//	}

//	@Override
//	public void onBatteryValueReceived(@NonNull final BluetoothDevice device, final int value) {
//		final Intent broadcast = new Intent(BROADCAST_BATTERY_LEVEL);
//		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
//		broadcast.putExtra(EXTRA_BATTERY_LEVEL, value);
//		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
//	}


//	@Override
//	public void onError(@NonNull final BluetoothDevice device, @NonNull final String message, final int errorCode) {
//		Log.w(TAG, "BS: onError: "+device.getAddress()+", "+message);
//
//		final Intent broadcast = new Intent(BROADCAST_ERROR);
//		broadcast.putExtra(EXTRA_DEVICE, mBluetoothDevice);
//		broadcast.putExtra(EXTRA_ERROR_MESSAGE, message);
//		broadcast.putExtra(EXTRA_ERROR_CODE, errorCode);
//		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
//	}

    /**
     * Shows a message as a Toast notification. This method is thread safe, you can call it from any thread
     *
     * @param messageResId an resource id of the message to be shown
     */
    protected void showToast(final int messageResId) {
        mHandler.post(() -> Toast.makeText(BleProfileService.this, messageResId, Toast.LENGTH_SHORT).show());
    }

    /**
     * Shows a message as a Toast notification. This method is thread safe, you can call it from any thread
     *
     * @param message a message to be shown
     */
    protected void showToast(final String message) {
        mHandler.post(() -> Toast.makeText(BleProfileService.this, message, Toast.LENGTH_SHORT).show());
    }

    /**
     * Returns the log session that can be used to append log entries. The method returns <code>null</code> if the nRF Logger app was not installed. It is safe to use logger when
     * {@link #onServiceStarted()} has been called.
     *
     * @return the log session
     */
    protected ILogSession getLogSession() {
        return mLogSession;
    }

    /**
     * Returns the device address
     *
     * @return device address
     */
    protected String getDeviceAddress() {
        return mBluetoothDevice.getAddress();
    }

    /**
     * Returns the Bluetooth device object
     *
     * @return bluetooth device
     */
    protected BluetoothDevice getBluetoothDevice() {
        return mBluetoothDevice;
    }

    /**
     * Returns the device name
     *
     * @return the device name
     */
    protected String getDeviceName() {
        return mDeviceName;
    }

    /**
     * Returns <code>true</code> if the device is connected to the sensor.
     *
     * @return <code>true</code> if device is connected to the sensor, <code>false</code> otherwise
     */
    protected boolean isConnected() {
        return mBleManager != null && mBleManager.isConnected();
    }
}
