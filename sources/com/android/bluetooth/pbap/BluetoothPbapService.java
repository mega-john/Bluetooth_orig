package com.android.bluetooth.pbap;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothPbap.Stub;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import com.android.bluetooth.C0000R;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.vcard.VCardConfig;
import java.io.IOException;
import javax.obex.ServerSession;

public class BluetoothPbapService extends Service {
    private static final String ACCESS_AUTHORITY_CLASS = "com.android.settings.bluetooth.BluetoothPermissionRequest";
    private static final String ACCESS_AUTHORITY_PACKAGE = "com.android.settings";
    public static final String AUTH_CANCELLED_ACTION = "com.android.bluetooth.pbap.authcancelled";
    public static final String AUTH_CHALL_ACTION = "com.android.bluetooth.pbap.authchall";
    public static final String AUTH_RESPONSE_ACTION = "com.android.bluetooth.pbap.authresponse";
    private static final int AUTH_TIMEOUT = 3;
    private static final String BLUETOOTH_ADMIN_PERM = "android.permission.BLUETOOTH_ADMIN";
    private static final String BLUETOOTH_PERM = "android.permission.BLUETOOTH";
    public static final boolean DEBUG = true;
    public static final String EXTRA_SESSION_KEY = "com.android.bluetooth.pbap.sessionkey";
    public static final int MSG_ACQUIRE_WAKE_LOCK = 5004;
    public static final int MSG_OBEX_AUTH_CHALL = 5003;
    public static final int MSG_RELEASE_WAKE_LOCK = 5005;
    public static final int MSG_SERVERSESSION_CLOSE = 5000;
    public static final int MSG_SESSION_DISCONNECTED = 5002;
    public static final int MSG_SESSION_ESTABLISHED = 5001;
    private static final int NOTIFICATION_ID_ACCESS = -1000001;
    private static final int NOTIFICATION_ID_AUTH = -1000002;
    private static final int RELEASE_WAKE_LOCK_DELAY = 10000;
    private static final int START_LISTENER = 1;
    private static final String TAG = "BluetoothPbapService";
    public static final String THIS_PACKAGE_NAME = "com.android.bluetooth";
    public static final String USER_CONFIRM_TIMEOUT_ACTION = "com.android.bluetooth.pbap.userconfirmtimeout";
    private static final int USER_CONFIRM_TIMEOUT_VALUE = 30000;
    private static final int USER_TIMEOUT = 2;
    public static final boolean VERBOSE = false;
    private static String sLocalPhoneName = null;
    private static String sLocalPhoneNum = null;
    private static String sRemoteDeviceName = null;
    private SocketAcceptThread mAcceptThread = null;
    private BluetoothAdapter mAdapter;
    private BluetoothPbapAuthenticator mAuth = null;
    private final Stub mBinder = new C00642();
    private BluetoothSocket mConnSocket = null;
    private boolean mHasStarted = false;
    private volatile boolean mInterrupted;
    private boolean mIsWaitingAuthorization = false;
    private BluetoothPbapObexServer mPbapServer;
    private BluetoothDevice mRemoteDevice = null;
    private ServerSession mServerSession = null;
    private BluetoothServerSocket mServerSocket = null;
    private final Handler mSessionStatusHandler = new C00631();
    private int mStartId = -1;
    private int mState = 0;
    private WakeLock mWakeLock = null;

