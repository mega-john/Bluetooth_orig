package com.android.bluetooth.map;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import com.android.bluetooth.mapapi.BluetoothMapContract;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;

public class BluetoothMapEmailAppObserver {
    /* renamed from: D */
    private static final boolean f4D = true;
    private static final String TAG = "BluetoothMapEmailAppObserver";
    /* renamed from: V */
    private static final boolean f5V = false;
    private Context mContext;
    private LinkedHashMap<BluetoothMapEmailSettingsItem, ArrayList<BluetoothMapEmailSettingsItem>> mFullList;
    BluetoothMapEmailSettingsLoader mLoader;
    BluetoothMapService mMapService = null;
    private LinkedHashMap<String, ContentObserver> mObserverMap = new LinkedHashMap();
    private PackageManager mPackageManager = null;
    private BroadcastReceiver mReceiver;
    private ContentResolver mResolver;

    /* renamed from: com.android.bluetooth.map.BluetoothMapEmailAppObserver$2 */
    class C00252 extends BroadcastReceiver {
        C00252() {
        }

        public void onReceive(Context context, Intent intent) {
            Log.d(BluetoothMapEmailAppObserver.TAG, "onReceive\n");
            String action = intent.getAction();
            BluetoothMapEmailSettingsItem app;
            if ("android.intent.action.PACKAGE_ADDED".equals(action)) {
                Log.d(BluetoothMapEmailAppObserver.TAG, "The installed package is: " + intent.getData().getEncodedSchemeSpecificPart());
                app = BluetoothMapEmailAppObserver.this.mLoader.createAppItem(BluetoothMapEmailAppObserver.this.mPackageManager.resolveActivity(intent, 0), false);
                if (app != null) {
                    BluetoothMapEmailAppObserver.this.registerObserver(app);
                    BluetoothMapEmailAppObserver.this.mFullList.put(app, BluetoothMapEmailAppObserver.this.mLoader.parseAccounts(app));
                }
            } else if ("android.intent.action.PACKAGE_REMOVED".equals(action)) {
                String packageName = intent.getData().getEncodedSchemeSpecificPart();
                Log.d(BluetoothMapEmailAppObserver.TAG, "The removed package is: " + packageName);
                app = BluetoothMapEmailAppObserver.this.getApp(packageName);
                if (app != null) {
                    BluetoothMapEmailAppObserver.this.unregisterObserver(app);
                    BluetoothMapEmailAppObserver.this.mFullList.remove(app);
                }
            }
        }
    }

    public BluetoothMapEmailAppObserver(Context context, BluetoothMapService mapService) {
        this.mContext = context;
        this.mMapService = mapService;
        this.mResolver = context.getContentResolver();
        this.mLoader = new BluetoothMapEmailSettingsLoader(this.mContext);
        this.mFullList = this.mLoader.parsePackages(false);
        createReceiver();
        initObservers();
    }

    private BluetoothMapEmailSettingsItem getApp(String packageName) {
        for (BluetoothMapEmailSettingsItem app : this.mFullList.keySet()) {
            if (app.getPackageName().equals(packageName)) {
                return app;
            }
        }
        return null;
    }

    private void handleAccountChanges(String packageNameWithProvider) {
        Log.d(TAG, "handleAccountChanges (packageNameWithProvider: " + packageNameWithProvider + "\n");
        BluetoothMapEmailSettingsItem app = getApp(packageNameWithProvider.replaceFirst("\\.[^\\.]+$", ""));
        if (app != null) {
            ArrayList<BluetoothMapEmailSettingsItem> newAccountList = this.mLoader.parseAccounts(app);
            ArrayList<BluetoothMapEmailSettingsItem> oldAccountList = (ArrayList) this.mFullList.get(app);
            ArrayList<BluetoothMapEmailSettingsItem> addedAccountList = (ArrayList) newAccountList.clone();
            ArrayList<BluetoothMapEmailSettingsItem> removedAccountList = (ArrayList) this.mFullList.get(app);
            this.mFullList.put(app, newAccountList);
            Iterator it = newAccountList.iterator();
            while (it.hasNext()) {
                BluetoothMapEmailSettingsItem newAcc = (BluetoothMapEmailSettingsItem) it.next();
                Iterator i$ = oldAccountList.iterator();
                while (i$.hasNext()) {
                    BluetoothMapEmailSettingsItem oldAcc = (BluetoothMapEmailSettingsItem) i$.next();
                    if (newAcc.getId() == oldAcc.getId()) {
                        removedAccountList.remove(oldAcc);
                        addedAccountList.remove(newAcc);
                        if (!newAcc.getName().equals(oldAcc.getName()) && newAcc.mIsChecked) {
                            this.mMapService.updateMasInstances(2);
                        }
                        if (newAcc.mIsChecked != oldAcc.mIsChecked) {
                            if (newAcc.mIsChecked) {
                                this.mMapService.updateMasInstances(0);
                            } else {
                                this.mMapService.updateMasInstances(1);
                            }
                        }
                    }
                }
            }
            it = removedAccountList.iterator();
            while (it.hasNext()) {
                BluetoothMapEmailSettingsItem removedAcc = (BluetoothMapEmailSettingsItem) it.next();
                this.mMapService.updateMasInstances(1);
            }
            it = addedAccountList.iterator();
            while (it.hasNext()) {
                BluetoothMapEmailSettingsItem addedAcc = (BluetoothMapEmailSettingsItem) it.next();
                this.mMapService.updateMasInstances(0);
            }
            return;
        }
        Log.e(TAG, "Received change notification on package not registered for notifications!");
    }

