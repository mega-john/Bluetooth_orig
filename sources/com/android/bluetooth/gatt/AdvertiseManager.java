package com.android.bluetooth.gatt;

import android.bluetooth.BluetoothUuid;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class AdvertiseManager {
    private static final boolean DBG = true;
    private static final int MSG_START_ADVERTISING = 0;
    private static final int MSG_STOP_ADVERTISING = 1;
    private static final int OPERATION_TIME_OUT_MILLIS = 500;
    private static final String TAG = "BtGatt.AdvertiseManager";
    private final AdapterService mAdapterService;
    private final Set<AdvertiseClient> mAdvertiseClients = new HashSet();
    private final AdvertiseNative mAdvertiseNative = new AdvertiseNative();
    private ClientHandler mHandler;
    private CountDownLatch mLatch;
    private final GattService mService;

    private class AdvertiseNative {
        private static final int ADVERTISING_CHANNEL_37 = 1;
        private static final int ADVERTISING_CHANNEL_38 = 2;
        private static final int ADVERTISING_CHANNEL_39 = 4;
        private static final int ADVERTISING_CHANNEL_ALL = 7;
        private static final int ADVERTISING_EVENT_TYPE_CONNECTABLE = 0;
        private static final int ADVERTISING_EVENT_TYPE_NON_CONNECTABLE = 3;
        private static final int ADVERTISING_EVENT_TYPE_SCANNABLE = 2;
        private static final int ADVERTISING_INTERVAL_DELTA_UNIT = 10;
        private static final int ADVERTISING_INTERVAL_HIGH_MILLS = 1000;
        private static final int ADVERTISING_INTERVAL_LOW_MILLS = 100;
        private static final int ADVERTISING_INTERVAL_MEDIUM_MILLS = 250;
        private static final int ADVERTISING_TX_POWER_LOW = 1;
        private static final int ADVERTISING_TX_POWER_MAX = 4;
        private static final int ADVERTISING_TX_POWER_MID = 2;
        private static final int ADVERTISING_TX_POWER_MIN = 0;
        private static final int ADVERTISING_TX_POWER_UPPER = 3;

        private native void gattAdvertiseNative(int i, boolean z);

        private native void gattClientDisableAdvNative(int i);

        private native void gattClientEnableAdvNative(int i, int i2, int i3, int i4, int i5, int i6, int i7);

        private native void gattClientSetAdvDataNative(int i, boolean z, boolean z2, boolean z3, int i2, byte[] bArr, byte[] bArr2, byte[] bArr3);

        private native void gattClientUpdateAdvNative(int i, int i2, int i3, int i4, int i5, int i6, int i7);

        private native void gattSetAdvDataNative(int i, boolean z, boolean z2, boolean z3, int i2, int i3, int i4, byte[] bArr, byte[] bArr2, byte[] bArr3);

        private AdvertiseNative() {
        }

        boolean startAdverising(AdvertiseClient client) {
            if (!AdvertiseManager.this.mAdapterService.isMultiAdvertisementSupported() && !AdvertiseManager.this.mAdapterService.isPeripheralModeSupported()) {
                return false;
            }
            if (AdvertiseManager.this.mAdapterService.isMultiAdvertisementSupported()) {
                return startMultiAdvertising(client);
            }
            return startSingleAdvertising(client);
        }

        boolean startMultiAdvertising(AdvertiseClient client) {
            AdvertiseManager.this.logd("starting multi advertising");
            resetCountDownLatch();
            enableAdvertising(client);
            if (!waitForCallback()) {
                return false;
            }
            resetCountDownLatch();
            setAdvertisingData(client, client.advertiseData, false);
            if (!waitForCallback()) {
                return false;
            }
            if (client.scanResponse != null) {
                resetCountDownLatch();
                setAdvertisingData(client, client.scanResponse, true);
                if (!waitForCallback()) {
                    return false;
                }
            }
            return true;
        }

        boolean startSingleAdvertising(AdvertiseClient client) {
            AdvertiseManager.this.logd("starting single advertising");
            resetCountDownLatch();
            enableAdvertising(client);
            if (!waitForCallback()) {
                return false;
            }
            setAdvertisingData(client, client.advertiseData, false);
            return true;
        }

        void stopAdvertising(AdvertiseClient client) {
            if (AdvertiseManager.this.mAdapterService.isMultiAdvertisementSupported()) {
                gattClientDisableAdvNative(client.clientIf);
                return;
            }
            gattAdvertiseNative(client.clientIf, false);
            try {
                AdvertiseManager.this.mService.onAdvertiseInstanceDisabled(0, client.clientIf);
            } catch (RemoteException e) {
                Log.d(AdvertiseManager.TAG, "failed onAdvertiseInstanceDisabled", e);
            }
        }

        private void resetCountDownLatch() {
            AdvertiseManager.this.mLatch = new CountDownLatch(1);
        }

        private boolean waitForCallback() {
            try {
                return AdvertiseManager.this.mLatch.await(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        private void enableAdvertising(AdvertiseClient client) {
            int clientIf = client.clientIf;
            int minAdvertiseUnit = (int) getAdvertisingIntervalUnit(client.settings);
            int maxAdvertiseUnit = minAdvertiseUnit + 10;
            int advertiseEventType = getAdvertisingEventType(client);
            int txPowerLevel = getTxPowerLevel(client.settings);
            int advertiseTimeoutSeconds = (int) TimeUnit.MILLISECONDS.toSeconds((long) client.settings.getTimeout());
            if (AdvertiseManager.this.mAdapterService.isMultiAdvertisementSupported()) {
                gattClientEnableAdvNative(clientIf, minAdvertiseUnit, maxAdvertiseUnit, advertiseEventType, 7, txPowerLevel, advertiseTimeoutSeconds);
            } else {
                gattAdvertiseNative(client.clientIf, true);
            }
        }

        private void setAdvertisingData(AdvertiseClient client, AdvertiseData data, boolean isScanResponse) {
            if (data != null) {
                byte[] serviceUuids;
                boolean includeName = data.getIncludeDeviceName();
                boolean includeTxPower = data.getIncludeTxPowerLevel();
                byte[] manufacturerData = getManufacturerData(data);
                byte[] serviceData = getServiceData(data);
                if (data.getServiceUuids() == null) {
                    serviceUuids = new byte[0];
                } else {
                    ByteBuffer advertisingUuidBytes = ByteBuffer.allocate(data.getServiceUuids().size() * 16).order(ByteOrder.LITTLE_ENDIAN);
                    for (ParcelUuid parcelUuid : data.getServiceUuids()) {
                        UUID uuid = parcelUuid.getUuid();
                        advertisingUuidBytes.putLong(uuid.getLeastSignificantBits()).putLong(uuid.getMostSignificantBits());
                    }
                    serviceUuids = advertisingUuidBytes.array();
                }
                if (AdvertiseManager.this.mAdapterService.isMultiAdvertisementSupported()) {
                    gattClientSetAdvDataNative(client.clientIf, isScanResponse, includeName, includeTxPower, 0, manufacturerData, serviceData, serviceUuids);
                } else {
                    gattSetAdvDataNative(client.clientIf, isScanResponse, includeName, includeTxPower, 0, 0, 0, manufacturerData, serviceData, serviceUuids);
                }
            }
        }

        private byte[] getManufacturerData(AdvertiseData advertiseData) {
            if (advertiseData.getManufacturerSpecificData().size() == 0) {
                return new byte[0];
            }
            int manufacturerId = advertiseData.getManufacturerSpecificData().keyAt(0);
            byte[] manufacturerData = (byte[]) advertiseData.getManufacturerSpecificData().get(manufacturerId);
            byte[] concated = new byte[((manufacturerData == null ? 0 : manufacturerData.length) + 2)];
            concated[0] = (byte) (manufacturerId & 255);
            concated[1] = (byte) ((manufacturerId >> 8) & 255);
            if (manufacturerData == null) {
                return concated;
            }
            System.arraycopy(manufacturerData, 0, concated, 2, manufacturerData.length);
            return concated;
        }

        private byte[] getServiceData(AdvertiseData advertiseData) {
            if (advertiseData.getServiceData().isEmpty()) {
                return new byte[0];
            }
            ParcelUuid uuid = (ParcelUuid) advertiseData.getServiceData().keySet().iterator().next();
            byte[] serviceData = (byte[]) advertiseData.getServiceData().get(uuid);
            byte[] concated = new byte[((serviceData == null ? 0 : serviceData.length) + 2)];
            int uuidValue = BluetoothUuid.getServiceIdentifierFromParcelUuid(uuid);
            concated[0] = (byte) (uuidValue & 255);
            concated[1] = (byte) ((uuidValue >> 8) & 255);
            if (serviceData == null) {
                return concated;
            }
            System.arraycopy(serviceData, 0, concated, 2, serviceData.length);
            return concated;
        }

        private int getTxPowerLevel(AdvertiseSettings settings) {
            switch (settings.getTxPowerLevel()) {
                case 0:
                    return 0;
                case 1:
                    return 1;
                case 3:
                    return 3;
                default:
                    return 2;
            }
        }

        private int getAdvertisingEventType(AdvertiseClient client) {
            if (client.settings.isConnectable()) {
                return 0;
            }
            return client.scanResponse == null ? 3 : 2;
        }

        private long getAdvertisingIntervalUnit(AdvertiseSettings settings) {
            switch (settings.getMode()) {
                case 0:
                    return (long) Utils.millsToUnit(1000);
                case 1:
                    return (long) Utils.millsToUnit(ADVERTISING_INTERVAL_MEDIUM_MILLS);
                case 2:
                    return (long) Utils.millsToUnit(100);
                default:
                    return (long) Utils.millsToUnit(1000);
            }
        }
    }

    private class ClientHandler extends Handler {
        ClientHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            AdvertiseManager.this.logd("message : " + msg.what);
            AdvertiseClient client = msg.obj;
            switch (msg.what) {
                case 0:
                    handleStartAdvertising(client);
                    return;
                case 1:
                    handleStopAdvertising(client);
                    return;
                default:
                    Log.e(AdvertiseManager.TAG, "recieve an unknown message : " + msg.what);
                    return;
            }
        }

        private void handleStartAdvertising(AdvertiseClient client) {
            Utils.enforceAdminPermission(AdvertiseManager.this.mService);
            int clientIf = client.clientIf;
            if (AdvertiseManager.this.mAdvertiseClients.contains(Integer.valueOf(clientIf))) {
                AdvertiseManager.this.postCallback(clientIf, 3);
            } else if (AdvertiseManager.this.mAdvertiseClients.size() >= maxAdvertiseInstances()) {
                AdvertiseManager.this.postCallback(clientIf, 2);
            } else if (AdvertiseManager.this.mAdvertiseNative.startAdverising(client)) {
                AdvertiseManager.this.mAdvertiseClients.add(client);
                AdvertiseManager.this.postCallback(clientIf, 0);
            } else {
                AdvertiseManager.this.postCallback(clientIf, 4);
            }
        }

        private void handleStopAdvertising(AdvertiseClient client) {
            Utils.enforceAdminPermission(AdvertiseManager.this.mService);
            if (client != null) {
                AdvertiseManager.this.logd("stop advertise for client " + client.clientIf);
                AdvertiseManager.this.mAdvertiseNative.stopAdvertising(client);
                if (client.appDied) {
                    AdvertiseManager.this.logd("app died - unregistering client : " + client.clientIf);
                    AdvertiseManager.this.mService.unregisterClient(client.clientIf);
                }
                if (AdvertiseManager.this.mAdvertiseClients.contains(client)) {
                    AdvertiseManager.this.mAdvertiseClients.remove(client);
                }
            }
        }

        int maxAdvertiseInstances() {
            if (AdvertiseManager.this.mAdapterService.isMultiAdvertisementSupported()) {
                return AdvertiseManager.this.mAdapterService.getNumOfAdvertisementInstancesSupported() - 1;
            }
            if (AdvertiseManager.this.mAdapterService.isPeripheralModeSupported()) {
                return 1;
            }
            return 0;
        }
    }

    AdvertiseManager(GattService service, AdapterService adapterService) {
        logd("advertise manager created");
        this.mService = service;
        this.mAdapterService = adapterService;
    }

    void start() {
        HandlerThread thread = new HandlerThread("BluetoothAdvertiseManager");
        thread.start();
        this.mHandler = new ClientHandler(thread.getLooper());
    }

    void cleanup() {
        logd("advertise clients cleared");
        this.mAdvertiseClients.clear();
    }

    void startAdvertising(AdvertiseClient client) {
        if (client != null) {
            Message message = new Message();
            message.what = 0;
            message.obj = client;
            this.mHandler.sendMessage(message);
        }
    }

    void stopAdvertising(AdvertiseClient client) {
        if (client != null) {
            Message message = new Message();
            message.what = 1;
            message.obj = client;
            this.mHandler.sendMessage(message);
        }
    }

    void callbackDone(int clientIf, int status) {
        if (status == 0) {
            this.mLatch.countDown();
        } else {
            postCallback(clientIf, 4);
        }
    }

    private void postCallback(int clientIf, int status) {
        try {
            AdvertiseClient client = getAdvertiseClient(clientIf);
            this.mService.onMultipleAdvertiseCallback(clientIf, status, true, client == null ? null : client.settings);
        } catch (RemoteException e) {
            loge("failed onMultipleAdvertiseCallback", e);
        }
    }

    private AdvertiseClient getAdvertiseClient(int clientIf) {
        for (AdvertiseClient client : this.mAdvertiseClients) {
            if (client.clientIf == clientIf) {
                return client;
            }
        }
        return null;
    }

    private void logd(String s) {
        Log.d(TAG, s);
    }

    private void loge(String s, Exception e) {
        Log.e(TAG, s, e);
    }
}
