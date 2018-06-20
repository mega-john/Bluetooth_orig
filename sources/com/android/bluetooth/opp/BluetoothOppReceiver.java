package com.android.bluetooth.opp;

import android.app.NotificationManager;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;
import android.widget.Toast;
import com.android.bluetooth.C0000R;
import com.android.vcard.VCardConfig;

public class BluetoothOppReceiver extends BroadcastReceiver {
    /* renamed from: D */
    private static final boolean f50D = true;
    private static final String TAG = "BluetoothOppReceiver";
    /* renamed from: V */
    private static final boolean f51V = false;

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals("android.bluetooth.adapter.action.STATE_CHANGED")) {
            if (12 == intent.getIntExtra("android.bluetooth.adapter.extra.STATE", Integer.MIN_VALUE)) {
                context.startService(new Intent(context, BluetoothOppService.class));
                synchronized (this) {
                    if (BluetoothOppManager.getInstance(context).mSendingFlag) {
                        BluetoothOppManager.getInstance(context).mSendingFlag = false;
                        Intent in1 = new Intent("android.bluetooth.devicepicker.action.LAUNCH");
                        in1.putExtra("android.bluetooth.devicepicker.extra.NEED_AUTH", false);
                        in1.putExtra("android.bluetooth.devicepicker.extra.FILTER_TYPE", 2);
                        in1.putExtra("android.bluetooth.devicepicker.extra.LAUNCH_PACKAGE", "com.android.bluetooth");
                        in1.putExtra("android.bluetooth.devicepicker.extra.DEVICE_PICKER_LAUNCH_CLASS", BluetoothOppReceiver.class.getName());
                        in1.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
                        context.startActivity(in1);
                    }
                }
            }
        } else if (action.equals("android.bluetooth.devicepicker.action.DEVICE_SELECTED")) {
            BluetoothOppManager mOppManager = BluetoothOppManager.getInstance(context);
            BluetoothDevice remoteDevice = (BluetoothDevice) intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE");
            mOppManager.startTransfer(remoteDevice);
            String deviceName = mOppManager.getDeviceName(remoteDevice);
            int batchSize = mOppManager.getBatchSize();
            if (mOppManager.mMultipleFlag) {
                toastMsg = context.getString(C0000R.string.bt_toast_5, new Object[]{Integer.toString(batchSize), deviceName});
            } else {
                toastMsg = context.getString(C0000R.string.bt_toast_4, new Object[]{deviceName});
            }
            Toast.makeText(context, toastMsg, 0).show();
        } else if (action.equals(Constants.ACTION_INCOMING_FILE_CONFIRM)) {
            uri = intent.getData();
            in = new Intent(context, BluetoothOppIncomingFileConfirmActivity.class);
            in.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
            in.setDataAndNormalize(uri);
            context.startActivity(in);
            notMgr = (NotificationManager) context.getSystemService("notification");
            if (notMgr != null) {
                notMgr.cancel((int) ContentUris.parseId(intent.getData()));
            }
        } else if (action.equals(BluetoothShare.INCOMING_FILE_CONFIRMATION_REQUEST_ACTION)) {
            Toast.makeText(context, context.getString(C0000R.string.incoming_file_toast_msg), 0).show();
        } else if (action.equals(Constants.ACTION_OPEN) || action.equals(Constants.ACTION_LIST)) {
            transInfo = new BluetoothOppTransferInfo();
            uri = intent.getData();
            transInfo = BluetoothOppUtility.queryRecord(context, uri);
            if (transInfo == null) {
                Log.e(TAG, "Error: Can not get data from db");
                return;
            }
            if (transInfo.mDirection == 1 && BluetoothShare.isStatusSuccess(transInfo.mStatus)) {
                BluetoothOppUtility.openReceivedFile(context, transInfo.mFileName, transInfo.mFileType, transInfo.mTimeStamp, uri);
                BluetoothOppUtility.updateVisibilityToHidden(context, uri);
            } else {
                in = new Intent(context, BluetoothOppTransferActivity.class);
                in.setFlags(VCardConfig.FLAG_REFRAIN_QP_TO_NAME_PROPERTIES);
                in.setDataAndNormalize(uri);
                context.startActivity(in);
            }
            notMgr = (NotificationManager) context.getSystemService("notification");
            if (notMgr != null) {
                notMgr.cancel((int) ContentUris.parseId(intent.getData()));
            }
        } else if (action.equals(Constants.ACTION_OPEN_OUTBOUND_TRANSFER)) {
            in = new Intent(context, BluetoothOppTransferHistory.class);
            in.setFlags(335544320);
            in.putExtra(BluetoothShare.DIRECTION, 0);
            context.startActivity(in);
        } else if (action.equals(Constants.ACTION_OPEN_INBOUND_TRANSFER)) {
            in = new Intent(context, BluetoothOppTransferHistory.class);
            in.setFlags(335544320);
            in.putExtra(BluetoothShare.DIRECTION, 1);
            context.startActivity(in);
        } else if (action.equals(Constants.ACTION_OPEN_RECEIVED_FILES)) {
            in = new Intent(context, BluetoothOppTransferHistory.class);
            in.setFlags(335544320);
            in.putExtra(BluetoothShare.DIRECTION, 1);
            in.putExtra(Constants.EXTRA_SHOW_ALL_FILES, true);
            context.startActivity(in);
        } else if (action.equals(Constants.ACTION_HIDE)) {
            Cursor cursor = context.getContentResolver().query(intent.getData(), null, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.STATUS));
                    int visibility = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.VISIBILITY));
                    if (cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION)) == 0 && visibility == 0) {
                        ContentValues values = new ContentValues();
                        values.put(BluetoothShare.VISIBILITY, Integer.valueOf(1));
                        context.getContentResolver().update(intent.getData(), values, null, null);
                    }
                }
                cursor.close();
            }
        } else if (action.equals(Constants.ACTION_COMPLETE_HIDE)) {
            ContentValues updateValues = new ContentValues();
            updateValues.put(BluetoothShare.VISIBILITY, Integer.valueOf(1));
            context.getContentResolver().update(BluetoothShare.CONTENT_URI, updateValues, "status >= '200' AND (visibility IS NULL OR visibility == '0') AND (confirm != '5')", null);
        } else if (action.equals(BluetoothShare.TRANSFER_COMPLETED_ACTION)) {
            toastMsg = null;
            transInfo = new BluetoothOppTransferInfo();
            transInfo = BluetoothOppUtility.queryRecord(context, intent.getData());
            if (transInfo == null) {
                Log.e(TAG, "Error: Can not get data from db");
            } else if (transInfo.mHandoverInitiated) {
                Intent handoverIntent = new Intent(Constants.ACTION_BT_OPP_TRANSFER_DONE);
                if (transInfo.mDirection == 1) {
                    handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_DIRECTION, 0);
                } else {
                    handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_DIRECTION, 1);
                }
                handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_ID, transInfo.mID);
                handoverIntent.putExtra(Constants.EXTRA_BT_OPP_ADDRESS, transInfo.mDestAddr);
                if (BluetoothShare.isStatusSuccess(transInfo.mStatus)) {
                    handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_STATUS, 0);
                    handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_URI, transInfo.mFileName);
                    handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_MIMETYPE, transInfo.mFileType);
                } else {
                    handoverIntent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_STATUS, 1);
                }
                context.sendBroadcast(handoverIntent, Constants.HANDOVER_STATUS_PERMISSION);
            } else {
                if (BluetoothShare.isStatusSuccess(transInfo.mStatus)) {
                    if (transInfo.mDirection == 0) {
                        toastMsg = context.getString(C0000R.string.notification_sent, new Object[]{transInfo.mFileName});
                    } else if (transInfo.mDirection == 1) {
                        toastMsg = context.getString(C0000R.string.notification_received, new Object[]{transInfo.mFileName});
                    }
                } else if (BluetoothShare.isStatusError(transInfo.mStatus)) {
                    if (transInfo.mDirection == 0) {
                        toastMsg = context.getString(C0000R.string.notification_sent_fail, new Object[]{transInfo.mFileName});
                    } else if (transInfo.mDirection == 1) {
                        toastMsg = context.getString(C0000R.string.download_fail_line1);
                    }
                }
                if (toastMsg != null) {
                    Toast.makeText(context, toastMsg, 0).show();
                }
            }
        }
    }
}
