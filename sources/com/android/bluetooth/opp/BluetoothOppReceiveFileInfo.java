package com.android.bluetooth.opp;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

public class BluetoothOppReceiveFileInfo {
    /* renamed from: D */
    private static final boolean f48D = true;
    /* renamed from: V */
    private static final boolean f49V = false;
    private static String sDesiredStoragePath = null;
    public String mData;
    public String mFileName;
    public long mLength;
    public FileOutputStream mOutputStream;
    public int mStatus;

    public BluetoothOppReceiveFileInfo(String data, long length, int status) {
        this.mData = data;
        this.mStatus = status;
        this.mLength = length;
    }

    public BluetoothOppReceiveFileInfo(String filename, long length, FileOutputStream outputStream, int status) {
        this.mFileName = filename;
        this.mOutputStream = outputStream;
        this.mStatus = status;
        this.mLength = length;
    }

    public BluetoothOppReceiveFileInfo(int status) {
        this(null, 0, null, status);
    }

    public static BluetoothOppReceiveFileInfo generateFileInfo(Context context, int id) {
        long length;
        Throwable th;
        ContentResolver contentResolver = context.getContentResolver();
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + id);
        String hint = null;
        String mimeType = null;
        Cursor metadataCursor = contentResolver.query(contentUri, new String[]{BluetoothShare.FILENAME_HINT, BluetoothShare.TOTAL_BYTES, BluetoothShare.MIMETYPE}, null, null, null);
        if (metadataCursor != null) {
            try {
                if (metadataCursor.moveToFirst()) {
                    hint = metadataCursor.getString(0);
                    length = (long) metadataCursor.getInt(1);
                    try {
                        mimeType = metadataCursor.getString(2);
                    } catch (Throwable th2) {
                        th = th2;
                        metadataCursor.close();
                        throw th;
                    }
                }
                length = 0;
                metadataCursor.close();
            } catch (Throwable th3) {
                th = th3;
                length = 0;
                metadataCursor.close();
                throw th;
            }
        }
        length = 0;
        if (Environment.getExternalStorageState().equals("mounted")) {
            File base = new File(Environment.getExternalStorageDirectory().getPath() + Constants.DEFAULT_STORE_SUBDIR);
            if (base.isDirectory() || base.mkdir()) {
                StatFs statFs = new StatFs(base.getPath());
                if (((long) statFs.getBlockSize()) * (((long) statFs.getAvailableBlocks()) - 4) < length) {
                    Log.d(Constants.TAG, "Receive File aborted - not enough free space");
                    return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_ERROR_SDCARD_FULL);
                }
                String filename = choosefilename(hint);
                if (filename == null) {
                    return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
                }
                String extension;
                int dotIndex = filename.lastIndexOf(".");
                if (dotIndex >= 0) {
                    extension = filename.substring(dotIndex);
                    filename = filename.substring(0, dotIndex);
                } else if (mimeType == null) {
                    return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
                } else {
                    extension = "";
                }
                String fullfilename = chooseUniquefilename(base.getPath() + File.separator + filename, extension);
                if (!safeCanonicalPath(fullfilename)) {
                    return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
                }
                if (fullfilename == null) {
                    return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
                }
                try {
                    new FileOutputStream(fullfilename).close();
                    int index = fullfilename.lastIndexOf(47) + 1;
                    if (index > 0) {
                        String displayName = fullfilename.substring(index);
                        ContentValues updateValues = new ContentValues();
                        updateValues.put(BluetoothShare.FILENAME_HINT, displayName);
                        context.getContentResolver().update(contentUri, updateValues, null, null);
                    }
                    return new BluetoothOppReceiveFileInfo(fullfilename, length, new FileOutputStream(fullfilename), 0);
                } catch (IOException e) {
                    Log.e(Constants.TAG, "Error when creating file " + fullfilename);
                    return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
                }
            }
            Log.d(Constants.TAG, "Receive File aborted - can't create base directory " + base.getPath());
            return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_FILE_ERROR);
        }
        Log.d(Constants.TAG, "Receive File aborted - no external storage");
        return new BluetoothOppReceiveFileInfo(BluetoothShare.STATUS_ERROR_NO_SDCARD);
    }

    private static boolean safeCanonicalPath(String uniqueFileName) {
        try {
            File receiveFile = new File(uniqueFileName);
            if (sDesiredStoragePath == null) {
                sDesiredStoragePath = Environment.getExternalStorageDirectory().getPath() + Constants.DEFAULT_STORE_SUBDIR;
            }
            if (receiveFile.getCanonicalPath().startsWith(sDesiredStoragePath)) {
                return true;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static String chooseUniquefilename(String filename, String extension) {
        String fullfilename = filename + extension;
        if (!new File(fullfilename).exists()) {
            return fullfilename;
        }
        filename = filename + Constants.filename_SEQUENCE_SEPARATOR;
        Random rnd = new Random(SystemClock.uptimeMillis());
        int sequence = 1;
        for (int magnitude = 1; magnitude < 1000000000; magnitude *= 10) {
            for (int iteration = 0; iteration < 9; iteration++) {
                fullfilename = filename + sequence + extension;
                if (!new File(fullfilename).exists()) {
                    return fullfilename;
                }
                sequence += rnd.nextInt(magnitude) + 1;
            }
        }
        return null;
    }

    private static String choosefilename(String hint) {
        if (null != null || hint == null || hint.endsWith("/") || hint.endsWith("\\")) {
            return null;
        }
        hint = hint.replace('\\', '/').replaceAll("\\s", " ").replaceAll("[:\"<>*?|]", "_");
        int index = hint.lastIndexOf(47) + 1;
        if (index > 0) {
            return hint.substring(index);
        }
        return hint;
    }
}
