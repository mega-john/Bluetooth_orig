package com.android.bluetooth.gatt;

import android.bluetooth.le.ResultStorageDescriptor;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.ScanSettings.Builder;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

class ScanClient {
    private static final ScanSettings DEFAULT_SCAN_SETTINGS = new Builder().setScanMode(2).build();
    boolean appDied;
    int clientIf;
    List<ScanFilter> filters;
    boolean isServer;
    ScanSettings settings;
    List<List<ResultStorageDescriptor>> storages;
    UUID[] uuids;

    ScanClient(int appIf, boolean isServer) {
        this(appIf, isServer, new UUID[0], DEFAULT_SCAN_SETTINGS, null, null);
    }

    ScanClient(int appIf, boolean isServer, UUID[] uuids) {
        this(appIf, isServer, uuids, DEFAULT_SCAN_SETTINGS, null, null);
    }

    ScanClient(int appIf, boolean isServer, ScanSettings settings, List<ScanFilter> filters) {
        this(appIf, isServer, new UUID[0], settings, filters, null);
    }

    ScanClient(int appIf, boolean isServer, ScanSettings settings, List<ScanFilter> filters, List<List<ResultStorageDescriptor>> storages) {
        this(appIf, isServer, new UUID[0], settings, filters, storages);
    }

    private ScanClient(int appIf, boolean isServer, UUID[] uuids, ScanSettings settings, List<ScanFilter> filters, List<List<ResultStorageDescriptor>> storages) {
        this.clientIf = appIf;
        this.isServer = isServer;
        this.uuids = uuids;
        this.settings = settings;
        this.filters = filters;
        this.storages = storages;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        if (this.clientIf != ((ScanClient) obj).clientIf) {
            return false;
        }
        return true;
    }

    public int hashCode() {
        return Objects.hash(new Object[]{Integer.valueOf(this.clientIf)});
    }
}
