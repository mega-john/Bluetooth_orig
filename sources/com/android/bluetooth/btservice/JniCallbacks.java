package com.android.bluetooth.btservice;

final class JniCallbacks {
    private AdapterProperties mAdapterProperties;
    private AdapterState mAdapterStateMachine;
    private BondStateMachine mBondStateMachine;
    private RemoteDevices mRemoteDevices;

    JniCallbacks(AdapterState adapterStateMachine, AdapterProperties adapterProperties) {
        this.mAdapterStateMachine = adapterStateMachine;
        this.mAdapterProperties = adapterProperties;
    }

    void init(BondStateMachine bondStateMachine, RemoteDevices remoteDevices) {
        this.mRemoteDevices = remoteDevices;
        this.mBondStateMachine = bondStateMachine;
    }

    void cleanup() {
        this.mRemoteDevices = null;
        this.mAdapterProperties = null;
        this.mAdapterStateMachine = null;
        this.mBondStateMachine = null;
    }

    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    void sspRequestCallback(byte[] address, byte[] name, int cod, int pairingVariant, int passkey) {
        this.mBondStateMachine.sspRequestCallback(address, name, cod, pairingVariant, passkey);
    }

    void devicePropertyChangedCallback(byte[] address, int[] types, byte[][] val) {
        this.mRemoteDevices.devicePropertyChangedCallback(address, types, val);
    }

    void deviceFoundCallback(byte[] address) {
        this.mRemoteDevices.deviceFoundCallback(address);
    }

    void pinRequestCallback(byte[] address, byte[] name, int cod) {
        this.mBondStateMachine.pinRequestCallback(address, name, cod);
    }

    void bondStateChangeCallback(int status, byte[] address, int newState) {
        this.mBondStateMachine.bondStateChangeCallback(status, address, newState);
    }

    void aclStateChangeCallback(int status, byte[] address, int newState) {
        this.mRemoteDevices.aclStateChangeCallback(status, address, newState);
    }

    void stateChangeCallback(int status) {
        this.mAdapterStateMachine.stateChangeCallback(status);
    }

    void discoveryStateChangeCallback(int state) {
        this.mAdapterProperties.discoveryStateChangeCallback(state);
    }

    void adapterPropertyChangedCallback(int[] types, byte[][] val) {
        this.mAdapterProperties.adapterPropertyChangedCallback(types, val);
    }

    void deviceMasInstancesFoundCallback(int status, byte[] address, String[] name, int[] scn, int[] id, int[] msgtype) {
        this.mRemoteDevices.deviceMasInstancesFoundCallback(status, address, name, scn, id, msgtype);
    }
}
