package com.android.bluetooth.map;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import com.android.bluetooth.mapapi.BluetoothMapContract;
import com.android.bluetooth.opp.BluetoothShare;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Calendar;
import javax.obex.HeaderSet;
import javax.obex.Operation;
import javax.obex.ServerRequestHandler;

public class BluetoothMapObexServer extends ServerRequestHandler {
    /* renamed from: D */
    private static final boolean f21D = true;
    private static final byte[] MAP_TARGET = new byte[]{(byte) -69, (byte) 88, (byte) 43, (byte) 64, (byte) 66, (byte) 12, (byte) 17, (byte) -37, (byte) -80, (byte) -34, (byte) 8, (byte) 0, (byte) 32, (byte) 12, (byte) -102, (byte) 102};
    private static final long PROVIDER_ANR_TIMEOUT = 20000;
    private static final String TAG = "BluetoothMapObexServer";
    private static final int THREADED_MAIL_HEADER_ID = 250;
    private static final long THREAD_MAIL_KEY = 1397510985;
    private static final String TYPE_GET_FOLDER_LISTING = "x-obex/folder-listing";
    private static final String TYPE_GET_MESSAGE_LISTING = "x-bt/MAP-msg-listing";
    private static final String TYPE_MESSAGE = "x-bt/message";
    private static final String TYPE_MESSAGE_UPDATE = "x-bt/MAP-messageUpdate";
    private static final String TYPE_SET_MESSAGE_STATUS = "x-bt/messageStatus";
    private static final String TYPE_SET_NOTIFICATION_REGISTRATION = "x-bt/MAP-NotificationRegistration";
    private static final int UUID_LENGTH = 16;
    /* renamed from: V */
    private static final boolean f22V = false;
    private BluetoothMapEmailSettingsItem mAccount = null;
    private long mAccountId = 0;
    private String mAuthority;
    private String mBaseEmailUriString = null;
    private Handler mCallback = null;
    private Context mContext;
    private BluetoothMapFolderElement mCurrentFolder;
    private Uri mEmailFolderUri = null;
    private boolean mEnableSmsMms = false;
    private boolean mIsAborted = false;
    private int mMasId = 0;
    private BluetoothMapContentObserver mObserver = null;
    BluetoothMapContent mOutContent;
    private ContentProviderClient mProviderClient = null;
    private ContentResolver mResolver;
    private boolean mThreadIdSupport = false;

    public BluetoothMapObexServer(Handler callback, Context context, BluetoothMapContentObserver observer, int masId, BluetoothMapEmailSettingsItem account, boolean enableSmsMms) throws RemoteException {
        this.mCallback = callback;
        this.mContext = context;
        this.mObserver = observer;
        this.mEnableSmsMms = enableSmsMms;
        this.mAccount = account;
        this.mMasId = masId;
        if (!(account == null || account.getProviderAuthority() == null)) {
            this.mAccountId = account.getAccountId();
            this.mAuthority = account.getProviderAuthority();
            this.mResolver = this.mContext.getContentResolver();
            Log.d(TAG, "BluetoothMapObexServer(): accountId=" + this.mAccountId);
            this.mBaseEmailUriString = account.mBase_uri + "/";
            Log.d(TAG, "BluetoothMapObexServer(): emailBaseUri=" + this.mBaseEmailUriString);
            this.mEmailFolderUri = BluetoothMapContract.buildFolderUri(this.mAuthority, Long.toString(this.mAccountId));
            Log.d(TAG, "BluetoothMapObexServer(): mEmailFolderUri=" + this.mEmailFolderUri);
            this.mProviderClient = acquireUnstableContentProviderOrThrow();
        }
        buildFolderStructure();
        this.mObserver.setFolderStructure(this.mCurrentFolder.getRoot());
        this.mOutContent = new BluetoothMapContent(this.mContext, this.mBaseEmailUriString);
    }

