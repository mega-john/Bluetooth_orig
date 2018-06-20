package com.android.bluetooth.btservice;

import android.os.Message;
import android.util.Log;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

final class AdapterState extends StateMachine {
    static final int ALL_DEVICES_DISCONNECTED = 22;
    static final int BEGIN_DISABLE = 21;
    private static final boolean DBG = true;
    static final int DISABLED = 24;
    static final int DISABLE_TIMEOUT = 103;
    private static final int DISABLE_TIMEOUT_DELAY = 20000;
    static final int ENABLED_READY = 3;
    static final int ENABLE_TIMEOUT = 101;
    private static final int ENABLE_TIMEOUT_DELAY = 20000;
    private static final int PROPERTY_OP_DELAY = 2000;
    static final int SET_SCAN_MODE_TIMEOUT = 105;
    static final int STARTED = 2;
    static final int START_TIMEOUT = 100;
    private static final int START_TIMEOUT_DELAY = 5000;
    static final int STOPPED = 25;
    static final int STOP_TIMEOUT = 104;
    private static final int STOP_TIMEOUT_DELAY = 5000;
    private static final String TAG = "BluetoothAdapterState";
    static final int USER_TURN_OFF = 20;
    static final int USER_TURN_OFF_DELAY_MS = 500;
    static final int USER_TURN_ON = 1;
    private static final boolean VDBG = false;
    private AdapterProperties mAdapterProperties;
    private AdapterService mAdapterService;
    private OffState mOffState = new OffState();
    private OnState mOnState = new OnState();
    private PendingCommandState mPendingCommandState = new PendingCommandState();

    private class OffState extends State {
        private OffState() {
        }

        public void enter() {
            AdapterState.this.infoLog("Entering OffState");
        }

        public boolean processMessage(Message msg) {
            AdapterService adapterService = AdapterState.this.mAdapterService;
            if (adapterService == null) {
                Log.e(AdapterState.TAG, "receive message at OffState after cleanup:" + msg.what);
                return false;
            }
            switch (msg.what) {
                case 1:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=OFF, MESSAGE = USER_TURN_ON");
                    AdapterState.this.notifyAdapterStateChange(11);
                    AdapterState.this.mPendingCommandState.setTurningOn(true);
                    AdapterState.this.transitionTo(AdapterState.this.mPendingCommandState);
                    AdapterState.this.sendMessageDelayed(100, 5000);
                    adapterService.processStart();
                    break;
                case 20:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=OFF, MESSAGE = USER_TURN_OFF");
                    break;
                default:
                    Log.d(AdapterState.TAG, "ERROR: UNEXPECTED MESSAGE: CURRENT_STATE=OFF, MESSAGE = " + msg.what);
                    return false;
            }
            return true;
        }
    }

    private class OnState extends State {
        private OnState() {
        }

        public void enter() {
            AdapterState.this.infoLog("Entering On State");
            AdapterService adapterService = AdapterState.this.mAdapterService;
            if (adapterService == null) {
                Log.e(AdapterState.TAG, "enter OnState after cleanup");
            } else {
                adapterService.autoConnect();
            }
        }

        public boolean processMessage(Message msg) {
            AdapterProperties adapterProperties = AdapterState.this.mAdapterProperties;
            if (adapterProperties == null) {
                Log.e(AdapterState.TAG, "receive message at OnState after cleanup:" + msg.what);
                return false;
            }
            switch (msg.what) {
                case 1:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=ON, MESSAGE = USER_TURN_ON");
                    Log.i(AdapterState.TAG, "Bluetooth already ON, ignoring USER_TURN_ON");
                    break;
                case 20:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=ON, MESSAGE = USER_TURN_OFF");
                    AdapterState.this.notifyAdapterStateChange(13);
                    AdapterState.this.mPendingCommandState.setTurningOff(true);
                    AdapterState.this.transitionTo(AdapterState.this.mPendingCommandState);
                    AdapterState.this.sendMessageDelayed(AdapterState.this.obtainMessage(AdapterState.SET_SCAN_MODE_TIMEOUT), 2000);
                    adapterProperties.onBluetoothDisable();
                    break;
                default:
                    Log.d(AdapterState.TAG, "ERROR: UNEXPECTED MESSAGE: CURRENT_STATE=ON, MESSAGE = " + msg.what);
                    return false;
            }
            return true;
        }
    }

    private class PendingCommandState extends State {
        private boolean mIsTurningOff;
        private boolean mIsTurningOn;

