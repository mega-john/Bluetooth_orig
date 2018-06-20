package com.android.bluetooth.map;

import android.graphics.drawable.Drawable;

public class BluetoothMapEmailSettingsItem implements Comparable<BluetoothMapEmailSettingsItem> {
    /* renamed from: D */
    private static final boolean f10D = true;
    private static final String TAG = "BluetoothMapEmailSettingsItem";
    /* renamed from: V */
    private static final boolean f11V = false;
    public String mBase_uri;
    public String mBase_uri_no_account;
    private Drawable mIcon;
    private String mId;
    protected boolean mIsChecked;
    private String mName;
    private String mPackageName;
    private String mProviderAuthority;

    public BluetoothMapEmailSettingsItem(String id, String name, String packageName, String authority, Drawable icon) {
        this.mName = name;
        this.mIcon = icon;
        this.mPackageName = packageName;
        this.mId = id;
        this.mProviderAuthority = authority;
        this.mBase_uri_no_account = "content://" + authority;
        this.mBase_uri = this.mBase_uri_no_account + "/" + id;
    }

    public long getAccountId() {
        if (this.mId != null) {
            return Long.parseLong(this.mId);
        }
        return -1;
    }

    public int compareTo(BluetoothMapEmailSettingsItem other) {
        if (other.mId.equals(this.mId) && other.mName.equals(this.mName) && other.mPackageName.equals(this.mPackageName) && other.mProviderAuthority.equals(this.mProviderAuthority) && other.mIsChecked == this.mIsChecked) {
            return 0;
        }
        return -1;
    }

    public int hashCode() {
        int i = 0;
        int hashCode = ((((((this.mId == null ? 0 : this.mId.hashCode()) + 31) * 31) + (this.mName == null ? 0 : this.mName.hashCode())) * 31) + (this.mPackageName == null ? 0 : this.mPackageName.hashCode())) * 31;
        if (this.mProviderAuthority != null) {
            i = this.mProviderAuthority.hashCode();
        }
        return hashCode + i;
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        BluetoothMapEmailSettingsItem other = (BluetoothMapEmailSettingsItem) obj;
        if (this.mId == null) {
            if (other.mId != null) {
                return false;
            }
        } else if (!this.mId.equals(other.mId)) {
            return false;
        }
        if (this.mName == null) {
            if (other.mName != null) {
                return false;
            }
        } else if (!this.mName.equals(other.mName)) {
            return false;
        }
        if (this.mPackageName == null) {
            if (other.mPackageName != null) {
                return false;
            }
        } else if (!this.mPackageName.equals(other.mPackageName)) {
            return false;
        }
        if (this.mProviderAuthority == null) {
            if (other.mProviderAuthority != null) {
                return false;
            }
            return true;
        } else if (this.mProviderAuthority.equals(other.mProviderAuthority)) {
            return true;
        } else {
            return false;
        }
    }

    public String toString() {
        return this.mName + " (" + this.mBase_uri + ")";
    }

    public Drawable getIcon() {
        return this.mIcon;
    }

    public void setIcon(Drawable icon) {
        this.mIcon = icon;
    }

    public String getName() {
        return this.mName;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public String getId() {
        return this.mId;
    }

    public void setId(String id) {
        this.mId = id;
    }

    public String getPackageName() {
        return this.mPackageName;
    }

    public void setPackageName(String packageName) {
        this.mPackageName = packageName;
    }

    public String getProviderAuthority() {
        return this.mProviderAuthority;
    }

    public void setProviderAuthority(String providerAuthority) {
        this.mProviderAuthority = providerAuthority;
    }
}