    private ContentProviderClient acquireUnstableContentProviderOrThrow() throws RemoteException {
        ContentProviderClient providerClient = this.mResolver.acquireUnstableContentProviderClient(this.mAuthority);
        if (providerClient == null) {
            throw new RemoteException("Failed to acquire provider for " + this.mAuthority);
        }
        providerClient.setDetectNotResponding(PROVIDER_ANR_TIMEOUT);
        return providerClient;
    }

    private void buildFolderStructure() throws RemoteException {
        this.mCurrentFolder = new BluetoothMapFolderElement("root", null);
        BluetoothMapFolderElement tmpFolder = this.mCurrentFolder.addFolder("telecom").addFolder("msg");
        addBaseFolders(tmpFolder);
        if (this.mEnableSmsMms) {
            addSmsMmsFolders(tmpFolder);
        }
        if (this.mEmailFolderUri != null) {
            Log.d(TAG, "buildFolderStructure(): " + this.mEmailFolderUri.toString());
            addEmailFolders(tmpFolder);
        }
    }

    private void addBaseFolders(BluetoothMapFolderElement root) {
        root.addFolder("inbox");
        root.addFolder("outbox");
        root.addFolder("sent");
        root.addFolder("deleted");
    }

    private void addSmsMmsFolders(BluetoothMapFolderElement root) {
        root.addSmsMmsFolder("inbox");
        root.addSmsMmsFolder("outbox");
        root.addSmsMmsFolder("sent");
        root.addSmsMmsFolder("deleted");
        root.addSmsMmsFolder("draft");
    }

    private void addEmailFolders(BluetoothMapFolderElement parentFolder) throws RemoteException {
        Cursor c = this.mProviderClient.query(this.mEmailFolderUri, BluetoothMapContract.BT_FOLDER_PROJECTION, "parent_id = " + parentFolder.getEmailFolderId(), null, null);
        while (c != null) {
            try {
                if (!c.moveToNext()) {
                    break;
                }
                addEmailFolders(parentFolder.addEmailFolder(c.getString(c.getColumnIndex("name")), c.getLong(c.getColumnIndex("_id"))));
            } catch (Throwable th) {
                if (c != null) {
                    c.close();
                }
            }
        }
        if (c != null) {
            c.close();
        }
    }

    public int onConnect(HeaderSet request, HeaderSet reply) {
        Log.d(TAG, "onConnect():");
        this.mThreadIdSupport = false;
        notifyUpdateWakeLock();
        try {
            byte[] uuid = (byte[]) request.getHeader(70);
            Long threadedMailKey = (Long) request.getHeader(THREADED_MAIL_HEADER_ID);
            if (uuid == null) {
                return 198;
            }
            Log.d(TAG, "onConnect(): uuid=" + Arrays.toString(uuid));
            if (uuid.length != 16) {
                Log.w(TAG, "Wrong UUID length");
                return 198;
            }
            for (int i = 0; i < 16; i++) {
                if (uuid[i] != MAP_TARGET[i]) {
                    Log.w(TAG, "Wrong UUID");
                    return 198;
                }
            }
            reply.setHeader(74, uuid);
            try {
                byte[] remote = (byte[]) request.getHeader(74);
                if (remote != null) {
                    Log.d(TAG, "onConnect(): remote=" + Arrays.toString(remote));
                    reply.setHeader(70, remote);
                }
                if (threadedMailKey != null && threadedMailKey.longValue() == THREAD_MAIL_KEY) {
                    this.mThreadIdSupport = true;
                    reply.setHeader(THREADED_MAIL_HEADER_ID, Long.valueOf(THREAD_MAIL_KEY));
                }
                if (this.mCallback != null) {
                    Message msg = Message.obtain(this.mCallback);
                    msg.what = 5001;
                    msg.sendToTarget();
                }
                return 160;
            } catch (IOException e) {
                Log.e(TAG, "Exception during onConnect:", e);
                this.mThreadIdSupport = false;
                return 208;
            }
        } catch (IOException e2) {
            Log.e(TAG, "Exception during onConnect:", e2);
            return 208;
        }
    }

