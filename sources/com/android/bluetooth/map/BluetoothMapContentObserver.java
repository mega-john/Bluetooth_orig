package com.android.bluetooth.map;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Sms.Inbox;
import android.provider.Telephony.Threads;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Xml;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.bluetooth.map.BluetoothMapbMessage.vCard;
import com.android.bluetooth.map.BluetoothMapbMessageMms.MimePart;
import com.android.bluetooth.opp.BluetoothShare;
import com.android.vcard.VCardBuilder;
import com.android.vcard.VCardConfig;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.xmlpull.v1.XmlSerializer;

public class BluetoothMapContentObserver {
    private static final String ACTION_MESSAGE_DELIVERY = "com.android.bluetooth.BluetoothMapContentObserver.action.MESSAGE_DELIVERY";
    public static final String ACTION_MESSAGE_SENT = "com.android.bluetooth.BluetoothMapContentObserver.action.MESSAGE_SENT";
    private static final int CONVERT_MMS_TO_SMS_PART_COUNT = 10;
    /* renamed from: D */
    private static final boolean f2D = true;
    public static final int DELETED_THREAD_ID = -1;
    static final String[] EMAIL_PROJECTION_SHORT = new String[]{"_id", "folder_id", "flag_read"};
    private static final String EVENT_TYPE_DELETE = "MessageDeleted";
    private static final String EVENT_TYPE_DELEVERY_SUCCESS = "DeliverySuccess";
    private static final String EVENT_TYPE_DELIVERY_FAILURE = "DeliveryFailure";
    private static final String EVENT_TYPE_NEW = "NewMessage";
    private static final String EVENT_TYPE_SENDING_FAILURE = "SendingFailure";
    private static final String EVENT_TYPE_SENDING_SUCCESS = "SendingSuccess";
    private static final String EVENT_TYPE_SHIFT = "MessageShift";
    public static final String EXTRA_MESSAGE_SENT_HANDLE = "HANDLE";
    public static final String EXTRA_MESSAGE_SENT_RESULT = "result";
    public static final String EXTRA_MESSAGE_SENT_RETRY = "retry";
    public static final String EXTRA_MESSAGE_SENT_TIMESTAMP = "timestamp";
    public static final String EXTRA_MESSAGE_SENT_TRANSPARENT = "transparent";
    public static final String EXTRA_MESSAGE_SENT_URI = "uri";
    public static final int MESSAGE_TYPE_RETRIEVE_CONF = 132;
    static final String[] MMS_PROJECTION_SHORT = new String[]{"_id", "thread_id", "m_type", "msg_box"};
    private static final long PROVIDER_ANR_TIMEOUT = 20000;
    static final String[] SMS_PROJECTION = new String[]{"_id", "thread_id", "address", "body", "date", "read", "type", BluetoothShare.STATUS, "locked", "error_code"};
    static final String[] SMS_PROJECTION_SHORT = new String[]{"_id", "thread_id", "type"};
    private static final String TAG = "BluetoothMapContentObserver";
    /* renamed from: V */
    private static final boolean f3V = false;
    private static final String[] folderMms = new String[]{"", "inbox", "sent", "draft", "outbox"};
    private static final String[] folderSms = new String[]{"", "inbox", "sent", "draft", "outbox", "outbox", "outbox", "inbox", "inbox"};
    private BluetoothMapEmailSettingsItem mAccount;
    private String mAuthority = null;
    private Context mContext;
    private boolean mEnableSmsMms = false;
    private BluetoothMapFolderElement mFolders = new BluetoothMapFolderElement("DUMMY", null);
    private boolean mInitialized = false;
    private int mMasId;
    private BluetoothMapMasInstance mMasInstance = null;
    private Uri mMessageUri = null;
    private BluetoothMnsObexClient mMnsClient;
    private Map<Long, Msg> mMsgListEmail = new HashMap();
    private Map<Long, Msg> mMsgListMms = new HashMap();
    private Map<Long, Msg> mMsgListSms = new HashMap();
    private final ContentObserver mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        public void onChange(boolean selfChange, Uri uri) {
            BluetoothMapContentObserver.this.handleMsgListChanges(uri);
        }
    };
    private boolean mObserverRegistered = false;
    private PhoneStateListener mPhoneListener = new C00232();
    private ContentProviderClient mProviderClient = null;
    private Map<Long, PushMsgInfo> mPushMsgList = Collections.synchronizedMap(new HashMap());
    private ContentResolver mResolver;
    private SmsBroadcastReceiver mSmsBroadcastReceiver = new SmsBroadcastReceiver();
    private TYPE mSmsType;

    /* renamed from: com.android.bluetooth.map.BluetoothMapContentObserver$2 */
    class C00232 extends PhoneStateListener {
        C00232() {
        }

        public void onServiceStateChanged(ServiceState serviceState) {
            Log.d(BluetoothMapContentObserver.TAG, "Phone service state change: " + serviceState.getState());
            if (serviceState.getState() == 0) {
                BluetoothMapContentObserver.this.resendPendingMessages();
            }
        }
    }

    private class Event {
        static final String PATH = "telecom/msg/";
        String eventType;
        String folder;
        long handle;
        TYPE msgType;
        String oldFolder;

        public Event(String eventType, long handle, String folder, String oldFolder, TYPE msgType) {
            this.eventType = eventType;
            this.handle = handle;
            if (folder == null) {
                this.folder = null;
            } else if (msgType == TYPE.EMAIL) {
                this.folder = folder;
            } else {
                this.folder = PATH + folder;
            }
            if (oldFolder == null) {
                this.oldFolder = null;
            } else if (msgType == TYPE.EMAIL) {
                this.oldFolder = oldFolder;
            } else {
                this.oldFolder = PATH + oldFolder;
            }
            this.msgType = msgType;
        }

        public byte[] encode() throws UnsupportedEncodingException {
            StringWriter sw = new StringWriter();
            XmlSerializer xmlEvtReport = Xml.newSerializer();
            try {
                xmlEvtReport.setOutput(sw);
                xmlEvtReport.startDocument(null, null);
                xmlEvtReport.text(VCardBuilder.VCARD_END_OF_LINE);
                xmlEvtReport.startTag("", "MAP-event-report");
                xmlEvtReport.attribute("", "version", "1.0");
                xmlEvtReport.startTag("", "event");
                xmlEvtReport.attribute("", "type", this.eventType);
                xmlEvtReport.attribute("", "handle", BluetoothMapUtils.getMapHandle(this.handle, this.msgType));
                if (this.folder != null) {
                    xmlEvtReport.attribute("", "folder", this.folder);
                }
                if (this.oldFolder != null) {
                    xmlEvtReport.attribute("", "old_folder", this.oldFolder);
                }
                xmlEvtReport.attribute("", "msg_type", this.msgType.name());
                xmlEvtReport.endTag("", "event");
                xmlEvtReport.endTag("", "MAP-event-report");
                xmlEvtReport.endDocument();
            } catch (IllegalArgumentException e) {
                Log.w(BluetoothMapContentObserver.TAG, e);
            } catch (IllegalStateException e2) {
                Log.w(BluetoothMapContentObserver.TAG, e2);
            } catch (IOException e3) {
                Log.w(BluetoothMapContentObserver.TAG, e3);
            }
            return sw.toString().getBytes("UTF-8");
        }
    }

    private class Msg {
        long folderId = -1;
        long id;
        boolean localInitiatedSend = false;
        long oldFolderId = -1;
        int threadId;
        boolean transparent = false;
        int type;

        public Msg(long id, int type, int threadId) {
            this.id = id;
            this.type = type;
            this.threadId = threadId;
        }

        public Msg(long id, long folderId) {
            this.id = id;
            this.folderId = folderId;
        }

        public int hashCode() {
            return ((int) (this.id ^ (this.id >>> 32))) + 31;
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
            if (this.id != ((Msg) obj).id) {
                return false;
            }
            return true;
        }
    }

    private class PushMsgInfo {
        boolean failedSent = false;
        long id;
        int parts;
        int partsDelivered;
        int partsSent;
        String phone;
        boolean resend = false;
        int retry;
        boolean sendInProgress = false;
        int statusDelivered = 0;
        long timestamp = 0;
        int transparent;
        Uri uri;

        public PushMsgInfo(long id, int transparent, int retry, String phone, Uri uri) {
            this.id = id;
            this.transparent = transparent;
            this.retry = retry;
            this.phone = phone;
            this.uri = uri;
        }
    }

    private class SmsBroadcastReceiver extends BroadcastReceiver {
        private final String[] ID_PROJECTION;
        private final Uri UPDATE_STATUS_URI;

        private SmsBroadcastReceiver() {
            this.ID_PROJECTION = new String[]{"_id"};
            this.UPDATE_STATUS_URI = Uri.withAppendedPath(Sms.CONTENT_URI, "/status");
        }

        public void register() {
            Handler handler = new Handler(Looper.getMainLooper());
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothMapContentObserver.ACTION_MESSAGE_DELIVERY);
            try {
                intentFilter.addDataType("message/*");
            } catch (MalformedMimeTypeException e) {
                Log.e(BluetoothMapContentObserver.TAG, "Wrong mime type!!!", e);
            }
            BluetoothMapContentObserver.this.mContext.registerReceiver(this, intentFilter, null, handler);
        }

        public void unregister() {
            try {
                BluetoothMapContentObserver.this.mContext.unregisterReceiver(this);
            } catch (IllegalArgumentException e) {
            }
        }

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            long handle = intent.getLongExtra(BluetoothMapContentObserver.EXTRA_MESSAGE_SENT_HANDLE, -1);
            PushMsgInfo msgInfo = (PushMsgInfo) BluetoothMapContentObserver.this.mPushMsgList.get(Long.valueOf(handle));
            Log.d(BluetoothMapContentObserver.TAG, "onReceive: action" + action);
            if (msgInfo == null) {
                Log.d(BluetoothMapContentObserver.TAG, "onReceive: no msgInfo found for handle " + handle);
            } else if (action.equals(BluetoothMapContentObserver.ACTION_MESSAGE_SENT)) {
                int result = intent.getIntExtra(BluetoothMapContentObserver.EXTRA_MESSAGE_SENT_RESULT, 0);
                msgInfo.partsSent++;
                if (result != -1) {
                    msgInfo.failedSent = true;
                }
                Log.d(BluetoothMapContentObserver.TAG, "onReceive: msgInfo.partsSent = " + msgInfo.partsSent + ", msgInfo.parts = " + msgInfo.parts + " result = " + result);
                if (msgInfo.partsSent == msgInfo.parts) {
                    actionMessageSent(context, intent, msgInfo);
                }
            } else if (action.equals(BluetoothMapContentObserver.ACTION_MESSAGE_DELIVERY)) {
                if (msgInfo.timestamp == intent.getLongExtra("timestamp", 0)) {
                    msgInfo.partsDelivered++;
                    SmsMessage message = SmsMessage.createFromPdu(intent.getByteArrayExtra("pdu"), intent.getStringExtra("format"));
                    if (message == null) {
                        Log.d(BluetoothMapContentObserver.TAG, "actionMessageDelivery: Can't get message from pdu");
                        return;
                    }
                    int status = message.getStatus();
                    if (status != 0) {
                        msgInfo.statusDelivered = status;
                    }
                }
                if (msgInfo.partsDelivered == msgInfo.parts) {
                    actionMessageDelivery(context, intent, msgInfo);
                }
            } else {
                Log.d(BluetoothMapContentObserver.TAG, "onReceive: Unknown action " + action);
            }
        }

        private void actionMessageSent(Context context, Intent intent, PushMsgInfo msgInfo) {
            boolean delete = false;
            Log.d(BluetoothMapContentObserver.TAG, "actionMessageSent(): msgInfo.failedSent = " + msgInfo.failedSent);
            msgInfo.sendInProgress = false;
            if (!msgInfo.failedSent) {
                Log.d(BluetoothMapContentObserver.TAG, "actionMessageSent: result OK");
                if (msgInfo.transparent != 0) {
                    delete = true;
                } else if (!Sms.moveMessageToFolder(context, msgInfo.uri, 2, 0)) {
                    Log.w(BluetoothMapContentObserver.TAG, "Failed to move " + msgInfo.uri + " to SENT");
                }
                BluetoothMapContentObserver.this.sendEvent(new Event(BluetoothMapContentObserver.EVENT_TYPE_SENDING_SUCCESS, msgInfo.id, BluetoothMapContentObserver.folderSms[2], null, BluetoothMapContentObserver.this.mSmsType));
            } else if (msgInfo.retry == 1) {
                msgInfo.resend = true;
                msgInfo.partsSent = 0;
                msgInfo.failedSent = false;
                BluetoothMapContentObserver.this.sendEvent(new Event(BluetoothMapContentObserver.EVENT_TYPE_SENDING_FAILURE, msgInfo.id, BluetoothMapContentObserver.folderSms[4], null, BluetoothMapContentObserver.this.mSmsType));
            } else {
                if (msgInfo.transparent != 0) {
                    delete = true;
                } else if (!Sms.moveMessageToFolder(context, msgInfo.uri, 5, 0)) {
                    Log.w(BluetoothMapContentObserver.TAG, "Failed to move " + msgInfo.uri + " to FAILED");
                }
                BluetoothMapContentObserver.this.sendEvent(new Event(BluetoothMapContentObserver.EVENT_TYPE_SENDING_FAILURE, msgInfo.id, BluetoothMapContentObserver.folderSms[5], null, BluetoothMapContentObserver.this.mSmsType));
            }
            if (delete) {
                synchronized (BluetoothMapContentObserver.this.mMsgListSms) {
                    BluetoothMapContentObserver.this.mMsgListSms.remove(Long.valueOf(msgInfo.id));
                }
                BluetoothMapContentObserver.this.mResolver.delete(msgInfo.uri, null, null);
            }
        }

        private void actionMessageDelivery(Context context, Intent intent, PushMsgInfo msgInfo) {
            Uri messageUri = intent.getData();
            msgInfo.sendInProgress = false;
            Cursor cursor = BluetoothMapContentObserver.this.mResolver.query(msgInfo.uri, this.ID_PROJECTION, null, null, null);
            try {
                if (cursor.moveToFirst()) {
                    Uri updateUri = ContentUris.withAppendedId(this.UPDATE_STATUS_URI, (long) cursor.getInt(0));
                    Log.d(BluetoothMapContentObserver.TAG, "actionMessageDelivery: uri=" + messageUri + ", status=" + msgInfo.statusDelivered);
                    ContentValues contentValues = new ContentValues(2);
                    contentValues.put(BluetoothShare.STATUS, Integer.valueOf(msgInfo.statusDelivered));
                    contentValues.put("date_sent", Long.valueOf(System.currentTimeMillis()));
                    BluetoothMapContentObserver.this.mResolver.update(updateUri, contentValues, null, null);
                } else {
                    Log.d(BluetoothMapContentObserver.TAG, "Can't find message for status update: " + messageUri);
                }
                cursor.close();
                if (msgInfo.statusDelivered == 0) {
                    BluetoothMapContentObserver.this.sendEvent(new Event(BluetoothMapContentObserver.EVENT_TYPE_DELEVERY_SUCCESS, msgInfo.id, BluetoothMapContentObserver.folderSms[2], null, BluetoothMapContentObserver.this.mSmsType));
                } else {
                    BluetoothMapContentObserver.this.sendEvent(new Event(BluetoothMapContentObserver.EVENT_TYPE_SENDING_FAILURE, msgInfo.id, BluetoothMapContentObserver.folderSms[2], null, BluetoothMapContentObserver.this.mSmsType));
                }
                BluetoothMapContentObserver.this.mPushMsgList.remove(Long.valueOf(msgInfo.id));
            } catch (Throwable th) {
                cursor.close();
            }
        }
    }

    private static void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {
            }
        }
    }

    public BluetoothMapContentObserver(Context context, BluetoothMnsObexClient mnsClient, BluetoothMapMasInstance masInstance, BluetoothMapEmailSettingsItem account, boolean enableSmsMms) throws RemoteException {
        this.mContext = context;
        this.mResolver = this.mContext.getContentResolver();
        this.mAccount = account;
        this.mMasInstance = masInstance;
        this.mMasId = this.mMasInstance.getMasId();
        if (account != null) {
            this.mAuthority = Uri.parse(account.mBase_uri).getAuthority();
            this.mMessageUri = Uri.parse(account.mBase_uri + "/" + "Message");
            this.mProviderClient = this.mResolver.acquireUnstableContentProviderClient(this.mAuthority);
            if (this.mProviderClient == null) {
                throw new RemoteException("Failed to acquire provider for " + this.mAuthority);
            }
            this.mProviderClient.setDetectNotResponding(PROVIDER_ANR_TIMEOUT);
        }
        this.mEnableSmsMms = enableSmsMms;
        this.mSmsType = getSmsType();
        this.mMnsClient = mnsClient;
    }

    public void setFolderStructure(BluetoothMapFolderElement folderStructure) {
        this.mFolders = folderStructure;
    }

    private TYPE getSmsType() {
        TelephonyManager tm = (TelephonyManager) this.mContext.getSystemService("phone");
        if (tm.getPhoneType() == 1) {
            return TYPE.SMS_GSM;
        }
        if (tm.getPhoneType() == 2) {
            return TYPE.SMS_CDMA;
        }
        return null;
    }

    public int setNotificationRegistration(int notificationStatus) throws RemoteException {
        Log.d(TAG, "setNotificationRegistration() enter");
        Handler mns = this.mMnsClient.getMessageHandler();
        if (mns != null) {
            Message msg = mns.obtainMessage();
            msg.what = 1;
            msg.arg1 = this.mMasId;
            msg.arg2 = notificationStatus;
            mns.sendMessageDelayed(msg, 10);
            Log.d(TAG, "setNotificationRegistration() MSG_MNS_NOTIFICATION_REGISTRATION send to MNS");
            if (notificationStatus == 1) {
                registerObserver();
            } else {
                unregisterObserver();
            }
            return 160;
        }
        Log.d(TAG, "setNotificationRegistration() Unable to send registration request");
        return 211;
    }

    public void registerObserver() throws RemoteException {
        if (!this.mObserverRegistered) {
            if (this.mEnableSmsMms) {
                this.mResolver.registerContentObserver(MmsSms.CONTENT_URI, false, this.mObserver);
                this.mObserverRegistered = true;
            }
            if (this.mAccount != null) {
                this.mProviderClient = this.mResolver.acquireUnstableContentProviderClient(this.mAuthority);
                if (this.mProviderClient == null) {
                    throw new RemoteException("Failed to acquire provider for " + this.mAuthority);
                }
                this.mProviderClient.setDetectNotResponding(PROVIDER_ANR_TIMEOUT);
                Uri uri = Uri.parse(this.mAccount.mBase_uri_no_account + "/" + "Message");
                Log.d(TAG, "Registering observer for: " + uri);
                this.mResolver.registerContentObserver(uri, true, this.mObserver);
                uri = Uri.parse(this.mAccount.mBase_uri + "/" + "Message");
                Log.d(TAG, "Registering observer for: " + uri);
                this.mResolver.registerContentObserver(uri, true, this.mObserver);
                this.mObserverRegistered = true;
            }
            initMsgList();
        }
    }

    public void unregisterObserver() {
        this.mResolver.unregisterContentObserver(this.mObserver);
        this.mObserverRegistered = false;
        if (this.mProviderClient != null) {
            this.mProviderClient.release();
            this.mProviderClient = null;
        }
    }

    private void sendEvent(Event evt) {
        Log.d(TAG, "sendEvent: " + evt.eventType + " " + evt.handle + " " + evt.folder + " " + evt.oldFolder + " " + evt.msgType.name());
        if (this.mMnsClient == null || !this.mMnsClient.isConnected()) {
            Log.d(TAG, "sendEvent: No MNS client registered or connected- don't send event");
            return;
        }
        try {
            this.mMnsClient.sendEvent(evt.encode(), this.mMasId);
        } catch (UnsupportedEncodingException e) {
        }
    }

    private void initMsgList() throws RemoteException {
        Cursor c;
        long id;
        HashMap<Long, Msg> hashMap;
        if (this.mEnableSmsMms) {
            HashMap<Long, Msg> msgListSms = new HashMap();
            c = this.mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION_SHORT, null, null, null);
            while (c != null) {
                try {
                    if (!c.moveToNext()) {
                        break;
                    }
                    id = c.getLong(c.getColumnIndex("_id"));
                    hashMap = msgListSms;
                    hashMap.put(Long.valueOf(id), new Msg(id, c.getInt(c.getColumnIndex("type")), c.getInt(c.getColumnIndex("thread_id"))));
                } catch (Throwable th) {
                    close(c);
                }
            }
            close(c);
            synchronized (this.mMsgListSms) {
                this.mMsgListSms.clear();
                this.mMsgListSms = msgListSms;
            }
            HashMap<Long, Msg> msgListMms = new HashMap();
            c = this.mResolver.query(Mms.CONTENT_URI, MMS_PROJECTION_SHORT, null, null, null);
            while (c != null) {
                try {
                    if (!c.moveToNext()) {
                        break;
                    }
                    id = c.getLong(c.getColumnIndex("_id"));
                    hashMap = msgListMms;
                    hashMap.put(Long.valueOf(id), new Msg(id, c.getInt(c.getColumnIndex("msg_box")), c.getInt(c.getColumnIndex("thread_id"))));
                } catch (Throwable th2) {
                    close(c);
                }
            }
            close(c);
            synchronized (this.mMsgListMms) {
                this.mMsgListMms.clear();
                this.mMsgListMms = msgListMms;
            }
        }
        if (this.mAccount != null) {
            HashMap<Long, Msg> msgListEmail = new HashMap();
            c = this.mProviderClient.query(this.mMessageUri, EMAIL_PROJECTION_SHORT, null, null, null);
            while (c != null) {
                try {
                    if (!c.moveToNext()) {
                        break;
                    }
                    id = c.getLong(c.getColumnIndex("_id"));
                    long j = id;
                    hashMap = msgListEmail;
                    hashMap.put(Long.valueOf(id), new Msg(j, (long) c.getInt(c.getColumnIndex("folder_id"))));
                } catch (Throwable th3) {
                    close(c);
                }
            }
            close(c);
            synchronized (this.mMsgListEmail) {
                this.mMsgListEmail.clear();
                this.mMsgListEmail = msgListEmail;
            }
        }
    }

    private void handleMsgListChangesSms() {
        HashMap<Long, Msg> msgListSms = new HashMap();
        Cursor c = this.mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION_SHORT, null, null, null);
        synchronized (this.mMsgListSms) {
            while (c != null) {
                Msg msg;
                if (c.moveToNext()) {
                    long id = c.getLong(c.getColumnIndex("_id"));
                    int type = c.getInt(c.getColumnIndex("type"));
                    int threadId = c.getInt(c.getColumnIndex("thread_id"));
                    msg = (Msg) this.mMsgListSms.remove(Long.valueOf(id));
                    if (msg == null) {
                        HashMap<Long, Msg> hashMap = msgListSms;
                        hashMap.put(Long.valueOf(id), new Msg(id, type, threadId));
                        sendEvent(new Event(EVENT_TYPE_NEW, id, folderSms[type], null, this.mSmsType));
                    } else {
                        try {
                            if (type != msg.type) {
                                Log.d(TAG, "new type: " + type + " old type: " + msg.type);
                                String oldFolder = folderSms[msg.type];
                                if (!oldFolder.equals(folderSms[type])) {
                                    sendEvent(new Event(EVENT_TYPE_SHIFT, id, folderSms[type], oldFolder, this.mSmsType));
                                }
                                msg.type = type;
                            } else if (threadId != msg.threadId) {
                                Log.d(TAG, "Message delete change: type: " + type + " old type: " + msg.type + "\n    threadId: " + threadId + " old threadId: " + msg.threadId);
                                if (threadId == -1) {
                                    sendEvent(new Event(EVENT_TYPE_DELETE, id, "deleted", folderSms[msg.type], this.mSmsType));
                                    msg.threadId = threadId;
                                } else {
                                    sendEvent(new Event(EVENT_TYPE_SHIFT, id, folderSms[msg.type], "deleted", this.mSmsType));
                                    msg.threadId = threadId;
                                }
                            }
                            msgListSms.put(Long.valueOf(id), msg);
                        } catch (Throwable th) {
                            close(c);
                        }
                    }
                }
            }
            close(c);
            for (Msg msg2 : this.mMsgListSms.values()) {
                sendEvent(new Event(EVENT_TYPE_DELETE, msg2.id, "deleted", folderSms[msg2.type], this.mSmsType));
            }
            this.mMsgListSms = msgListSms;
        }
    }

    private void handleMsgListChangesMms() {
        HashMap<Long, Msg> msgListMms = new HashMap();
        Cursor c = this.mResolver.query(Mms.CONTENT_URI, MMS_PROJECTION_SHORT, null, null, null);
        synchronized (this.mMsgListMms) {
            while (c != null) {
                Msg msg;
                if (c.moveToNext()) {
                    long id = c.getLong(c.getColumnIndex("_id"));
                    int type = c.getInt(c.getColumnIndex("msg_box"));
                    int mtype = c.getInt(c.getColumnIndex("m_type"));
                    int threadId = c.getInt(c.getColumnIndex("thread_id"));
                    msg = (Msg) this.mMsgListMms.remove(Long.valueOf(id));
                    if (msg != null) {
                        try {
                            if (type != msg.type) {
                                Log.d(TAG, "new type: " + type + " old type: " + msg.type);
                                if (!msg.localInitiatedSend) {
                                    sendEvent(new Event(EVENT_TYPE_SHIFT, id, folderMms[type], folderMms[msg.type], TYPE.MMS));
                                }
                                msg.type = type;
                                if (folderMms[type].equals("sent") && msg.localInitiatedSend) {
                                    msg.localInitiatedSend = false;
                                    sendEvent(new Event(EVENT_TYPE_SENDING_SUCCESS, id, folderSms[type], null, TYPE.MMS));
                                }
                            } else if (threadId != msg.threadId) {
                                Log.d(TAG, "Message delete change: type: " + type + " old type: " + msg.type + "\n    threadId: " + threadId + " old threadId: " + msg.threadId);
                                if (threadId == -1) {
                                    sendEvent(new Event(EVENT_TYPE_DELETE, id, "deleted", folderMms[msg.type], TYPE.MMS));
                                    msg.threadId = threadId;
                                } else {
                                    sendEvent(new Event(EVENT_TYPE_SHIFT, id, folderMms[msg.type], "deleted", TYPE.MMS));
                                    msg.threadId = threadId;
                                }
                            }
                            msgListMms.put(Long.valueOf(id), msg);
                        } catch (Throwable th) {
                            close(c);
                        }
                    } else if (!folderMms[type].equals("inbox") || mtype == 132) {
                        HashMap<Long, Msg> hashMap = msgListMms;
                        hashMap.put(Long.valueOf(id), new Msg(id, type, threadId));
                        sendEvent(new Event(EVENT_TYPE_NEW, id, folderMms[type], null, TYPE.MMS));
                    }
                }
            }
            close(c);
            for (Msg msg2 : this.mMsgListMms.values()) {
                sendEvent(new Event(EVENT_TYPE_DELETE, msg2.id, "deleted", folderMms[msg2.type], TYPE.MMS));
            }
            this.mMsgListMms = msgListMms;
        }
    }

    private void handleMsgListChangesEmail(Uri uri) throws RemoteException {
        HashMap<Long, Msg> msgListEmail = new HashMap();
        Cursor c = this.mProviderClient.query(this.mMessageUri, EMAIL_PROJECTION_SHORT, null, null, null);
        synchronized (this.mMsgListEmail) {
            Msg msg;
            while (c != null) {
                BluetoothMapFolderElement oldFolderElement;
                String oldFolder;
                if (c.moveToNext()) {
                    String newFolder;
                    long id = c.getLong(c.getColumnIndex("_id"));
                    int folderId = c.getInt(c.getColumnIndex("folder_id"));
                    msg = (Msg) this.mMsgListEmail.remove(Long.valueOf(id));
                    BluetoothMapFolderElement folderElement = this.mFolders.getEmailFolderById((long) folderId);
                    if (folderElement != null) {
                        newFolder = folderElement.getFullPath();
                    } else {
                        try {
                            newFolder = "unknown";
                        } catch (Throwable th) {
                            close(c);
                        }
                    }
                    if (msg == null) {
                        HashMap<Long, Msg> hashMap = msgListEmail;
                        hashMap.put(Long.valueOf(id), new Msg(id, (long) folderId));
                        sendEvent(new Event(EVENT_TYPE_NEW, id, newFolder, null, TYPE.EMAIL));
                    } else {
                        if (((long) folderId) != msg.folderId) {
                            Log.d(TAG, "new folderId: " + folderId + " old folderId: " + msg.folderId);
                            oldFolderElement = this.mFolders.getEmailFolderById(msg.folderId);
                            if (oldFolderElement != null) {
                                oldFolder = oldFolderElement.getFullPath();
                            } else {
                                oldFolder = "unknown";
                            }
                            BluetoothMapFolderElement deletedFolder = this.mFolders.getEmailFolderByName("deleted");
                            BluetoothMapFolderElement sentFolder = this.mFolders.getEmailFolderByName("sent");
                            if (deletedFolder != null && deletedFolder.getEmailFolderId() == ((long) folderId)) {
                                sendEvent(new Event(EVENT_TYPE_DELETE, msg.id, newFolder, oldFolder, TYPE.EMAIL));
                            } else if (sentFolder == null || sentFolder.getEmailFolderId() != ((long) folderId) || !msg.localInitiatedSend) {
                                sendEvent(new Event(EVENT_TYPE_SHIFT, id, newFolder, oldFolder, TYPE.EMAIL));
                            } else if (msg.transparent) {
                                this.mResolver.delete(ContentUris.withAppendedId(this.mMessageUri, id), null, null);
                            } else {
                                msg.localInitiatedSend = false;
                                sendEvent(new Event(EVENT_TYPE_SENDING_SUCCESS, msg.id, oldFolder, null, TYPE.EMAIL));
                            }
                            msg.folderId = (long) folderId;
                        }
                        msgListEmail.put(Long.valueOf(id), msg);
                    }
                }
            }
            close(c);
            for (Msg msg2 : this.mMsgListEmail.values()) {
                oldFolderElement = this.mFolders.getEmailFolderById(msg2.folderId);
                if (oldFolderElement != null) {
                    oldFolder = oldFolderElement.getFullPath();
                } else {
                    oldFolder = "unknown";
                }
                if (msg2.localInitiatedSend) {
                    msg2.localInitiatedSend = false;
                    if (msg2.transparent) {
                        oldFolder = null;
                    }
                    sendEvent(new Event(EVENT_TYPE_SENDING_SUCCESS, msg2.id, oldFolder, null, TYPE.EMAIL));
                }
                if (!msg2.transparent) {
                    sendEvent(new Event(EVENT_TYPE_DELETE, msg2.id, null, oldFolder, TYPE.EMAIL));
                }
            }
            this.mMsgListEmail = msgListEmail;
        }
    }

    private void handleMsgListChanges(Uri uri) {
        if (uri.getAuthority().equals(this.mAuthority)) {
            try {
                handleMsgListChangesEmail(uri);
                return;
            } catch (RemoteException e) {
                this.mMasInstance.restartObexServerSession();
                Log.w(TAG, "Problems contacting the ContentProvider in mas Instance " + this.mMasId + " restaring ObexServerSession");
                return;
            }
        }
        handleMsgListChangesSms();
        handleMsgListChangesMms();
    }

    private boolean setEmailMessageStatusDelete(BluetoothMapFolderElement mCurrentFolder, String uriStr, long handle, int status) {
        boolean res = false;
        Uri uri = Uri.parse(uriStr + "Message");
        ContentValues contentValues = new ContentValues();
        BluetoothMapFolderElement deleteFolder = this.mFolders.getEmailFolderByName("deleted");
        contentValues.put("_id", Long.valueOf(handle));
        synchronized (this.mMsgListEmail) {
            Msg msg = (Msg) this.mMsgListEmail.get(Long.valueOf(handle));
            long folderId;
            if (status == 1) {
                folderId = -1;
                if (deleteFolder != null) {
                    folderId = deleteFolder.getEmailFolderId();
                }
                contentValues.put("folder_id", Long.valueOf(folderId));
                if (this.mResolver.update(uri, contentValues, null, null) > 0) {
                    res = true;
                    if (msg != null) {
                        msg.oldFolderId = msg.folderId;
                        msg.folderId = folderId;
                    }
                    Log.d(TAG, "Deleted MSG: " + handle + " from folderId: " + folderId);
                } else {
                    Log.w(TAG, "Msg: " + handle + " - Set delete status " + status + " failed for folderId " + folderId);
                }
            } else if (!(status != 0 || msg == null || deleteFolder == null)) {
                if (msg.folderId == deleteFolder.getEmailFolderId()) {
                    folderId = -1;
                    if (msg == null || msg.oldFolderId == -1) {
                        BluetoothMapFolderElement inboxFolder = mCurrentFolder.getEmailFolderByName("inbox");
                        if (inboxFolder != null) {
                            folderId = inboxFolder.getEmailFolderId();
                        }
                        Log.d(TAG, "We did not delete the message, hence the old folder is unknown. Moving to inbox.");
                    } else {
                        folderId = msg.oldFolderId;
                    }
                    contentValues.put("folder_id", Long.valueOf(folderId));
                    if (this.mResolver.update(uri, contentValues, null, null) > 0) {
                        res = true;
                        msg.folderId = folderId;
                    } else {
                        Log.d(TAG, "We did not delete the message, hence the old folder is unknown. Moving to inbox.");
                    }
                }
            }
        }
        if (!res) {
            Log.w(TAG, "Set delete status " + status + " failed.");
        }
        return res;
    }

    private void updateThreadId(Uri uri, String valueString, long threadId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(valueString, Long.valueOf(threadId));
        this.mResolver.update(uri, contentValues, null, null);
    }

    private boolean deleteMessageMms(long handle) {
        boolean res = false;
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, handle);
        Cursor c = this.mResolver.query(uri, null, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    if (c.getInt(c.getColumnIndex("thread_id")) != -1) {
                        synchronized (this.mMsgListMms) {
                            Msg msg = (Msg) this.mMsgListMms.get(Long.valueOf(handle));
                            if (msg != null) {
                                msg.threadId = -1;
                            }
                        }
                        updateThreadId(uri, "thread_id", -1);
                    } else {
                        synchronized (this.mMsgListMms) {
                            this.mMsgListMms.remove(Long.valueOf(handle));
                        }
                        this.mResolver.delete(uri, null, null);
                    }
                    res = true;
                }
            } catch (Throwable th) {
                close(c);
            }
        }
        close(c);
        return res;
    }

    private boolean unDeleteMessageMms(long handle) {
        boolean res = false;
        Uri uri = ContentUris.withAppendedId(Mms.CONTENT_URI, handle);
        Cursor c = this.mResolver.query(uri, null, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    int threadId = c.getInt(c.getColumnIndex("thread_id"));
                    if (threadId == -1) {
                        String address;
                        long id = c.getLong(c.getColumnIndex("_id"));
                        if (c.getInt(c.getColumnIndex("msg_box")) == 1) {
                            address = BluetoothMapContent.getAddressMms(this.mResolver, id, BluetoothMapContent.MMS_FROM);
                        } else {
                            address = BluetoothMapContent.getAddressMms(this.mResolver, id, BluetoothMapContent.MMS_TO);
                        }
                        Set<String> recipients = new HashSet();
                        recipients.addAll(Arrays.asList(new String[]{address}));
                        Long oldThreadId = Long.valueOf(Threads.getOrCreateThreadId(this.mContext, recipients));
                        synchronized (this.mMsgListMms) {
                            Msg msg = (Msg) this.mMsgListMms.get(Long.valueOf(handle));
                            if (msg != null) {
                                msg.threadId = oldThreadId.intValue();
                            }
                        }
                        updateThreadId(uri, "thread_id", oldThreadId.longValue());
                    } else {
                        Log.d(TAG, "Message not in deleted folder: handle " + handle + " threadId " + threadId);
                    }
                    res = true;
                }
            } catch (Throwable th) {
                close(c);
            }
        }
        close(c);
        return res;
    }

    private boolean deleteMessageSms(long handle) {
        boolean res = false;
        Uri uri = ContentUris.withAppendedId(Sms.CONTENT_URI, handle);
        Cursor c = this.mResolver.query(uri, null, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    if (c.getInt(c.getColumnIndex("thread_id")) != -1) {
                        synchronized (this.mMsgListSms) {
                            Msg msg = (Msg) this.mMsgListSms.get(Long.valueOf(handle));
                            if (msg != null) {
                                msg.threadId = -1;
                            }
                        }
                        updateThreadId(uri, "thread_id", -1);
                    } else {
                        synchronized (this.mMsgListSms) {
                            this.mMsgListSms.remove(Long.valueOf(handle));
                        }
                        this.mResolver.delete(uri, null, null);
                    }
                    res = true;
                }
            } catch (Throwable th) {
                close(c);
            }
        }
        close(c);
        return res;
    }

    private boolean unDeleteMessageSms(long handle) {
        boolean res = false;
        Uri uri = ContentUris.withAppendedId(Sms.CONTENT_URI, handle);
        Cursor c = this.mResolver.query(uri, null, null, null, null);
        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    int threadId = c.getInt(c.getColumnIndex("thread_id"));
                    if (threadId == -1) {
                        String address = c.getString(c.getColumnIndex("address"));
                        Set<String> recipients = new HashSet();
                        recipients.addAll(Arrays.asList(new String[]{address}));
                        Long oldThreadId = Long.valueOf(Threads.getOrCreateThreadId(this.mContext, recipients));
                        synchronized (this.mMsgListSms) {
                            Msg msg = (Msg) this.mMsgListSms.get(Long.valueOf(handle));
                            if (msg != null) {
                                msg.threadId = oldThreadId.intValue();
                            }
                        }
                        updateThreadId(uri, "thread_id", oldThreadId.longValue());
                    } else {
                        Log.d(TAG, "Message not in deleted folder: handle " + handle + " threadId " + threadId);
                    }
                    res = true;
                }
            } catch (Throwable th) {
                close(c);
            }
        }
        close(c);
        return res;
    }

    public boolean setMessageStatusDeleted(long handle, TYPE type, BluetoothMapFolderElement mCurrentFolder, String uriStr, int statusValue) {
        Log.d(TAG, "setMessageStatusDeleted: handle " + handle + " type " + type + " value " + statusValue);
        if (type == TYPE.EMAIL) {
            return setEmailMessageStatusDelete(mCurrentFolder, uriStr, handle, statusValue);
        }
        if (statusValue == 1) {
            if (type == TYPE.SMS_GSM || type == TYPE.SMS_CDMA) {
                return deleteMessageSms(handle);
            }
            if (type == TYPE.MMS) {
                return deleteMessageMms(handle);
            }
            return false;
        } else if (statusValue != 0) {
            return false;
        } else {
            if (type == TYPE.SMS_GSM || type == TYPE.SMS_CDMA) {
                return unDeleteMessageSms(handle);
            }
            if (type == TYPE.MMS) {
                return unDeleteMessageMms(handle);
            }
            return false;
        }
    }

    public boolean setMessageStatusRead(long handle, TYPE type, String uriStr, int statusValue) throws RemoteException {
        Uri uri;
        ContentValues contentValues;
        int count = 0;
        Log.d(TAG, "setMessageStatusRead: handle " + handle + " type " + type + " value " + statusValue);
        if (type == TYPE.SMS_GSM || type == TYPE.SMS_CDMA) {
            uri = Inbox.CONTENT_URI;
            contentValues = new ContentValues();
            contentValues.put("read", Integer.valueOf(statusValue));
            contentValues.put("seen", Integer.valueOf(statusValue));
            String where = "_id=" + handle;
            Log.d(TAG, " -> SMS Uri: " + uri.toString() + " Where " + where + " values " + contentValues.toString());
            count = this.mResolver.update(uri, contentValues, where, null);
            Log.d(TAG, " -> " + count + " rows updated!");
        } else if (type == TYPE.MMS) {
            uri = ContentUris.withAppendedId(Mms.CONTENT_URI, handle);
            Log.d(TAG, " -> MMS Uri: " + uri.toString());
            contentValues = new ContentValues();
            contentValues.put("read", Integer.valueOf(statusValue));
            count = this.mResolver.update(uri, contentValues, null, null);
            Log.d(TAG, " -> " + count + " rows updated!");
        }
        if (type == TYPE.EMAIL) {
            uri = this.mMessageUri;
            contentValues = new ContentValues();
            contentValues.put("flag_read", Integer.valueOf(statusValue));
            contentValues.put("_id", Long.valueOf(handle));
            count = this.mProviderClient.update(uri, contentValues, null, null);
        }
        if (count > 0) {
            return true;
        }
        return false;
    }

    public long pushMessage(BluetoothMapbMessage msg, BluetoothMapFolderElement folderElement, BluetoothMapAppParams ap, String emailBaseUri) throws IllegalArgumentException, RemoteException, IOException {
        Throwable e;
        Throwable th;
        Log.d(TAG, "pushMessage");
        ArrayList<vCard> recipientList = msg.getRecipients();
        int transparent = ap.getTransparent() == -1 ? 0 : ap.getTransparent();
        int retry = ap.getRetry();
        int charset = ap.getCharset();
        long handle = -1;
        if (recipientList == null) {
            Log.d(TAG, "empty recipient list");
            return -1;
        }
        String msgBody;
        if (msg.getType().equals(TYPE.EMAIL)) {
            msgBody = ((BluetoothMapbMessageEmail) msg).getEmailBody();
            FileOutputStream fileOutputStream = null;
            ParcelFileDescriptor parcelFileDescriptor = null;
            Uri uriInsert = Uri.parse(emailBaseUri + "Message");
            Log.d(TAG, "pushMessage - uriInsert= " + uriInsert.toString() + ", intoFolder id=" + folderElement.getEmailFolderId());
            synchronized (this.mMsgListEmail) {
                ContentValues values = new ContentValues();
                long folderId = folderElement.getEmailFolderId();
                values.put("folder_id", Long.valueOf(folderId));
                Uri uriNew = this.mProviderClient.insert(uriInsert, values);
                Log.d(TAG, "pushMessage - uriNew= " + uriNew.toString());
                handle = Long.parseLong(uriNew.getLastPathSegment());
                try {
                    parcelFileDescriptor = this.mProviderClient.openFile(uriNew, "w");
                    FileOutputStream fileOutputStream2 = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());
                    try {
                        fileOutputStream2.write(msgBody.getBytes(), 0, msgBody.getBytes().length);
                        if (fileOutputStream2 != null) {
                            try {
                                fileOutputStream2.close();
                            } catch (Throwable e2) {
                                Log.w(TAG, e2);
                            } catch (Throwable th2) {
                                th = th2;
                                fileOutputStream = fileOutputStream2;
                                throw th;
                            }
                        }
                        if (parcelFileDescriptor != null) {
                            try {
                                parcelFileDescriptor.close();
                            } catch (Throwable e22) {
                                Log.w(TAG, e22);
                            }
                        }
                        Msg newMsg = new Msg(handle, folderId);
                        newMsg.transparent = transparent == 1;
                        if (folderId == folderElement.getEmailFolderByName("outbox").getEmailFolderId()) {
                            newMsg.localInitiatedSend = true;
                        }
                        this.mMsgListEmail.put(Long.valueOf(handle), newMsg);
                    } catch (FileNotFoundException e3) {
                        e22 = e3;
                        fileOutputStream = fileOutputStream2;
                        try {
                            Log.w(TAG, e22);
                            throw new IOException("Unable to open file stream");
                        } catch (Throwable th3) {
                            th = th3;
                            if (fileOutputStream != null) {
                                try {
                                    fileOutputStream.close();
                                } catch (Throwable e222) {
                                    Log.w(TAG, e222);
                                } catch (Throwable th4) {
                                    th = th4;
                                    throw th;
                                }
                            }
                            if (parcelFileDescriptor != null) {
                                try {
                                    parcelFileDescriptor.close();
                                } catch (Throwable e2222) {
                                    Log.w(TAG, e2222);
                                }
                            }
                            throw th;
                        }
                    } catch (NullPointerException e4) {
                        e2222 = e4;
                        fileOutputStream = fileOutputStream2;
                        Log.w(TAG, e2222);
                        throw new IllegalArgumentException("Unable to parse message.");
                    } catch (Throwable th5) {
                        th = th5;
                        fileOutputStream = fileOutputStream2;
                        if (fileOutputStream != null) {
                            fileOutputStream.close();
                        }
                        if (parcelFileDescriptor != null) {
                            parcelFileDescriptor.close();
                        }
                        throw th;
                    }
                } catch (FileNotFoundException e5) {
                    e2222 = e5;
                    Log.w(TAG, e2222);
                    throw new IOException("Unable to open file stream");
                } catch (NullPointerException e6) {
                    e2222 = e6;
                    Log.w(TAG, e2222);
                    throw new IllegalArgumentException("Unable to parse message.");
                }
            }
        }
        Iterator i$ = recipientList.iterator();
        while (i$.hasNext()) {
            vCard recipient = (vCard) i$.next();
            if (recipient.getEnvLevel() == 0) {
                String phone = recipient.getFirstPhoneNumber();
                String email = recipient.getFirstEmail();
                String folder = folderElement.getName();
                msgBody = null;
                if (msg.getType().equals(TYPE.MMS) && ((BluetoothMapbMessageMms) msg).getTextOnly()) {
                    msgBody = ((BluetoothMapbMessageMms) msg).getMessageAsText();
                    int smsParts = SmsManager.getDefault().divideMessage(msgBody).size();
                    if (smsParts <= 10) {
                        Log.d(TAG, "pushMessage - converting MMS to SMS, sms parts=" + smsParts);
                        msg.setType(this.mSmsType);
                    } else {
                        Log.d(TAG, "pushMessage - MMS text only but to big to convert to SMS");
                        msgBody = null;
                    }
                }
                if (msg.getType().equals(TYPE.MMS)) {
                    handle = sendMmsMessage(folder, phone, (BluetoothMapbMessageMms) msg);
                } else if (msg.getType().equals(TYPE.SMS_GSM) || msg.getType().equals(TYPE.SMS_CDMA)) {
                    if (msgBody == null) {
                        msgBody = ((BluetoothMapbMessageSms) msg).getSmsBody();
                    }
                    Uri contentUri = Uri.parse(Sms.CONTENT_URI + "/" + folder);
                    synchronized (this.mMsgListSms) {
                        Uri uri = Sms.addMessageToUri(this.mResolver, contentUri, phone, msgBody, "", Long.valueOf(System.currentTimeMillis()), false, true);
                        if (uri == null) {
                            Log.d(TAG, "pushMessage - failure on add to uri " + contentUri);
                            return -1;
                        }
                        Cursor c = this.mResolver.query(uri, SMS_PROJECTION_SHORT, null, null, null);
                        if (c != null) {
                            try {
                                if (c.moveToFirst()) {
                                    long id = c.getLong(c.getColumnIndex("_id"));
                                    this.mMsgListSms.put(Long.valueOf(id), new Msg(id, c.getInt(c.getColumnIndex("type")), c.getInt(c.getColumnIndex("thread_id"))));
                                    handle = Long.parseLong(uri.getLastPathSegment());
                                    if (folder.equals("outbox")) {
                                        PushMsgInfo msgInfo = new PushMsgInfo(handle, transparent, retry, phone, uri);
                                        this.mPushMsgList.put(Long.valueOf(handle), msgInfo);
                                        sendMessage(msgInfo, msgBody);
                                    }
                                }
                            } finally {
                                close(c);
                            }
                        }
                        close(c);
                        return -1;
                    }
                } else {
                    Log.d(TAG, "pushMessage - failure on type ");
                    return -1;
                }
            }
        }
        return handle;
    }

    public long sendMmsMessage(String folder, String to_address, BluetoothMapbMessageMms msg) {
        if (folder == null || !(folder.equalsIgnoreCase("outbox") || folder.equalsIgnoreCase("draft"))) {
            throw new IllegalArgumentException("Cannot push message to other folders than outbox/draft");
        }
        long handle = pushMmsToFolder(3, to_address, msg);
        if (-1 != handle && folder.equalsIgnoreCase("outbox")) {
            moveDraftToOutbox(handle);
            Intent sendIntent = new Intent("android.intent.action.MMS_SEND_OUTBOX_MSG");
            Log.d(TAG, "broadcasting intent: " + sendIntent.toString());
            this.mContext.sendBroadcast(sendIntent);
        }
        return handle;
    }

    private void moveDraftToOutbox(long handle) {
        if (handle == -1) {
            String whereClause = " _id= " + handle;
            Uri uri = Mms.CONTENT_URI;
            Cursor queryResult = this.mResolver.query(uri, null, whereClause, null, null);
            if (queryResult != null) {
                try {
                    if (queryResult.moveToFirst()) {
                        ContentValues data = new ContentValues();
                        data.put("msg_box", Integer.valueOf(4));
                        this.mResolver.update(uri, data, whereClause, null);
                        Log.d(TAG, "Moved draft MMS to outbox");
                        queryResult.close();
                    }
                } catch (Throwable th) {
                    queryResult.close();
                }
            }
            Log.d(TAG, "Could not move draft to outbox ");
            queryResult.close();
        }
    }

    private long pushMmsToFolder(int folder, String to_address, BluetoothMapbMessageMms msg) {
        long j;
        ContentValues values = new ContentValues();
        values.put("msg_box", Integer.valueOf(folder));
        values.put("read", Integer.valueOf(0));
        values.put("seen", Integer.valueOf(0));
        if (msg.getSubject() != null) {
            values.put("sub", msg.getSubject());
        } else {
            values.put("sub", "");
        }
        if (msg.getSubject() != null && msg.getSubject().length() > 0) {
            values.put("sub_cs", Integer.valueOf(106));
        }
        values.put("ct_t", "application/vnd.wap.multipart.related");
        values.put("exp", Integer.valueOf(604800));
        values.put("m_cls", "personal");
        values.put("m_type", Integer.valueOf(128));
        values.put("v", Integer.valueOf(18));
        values.put("pri", Integer.valueOf(BluetoothMapContent.MMS_BCC));
        values.put("rr", Integer.valueOf(BluetoothMapContent.MMS_BCC));
        values.put("tr_id", "T" + Long.toHexString(System.currentTimeMillis()));
        values.put("d_rpt", Integer.valueOf(BluetoothMapContent.MMS_BCC));
        values.put("locked", Integer.valueOf(0));
        if (msg.getTextOnly()) {
            values.put("text_only", Boolean.valueOf(true));
        }
        values.put("m_size", Integer.valueOf(msg.getSize()));
        Set<String> recipients = new HashSet();
        Set<String> set = recipients;
        set.addAll(Arrays.asList(new String[]{to_address}));
        values.put("thread_id", Long.valueOf(Threads.getOrCreateThreadId(this.mContext, recipients)));
        Uri uri = Mms.CONTENT_URI;
        synchronized (this.mMsgListMms) {
            uri = this.mResolver.insert(uri, values);
            if (uri == null) {
                Log.e(TAG, "Unabled to insert MMS " + values + "Uri: " + uri);
                j = -1;
            } else {
                Cursor c = this.mResolver.query(uri, MMS_PROJECTION_SHORT, null, null, null);
                if (c != null) {
                    try {
                        if (c.moveToFirst()) {
                            long id = c.getLong(c.getColumnIndex("_id"));
                            Msg newMsg = new Msg(id, c.getInt(c.getColumnIndex("msg_box")), c.getInt(c.getColumnIndex("thread_id")));
                            newMsg.localInitiatedSend = true;
                            this.mMsgListMms.put(Long.valueOf(id), newMsg);
                        }
                    } catch (Throwable th) {
                        close(c);
                    }
                }
                close(c);
                j = Long.parseLong(uri.getLastPathSegment());
                try {
                    if (msg.getMimeParts() == null) {
                        Log.w(TAG, "No MMS parts present...");
                    } else {
                        int count = 0;
                        Iterator i$ = msg.getMimeParts().iterator();
                        while (i$.hasNext()) {
                            MimePart part = (MimePart) i$.next();
                            count++;
                            values.clear();
                            ContentValues contentValues;
                            if (part.mContentType != null && part.mContentType.toUpperCase().contains("TEXT")) {
                                values.put("ct", "text/plain");
                                values.put("chset", Integer.valueOf(106));
                                if (part.mPartName != null) {
                                    contentValues = values;
                                    contentValues.put("fn", part.mPartName);
                                    contentValues = values;
                                    contentValues.put("name", part.mPartName);
                                } else {
                                    values.put("fn", "text_" + count + ".txt");
                                    values.put("name", "text_" + count + ".txt");
                                }
                                if (part.mContentId != null) {
                                    contentValues = values;
                                    contentValues.put("cid", part.mContentId);
                                } else if (part.mPartName != null) {
                                    values.put("cid", "<" + part.mPartName + ">");
                                } else {
                                    values.put("cid", "<text_" + count + ">");
                                }
                                if (part.mContentLocation != null) {
                                    contentValues = values;
                                    contentValues.put("cl", part.mContentLocation);
                                } else if (part.mPartName != null) {
                                    values.put("cl", part.mPartName + ".txt");
                                } else {
                                    values.put("cl", "text_" + count + ".txt");
                                }
                                if (part.mContentDisposition != null) {
                                    contentValues = values;
                                    contentValues.put("cd", part.mContentDisposition);
                                }
                                values.put("text", part.getDataAsString());
                                uri = this.mResolver.insert(Uri.parse(Mms.CONTENT_URI + "/" + j + "/part"), values);
                            } else if (part.mContentType == null || !part.mContentType.toUpperCase().contains("SMIL")) {
                                writeMmsDataPart(j, part, count);
                            } else {
                                values.put("seq", Integer.valueOf(-1));
                                values.put("ct", "application/smil");
                                if (part.mContentId != null) {
                                    contentValues = values;
                                    contentValues.put("cid", part.mContentId);
                                } else {
                                    values.put("cid", "<smil_" + count + ">");
                                }
                                if (part.mContentLocation != null) {
                                    contentValues = values;
                                    contentValues.put("cl", part.mContentLocation);
                                } else {
                                    values.put("cl", "smil_" + count + ".xml");
                                }
                                if (part.mContentDisposition != null) {
                                    contentValues = values;
                                    contentValues.put("cd", part.mContentDisposition);
                                }
                                values.put("fn", "smil.xml");
                                values.put("name", "smil.xml");
                                contentValues = values;
                                contentValues.put("text", new String(part.mData, "UTF-8"));
                                uri = this.mResolver.insert(Uri.parse(Mms.CONTENT_URI + "/" + j + "/part"), values);
                            }
                            if (uri != null) {
                            }
                        }
                    }
                } catch (UnsupportedEncodingException e) {
                    Log.w(TAG, e);
                } catch (IOException e2) {
                    Log.w(TAG, e2);
                }
                values.clear();
                values.put("contact_id", "null");
                values.put("address", BluetoothMapContent.INSERT_ADDRES_TOKEN);
                values.put("type", Integer.valueOf(BluetoothMapContent.MMS_FROM));
                values.put("charset", Integer.valueOf(106));
                if (this.mResolver.insert(Uri.parse(Mms.CONTENT_URI + "/" + j + "/addr"), values) != null) {
                    values.clear();
                    values.put("contact_id", "null");
                    values.put("address", to_address);
                    values.put("type", Integer.valueOf(BluetoothMapContent.MMS_TO));
                    values.put("charset", Integer.valueOf(106));
                } else {
                    values.clear();
                    values.put("contact_id", "null");
                    values.put("address", to_address);
                    values.put("type", Integer.valueOf(BluetoothMapContent.MMS_TO));
                    values.put("charset", Integer.valueOf(106));
                }
                if (this.mResolver.insert(Uri.parse(Mms.CONTENT_URI + "/" + j + "/addr"), values) != null) {
                }
            }
        }
        return j;
    }

    private void writeMmsDataPart(long handle, MimePart part, int count) throws IOException {
        ContentValues values = new ContentValues();
        values.put("mid", Long.valueOf(handle));
        if (part.mContentType != null) {
            values.put("ct", part.mContentType);
        } else {
            Log.w(TAG, "MMS has no CONTENT_TYPE for part " + count);
        }
        if (part.mContentId != null) {
            values.put("cid", part.mContentId);
        } else if (part.mPartName != null) {
            values.put("cid", "<" + part.mPartName + ">");
        } else {
            values.put("cid", "<part_" + count + ">");
        }
        if (part.mContentLocation != null) {
            values.put("cl", part.mContentLocation);
        } else if (part.mPartName != null) {
            values.put("cl", part.mPartName + ".dat");
        } else {
            values.put("cl", "part_" + count + ".dat");
        }
        if (part.mContentDisposition != null) {
            values.put("cd", part.mContentDisposition);
        }
        if (part.mPartName != null) {
            values.put("fn", part.mPartName);
            values.put("name", part.mPartName);
        } else {
            values.put("fn", "part_" + count + ".dat");
            values.put("name", "part_" + count + ".dat");
        }
        OutputStream os = this.mResolver.openOutputStream(this.mResolver.insert(Uri.parse(Mms.CONTENT_URI + "/" + handle + "/part"), values));
        os.write(part.mData);
        os.close();
    }

    public void sendMessage(PushMsgInfo msgInfo, String msgBody) {
        SmsManager smsMng = SmsManager.getDefault();
        ArrayList<String> parts = smsMng.divideMessage(msgBody);
        msgInfo.parts = parts.size();
        msgInfo.timestamp = Calendar.getInstance().getTime().getTime();
        msgInfo.partsDelivered = 0;
        msgInfo.partsSent = 0;
        ArrayList<PendingIntent> deliveryIntents = new ArrayList(msgInfo.parts);
        ArrayList<PendingIntent> sentIntents = new ArrayList(msgInfo.parts);
        for (int i = 0; i < msgInfo.parts; i++) {
            Intent intentDelivery = new Intent(ACTION_MESSAGE_DELIVERY, null);
            intentDelivery.setType("message/" + Long.toString(msgInfo.id) + msgInfo.timestamp + i);
            intentDelivery.putExtra(EXTRA_MESSAGE_SENT_HANDLE, msgInfo.id);
            intentDelivery.putExtra("timestamp", msgInfo.timestamp);
            PendingIntent pendingIntentDelivery = PendingIntent.getBroadcast(this.mContext, 0, intentDelivery, VCardConfig.FLAG_CONVERT_PHONETIC_NAME_STRINGS);
            Intent intentSent = new Intent(ACTION_MESSAGE_SENT, null);
            intentSent.setType("message/" + Long.toString(msgInfo.id) + msgInfo.timestamp + i);
            intentSent.putExtra(EXTRA_MESSAGE_SENT_HANDLE, msgInfo.id);
            intentSent.putExtra("uri", msgInfo.uri.toString());
            intentSent.putExtra(EXTRA_MESSAGE_SENT_RETRY, msgInfo.retry);
            intentSent.putExtra(EXTRA_MESSAGE_SENT_TRANSPARENT, msgInfo.transparent);
            PendingIntent pendingIntentSent = PendingIntent.getBroadcast(this.mContext, 0, intentSent, VCardConfig.FLAG_CONVERT_PHONETIC_NAME_STRINGS);
            deliveryIntents.add(pendingIntentDelivery);
            sentIntents.add(pendingIntentSent);
        }
        Log.d(TAG, "sendMessage to " + msgInfo.phone);
        smsMng.sendMultipartTextMessage(msgInfo.phone, null, parts, sentIntents, deliveryIntents);
    }

    public static void actionMessageSentDisconnected(Context context, Intent intent, int result) {
        boolean delete = false;
        int transparent = intent.getIntExtra(EXTRA_MESSAGE_SENT_TRANSPARENT, 0);
        String uriString = intent.getStringExtra("uri");
        if (uriString != null) {
            Uri uri = Uri.parse(uriString);
            if (result == -1) {
                Log.d(TAG, "actionMessageSentDisconnected: result OK");
                if (transparent != 0) {
                    delete = true;
                } else if (!Sms.moveMessageToFolder(context, uri, 2, 0)) {
                    Log.d(TAG, "Failed to move " + uri + " to SENT");
                }
            } else if (transparent != 0) {
                delete = true;
            } else if (!Sms.moveMessageToFolder(context, uri, 5, 0)) {
                Log.d(TAG, "Failed to move " + uri + " to FAILED");
            }
            if (delete) {
                ContentResolver resolver = context.getContentResolver();
                if (resolver != null) {
                    resolver.delete(uri, null, null);
                } else {
                    Log.w(TAG, "Unable to get resolver");
                }
            }
        }
    }

    private void registerPhoneServiceStateListener() {
        ((TelephonyManager) this.mContext.getSystemService("phone")).listen(this.mPhoneListener, 1);
    }

    private void unRegisterPhoneServiceStateListener() {
        ((TelephonyManager) this.mContext.getSystemService("phone")).listen(this.mPhoneListener, 0);
    }

    private void resendPendingMessages() {
        Cursor c = this.mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION, "type = 4", null, null);
        while (c != null) {
            try {
                if (!c.moveToNext()) {
                    break;
                }
                long id = c.getLong(c.getColumnIndex("_id"));
                String msgBody = c.getString(c.getColumnIndex("body"));
                PushMsgInfo msgInfo = (PushMsgInfo) this.mPushMsgList.get(Long.valueOf(id));
                if (!(msgInfo == null || !msgInfo.resend || msgInfo.sendInProgress)) {
                    msgInfo.sendInProgress = true;
                    sendMessage(msgInfo, msgBody);
                }
            } catch (Throwable th) {
                close(c);
            }
        }
        close(c);
    }

    private void failPendingMessages() {
        Cursor c = this.mResolver.query(Sms.CONTENT_URI, SMS_PROJECTION, "type = 4", null, null);
        if (c != null) {
            while (c != null) {
                try {
                    if (!c.moveToNext()) {
                        break;
                    }
                    long id = c.getLong(c.getColumnIndex("_id"));
                    String msgBody = c.getString(c.getColumnIndex("body"));
                    PushMsgInfo msgInfo = (PushMsgInfo) this.mPushMsgList.get(Long.valueOf(id));
                    if (msgInfo != null && msgInfo.resend) {
                        Sms.moveMessageToFolder(this.mContext, msgInfo.uri, 5, 0);
                    }
                } catch (Throwable th) {
                    close(c);
                }
            }
            close(c);
        }
    }

    private void removeDeletedMessages() {
        this.mResolver.delete(Sms.CONTENT_URI, "thread_id = -1", null);
    }

    public void init() {
        this.mSmsBroadcastReceiver.register();
        registerPhoneServiceStateListener();
        this.mInitialized = true;
    }

    public void deinit() {
        this.mInitialized = false;
        unregisterObserver();
        this.mSmsBroadcastReceiver.unregister();
        unRegisterPhoneServiceStateListener();
        failPendingMessages();
        removeDeletedMessages();
    }

    public boolean handleSmsSendIntent(Context context, Intent intent) {
        if (!this.mInitialized) {
            return false;
        }
        this.mSmsBroadcastReceiver.onReceive(context, intent);
        return true;
    }
}
