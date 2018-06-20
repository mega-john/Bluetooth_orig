package com.android.bluetooth.gatt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class SearchQueue {
    private List<Entry> mEntries = new ArrayList();

    class Entry {
        public int charInstId;
        public long charUuidLsb;
        public long charUuidMsb;
        public int connId;
        public int srvcInstId;
        public int srvcType;
        public long srvcUuidLsb;
        public long srvcUuidMsb;

        Entry() {
        }
    }

    SearchQueue() {
    }

    void add(int connId, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb) {
        Entry entry = new Entry();
        entry.connId = connId;
        entry.srvcType = srvcType;
        entry.srvcInstId = srvcInstId;
        entry.srvcUuidLsb = srvcUuidLsb;
        entry.srvcUuidMsb = srvcUuidMsb;
        entry.charUuidLsb = 0;
        this.mEntries.add(entry);
    }

    void add(int connId, int srvcType, int srvcInstId, long srvcUuidLsb, long srvcUuidMsb, int charInstId, long charUuidLsb, long charUuidMsb) {
        Entry entry = new Entry();
        entry.connId = connId;
        entry.srvcType = srvcType;
        entry.srvcInstId = srvcInstId;
        entry.srvcUuidLsb = srvcUuidLsb;
        entry.srvcUuidMsb = srvcUuidMsb;
        entry.charInstId = charInstId;
        entry.charUuidLsb = charUuidLsb;
        entry.charUuidMsb = charUuidMsb;
        this.mEntries.add(entry);
    }

    Entry pop() {
        Entry entry = (Entry) this.mEntries.get(0);
        this.mEntries.remove(0);
        return entry;
    }

    void removeConnId(int connId) {
        Iterator<Entry> it = this.mEntries.iterator();
        while (it.hasNext()) {
            if (((Entry) it.next()).connId == connId) {
                it.remove();
            }
        }
    }

    boolean isEmpty() {
        return this.mEntries.isEmpty();
    }

    void clear() {
        this.mEntries.clear();
    }
}
