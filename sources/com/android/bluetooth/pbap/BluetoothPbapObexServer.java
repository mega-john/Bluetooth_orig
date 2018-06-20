package com.android.bluetooth.pbap;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import com.android.bluetooth.btservice.AbstractionLayer;
import com.android.bluetooth.opp.BluetoothShare;
import java.io.IOException;
import java.io.OutputStream;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.Arrays;
import javax.obex.ApplicationParameter;
import javax.obex.HeaderSet;
import javax.obex.Operation;
import javax.obex.ServerRequestHandler;

public class BluetoothPbapObexServer extends ServerRequestHandler {
    private static int CALLLOG_NUM_LIMIT = 50;
    private static final String CCH = "cch";
    private static final String CCH_PATH = "/telecom/cch";
    /* renamed from: D */
    private static final boolean f70D = true;
    private static final String ICH = "ich";
    private static final String ICH_PATH = "/telecom/ich";
    private static final String[] LEGAL_PATH = new String[]{"/telecom", PB_PATH, ICH_PATH, OCH_PATH, MCH_PATH, CCH_PATH};
    private static final String[] LEGAL_PATH_WITH_SIM = new String[]{"/telecom", PB_PATH, ICH_PATH, OCH_PATH, MCH_PATH, CCH_PATH, "/SIM1", "/SIM1/telecom", "/SIM1/telecom/ich", "/SIM1/telecom/och", "/SIM1/telecom/mch", "/SIM1/telecom/cch", "/SIM1/telecom/pb"};
    private static final String MCH = "mch";
    private static final String MCH_PATH = "/telecom/mch";
    private static final int NEED_SEND_BODY = -1;
    private static final String OCH = "och";
    private static final String OCH_PATH = "/telecom/och";
    public static int ORDER_BY_ALPHABETICAL = 1;
    public static int ORDER_BY_INDEXED = 0;
    private static final String PB = "pb";
    private static final byte[] PBAP_TARGET = new byte[]{(byte) 121, (byte) 97, (byte) 53, (byte) -16, (byte) -16, (byte) -59, (byte) 17, (byte) -40, (byte) 9, (byte) 102, (byte) 8, (byte) 0, (byte) 32, (byte) 12, (byte) -102, (byte) 102};
    private static final String PB_PATH = "/telecom/pb";
    private static final String SIM1 = "SIM1";
    private static final String TAG = "BluetoothPbapObexServer";
    private static final String TYPE_LISTING = "x-bt/vcard-listing";
    private static final String TYPE_PB = "x-bt/phonebook";
    private static final String TYPE_VCARD = "x-bt/vcard";
    private static final int UUID_LENGTH = 16;
    /* renamed from: V */
    private static final boolean f71V = false;
    private static final int VCARD_NAME_SUFFIX_LENGTH = 5;
    public static boolean sIsAborted = f71V;
    private Handler mCallback = null;
    private Context mContext;
    private String mCurrentPath = "";
    private int mMissedCallSize = 0;
    private boolean mNeedNewMissedCallsNum = f71V;
    private boolean mNeedPhonebookSize = f71V;
    private int mOrderBy = ORDER_BY_INDEXED;
    private BluetoothPbapVcardManager mVcardManager;

    private class AppParamValue {
        public byte[] filter = new byte[]{(byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0};
        public boolean ignorefilter = true;
        public int listStartOffset = 0;
        public int maxListCount = 65535;
        public int needTag = 0;
        public String order = "";
        public String searchAttr = "";
        public String searchValue = "";
        public boolean vcard21 = true;

        public void dump() {
            Log.i(BluetoothPbapObexServer.TAG, "maxListCount=" + this.maxListCount + " listStartOffset=" + this.listStartOffset + " searchValue=" + this.searchValue + " searchAttr=" + this.searchAttr + " needTag=" + this.needTag + " vcard21=" + this.vcard21 + " order=" + this.order);
        }
    }

