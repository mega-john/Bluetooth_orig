package com.android.bluetooth.pbap;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Profile;
import android.provider.ContactsContract.RawContactsEntity;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.opp.BluetoothShare;
import com.android.vcard.VCardComposer;
import com.android.vcard.VCardConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class BluetoothPbapUtils {
    public static int FILTER_NICKNAME = 23;
    public static int FILTER_PHOTO = 3;
    public static int FILTER_TEL = 7;
    private static final String TAG = "FilterUtils";
    /* renamed from: V */
    private static final boolean f73V = false;

    public static boolean hasFilter(byte[] filter) {
        return filter != null && filter.length > 0;
    }

    public static boolean isNameAndNumberOnly(byte[] filter) {
        if (hasFilter(filter)) {
            for (int i = 0; i <= 4; i++) {
                if (filter[i] != (byte) 0) {
                    return false;
                }
            }
            if ((filter[5] & 127) > 0 || filter[6] != (byte) 0 || (filter[7] & 120) > 0) {
                return false;
            }
            return true;
        }
        Log.v(TAG, "No filter set. isNameAndNumberOnly=false");
        return false;
    }

    public static boolean isFilterBitSet(byte[] filter, int filterBit) {
        if (hasFilter(filter)) {
            int byteNumber = 7 - (filterBit / 8);
            int bitNumber = filterBit % 8;
            if (byteNumber < filter.length) {
                if ((filter[byteNumber] & (1 << bitNumber)) > 0) {
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    public static VCardComposer createFilteredVCardComposer(Context ctx, int vcardType, byte[] filter) {
        int vType = vcardType;
        boolean includePhoto = BluetoothPbapConfig.includePhotosInVcard() && (!hasFilter(filter) || isFilterBitSet(filter, FILTER_PHOTO));
        if (!includePhoto) {
            vType |= VCardConfig.FLAG_REFRAIN_IMAGE_EXPORT;
        }
        return new VCardComposer(ctx, vType, true);
    }

    public static boolean isProfileSet(Context context) {
        boolean isSet = true;
        Cursor c = context.getContentResolver().query(Profile.CONTENT_VCARD_URI, new String[]{"_id"}, null, null, null);
        if (c == null || c.getCount() <= 0) {
            isSet = false;
        }
        if (c != null) {
            c.close();
        }
        return isSet;
    }

    public static String getProfileName(Context context) {
        Cursor c = context.getContentResolver().query(Profile.CONTENT_URI, new String[]{"display_name"}, null, null, null);
        String ownerName = null;
        if (c != null && c.moveToFirst()) {
            ownerName = c.getString(0);
        }
        if (c != null) {
            c.close();
        }
        return ownerName;
    }

    public static final String createProfileVCard(Context ctx, int vcardType, byte[] filter) {
        VCardComposer composer = null;
        String vcard = null;
        try {
            composer = createFilteredVCardComposer(ctx, vcardType, filter);
            if (composer.init(Profile.CONTENT_URI, null, null, null, null, Uri.withAppendedPath(Profile.CONTENT_URI, RawContactsEntity.CONTENT_URI.getLastPathSegment()))) {
                vcard = composer.createOneEntry();
            } else {
                Log.e(TAG, "Unable to create profile vcard. Error initializing composer: " + composer.getErrorReason());
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unable to create profile vcard.", t);
        }
        if (composer != null) {
            try {
                composer.terminate();
            } catch (Throwable th) {
            }
        }
        return vcard;
    }

    public static boolean createProfileVCardFile(File file, Context context) {
        Throwable t;
        InputStream is = null;
        OutputStream os = null;
        boolean success = true;
        try {
            AssetFileDescriptor fd = context.getContentResolver().openAssetFileDescriptor(Profile.CONTENT_VCARD_URI, "r");
            if (fd == null) {
                return false;
            }
            is = fd.createInputStream();
            OutputStream os2 = new FileOutputStream(file);
            try {
                Utils.copyStream(is, os2, BluetoothShare.STATUS_SUCCESS);
                os = os2;
            } catch (Throwable th) {
                t = th;
                os = os2;
                Log.e(TAG, "Unable to create default contact vcard file", t);
                success = false;
                Utils.safeCloseStream(is);
                Utils.safeCloseStream(os);
                return success;
            }
            Utils.safeCloseStream(is);
            Utils.safeCloseStream(os);
            return success;
        } catch (Throwable th2) {
            t = th2;
            Log.e(TAG, "Unable to create default contact vcard file", t);
            success = false;
            Utils.safeCloseStream(is);
            Utils.safeCloseStream(os);
            return success;
        }
    }
}
