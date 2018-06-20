package com.android.bluetooth.opp;

import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import com.android.bluetooth.C0000R;
import com.android.vcard.VCardConfig;
import com.google.android.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class BluetoothOppUtility {
    /* renamed from: D */
    private static final boolean f62D = true;
    private static final String TAG = "BluetoothOppUtility";
    /* renamed from: V */
    private static final boolean f63V = false;
    private static final ConcurrentHashMap<Uri, BluetoothOppSendFileInfo> sSendFileMap = new ConcurrentHashMap();

    public static BluetoothOppTransferInfo queryRecord(Context context, Uri uri) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothOppTransferInfo info = new BluetoothOppTransferInfo();
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor == null) {
            return null;
        }
        if (cursor.moveToFirst()) {
            info.mID = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
            info.mStatus = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.STATUS));
            info.mDirection = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.DIRECTION));
            info.mTotalBytes = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES));
            info.mCurrentBytes = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.CURRENT_BYTES));
            info.mTimeStamp = Long.valueOf(cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")));
            info.mDestAddr = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.DESTINATION));
            info.mFileName = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare._DATA));
            if (info.mFileName == null) {
                info.mFileName = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.FILENAME_HINT));
            }
            if (info.mFileName == null) {
                info.mFileName = context.getString(C0000R.string.unknown_file);
            }
            info.mFileUri = cursor.getString(cursor.getColumnIndexOrThrow("uri"));
            if (info.mFileUri != null) {
                info.mFileType = context.getContentResolver().getType(originalUri(Uri.parse(info.mFileUri)));
            } else {
                info.mFileType = context.getContentResolver().getType(Uri.parse(info.mFileName));
            }
            if (info.mFileType == null) {
                info.mFileType = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.MIMETYPE));
            }
            info.mDeviceName = BluetoothOppManager.getInstance(context).getDeviceName(adapter.getRemoteDevice(info.mDestAddr));
            info.mHandoverInitiated = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION)) == 5;
        }
        cursor.close();
        return info;
    }

    public static ArrayList<String> queryTransfersInBatch(Context context, Long timeStamp) {
        ArrayList<String> uris = Lists.newArrayList();
        String WHERE = "timestamp == " + timeStamp;
        Cursor metadataCursor = context.getContentResolver().query(BluetoothShare.CONTENT_URI, new String[]{BluetoothShare._DATA}, WHERE, null, "_id");
        if (metadataCursor == null) {
            return null;
        }
        metadataCursor.moveToFirst();
        while (!metadataCursor.isAfterLast()) {
            String fileName = metadataCursor.getString(0);
            Uri path = Uri.parse(fileName);
            if (path.getScheme() == null) {
                path = Uri.fromFile(new File(fileName));
            }
            uris.add(path.toString());
            metadataCursor.moveToNext();
        }
        metadataCursor.close();
        return uris;
    }

    public static void openReceivedFile(Context context, String fileName, String mimetype, Long timeStamp, Uri uri) {
        if (fileName == null || mimetype == null) {
            Log.e(TAG, "ERROR: Para fileName ==null, or mimetype == null");
        } else if (new File(fileName).exists()) {
            Uri path = Uri.parse(fileName);
            if (path.getScheme() == null) {
                path = Uri.fromFile(new File(fileName));
            }
            if (isRecognizedFileType(context, path, mimetype)) {
                Intent activityIntent = new Intent("android.intent.action.VIEW");
                activityIntent.setDataAndTypeAndNormalize(path, mimetype);
                activityIntent.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
                try {
                    context.startActivity(activityIntent);
                    return;
                } catch (ActivityNotFoundException e) {
                    return;
                }
            }
            in = new Intent(context, BluetoothOppBtErrorActivity.class);
            in.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
            in.putExtra("title", context.getString(C0000R.string.unknown_file));
            in.putExtra("content", context.getString(C0000R.string.unknown_file_desc));
            context.startActivity(in);
        } else {
            in = new Intent(context, BluetoothOppBtErrorActivity.class);
            in.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
            in.putExtra("title", context.getString(C0000R.string.not_exist_file));
            in.putExtra("content", context.getString(C0000R.string.not_exist_file_desc));
            context.startActivity(in);
            context.getContentResolver().delete(uri, null, null);
        }
    }

    public static boolean isRecognizedFileType(Context context, Uri fileUri, String mimetype) {
        Log.d(TAG, "RecognizedFileType() fileUri: " + fileUri + " mimetype: " + mimetype);
        Intent mimetypeIntent = new Intent("android.intent.action.VIEW");
        mimetypeIntent.setDataAndTypeAndNormalize(fileUri, mimetype);
        if (context.getPackageManager().queryIntentActivities(mimetypeIntent, 65536).size() != 0) {
            return true;
        }
        Log.d(TAG, "NO application to handle MIME type " + mimetype);
        return false;
    }

    public static void updateVisibilityToHidden(Context context, Uri uri) {
        ContentValues updateValues = new ContentValues();
        updateValues.put(BluetoothShare.VISIBILITY, Integer.valueOf(1));
        context.getContentResolver().update(uri, updateValues, null, null);
    }

    public static String formatProgressText(long totalBytes, long currentBytes) {
        if (totalBytes <= 0) {
            return "0%";
        }
        long progress = (100 * currentBytes) / totalBytes;
        StringBuilder sb = new StringBuilder();
        sb.append(progress);
        sb.append('%');
        return sb.toString();
    }

    public static String getStatusDescription(Context context, int statusCode, String deviceName) {
        if (statusCode == BluetoothShare.STATUS_PENDING) {
            return context.getString(C0000R.string.status_pending);
        }
        if (statusCode == BluetoothShare.STATUS_RUNNING) {
            return context.getString(C0000R.string.status_running);
        }
        if (statusCode == BluetoothShare.STATUS_SUCCESS) {
            return context.getString(C0000R.string.status_success);
        }
        if (statusCode == BluetoothShare.STATUS_NOT_ACCEPTABLE) {
            return context.getString(C0000R.string.status_not_accept);
        }
        if (statusCode == BluetoothShare.STATUS_FORBIDDEN) {
            return context.getString(C0000R.string.status_forbidden);
        }
        if (statusCode == BluetoothShare.STATUS_CANCELED) {
            return context.getString(C0000R.string.status_canceled);
        }
        if (statusCode == BluetoothShare.STATUS_FILE_ERROR) {
            return context.getString(C0000R.string.status_file_error);
        }
        if (statusCode == BluetoothShare.STATUS_ERROR_NO_SDCARD) {
            return context.getString(C0000R.string.status_no_sd_card);
        }
        if (statusCode == BluetoothShare.STATUS_CONNECTION_ERROR) {
            return context.getString(C0000R.string.status_connection_error);
        }
        if (statusCode == BluetoothShare.STATUS_ERROR_SDCARD_FULL) {
            return context.getString(C0000R.string.bt_sm_2_1, new Object[]{deviceName});
        } else if (statusCode == BluetoothShare.STATUS_BAD_REQUEST || statusCode == BluetoothShare.STATUS_LENGTH_REQUIRED || statusCode == BluetoothShare.STATUS_PRECONDITION_FAILED || statusCode == BluetoothShare.STATUS_UNHANDLED_OBEX_CODE || statusCode == BluetoothShare.STATUS_OBEX_DATA_ERROR) {
            return context.getString(C0000R.string.status_protocol_error);
        } else {
            return context.getString(C0000R.string.status_unknown_error);
        }
    }

    public static void retryTransfer(Context context, BluetoothOppTransferInfo transInfo) {
        ContentValues values = new ContentValues();
        values.put("uri", transInfo.mFileUri);
        values.put(BluetoothShare.MIMETYPE, transInfo.mFileType);
        values.put(BluetoothShare.DESTINATION, transInfo.mDestAddr);
        Uri contentUri = context.getContentResolver().insert(BluetoothShare.CONTENT_URI, values);
    }

    static Uri originalUri(Uri uri) {
        String mUri = uri.toString();
        int atIndex = mUri.lastIndexOf("@");
        if (atIndex != -1) {
            return Uri.parse(mUri.substring(0, atIndex));
        }
        return uri;
    }

    static Uri generateUri(Uri uri, BluetoothOppSendFileInfo sendFileInfo) {
        String fileInfo = sendFileInfo.toString();
        return Uri.parse(uri + fileInfo.substring(fileInfo.lastIndexOf("@")));
    }

    static void putSendFileInfo(Uri uri, BluetoothOppSendFileInfo sendFileInfo) {
        Log.d(TAG, "putSendFileInfo: uri=" + uri + " sendFileInfo=" + sendFileInfo);
        sSendFileMap.put(uri, sendFileInfo);
    }

    static BluetoothOppSendFileInfo getSendFileInfo(Uri uri) {
        Log.d(TAG, "getSendFileInfo: uri=" + uri);
        BluetoothOppSendFileInfo info = (BluetoothOppSendFileInfo) sSendFileMap.get(uri);
        return info != null ? info : BluetoothOppSendFileInfo.SEND_FILE_INFO_ERROR;
    }

    static void closeSendFileInfo(Uri uri) {
        Log.d(TAG, "closeSendFileInfo: uri=" + uri);
        BluetoothOppSendFileInfo info = (BluetoothOppSendFileInfo) sSendFileMap.remove(uri);
        if (info != null && info.mInputStream != null) {
            try {
                info.mInputStream.close();
            } catch (IOException e) {
            }
        }
    }
}
