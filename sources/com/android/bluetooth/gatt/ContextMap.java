package com.android.bluetooth.gatt;

import android.os.IBinder.DeathRecipient;
import android.os.IInterface;
import android.os.RemoteException;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

class ContextMap<T> {
    private static final String TAG = "BtGatt.ContextMap";
    List<App> mApps = new ArrayList();
    Set<Connection> mConnections = new HashSet();

    class App {
        T callback;
        private List<CallbackInfo> congestionQueue = new ArrayList();
        int id;
        Boolean isCongested = Boolean.valueOf(false);
        private DeathRecipient mDeathRecipient;
        UUID uuid;

        App(UUID uuid, T callback) {
            this.uuid = uuid;
            this.callback = callback;
        }

        void linkToDeath(DeathRecipient deathRecipient) {
            try {
                ((IInterface) this.callback).asBinder().linkToDeath(deathRecipient, 0);
                this.mDeathRecipient = deathRecipient;
            } catch (RemoteException e) {
                Log.e(ContextMap.TAG, "Unable to link deathRecipient for app id " + this.id);
            }
        }

        void unlinkToDeath() {
            if (this.mDeathRecipient != null) {
                try {
                    ((IInterface) this.callback).asBinder().unlinkToDeath(this.mDeathRecipient, 0);
                } catch (NoSuchElementException e) {
                    Log.e(ContextMap.TAG, "Unable to unlink deathRecipient for app id " + this.id);
                }
            }
        }

        void queueCallback(CallbackInfo callbackInfo) {
            this.congestionQueue.add(callbackInfo);
        }

        CallbackInfo popQueuedCallback() {
            if (this.congestionQueue.size() == 0) {
                return null;
            }
            return (CallbackInfo) this.congestionQueue.remove(0);
        }
    }

    class Connection {
        String address;
        int appId;
        int connId;

        Connection(int connId, String address, int appId) {
            this.connId = connId;
            this.address = address;
            this.appId = appId;
        }
    }

    ContextMap() {
    }

    void add(UUID uuid, T callback) {
        synchronized (this.mApps) {
            this.mApps.add(new App(uuid, callback));
        }
    }

    void remove(UUID uuid) {
        synchronized (this.mApps) {
            Iterator<App> i = this.mApps.iterator();
            while (i.hasNext()) {
                App entry = (App) i.next();
                if (entry.uuid.equals(uuid)) {
                    entry.unlinkToDeath();
                    i.remove();
                    break;
                }
            }
        }
    }

    void remove(int id) {
        synchronized (this.mApps) {
            Iterator<App> i = this.mApps.iterator();
            while (i.hasNext()) {
                App entry = (App) i.next();
                if (entry.id == id) {
                    entry.unlinkToDeath();
                    i.remove();
                    break;
                }
            }
        }
    }

    void addConnection(int id, int connId, String address) {
        synchronized (this.mConnections) {
            if (getById(id) != null) {
                this.mConnections.add(new Connection(connId, address, id));
            }
        }
    }

    void removeConnection(int id, int connId) {
        synchronized (this.mConnections) {
            Iterator<Connection> i = this.mConnections.iterator();
            while (i.hasNext()) {
                if (((Connection) i.next()).connId == connId) {
                    i.remove();
                    break;
                }
            }
        }
    }

    App getById(int id) {
        for (App entry : this.mApps) {
            if (entry.id == id) {
                return entry;
            }
        }
        Log.e(TAG, "Context not found for ID " + id);
        return null;
    }

    App getByUuid(UUID uuid) {
        for (App entry : this.mApps) {
            if (entry.uuid.equals(uuid)) {
                return entry;
            }
        }
        Log.e(TAG, "Context not found for UUID " + uuid);
        return null;
    }

    Set<String> getConnectedDevices() {
        Set<String> addresses = new HashSet();
        for (Connection connection : this.mConnections) {
            addresses.add(connection.address);
        }
        return addresses;
    }

    App getByConnId(int connId) {
        for (Connection connection : this.mConnections) {
            if (connection.connId == connId) {
                return getById(connection.appId);
            }
        }
        return null;
    }

    Integer connIdByAddress(int id, String address) {
        if (getById(id) == null) {
            return null;
        }
        for (Connection connection : this.mConnections) {
            if (connection.address.equals(address) && connection.appId == id) {
                return Integer.valueOf(connection.connId);
            }
        }
        return null;
    }

    String addressByConnId(int connId) {
        for (Connection connection : this.mConnections) {
            if (connection.connId == connId) {
                return connection.address;
            }
        }
        return null;
    }

    List<Connection> getConnectionByApp(int appId) {
        List<Connection> currentConnections = new ArrayList();
        for (Connection connection : this.mConnections) {
            if (connection.appId == appId) {
                currentConnections.add(connection);
            }
        }
        return currentConnections;
    }

    void clear() {
        synchronized (this.mApps) {
            Iterator<App> i = this.mApps.iterator();
            while (i.hasNext()) {
                ((App) i.next()).unlinkToDeath();
                i.remove();
            }
        }
        synchronized (this.mConnections) {
            this.mConnections.clear();
        }
    }

    void dump(StringBuilder sb) {
        sb.append("  Entries: " + this.mApps.size() + "\n");
        for (App entry : this.mApps) {
            List<Connection> connections = getConnectionByApp(entry.id);
            sb.append("\n  Application Id: " + entry.id + "\n");
            sb.append("  UUID: " + entry.uuid + "\n");
            sb.append("  Connections: " + connections.size() + "\n");
            for (Connection connection : connections) {
                sb.append("    " + connection.connId + ": " + connection.address + "\n");
            }
        }
    }
}