        private PendingCommandState() {
        }

        public void enter() {
            AdapterState.this.infoLog("Entering PendingCommandState State: isTurningOn()=" + isTurningOn() + ", isTurningOff()=" + isTurningOff());
        }

        public void setTurningOn(boolean isTurningOn) {
            this.mIsTurningOn = isTurningOn;
        }

        public boolean isTurningOn() {
            return this.mIsTurningOn;
        }

        public void setTurningOff(boolean isTurningOff) {
            this.mIsTurningOff = isTurningOff;
        }

        public boolean isTurningOff() {
            return this.mIsTurningOff;
        }

        public boolean processMessage(Message msg) {
            boolean isTurningOn = isTurningOn();
            boolean isTurningOff = isTurningOff();
            AdapterService adapterService = AdapterState.this.mAdapterService;
            AdapterProperties adapterProperties = AdapterState.this.mAdapterProperties;
            if (adapterService == null || adapterProperties == null) {
                Log.e(AdapterState.TAG, "receive message at Pending State after cleanup:" + msg.what);
                return false;
            }
            switch (msg.what) {
                case 1:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=PENDING, MESSAGE = USER_TURN_ON, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    if (!isTurningOn) {
                        Log.i(AdapterState.TAG, "CURRENT_STATE=PENDING: Deferring request USER_TURN_ON");
                        AdapterState.this.deferMessage(msg);
                        break;
                    }
                    Log.i(AdapterState.TAG, "CURRENT_STATE=PENDING: Alreadying turning on bluetooth... Ignoring USER_TURN_ON...");
                    break;
                case 2:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=PENDING, MESSAGE = STARTED, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    AdapterState.this.removeMessages(100);
                    AdapterState.this.sendMessageDelayed(AdapterState.USER_TURN_OFF_DELAY_MS, 3);
                    break;
                case 3:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=PENDING, MESSAGE = ENABLE_READY, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    AdapterState.this.removeMessages(AdapterState.ENABLE_TIMEOUT);
                    adapterProperties.onBluetoothReady();
                    AdapterState.this.mPendingCommandState.setTurningOn(false);
                    AdapterState.this.transitionTo(AdapterState.this.mOnState);
                    AdapterState.this.notifyAdapterStateChange(12);
                    break;
                case 20:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=PENDING, MESSAGE = USER_TURN_ON, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    if (!isTurningOff) {
                        Log.i(AdapterState.TAG, "CURRENT_STATE=PENDING: Deferring request USER_TURN_OFF");
                        AdapterState.this.deferMessage(msg);
                        break;
                    }
                    Log.i(AdapterState.TAG, "CURRENT_STATE=PENDING: Alreadying turning off bluetooth... Ignoring USER_TURN_OFF...");
                    break;
                case 21:
                    break;
                case 24:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=PENDING, MESSAGE = DISABLED, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    if (!isTurningOn) {
                        AdapterState.this.removeMessages(AdapterState.DISABLE_TIMEOUT);
                        AdapterState.this.sendMessageDelayed(AdapterState.STOP_TIMEOUT, 5000);
                        if (adapterService.stopProfileServices()) {
                            Log.d(AdapterState.TAG, "Stopping profile services that were post enabled");
                            break;
                        }
                    }
                    AdapterState.this.removeMessages(AdapterState.ENABLE_TIMEOUT);
                    AdapterState.this.errorLog("Error enabling Bluetooth - hardware init failed");
                    AdapterState.this.mPendingCommandState.setTurningOn(false);
                    AdapterState.this.transitionTo(AdapterState.this.mOffState);
                    adapterService.stopProfileServices();
                    AdapterState.this.notifyAdapterStateChange(10);
                    break;
                case 25:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=PENDING, MESSAGE = STOPPED, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    AdapterState.this.removeMessages(AdapterState.STOP_TIMEOUT);
                    setTurningOff(false);
                    AdapterState.this.transitionTo(AdapterState.this.mOffState);
                    AdapterState.this.notifyAdapterStateChange(10);
                    break;
                case 100:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=PENDING, MESSAGE = START_TIMEOUT, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    AdapterState.this.errorLog("Error enabling Bluetooth");
                    AdapterState.this.mPendingCommandState.setTurningOn(false);
                    AdapterState.this.transitionTo(AdapterState.this.mOffState);
                    AdapterState.this.notifyAdapterStateChange(10);
                    break;
                case AdapterState.ENABLE_TIMEOUT /*101*/:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=PENDING, MESSAGE = ENABLE_TIMEOUT, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    AdapterState.this.errorLog("Error enabling Bluetooth");
                    AdapterState.this.mPendingCommandState.setTurningOn(false);
                    AdapterState.this.transitionTo(AdapterState.this.mOffState);
                    AdapterState.this.notifyAdapterStateChange(10);
                    break;
                case AdapterState.DISABLE_TIMEOUT /*103*/:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=PENDING, MESSAGE = DISABLE_TIMEOUT, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    AdapterState.this.errorLog("Error disabling Bluetooth");
                    AdapterState.this.mPendingCommandState.setTurningOff(false);
                    AdapterState.this.transitionTo(AdapterState.this.mOnState);
                    AdapterState.this.notifyAdapterStateChange(12);
                    break;
                case AdapterState.STOP_TIMEOUT /*104*/:
                    Log.d(AdapterState.TAG, "CURRENT_STATE=PENDING, MESSAGE = STOP_TIMEOUT, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
                    AdapterState.this.errorLog("Error stopping Bluetooth profiles");
                    AdapterState.this.mPendingCommandState.setTurningOff(false);
                    AdapterState.this.transitionTo(AdapterState.this.mOffState);
                    break;
                case AdapterState.SET_SCAN_MODE_TIMEOUT /*105*/:
                    Log.w(AdapterState.TAG, "Timeout will setting scan mode..Continuing with disable...");
                    break;
                default:
                    Log.d(AdapterState.TAG, "ERROR: UNEXPECTED MESSAGE: CURRENT_STATE=PENDING, MESSAGE = " + msg.what);
                    return false;
            }
            Log.d(AdapterState.TAG, "CURRENT_STATE=PENDING, MESSAGE = BEGIN_DISABLE, isTurningOn=" + isTurningOn + ", isTurningOff=" + isTurningOff);
            AdapterState.this.removeMessages(AdapterState.SET_SCAN_MODE_TIMEOUT);
            AdapterState.this.sendMessageDelayed(AdapterState.DISABLE_TIMEOUT, 20000);
            if (!adapterService.disableNative()) {
                AdapterState.this.removeMessages(AdapterState.DISABLE_TIMEOUT);
                Log.e(AdapterState.TAG, "Error while turning Bluetooth Off");
                AdapterState.this.mPendingCommandState.setTurningOff(false);
                AdapterState.this.notifyAdapterStateChange(12);
            }
            return true;
        }
    }