    public void registerObserver(BluetoothMapEmailSettingsItem app) {
        Uri uri = BluetoothMapContract.buildAccountUri(app.getProviderAuthority());
        ContentObserver observer = new ContentObserver(new Handler()) {
            public void onChange(boolean selfChange) {
                onChange(selfChange, null);
            }

            public void onChange(boolean selfChange, Uri uri) {
                if (uri != null) {
                    BluetoothMapEmailAppObserver.this.handleAccountChanges(uri.getHost());
                } else {
                    Log.e(BluetoothMapEmailAppObserver.TAG, "Unable to handle change as the URI is NULL!");
                }
            }
        };
        this.mObserverMap.put(uri.toString(), observer);
        this.mResolver.registerContentObserver(uri, true, observer);
    }

    public void unregisterObserver(BluetoothMapEmailSettingsItem app) {
        Uri uri = BluetoothMapContract.buildAccountUri(app.getProviderAuthority());
        this.mResolver.unregisterContentObserver((ContentObserver) this.mObserverMap.get(uri.toString()));
        this.mObserverMap.remove(uri.toString());
    }

    private void initObservers() {
        Log.d(TAG, "initObservers()");
        for (BluetoothMapEmailSettingsItem app : this.mFullList.keySet()) {
            registerObserver(app);
        }
    }

    private void deinitObservers() {
        Log.d(TAG, "deinitObservers()");
        for (BluetoothMapEmailSettingsItem app : this.mFullList.keySet()) {
            unregisterObserver(app);
        }
    }

    private void createReceiver() {
        Log.d(TAG, "createReceiver()\n");
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.PACKAGE_ADDED");
        intentFilter.addAction("android.intent.action.PACKAGE_REMOVED");
        intentFilter.addDataScheme("package");
        this.mReceiver = new C00252();
        this.mContext.registerReceiver(this.mReceiver, new IntentFilter("android.intent.action.PACKAGE_ADDED"));
    }

    private void removeReceiver() {
        Log.d(TAG, "removeReceiver()\n");
        this.mContext.unregisterReceiver(this.mReceiver);
    }

    private PackageInfo getPackageInfo(String packageName) {
        this.mPackageManager = this.mContext.getPackageManager();
        try {
            return this.mPackageManager.getPackageInfo(packageName, BluetoothMapContentObserver.MESSAGE_TYPE_RETRIEVE_CONF);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Error getting package metadata", e);
            return null;
        }
    }

    public ArrayList<BluetoothMapEmailSettingsItem> getEnabledAccountItems() {
        Log.d(TAG, "getEnabledAccountItems()\n");
        ArrayList<BluetoothMapEmailSettingsItem> list = new ArrayList();
        for (BluetoothMapEmailSettingsItem app : this.mFullList.keySet()) {
            Iterator i$ = ((ArrayList) this.mFullList.get(app)).iterator();
            while (i$.hasNext()) {
                BluetoothMapEmailSettingsItem acc = (BluetoothMapEmailSettingsItem) i$.next();
                if (acc.mIsChecked) {
                    list.add(acc);
                }
            }
        }
        return list;
    }

    public ArrayList<BluetoothMapEmailSettingsItem> getAllAccountItems() {
        Log.d(TAG, "getAllAccountItems()\n");
        ArrayList<BluetoothMapEmailSettingsItem> list = new ArrayList();
        for (BluetoothMapEmailSettingsItem app : this.mFullList.keySet()) {
            list.addAll((ArrayList) this.mFullList.get(app));
        }
        return list;
    }

    public void shutdown() {
        deinitObservers();
        removeReceiver();
    }
}
