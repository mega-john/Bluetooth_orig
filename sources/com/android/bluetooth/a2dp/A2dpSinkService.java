package com.android.bluetooth.a2dp;

import android.bluetooth.BluetoothAudioConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetoothA2dpSink.Stub;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;
import java.util.ArrayList;
import java.util.List;

public class A2dpSinkService extends ProfileService {
    private static final boolean DBG = false;
    private static final String TAG = "A2dpSinkService";
    private static A2dpSinkService sA2dpSinkService;
    private A2dpSinkStateMachine mStateMachine;

    private static class BluetoothA2dpSinkBinder extends Stub implements IProfileServiceBinder {
        private A2dpSinkService mService;

        private A2dpSinkService getService() {
            if (!Utils.checkCaller()) {
                Log.w(A2dpSinkService.TAG, "A2dp call not allowed for non-active user");
                return null;
            } else if (this.mService == null || !this.mService.isAvailable()) {
                return null;
            } else {
                return this.mService;
            }
        }

        BluetoothA2dpSinkBinder(A2dpSinkService svc) {
            this.mService = svc;
        }

        public boolean cleanup() {
            this.mService = null;
            return true;
        }

        public boolean connect(BluetoothDevice device) {
            A2dpSinkService service = getService();
            if (service == null) {
                return A2dpSinkService.DBG;
            }
            return service.connect(device);
        }

        public boolean disconnect(BluetoothDevice device) {
            A2dpSinkService service = getService();
            if (service == null) {
                return A2dpSinkService.DBG;
            }
            return service.disconnect(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            A2dpSinkService service = getService();
            if (service == null) {
                return new ArrayList(0);
            }
            return service.getConnectedDevices();
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            A2dpSinkService service = getService();
            if (service == null) {
                return new ArrayList(0);
            }
            return service.getDevicesMatchingConnectionStates(states);
        }

        public int getConnectionState(BluetoothDevice device) {
            A2dpSinkService service = getService();
            if (service == null) {
                return 0;
            }
            return service.getConnectionState(device);
        }

        public BluetoothAudioConfig getAudioConfig(BluetoothDevice device) {
            A2dpSinkService service = getService();
            if (service == null) {
                return null;
            }
            return service.getAudioConfig(device);
        }
    }

    protected String getName() {
        return TAG;
    }

    protected IProfileServiceBinder initBinder() {
        return new BluetoothA2dpSinkBinder(this);
    }

    protected boolean start() {
        this.mStateMachine = A2dpSinkStateMachine.make(this, this);
        setA2dpSinkService(this);
        return true;
    }

    protected boolean stop() {
        this.mStateMachine.doQuit();
        return true;
    }

    protected boolean cleanup() {
        if (this.mStateMachine != null) {
            this.mStateMachine.cleanup();
        }
        clearA2dpSinkService();
        return true;
    }

    public static synchronized A2dpSinkService getA2dpSinkService() {
        A2dpSinkService a2dpSinkService;
        synchronized (A2dpSinkService.class) {
            if (sA2dpSinkService == null || !sA2dpSinkService.isAvailable()) {
                a2dpSinkService = null;
            } else {
                a2dpSinkService = sA2dpSinkService;
            }
        }
        return a2dpSinkService;
    }

    private static synchronized void setA2dpSinkService(A2dpSinkService instance) {
        synchronized (A2dpSinkService.class) {
            if (instance != null) {
                if (instance.isAvailable()) {
                    sA2dpSinkService = instance;
                }
            }
        }
    }

    private static synchronized void clearA2dpSinkService() {
        synchronized (A2dpSinkService.class) {
            sA2dpSinkService = null;
        }
    }

    public boolean connect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState == 2 || connectionState == 1) {
            return DBG;
        }
        this.mStateMachine.sendMessage(1, device);
        return true;
    }

    boolean disconnect(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH ADMIN permission");
        int connectionState = this.mStateMachine.getConnectionState(device);
        if (connectionState != 2 && connectionState != 1) {
            return DBG;
        }
        this.mStateMachine.sendMessage(2, device);
        return true;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return this.mStateMachine.getConnectedDevices();
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return this.mStateMachine.getDevicesMatchingConnectionStates(states);
    }

    int getConnectionState(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return this.mStateMachine.getConnectionState(device);
    }

    BluetoothAudioConfig getAudioConfig(BluetoothDevice device) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        return this.mStateMachine.getAudioConfig(device);
    }

    public void dump(StringBuilder sb) {
        super.dump(sb);
        if (this.mStateMachine != null) {
            this.mStateMachine.dump(sb);
        }
    }
}