    public static class ContentType {
        public static final int COMBINED_CALL_HISTORY = 5;
        public static final int INCOMING_CALL_HISTORY = 2;
        public static final int MISSED_CALL_HISTORY = 4;
        public static final int OUTGOING_CALL_HISTORY = 3;
        public static final int PHONEBOOK = 1;
    }

    /* JADX WARNING: inconsistent code. */
    /* Code decompiled incorrectly, please refer to instructions dump. */
    private final int pushHeader(javax.obex.Operation r6, javax.obex.HeaderSet r7) {
        /* JADX: method processing error */
/*
Error: jadx.core.utils.exceptions.JadxRuntimeException: Can't find block by offset: 0x0025 in list [B:5:0x0023]
	at jadx.core.utils.BlockUtils.getBlockByOffset(BlockUtils.java:43)
	at jadx.core.dex.instructions.IfNode.initBlocks(IfNode.java:60)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.initBlocksInIfNodes(BlockFinish.java:48)
	at jadx.core.dex.visitors.blocksmaker.BlockFinish.visit(BlockFinish.java:33)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:31)
	at jadx.core.dex.visitors.DepthTraversal.visit(DepthTraversal.java:17)
	at jadx.core.ProcessClass.process(ProcessClass.java:34)
	at jadx.api.JadxDecompiler.processClass(JadxDecompiler.java:282)
	at jadx.api.JavaClass.decompile(JavaClass.java:62)
*/
        /*
        r5 = this;
        r1 = 0;
        r3 = "BluetoothPbapObexServer";
        r4 = "Push Header";
        android.util.Log.d(r3, r4);
        r3 = "BluetoothPbapObexServer";
        r4 = r7.toString();
        android.util.Log.d(r3, r4);
        r2 = 160; // 0xa0 float:2.24E-43 double:7.9E-322;
        r6.sendHeaders(r7);	 Catch:{ IOException -> 0x0026, all -> 0x003b }
        r1 = r6.openOutputStream();	 Catch:{ IOException -> 0x0026, all -> 0x003b }
        r1.flush();	 Catch:{ IOException -> 0x0026, all -> 0x003b }
        r3 = closeStream(r1, r6);
        if (r3 != 0) goto L_0x0025;
    L_0x0023:
        r2 = 208; // 0xd0 float:2.91E-43 double:1.03E-321;
    L_0x0025:
        return r2;
    L_0x0026:
        r0 = move-exception;
        r3 = "BluetoothPbapObexServer";	 Catch:{ IOException -> 0x0026, all -> 0x003b }
        r4 = r0.toString();	 Catch:{ IOException -> 0x0026, all -> 0x003b }
        android.util.Log.e(r3, r4);	 Catch:{ IOException -> 0x0026, all -> 0x003b }
        r2 = 208; // 0xd0 float:2.91E-43 double:1.03E-321;
        r3 = closeStream(r1, r6);
        if (r3 != 0) goto L_0x0025;
    L_0x0038:
        r2 = 208; // 0xd0 float:2.91E-43 double:1.03E-321;
        goto L_0x0025;
    L_0x003b:
        r3 = move-exception;
        r4 = closeStream(r1, r6);
        if (r4 != 0) goto L_0x0044;
    L_0x0042:
        r2 = 208; // 0xd0 float:2.91E-43 double:1.03E-321;
    L_0x0044:
        throw r3;
        */
        throw new UnsupportedOperationException("Method not decompiled: com.android.bluetooth.pbap.BluetoothPbapObexServer.pushHeader(javax.obex.Operation, javax.obex.HeaderSet):int");
    }

    public BluetoothPbapObexServer(Handler callback, Context context) {
        this.mCallback = callback;
        this.mContext = context;
        this.mVcardManager = new BluetoothPbapVcardManager(this.mContext);
    }