    public void onDisconnect(HeaderSet req, HeaderSet resp) {
        Log.d(TAG, "onDisconnect(): enter");
        notifyUpdateWakeLock();
        resp.responseCode = 160;
        if (this.mCallback != null) {
            Message msg = Message.obtain(this.mCallback);
            msg.what = 5002;
            msg.sendToTarget();
        }
    }

    public int onAbort(HeaderSet request, HeaderSet reply) {
        Log.d(TAG, "onAbort(): enter.");
        notifyUpdateWakeLock();
        this.mIsAborted = true;
        return 160;
    }

    public int onPut(Operation op) {
        Log.d(TAG, "onPut(): enter");
        this.mIsAborted = false;
        notifyUpdateWakeLock();
        BluetoothMapAppParams appParams = null;
        try {
            HeaderSet request = op.getReceivedHeader();
            String type = (String) request.getHeader(66);
            String name = (String) request.getHeader(1);
            byte[] appParamRaw = (byte[]) request.getHeader(76);
            if (appParamRaw != null) {
                appParams = new BluetoothMapAppParams(appParamRaw);
            }
            Log.d(TAG, "type = " + type + ", name = " + name);
            if (type.equals(TYPE_MESSAGE_UPDATE)) {
                return updateInbox();
            }
            if (type.equals(TYPE_SET_NOTIFICATION_REGISTRATION)) {
                return this.mObserver.setNotificationRegistration(appParams.getNotificationStatus());
            }
            if (type.equals(TYPE_SET_MESSAGE_STATUS)) {
                return setMessageStatus(name, appParams);
            }
            return type.equals(TYPE_MESSAGE) ? pushMessage(op, name, appParams) : BluetoothShare.STATUS_RUNNING;
        } catch (RemoteException e) {
            try {
                this.mProviderClient = acquireUnstableContentProviderOrThrow();
            } catch (RemoteException e2) {
            }
            return BluetoothShare.STATUS_RUNNING;
        } catch (RemoteException e3) {
            Log.e(TAG, "Exception occured while handling request", e3);
            return this.mIsAborted ? 160 : BluetoothShare.STATUS_RUNNING;
        }
    }

    private int updateInbox() throws RemoteException {
        if (this.mAccount == null) {
            return 209;
        }
        BluetoothMapFolderElement inboxFolder = this.mCurrentFolder.getEmailFolderByName("inbox");
        if (inboxFolder == null) {
            return 209;
        }
        long accountId = this.mAccountId;
        Log.d(TAG, "updateInbox inbox=" + inboxFolder.getName() + "id=" + inboxFolder.getEmailFolderId());
        Bundle extras = new Bundle(2);
        if (accountId != -1) {
            Log.d(TAG, "updateInbox accountId=" + accountId);
            extras.putLong("UpdateFolderId", inboxFolder.getEmailFolderId());
            extras.putLong("UpdateAccountId", accountId);
            Log.d(TAG, "updateInbox in: " + Uri.parse(this.mBaseEmailUriString).toString());
            try {
                Log.d(TAG, "updateInbox call()...");
                if (this.mProviderClient.call("UpdateFolder", null, extras) != null) {
                    return 160;
                }
                Log.d(TAG, "updateInbox call failed");
                return 209;
            } catch (RemoteException e) {
                this.mProviderClient = acquireUnstableContentProviderOrThrow();
                return 211;
            } catch (NullPointerException e2) {
                Log.e(TAG, "UpdateInbox - if uri or method is null", e2);
                return 211;
            } catch (IllegalArgumentException e3) {
                Log.e(TAG, "UpdateInbox - if uri is not known", e3);
                return 211;
            }
        }
        Log.d(TAG, "updateInbox accountId=0 -> OBEX_HTTP_NOT_IMPLEMENTED");
        return 209;
    }

