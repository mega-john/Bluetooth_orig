package com.android.bluetooth.map;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import com.android.bluetooth.mapapi.BluetoothMapContract;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

public class BluetoothMapEmailSettingsLoader {
    /* renamed from: D */
    private static final boolean f12D = true;
    private static final long PROVIDER_ANR_TIMEOUT = 20000;
    private static final String TAG = "BluetoothMapEmailSettingsLoader";
    /* renamed from: V */
    private static final boolean f13V = false;
    private int mAccountsEnabledCount = 0;
    private Context mContext = null;
    private PackageManager mPackageManager = null;
    private ContentProviderClient mProviderClient = null;
    private ContentResolver mResolver;

    public BluetoothMapEmailSettingsLoader(Context ctx) {
        this.mContext = ctx;
    }

    public LinkedHashMap<BluetoothMapEmailSettingsItem, ArrayList<BluetoothMapEmailSettingsItem>> parsePackages(boolean includeIcon) {
        LinkedHashMap<BluetoothMapEmailSettingsItem, ArrayList<BluetoothMapEmailSettingsItem>> groups = new LinkedHashMap();
        Intent searchIntent = new Intent("android.bluetooth.action.BLUETOOTH_MAP_PROVIDER");
        this.mAccountsEnabledCount = 0;
        this.mPackageManager = this.mContext.getPackageManager();
        List<ResolveInfo> resInfos = this.mPackageManager.queryIntentContentProviders(searchIntent, 0);
        if (resInfos != null) {
            Log.d(TAG, "Found " + resInfos.size() + " applications");
            for (ResolveInfo rInfo : resInfos) {
                if ((rInfo.providerInfo.applicationInfo.flags & 2097152) == 0) {
                    BluetoothMapEmailSettingsItem app = createAppItem(rInfo, includeIcon);
                    if (app != null) {
                        ArrayList<BluetoothMapEmailSettingsItem> accounts = parseAccounts(app);
                        if (accounts.size() > 0) {
                            app.mIsChecked = true;
                            Iterator i$ = accounts.iterator();
                            while (i$.hasNext()) {
                                if (!((BluetoothMapEmailSettingsItem) i$.next()).mIsChecked) {
                                    app.mIsChecked = false;
                                    break;
                                }
                            }
                            groups.put(app, accounts);
                        }
                    }
                } else {
                    Log.d(TAG, "Ignoring force-stopped authority " + rInfo.providerInfo.authority + "\n");
                }
            }
        } else {
            Log.d(TAG, "Found no applications");
        }
        return groups;
    }

    public BluetoothMapEmailSettingsItem createAppItem(ResolveInfo rInfo, boolean includeIcon) {
        Drawable drawable = null;
        String provider = rInfo.providerInfo.authority;
        if (provider == null) {
            return null;
        }
        String name = rInfo.loadLabel(this.mPackageManager).toString();
        Log.d(TAG, rInfo.providerInfo.packageName + " - " + name + " - meta-data(provider = " + provider + ")\n");
        String str = "0";
        String str2 = rInfo.providerInfo.packageName;
        if (includeIcon) {
            drawable = rInfo.loadIcon(this.mPackageManager);
        }
        return new BluetoothMapEmailSettingsItem(str, name, str2, provider, drawable);
    }

    public ArrayList<BluetoothMapEmailSettingsItem> parseAccounts(BluetoothMapEmailSettingsItem app) {
        Cursor c = null;
        Log.d(TAG, "Adding app " + app.getPackageName());
        ArrayList<BluetoothMapEmailSettingsItem> children = new ArrayList();
        this.mResolver = this.mContext.getContentResolver();
        try {
            this.mProviderClient = this.mResolver.acquireUnstableContentProviderClient(Uri.parse(app.mBase_uri_no_account));
            if (this.mProviderClient == null) {
                throw new RemoteException("Failed to acquire provider for " + app.getPackageName());
            }
            this.mProviderClient.setDetectNotResponding(PROVIDER_ANR_TIMEOUT);
            c = this.mProviderClient.query(Uri.parse(app.mBase_uri_no_account + "/" + "Account"), BluetoothMapContract.BT_ACCOUNT_PROJECTION, null, null, "_id DESC");
            while (c != null && c.moveToNext()) {
                BluetoothMapEmailSettingsItem child = new BluetoothMapEmailSettingsItem(String.valueOf(c.getInt(c.getColumnIndex("_id"))), c.getString(c.getColumnIndex("account_display_name")), app.getPackageName(), app.getProviderAuthority(), null);
                child.mIsChecked = c.getInt(c.getColumnIndex("flag_expose")) != 0;
                if (child.mIsChecked) {
                    this.mAccountsEnabledCount++;
                }
                children.add(child);
            }
            if (c != null) {
                c.close();
            }
            return children;
        } catch (RemoteException e) {
            Log.d(TAG, "Could not establish ContentProviderClient for " + app.getPackageName() + " - returning empty account list");
            return children;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    public int getAccountsEnabledCount() {
        Log.d(TAG, "Enabled Accounts count:" + this.mAccountsEnabledCount);
        return this.mAccountsEnabledCount;
    }
}