    public int onConnect(HeaderSet request, HeaderSet reply) {
        notifyUpdateWakeLock();
        try {
            byte[] uuid = (byte[]) request.getHeader(70);
            if (uuid == null) {
                return 198;
            }
            Log.d(TAG, "onConnect(): uuid=" + Arrays.toString(uuid));
            if (uuid.length != 16) {
                Log.w(TAG, "Wrong UUID length");
                return 198;
            }
            for (int i = 0; i < 16; i++) {
                if (uuid[i] != PBAP_TARGET[i]) {
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
                Message msg = Message.obtain(this.mCallback);
                msg.what = 5001;
                msg.sendToTarget();
                return 160;
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                return 208;
            }
        } catch (IOException e2) {
            Log.e(TAG, e2.toString());
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
        sIsAborted = true;
        return 160;
    }

    public int onPut(Operation op) {
        Log.d(TAG, "onPut(): not support PUT request.");
        notifyUpdateWakeLock();
        return BluetoothShare.STATUS_RUNNING;
    }

    public int onDelete(HeaderSet request, HeaderSet reply) {
        Log.d(TAG, "onDelete(): not support PUT request.");
        notifyUpdateWakeLock();
        return BluetoothShare.STATUS_RUNNING;
    }

    public int onSetPath(HeaderSet request, HeaderSet reply, boolean backup, boolean create) {
        Log.d(TAG, "before setPath, mCurrentPath ==  " + this.mCurrentPath);
        notifyUpdateWakeLock();
        String current_path_tmp = this.mCurrentPath;
        try {
            String tmp_path = (String) request.getHeader(1);
            Log.d(TAG, "backup=" + backup + " create=" + create + " name=" + tmp_path);
            if (backup) {
                if (current_path_tmp.length() != 0) {
                    current_path_tmp = current_path_tmp.substring(0, current_path_tmp.lastIndexOf("/"));
                }
            } else if (tmp_path == null) {
                current_path_tmp = "";
            } else {
                current_path_tmp = current_path_tmp + "/" + tmp_path;
            }
            if (current_path_tmp.length() == 0 || isLegalPath(current_path_tmp)) {
                this.mCurrentPath = current_path_tmp;
                return 160;
            } else if (create) {
                Log.w(TAG, "path create is forbidden!");
                return 195;
            } else {
                Log.w(TAG, "path is not legal");
                return 196;
            }
        } catch (IOException e) {
            Log.e(TAG, "Get name header fail");
            return 208;
        }
    }

    public void onClose() {
        if (this.mCallback != null) {
            Message msg = Message.obtain(this.mCallback);
            msg.what = 5000;
            msg.sendToTarget();
            Log.d(TAG, "onClose(): msg MSG_SERVERSESSION_CLOSE sent out.");
        }
    }

    public int onGet(Operation op) {
        notifyUpdateWakeLock();
        sIsAborted = f71V;
        HeaderSet reply = new HeaderSet();
        String type = "";
        String name = "";
        AppParamValue appParamValue = new AppParamValue();
        try {
            HeaderSet request = op.getReceivedHeader();
            type = (String) request.getHeader(66);
            name = (String) request.getHeader(1);
            byte[] appParam = (byte[]) request.getHeader(76);
            Log.d(TAG, "OnGet type is " + type + "; name is " + name);
            if (type == null) {
                return 198;
            }
            boolean validName = true;
            if (TextUtils.isEmpty(name)) {
                validName = f71V;
            }
            if (!validName || (validName && type.equals(TYPE_VCARD))) {
                Log.d(TAG, "Guess what carkit actually want from current path (" + this.mCurrentPath + ")");
                if (this.mCurrentPath.equals(PB_PATH)) {
                    appParamValue.needTag = 1;
                } else if (this.mCurrentPath.equals(ICH_PATH)) {
                    appParamValue.needTag = 2;
                } else if (this.mCurrentPath.equals(OCH_PATH)) {
                    appParamValue.needTag = 3;
                } else if (this.mCurrentPath.equals(MCH_PATH)) {
                    appParamValue.needTag = 4;
                    this.mNeedNewMissedCallsNum = true;
                } else if (this.mCurrentPath.equals(CCH_PATH)) {
                    appParamValue.needTag = 5;
                } else {
                    Log.w(TAG, "mCurrentpath is not valid path!!!");
                    return 198;
                }
                Log.v(TAG, "onGet(): appParamValue.needTag=" + appParamValue.needTag);
            } else if (name.contains(SIM1.subSequence(0, SIM1.length()))) {
                Log.w(TAG, "Not support access SIM card info!");
                return 198;
            } else if (isNameMatchTarget(name, PB)) {
                appParamValue.needTag = 1;
                Log.v(TAG, "download phonebook request");
            } else if (isNameMatchTarget(name, ICH)) {
                appParamValue.needTag = 2;
                Log.v(TAG, "download incoming calls request");
            } else if (isNameMatchTarget(name, OCH)) {
                appParamValue.needTag = 3;
                Log.v(TAG, "download outgoing calls request");
            } else if (isNameMatchTarget(name, MCH)) {
                appParamValue.needTag = 4;
                this.mNeedNewMissedCallsNum = true;
                Log.v(TAG, "download missed calls request");
            } else if (isNameMatchTarget(name, CCH)) {
                appParamValue.needTag = 5;
                Log.v(TAG, "download combined calls request");
            } else {
                Log.w(TAG, "Input name doesn't contain valid info!!!");
                return 196;
            }
            if (appParam != null && !parseApplicationParameter(appParam, appParamValue)) {
                return BluetoothShare.STATUS_RUNNING;
            }
            if (type.equals(TYPE_LISTING)) {
                return pullVcardListing(appParam, appParamValue, reply, op);
            }
            if (type.equals(TYPE_VCARD)) {
                return pullVcardEntry(appParam, appParamValue, op, name, this.mCurrentPath);
            } else if (type.equals(TYPE_PB)) {
                return pullPhonebook(appParam, appParamValue, reply, op, name);
            } else {
                Log.w(TAG, "unknown type request!!!");
                return 198;
            }
        } catch (IOException e) {
            Log.e(TAG, "request headers error");
            return 208;
        }
    }

    private boolean isNameMatchTarget(String name, String target) {
        String contentTypeName = name;
        if (contentTypeName.endsWith(".vcf")) {
            contentTypeName = contentTypeName.substring(0, contentTypeName.length() - ".vcf".length());
        }
        for (String subName : contentTypeName.split("/")) {
            if (subName.equals(target)) {
                return true;
            }
        }
        return f71V;
    }

    private final boolean isLegalPath(String str) {
        if (str.length() == 0) {
            return true;
        }
        for (Object equals : LEGAL_PATH) {
            if (str.equals(equals)) {
                return true;
            }
        }
        return f71V;
    }

    private final boolean parseApplicationParameter(byte[] appParam, AppParamValue appParamValue) {
        int i = 0;
        boolean parseOk = true;
        while (i < appParam.length && parseOk) {
            int lowValue;
            switch (appParam[i]) {
                case (byte) 1:
                    i += 2;
                    appParamValue.order = Byte.toString(appParam[i]);
                    i++;
                    break;
                case (byte) 2:
                    i++;
                    int length = appParam[i];
                    if (length != 0) {
                        if (appParam[i + length] == (byte) 0) {
                            appParamValue.searchValue = new String(appParam, i + 1, length - 1);
                        } else {
                            appParamValue.searchValue = new String(appParam, i + 1, length);
                        }
                        i = (i + length) + 1;
                        break;
                    }
                    parseOk = f71V;
                    break;
                case (byte) 3:
                    i += 2;
                    appParamValue.searchAttr = Byte.toString(appParam[i]);
                    i++;
                    break;
                case (byte) 4:
                    i += 2;
                    if (appParam[i] == (byte) 0 && appParam[i + 1] == (byte) 0) {
                        this.mNeedPhonebookSize = true;
                    } else {
                        lowValue = appParam[i + 1] & 255;
                        appParamValue.maxListCount = ((appParam[i] & 255) * 256) + lowValue;
                    }
                    i += 2;
                    break;
                case (byte) 5:
                    i += 2;
                    lowValue = appParam[i + 1] & 255;
                    appParamValue.listStartOffset = ((appParam[i] & 255) * 256) + lowValue;
                    i += 2;
                    break;
                case (byte) 6:
                    i += 2;
                    for (int index = 0; index < 8; index++) {
                        if (appParam[i + index] != (byte) 0) {
                            appParamValue.ignorefilter = f71V;
                            appParamValue.filter[index] = appParam[i + index];
                        }
                    }
                    i += 8;
                    break;
                case AbstractionLayer.BT_STATUS_PARM_INVALID /*7*/:
                    i += 2;
                    if (appParam[i] != (byte) 0) {
                        appParamValue.vcard21 = f71V;
                    }
                    i++;
                    break;
                default:
                    parseOk = f71V;
                    Log.e(TAG, "Parse Application Parameter error");
                    break;
            }
        }
        appParamValue.dump();
        return parseOk;
    }

    private final int sendVcardListingXml(int type, Operation op, int maxListCount, int listStartOffset, String searchValue, String searchAttr) {
        StringBuilder result = new StringBuilder();
        result.append("<?xml version=\"1.0\"?>");
        result.append("<!DOCTYPE vcard-listing SYSTEM \"vcard-listing.dtd\">");
        result.append("<vCard-listing version=\"1.0\">");
        if (type == 1) {
            int itemsFound;
            if (searchAttr.equals("0")) {
                itemsFound = createList(maxListCount, listStartOffset, searchValue, result, "name");
            } else {
                if (!searchAttr.equals("1")) {
                    return 204;
                }
                itemsFound = createList(maxListCount, listStartOffset, searchValue, result, "number");
            }
        } else {
            ArrayList<String> nameList = this.mVcardManager.loadCallHistoryList(type);
            int requestSize = nameList.size() >= maxListCount ? maxListCount : nameList.size();
            int startPoint = listStartOffset;
            int endPoint = startPoint + requestSize;
            if (endPoint > nameList.size()) {
                endPoint = nameList.size();
            }
            Log.d(TAG, "call log list, size=" + requestSize + " offset=" + listStartOffset);
            for (int j = startPoint; j < endPoint; j++) {
                writeVCardEntry(j + 1, (String) nameList.get(j), result);
            }
        }
        result.append("</vCard-listing>");
        return pushBytes(op, result.toString());
    }

    private int createList(int maxListCount, int listStartOffset, String searchValue, StringBuilder result, String type) {
        int itemsFound = 0;
        ArrayList<String> nameList = this.mVcardManager.getPhonebookNameList(this.mOrderBy);
        int requestSize = nameList.size() >= maxListCount ? maxListCount : nameList.size();
        int listSize = nameList.size();
        String compareValue = "";
        Log.d(TAG, "search by " + type + ", requestSize=" + requestSize + " offset=" + listStartOffset + " searchValue=" + searchValue);
        int pos;
        String currentValue;
        if (type.equals("number")) {
            ArrayList<String> names = this.mVcardManager.getContactNamesByNumber(searchValue);
            for (int i = 0; i < names.size(); i++) {
                compareValue = ((String) names.get(i)).trim();
                Log.d(TAG, "compareValue=" + compareValue);
                for (pos = listStartOffset; pos < listSize && itemsFound < requestSize; pos++) {
                    currentValue = (String) nameList.get(pos);
                    Log.d(TAG, "currentValue=" + currentValue);
                    if (currentValue.equals(compareValue)) {
                        itemsFound++;
                        if (currentValue.contains(",")) {
                            currentValue = currentValue.substring(0, currentValue.lastIndexOf(44));
                        }
                        writeVCardEntry(pos, currentValue, result);
                    }
                }
                if (itemsFound >= requestSize) {
                    break;
                }
            }
        } else {
            if (searchValue != null) {
                compareValue = searchValue.trim();
            }
            for (pos = listStartOffset; pos < listSize && itemsFound < requestSize; pos++) {
                currentValue = (String) nameList.get(pos);
                if (currentValue.contains(",")) {
                    currentValue = currentValue.substring(0, currentValue.lastIndexOf(44));
                }
                if (searchValue.isEmpty() || currentValue.toLowerCase().equals(compareValue.toLowerCase())) {
                    itemsFound++;
                    writeVCardEntry(pos, currentValue, result);
                }
            }
        }
        return itemsFound;
    }

    private final int pushBytes(Operation op, String vcardString) {
        if (vcardString == null) {
            Log.w(TAG, "vcardString is null!");
            return 160;
        }
        OutputStream outputStream = null;
        int pushResult = 160;
        try {
            outputStream = op.openOutputStream();
            outputStream.write(vcardString.getBytes());
        } catch (IOException e) {
            Log.e(TAG, "open/write outputstrem failed" + e.toString());
            pushResult = 208;
        }
        if (closeStream(outputStream, op)) {
            return pushResult;
        }
        return 208;
    }

    private final int handleAppParaForResponse(AppParamValue appParamValue, int size, HeaderSet reply, Operation op) {
        byte[] misnum = new byte[1];
        ApplicationParameter ap = new ApplicationParameter();
        int nmnum;
        if (this.mNeedPhonebookSize) {
            this.mNeedPhonebookSize = f71V;
            ap.addAPPHeader((byte) 8, (byte) 2, new byte[]{(byte) ((size / 256) & 255), (byte) ((size % 256) & 255)});
            if (this.mNeedNewMissedCallsNum) {
                this.mNeedNewMissedCallsNum = f71V;
                nmnum = size - this.mMissedCallSize;
                this.mMissedCallSize = size;
                if (nmnum <= 0) {
                    nmnum = 0;
                }
                misnum[0] = (byte) nmnum;
                ap.addAPPHeader((byte) 9, (byte) 1, misnum);
                Log.d(TAG, "handleAppParaForResponse(): mNeedNewMissedCallsNum=true,  num= " + nmnum);
            }
            reply.setHeader(76, ap.getAPPparam());
            Log.d(TAG, "Send back Phonebook size only, without body info! Size= " + size);
            return pushHeader(op, reply);
        }
        if (this.mNeedNewMissedCallsNum) {
            this.mNeedNewMissedCallsNum = f71V;
            nmnum = size - this.mMissedCallSize;
            this.mMissedCallSize = size;
            if (nmnum <= 0) {
                nmnum = 0;
            }
            misnum[0] = (byte) nmnum;
            ap.addAPPHeader((byte) 9, (byte) 1, misnum);
            reply.setHeader(76, ap.getAPPparam());
            Log.d(TAG, "handleAppParaForResponse(): mNeedNewMissedCallsNum=true,  num= " + nmnum);
            try {
                op.sendHeaders(reply);
            } catch (IOException e) {
                Log.e(TAG, e.toString());
                return 208;
            }
        }
        return -1;
    }

    private final int pullVcardListing(byte[] appParam, AppParamValue appParamValue, HeaderSet reply, Operation op) {
        String searchAttr = appParamValue.searchAttr.trim();
        if (searchAttr == null || searchAttr.length() == 0) {
            appParamValue.searchAttr = "0";
            Log.d(TAG, "searchAttr is not set by PCE, assume search by name by default");
        } else if (searchAttr.equals("0") || searchAttr.equals("1")) {
            Log.i(TAG, "searchAttr is valid: " + searchAttr);
        } else {
            Log.w(TAG, "search attr not supported");
            if (!searchAttr.equals("2")) {
                return 204;
            }
            Log.w(TAG, "do not support search by sound");
            return 209;
        }
        int size = this.mVcardManager.getPhonebookSize(appParamValue.needTag);
        int needSendBody = handleAppParaForResponse(appParamValue, size, reply, op);
        if (needSendBody != -1) {
            return needSendBody;
        }
        if (size == 0) {
            return 160;
        }
        String orderPara = appParamValue.order.trim();
        if (TextUtils.isEmpty(orderPara)) {
            orderPara = "0";
            Log.d(TAG, "Order parameter is not set by PCE. Assume order by 'Indexed' by default");
        } else if (orderPara.equals("0") || orderPara.equals("1")) {
            Log.i(TAG, "Order parameter is valid: " + orderPara);
        } else if (!orderPara.equals("2")) {
            return 204;
        } else {
            Log.w(TAG, "Do not support order by sound");
            return 209;
        }
        if (orderPara.equals("0")) {
            this.mOrderBy = ORDER_BY_INDEXED;
        } else if (orderPara.equals("1")) {
            this.mOrderBy = ORDER_BY_ALPHABETICAL;
        }
        return sendVcardListingXml(appParamValue.needTag, op, appParamValue.maxListCount, appParamValue.listStartOffset, appParamValue.searchValue, appParamValue.searchAttr);
    }

    private final int pullVcardEntry(byte[] appParam, AppParamValue appParamValue, Operation op, String name, String current_path) {
        if (name == null || name.length() < 5) {
            Log.d(TAG, "Name is Null, or the length of name < 5 !");
            return 198;
        }
        String strIndex = name.substring(0, (name.length() - 5) + 1);
        int intIndex = 0;
        if (strIndex.trim().length() != 0) {
            try {
                intIndex = Integer.parseInt(strIndex);
            } catch (NumberFormatException e) {
                Log.e(TAG, "catch number format exception " + e.toString());
                return 198;
            }
        }
        int size = this.mVcardManager.getPhonebookSize(appParamValue.needTag);
        if (size == 0) {
            return 196;
        }
        boolean vcard21 = appParamValue.vcard21;
        if (appParamValue.needTag == 0) {
            Log.w(TAG, "wrong path!");
            return 198;
        } else if (appParamValue.needTag == 1) {
            if (intIndex < 0 || intIndex >= size) {
                Log.w(TAG, "The requested vcard is not acceptable! name= " + name);
                return 196;
            } else if (intIndex == 0) {
                return pushBytes(op, this.mVcardManager.getOwnerPhoneNumberVcard(vcard21, null));
            } else {
                return this.mVcardManager.composeAndSendPhonebookOneVcard(op, intIndex, vcard21, null, this.mOrderBy, appParamValue.ignorefilter, appParamValue.filter);
            }
        } else if (intIndex <= 0 || intIndex > size) {
            Log.w(TAG, "The requested vcard is not acceptable! name= " + name);
            return 196;
        } else if (intIndex < 1) {
            return 160;
        } else {
            return this.mVcardManager.composeAndSendCallLogVcards(appParamValue.needTag, op, intIndex, intIndex, vcard21, appParamValue.ignorefilter, appParamValue.filter);
        }
    }

    private final int pullPhonebook(byte[] appParam, AppParamValue appParamValue, HeaderSet reply, Operation op, String name) {
        if (name != null) {
            int dotIndex = name.indexOf(".");
            String vcf = "vcf";
            if (dotIndex >= 0 && dotIndex <= name.length()) {
                if (!name.regionMatches(dotIndex + 1, vcf, 0, vcf.length())) {
                    Log.w(TAG, "name is not .vcf");
                    return 198;
                }
            }
        }
        int pbSize = this.mVcardManager.getPhonebookSize(appParamValue.needTag);
        int needSendBody = handleAppParaForResponse(appParamValue, pbSize, reply, op);
        if (needSendBody != -1) {
            return needSendBody;
        }
        if (pbSize == 0) {
            return 160;
        }
        int requestSize;
        if (pbSize >= appParamValue.maxListCount) {
            requestSize = appParamValue.maxListCount;
        } else {
            requestSize = pbSize;
        }
        int startPoint = appParamValue.listStartOffset;
        if (startPoint < 0 || startPoint >= pbSize) {
            Log.w(TAG, "listStartOffset is not correct! " + startPoint);
            return 160;
        }
        if (appParamValue.needTag != 1 && requestSize > CALLLOG_NUM_LIMIT) {
            requestSize = CALLLOG_NUM_LIMIT;
        }
        int endPoint = (startPoint + requestSize) - 1;
        if (endPoint > pbSize - 1) {
            endPoint = pbSize - 1;
        }
        Log.d(TAG, "pullPhonebook(): requestSize=" + requestSize + " startPoint=" + startPoint + " endPoint=" + endPoint);
        boolean vcard21 = appParamValue.vcard21;
        if (appParamValue.needTag != 1) {
            return this.mVcardManager.composeAndSendCallLogVcards(appParamValue.needTag, op, startPoint + 1, endPoint + 1, vcard21, appParamValue.ignorefilter, appParamValue.filter);
        } else if (startPoint == 0) {
            String ownerVcard = this.mVcardManager.getOwnerPhoneNumberVcard(vcard21, null);
            if (endPoint == 0) {
                return pushBytes(op, ownerVcard);
            }
            return this.mVcardManager.composeAndSendPhonebookVcards(op, 1, endPoint, vcard21, ownerVcard, appParamValue.ignorefilter, appParamValue.filter);
        } else {
            return this.mVcardManager.composeAndSendPhonebookVcards(op, startPoint, endPoint, vcard21, null, appParamValue.ignorefilter, appParamValue.filter);
        }
    }

    public static boolean closeStream(OutputStream out, Operation op) {
        boolean returnvalue = true;
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                Log.e(TAG, "outputStream close failed" + e.toString());
                returnvalue = f71V;
            }
        }
        if (op == null) {
            return returnvalue;
        }
        try {
            op.close();
            return returnvalue;
        } catch (IOException e2) {
            Log.e(TAG, "operation close failed" + e2.toString());
            return f71V;
        }
    }

