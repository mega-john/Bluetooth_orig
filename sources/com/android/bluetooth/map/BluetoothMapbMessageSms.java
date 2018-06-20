package com.android.bluetooth.map;

import android.util.Log;
import com.android.bluetooth.map.BluetoothMapSmsPdu.SmsPdu;
import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;

public class BluetoothMapbMessageSms extends BluetoothMapbMessage {
    private String mSmsBody = null;
    private ArrayList<SmsPdu> mSmsBodyPdus = null;

    public void setSmsBodyPdus(ArrayList<SmsPdu> smsBodyPdus) {
        this.mSmsBodyPdus = smsBodyPdus;
        this.mCharset = null;
        if (smsBodyPdus.size() > 0) {
            this.mEncoding = ((SmsPdu) smsBodyPdus.get(0)).getEncodingString();
        }
    }

    public String getSmsBody() {
        return this.mSmsBody;
    }

    public void setSmsBody(String smsBody) {
        this.mSmsBody = smsBody;
        this.mCharset = "UTF-8";
        this.mEncoding = null;
    }

    public void parseMsgPart(String msgPart) {
        if (this.mAppParamCharset == 0) {
            Log.d(TAG, "Decoding \"" + msgPart + "\" as native PDU");
            byte[] msgBytes = decodeBinary(msgPart);
            if (msgBytes.length <= 0 || msgBytes[0] >= msgBytes.length - 1 || (msgBytes[msgBytes[0] + 1] & 3) == 1) {
                this.mSmsBody += BluetoothMapSmsPdu.decodePdu(msgBytes, this.mType == TYPE.SMS_CDMA ? BluetoothMapSmsPdu.SMS_TYPE_CDMA : BluetoothMapSmsPdu.SMS_TYPE_GSM);
                return;
            } else {
                Log.d(TAG, "Only submit PDUs are supported");
                throw new IllegalArgumentException("Only submit PDUs are supported");
            }
        }
        this.mSmsBody += msgPart;
    }

    public void parseMsgInit() {
        this.mSmsBody = "";
    }

    public byte[] encode() throws UnsupportedEncodingException {
        ArrayList<byte[]> bodyFragments = new ArrayList();
        if (this.mSmsBody != null) {
            bodyFragments.add(this.mSmsBody.replaceAll("END:MSG", "/END\\:MSG").getBytes("UTF-8"));
        } else if (this.mSmsBodyPdus == null || this.mSmsBodyPdus.size() <= 0) {
            bodyFragments.add(new byte[0]);
        } else {
            Iterator i$ = this.mSmsBodyPdus.iterator();
            while (i$.hasNext()) {
                SmsPdu pdu = (SmsPdu) i$.next();
                bodyFragments.add(encodeBinary(pdu.getData(), pdu.getScAddress()).getBytes("UTF-8"));
            }
        }
        return encodeGeneric(bodyFragments);
    }
}
