package com.android.bluetooth.gatt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.IBluetoothGatt.Stub;
import android.bluetooth.IBluetoothGattCallback;
import android.bluetooth.IBluetoothGattServerCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.ResultStorageDescriptor;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.IBinder.DeathRecipient;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;
import com.android.bluetooth.util.NumberUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class GattService extends ProfileService {
    private static final int ADVT_STATE_ONFOUND = 0;
    private static final int ADVT_STATE_ONLOST = 1;
    private static final boolean DBG = true;
    private static final UUID[] HID_UUIDS = new UUID[]{UUID.fromString("00002A4A-0000-1000-8000-00805F9B34FB"), UUID.fromString("00002A4B-0000-1000-8000-00805F9B34FB"), UUID.fromString("00002A4C-0000-1000-8000-00805F9B34FB"), UUID.fromString("00002A4D-0000-1000-8000-00805F9B34FB")};
    private static final int MAC_ADDRESS_LENGTH = 6;
    static final int SCAN_FILTER_ENABLED = 1;
    static final int SCAN_FILTER_MODIFIED = 2;
    private static final String TAG = "BtGatt.GattService";
    private static final int TIME_STAMP_LENGTH = 2;
    private static final int TRUNCATED_RESULT_SIZE = 11;
    private static final boolean VDBG = false;
    private AdvertiseManager mAdvertiseManager;
    private List<UUID> mAdvertisingServiceUuids = new ArrayList();
    ClientMap mClientMap = new ClientMap();
    HandleMap mHandleMap = new HandleMap();
    private int mMaxScanFilters;
    private Map<ScanClient, ScanResult> mOnFoundResults = new HashMap();
    private Set<String> mReliableQueue = new HashSet();
    private ScanManager mScanManager;
    SearchQueue mSearchQueue = new SearchQueue();
    ServerMap mServerMap = new ServerMap();
    private List<ServiceDeclaration> mServiceDeclarations = new ArrayList();

    private static class BluetoothGattBinder extends Stub implements IProfileServiceBinder {
        private GattService mService;

        public BluetoothGattBinder(GattService svc) {
            this.mService = svc;
        }

        public boolean cleanup() {
            this.mService = null;
            return true;
        }

        private GattService getService() {
            if (this.mService != null && this.mService.isAvailable()) {
                return this.mService;
            }
            Log.e(GattService.TAG, "getService() - Service requested, but not available!");
            return null;
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
            GattService service = getService();
            if (service == null) {
                return new ArrayList();
            }
            return service.getDevicesMatchingConnectionStates(states);
        }

        public void registerClient(ParcelUuid uuid, IBluetoothGattCallback callback) {
            GattService service = getService();
            if (service != null) {
                service.registerClient(uuid.getUuid(), callback);
            }
        }

        public void unregisterClient(int clientIf) {
            GattService service = getService();
            if (service != null) {
                service.unregisterClient(clientIf);
            }
        }

        public void startScan(int appIf, boolean isServer, ScanSettings settings, List<ScanFilter> filters, List storages) {
            GattService service = getService();
            if (service != null) {
                service.startScan(appIf, isServer, settings, filters, storages);
            }
        }

        public void stopScan(int appIf, boolean isServer) {
            GattService service = getService();
            if (service != null) {
                service.stopScan(new ScanClient(appIf, isServer));
            }
        }

        public void flushPendingBatchResults(int appIf, boolean isServer) {
            GattService service = getService();
            if (service != null) {
                service.flushPendingBatchResults(appIf, isServer);
            }
        }

        public void clientConnect(int clientIf, String address, boolean isDirect, int transport) {
            GattService service = getService();
            if (service != null) {
                service.clientConnect(clientIf, address, isDirect, transport);
            }
        }

        public void clientDisconnect(int clientIf, String address) {
            GattService service = getService();
            if (service != null) {
                service.clientDisconnect(clientIf, address);
            }
        }

        public void refreshDevice(int clientIf, String address) {
            GattService service = getService();
            if (service != null) {
                service.refreshDevice(clientIf, address);
            }
        }

        public void discoverServices(int clientIf, String address) {
            GattService service = getService();
            if (service != null) {
                service.discoverServices(clientIf, address);
            }
        }

        public void readCharacteristic(int clientIf, String address, int srvcType, int srvcInstanceId, ParcelUuid srvcId, int charInstanceId, ParcelUuid charId, int authReq) {
            GattService service = getService();
            if (service != null) {
                service.readCharacteristic(clientIf, address, srvcType, srvcInstanceId, srvcId.getUuid(), charInstanceId, charId.getUuid(), authReq);
            }
        }

        public void writeCharacteristic(int clientIf, String address, int srvcType, int srvcInstanceId, ParcelUuid srvcId, int charInstanceId, ParcelUuid charId, int writeType, int authReq, byte[] value) {
            GattService service = getService();
            if (service != null) {
                service.writeCharacteristic(clientIf, address, srvcType, srvcInstanceId, srvcId.getUuid(), charInstanceId, charId.getUuid(), writeType, authReq, value);
            }
        }

        public void readDescriptor(int clientIf, String address, int srvcType, int srvcInstanceId, ParcelUuid srvcId, int charInstanceId, ParcelUuid charId, int descrInstanceId, ParcelUuid descrId, int authReq) {
            GattService service = getService();
            if (service != null) {
                service.readDescriptor(clientIf, address, srvcType, srvcInstanceId, srvcId.getUuid(), charInstanceId, charId.getUuid(), descrInstanceId, descrId.getUuid(), authReq);
            }
        }

        public void writeDescriptor(int clientIf, String address, int srvcType, int srvcInstanceId, ParcelUuid srvcId, int charInstanceId, ParcelUuid charId, int descrInstanceId, ParcelUuid descrId, int writeType, int authReq, byte[] value) {
            GattService service = getService();
            if (service != null) {
                service.writeDescriptor(clientIf, address, srvcType, srvcInstanceId, srvcId.getUuid(), charInstanceId, charId.getUuid(), descrInstanceId, descrId.getUuid(), writeType, authReq, value);
            }
        }

        public void beginReliableWrite(int clientIf, String address) {
            GattService service = getService();
            if (service != null) {
                service.beginReliableWrite(clientIf, address);
            }
        }

        public void endReliableWrite(int clientIf, String address, boolean execute) {
            GattService service = getService();
            if (service != null) {
                service.endReliableWrite(clientIf, address, execute);
            }
        }

        public void registerForNotification(int clientIf, String address, int srvcType, int srvcInstanceId, ParcelUuid srvcId, int charInstanceId, ParcelUuid charId, boolean enable) {
            GattService service = getService();
            if (service != null) {
                service.registerForNotification(clientIf, address, srvcType, srvcInstanceId, srvcId.getUuid(), charInstanceId, charId.getUuid(), enable);
            }
        }

        public void readRemoteRssi(int clientIf, String address) {
            GattService service = getService();
            if (service != null) {
                service.readRemoteRssi(clientIf, address);
            }
        }

        public void configureMTU(int clientIf, String address, int mtu) {
            GattService service = getService();
            if (service != null) {
                service.configureMTU(clientIf, address, mtu);
            }
        }

        public void connectionParameterUpdate(int clientIf, String address, int connectionPriority) {
            GattService service = getService();
            if (service != null) {
                service.connectionParameterUpdate(clientIf, address, connectionPriority);
            }
        }

        public void registerServer(ParcelUuid uuid, IBluetoothGattServerCallback callback) {
            GattService service = getService();
            if (service != null) {
                service.registerServer(uuid.getUuid(), callback);
            }
        }

        public void unregisterServer(int serverIf) {
            GattService service = getService();
            if (service != null) {
                service.unregisterServer(serverIf);
            }
        }

        public void serverConnect(int serverIf, String address, boolean isDirect, int transport) {
            GattService service = getService();
            if (service != null) {
                service.serverConnect(serverIf, address, isDirect, transport);
            }
        }

        public void serverDisconnect(int serverIf, String address) {
            GattService service = getService();
            if (service != null) {
                service.serverDisconnect(serverIf, address);
            }
        }

        public void beginServiceDeclaration(int serverIf, int srvcType, int srvcInstanceId, int minHandles, ParcelUuid srvcId, boolean advertisePreferred) {
            GattService service = getService();
            if (service != null) {
                service.beginServiceDeclaration(serverIf, srvcType, srvcInstanceId, minHandles, srvcId.getUuid(), advertisePreferred);
            }
        }

        public void addIncludedService(int serverIf, int srvcType, int srvcInstanceId, ParcelUuid srvcId) {
            GattService service = getService();
            if (service != null) {
                service.addIncludedService(serverIf, srvcType, srvcInstanceId, srvcId.getUuid());
            }
        }

        public void addCharacteristic(int serverIf, ParcelUuid charId, int properties, int permissions) {
            GattService service = getService();
            if (service != null) {
                service.addCharacteristic(serverIf, charId.getUuid(), properties, permissions);
            }
        }

        public void addDescriptor(int serverIf, ParcelUuid descId, int permissions) {
            GattService service = getService();
            if (service != null) {
                service.addDescriptor(serverIf, descId.getUuid(), permissions);
            }
        }

        public void endServiceDeclaration(int serverIf) {
            GattService service = getService();
            if (service != null) {
                service.endServiceDeclaration(serverIf);
            }
        }

        public void removeService(int serverIf, int srvcType, int srvcInstanceId, ParcelUuid srvcId) {
            GattService service = getService();
            if (service != null) {
                service.removeService(serverIf, srvcType, srvcInstanceId, srvcId.getUuid());
            }
        }

        public void clearServices(int serverIf) {
            GattService service = getService();
            if (service != null) {
                service.clearServices(serverIf);
            }
        }

        public void sendResponse(int serverIf, String address, int requestId, int status, int offset, byte[] value) {
            GattService service = getService();
            if (service != null) {
                service.sendResponse(serverIf, address, requestId, status, offset, value);
            }
        }

        public void sendNotification(int serverIf, String address, int srvcType, int srvcInstanceId, ParcelUuid srvcId, int charInstanceId, ParcelUuid charId, boolean confirm, byte[] value) {
            GattService service = getService();
            if (service != null) {
                service.sendNotification(serverIf, address, srvcType, srvcInstanceId, srvcId.getUuid(), charInstanceId, charId.getUuid(), confirm, value);
            }
        }

        public void startMultiAdvertising(int clientIf, AdvertiseData advertiseData, AdvertiseData scanResponse, AdvertiseSettings settings) {
            GattService service = getService();
            if (service != null) {
                service.startMultiAdvertising(clientIf, advertiseData, scanResponse, settings);
            }
        }

        public void stopMultiAdvertising(int clientIf) {
            GattService service = getService();
            if (service != null) {
                service.stopMultiAdvertising(new AdvertiseClient(clientIf));
            }
        }
    }

    class ClientDeathRecipient implements DeathRecipient {
        int mAppIf;

        public ClientDeathRecipient(int appIf) {
            this.mAppIf = appIf;
        }

        public void binderDied() {
            Log.d(GattService.TAG, "Binder is dead - unregistering client (" + this.mAppIf + ")!");
            if (isScanClient(this.mAppIf)) {
                ScanClient client = new ScanClient(this.mAppIf, false);
                client.appDied = true;
                GattService.this.stopScan(client);
                return;
            }
            AdvertiseClient client2 = new AdvertiseClient(this.mAppIf);
            client2.appDied = true;
            GattService.this.stopMultiAdvertising(client2);
        }

        private boolean isScanClient(int clientIf) {
            for (ScanClient client : GattService.this.mScanManager.getRegularScanQueue()) {
                if (client.clientIf == clientIf) {
                    return true;
                }
            }
            for (ScanClient client2 : GattService.this.mScanManager.getBatchScanQueue()) {
                if (client2.clientIf == clientIf) {
                    return true;
                }
            }
            return false;
        }
    }

    class ClientMap extends ContextMap<IBluetoothGattCallback> {
        ClientMap() {
        }
    }

    class ServerDeathRecipient implements DeathRecipient {
        int mAppIf;

        public ServerDeathRecipient(int appIf) {
            this.mAppIf = appIf;
        }

        public void binderDied() {
            Log.d(GattService.TAG, "Binder is dead - unregistering server (" + this.mAppIf + ")!");
            GattService.this.unregisterServer(this.mAppIf);
        }
    }

    class ServerMap extends ContextMap<IBluetoothGattServerCallback> {
        ServerMap() {
        }
    }

    private static native void classInitNative();

    private native void cleanupNative();

    private native void gattClientConfigureMTUNative(int i, int i2);

    private native void gattClientConnectNative(int i, String str, boolean z, int i2);

    private native void gattClientDisconnectNative(int i, String str, int i2);

    private native void gattClientExecuteWriteNative(int i, boolean z);

    private native void gattClientGetCharacteristicNative(int i, int i2, int i3, long j, long j2, int i4, long j3, long j4);

    private native void gattClientGetDescriptorNative(int i, int i2, int i3, long j, long j2, int i4, long j3, long j4, int i5, long j5, long j6);

    private native int gattClientGetDeviceTypeNative(String str);

    private native void gattClientGetIncludedServiceNative(int i, int i2, int i3, long j, long j2, int i4, int i5, long j3, long j4);

    private native void gattClientReadCharacteristicNative(int i, int i2, int i3, long j, long j2, int i4, long j3, long j4, int i5);

    private native void gattClientReadDescriptorNative(int i, int i2, int i3, long j, long j2, int i4, long j3, long j4, int i5, long j5, long j6, int i6);

    private native void gattClientReadRemoteRssiNative(int i, String str);

    private native void gattClientRefreshNative(int i, String str);

    private native void gattClientRegisterAppNative(long j, long j2);

    private native void gattClientRegisterForNotificationsNative(int i, String str, int i2, int i3, long j, long j2, int i4, long j3, long j4, boolean z);

    private native void gattClientSearchServiceNative(int i, boolean z, long j, long j2);

    private native void gattClientUnregisterAppNative(int i);

    private native void gattClientWriteCharacteristicNative(int i, int i2, int i3, long j, long j2, int i4, long j3, long j4, int i5, int i6, byte[] bArr);

    private native void gattClientWriteDescriptorNative(int i, int i2, int i3, long j, long j2, int i4, long j3, long j4, int i5, long j5, long j6, int i6, int i7, byte[] bArr);

    private native void gattConnectionParameterUpdateNative(int i, String str, int i2, int i3, int i4, int i5);

    private native void gattServerAddCharacteristicNative(int i, int i2, long j, long j2, int i3, int i4);

    private native void gattServerAddDescriptorNative(int i, int i2, long j, long j2, int i3);

    private native void gattServerAddIncludedServiceNative(int i, int i2, int i3);

    private native void gattServerAddServiceNative(int i, int i2, int i3, long j, long j2, int i4);

    private native void gattServerConnectNative(int i, String str, boolean z, int i2);

    private native void gattServerDeleteServiceNative(int i, int i2);

    private native void gattServerDisconnectNative(int i, String str, int i2);

    private native void gattServerRegisterAppNative(long j, long j2);

    private native void gattServerSendIndicationNative(int i, int i2, int i3, byte[] bArr);

    private native void gattServerSendNotificationNative(int i, int i2, int i3, byte[] bArr);

    private native void gattServerSendResponseNative(int i, int i2, int i3, int i4, int i5, int i6, byte[] bArr, int i7);

    private native void gattServerStartServiceNative(int i, int i2, int i3);

    private native void gattServerStopServiceNative(int i, int i2);

    private native void gattServerUnregisterAppNative(int i);

    private native void gattTestNative(int i, long j, long j2, String str, int i2, int i3, int i4, int i5, int i6);

    private native void initializeNative();

    static {
        classInitNative();
    }

    private ServiceDeclaration addDeclaration() {
        synchronized (this.mServiceDeclarations) {
            this.mServiceDeclarations.add(new ServiceDeclaration());
        }
        return getActiveDeclaration();
    }

    private ServiceDeclaration getActiveDeclaration() {
        synchronized (this.mServiceDeclarations) {
            if (this.mServiceDeclarations.size() > 0) {
                ServiceDeclaration serviceDeclaration = (ServiceDeclaration) this.mServiceDeclarations.get(this.mServiceDeclarations.size() - 1);
                return serviceDeclaration;
            }
            return null;
        }
    }

    private ServiceDeclaration getPendingDeclaration() {
        synchronized (this.mServiceDeclarations) {
            if (this.mServiceDeclarations.size() > 0) {
                ServiceDeclaration serviceDeclaration = (ServiceDeclaration) this.mServiceDeclarations.get(0);
                return serviceDeclaration;
            }
            return null;
        }
    }

    private void removePendingDeclaration() {
        synchronized (this.mServiceDeclarations) {
            if (this.mServiceDeclarations.size() > 0) {
                this.mServiceDeclarations.remove(0);
            }
        }
    }

    protected String getName() {
        return TAG;
    }

    protected IProfileServiceBinder initBinder() {
        return new BluetoothGattBinder(this);
    }

    protected boolean start() {
        Log.d(TAG, "start()");
        initializeNative();
        this.mAdvertiseManager = new AdvertiseManager(this, AdapterService.getAdapterService());
        this.mAdvertiseManager.start();
        this.mScanManager = new ScanManager(this);
        this.mScanManager.start();
        return true;
    }

    protected boolean stop() {
        Log.d(TAG, "stop()");
        this.mClientMap.clear();
        this.mServerMap.clear();
        this.mSearchQueue.clear();
        this.mHandleMap.clear();
        this.mServiceDeclarations.clear();
        this.mReliableQueue.clear();
        if (this.mAdvertiseManager != null) {
            this.mAdvertiseManager.cleanup();
        }
        if (this.mScanManager != null) {
            this.mScanManager.cleanup();
        }
        return true;
    }

    protected boolean cleanup() {
        Log.d(TAG, "cleanup()");
        cleanupNative();
        if (this.mAdvertiseManager != null) {
            this.mAdvertiseManager.cleanup();
        }
        if (this.mScanManager != null) {
            this.mScanManager.cleanup();
        }
        return true;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        if (GattDebugUtils.handleDebugAction(this, intent)) {
            return 2;
        }
        return super.onStartCommand(intent, flags, startId);
    }

    void onScanResult(String address, int rssi, byte[] adv_data) {
        List<UUID> remoteUuids = parseUuids(adv_data);
        for (ScanClient client : this.mScanManager.getRegularScanQueue()) {
            if (client.uuids.length > 0) {
                int matches = 0;
                for (UUID search : client.uuids) {
                    for (UUID remote : remoteUuids) {
                        if (remote.equals(search)) {
                            matches++;
                            break;
                        }
                    }
                }
                if (matches < client.uuids.length) {
                }
            }
            if (client.isServer) {
                App app = this.mServerMap.getById(client.clientIf);
                if (app != null) {
                    try {
                        ((IBluetoothGattServerCallback) app.callback).onScanResult(address, rssi, adv_data);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Exception: " + e);
                        this.mServerMap.remove(client.clientIf);
                        this.mScanManager.stopScan(client);
                    }
                }
            } else {
                App app2 = this.mClientMap.getById(client.clientIf);
                if (app2 != null) {
                    ScanResult result = new ScanResult(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address), ScanRecord.parseFromBytes(adv_data), rssi, SystemClock.elapsedRealtimeNanos());
                    if (matchesFilters(client, result)) {
                        try {
                            ScanSettings settings = client.settings;
                            if ((settings.getCallbackType() & 2) != 0) {
                                synchronized (this.mOnFoundResults) {
                                    this.mOnFoundResults.put(client, result);
                                }
                                ((IBluetoothGattCallback) app2.callback).onFoundOrLost(true, result);
                            }
                            if ((settings.getCallbackType() & 1) != 0) {
                                ((IBluetoothGattCallback) app2.callback).onScanResult(result);
                            }
                        } catch (RemoteException e2) {
                            Log.e(TAG, "Exception: " + e2);
                            this.mClientMap.remove(client.clientIf);
                            this.mScanManager.stopScan(client);
                        }
                    }
                }
            }
        }
    }

    private boolean matchesFilters(ScanClient client, ScanResult scanResult) {
        if (client.filters == null || client.filters.isEmpty()) {
            return true;
        }
        for (ScanFilter filter : client.filters) {
            if (filter.matches(scanResult)) {
                return true;
            }
        }
        return false;
    }

    void onClientRegistered(int status, int clientIf, long uuidLsb, long uuidMsb) throws RemoteException {
        UUID uuid = new UUID(uuidMsb, uuidLsb);
        Log.d(TAG, "onClientRegistered() - UUID=" + uuid + ", clientIf=" + clientIf);
        App app = this.mClientMap.getByUuid(uuid);
        if (app != null) {
            if (status == 0) {
                app.id = clientIf;
                app.linkToDeath(new ClientDeathRecipient(clientIf));
            } else {
                this.mClientMap.remove(uuid);
            }
            ((IBluetoothGattCallback) app.callback).onClientRegistered(status, clientIf);
        }
    }

    void onConnected(int clientIf, int connId, int status, String address) throws RemoteException {
        Log.d(TAG, "onConnected() - clientIf=" + clientIf + ", connId=" + connId + ", address=" + address);
        if (status == 0) {
            this.mClientMap.addConnection(clientIf, connId, address);
        }
        App app = this.mClientMap.getById(clientIf);
        if (app != null) {
            ((IBluetoothGattCallback) app.callback).onClientConnectionState(status, clientIf, status == 0, address);
        }
    }

    void onDisconnected(int clientIf, int connId, int status, String address) throws RemoteException {
        Log.d(TAG, "onDisconnected() - clientIf=" + clientIf + ", connId=" + connId + ", address=" + address);
        this.mClientMap.removeConnection(clientIf, connId);
        this.mSearchQueue.removeConnId(connId);
        App app = this.mClientMap.getById(clientIf);
        if (app != null) {
            ((IBluetoothGattCallback) app.callback).onClientConnectionState(status, clientIf, false, address);
        }
    }

    void onSearchCompleted(int connId, int status) throws RemoteException {
        Log.d(TAG, "onSearchCompleted() - connId=" + connId + ", status=" + status);
        continueSearch(connId, status);
    }

    void onSearchResult(int connId, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb) throws RemoteException {
        UUID uuid = new UUID(srvcUuidMsb, srvcUuidLsb);
        String address = this.mClientMap.addressByConnId(connId);
        this.mSearchQueue.add(connId, srvcType, srvcInstId, srvcUuidLsb, srvcUuidMsb);
        App app = this.mClientMap.getByConnId(connId);
        if (app != null) {
            ((IBluetoothGattCallback) app.callback).onGetService(address, srvcType, srvcInstId, new ParcelUuid(uuid));
        }
    }

    void onGetCharacteristic(int connId, int status, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb, int charInstId, long charUuidLsb, long charUuidMsb, int charProp) throws RemoteException {
        UUID uuid = new UUID(srvcUuidMsb, srvcUuidLsb);
        uuid = new UUID(charUuidMsb, charUuidLsb);
        String address = this.mClientMap.addressByConnId(connId);
        if (status == 0) {
            this.mSearchQueue.add(connId, srvcType, srvcInstId, srvcUuidLsb, srvcUuidMsb, charInstId, charUuidLsb, charUuidMsb);
            App app = this.mClientMap.getByConnId(connId);
            if (app != null) {
                ((IBluetoothGattCallback) app.callback).onGetCharacteristic(address, srvcType, srvcInstId, new ParcelUuid(uuid), charInstId, new ParcelUuid(uuid), charProp);
            }
            gattClientGetCharacteristicNative(connId, srvcType, srvcInstId, srvcUuidLsb, srvcUuidMsb, charInstId, charUuidLsb, charUuidMsb);
            return;
        }
        gattClientGetIncludedServiceNative(connId, srvcType, srvcInstId, srvcUuidLsb, srvcUuidMsb, 0, 0, 0, 0);
    }

    void onGetDescriptor(int connId, int status, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb, int charInstId, long charUuidLsb, long charUuidMsb, int descrInstId, long descrUuidLsb, long descrUuidMsb) throws RemoteException {
        UUID uuid = new UUID(srvcUuidMsb, srvcUuidLsb);
        uuid = new UUID(charUuidMsb, charUuidLsb);
        uuid = new UUID(descrUuidMsb, descrUuidLsb);
        String address = this.mClientMap.addressByConnId(connId);
        if (status == 0) {
            App app = this.mClientMap.getByConnId(connId);
            if (app != null) {
                ((IBluetoothGattCallback) app.callback).onGetDescriptor(address, srvcType, srvcInstId, new ParcelUuid(uuid), charInstId, new ParcelUuid(uuid), descrInstId, new ParcelUuid(uuid));
            }
            gattClientGetDescriptorNative(connId, srvcType, srvcInstId, srvcUuidLsb, srvcUuidMsb, charInstId, charUuidLsb, charUuidMsb, descrInstId, descrUuidLsb, descrUuidMsb);
            return;
        }
        continueSearch(connId, 0);
    }

    void onGetIncludedService(int connId, int status, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb, int inclSrvcType, int inclSrvcInstId, long inclSrvcUuidLsb, long inclSrvcUuidMsb) throws RemoteException {
        UUID uuid = new UUID(srvcUuidMsb, srvcUuidLsb);
        uuid = new UUID(inclSrvcUuidMsb, inclSrvcUuidLsb);
        String address = this.mClientMap.addressByConnId(connId);
        if (status == 0) {
            App app = this.mClientMap.getByConnId(connId);
            if (app != null) {
                ((IBluetoothGattCallback) app.callback).onGetIncludedService(address, srvcType, srvcInstId, new ParcelUuid(uuid), inclSrvcType, inclSrvcInstId, new ParcelUuid(uuid));
            }
            gattClientGetIncludedServiceNative(connId, srvcType, srvcInstId, srvcUuidLsb, srvcUuidMsb, inclSrvcType, inclSrvcInstId, inclSrvcUuidLsb, inclSrvcUuidMsb);
            return;
        }
        continueSearch(connId, 0);
    }

    void onRegisterForNotifications(int connId, int status, int registered, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb, int charInstId, long charUuidLsb, long charUuidMsb) {
        UUID srvcUuid = new UUID(srvcUuidMsb, srvcUuidLsb);
        Log.d(TAG, "onRegisterForNotifications() - address=" + this.mClientMap.addressByConnId(connId) + ", status=" + status + ", registered=" + registered + ", charUuid=" + new UUID(charUuidMsb, charUuidLsb));
    }

    void onNotify(int connId, String address, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb, int charInstId, long charUuidLsb, long charUuidMsb, boolean isNotify, byte[] data) throws RemoteException {
        UUID srvcUuid = new UUID(srvcUuidMsb, srvcUuidLsb);
        UUID charUuid = new UUID(charUuidMsb, charUuidLsb);
        if (!isHidUuid(charUuid) || checkCallingOrSelfPermission("android.permission.BLUETOOTH_PRIVILEGED") == 0) {
            App app = this.mClientMap.getByConnId(connId);
            if (app != null) {
                ((IBluetoothGattCallback) app.callback).onNotify(address, srvcType, srvcInstId, new ParcelUuid(srvcUuid), charInstId, new ParcelUuid(charUuid), data);
            }
        }
    }

    void onReadCharacteristic(int connId, int status, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb, int charInstId, long charUuidLsb, long charUuidMsb, int charType, byte[] data) throws RemoteException {
        UUID srvcUuid = new UUID(srvcUuidMsb, srvcUuidLsb);
        UUID charUuid = new UUID(charUuidMsb, charUuidLsb);
        String address = this.mClientMap.addressByConnId(connId);
        App app = this.mClientMap.getByConnId(connId);
        if (app != null) {
            ((IBluetoothGattCallback) app.callback).onCharacteristicRead(address, status, srvcType, srvcInstId, new ParcelUuid(srvcUuid), charInstId, new ParcelUuid(charUuid), data);
        }
    }

    void onWriteCharacteristic(int connId, int status, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb, int charInstId, long charUuidLsb, long charUuidMsb) throws RemoteException {
        UUID srvcUuid = new UUID(srvcUuidMsb, srvcUuidLsb);
        UUID charUuid = new UUID(charUuidMsb, charUuidLsb);
        String address = this.mClientMap.addressByConnId(connId);
        App app = this.mClientMap.getByConnId(connId);
        if (app != null) {
            if (app.isCongested.booleanValue()) {
                if (status == 143) {
                    status = 0;
                }
                app.queueCallback(new CallbackInfo(address, status, srvcType, srvcInstId, srvcUuid, charInstId, charUuid));
                return;
            }
            ((IBluetoothGattCallback) app.callback).onCharacteristicWrite(address, status, srvcType, srvcInstId, new ParcelUuid(srvcUuid), charInstId, new ParcelUuid(charUuid));
        }
    }

    void onExecuteCompleted(int connId, int status) throws RemoteException {
        String address = this.mClientMap.addressByConnId(connId);
        App app = this.mClientMap.getByConnId(connId);
        if (app != null) {
            ((IBluetoothGattCallback) app.callback).onExecuteWrite(address, status);
        }
    }

    void onReadDescriptor(int connId, int status, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb, int charInstId, long charUuidLsb, long charUuidMsb, int descrInstId, long descrUuidLsb, long descrUuidMsb, int charType, byte[] data) throws RemoteException {
        UUID uuid = new UUID(srvcUuidMsb, srvcUuidLsb);
        uuid = new UUID(charUuidMsb, charUuidLsb);
        uuid = new UUID(descrUuidMsb, descrUuidLsb);
        String address = this.mClientMap.addressByConnId(connId);
        App app = this.mClientMap.getByConnId(connId);
        if (app != null) {
            ((IBluetoothGattCallback) app.callback).onDescriptorRead(address, status, srvcType, srvcInstId, new ParcelUuid(uuid), charInstId, new ParcelUuid(uuid), descrInstId, new ParcelUuid(uuid), data);
        }
    }

    void onWriteDescriptor(int connId, int status, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb, int charInstId, long charUuidLsb, long charUuidMsb, int descrInstId, long descrUuidLsb, long descrUuidMsb) throws RemoteException {
        UUID uuid = new UUID(srvcUuidMsb, srvcUuidLsb);
        uuid = new UUID(charUuidMsb, charUuidLsb);
        uuid = new UUID(descrUuidMsb, descrUuidLsb);
        String address = this.mClientMap.addressByConnId(connId);
        App app = this.mClientMap.getByConnId(connId);
        if (app != null) {
            ((IBluetoothGattCallback) app.callback).onDescriptorWrite(address, status, srvcType, srvcInstId, new ParcelUuid(uuid), charInstId, new ParcelUuid(uuid), descrInstId, new ParcelUuid(uuid));
        }
    }

    void onReadRemoteRssi(int clientIf, String address, int rssi, int status) throws RemoteException {
        Log.d(TAG, "onReadRemoteRssi() - clientIf=" + clientIf + " address=" + address + ", rssi=" + rssi + ", status=" + status);
        App app = this.mClientMap.getById(clientIf);
        if (app != null) {
            ((IBluetoothGattCallback) app.callback).onReadRemoteRssi(address, rssi, status);
        }
    }

    void onScanFilterEnableDisabled(int action, int status, int clientIf) {
        Log.d(TAG, "onScanFilterEnableDisabled() - clientIf=" + clientIf + ", status=" + status + ", action=" + action);
        this.mScanManager.callbackDone(clientIf, status);
    }

    void onScanFilterParamsConfigured(int action, int status, int clientIf, int availableSpace) {
        Log.d(TAG, "onScanFilterParamsConfigured() - clientIf=" + clientIf + ", status=" + status + ", action=" + action + ", availableSpace=" + availableSpace);
        this.mScanManager.callbackDone(clientIf, status);
    }

    void onScanFilterConfig(int action, int status, int clientIf, int filterType, int availableSpace) {
        Log.d(TAG, "onScanFilterConfig() - clientIf=" + clientIf + ", action = " + action + " status = " + status + ", filterType=" + filterType + ", availableSpace=" + availableSpace);
        this.mScanManager.callbackDone(clientIf, status);
    }

    void onBatchScanStorageConfigured(int status, int clientIf) {
        Log.d(TAG, "onBatchScanStorageConfigured() - clientIf=" + clientIf + ", status=" + status);
        this.mScanManager.callbackDone(clientIf, status);
    }

    void onBatchScanStartStopped(int startStopAction, int status, int clientIf) {
        Log.d(TAG, "onBatchScanStartStopped() - clientIf=" + clientIf + ", status=" + status + ", startStopAction=" + startStopAction);
        this.mScanManager.callbackDone(clientIf, status);
    }

    void onBatchScanReports(int status, int clientIf, int reportType, int numRecords, byte[] recordData) throws RemoteException {
        Log.d(TAG, "onBatchScanReports() - clientIf=" + clientIf + ", status=" + status + ", reportType=" + reportType + ", numRecords=" + numRecords);
        this.mScanManager.callbackDone(clientIf, status);
        Set<ScanResult> results = parseBatchScanResults(numRecords, reportType, recordData);
        if (reportType == 1) {
            App app = this.mClientMap.getById(clientIf);
            if (app != null) {
                ((IBluetoothGattCallback) app.callback).onBatchScanResults(new ArrayList(results));
                return;
            }
            return;
        }
        for (ScanClient client : this.mScanManager.getFullBatchScanQueue()) {
            deliverBatchScan(client, results);
        }
    }

    private void deliverBatchScan(ScanClient client, Set<ScanResult> allResults) throws RemoteException {
        App app = this.mClientMap.getById(client.clientIf);
        if (app != null) {
            if (client.filters == null || client.filters.isEmpty()) {
                ((IBluetoothGattCallback) app.callback).onBatchScanResults(new ArrayList(allResults));
            }
            List<ScanResult> results = new ArrayList();
            for (ScanResult scanResult : allResults) {
                if (matchesFilters(client, scanResult)) {
                    results.add(scanResult);
                }
            }
            ((IBluetoothGattCallback) app.callback).onBatchScanResults(results);
        }
    }

    private Set<ScanResult> parseBatchScanResults(int numRecords, int reportType, byte[] batchRecord) {
        if (numRecords == 0) {
            return Collections.emptySet();
        }
        Log.d(TAG, "current time is " + SystemClock.elapsedRealtimeNanos());
        if (reportType == 1) {
            return parseTruncatedResults(numRecords, batchRecord);
        }
        return parseFullResults(numRecords, batchRecord);
    }

    private Set<ScanResult> parseTruncatedResults(int numRecords, byte[] batchRecord) {
        Log.d(TAG, "batch record " + Arrays.toString(batchRecord));
        Set<ScanResult> results = new HashSet(numRecords);
        long now = SystemClock.elapsedRealtimeNanos();
        for (int i = 0; i < numRecords; i++) {
            byte[] record = extractBytes(batchRecord, i * 11, 11);
            byte[] address = extractBytes(record, 0, 6);
            reverse(address);
            results.add(new ScanResult(this.mAdapter.getRemoteDevice(address), ScanRecord.parseFromBytes(new byte[0]), record[8], now - parseTimestampNanos(extractBytes(record, 9, 2))));
        }
        return results;
    }

    long parseTimestampNanos(byte[] data) {
        return TimeUnit.MILLISECONDS.toNanos(50 * ((long) NumberUtils.littleEndianByteArrayToInt(data)));
    }

    private Set<ScanResult> parseFullResults(int numRecords, byte[] batchRecord) {
        Log.d(TAG, "Batch record : " + Arrays.toString(batchRecord));
        Set<ScanResult> hashSet = new HashSet(numRecords);
        int position = 0;
        long now = SystemClock.elapsedRealtimeNanos();
        while (position < batchRecord.length) {
            byte[] address = extractBytes(batchRecord, position, 6);
            reverse(address);
            BluetoothDevice device = this.mAdapter.getRemoteDevice(address);
            position = ((position + 6) + 1) + 1;
            int position2 = position + 1;
            int rssi = batchRecord[position];
            long timestampNanos = now - parseTimestampNanos(extractBytes(batchRecord, position2, 2));
            position = position2 + 2;
            position2 = position + 1;
            int advertisePacketLen = batchRecord[position];
            byte[] advertiseBytes = extractBytes(batchRecord, position2, advertisePacketLen);
            position = position2 + advertisePacketLen;
            position2 = position + 1;
            int scanResponsePacketLen = batchRecord[position];
            byte[] scanResponseBytes = extractBytes(batchRecord, position2, scanResponsePacketLen);
            position = position2 + scanResponsePacketLen;
            byte[] scanRecord = new byte[(advertisePacketLen + scanResponsePacketLen)];
            System.arraycopy(advertiseBytes, 0, scanRecord, 0, advertisePacketLen);
            System.arraycopy(scanResponseBytes, 0, scanRecord, advertisePacketLen, scanResponsePacketLen);
            Log.d(TAG, "ScanRecord : " + Arrays.toString(scanRecord));
            hashSet.add(new ScanResult(device, ScanRecord.parseFromBytes(scanRecord), rssi, timestampNanos));
        }
        return hashSet;
    }

    private void reverse(byte[] address) {
        int len = address.length;
        for (int i = 0; i < len / 2; i++) {
            byte b = address[i];
            address[i] = address[(len - 1) - i];
            address[(len - 1) - i] = b;
        }
    }

    private static byte[] extractBytes(byte[] scanRecord, int start, int length) {
        byte[] bytes = new byte[length];
        System.arraycopy(scanRecord, start, bytes, 0, length);
        return bytes;
    }

    void onBatchScanThresholdCrossed(int clientIf) {
        Log.d(TAG, "onBatchScanThresholdCrossed() - clientIf=" + clientIf);
        flushPendingBatchResults(clientIf, false);
    }

    void onTrackAdvFoundLost(int filterIndex, int addrType, String address, int advState, int clientIf) throws RemoteException {
        Log.d(TAG, "onClientAdvertiserFoundLost() - clientIf=" + clientIf + "address = " + address + "adv_state = " + advState + "client_if = " + clientIf);
        App app = this.mClientMap.getById(clientIf);
        if (app == null || app.callback == null) {
            Log.e(TAG, "app or callback is null");
        } else if (advState == 1) {
            for (ScanClient client : this.mScanManager.getRegularScanQueue()) {
                if (client.clientIf == clientIf && (client.settings.getCallbackType() & 4) != 0) {
                    while (!this.mOnFoundResults.isEmpty()) {
                        ((IBluetoothGattCallback) app.callback).onFoundOrLost(false, (ScanResult) this.mOnFoundResults.get(client));
                        synchronized (this.mOnFoundResults) {
                            this.mOnFoundResults.remove(client);
                        }
                    }
                    continue;
                }
            }
        }
    }

    void onMultipleAdvertiseCallback(int clientIf, int status, boolean isStart, AdvertiseSettings settings) throws RemoteException {
        App app = this.mClientMap.getById(clientIf);
        if (app == null || app.callback == null) {
            Log.e(TAG, "Advertise app or callback is null");
        } else {
            ((IBluetoothGattCallback) app.callback).onMultiAdvertiseCallback(status, isStart, settings);
        }
    }

    void onConfigureMTU(int connId, int status, int mtu) throws RemoteException {
        String address = this.mClientMap.addressByConnId(connId);
        Log.d(TAG, "onConfigureMTU() address=" + address + ", status=" + status + ", mtu=" + mtu);
        App app = this.mClientMap.getByConnId(connId);
        if (app != null) {
            ((IBluetoothGattCallback) app.callback).onConfigureMTU(address, mtu, status);
        }
    }

    void onAdvertiseCallback(int status, int clientIf) {
        Log.d(TAG, "onAdvertiseCallback,- clientIf=" + clientIf + ", status=" + status);
        this.mAdvertiseManager.callbackDone(clientIf, status);
    }

    void onAdvertiseInstanceEnabled(int status, int clientIf) {
        Log.d(TAG, "onAdvertiseInstanceEnabled() - clientIf=" + clientIf + ", status=" + status);
        this.mAdvertiseManager.callbackDone(clientIf, status);
    }

    void onAdvertiseDataUpdated(int status, int client_if) {
        Log.d(TAG, "onAdvertiseDataUpdated() - client_if=" + client_if + ", status=" + status);
    }

    void onAdvertiseDataSet(int status, int clientIf) {
        Log.d(TAG, "onAdvertiseDataSet() - clientIf=" + clientIf + ", status=" + status);
        this.mAdvertiseManager.callbackDone(clientIf, status);
    }

    void onAdvertiseInstanceDisabled(int status, int clientIf) throws RemoteException {
        Log.d(TAG, "onAdvertiseInstanceDisabled() - clientIf=" + clientIf + ", status=" + status);
        App app = this.mClientMap.getById(clientIf);
        if (app != null) {
            Log.d(TAG, "Client app is not null!");
            if (status == 0) {
                ((IBluetoothGattCallback) app.callback).onMultiAdvertiseCallback(0, false, null);
            } else {
                ((IBluetoothGattCallback) app.callback).onMultiAdvertiseCallback(4, false, null);
            }
        }
    }

    void onClientCongestion(int connId, boolean congested) throws RemoteException {
        App app = this.mClientMap.getByConnId(connId);
        if (app != null) {
            app.isCongested = Boolean.valueOf(congested);
            while (!app.isCongested.booleanValue()) {
                CallbackInfo callbackInfo = app.popQueuedCallback();
                if (callbackInfo != null) {
                    ((IBluetoothGattCallback) app.callback).onCharacteristicWrite(callbackInfo.address, callbackInfo.status, callbackInfo.srvcType, callbackInfo.srvcInstId, new ParcelUuid(callbackInfo.srvcUuid), callbackInfo.charInstId, new ParcelUuid(callbackInfo.charUuid));
                } else {
                    return;
                }
            }
        }
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        BluetoothDevice device;
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Map<BluetoothDevice, Integer> deviceStates = new HashMap();
        for (BluetoothDevice device2 : this.mAdapter.getBondedDevices()) {
            if (getDeviceType(device2) != 1) {
                deviceStates.put(device2, Integer.valueOf(0));
            }
        }
        Set<String> connectedDevices = new HashSet();
        connectedDevices.addAll(this.mClientMap.getConnectedDevices());
        connectedDevices.addAll(this.mServerMap.getConnectedDevices());
        for (String address : connectedDevices) {
            device2 = this.mAdapter.getRemoteDevice(address);
            if (device2 != null) {
                deviceStates.put(device2, Integer.valueOf(2));
            }
        }
        List<BluetoothDevice> deviceList = new ArrayList();
        for (Entry<BluetoothDevice, Integer> entry : deviceStates.entrySet()) {
            for (int state : states) {
                if (((Integer) entry.getValue()).intValue() == state) {
                    deviceList.add(entry.getKey());
                }
            }
        }
        return deviceList;
    }

    void startScan(int appIf, boolean isServer, ScanSettings settings, List<ScanFilter> filters, List<List<ResultStorageDescriptor>> storages) {
        Log.d(TAG, "start scan with filters");
        enforceAdminPermission();
        if (needsPrivilegedPermissionForScan(settings)) {
            enforcePrivilegedPermission();
        }
        this.mScanManager.startScan(new ScanClient(appIf, isServer, settings, filters, storages));
    }

    void flushPendingBatchResults(int clientIf, boolean isServer) {
        Log.d(TAG, "flushPendingBatchResults - clientIf=" + clientIf + ", isServer=" + isServer);
        this.mScanManager.flushBatchScanResults(new ScanClient(clientIf, isServer));
    }

    void stopScan(ScanClient client) {
        enforceAdminPermission();
        Log.d(TAG, "stopScan() - queue size =" + (this.mScanManager.getBatchScanQueue().size() + this.mScanManager.getRegularScanQueue().size()));
        this.mScanManager.stopScan(client);
    }

    void registerClient(UUID uuid, IBluetoothGattCallback callback) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "registerClient() - UUID=" + uuid);
        this.mClientMap.add(uuid, callback);
        gattClientRegisterAppNative(uuid.getLeastSignificantBits(), uuid.getMostSignificantBits());
    }

    void unregisterClient(int clientIf) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "unregisterClient() - clientIf=" + clientIf);
        this.mClientMap.remove(clientIf);
        gattClientUnregisterAppNative(clientIf);
    }

    void clientConnect(int clientIf, String address, boolean isDirect, int transport) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "clientConnect() - address=" + address + ", isDirect=" + isDirect);
        gattClientConnectNative(clientIf, address, isDirect, transport);
    }

    void clientDisconnect(int clientIf, String address) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Integer connId = this.mClientMap.connIdByAddress(clientIf, address);
        Log.d(TAG, "clientDisconnect() - address=" + address + ", connId=" + connId);
        gattClientDisconnectNative(clientIf, address, connId != null ? connId.intValue() : 0);
    }

    void startMultiAdvertising(int clientIf, AdvertiseData advertiseData, AdvertiseData scanResponse, AdvertiseSettings settings) {
        enforceAdminPermission();
        this.mAdvertiseManager.startAdvertising(new AdvertiseClient(clientIf, settings, advertiseData, scanResponse));
    }

    void stopMultiAdvertising(AdvertiseClient client) {
        enforceAdminPermission();
        this.mAdvertiseManager.stopAdvertising(client);
    }

    synchronized List<ParcelUuid> getRegisteredServiceUuids() {
        List<ParcelUuid> serviceUuids;
        Utils.enforceAdminPermission(this);
        serviceUuids = new ArrayList();
        for (Entry entry : this.mHandleMap.mEntries) {
            serviceUuids.add(new ParcelUuid(entry.uuid));
        }
        return serviceUuids;
    }

    List<String> getConnectedDevices() {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Set<String> connectedDevAddress = new HashSet();
        connectedDevAddress.addAll(this.mClientMap.getConnectedDevices());
        connectedDevAddress.addAll(this.mServerMap.getConnectedDevices());
        return new ArrayList(connectedDevAddress);
    }

    void refreshDevice(int clientIf, String address) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "refreshDevice() - address=" + address);
        gattClientRefreshNative(clientIf, address);
    }

    void discoverServices(int clientIf, String address) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Integer connId = this.mClientMap.connIdByAddress(clientIf, address);
        Log.d(TAG, "discoverServices() - address=" + address + ", connId=" + connId);
        if (connId != null) {
            gattClientSearchServiceNative(connId.intValue(), true, 0, 0);
        } else {
            Log.e(TAG, "discoverServices() - No connection for " + address + "...");
        }
    }

    void readCharacteristic(int clientIf, String address, int srvcType, int srvcInstanceId, UUID srvcUuid, int charInstanceId, UUID charUuid, int authReq) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (isHidUuid(charUuid)) {
            enforcePrivilegedPermission();
        }
        Integer connId = this.mClientMap.connIdByAddress(clientIf, address);
        if (connId != null) {
            gattClientReadCharacteristicNative(connId.intValue(), srvcType, srvcInstanceId, srvcUuid.getLeastSignificantBits(), srvcUuid.getMostSignificantBits(), charInstanceId, charUuid.getLeastSignificantBits(), charUuid.getMostSignificantBits(), authReq);
            return;
        }
        Log.e(TAG, "readCharacteristic() - No connection for " + address + "...");
    }

    void writeCharacteristic(int clientIf, String address, int srvcType, int srvcInstanceId, UUID srvcUuid, int charInstanceId, UUID charUuid, int writeType, int authReq, byte[] value) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (isHidUuid(charUuid)) {
            enforcePrivilegedPermission();
        }
        if (this.mReliableQueue.contains(address)) {
            writeType = 3;
        }
        Integer connId = this.mClientMap.connIdByAddress(clientIf, address);
        if (connId != null) {
            gattClientWriteCharacteristicNative(connId.intValue(), srvcType, srvcInstanceId, srvcUuid.getLeastSignificantBits(), srvcUuid.getMostSignificantBits(), charInstanceId, charUuid.getLeastSignificantBits(), charUuid.getMostSignificantBits(), writeType, authReq, value);
            return;
        }
        Log.e(TAG, "writeCharacteristic() - No connection for " + address + "...");
    }

    void readDescriptor(int clientIf, String address, int srvcType, int srvcInstanceId, UUID srvcUuid, int charInstanceId, UUID charUuid, int descrInstanceId, UUID descrUuid, int authReq) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (isHidUuid(charUuid)) {
            enforcePrivilegedPermission();
        }
        Integer connId = this.mClientMap.connIdByAddress(clientIf, address);
        if (connId != null) {
            gattClientReadDescriptorNative(connId.intValue(), srvcType, srvcInstanceId, srvcUuid.getLeastSignificantBits(), srvcUuid.getMostSignificantBits(), charInstanceId, charUuid.getLeastSignificantBits(), charUuid.getMostSignificantBits(), descrInstanceId, descrUuid.getLeastSignificantBits(), descrUuid.getMostSignificantBits(), authReq);
            return;
        }
        Log.e(TAG, "readDescriptor() - No connection for " + address + "...");
    }

    void writeDescriptor(int clientIf, String address, int srvcType, int srvcInstanceId, UUID srvcUuid, int charInstanceId, UUID charUuid, int descrInstanceId, UUID descrUuid, int writeType, int authReq, byte[] value) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (isHidUuid(charUuid)) {
            enforcePrivilegedPermission();
        }
        Integer connId = this.mClientMap.connIdByAddress(clientIf, address);
        if (connId != null) {
            gattClientWriteDescriptorNative(connId.intValue(), srvcType, srvcInstanceId, srvcUuid.getLeastSignificantBits(), srvcUuid.getMostSignificantBits(), charInstanceId, charUuid.getLeastSignificantBits(), charUuid.getMostSignificantBits(), descrInstanceId, descrUuid.getLeastSignificantBits(), descrUuid.getMostSignificantBits(), writeType, authReq, value);
            return;
        }
        Log.e(TAG, "writeDescriptor() - No connection for " + address + "...");
    }

    void beginReliableWrite(int clientIf, String address) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "beginReliableWrite() - address=" + address);
        this.mReliableQueue.add(address);
    }

    void endReliableWrite(int clientIf, String address, boolean execute) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "endReliableWrite() - address=" + address + " execute: " + execute);
        this.mReliableQueue.remove(address);
        Integer connId = this.mClientMap.connIdByAddress(clientIf, address);
        if (connId != null) {
            gattClientExecuteWriteNative(connId.intValue(), execute);
        }
    }

    void registerForNotification(int clientIf, String address, int srvcType, int srvcInstanceId, UUID srvcUuid, int charInstanceId, UUID charUuid, boolean enable) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        if (isHidUuid(charUuid)) {
            enforcePrivilegedPermission();
        }
        Log.d(TAG, "registerForNotification() - address=" + address + " enable: " + enable);
        if (this.mClientMap.connIdByAddress(clientIf, address) != null) {
            gattClientRegisterForNotificationsNative(clientIf, address, srvcType, srvcInstanceId, srvcUuid.getLeastSignificantBits(), srvcUuid.getMostSignificantBits(), charInstanceId, charUuid.getLeastSignificantBits(), charUuid.getMostSignificantBits(), enable);
            return;
        }
        Log.e(TAG, "registerForNotification() - No connection for " + address + "...");
    }

    void readRemoteRssi(int clientIf, String address) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "readRemoteRssi() - address=" + address);
        gattClientReadRemoteRssiNative(clientIf, address);
    }

    void configureMTU(int clientIf, String address, int mtu) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "configureMTU() - address=" + address + " mtu=" + mtu);
        Integer connId = this.mClientMap.connIdByAddress(clientIf, address);
        if (connId != null) {
            gattClientConfigureMTUNative(connId.intValue(), mtu);
        } else {
            Log.e(TAG, "configureMTU() - No connection for " + address + "...");
        }
    }

    void connectionParameterUpdate(int clientIf, String address, int connectionPriority) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int minInterval = 24;
        int maxInterval = 40;
        int latency = 0;
        switch (connectionPriority) {
            case 1:
                minInterval = 6;
                maxInterval = 8;
                break;
            case 2:
                minInterval = 80;
                maxInterval = 100;
                latency = 2;
                break;
        }
        Log.d(TAG, "connectionParameterUpdate() - address=" + address + "params=" + connectionPriority + " interval=" + minInterval + "/" + maxInterval);
        gattConnectionParameterUpdateNative(clientIf, address, minInterval, maxInterval, latency, 2000);
    }

    void onServerRegistered(int status, int serverIf, long uuidLsb, long uuidMsb) throws RemoteException {
        UUID uuid = new UUID(uuidMsb, uuidLsb);
        Log.d(TAG, "onServerRegistered() - UUID=" + uuid + ", serverIf=" + serverIf);
        App app = this.mServerMap.getByUuid(uuid);
        if (app != null) {
            app.id = serverIf;
            app.linkToDeath(new ServerDeathRecipient(serverIf));
            ((IBluetoothGattServerCallback) app.callback).onServerRegistered(status, serverIf);
        }
    }

    void onServiceAdded(int status, int serverIf, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb, int srvcHandle) throws RemoteException {
        UUID uuid = new UUID(srvcUuidMsb, srvcUuidLsb);
        Log.d(TAG, "onServiceAdded() UUID=" + uuid + ", status=" + status + ", handle=" + srvcHandle);
        if (status == 0) {
            this.mHandleMap.addService(serverIf, srvcHandle, uuid, srvcType, srvcInstId, this.mAdvertisingServiceUuids.remove(uuid));
        }
        continueServiceDeclaration(serverIf, status, srvcHandle);
    }

    void onIncludedServiceAdded(int status, int serverIf, int srvcHandle, int includedSrvcHandle) throws RemoteException {
        Log.d(TAG, "onIncludedServiceAdded() status=" + status + ", service=" + srvcHandle + ", included=" + includedSrvcHandle);
        continueServiceDeclaration(serverIf, status, srvcHandle);
    }

    void onCharacteristicAdded(int status, int serverIf, long charUuidLsb, long charUuidMsb, int srvcHandle, int charHandle) throws RemoteException {
        UUID uuid = new UUID(charUuidMsb, charUuidLsb);
        Log.d(TAG, "onCharacteristicAdded() UUID=" + uuid + ", status=" + status + ", srvcHandle=" + srvcHandle + ", charHandle=" + charHandle);
        if (status == 0) {
            this.mHandleMap.addCharacteristic(serverIf, charHandle, uuid, srvcHandle);
        }
        continueServiceDeclaration(serverIf, status, srvcHandle);
    }

    void onDescriptorAdded(int status, int serverIf, long descrUuidLsb, long descrUuidMsb, int srvcHandle, int descrHandle) throws RemoteException {
        UUID uuid = new UUID(descrUuidMsb, descrUuidLsb);
        Log.d(TAG, "onDescriptorAdded() UUID=" + uuid + ", status=" + status + ", srvcHandle=" + srvcHandle + ", descrHandle=" + descrHandle);
        if (status == 0) {
            this.mHandleMap.addDescriptor(serverIf, descrHandle, uuid, srvcHandle);
        }
        continueServiceDeclaration(serverIf, status, srvcHandle);
    }

    void onServiceStarted(int status, int serverIf, int srvcHandle) throws RemoteException {
        Log.d(TAG, "onServiceStarted() srvcHandle=" + srvcHandle + ", status=" + status);
        if (status == 0) {
            this.mHandleMap.setStarted(serverIf, srvcHandle, true);
        }
    }

    void onServiceStopped(int status, int serverIf, int srvcHandle) throws RemoteException {
        Log.d(TAG, "onServiceStopped() srvcHandle=" + srvcHandle + ", status=" + status);
        if (status == 0) {
            this.mHandleMap.setStarted(serverIf, srvcHandle, false);
        }
        stopNextService(serverIf, status);
    }

    void onServiceDeleted(int status, int serverIf, int srvcHandle) {
        Log.d(TAG, "onServiceDeleted() srvcHandle=" + srvcHandle + ", status=" + status);
        this.mHandleMap.deleteService(serverIf, srvcHandle);
    }

    void onClientConnected(String address, boolean connected, int connId, int serverIf) throws RemoteException {
        Log.d(TAG, "onConnected() connId=" + connId + ", address=" + address + ", connected=" + connected);
        App app = this.mServerMap.getById(serverIf);
        if (app != null) {
            if (connected) {
                this.mServerMap.addConnection(serverIf, connId, address);
            } else {
                this.mServerMap.removeConnection(serverIf, connId);
            }
            ((IBluetoothGattServerCallback) app.callback).onServerConnectionState(0, serverIf, connected, address);
        }
    }

    void onAttributeRead(String address, int connId, int transId, int attrHandle, int offset, boolean isLong) throws RemoteException {
        Entry entry = this.mHandleMap.getByHandle(attrHandle);
        if (entry != null) {
            this.mHandleMap.addRequest(transId, attrHandle);
            App app = this.mServerMap.getById(entry.serverIf);
            if (app != null) {
                Entry serviceEntry;
                switch (entry.type) {
                    case 2:
                        serviceEntry = this.mHandleMap.getByHandle(entry.serviceHandle);
                        ((IBluetoothGattServerCallback) app.callback).onCharacteristicReadRequest(address, transId, offset, isLong, serviceEntry.serviceType, serviceEntry.instance, new ParcelUuid(serviceEntry.uuid), entry.instance, new ParcelUuid(entry.uuid));
                        return;
                    case 3:
                        serviceEntry = this.mHandleMap.getByHandle(entry.serviceHandle);
                        Entry charEntry = this.mHandleMap.getByHandle(entry.charHandle);
                        ((IBluetoothGattServerCallback) app.callback).onDescriptorReadRequest(address, transId, offset, isLong, serviceEntry.serviceType, serviceEntry.instance, new ParcelUuid(serviceEntry.uuid), charEntry.instance, new ParcelUuid(charEntry.uuid), new ParcelUuid(entry.uuid));
                        return;
                    default:
                        Log.e(TAG, "onAttributeRead() - Requested unknown attribute type.");
                        return;
                }
            }
        }
    }

    void onAttributeWrite(String address, int connId, int transId, int attrHandle, int offset, int length, boolean needRsp, boolean isPrep, byte[] data) throws RemoteException {
        Entry entry = this.mHandleMap.getByHandle(attrHandle);
        if (entry != null) {
            this.mHandleMap.addRequest(transId, attrHandle);
            App app = this.mServerMap.getById(entry.serverIf);
            if (app != null) {
                Entry serviceEntry;
                switch (entry.type) {
                    case 2:
                        serviceEntry = this.mHandleMap.getByHandle(entry.serviceHandle);
                        ((IBluetoothGattServerCallback) app.callback).onCharacteristicWriteRequest(address, transId, offset, length, isPrep, needRsp, serviceEntry.serviceType, serviceEntry.instance, new ParcelUuid(serviceEntry.uuid), entry.instance, new ParcelUuid(entry.uuid), data);
                        return;
                    case 3:
                        serviceEntry = this.mHandleMap.getByHandle(entry.serviceHandle);
                        Entry charEntry = this.mHandleMap.getByHandle(entry.charHandle);
                        ((IBluetoothGattServerCallback) app.callback).onDescriptorWriteRequest(address, transId, offset, length, isPrep, needRsp, serviceEntry.serviceType, serviceEntry.instance, new ParcelUuid(serviceEntry.uuid), charEntry.instance, new ParcelUuid(charEntry.uuid), new ParcelUuid(entry.uuid), data);
                        return;
                    default:
                        Log.e(TAG, "onAttributeWrite() - Requested unknown attribute type.");
                        return;
                }
            }
        }
    }

    void onExecuteWrite(String address, int connId, int transId, int execWrite) throws RemoteException {
        boolean z = true;
        Log.d(TAG, "onExecuteWrite() connId=" + connId + ", address=" + address + ", transId=" + transId);
        App app = this.mServerMap.getByConnId(connId);
        if (app != null) {
            IBluetoothGattServerCallback iBluetoothGattServerCallback = (IBluetoothGattServerCallback) app.callback;
            if (execWrite != 1) {
                z = false;
            }
            iBluetoothGattServerCallback.onExecuteWrite(address, transId, z);
        }
    }

    void onResponseSendCompleted(int status, int attrHandle) {
        Log.d(TAG, "onResponseSendCompleted() handle=" + attrHandle);
    }

    void onNotificationSent(int connId, int status) throws RemoteException {
        String address = this.mServerMap.addressByConnId(connId);
        if (address != null) {
            App app = this.mServerMap.getByConnId(connId);
            if (app == null) {
                return;
            }
            if (app.isCongested.booleanValue()) {
                if (status == 143) {
                    status = 0;
                }
                app.queueCallback(new CallbackInfo(address, status));
                return;
            }
            ((IBluetoothGattServerCallback) app.callback).onNotificationSent(address, status);
        }
    }

    void onServerCongestion(int connId, boolean congested) throws RemoteException {
        Log.d(TAG, "onServerCongestion() - connId=" + connId + ", congested=" + congested);
        App app = this.mServerMap.getByConnId(connId);
        if (app != null) {
            app.isCongested = Boolean.valueOf(congested);
            while (!app.isCongested.booleanValue()) {
                CallbackInfo callbackInfo = app.popQueuedCallback();
                if (callbackInfo != null) {
                    ((IBluetoothGattServerCallback) app.callback).onNotificationSent(callbackInfo.address, callbackInfo.status);
                } else {
                    return;
                }
            }
        }
    }

    void onMtuChanged(int connId, int mtu) throws RemoteException {
        Log.d(TAG, "onMtuChanged() - connId=" + connId + ", mtu=" + mtu);
        String address = this.mServerMap.addressByConnId(connId);
        if (address != null) {
            App app = this.mServerMap.getByConnId(connId);
            if (app != null) {
                ((IBluetoothGattServerCallback) app.callback).onMtuChanged(address, mtu);
            }
        }
    }

    void registerServer(UUID uuid, IBluetoothGattServerCallback callback) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "registerServer() - UUID=" + uuid);
        this.mServerMap.add(uuid, callback);
        gattServerRegisterAppNative(uuid.getLeastSignificantBits(), uuid.getMostSignificantBits());
    }

    void unregisterServer(int serverIf) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "unregisterServer() - serverIf=" + serverIf);
        deleteServices(serverIf);
        this.mServerMap.remove(serverIf);
        gattServerUnregisterAppNative(serverIf);
    }

    void serverConnect(int serverIf, String address, boolean isDirect, int transport) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "serverConnect() - address=" + address);
        gattServerConnectNative(serverIf, address, isDirect, transport);
    }

    void serverDisconnect(int serverIf, String address) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Integer connId = this.mServerMap.connIdByAddress(serverIf, address);
        Log.d(TAG, "serverDisconnect() - address=" + address + ", connId=" + connId);
        gattServerDisconnectNative(serverIf, address, connId != null ? connId.intValue() : 0);
    }

    void beginServiceDeclaration(int serverIf, int srvcType, int srvcInstanceId, int minHandles, UUID srvcUuid, boolean advertisePreferred) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "beginServiceDeclaration() - uuid=" + srvcUuid);
        addDeclaration().addService(srvcUuid, srvcType, srvcInstanceId, minHandles, advertisePreferred);
    }

    void addIncludedService(int serverIf, int srvcType, int srvcInstanceId, UUID srvcUuid) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "addIncludedService() - uuid=" + srvcUuid);
        getActiveDeclaration().addIncludedService(srvcUuid, srvcType, srvcInstanceId);
    }

    void addCharacteristic(int serverIf, UUID charUuid, int properties, int permissions) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "addCharacteristic() - uuid=" + charUuid);
        getActiveDeclaration().addCharacteristic(charUuid, properties, permissions);
    }

    void addDescriptor(int serverIf, UUID descUuid, int permissions) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "addDescriptor() - uuid=" + descUuid);
        getActiveDeclaration().addDescriptor(descUuid, permissions);
    }

    void endServiceDeclaration(int serverIf) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "endServiceDeclaration()");
        if (getActiveDeclaration() == getPendingDeclaration()) {
            try {
                continueServiceDeclaration(serverIf, 0, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "" + e);
            }
        }
    }

    void removeService(int serverIf, int srvcType, int srvcInstanceId, UUID srvcUuid) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "removeService() - uuid=" + srvcUuid);
        int srvcHandle = this.mHandleMap.getServiceHandle(srvcUuid, srvcType, srvcInstanceId);
        if (srvcHandle != 0) {
            gattServerDeleteServiceNative(serverIf, srvcHandle);
        }
    }

    void clearServices(int serverIf) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        Log.d(TAG, "clearServices()");
        deleteServices(serverIf);
    }

    void sendResponse(int serverIf, String address, int requestId, int status, int offset, byte[] value) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int handle = 0;
        Entry entry = this.mHandleMap.getByRequestId(requestId);
        if (entry != null) {
            handle = entry.handle;
        }
        gattServerSendResponseNative(serverIf, this.mServerMap.connIdByAddress(serverIf, address).intValue(), requestId, (byte) status, handle, offset, value, 0);
        this.mHandleMap.deleteRequest(requestId);
    }

    void sendNotification(int serverIf, String address, int srvcType, int srvcInstanceId, UUID srvcUuid, int charInstanceId, UUID charUuid, boolean confirm, byte[] value) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        int srvcHandle = this.mHandleMap.getServiceHandle(srvcUuid, srvcType, srvcInstanceId);
        if (srvcHandle != 0) {
            int charHandle = this.mHandleMap.getCharacteristicHandle(srvcHandle, charUuid, charInstanceId);
            if (charHandle != 0) {
                int connId = this.mServerMap.connIdByAddress(serverIf, address).intValue();
                if (connId == 0) {
                    return;
                }
                if (confirm) {
                    gattServerSendIndicationNative(serverIf, charHandle, connId, value);
                } else {
                    gattServerSendNotificationNative(serverIf, charHandle, connId, value);
                }
            }
        }
    }

    private boolean isHidUuid(UUID uuid) {
        for (UUID hid_uuid : HID_UUIDS) {
            if (hid_uuid.equals(uuid)) {
                return true;
            }
        }
        return false;
    }

    private int getDeviceType(BluetoothDevice device) {
        int type = gattClientGetDeviceTypeNative(device.getAddress());
        Log.d(TAG, "getDeviceType() - device=" + device + ", type=" + type);
        return type;
    }

    private void enforceAdminPermission() {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_ADMIN_PERM, "Need BLUETOOTH_ADMIN permission");
    }

    private boolean needsPrivilegedPermissionForScan(ScanSettings settings) {
        boolean z = true;
        if (settings == null) {
            return false;
        }
        if (settings.getCallbackType() != 1) {
            return true;
        }
        if (settings.getReportDelayMillis() == 0) {
            return false;
        }
        if (settings.getScanResultType() != 1) {
            z = false;
        }
        return z;
    }

    private void enforcePrivilegedPermission() {
        enforceCallingOrSelfPermission("android.permission.BLUETOOTH_PRIVILEGED", "Need BLUETOOTH_PRIVILEGED permission");
    }

    private void continueSearch(int connId, int status) throws RemoteException {
        if (status != 0 || this.mSearchQueue.isEmpty()) {
            App app = this.mClientMap.getByConnId(connId);
            if (app != null) {
                ((IBluetoothGattCallback) app.callback).onSearchComplete(this.mClientMap.addressByConnId(connId), status);
                return;
            }
            return;
        }
        Entry svc = this.mSearchQueue.pop();
        if (svc.charUuidLsb == 0) {
            gattClientGetCharacteristicNative(svc.connId, svc.srvcType, svc.srvcInstId, svc.srvcUuidLsb, svc.srvcUuidMsb, 0, 0, 0);
        } else {
            gattClientGetDescriptorNative(svc.connId, svc.srvcType, svc.srvcInstId, svc.srvcUuidLsb, svc.srvcUuidMsb, svc.charInstId, svc.charUuidLsb, svc.charUuidMsb, 0, 0, 0);
        }
    }

    private void continueServiceDeclaration(int serverIf, int status, int srvcHandle) throws RemoteException {
        if (this.mServiceDeclarations.size() != 0) {
            Log.d(TAG, "continueServiceDeclaration() - srvcHandle=" + srvcHandle);
            boolean finished = false;
            Entry entry = null;
            if (status == 0) {
                entry = getPendingDeclaration().getNext();
            }
            if (entry != null) {
                Log.d(TAG, "continueServiceDeclaration() - next entry type=" + entry.type);
                switch (entry.type) {
                    case (byte) 1:
                        if (entry.advertisePreferred) {
                            this.mAdvertisingServiceUuids.add(entry.uuid);
                        }
                        gattServerAddServiceNative(serverIf, entry.serviceType, entry.instance, entry.uuid.getLeastSignificantBits(), entry.uuid.getMostSignificantBits(), getPendingDeclaration().getNumHandles());
                        break;
                    case (byte) 2:
                        gattServerAddCharacteristicNative(serverIf, srvcHandle, entry.uuid.getLeastSignificantBits(), entry.uuid.getMostSignificantBits(), entry.properties, entry.permissions);
                        break;
                    case (byte) 3:
                        gattServerAddDescriptorNative(serverIf, srvcHandle, entry.uuid.getLeastSignificantBits(), entry.uuid.getMostSignificantBits(), entry.permissions);
                        break;
                    case (byte) 4:
                        int inclSrvc = this.mHandleMap.getServiceHandle(entry.uuid, entry.serviceType, entry.instance);
                        if (inclSrvc == 0) {
                            finished = true;
                            break;
                        } else {
                            gattServerAddIncludedServiceNative(serverIf, srvcHandle, inclSrvc);
                            break;
                        }
                }
            }
            gattServerStartServiceNative(serverIf, srvcHandle, 3);
            finished = true;
            if (finished) {
                Log.d(TAG, "continueServiceDeclaration() - completed.");
                App app = this.mServerMap.getById(serverIf);
                if (app != null) {
                    Entry serviceEntry = this.mHandleMap.getByHandle(srvcHandle);
                    if (serviceEntry != null) {
                        ((IBluetoothGattServerCallback) app.callback).onServiceAdded(status, serviceEntry.serviceType, serviceEntry.instance, new ParcelUuid(serviceEntry.uuid));
                    } else {
                        ((IBluetoothGattServerCallback) app.callback).onServiceAdded(status, 0, 0, null);
                    }
                }
                removePendingDeclaration();
                if (getPendingDeclaration() != null) {
                    continueServiceDeclaration(serverIf, 0, 0);
                }
            }
        }
    }

    private void stopNextService(int serverIf, int status) throws RemoteException {
        Log.d(TAG, "stopNextService() - serverIf=" + serverIf + ", status=" + status);
        if (status == 0) {
            for (Entry entry : this.mHandleMap.getEntries()) {
                if (entry.type == 1 && entry.serverIf == serverIf && entry.started) {
                    gattServerStopServiceNative(serverIf, entry.handle);
                    return;
                }
            }
        }
    }

    private void deleteServices(int serverIf) {
        Log.d(TAG, "deleteServices() - serverIf=" + serverIf);
        List<Integer> handleList = new ArrayList();
        for (Entry entry : this.mHandleMap.getEntries()) {
            if (entry.type == 1 && entry.serverIf == serverIf) {
                handleList.add(Integer.valueOf(entry.handle));
            }
        }
        for (Integer handle : handleList) {
            gattServerDeleteServiceNative(serverIf, handle.intValue());
        }
    }

    private List<UUID> parseUuids(byte[] adv_data) {
        List<UUID> uuids = new ArrayList();
        int offset = 0;
        while (offset < adv_data.length - 2) {
            int offset2 = offset + 1;
            int len = adv_data[offset];
            if (len != 0) {
                offset = offset2 + 1;
                switch (adv_data[offset2]) {
                    case 2:
                    case 3:
                        offset2 = offset;
                        while (len > 1) {
                            offset = offset2 + 1;
                            int uuid16 = adv_data[offset2];
                            offset2 = offset + 1;
                            uuid16 += adv_data[offset] << 8;
                            len -= 2;
                            uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", new Object[]{Integer.valueOf(uuid16)})));
                        }
                        offset = offset2;
                        break;
                    default:
                        offset += len - 1;
                        break;
                }
            }
            offset = offset2;
            return uuids;
        }
        return uuids;
    }

    public void dump(StringBuilder sb) {
        super.dump(sb);
        ProfileService.println(sb, "mAdvertisingServiceUuids:");
        for (UUID uuid : this.mAdvertisingServiceUuids) {
            ProfileService.println(sb, "  " + uuid);
        }
        ProfileService.println(sb, "mOnFoundResults:");
        for (ScanResult result : this.mOnFoundResults.values()) {
            ProfileService.println(sb, "  " + result);
        }
        ProfileService.println(sb, "mOnFoundResults:");
        for (ServiceDeclaration declaration : this.mServiceDeclarations) {
            ProfileService.println(sb, "  " + declaration);
        }
        ProfileService.println(sb, "mMaxScanFilters: " + this.mMaxScanFilters);
        sb.append("\nGATT Client Map\n");
        this.mClientMap.dump(sb);
        sb.append("\nGATT Server Map\n");
        this.mServerMap.dump(sb);
        sb.append("\nGATT Handle Map\n");
        this.mHandleMap.dump(sb);
    }

    void gattTestCommand(int command, UUID uuid1, String bda1, int p1, int p2, int p3, int p4, int p5) {
        if (bda1 == null) {
            bda1 = "00:00:00:00:00:00";
        }
        if (uuid1 != null) {
            gattTestNative(command, uuid1.getLeastSignificantBits(), uuid1.getMostSignificantBits(), bda1, p1, p2, p3, p4, p5);
            return;
        }
        gattTestNative(command, 0, 0, bda1, p1, p2, p3, p4, p5);
    }
}
