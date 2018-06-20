package com.android.bluetooth.pan;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkUtils;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Slog;

public class BluetoothTetheringNetworkFactory extends NetworkFactory {
    private static final int NETWORK_SCORE = 69;
    private static final String NETWORK_TYPE = "Bluetooth Tethering";
    private static final String TAG = "BluetoothTetheringNetworkFactory";
    private final Context mContext;
    private LinkProperties mLinkProperties = new LinkProperties();
    private NetworkAgent mNetworkAgent;
    private final NetworkCapabilities mNetworkCapabilities = new NetworkCapabilities();
    private final NetworkInfo mNetworkInfo = new NetworkInfo(7, 0, NETWORK_TYPE, "");
    private final PanService mPanService;

    /* renamed from: com.android.bluetooth.pan.BluetoothTetheringNetworkFactory$1 */
    class C00591 implements Runnable {
        C00591() {
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void run() {
            synchronized (BluetoothTetheringNetworkFactory.this) {
                LinkProperties linkProperties = BluetoothTetheringNetworkFactory.this.mLinkProperties;
                if (linkProperties.getInterfaceName() == null) {
                    Slog.e(BluetoothTetheringNetworkFactory.TAG, "attempted to reverse tether without interface name");
                    return;
                }
                BluetoothTetheringNetworkFactory.this.log("dhcpThread(+" + linkProperties.getInterfaceName() + "): mNetworkInfo=" + BluetoothTetheringNetworkFactory.this.mNetworkInfo);
            }
        }
    }

    public BluetoothTetheringNetworkFactory(Context context, Looper looper, PanService panService) {
        super(looper, context, NETWORK_TYPE, new NetworkCapabilities());
        this.mContext = context;
        this.mPanService = panService;
        initNetworkCapabilities();
        setCapabilityFilter(this.mNetworkCapabilities);
    }

    protected void startNetwork() {
        new Thread(new C00591()).start();
    }

    protected void stopNetwork() {
    }

    private synchronized void onCancelRequest() {
        if (!TextUtils.isEmpty(this.mLinkProperties.getInterfaceName())) {
            NetworkUtils.stopDhcp(this.mLinkProperties.getInterfaceName());
        }
        this.mLinkProperties.clear();
        this.mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);
        if (this.mNetworkAgent != null) {
            this.mNetworkAgent.sendNetworkInfo(this.mNetworkInfo);
            this.mNetworkAgent = null;
        }
        for (BluetoothDevice device : this.mPanService.getConnectedDevices()) {
            this.mPanService.disconnect(device);
        }
    }

    public void startReverseTether(String iface) {
        if (iface == null || TextUtils.isEmpty(iface)) {
            Slog.e(TAG, "attempted to reverse tether with empty interface");
            return;
        }
        synchronized (this) {
            if (this.mLinkProperties.getInterfaceName() != null) {
                Slog.e(TAG, "attempted to reverse tether while already in process");
                return;
            }
            this.mLinkProperties = new LinkProperties();
            this.mLinkProperties.setInterfaceName(iface);
            register();
            setScoreFilter(NETWORK_SCORE);
        }
    }

    public synchronized void stopReverseTether() {
        if (TextUtils.isEmpty(this.mLinkProperties.getInterfaceName())) {
            Slog.e(TAG, "attempted to stop reverse tether with nothing tethered");
        } else {
            onCancelRequest();
            setScoreFilter(-1);
            unregister();
        }
    }

    private void initNetworkCapabilities() {
        this.mNetworkCapabilities.addTransportType(2);
        this.mNetworkCapabilities.addCapability(12);
        this.mNetworkCapabilities.addCapability(13);
        this.mNetworkCapabilities.setLinkUpstreamBandwidthKbps(24000);
        this.mNetworkCapabilities.setLinkDownstreamBandwidthKbps(24000);
    }
}