    public boolean isTurningOn() {
        return this.mPendingCommandState.isTurningOn();
    }

    public boolean isTurningOff() {
        return this.mPendingCommandState.isTurningOff();
    }

    private AdapterState(AdapterService service, AdapterProperties adapterProperties) {
        super("BluetoothAdapterState:");
        addState(this.mOnState);
        addState(this.mOffState);
        addState(this.mPendingCommandState);
        this.mAdapterService = service;
        this.mAdapterProperties = adapterProperties;
        setInitialState(this.mOffState);
    }

    public static AdapterState make(AdapterService service, AdapterProperties adapterProperties) {
        Log.d(TAG, "make");
        AdapterState as = new AdapterState(service, adapterProperties);
        as.start();
        return as;
    }

    public void doQuit() {
        quitNow();
    }

    public void cleanup() {
        if (this.mAdapterProperties != null) {
            this.mAdapterProperties = null;
        }
        if (this.mAdapterService != null) {
            this.mAdapterService = null;
        }
    }

    private void notifyAdapterStateChange(int newState) {
        AdapterService adapterService = this.mAdapterService;
        AdapterProperties adapterProperties = this.mAdapterProperties;
        if (adapterService == null || adapterProperties == null) {
            Log.e(TAG, "notifyAdapterStateChange after cleanup:" + newState);
            return;
        }
        int oldState = adapterProperties.getState();
        adapterProperties.setState(newState);
        infoLog("Bluetooth adapter state changed: " + oldState + "-> " + newState);
        adapterService.updateAdapterState(oldState, newState);
    }

    void stateChangeCallback(int status) {
        if (status == 0) {
            sendMessage(24);
        } else if (status == 1) {
            sendMessage(3);
        } else {
            errorLog("Incorrect status in stateChangeCallback");
        }
    }

    private void infoLog(String msg) {
        Log.i(TAG, msg);
    }

    private void errorLog(String msg) {
        Log.e(TAG, msg);
    }
}