    /* renamed from: com.android.bluetooth.pbap.BluetoothPbapService$1 */
    class C00631 extends Handler {
        C00631() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    if (BluetoothPbapService.this.mAdapter.isEnabled()) {
                        BluetoothPbapService.this.startRfcommSocketListener();
                        return;
                    } else {
                        BluetoothPbapService.this.closeService();
                        return;
                    }
                case 2:
                    Intent intent = new Intent("android.bluetooth.device.action.CONNECTION_ACCESS_CANCEL");
                    intent.putExtra("android.bluetooth.device.extra.DEVICE", BluetoothPbapService.this.mRemoteDevice);
                    intent.putExtra("android.bluetooth.device.extra.ACCESS_REQUEST_TYPE", 2);
                    BluetoothPbapService.this.sendBroadcast(intent);
                    BluetoothPbapService.this.mIsWaitingAuthorization = false;
                    BluetoothPbapService.this.stopObexServerSession();
                    return;
                case 3:
                    BluetoothPbapService.this.sendBroadcast(new Intent(BluetoothPbapService.USER_CONFIRM_TIMEOUT_ACTION));
                    BluetoothPbapService.this.removePbapNotification(BluetoothPbapService.NOTIFICATION_ID_AUTH);
                    BluetoothPbapService.this.notifyAuthCancelled();
                    return;
                case 5000:
                    BluetoothPbapService.this.stopObexServerSession();
                    return;
                case 5003:
                    BluetoothPbapService.this.createPbapNotification(BluetoothPbapService.AUTH_CHALL_ACTION);
                    BluetoothPbapService.this.mSessionStatusHandler.sendMessageDelayed(BluetoothPbapService.this.mSessionStatusHandler.obtainMessage(3), 30000);
                    return;
                case 5004:
                    if (BluetoothPbapService.this.mWakeLock == null) {
                        BluetoothPbapService.this.mWakeLock = ((PowerManager) BluetoothPbapService.this.getSystemService("power")).newWakeLock(1, "StartingObexPbapTransaction");
                        BluetoothPbapService.this.mWakeLock.setReferenceCounted(false);
                        BluetoothPbapService.this.mWakeLock.acquire();
                        Log.w(BluetoothPbapService.TAG, "Acquire Wake Lock");
                    }
                    BluetoothPbapService.this.mSessionStatusHandler.removeMessages(5005);
                    BluetoothPbapService.this.mSessionStatusHandler.sendMessageDelayed(BluetoothPbapService.this.mSessionStatusHandler.obtainMessage(5005), 10000);
                    return;
                case 5005:
                    if (BluetoothPbapService.this.mWakeLock != null) {
                        BluetoothPbapService.this.mWakeLock.release();
                        BluetoothPbapService.this.mWakeLock = null;
                        Log.w(BluetoothPbapService.TAG, "Release Wake Lock");
                        return;
                    }
                    return;
                default:
                    return;
            }
        }
    }

    /* renamed from: com.android.bluetooth.pbap.BluetoothPbapService$2 */
    class C00642 extends Stub {
        C00642() {
        }

        public int getState() {
            Log.d(BluetoothPbapService.TAG, "getState " + BluetoothPbapService.this.mState);
            if (Utils.checkCaller()) {
                BluetoothPbapService.this.enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
                return BluetoothPbapService.this.mState;
            }
            Log.w(BluetoothPbapService.TAG, "getState(): not allowed for non-active user");
            return 0;
        }

        public BluetoothDevice getClient() {
            Log.d(BluetoothPbapService.TAG, "getClient" + BluetoothPbapService.this.mRemoteDevice);
            if (Utils.checkCaller()) {
                BluetoothPbapService.this.enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
                if (BluetoothPbapService.this.mState != 0) {
                    return BluetoothPbapService.this.mRemoteDevice;
                }
                return null;
            }
            Log.w(BluetoothPbapService.TAG, "getClient(): not allowed for non-active user");
            return null;
        }

        public boolean isConnected(BluetoothDevice device) {
            if (Utils.checkCaller()) {
                BluetoothPbapService.this.enforceCallingOrSelfPermission("android.permission.BLUETOOTH", "Need BLUETOOTH permission");
                if (BluetoothPbapService.this.mState == 2 && BluetoothPbapService.this.mRemoteDevice.equals(device)) {
                    return true;
                }
                return false;
            }
            Log.w(BluetoothPbapService.TAG, "isConnected(): not allowed for non-active user");
            return false;
        }

        public boolean connect(BluetoothDevice device) {
            if (Utils.checkCaller()) {
                BluetoothPbapService.this.enforceCallingOrSelfPermission("android.permission.BLUETOOTH_ADMIN", "Need BLUETOOTH_ADMIN permission");
            } else {
                Log.w(BluetoothPbapService.TAG, "connect(): not allowed for non-active user");
            }
            return false;
        }

        public void disconnect() {
            Log.d(BluetoothPbapService.TAG, "disconnect");
            if (Utils.checkCaller()) {
                BluetoothPbapService.this.enforceCallingOrSelfPermission("android.permission.BLUETOOTH_ADMIN", "Need BLUETOOTH_ADMIN permission");
                synchronized (BluetoothPbapService.this) {
                    switch (BluetoothPbapService.this.mState) {
                        case 2:
                            if (BluetoothPbapService.this.mServerSession != null) {
                                BluetoothPbapService.this.mServerSession.close();
                                BluetoothPbapService.this.mServerSession = null;
                            }
                            BluetoothPbapService.this.closeConnectionSocket();
                            BluetoothPbapService.this.setState(0, 2);
                            break;
                    }
                }
                return;
            }
            Log.w(BluetoothPbapService.TAG, "disconnect(): not allowed for non-active user");
        }
    }

    private class SocketAcceptThread extends Thread {
        private boolean stopped;

        private SocketAcceptThread() {
            this.stopped = false;
        }

        /* JADX WARNING: inconsistent code. */
        /* Code decompiled incorrectly, please refer to instructions dump. */
        public void run() {
            if (BluetoothPbapService.this.mServerSocket != null || BluetoothPbapService.this.initSocket()) {
                while (!this.stopped) {
                    BluetoothServerSocket serverSocket = BluetoothPbapService.this.mServerSocket;
                    if (serverSocket == null) {
                        Log.w(BluetoothPbapService.TAG, "mServerSocket is null");
                        return;
                    }
                    BluetoothPbapService.this.mConnSocket = serverSocket.accept();
                    synchronized (BluetoothPbapService.this) {
                        if (BluetoothPbapService.this.mConnSocket == null) {
                            Log.w(BluetoothPbapService.TAG, "mConnSocket is null");
                            return;
                        }
                        BluetoothPbapService.this.mRemoteDevice = BluetoothPbapService.this.mConnSocket.getRemoteDevice();
                    }
                }
                return;
            }
            return;
            this.stopped = true;
        }

        void shutdown() {
            this.stopped = true;
            interrupt();
        }
    }

    public void onCreate() {
        super.onCreate();
        this.mInterrupted = false;
        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!this.mHasStarted) {
            this.mHasStarted = true;
            BluetoothPbapConfig.init(this);
            if (this.mAdapter.getState() == 12) {
                this.mSessionStatusHandler.sendMessage(this.mSessionStatusHandler.obtainMessage(1));
            }
        }
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        this.mStartId = startId;
        if (this.mAdapter == null) {
            Log.w(TAG, "Stopping BluetoothPbapService: device does not have BT or device is not ready");
            closeService();
        } else if (intent != null) {
            parseIntent(intent);
        }
        return 2;
    }

    private void parseIntent(Intent intent) {
        String action = intent.getStringExtra(AdapterService.EXTRA_ACTION);
        int state = intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE);
        boolean removeTimeoutMsg = true;
        if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
            if (state == 13) {
                if (this.mSessionStatusHandler.hasMessages(2)) {
                    Intent timeoutIntent = new Intent("android.bluetooth.device.action.CONNECTION_ACCESS_CANCEL");
                    timeoutIntent.setClassName(ACCESS_AUTHORITY_PACKAGE, ACCESS_AUTHORITY_CLASS);
                    timeoutIntent.putExtra("android.bluetooth.device.extra.ACCESS_REQUEST_TYPE", 2);
                    sendBroadcast(timeoutIntent, "android.permission.BLUETOOTH_ADMIN");
                }
                closeService();
            } else {
                removeTimeoutMsg = false;
            }
        } else if (action.equals("android.bluetooth.device.action.ACL_DISCONNECTED") && this.mIsWaitingAuthorization) {
            BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            if (this.mRemoteDevice == null || device == null) {
                Log.e(TAG, "Unexpected error!");
                return;
            }
            Log.d(TAG, "ACL disconnected for " + device);
            if (this.mRemoteDevice.equals(device)) {
                Intent cancelIntent = new Intent("android.bluetooth.device.action.CONNECTION_ACCESS_CANCEL");
                cancelIntent.putExtra("android.bluetooth.device.extra.DEVICE", device);
                cancelIntent.putExtra("android.bluetooth.device.extra.ACCESS_REQUEST_TYPE", 2);
                sendBroadcast(cancelIntent);
                this.mIsWaitingAuthorization = false;
                stopObexServerSession();
            }
        } else if (action.equals("android.bluetooth.device.action.CONNECTION_ACCESS_REPLY")) {
            int requestType = intent.getIntExtra("android.bluetooth.device.extra.ACCESS_REQUEST_TYPE", 2);
            if (this.mIsWaitingAuthorization && requestType == 2) {
                this.mIsWaitingAuthorization = false;
                if (intent.getIntExtra("android.bluetooth.device.extra.CONNECTION_ACCESS_RESULT", 2) == 1) {
                    if (intent.getBooleanExtra("android.bluetooth.device.extra.ALWAYS_ALLOWED", false)) {
                        this.mRemoteDevice.setPhonebookAccessPermission(1);
                    }
                    try {
                        if (this.mConnSocket != null) {
                            startObexServerSession();
                        } else {
                            stopObexServerSession();
                        }
                    } catch (IOException ex) {
                        Log.e(TAG, "Caught the error: " + ex.toString());
                    }
                } else {
                    if (intent.getBooleanExtra("android.bluetooth.device.extra.ALWAYS_ALLOWED", false)) {
                        this.mRemoteDevice.setPhonebookAccessPermission(2);
                    }
                    stopObexServerSession();
                }
            } else {
                return;
            }
        } else if (action.equals(AUTH_RESPONSE_ACTION)) {
            notifyAuthKeyInput(intent.getStringExtra(EXTRA_SESSION_KEY));
        } else if (action.equals(AUTH_CANCELLED_ACTION)) {
            notifyAuthCancelled();
        } else {
            removeTimeoutMsg = false;
        }
        if (removeTimeoutMsg) {
            this.mSessionStatusHandler.removeMessages(2);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        setState(0, 2);
        closeService();
        if (this.mSessionStatusHandler != null) {
            this.mSessionStatusHandler.removeCallbacksAndMessages(null);
        }
    }

    public IBinder onBind(Intent intent) {
        return this.mBinder;
    }

    private void startRfcommSocketListener() {
        if (this.mAcceptThread == null) {
            this.mAcceptThread = new SocketAcceptThread();
            this.mAcceptThread.setName("BluetoothPbapAcceptThread");
            this.mAcceptThread.start();
        }
    }

    private final boolean initSocket() {
        boolean initSocketOK = false;
        int i = 0;
        while (i < 10 && !this.mInterrupted) {
            initSocketOK = true;
            try {
                this.mServerSocket = this.mAdapter.listenUsingEncryptedRfcommWithServiceRecord("OBEX Phonebook Access Server", BluetoothUuid.PBAP_PSE.getUuid());
            } catch (IOException e) {
                Log.e(TAG, "Error create RfcommServerSocket " + e.toString());
                initSocketOK = false;
            }
            if (!initSocketOK && this.mAdapter != null) {
                int state = this.mAdapter.getState();
                if (state != 11 && state != 12) {
                    Log.w(TAG, "initServerSocket failed as BT is (being) turned off");
                    break;
                }
                try {
                    Thread.sleep(300);
                    i++;
                } catch (InterruptedException e2) {
                    Log.e(TAG, "socketAcceptThread thread was interrupted (3)");
                }
            } else {
                break;
            }
        }
        if (this.mInterrupted) {
            initSocketOK = false;
            closeServerSocket();
        }
        if (!initSocketOK) {
            Log.e(TAG, "Error to create listening socket after 10 try");
        }
        return initSocketOK;
    }

    private final synchronized void closeServerSocket() {
        if (this.mServerSocket != null) {
            try {
                this.mServerSocket.close();
                this.mServerSocket = null;
            } catch (IOException ex) {
                Log.e(TAG, "Close Server Socket error: " + ex);
            }
        }
    }

    private final synchronized void closeConnectionSocket() {
        if (this.mConnSocket != null) {
            try {
                this.mConnSocket.close();
                this.mConnSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "Close Connection Socket error: " + e.toString());
            }
        }
    }

    private final void closeService() {
        this.mInterrupted = true;
        closeServerSocket();
        if (this.mAcceptThread != null) {
            try {
                this.mAcceptThread.shutdown();
                this.mAcceptThread.join();
                this.mAcceptThread = null;
            } catch (InterruptedException ex) {
                Log.w(TAG, "mAcceptThread close error" + ex);
            }
        }
        if (this.mWakeLock != null) {
            this.mWakeLock.release();
            this.mWakeLock = null;
        }
        if (this.mServerSession != null) {
            this.mServerSession.close();
            this.mServerSession = null;
        }
        closeConnectionSocket();
        this.mHasStarted = false;
        if (this.mStartId != -1 && stopSelfResult(this.mStartId)) {
            this.mStartId = -1;
        }
    }

    private final void startObexServerSession() throws IOException {
        if (this.mWakeLock == null) {
            this.mWakeLock = ((PowerManager) getSystemService("power")).newWakeLock(1, "StartingObexPbapTransaction");
            this.mWakeLock.setReferenceCounted(false);
            this.mWakeLock.acquire();
        }
        TelephonyManager tm = (TelephonyManager) getSystemService("phone");
        if (tm != null) {
            sLocalPhoneNum = tm.getLine1Number();
            sLocalPhoneName = tm.getLine1AlphaTag();
            if (TextUtils.isEmpty(sLocalPhoneName)) {
                sLocalPhoneName = getString(C0000R.string.localPhoneName);
            }
        }
        this.mPbapServer = new BluetoothPbapObexServer(this.mSessionStatusHandler, this);
        synchronized (this) {
            this.mAuth = new BluetoothPbapAuthenticator(this.mSessionStatusHandler);
            this.mAuth.setChallenged(false);
            this.mAuth.setCancelled(false);
        }
        this.mServerSession = new ServerSession(new BluetoothPbapRfcommTransport(this.mConnSocket), this.mPbapServer, this.mAuth);
        setState(2);
        this.mSessionStatusHandler.removeMessages(5005);
        this.mSessionStatusHandler.sendMessageDelayed(this.mSessionStatusHandler.obtainMessage(5005), 10000);
    }

    private void stopObexServerSession() {
        this.mSessionStatusHandler.removeMessages(5004);
        this.mSessionStatusHandler.removeMessages(5005);
        if (this.mWakeLock != null) {
            this.mWakeLock.release();
            this.mWakeLock = null;
        }
        if (this.mServerSession != null) {
            this.mServerSession.close();
            this.mServerSession = null;
        }
        this.mAcceptThread = null;
        closeConnectionSocket();
        if (this.mAdapter.isEnabled()) {
            startRfcommSocketListener();
        }
        setState(0);
    }

    private void notifyAuthKeyInput(String key) {
        synchronized (this.mAuth) {
            if (key != null) {
                this.mAuth.setSessionKey(key);
            }
            this.mAuth.setChallenged(true);
            this.mAuth.notify();
        }
    }

    private void notifyAuthCancelled() {
        synchronized (this.mAuth) {
            this.mAuth.setCancelled(true);
            this.mAuth.notify();
        }
    }

    private void setState(int state) {
        setState(state, 1);
    }

    private synchronized void setState(int state, int result) {
        if (state != this.mState) {
            Log.d(TAG, "Pbap state " + this.mState + " -> " + state + ", result = " + result);
            int prevState = this.mState;
            this.mState = state;
            Intent intent = new Intent("android.bluetooth.pbap.intent.action.PBAP_STATE_CHANGED");
            intent.putExtra("android.bluetooth.pbap.intent.PBAP_PREVIOUS_STATE", prevState);
            intent.putExtra("android.bluetooth.pbap.intent.PBAP_STATE", this.mState);
            intent.putExtra("android.bluetooth.device.extra.DEVICE", this.mRemoteDevice);
            sendBroadcast(intent, "android.permission.BLUETOOTH");
            AdapterService s = AdapterService.getAdapterService();
            if (s != null) {
                s.onProfileConnectionStateChanged(this.mRemoteDevice, 6, this.mState, prevState);
            }
        }
    }

    private void createPbapNotification(String action) {
        NotificationManager nm = (NotificationManager) getSystemService("notification");
        Intent clickIntent = new Intent();
        clickIntent.setClass(this, BluetoothPbapActivity.class);
        clickIntent.addFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
        clickIntent.setAction(action);
        Intent deleteIntent = new Intent();
        deleteIntent.setClass(this, BluetoothPbapReceiver.class);
        String name = getRemoteDeviceName();
        if (action.equals(AUTH_CHALL_ACTION)) {
            deleteIntent.setAction(AUTH_CANCELLED_ACTION);
            Notification notification = new Notification(17301632, getString(C0000R.string.auth_notif_ticker), System.currentTimeMillis());
            notification.color = getResources().getColor(17170521);
            notification.setLatestEventInfo(this, getString(C0000R.string.auth_notif_title), getString(C0000R.string.auth_notif_message, new Object[]{name}), PendingIntent.getActivity(this, 0, clickIntent, 0));
            notification.flags |= 16;
            notification.flags |= 8;
            notification.defaults = 1;
            notification.deleteIntent = PendingIntent.getBroadcast(this, 0, deleteIntent, 0);
            nm.notify(NOTIFICATION_ID_AUTH, notification);
        }
    }

    private void removePbapNotification(int id) {
        ((NotificationManager) getSystemService("notification")).cancel(id);
    }

    public static String getLocalPhoneNum() {
        return sLocalPhoneNum;
    }

    public static String getLocalPhoneName() {
        return sLocalPhoneName;
    }

    public static String getRemoteDeviceName() {
        return sRemoteDeviceName;
    }
}
