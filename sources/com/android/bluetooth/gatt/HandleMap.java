package com.android.bluetooth.gatt;

import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class HandleMap {
    private static final boolean DBG = true;
    private static final String TAG = "BtGatt.HandleMap";
    public static final int TYPE_CHARACTERISTIC = 2;
    public static final int TYPE_DESCRIPTOR = 3;
    public static final int TYPE_SERVICE = 1;
    public static final int TYPE_UNDEFINED = 0;
    List<Entry> mEntries;
    int mLastCharacteristic;
    Map<Integer, Integer> mRequestMap;

    class Entry {
        boolean advertisePreferred = false;
        int charHandle = 0;
        int handle = 0;
        int instance = 0;
        int serverIf = 0;
        int serviceHandle = 0;
        int serviceType = 0;
        boolean started = false;
        int type = 0;
        UUID uuid = null;

        Entry(int serverIf, int handle, UUID uuid, int serviceType, int instance) {
            this.serverIf = serverIf;
            this.type = 1;
            this.handle = handle;
            this.uuid = uuid;
            this.instance = instance;
            this.serviceType = serviceType;
        }

        Entry(int serverIf, int handle, UUID uuid, int serviceType, int instance, boolean advertisePreferred) {
            this.serverIf = serverIf;
            this.type = 1;
            this.handle = handle;
            this.uuid = uuid;
            this.instance = instance;
            this.serviceType = serviceType;
            this.advertisePreferred = advertisePreferred;
        }

        Entry(int serverIf, int type, int handle, UUID uuid, int serviceHandle) {
            this.serverIf = serverIf;
            this.type = type;
            this.handle = handle;
            this.uuid = uuid;
            this.instance = this.instance;
            this.serviceHandle = serviceHandle;
        }

        Entry(int serverIf, int type, int handle, UUID uuid, int serviceHandle, int charHandle) {
            this.serverIf = serverIf;
            this.type = type;
            this.handle = handle;
            this.uuid = uuid;
            this.instance = this.instance;
            this.serviceHandle = serviceHandle;
            this.charHandle = charHandle;
        }
    }

    HandleMap() {
        this.mEntries = null;
        this.mRequestMap = null;
        this.mLastCharacteristic = 0;
        this.mEntries = new ArrayList();
        this.mRequestMap = new HashMap();
    }

    void clear() {
        this.mEntries.clear();
        this.mRequestMap.clear();
    }

    void addService(int serverIf, int handle, UUID uuid, int serviceType, int instance, boolean advertisePreferred) {
        this.mEntries.add(new Entry(serverIf, handle, uuid, serviceType, instance, advertisePreferred));
    }

    void addCharacteristic(int serverIf, int handle, UUID uuid, int serviceHandle) {
        this.mLastCharacteristic = handle;
        this.mEntries.add(new Entry(serverIf, 2, handle, uuid, serviceHandle));
    }

    void addDescriptor(int serverIf, int handle, UUID uuid, int serviceHandle) {
        this.mEntries.add(new Entry(serverIf, 3, handle, uuid, serviceHandle, this.mLastCharacteristic));
    }

    void setStarted(int serverIf, int handle, boolean started) {
        for (Entry entry : this.mEntries) {
            if (entry.type == 1 && entry.serverIf == serverIf && entry.handle == handle) {
                entry.started = started;
                return;
            }
        }
    }

    Entry getByHandle(int handle) {
        for (Entry entry : this.mEntries) {
            if (entry.handle == handle) {
                return entry;
            }
        }
        Log.e(TAG, "getByHandle() - Handle " + handle + " not found!");
        return null;
    }

    int getServiceHandle(UUID uuid, int serviceType, int instance) {
        for (Entry entry : this.mEntries) {
            if (entry.type == 1 && entry.serviceType == serviceType && entry.instance == instance && entry.uuid.equals(uuid)) {
                return entry.handle;
            }
        }
        Log.e(TAG, "getServiceHandle() - UUID " + uuid + " not found!");
        return 0;
    }

    int getCharacteristicHandle(int serviceHandle, UUID uuid, int instance) {
        for (Entry entry : this.mEntries) {
            if (entry.type == 2 && entry.serviceHandle == serviceHandle && entry.instance == instance && entry.uuid.equals(uuid)) {
                return entry.handle;
            }
        }
        Log.e(TAG, "getCharacteristicHandle() - Service " + serviceHandle + ", UUID " + uuid + " not found!");
        return 0;
    }

    void deleteService(int serverIf, int serviceHandle) {
        Iterator<Entry> it = this.mEntries.iterator();
        while (it.hasNext()) {
            Entry entry = (Entry) it.next();
            if (entry.serverIf == serverIf && (entry.handle == serviceHandle || entry.serviceHandle == serviceHandle)) {
                it.remove();
            }
        }
    }

    List<Entry> getEntries() {
        return this.mEntries;
    }

    void addRequest(int requestId, int handle) {
        this.mRequestMap.put(Integer.valueOf(requestId), Integer.valueOf(handle));
    }

    void deleteRequest(int requestId) {
        this.mRequestMap.remove(Integer.valueOf(requestId));
    }

    Entry getByRequestId(int requestId) {
        Integer handle = (Integer) this.mRequestMap.get(Integer.valueOf(requestId));
        if (handle != null) {
            return getByHandle(handle.intValue());
        }
        Log.e(TAG, "getByRequestId() - Request ID " + requestId + " not found!");
        return null;
    }

    void dump(StringBuilder sb) {
        sb.append("  Entries: " + this.mEntries.size() + "\n");
        sb.append("  Requests: " + this.mRequestMap.size() + "\n");
        for (Entry entry : this.mEntries) {
            sb.append("  " + entry.serverIf + ": [" + entry.handle + "] ");
            switch (entry.type) {
                case 1:
                    sb.append("Service " + entry.uuid);
                    sb.append(", started " + entry.started);
                    break;
                case 2:
                    sb.append("  Characteristic " + entry.uuid);
                    break;
                case 3:
                    sb.append("    Descriptor " + entry.uuid);
                    break;
                default:
                    break;
            }
            sb.append("\n");
        }
    }
}
