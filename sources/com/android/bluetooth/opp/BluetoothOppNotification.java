package com.android.bluetooth.opp;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import com.android.bluetooth.C0000R;
import java.util.HashMap;

class BluetoothOppNotification {
    private static final int NOTIFICATION_ID_INBOUND = -1000006;
    private static final int NOTIFICATION_ID_OUTBOUND = -1000005;
    private static final int NOTIFY = 0;
    private static final String TAG = "BluetoothOppNotification";
    /* renamed from: V */
    private static final boolean f40V = false;
    static final String WHERE_COMPLETED = "status >= '200' AND (visibility IS NULL OR visibility == '0') AND (confirm != '5')";
    private static final String WHERE_COMPLETED_INBOUND = "status >= '200' AND (visibility IS NULL OR visibility == '0') AND (confirm != '5') AND (direction == 1)";
    private static final String WHERE_COMPLETED_OUTBOUND = "status >= '200' AND (visibility IS NULL OR visibility == '0') AND (confirm != '5') AND (direction == 0)";
    static final String WHERE_CONFIRM_PENDING = "confirm == '0' AND (visibility IS NULL OR visibility == '0')";
    static final String WHERE_RUNNING = "(status == '192') AND (visibility IS NULL OR visibility == '0') AND (confirm == '1' OR confirm == '2' OR confirm == '5')";
    static final String confirm = "(confirm == '1' OR confirm == '2' OR confirm == '5')";
    static final String not_through_handover = "(confirm != '5')";
    static final String status = "(status == '192')";
    static final String visible = "(visibility IS NULL OR visibility == '0')";
    private int mActiveNotificationId = 0;
    private Context mContext;
    private Handler mHandler = new C00421();
    public NotificationManager mNotificationMgr;
    private HashMap<String, NotificationItem> mNotifications;
    private int mPendingUpdate = 0;
    private boolean mUpdateCompleteNotification = true;
    private NotificationUpdateThread mUpdateNotificationThread;

