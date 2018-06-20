package com.android.bluetooth.gatt;

import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import java.util.Objects;

class AdvertiseClient {
    AdvertiseData advertiseData;
    boolean appDied;
    int clientIf;
    AdvertiseData scanResponse;
    AdvertiseSettings settings;

    AdvertiseClient(int clientIf) {
        this.clientIf = clientIf;
    }

    AdvertiseClient(int clientIf, AdvertiseSettings settings, AdvertiseData advertiseData, AdvertiseData scanResponse) {
        this.clientIf = clientIf;
        this.settings = settings;
        this.advertiseData = advertiseData;
        this.scanResponse = scanResponse;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        if (this.clientIf != ((AdvertiseClient) obj).clientIf) {
            return false;
        }
        return true;
    }

    public int hashCode() {
        return Objects.hash(new Object[]{Integer.valueOf(this.clientIf)});
    }
}
