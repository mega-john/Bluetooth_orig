package com.android.bluetooth.gatt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class ServiceDeclaration {
    private static final boolean DBG = true;
    private static final String TAG = "BtGatt.ServiceDeclaration";
    public static final byte TYPE_CHARACTERISTIC = (byte) 2;
    public static final byte TYPE_DESCRIPTOR = (byte) 3;
    public static final byte TYPE_INCLUDED_SERVICE = (byte) 4;
    public static final byte TYPE_SERVICE = (byte) 1;
    public static final byte TYPE_UNDEFINED = (byte) 0;
    List<Entry> mEntries;
    int mNumHandles;

    class Entry {
        boolean advertisePreferred;
        int instance;
        int permissions;
        int properties;
        int serviceHandle;
        int serviceType;
        byte type;
        UUID uuid;

        Entry(UUID uuid, int serviceType, int instance) {
            this.type = (byte) 0;
            this.uuid = null;
            this.instance = 0;
            this.permissions = 0;
            this.properties = 0;
            this.serviceType = 0;
            this.serviceHandle = 0;
            this.advertisePreferred = false;
            this.type = (byte) 1;
            this.uuid = uuid;
            this.instance = instance;
            this.serviceType = serviceType;
        }

        Entry(UUID uuid, int serviceType, int instance, boolean advertisePreferred) {
            this.type = (byte) 0;
            this.uuid = null;
            this.instance = 0;
            this.permissions = 0;
            this.properties = 0;
            this.serviceType = 0;
            this.serviceHandle = 0;
            this.advertisePreferred = false;
            this.type = (byte) 1;
            this.uuid = uuid;
            this.instance = instance;
            this.serviceType = serviceType;
            this.advertisePreferred = advertisePreferred;
        }

        Entry(UUID uuid, int properties, int permissions, int instance) {
            this.type = (byte) 0;
            this.uuid = null;
            this.instance = 0;
            this.permissions = 0;
            this.properties = 0;
            this.serviceType = 0;
            this.serviceHandle = 0;
            this.advertisePreferred = false;
            this.type = (byte) 2;
            this.uuid = uuid;
            this.instance = instance;
            this.permissions = permissions;
            this.properties = properties;
        }

        Entry(UUID uuid, int permissions) {
            this.type = (byte) 0;
            this.uuid = null;
            this.instance = 0;
            this.permissions = 0;
            this.properties = 0;
            this.serviceType = 0;
            this.serviceHandle = 0;
            this.advertisePreferred = false;
            this.type = (byte) 3;
            this.uuid = uuid;
            this.permissions = permissions;
        }
    }

    ServiceDeclaration() {
        this.mEntries = null;
        this.mNumHandles = 0;
        this.mEntries = new ArrayList();
    }

    void addService(UUID uuid, int serviceType, int instance, int minHandles, boolean advertisePreferred) {
        this.mEntries.add(new Entry(uuid, serviceType, instance, advertisePreferred));
        if (minHandles == 0) {
            this.mNumHandles++;
        } else {
            this.mNumHandles = minHandles;
        }
    }

    void addIncludedService(UUID uuid, int serviceType, int instance) {
        Entry entry = new Entry(uuid, serviceType, instance);
        entry.type = (byte) 4;
        this.mEntries.add(entry);
        this.mNumHandles++;
    }

    void addCharacteristic(UUID uuid, int properties, int permissions) {
        this.mEntries.add(new Entry(uuid, properties, permissions, 0));
        this.mNumHandles += 2;
    }

    void addDescriptor(UUID uuid, int permissions) {
        this.mEntries.add(new Entry(uuid, permissions));
        this.mNumHandles++;
    }

    Entry getNext() {
        if (this.mEntries.isEmpty()) {
            return null;
        }
        Entry entry = (Entry) this.mEntries.get(0);
        this.mEntries.remove(0);
        return entry;
    }

    boolean isServiceAdvertisePreferred(UUID uuid) {
        for (Entry entry : this.mEntries) {
            if (entry.uuid.equals(uuid)) {
                return entry.advertisePreferred;
            }
        }
        return false;
    }

    int getNumHandles() {
        return this.mNumHandles;
    }
}