    /* renamed from: com.android.bluetooth.opp.BluetoothOppNotification$1 */
    class C00421 extends Handler {
        C00421() {
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    synchronized (BluetoothOppNotification.this) {
                        if (BluetoothOppNotification.this.mPendingUpdate > 0 && BluetoothOppNotification.this.mUpdateNotificationThread == null) {
                            BluetoothOppNotification.this.mUpdateNotificationThread = new NotificationUpdateThread();
                            BluetoothOppNotification.this.mUpdateNotificationThread.start();
                            BluetoothOppNotification.this.mHandler.sendMessageDelayed(BluetoothOppNotification.this.mHandler.obtainMessage(0), 1000);
                        } else if (BluetoothOppNotification.this.mPendingUpdate > 0) {
                            BluetoothOppNotification.this.mHandler.sendMessageDelayed(BluetoothOppNotification.this.mHandler.obtainMessage(0), 1000);
                        }
                    }
                    return;
                default:
                    return;
            }
        }
    }

    static class NotificationItem {
        String description;
        String destination;
        int direction;
        boolean handoverInitiated = BluetoothOppNotification.f40V;
        int id;
        long timeStamp = 0;
        int totalCurrent = 0;
        int totalTotal = 0;

        NotificationItem() {
        }
    }

    private class NotificationUpdateThread extends Thread {
        public NotificationUpdateThread() {
            super("Notification Update Thread");
        }

        public void run() {
            Process.setThreadPriority(10);
            synchronized (BluetoothOppNotification.this) {
                if (BluetoothOppNotification.this.mUpdateNotificationThread != this) {
                    throw new IllegalStateException("multiple UpdateThreads in BluetoothOppNotification");
                }
                BluetoothOppNotification.this.mPendingUpdate = 0;
            }
            BluetoothOppNotification.this.updateActiveNotification();
            BluetoothOppNotification.this.updateCompletedNotification();
            BluetoothOppNotification.this.updateIncomingFileConfirmNotification();
            synchronized (BluetoothOppNotification.this) {
                BluetoothOppNotification.this.mUpdateNotificationThread = null;
            }
        }
    }

    BluetoothOppNotification(Context ctx) {
        this.mContext = ctx;
        this.mNotificationMgr = (NotificationManager) this.mContext.getSystemService("notification");
        this.mNotifications = new HashMap();
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    public void updateNotification() {
        synchronized (this) {
            this.mPendingUpdate++;
            if (this.mPendingUpdate > 1) {
            } else if (!this.mHandler.hasMessages(0)) {
                this.mHandler.sendMessage(this.mHandler.obtainMessage(0));
            }
        }
    }

    private void updateActiveNotification() {
        Cursor cursor = this.mContext.getContentResolver().query(BluetoothShare.CONTENT_URI, null, WHERE_RUNNING, null, "_id");
        if (cursor != null) {
            NotificationItem item;
            if (cursor.getCount() > 0) {
                this.mUpdateCompleteNotification = f40V;
            } else {
                this.mUpdateCompleteNotification = true;
            }
            int timestampIndex = cursor.getColumnIndexOrThrow("timestamp");
            int directionIndex = cursor.getColumnIndexOrThrow(BluetoothShare.DIRECTION);
            int idIndex = cursor.getColumnIndexOrThrow("_id");
            int totalBytesIndex = cursor.getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES);
            int currentBytesIndex = cursor.getColumnIndexOrThrow(BluetoothShare.CURRENT_BYTES);
            int dataIndex = cursor.getColumnIndexOrThrow(BluetoothShare._DATA);
            int filenameHintIndex = cursor.getColumnIndexOrThrow(BluetoothShare.FILENAME_HINT);
            int confirmIndex = cursor.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION);
            int destinationIndex = cursor.getColumnIndexOrThrow(BluetoothShare.DESTINATION);
            this.mNotifications.clear();
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                long timeStamp = cursor.getLong(timestampIndex);
                int dir = cursor.getInt(directionIndex);
                int id = cursor.getInt(idIndex);
                int total = cursor.getInt(totalBytesIndex);
                int current = cursor.getInt(currentBytesIndex);
                int confirmation = cursor.getInt(confirmIndex);
                String destination = cursor.getString(destinationIndex);
                String fileName = cursor.getString(dataIndex);
                if (fileName == null) {
                    fileName = cursor.getString(filenameHintIndex);
                }
                if (fileName == null) {
                    fileName = this.mContext.getString(C0000R.string.unknown_file);
                }
                String batchID = Long.toString(timeStamp);
                if (!this.mNotifications.containsKey(batchID)) {
                    item = new NotificationItem();
                    item.timeStamp = timeStamp;
                    item.id = id;
                    item.direction = dir;
                    if (item.direction == 0) {
                        item.description = this.mContext.getString(C0000R.string.notification_sending, new Object[]{fileName});
                    } else if (item.direction == 1) {
                        item.description = this.mContext.getString(C0000R.string.notification_receiving, new Object[]{fileName});
                    }
                    item.totalCurrent = current;
                    item.totalTotal = total;
                    item.handoverInitiated = confirmation == 5 ? true : f40V;
                    item.destination = destination;
                    this.mNotifications.put(batchID, item);
                }
                cursor.moveToNext();
            }
            cursor.close();
            for (NotificationItem item2 : this.mNotifications.values()) {
                Intent intent;
                if (item2.handoverInitiated) {
                    float progress;
                    if (item2.totalTotal == -1) {
                        progress = -1.0f;
                    } else {
                        progress = ((float) item2.totalCurrent) / ((float) item2.totalTotal);
                    }
                    intent = new Intent(Constants.ACTION_BT_OPP_TRANSFER_PROGRESS);
                    if (item2.direction == 1) {
                        intent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_DIRECTION, 0);
                    } else {
                        intent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_DIRECTION, 1);
                    }
                    intent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_ID, item2.id);
                    intent.putExtra(Constants.EXTRA_BT_OPP_TRANSFER_PROGRESS, progress);
                    intent.putExtra(Constants.EXTRA_BT_OPP_ADDRESS, item2.destination);
                    this.mContext.sendBroadcast(intent, Constants.HANDOVER_STATUS_PERMISSION);
                } else {
                    Builder b = new Builder(this.mContext);
                    b.setColor(this.mContext.getResources().getColor(17170521));
                    b.setContentTitle(item2.description);
                    b.setContentInfo(BluetoothOppUtility.formatProgressText((long) item2.totalTotal, (long) item2.totalCurrent));
                    b.setProgress(item2.totalTotal, item2.totalCurrent, item2.totalTotal == -1 ? true : f40V);
                    b.setWhen(item2.timeStamp);
                    if (item2.direction == 0) {
                        b.setSmallIcon(17301640);
                    } else if (item2.direction == 1) {
                        b.setSmallIcon(17301633);
                    }
                    b.setOngoing(true);
                    intent = new Intent(Constants.ACTION_LIST);
                    intent.setClassName("com.android.bluetooth", BluetoothOppReceiver.class.getName());
                    intent.setDataAndNormalize(Uri.parse(BluetoothShare.CONTENT_URI + "/" + item2.id));
                    b.setContentIntent(PendingIntent.getBroadcast(this.mContext, 0, intent, 0));
                    this.mNotificationMgr.notify(item2.id, b.getNotification());
                    this.mActiveNotificationId = item2.id;
                }
            }
        }
    }

    private void updateCompletedNotification() {
        long timeStamp = 0;
        int outboundSuccNumber = 0;
        int outboundFailNumber = 0;
        int inboundSuccNumber = 0;
        int inboundFailNumber = 0;
        if (this.mUpdateCompleteNotification) {
            if (!(this.mNotificationMgr == null || this.mActiveNotificationId == 0)) {
                this.mNotificationMgr.cancel(this.mActiveNotificationId);
            }
            Cursor cursor = this.mContext.getContentResolver().query(BluetoothShare.CONTENT_URI, null, WHERE_COMPLETED_OUTBOUND, null, "timestamp DESC");
            if (cursor != null) {
                String title;
                String caption;
                Intent intent;
                int timestampIndex = cursor.getColumnIndexOrThrow("timestamp");
                int statusIndex = cursor.getColumnIndexOrThrow(BluetoothShare.STATUS);
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    if (cursor.isFirst()) {
                        timeStamp = cursor.getLong(timestampIndex);
                    }
                    if (BluetoothShare.isStatusError(cursor.getInt(statusIndex))) {
                        outboundFailNumber++;
                    } else {
                        outboundSuccNumber++;
                    }
                    cursor.moveToNext();
                }
                cursor.close();
                if (outboundSuccNumber + outboundFailNumber > 0) {
                    Notification outNoti = new Notification();
                    outNoti.icon = 17301641;
                    title = this.mContext.getString(C0000R.string.outbound_noti_title);
                    caption = this.mContext.getString(C0000R.string.noti_caption, new Object[]{Integer.valueOf(outboundSuccNumber), Integer.valueOf(outboundFailNumber)});
                    intent = new Intent(Constants.ACTION_OPEN_OUTBOUND_TRANSFER);
                    intent.setClassName("com.android.bluetooth", BluetoothOppReceiver.class.getName());
                    outNoti.color = this.mContext.getResources().getColor(17170521);
                    outNoti.setLatestEventInfo(this.mContext, title, caption, PendingIntent.getBroadcast(this.mContext, 0, intent, 0));
                    intent = new Intent(Constants.ACTION_COMPLETE_HIDE);
                    intent.setClassName("com.android.bluetooth", BluetoothOppReceiver.class.getName());
                    outNoti.deleteIntent = PendingIntent.getBroadcast(this.mContext, 0, intent, 0);
                    outNoti.when = timeStamp;
                    this.mNotificationMgr.notify(NOTIFICATION_ID_OUTBOUND, outNoti);
                } else if (this.mNotificationMgr != null) {
                    this.mNotificationMgr.cancel(NOTIFICATION_ID_OUTBOUND);
                }
                cursor = this.mContext.getContentResolver().query(BluetoothShare.CONTENT_URI, null, WHERE_COMPLETED_INBOUND, null, "timestamp DESC");
                if (cursor != null) {
                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        if (cursor.isFirst()) {
                            timeStamp = cursor.getLong(timestampIndex);
                        }
                        if (BluetoothShare.isStatusError(cursor.getInt(statusIndex))) {
                            inboundFailNumber++;
                        } else {
                            inboundSuccNumber++;
                        }
                        cursor.moveToNext();
                    }
                    cursor.close();
                    if (inboundSuccNumber + inboundFailNumber > 0) {
                        Notification inNoti = new Notification();
                        inNoti.icon = 17301634;
                        title = this.mContext.getString(C0000R.string.inbound_noti_title);
                        caption = this.mContext.getString(C0000R.string.noti_caption, new Object[]{Integer.valueOf(inboundSuccNumber), Integer.valueOf(inboundFailNumber)});
                        intent = new Intent(Constants.ACTION_OPEN_INBOUND_TRANSFER);
                        intent.setClassName("com.android.bluetooth", BluetoothOppReceiver.class.getName());
                        inNoti.color = this.mContext.getResources().getColor(17170521);
                        inNoti.setLatestEventInfo(this.mContext, title, caption, PendingIntent.getBroadcast(this.mContext, 0, intent, 0));
                        intent = new Intent(Constants.ACTION_COMPLETE_HIDE);
                        intent.setClassName("com.android.bluetooth", BluetoothOppReceiver.class.getName());
                        inNoti.deleteIntent = PendingIntent.getBroadcast(this.mContext, 0, intent, 0);
                        inNoti.when = timeStamp;
                        this.mNotificationMgr.notify(NOTIFICATION_ID_INBOUND, inNoti);
                    } else if (this.mNotificationMgr != null) {
                        this.mNotificationMgr.cancel(NOTIFICATION_ID_INBOUND);
                    }
                }
            }
        }
    }

    private void updateIncomingFileConfirmNotification() {
        Cursor cursor = this.mContext.getContentResolver().query(BluetoothShare.CONTENT_URI, null, WHERE_CONFIRM_PENDING, null, "_id");
        if (cursor != null) {
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                CharSequence title = this.mContext.getText(C0000R.string.incoming_file_confirm_Notification_title);
                CharSequence caption = this.mContext.getText(C0000R.string.incoming_file_confirm_Notification_caption);
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("_id"));
                long timeStamp = cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + id);
                Notification n = new Notification();
                n.icon = C0000R.drawable.bt_incomming_file_notification;
                n.flags |= 8;
                n.flags |= 2;
                n.defaults = 1;
                n.tickerText = title;
                Intent intent = new Intent(Constants.ACTION_INCOMING_FILE_CONFIRM);
                intent.setClassName("com.android.bluetooth", BluetoothOppReceiver.class.getName());
                intent.setDataAndNormalize(contentUri);
                n.when = timeStamp;
                n.color = this.mContext.getResources().getColor(17170521);
                n.setLatestEventInfo(this.mContext, title, caption, PendingIntent.getBroadcast(this.mContext, 0, intent, 0));
                intent = new Intent(Constants.ACTION_HIDE);
                intent.setClassName("com.android.bluetooth", BluetoothOppReceiver.class.getName());
                intent.setDataAndNormalize(contentUri);
                n.deleteIntent = PendingIntent.getBroadcast(this.mContext, 0, intent, 0);
                this.mNotificationMgr.notify(id, n);
                cursor.moveToNext();
            }
            cursor.close();
        }
    }
}