    private BluetoothMapFolderElement getFolderElementFromName(String folderName) {
        if (folderName == null || folderName.trim().isEmpty()) {
            BluetoothMapFolderElement folderElement = this.mCurrentFolder;
            Log.d(TAG, "no folder name supplied, setting folder to current: " + folderElement.getName());
            return folderElement;
        }
        folderElement = this.mCurrentFolder.getSubFolder(folderName);
        Log.d(TAG, "Folder name: " + folderName + " resulted in this element: " + folderElement.getName());
        return folderElement;
    }

    private int pushMessage(Operation op, String folderName, BluetoothMapAppParams appParams) {
        if (appParams.getCharset() == -1) {
            Log.d(TAG, "pushMessage: Missing charset - unable to decode message content. appParams.getCharset() = " + appParams.getCharset());
            return 204;
        }
        InputStream bMsgStream = null;
        try {
            BluetoothMapFolderElement folderElement = getFolderElementFromName(folderName);
            if (folderElement == null) {
                Log.w(TAG, "pushMessage: folderElement == null - sending OBEX_HTTP_PRECON_FAILED");
                if (bMsgStream == null) {
                    return 204;
                }
                try {
                    bMsgStream.close();
                    return 204;
                } catch (IOException e) {
                    return 204;
                }
            }
            folderName = folderElement.getName();
            if (folderName.equals("outbox") || folderName.equals("draft")) {
                bMsgStream = op.openInputStream();
                BluetoothMapbMessage message = BluetoothMapbMessage.parse(bMsgStream, appParams.getCharset());
                if (this.mObserver == null || message == null) {
                    Log.w(TAG, "mObserver or parsed message not available");
                    if (bMsgStream == null) {
                        return 211;
                    }
                    try {
                        bMsgStream.close();
                        return 211;
                    } catch (IOException e2) {
                        return 211;
                    }
                } else if (!(message.getType().equals(TYPE.EMAIL) && folderElement.getEmailFolderId() == -1) && (!(message.getType().equals(TYPE.SMS_GSM) || message.getType().equals(TYPE.SMS_CDMA) || message.getType().equals(TYPE.MMS)) || folderElement.hasSmsMmsContent())) {
                    long handle = this.mObserver.pushMessage(message, folderElement, appParams, this.mBaseEmailUriString);
                    Log.d(TAG, "pushMessage handle: " + handle);
                    if (handle < 0) {
                        Log.w(TAG, "Message  handle not created");
                        if (bMsgStream == null) {
                            return 211;
                        }
                        try {
                            bMsgStream.close();
                            return 211;
                        } catch (IOException e3) {
                            return 211;
                        }
                    }
                    HeaderSet replyHeaders = new HeaderSet();
                    String handleStr = BluetoothMapUtils.getMapHandle(handle, message.getType());
                    Log.d(TAG, "handleStr: " + handleStr + " message.getType(): " + message.getType());
                    replyHeaders.setHeader(1, handleStr);
                    op.sendHeaders(replyHeaders);
                    if (bMsgStream != null) {
                        try {
                            bMsgStream.close();
                        } catch (IOException e4) {
                        }
                    }
                    return 160;
                } else {
                    Log.w(TAG, "Wrong message type recieved");
                    if (bMsgStream == null) {
                        return 198;
                    }
                    try {
                        bMsgStream.close();
                        return 198;
                    } catch (IOException e5) {
                        return 198;
                    }
                }
            }
            Log.d(TAG, "pushMessage: Is only allowed to outbox and draft. folderName=" + folderName);
            if (bMsgStream == null) {
                return 198;
            }
            try {
                bMsgStream.close();
                return 198;
            } catch (IOException e6) {
                return 198;
            }
        } catch (RemoteException e7) {
            try {
                this.mProviderClient = acquireUnstableContentProviderOrThrow();
            } catch (RemoteException e8) {
            }
            if (bMsgStream == null) {
                return BluetoothShare.STATUS_RUNNING;
            }
            try {
                bMsgStream.close();
                return BluetoothShare.STATUS_RUNNING;
            } catch (IOException e9) {
                return BluetoothShare.STATUS_RUNNING;
            }
        } catch (IllegalArgumentException e10) {
            Log.e(TAG, "Wrongly formatted bMessage received", e10);
            if (bMsgStream == null) {
                return 204;
            }
            try {
                bMsgStream.close();
                return 204;
            } catch (IOException e11) {
                return 204;
            }
        } catch (IOException e12) {
            Log.e(TAG, "Exception occured: ", e12);
            if (this.mIsAborted) {
                Log.d(TAG, "PushMessage Operation Aborted");
                if (bMsgStream == null) {
                    return 160;
                }
                try {
                    bMsgStream.close();
                    return 160;
                } catch (IOException e13) {
                    return 160;
                }
            } else if (bMsgStream == null) {
                return BluetoothShare.STATUS_RUNNING;
            } else {
                try {
                    bMsgStream.close();
                    return BluetoothShare.STATUS_RUNNING;
                } catch (IOException e14) {
                    return BluetoothShare.STATUS_RUNNING;
                }
            }
        } catch (RemoteException e15) {
            Log.e(TAG, "Exception:", e15);
            if (bMsgStream == null) {
                return BluetoothShare.STATUS_RUNNING;
            }
            try {
                bMsgStream.close();
                return BluetoothShare.STATUS_RUNNING;
            } catch (IOException e16) {
                return BluetoothShare.STATUS_RUNNING;
            }
        } catch (Throwable th) {
            if (bMsgStream != null) {
                try {
                    bMsgStream.close();
                } catch (IOException e17) {
                }
            }
        }
    }

