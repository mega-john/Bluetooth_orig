package com.android.bluetooth.gatt;

import android.bluetooth.BluetoothUuid;
import android.bluetooth.le.ScanFilter;
import android.os.ParcelUuid;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

class ScanFilterQueue {
    private static final byte DEVICE_TYPE_ALL = (byte) 0;
    private static final int MAX_LEN_PER_FIELD = 26;
    public static final int TYPE_DEVICE_ADDRESS = 0;
    public static final int TYPE_LOCAL_NAME = 4;
    public static final int TYPE_MANUFACTURER_DATA = 5;
    public static final int TYPE_SERVICE_DATA = 6;
    public static final int TYPE_SERVICE_DATA_CHANGED = 1;
    public static final int TYPE_SERVICE_UUID = 2;
    public static final int TYPE_SOLICIT_UUID = 3;
    private Set<Entry> mEntries = new HashSet();

    class Entry {
        public byte addr_type;
        public String address;
        public int company;
        public int company_mask;
        public byte[] data;
        public byte[] data_mask;
        public String name;
        public byte type;
        public UUID uuid;
        public UUID uuid_mask;

        Entry() {
        }

        public int hashCode() {
            return Objects.hash(new Object[]{this.address, Byte.valueOf(this.addr_type), Byte.valueOf(this.type), this.uuid, this.uuid_mask, this.name, Integer.valueOf(this.company), Integer.valueOf(this.company_mask), this.data, this.data_mask});
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            Entry other = (Entry) obj;
            if (Objects.equals(this.address, other.address) && this.addr_type == other.addr_type && this.type == other.type && Objects.equals(this.uuid, other.uuid) && Objects.equals(this.uuid_mask, other.uuid_mask) && Objects.equals(this.name, other.name) && this.company == other.company && this.company_mask == other.company_mask && Objects.deepEquals(this.data, other.data) && Objects.deepEquals(this.data_mask, other.data_mask)) {
                return true;
            }
            return false;
        }
    }

    ScanFilterQueue() {
    }

    void addDeviceAddress(String address, byte type) {
        Entry entry = new Entry();
        entry.type = DEVICE_TYPE_ALL;
        entry.address = address;
        entry.addr_type = type;
        this.mEntries.add(entry);
    }

    void addServiceChanged() {
        Entry entry = new Entry();
        entry.type = (byte) 1;
        this.mEntries.add(entry);
    }

    void addUuid(UUID uuid) {
        Entry entry = new Entry();
        entry.type = (byte) 2;
        entry.uuid = uuid;
        entry.uuid_mask = new UUID(0, 0);
        this.mEntries.add(entry);
    }

    void addUuid(UUID uuid, UUID uuid_mask) {
        Entry entry = new Entry();
        entry.type = (byte) 2;
        entry.uuid = uuid;
        entry.uuid_mask = uuid_mask;
        this.mEntries.add(entry);
    }

    void addSolicitUuid(UUID uuid) {
        Entry entry = new Entry();
        entry.type = (byte) 3;
        entry.uuid = uuid;
        this.mEntries.add(entry);
    }

    void addName(String name) {
        Entry entry = new Entry();
        entry.type = (byte) 4;
        entry.name = name;
        this.mEntries.add(entry);
    }

    void addManufacturerData(int company, byte[] data) {
        Entry entry = new Entry();
        entry.type = (byte) 5;
        entry.company = company;
        entry.company_mask = 65535;
        entry.data = data;
        entry.data_mask = new byte[data.length];
        Arrays.fill(entry.data_mask, (byte) -1);
        this.mEntries.add(entry);
    }

    void addManufacturerData(int company, int company_mask, byte[] data, byte[] data_mask) {
        Entry entry = new Entry();
        entry.type = (byte) 5;
        entry.company = company;
        entry.company_mask = company_mask;
        entry.data = data;
        entry.data_mask = data_mask;
        this.mEntries.add(entry);
    }

    void addServiceData(byte[] data, byte[] dataMask) {
        Entry entry = new Entry();
        entry.type = (byte) 6;
        entry.data = data;
        entry.data_mask = dataMask;
        this.mEntries.add(entry);
    }

    Entry pop() {
        if (isEmpty()) {
            return null;
        }
        Iterator<Entry> iterator = this.mEntries.iterator();
        Entry entry = (Entry) iterator.next();
        iterator.remove();
        return entry;
    }

    boolean isEmpty() {
        return this.mEntries.isEmpty();
    }

    void clearUuids() {
        Iterator<Entry> it = this.mEntries.iterator();
        while (it.hasNext()) {
            if (((Entry) it.next()).type == (byte) 2) {
                it.remove();
            }
        }
    }

    void clear() {
        this.mEntries.clear();
    }

    int getFeatureSelection() {
        int selc = 0;
        for (Entry entry : this.mEntries) {
            selc |= 1 << entry.type;
        }
        return selc;
    }

    void addScanFilter(ScanFilter filter) {
        if (filter != null) {
            if (filter.getDeviceName() != null) {
                addName(filter.getDeviceName());
            }
            if (filter.getDeviceAddress() != null) {
                addDeviceAddress(filter.getDeviceAddress(), DEVICE_TYPE_ALL);
            }
            if (filter.getServiceUuid() != null) {
                if (filter.getServiceUuidMask() == null) {
                    addUuid(filter.getServiceUuid().getUuid());
                } else {
                    addUuid(filter.getServiceUuid().getUuid(), filter.getServiceUuidMask().getUuid());
                }
            }
            if (filter.getManufacturerData() != null) {
                if (filter.getManufacturerDataMask() == null) {
                    addManufacturerData(filter.getManufacturerId(), filter.getManufacturerData());
                } else {
                    addManufacturerData(filter.getManufacturerId(), 65535, filter.getManufacturerData(), filter.getManufacturerDataMask());
                }
            }
            if (filter.getServiceDataUuid() != null && filter.getServiceData() != null) {
                ParcelUuid serviceDataUuid = filter.getServiceDataUuid();
                byte[] serviceData = filter.getServiceData();
                byte[] serviceDataMask = filter.getServiceDataMask();
                if (serviceDataMask == null) {
                    serviceDataMask = new byte[serviceData.length];
                    Arrays.fill(serviceDataMask, (byte) -1);
                }
                serviceData = concate(serviceDataUuid, serviceData);
                serviceDataMask = concate(serviceDataUuid, serviceDataMask);
                if (serviceData != null && serviceDataMask != null) {
                    addServiceData(serviceData, serviceDataMask);
                }
            }
        }
    }

    private byte[] concate(ParcelUuid serviceDataUuid, byte[] serviceData) {
        int dataLen = (serviceData == null ? 0 : serviceData.length) + 2;
        if (dataLen > 26) {
            return null;
        }
        byte[] concated = new byte[dataLen];
        int uuidValue = BluetoothUuid.getServiceIdentifierFromParcelUuid(serviceDataUuid);
        concated[0] = (byte) (uuidValue & 255);
        concated[1] = (byte) ((uuidValue >> 8) & 255);
        if (serviceData == null) {
            return concated;
        }
        System.arraycopy(serviceData, 0, concated, 2, serviceData.length);
        return concated;
    }
}
