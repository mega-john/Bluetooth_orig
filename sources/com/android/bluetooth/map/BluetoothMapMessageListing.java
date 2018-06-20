package com.android.bluetooth.map;

import android.util.Log;
import com.android.internal.util.FastXmlSerializer;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.xmlpull.v1.XmlSerializer;

public class BluetoothMapMessageListing {
    /* renamed from: D */
    private static final boolean f18D = true;
    private static final String TAG = "BluetoothMapMessageListing";
    private boolean hasUnread = false;
    private List<BluetoothMapMessageListingElement> list = new ArrayList();

    public void add(BluetoothMapMessageListingElement element) {
        this.list.add(element);
        if (element.getReadBool()) {
            this.hasUnread = true;
        }
    }

    public int getCount() {
        if (this.list != null) {
            return this.list.size();
        }
        return 0;
    }

    public boolean hasUnread() {
        return this.hasUnread;
    }

    public List<BluetoothMapMessageListingElement> getList() {
        return this.list;
    }

    public byte[] encode(boolean includeThreadId) throws UnsupportedEncodingException {
        StringWriter sw = new StringWriter();
        XmlSerializer xmlMsgElement = new FastXmlSerializer();
        try {
            xmlMsgElement.setOutput(sw);
            xmlMsgElement.startDocument("UTF-8", Boolean.valueOf(true));
            xmlMsgElement.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
            xmlMsgElement.startTag(null, "MAP-msg-listing");
            xmlMsgElement.attribute(null, "version", "1.0");
            for (BluetoothMapMessageListingElement element : this.list) {
                element.encode(xmlMsgElement, includeThreadId);
            }
            xmlMsgElement.endTag(null, "MAP-msg-listing");
            xmlMsgElement.endDocument();
        } catch (IllegalArgumentException e) {
            Log.w(TAG, e);
        } catch (IllegalStateException e2) {
            Log.w(TAG, e2);
        } catch (IOException e3) {
            Log.w(TAG, e3);
        }
        return sw.toString().getBytes("UTF-8");
    }

    public void sort() {
        Collections.sort(this.list);
    }

    public void segment(int count, int offset) {
        count = Math.min(count, this.list.size() - offset);
        if (count > 0) {
            this.list = this.list.subList(offset, offset + count);
            if (this.list == null) {
                this.list = new ArrayList();
            }
        } else if (offset > this.list.size()) {
            this.list = new ArrayList();
            Log.d(TAG, "offset greater than list size. Returning empty list");
        } else {
            this.list = this.list.subList(offset, this.list.size());
        }
    }
}
