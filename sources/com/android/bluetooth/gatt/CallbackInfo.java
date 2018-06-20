package com.android.bluetooth.gatt;

import java.util.UUID;

class CallbackInfo {
    String address;
    int charInstId;
    UUID charUuid;
    int srvcInstId;
    int srvcType;
    UUID srvcUuid;
    int status;

    CallbackInfo(String address, int status, int srvcType, int srvcInstId, UUID srvcUuid, int charInstId, UUID charUuid) {
        this.address = address;
        this.status = status;
        this.srvcType = srvcType;
        this.srvcInstId = srvcInstId;
        this.srvcUuid = srvcUuid;
        this.charInstId = charInstId;
        this.charUuid = charUuid;
    }

    CallbackInfo(String address, int status) {
        this.address = address;
        this.status = status;
    }
}