    public final void onAuthenticationFailure(byte[] userName) {
    }

    public static final String createSelectionPara(int type) {
        switch (type) {
            case 2:
                return "type=1";
            case 3:
                return "type=2";
            case 4:
                return "type=3";
            default:
                return null;
        }
    }

    private void xmlEncode(String name, StringBuilder result) {
        if (name != null) {
            StringCharacterIterator iterator = new StringCharacterIterator(name);
            for (char character = iterator.current(); character != '￿'; character = iterator.next()) {
                if (character == '<') {
                    result.append("&lt;");
                } else if (character == '>') {
                    result.append("&gt;");
                } else if (character == '\"') {
                    result.append("&quot;");
                } else if (character == '\'') {
                    result.append("&#039;");
                } else if (character == '&') {
                    result.append("&amp;");
                } else {
                    result.append(character);
                }
            }
        }
    }

    private void writeVCardEntry(int vcfIndex, String name, StringBuilder result) {
        result.append("<card handle=\"");
        result.append(vcfIndex);
        result.append(".vcf\" name=\"");
        xmlEncode(name, result);
        result.append("\"/>");
    }

    private void notifyUpdateWakeLock() {
        Message msg = Message.obtain(this.mCallback);
        msg.what = 5004;
        msg.sendToTarget();
    }

    public static final void logHeader(HeaderSet hs) {
        Log.v(TAG, "Dumping HeaderSet " + hs.toString());
        try {
            Log.v(TAG, "COUNT : " + hs.getHeader(BluetoothShare.STATUS_RUNNING));
            Log.v(TAG, "NAME : " + hs.getHeader(1));
            Log.v(TAG, "TYPE : " + hs.getHeader(66));
            Log.v(TAG, "LENGTH : " + hs.getHeader(195));
            Log.v(TAG, "TIME_ISO_8601 : " + hs.getHeader(68));
            Log.v(TAG, "TIME_4_BYTE : " + hs.getHeader(196));
            Log.v(TAG, "DESCRIPTION : " + hs.getHeader(5));
            Log.v(TAG, "TARGET : " + hs.getHeader(70));
            Log.v(TAG, "HTTP : " + hs.getHeader(71));
            Log.v(TAG, "WHO : " + hs.getHeader(74));
            Log.v(TAG, "OBJECT_CLASS : " + hs.getHeader(79));
            Log.v(TAG, "APPLICATION_PARAMETER : " + hs.getHeader(76));
        } catch (IOException e) {
            Log.e(TAG, "dump HeaderSet error " + e);
        }
    }
}
