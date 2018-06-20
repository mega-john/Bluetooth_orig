package com.android.bluetooth.opp;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class BluetoothOppSendFileInfo {
    /* renamed from: D */
    private static final boolean f53D = true;
    static final BluetoothOppSendFileInfo SEND_FILE_INFO_ERROR = new BluetoothOppSendFileInfo(null, null, 0, null, BluetoothShare.STATUS_FILE_ERROR);
    private static final String TAG = "BluetoothOppSendFileInfo";
    /* renamed from: V */
    private static final boolean f54V = false;
    public final String mData;
    public final String mFileName;
    public final FileInputStream mInputStream;
    public final long mLength;
    public final String mMimetype;
    public final int mStatus;

    public BluetoothOppSendFileInfo(String fileName, String type, long length, FileInputStream inputStream, int status) {
        this.mFileName = fileName;
        this.mMimetype = type;
        this.mLength = length;
        this.mInputStream = inputStream;
        this.mStatus = status;
        this.mData = null;
    }

    public BluetoothOppSendFileInfo(String data, String type, long length, int status) {
        this.mFileName = null;
        this.mInputStream = null;
        this.mData = data;
        this.mMimetype = type;
        this.mLength = length;
        this.mStatus = status;
    }

    public static BluetoothOppSendFileInfo generateFileInfo(Context context, Uri uri, String type) {
        String fileName;
        long length;
        Throwable th;
        String contentType;
        ContentResolver contentResolver = context.getContentResolver();
        String scheme = uri.getScheme();
        if ("content".equals(scheme)) {
            Cursor metadataCursor;
            String contentType2 = contentResolver.getType(uri);
            try {
                metadataCursor = contentResolver.query(uri, new String[]{"_display_name", "_size"}, null, null, null);
            } catch (SQLiteException e) {
                metadataCursor = null;
            }
            if (metadataCursor != null) {
                try {
                    if (metadataCursor.moveToFirst()) {
                        fileName = metadataCursor.getString(0);
                        try {
                            length = (long) metadataCursor.getInt(1);
                        } catch (Throwable th2) {
                            th = th2;
                            length = 0;
                            metadataCursor.close();
                            throw th;
                        }
                        try {
                            Log.d(TAG, "fileName = " + fileName + " length = " + length);
                        } catch (Throwable th3) {
                            th = th3;
                            metadataCursor.close();
                            throw th;
                        }
                    }
                    length = 0;
                    fileName = null;
                    metadataCursor.close();
                } catch (Throwable th4) {
                    th = th4;
                    length = 0;
                    fileName = null;
                    metadataCursor.close();
                    throw th;
                }
            }
            length = 0;
            fileName = null;
            if (fileName == null) {
                fileName = uri.getLastPathSegment();
            }
            contentType = contentType2;
        } else if ("file".equals(scheme)) {
            fileName = uri.getLastPathSegment();
            contentType = type;
            length = new File(uri.getPath()).length();
        } else {
            length = 0;
            fileName = null;
            return SEND_FILE_INFO_ERROR;
        }
        FileInputStream is = null;
        if (scheme.equals("content")) {
            try {
                AssetFileDescriptor fd = contentResolver.openAssetFileDescriptor(uri, "r");
                long statLength = fd.getLength();
                if (length != statLength && statLength > 0) {
                    Log.e(TAG, "Content provider length is wrong (" + Long.toString(length) + "), using stat length (" + Long.toString(statLength) + ")");
                    length = statLength;
                }
                try {
                    is = fd.createInputStream();
                } catch (IOException e2) {
                    try {
                        fd.close();
                    } catch (IOException e3) {
                    }
                }
            } catch (FileNotFoundException e4) {
            }
        }
        if (is == null) {
            try {
                is = (FileInputStream) contentResolver.openInputStream(uri);
            } catch (FileNotFoundException e5) {
                return SEND_FILE_INFO_ERROR;
            }
        }
        if (length == 0) {
            try {
                length = (long) is.available();
            } catch (IOException e6) {
                Log.e(TAG, "Read stream exception: ", e6);
                return SEND_FILE_INFO_ERROR;
            }
        }
        return new BluetoothOppSendFileInfo(fileName, contentType, length, is, 0);
    }
}