    private int setMessageStatus(String msgHandle, BluetoothMapAppParams appParams) {
        int indicator = appParams.getStatusIndicator();
        int value = appParams.getStatusValue();
        if (indicator == -1 || value == -1 || msgHandle == null) {
            return 204;
        }
        if (this.mObserver == null) {
            Log.d(TAG, "Error: no mObserver!");
            return 211;
        }
        try {
            long handle = BluetoothMapUtils.getCpHandle(msgHandle);
            TYPE msgType = BluetoothMapUtils.getMsgTypeFromHandle(msgHandle);
            Log.d(TAG, "setMessageStatus. Handle:" + handle + ", MsgType: " + msgType);
            if (indicator != 1) {
                try {
                    if (!this.mObserver.setMessageStatusRead(handle, msgType, this.mBaseEmailUriString, value)) {
                        Log.d(TAG, "not able to update the message");
                        return 211;
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Error in setMessageStatusRead()", e);
                    return 211;
                }
            } else if (!this.mObserver.setMessageStatusDeleted(handle, msgType, this.mCurrentFolder, this.mBaseEmailUriString, value)) {
                return 211;
            }
            return 160;
        } catch (NumberFormatException e2) {
            Log.w(TAG, "Wrongly formatted message handle: " + msgHandle);
            return 204;
        }
    }

    public int onSetPath(HeaderSet request, HeaderSet reply, boolean backup, boolean create) {
        notifyUpdateWakeLock();
        try {
            String folderName = (String) request.getHeader(1);
            Log.d(TAG, "onSetPath name is " + folderName + " backup: " + backup + " create: " + create);
            if (backup) {
                if (this.mCurrentFolder.getParent() == null) {
                    return BluetoothShare.STATUS_RUNNING;
                }
                this.mCurrentFolder = this.mCurrentFolder.getParent();
            }
            if (folderName != null && !folderName.trim().isEmpty()) {
                BluetoothMapFolderElement folder = this.mCurrentFolder.getSubFolder(folderName);
                if (folder == null) {
                    return BluetoothShare.STATUS_RUNNING;
                }
                this.mCurrentFolder = folder;
            } else if (!backup) {
                this.mCurrentFolder = this.mCurrentFolder.getRoot();
            }
            return 160;
        } catch (Exception e) {
            Log.e(TAG, "request headers error", e);
            return BluetoothShare.STATUS_RUNNING;
        }
    }

    public void onClose() {
        if (this.mCallback != null) {
            Message msg = Message.obtain(this.mCallback);
            msg.what = 5000;
            msg.arg1 = this.mMasId;
            msg.sendToTarget();
            Log.d(TAG, "onClose(): msg MSG_SERVERSESSION_CLOSE sent out.");
        }
        if (this.mProviderClient != null) {
            this.mProviderClient.release();
            this.mProviderClient = null;
        }
    }

    public int onGet(Operation op) {
        notifyUpdateWakeLock();
        this.mIsAborted = false;
        BluetoothMapAppParams appParams = null;
        try {
            HeaderSet request = op.getReceivedHeader();
            String type = (String) request.getHeader(66);
            String name = (String) request.getHeader(1);
            byte[] appParamRaw = (byte[]) request.getHeader(76);
            if (appParamRaw != null) {
                appParams = new BluetoothMapAppParams(appParamRaw);
            }
            Log.d(TAG, "OnGet type is " + type + " name is " + name);
            if (type == null) {
                return BluetoothShare.STATUS_RUNNING;
            }
            if (type.equals(TYPE_GET_FOLDER_LISTING)) {
                return sendFolderListingRsp(op, appParams);
            }
            if (type.equals(TYPE_GET_MESSAGE_LISTING)) {
                return sendMessageListingRsp(op, appParams, name);
            }
            if (type.equals(TYPE_MESSAGE)) {
                return sendGetMessageRsp(op, name, appParams);
            }
            Log.w(TAG, "unknown type request: " + type);
            return 198;
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Exception:", e);
            return 204;
        } catch (ParseException e2) {
            Log.e(TAG, "Exception:", e2);
            return 204;
        } catch (Exception e3) {
            Log.e(TAG, "Exception occured while handling request", e3);
            if (!this.mIsAborted) {
                return BluetoothShare.STATUS_RUNNING;
            }
            Log.d(TAG, "onGet Operation Aborted");
            return 160;
        }
    }

    private int sendMessageListingRsp(Operation op, BluetoothMapAppParams appParams, String folderName) {
        OutputStream outStream = null;
        byte[] outBytes = null;
        int bytesWritten = 0;
        HeaderSet replyHeaders = new HeaderSet();
        BluetoothMapAppParams outAppParams = new BluetoothMapAppParams();
        if (appParams == null) {
            appParams = new BluetoothMapAppParams();
            appParams.setMaxListCount(1024);
            appParams.setStartOffset(0);
        }
        BluetoothMapFolderElement folderToList = getFolderElementFromName(folderName);
        if (folderToList == null) {
            Log.w(TAG, "sendMessageListingRsp: folderToList == null - sending OBEX_HTTP_BAD_REQUEST");
            return BluetoothShare.STATUS_RUNNING;
        }
        try {
            boolean hasUnread;
            outStream = op.openOutputStream();
            if (appParams.getMaxListCount() == -1) {
                appParams.setMaxListCount(1024);
            }
            if (appParams.getStartOffset() == -1) {
                appParams.setStartOffset(0);
            }
            if (appParams.getMaxListCount() != 0) {
                BluetoothMapMessageListing outList = this.mOutContent.msgListing(folderToList, appParams);
                outAppParams.setMessageListingSize(outList.getCount());
                outBytes = outList.encode(this.mThreadIdSupport);
                hasUnread = outList.hasUnread();
            } else {
                int listSize = this.mOutContent.msgListingSize(folderToList, appParams);
                hasUnread = this.mOutContent.msgListingHasUnread(folderToList, appParams);
                outAppParams.setMessageListingSize(listSize);
                op.noBodyHeader();
            }
            if (hasUnread) {
                outAppParams.setNewMessage(1);
            } else {
                outAppParams.setNewMessage(0);
            }
            outAppParams.setMseTime(Calendar.getInstance().getTime().getTime());
            replyHeaders.setHeader(76, outAppParams.EncodeParams());
            op.sendHeaders(replyHeaders);
            int maxChunkSize = op.getMaxPacketSize();
            if (outBytes != null) {
                while (bytesWritten < outBytes.length && !this.mIsAborted) {
                    try {
                        int bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                        outStream.write(outBytes, bytesWritten, bytesToWrite);
                        bytesWritten += bytesToWrite;
                    } catch (IllegalArgumentException e) {
                        Log.w(TAG, e);
                        if (outStream != null) {
                            try {
                                outStream.close();
                            } catch (IOException e2) {
                            }
                        }
                    } catch (Throwable th) {
                        if (outStream != null) {
                            try {
                                outStream.close();
                            } catch (IOException e3) {
                            }
                        }
                    }
                }
                if (outStream != null) {
                    try {
                        outStream.close();
                    } catch (IOException e4) {
                    }
                }
                if (!(bytesWritten == outBytes.length || this.mIsAborted)) {
                    Log.w(TAG, "sendMessageListingRsp: bytesWritten != outBytes.length - sending OBEX_HTTP_BAD_REQUEST");
                    return BluetoothShare.STATUS_RUNNING;
                }
            } else if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e5) {
                }
            }
            return 160;
        } catch (IOException e6) {
            Log.w(TAG, "sendMessageListingRsp: IOException - sending OBEX_HTTP_BAD_REQUEST", e6);
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e7) {
                }
            }
            if (!this.mIsAborted) {
                return BluetoothShare.STATUS_RUNNING;
            }
            Log.d(TAG, "sendMessageListingRsp Operation Aborted");
            return 160;
        } catch (IOException e62) {
            Log.w(TAG, "sendMessageListingRsp: IllegalArgumentException - sending OBEX_HTTP_BAD_REQUEST", e62);
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e8) {
                }
            }
            return BluetoothShare.STATUS_RUNNING;
        }
    }

    private int sendFolderListingRsp(Operation op, BluetoothMapAppParams appParams) {
        OutputStream outStream = null;
        byte[] outBytes = null;
        BluetoothMapAppParams outAppParams = new BluetoothMapAppParams();
        int bytesWritten = 0;
        HeaderSet replyHeaders = new HeaderSet();
        if (appParams == null) {
            appParams = new BluetoothMapAppParams();
            appParams.setMaxListCount(1024);
        }
        try {
            int maxListCount = appParams.getMaxListCount();
            int listStartOffset = appParams.getStartOffset();
            if (listStartOffset == -1) {
                listStartOffset = 0;
            }
            if (maxListCount == -1) {
                maxListCount = 1024;
            }
            if (maxListCount != 0) {
                outBytes = this.mCurrentFolder.encode(listStartOffset, maxListCount);
                outStream = op.openOutputStream();
            }
            outAppParams.setFolderListingSize(this.mCurrentFolder.getSubFolderCount());
            replyHeaders.setHeader(76, outAppParams.EncodeParams());
            op.sendHeaders(replyHeaders);
            int maxChunkSize = op.getMaxPacketSize();
            if (outBytes == null) {
                return 160;
            }
            while (bytesWritten < outBytes.length && !this.mIsAborted) {
                try {
                    int bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                } catch (IOException e) {
                    if (outStream != null) {
                        try {
                            outStream.close();
                        } catch (IOException e2) {
                        }
                    }
                } catch (Throwable th) {
                    if (outStream != null) {
                        try {
                            outStream.close();
                        } catch (IOException e3) {
                        }
                    }
                }
            }
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e4) {
                }
            }
            if (bytesWritten == outBytes.length || this.mIsAborted) {
                return 160;
            }
            return BluetoothShare.STATUS_RUNNING;
        } catch (IOException e1) {
            Log.w(TAG, "sendFolderListingRsp: IOException - sending OBEX_HTTP_BAD_REQUEST Exception:", e1);
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e5) {
                }
            }
            if (!this.mIsAborted) {
                return BluetoothShare.STATUS_RUNNING;
            }
            Log.d(TAG, "sendFolderListingRsp Operation Aborted");
            return 160;
        } catch (IllegalArgumentException e12) {
            Log.w(TAG, "sendFolderListingRsp: IllegalArgumentException - sending OBEX_HTTP_BAD_REQUEST Exception:", e12);
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e6) {
                }
            }
            return 204;
        }
    }

    private int sendGetMessageRsp(Operation op, String handle, BluetoothMapAppParams appParams) {
        OutputStream outStream = null;
        int bytesWritten = 0;
        try {
            byte[] outBytes = this.mOutContent.getMessage(handle, appParams, this.mCurrentFolder);
            outStream = op.openOutputStream();
            if (BluetoothMapUtils.getMsgTypeFromHandle(handle).equals(TYPE.EMAIL) && appParams.getFractionRequest() == 0) {
                BluetoothMapAppParams outAppParams = new BluetoothMapAppParams();
                HeaderSet replyHeaders = new HeaderSet();
                outAppParams.setFractionDeliver(1);
                replyHeaders.setHeader(76, outAppParams.EncodeParams());
                op.sendHeaders(replyHeaders);
            }
            int maxChunkSize = op.getMaxPacketSize();
            if (outBytes == null) {
                return 160;
            }
            while (bytesWritten < outBytes.length && !this.mIsAborted) {
                try {
                    int bytesToWrite = Math.min(maxChunkSize, outBytes.length - bytesWritten);
                    outStream.write(outBytes, bytesWritten, bytesToWrite);
                    bytesWritten += bytesToWrite;
                } catch (IllegalArgumentException e) {
                    if (e.getMessage().equals("Abort Received")) {
                        Log.w(TAG, "getMessage() Aborted...", e);
                    }
                    if (outStream != null) {
                        try {
                            outStream.close();
                        } catch (IOException e2) {
                        }
                    }
                } catch (Throwable th) {
                    if (outStream != null) {
                        try {
                            outStream.close();
                        } catch (IOException e3) {
                        }
                    }
                }
            }
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e4) {
                }
            }
            if (bytesWritten == outBytes.length || this.mIsAborted) {
                return 160;
            }
            return BluetoothShare.STATUS_RUNNING;
        } catch (IOException e5) {
            Log.w(TAG, "sendGetMessageRsp: IOException - sending OBEX_HTTP_BAD_REQUEST", e5);
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e6) {
                }
            }
            if (!this.mIsAborted) {
                return BluetoothShare.STATUS_RUNNING;
            }
            Log.d(TAG, "sendGetMessageRsp Operation Aborted");
            return 160;
        } catch (IllegalArgumentException e7) {
            Log.w(TAG, "sendGetMessageRsp: IllegalArgumentException (e.g. invalid handle) - sending OBEX_HTTP_BAD_REQUEST", e7);
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e8) {
                }
            }
            return BluetoothShare.STATUS_RUNNING;
        }
    }

    private void notifyUpdateWakeLock() {
        if (this.mCallback != null) {
            Message msg = Message.obtain(this.mCallback);
            msg.what = 5005;
            msg.sendToTarget();
        }
    }

    private static final void logHeader(HeaderSet hs) {
        Log.v(TAG, "Dumping HeaderSet " + hs.toString());
        try {
            Log.v(TAG, "CONNECTION_ID : " + hs.getHeader(203));
            Log.v(TAG, "NAME : " + hs.getHeader(1));
            Log.v(TAG, "TYPE : " + hs.getHeader(66));
            Log.v(TAG, "TARGET : " + hs.getHeader(70));
            Log.v(TAG, "WHO : " + hs.getHeader(74));
            Log.v(TAG, "APPLICATION_PARAMETER : " + hs.getHeader(76));
        } catch (IOException e) {
            Log.e(TAG, "dump HeaderSet error " + e);
        }
        Log.v(TAG, "NEW!!! Dumping HeaderSet END");
    }
}
