package com.android.bluetooth.map;

import com.android.bluetooth.map.BluetoothMapUtils.TYPE;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.xmlpull.v1.XmlSerializer;

public class BluetoothMapMessageListingElement implements Comparable<BluetoothMapMessageListingElement> {
    /* renamed from: D */
    private static final boolean f19D = false;
    private static final String TAG = "BluetoothMapMessageListingElement";
    /* renamed from: V */
    private static final boolean f20V = false;
    private int mAttachmentSize = -1;
    private long mCpHandle = 0;
    private int mCursorIndex = 0;
    private long mDateTime = 0;
    private String mPriority = null;
    private String mProtect = null;
    private boolean mRead = f19D;
    private String mReceptionStatus = null;
    private String mRecipientAddressing = null;
    private String mRecipientName = null;
    private String mReplytoAddressing = null;
    private boolean mReportRead = f19D;
    private String mSenderAddressing = null;
    private String mSenderName = null;
    private String mSent = null;
    private int mSize = -1;
    private String mSubject = null;
    private String mText = null;
    private String mThreadId = null;
    private TYPE mType = null;

    public int getCursorIndex() {
        return this.mCursorIndex;
    }

    public void setCursorIndex(int cursorIndex) {
        this.mCursorIndex = cursorIndex;
    }

    public long getHandle() {
        return this.mCpHandle;
    }

    public void setHandle(long handle) {
        this.mCpHandle = handle;
    }

    public long getDateTime() {
        return this.mDateTime;
    }

    public String getDateTimeString() {
        return new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date(this.mDateTime));
    }

    public void setDateTime(long dateTime) {
        this.mDateTime = dateTime;
    }

    public String getSubject() {
        return this.mSubject;
    }

    public void setSubject(String subject) {
        this.mSubject = subject;
    }

    public String getSenderName() {
        return this.mSenderName;
    }

    public void setSenderName(String senderName) {
        this.mSenderName = senderName;
    }

    public String getSenderAddressing() {
        return this.mSenderAddressing;
    }

    public void setSenderAddressing(String senderAddressing) {
        this.mSenderAddressing = senderAddressing;
    }

    public String getReplyToAddressing() {
        return this.mReplytoAddressing;
    }

    public void setReplytoAddressing(String replytoAddressing) {
        this.mReplytoAddressing = replytoAddressing;
    }

    public String getRecipientName() {
        return this.mRecipientName;
    }

    public void setRecipientName(String recipientName) {
        this.mRecipientName = recipientName;
    }

    public String getRecipientAddressing() {
        return this.mRecipientAddressing;
    }

    public void setRecipientAddressing(String recipientAddressing) {
        this.mRecipientAddressing = recipientAddressing;
    }

    public TYPE getType() {
        return this.mType;
    }

    public void setType(TYPE type) {
        this.mType = type;
    }

    public int getSize() {
        return this.mSize;
    }

    public void setSize(int size) {
        this.mSize = size;
    }

    public String getText() {
        return this.mText;
    }

    public void setText(String text) {
        this.mText = text;
    }

    public String getReceptionStatus() {
        return this.mReceptionStatus;
    }

    public void setReceptionStatus(String receptionStatus) {
        this.mReceptionStatus = receptionStatus;
    }

    public int getAttachmentSize() {
        return this.mAttachmentSize;
    }

    public void setAttachmentSize(int attachmentSize) {
        this.mAttachmentSize = attachmentSize;
    }

    public String getPriority() {
        return this.mPriority;
    }

    public void setPriority(String priority) {
        this.mPriority = priority;
    }

    public String getRead() {
        return this.mRead ? "yes" : "no";
    }

    public boolean getReadBool() {
        return this.mRead;
    }

    public void setRead(boolean read, boolean reportRead) {
        this.mRead = read;
        this.mReportRead = reportRead;
    }

    public String getSent() {
        return this.mSent;
    }

    public void setSent(String sent) {
        this.mSent = sent;
    }

    public String getProtect() {
        return this.mProtect;
    }

    public void setProtect(String protect) {
        this.mProtect = protect;
    }

    public void setThreadId(long threadId) {
        if (threadId != -1) {
            this.mThreadId = BluetoothMapUtils.getLongAsString(threadId);
        }
    }

    public int compareTo(BluetoothMapMessageListingElement e) {
        if (this.mDateTime < e.mDateTime) {
            return 1;
        }
        if (this.mDateTime > e.mDateTime) {
            return -1;
        }
        return 0;
    }

    private static String stripInvalidChars(String text) {
        char[] out = new char[text.length()];
        int i = 0;
        int l = text.length();
        int o = 0;
        while (i < l) {
            int o2;
            char c = text.charAt(i);
            if ((c < ' ' || c > '퟿') && (c < '' || c > '�')) {
                o2 = o;
            } else {
                o2 = o + 1;
                out[o] = c;
            }
            i++;
            o = o2;
        }
        return i == o ? text : new String(out, 0, o);
    }

    public void encode(XmlSerializer xmlMsgElement, boolean includeThreadId) throws IllegalArgumentException, IllegalStateException, IOException {
        xmlMsgElement.startTag(null, "msg");
        xmlMsgElement.attribute(null, "handle", BluetoothMapUtils.getMapHandle(this.mCpHandle, this.mType));
        if (this.mSubject != null) {
            xmlMsgElement.attribute(null, "subject", stripInvalidChars(this.mSubject));
        }
        if (this.mDateTime != 0) {
            xmlMsgElement.attribute(null, "datetime", getDateTimeString());
        }
        if (this.mSenderName != null) {
            xmlMsgElement.attribute(null, "sender_name", stripInvalidChars(this.mSenderName));
        }
        if (this.mSenderAddressing != null) {
            xmlMsgElement.attribute(null, "sender_addressing", this.mSenderAddressing);
        }
        if (this.mReplytoAddressing != null) {
            xmlMsgElement.attribute(null, "replyto_addressing", this.mReplytoAddressing);
        }
        if (this.mRecipientName != null) {
            xmlMsgElement.attribute(null, "recipient_name", stripInvalidChars(this.mRecipientName));
        }
        if (this.mRecipientAddressing != null) {
            xmlMsgElement.attribute(null, "recipient_addressing", this.mRecipientAddressing);
        }
        if (this.mType != null) {
            xmlMsgElement.attribute(null, "type", this.mType.name());
        }
        if (this.mSize != -1) {
            xmlMsgElement.attribute(null, "size", Integer.toString(this.mSize));
        }
        if (this.mText != null) {
            xmlMsgElement.attribute(null, "text", this.mText);
        }
        if (this.mReceptionStatus != null) {
            xmlMsgElement.attribute(null, "reception_status", this.mReceptionStatus);
        }
        if (this.mAttachmentSize != -1) {
            xmlMsgElement.attribute(null, "attachment_size", Integer.toString(this.mAttachmentSize));
        }
        if (this.mPriority != null) {
            xmlMsgElement.attribute(null, "priority", this.mPriority);
        }
        if (this.mReportRead) {
            xmlMsgElement.attribute(null, "read", getRead());
        }
        if (this.mSent != null) {
            xmlMsgElement.attribute(null, "sent", this.mSent);
        }
        if (this.mProtect != null) {
            xmlMsgElement.attribute(null, "protected", this.mProtect);
        }
        if (this.mThreadId != null && includeThreadId) {
            xmlMsgElement.attribute(null, "thread_id", this.mThreadId);
        }
        xmlMsgElement.endTag(null, "msg");
    }
}
