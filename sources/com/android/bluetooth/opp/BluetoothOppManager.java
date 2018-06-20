package com.android.bluetooth.opp;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import com.android.bluetooth.C0000R;
import com.android.vcard.VCardConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BluetoothOppManager {
    private static final int ALLOWED_INSERT_SHARE_THREAD_NUMBER = 3;
    private static final String ARRAYLIST_ITEM_SEPERATOR = ";";
    private static final String FILE_URI = "FILE_URI";
    private static final String FILE_URIS = "FILE_URIS";
    private static BluetoothOppManager INSTANCE = null;
    private static Object INSTANCE_LOCK = new Object();
    private static final String MIME_TYPE = "MIMETYPE";
    private static final String MIME_TYPE_MULTIPLE = "MIMETYPE_MULTIPLE";
    private static final String MULTIPLE_FLAG = "MULTIPLE_FLAG";
    private static final String OPP_PREFERENCE_FILE = "OPPMGR";
    private static final String SENDING_FLAG = "SENDINGFLAG";
    private static final String TAG = "BluetoothOppManager";
    /* renamed from: V */
    private static final boolean f39V = false;
    private static final int WHITELIST_DURATION_MS = 15000;
    private BluetoothAdapter mAdapter;
    private Context mContext;
    private boolean mInitialized;
    private int mInsertShareThreadNum = 0;
    private boolean mIsHandoverInitiated;
    private String mMimeTypeOfSendingFile;
    private String mMimeTypeOfSendingFiles;
    public boolean mMultipleFlag;
    public boolean mSendingFlag;
    private String mUriOfSendingFile;
    private ArrayList<Uri> mUrisOfSendingFiles;
    private List<Pair<String, Long>> mWhitelist = new ArrayList();
    private int mfileNumInBatch;

    private class InsertShareInfoThread extends Thread {
        private final boolean mIsHandoverInitiated;
        private final boolean mIsMultiple;
        private final BluetoothDevice mRemoteDevice;
        private final String mTypeOfMultipleFiles;
        private final String mTypeOfSingleFile;
        private final String mUri;
        private final ArrayList<Uri> mUris;

        public InsertShareInfoThread(BluetoothDevice device, boolean multiple, String typeOfSingleFile, String uri, String typeOfMultipleFiles, ArrayList<Uri> uris, boolean handoverInitiated) {
            super("Insert ShareInfo Thread");
            this.mRemoteDevice = device;
            this.mIsMultiple = multiple;
            this.mTypeOfSingleFile = typeOfSingleFile;
            this.mUri = uri;
            this.mTypeOfMultipleFiles = typeOfMultipleFiles;
            this.mUris = uris;
            this.mIsHandoverInitiated = handoverInitiated;
            synchronized (BluetoothOppManager.this) {
                BluetoothOppManager.this.mInsertShareThreadNum = BluetoothOppManager.this.mInsertShareThreadNum + 1;
            }
        }

        public void run() {
            Process.setThreadPriority(10);
            if (this.mRemoteDevice == null) {
                Log.e(BluetoothOppManager.TAG, "Target bt device is null!");
                return;
            }
            if (this.mIsMultiple) {
                insertMultipleShare();
            } else {
                insertSingleShare();
            }
            synchronized (BluetoothOppManager.this) {
                BluetoothOppManager.this.mInsertShareThreadNum = BluetoothOppManager.this.mInsertShareThreadNum - 1;
            }
        }

        private void insertMultipleShare() {
            int count = this.mUris.size();
            Long ts = Long.valueOf(System.currentTimeMillis());
            for (int i = 0; i < count; i++) {
                Uri fileUri = (Uri) this.mUris.get(i);
                ContentValues values = new ContentValues();
                values.put("uri", fileUri.toString());
                String contentType = BluetoothOppManager.this.mContext.getContentResolver().getType(BluetoothOppUtility.originalUri(fileUri));
                if (TextUtils.isEmpty(contentType)) {
                    contentType = this.mTypeOfMultipleFiles;
                }
                values.put(BluetoothShare.MIMETYPE, contentType);
                values.put(BluetoothShare.DESTINATION, this.mRemoteDevice.getAddress());
                values.put("timestamp", ts);
                if (this.mIsHandoverInitiated) {
                    values.put(BluetoothShare.USER_CONFIRMATION, Integer.valueOf(5));
                }
                BluetoothOppManager.this.mContext.getContentResolver().insert(BluetoothShare.CONTENT_URI, values);
            }
        }

        private void insertSingleShare() {
            ContentValues values = new ContentValues();
            values.put("uri", this.mUri);
            values.put(BluetoothShare.MIMETYPE, this.mTypeOfSingleFile);
            values.put(BluetoothShare.DESTINATION, this.mRemoteDevice.getAddress());
            if (this.mIsHandoverInitiated) {
                values.put(BluetoothShare.USER_CONFIRMATION, Integer.valueOf(5));
            }
            Uri contentUri = BluetoothOppManager.this.mContext.getContentResolver().insert(BluetoothShare.CONTENT_URI, values);
        }
    }

    public static BluetoothOppManager getInstance(Context context) {
        BluetoothOppManager bluetoothOppManager;
        synchronized (INSTANCE_LOCK) {
            if (INSTANCE == null) {
                INSTANCE = new BluetoothOppManager();
            }
            INSTANCE.init(context);
            bluetoothOppManager = INSTANCE;
        }
        return bluetoothOppManager;
    }

    private boolean init(Context context) {
        if (!this.mInitialized) {
            this.mInitialized = true;
            this.mContext = context;
            this.mAdapter = BluetoothAdapter.getDefaultAdapter();
            if (this.mAdapter == null) {
                restoreApplicationData();
            } else {
                restoreApplicationData();
            }
        }
        return true;
    }

    private void cleanupWhitelist() {
        long curTime = SystemClock.elapsedRealtime();
        Iterator<Pair<String, Long>> iter = this.mWhitelist.iterator();
        while (iter.hasNext()) {
            if (curTime - ((Long) ((Pair) iter.next()).second).longValue() > 15000) {
                iter.remove();
            }
        }
    }

    public synchronized void addToWhitelist(String address) {
        if (address != null) {
            Iterator<Pair<String, Long>> iter = this.mWhitelist.iterator();
            while (iter.hasNext()) {
                if (((String) ((Pair) iter.next()).first).equals(address)) {
                    iter.remove();
                }
            }
            this.mWhitelist.add(new Pair(address, Long.valueOf(SystemClock.elapsedRealtime())));
        }
    }

    public synchronized boolean isWhitelisted(String address) {
        boolean z;
        cleanupWhitelist();
        for (Pair<String, Long> entry : this.mWhitelist) {
            if (((String) entry.first).equals(address)) {
                z = true;
                break;
            }
        }
        z = f39V;
        return z;
    }

    private void restoreApplicationData() {
        SharedPreferences settings = this.mContext.getSharedPreferences(OPP_PREFERENCE_FILE, 0);
        this.mSendingFlag = settings.getBoolean(SENDING_FLAG, f39V);
        this.mMimeTypeOfSendingFile = settings.getString(MIME_TYPE, null);
        this.mUriOfSendingFile = settings.getString(FILE_URI, null);
        this.mMimeTypeOfSendingFiles = settings.getString(MIME_TYPE_MULTIPLE, null);
        this.mMultipleFlag = settings.getBoolean(MULTIPLE_FLAG, f39V);
        String strUris = settings.getString(FILE_URIS, null);
        this.mUrisOfSendingFiles = new ArrayList();
        if (strUris != null) {
            String[] splitUri = strUris.split(ARRAYLIST_ITEM_SEPERATOR);
            for (String parse : splitUri) {
                this.mUrisOfSendingFiles.add(Uri.parse(parse));
            }
        }
        this.mContext.getSharedPreferences(OPP_PREFERENCE_FILE, 0).edit().clear().apply();
    }

    private void storeApplicationData() {
        Editor editor = this.mContext.getSharedPreferences(OPP_PREFERENCE_FILE, 0).edit();
        editor.putBoolean(SENDING_FLAG, this.mSendingFlag);
        editor.putBoolean(MULTIPLE_FLAG, this.mMultipleFlag);
        if (this.mMultipleFlag) {
            editor.putString(MIME_TYPE_MULTIPLE, this.mMimeTypeOfSendingFiles);
            StringBuilder sb = new StringBuilder();
            int count = this.mUrisOfSendingFiles.size();
            for (int i = 0; i < count; i++) {
                sb.append((Uri) this.mUrisOfSendingFiles.get(i));
                sb.append(ARRAYLIST_ITEM_SEPERATOR);
            }
            editor.putString(FILE_URIS, sb.toString());
            editor.remove(MIME_TYPE);
            editor.remove(FILE_URI);
        } else {
            editor.putString(MIME_TYPE, this.mMimeTypeOfSendingFile);
            editor.putString(FILE_URI, this.mUriOfSendingFile);
            editor.remove(MIME_TYPE_MULTIPLE);
            editor.remove(FILE_URIS);
        }
        editor.apply();
    }

    public void saveSendingFileInfo(String mimeType, String uriString, boolean isHandover) {
        synchronized (this) {
            this.mMultipleFlag = f39V;
            this.mMimeTypeOfSendingFile = mimeType;
            this.mIsHandoverInitiated = isHandover;
            Uri uri = Uri.parse(uriString);
            BluetoothOppSendFileInfo sendFileInfo = BluetoothOppSendFileInfo.generateFileInfo(this.mContext, uri, mimeType);
            uri = BluetoothOppUtility.generateUri(uri, sendFileInfo);
            BluetoothOppUtility.putSendFileInfo(uri, sendFileInfo);
            this.mUriOfSendingFile = uri.toString();
            storeApplicationData();
        }
    }

    public void saveSendingFileInfo(String mimeType, ArrayList<Uri> uris, boolean isHandover) {
        synchronized (this) {
            this.mMultipleFlag = true;
            this.mMimeTypeOfSendingFiles = mimeType;
            this.mUrisOfSendingFiles = new ArrayList();
            this.mIsHandoverInitiated = isHandover;
            Iterator i$ = uris.iterator();
            while (i$.hasNext()) {
                Uri uri = (Uri) i$.next();
                BluetoothOppSendFileInfo sendFileInfo = BluetoothOppSendFileInfo.generateFileInfo(this.mContext, uri, mimeType);
                uri = BluetoothOppUtility.generateUri(uri, sendFileInfo);
                this.mUrisOfSendingFiles.add(uri);
                BluetoothOppUtility.putSendFileInfo(uri, sendFileInfo);
            }
            storeApplicationData();
        }
    }

    public boolean isEnabled() {
        if (this.mAdapter != null) {
            return this.mAdapter.isEnabled();
        }
        return f39V;
    }

    public void enableBluetooth() {
        if (this.mAdapter != null) {
            this.mAdapter.enable();
        }
    }

    public void disableBluetooth() {
        if (this.mAdapter != null) {
            this.mAdapter.disable();
        }
    }

    public String getDeviceName(BluetoothDevice device) {
        String deviceName = BluetoothOppPreference.getInstance(this.mContext).getName(device);
        if (deviceName == null && this.mAdapter != null) {
            deviceName = device.getName();
        }
        if (deviceName == null) {
            return this.mContext.getString(C0000R.string.unknown_device);
        }
        return deviceName;
    }

    public int getBatchSize() {
        int i;
        synchronized (this) {
            i = this.mfileNumInBatch;
        }
        return i;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void startTransfer(BluetoothDevice device) {
        synchronized (this) {
            if (this.mInsertShareThreadNum > 3) {
                Log.e(TAG, "Too many shares user triggered concurrently!");
                Intent in = new Intent(this.mContext, BluetoothOppBtErrorActivity.class);
                in.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
                in.putExtra("title", this.mContext.getString(C0000R.string.enabling_progress_title));
                in.putExtra("content", this.mContext.getString(C0000R.string.ErrorTooManyRequests));
                this.mContext.startActivity(in);
                return;
            }
            InsertShareInfoThread insertThread = new InsertShareInfoThread(device, this.mMultipleFlag, this.mMimeTypeOfSendingFile, this.mUriOfSendingFile, this.mMimeTypeOfSendingFiles, this.mUrisOfSendingFiles, this.mIsHandoverInitiated);
            if (this.mMultipleFlag) {
                this.mfileNumInBatch = this.mUrisOfSendingFiles.size();
            }
        }
    }
}
