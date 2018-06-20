package com.android.bluetooth.opp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;
import java.util.HashMap;

public final class BluetoothOppProvider extends ContentProvider {
    /* renamed from: D */
    private static final boolean f46D = true;
    private static final String DB_NAME = "btopp.db";
    private static final String DB_TABLE = "btopp";
    private static final int DB_VERSION = 1;
    private static final int DB_VERSION_NOP_UPGRADE_FROM = 0;
    private static final int DB_VERSION_NOP_UPGRADE_TO = 1;
    private static final HashMap<String, String> LIVE_FOLDER_PROJECTION_MAP = new HashMap();
    private static final int LIVE_FOLDER_RECEIVED_FILES = 3;
    private static final int SHARES = 1;
    private static final int SHARES_ID = 2;
    private static final String SHARE_LIST_TYPE = "vnd.android.cursor.dir/vnd.android.btopp";
    private static final String SHARE_TYPE = "vnd.android.cursor.item/vnd.android.btopp";
    private static final String TAG = "BluetoothOppProvider";
    /* renamed from: V */
    private static final boolean f47V = false;
    private static final UriMatcher sURIMatcher = new UriMatcher(-1);
    private SQLiteOpenHelper mOpenHelper = null;

    private final class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(Context context) {
            super(context, BluetoothOppProvider.DB_NAME, null, 1);
        }

        public void onCreate(SQLiteDatabase db) {
            BluetoothOppProvider.this.createTable(db);
        }

        public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
            if (oldV == 0) {
                if (newV != 1) {
                    oldV = 1;
                } else {
                    return;
                }
            }
            Log.i(BluetoothOppProvider.TAG, "Upgrading downloads database from version " + oldV + " to " + newV + ", which will destroy all old data");
            BluetoothOppProvider.this.dropTable(db);
            BluetoothOppProvider.this.createTable(db);
        }
    }

    static {
        sURIMatcher.addURI("com.android.bluetooth.opp", DB_TABLE, 1);
        sURIMatcher.addURI("com.android.bluetooth.opp", "btopp/#", 2);
        sURIMatcher.addURI("com.android.bluetooth.opp", "live_folders/received", 3);
        LIVE_FOLDER_PROJECTION_MAP.put("_id", "_id AS _id");
        LIVE_FOLDER_PROJECTION_MAP.put("name", "hint AS name");
    }

    private void createTable(SQLiteDatabase db) {
        try {
            db.execSQL("CREATE TABLE btopp(_id INTEGER PRIMARY KEY AUTOINCREMENT,uri TEXT, hint TEXT, _data TEXT, mimetype TEXT, direction INTEGER, destination TEXT, visibility INTEGER, confirm INTEGER, status INTEGER, total_bytes INTEGER, current_bytes INTEGER, timestamp INTEGER,scanned INTEGER); ");
        } catch (SQLException ex) {
            Log.e(TAG, "couldn't create table in downloads database");
            throw ex;
        }
    }

    private void dropTable(SQLiteDatabase db) {
        try {
            db.execSQL("DROP TABLE IF EXISTS btopp");
        } catch (SQLException ex) {
            Log.e(TAG, "couldn't drop table in downloads database");
            throw ex;
        }
    }

    public String getType(Uri uri) {
        switch (sURIMatcher.match(uri)) {
            case 1:
                return SHARE_LIST_TYPE;
            case 2:
                return SHARE_TYPE;
            default:
                Log.d(TAG, "calling getType on an unknown URI: " + uri);
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
    }

    private static final void copyString(String key, ContentValues from, ContentValues to) {
        String s = from.getAsString(key);
        if (s != null) {
            to.put(key, s);
        }
    }

    private static final void copyInteger(String key, ContentValues from, ContentValues to) {
        Integer i = from.getAsInteger(key);
        if (i != null) {
            to.put(key, i);
        }
    }

    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = this.mOpenHelper.getWritableDatabase();
        if (sURIMatcher.match(uri) != 1) {
            Log.d(TAG, "calling insert on an unknown/invalid URI: " + uri);
            throw new IllegalArgumentException("Unknown/Invalid URI " + uri);
        }
        ContentValues filteredValues = new ContentValues();
        copyString("uri", values, filteredValues);
        copyString(BluetoothShare.FILENAME_HINT, values, filteredValues);
        copyString(BluetoothShare.MIMETYPE, values, filteredValues);
        copyString(BluetoothShare.DESTINATION, values, filteredValues);
        copyInteger(BluetoothShare.VISIBILITY, values, filteredValues);
        copyInteger(BluetoothShare.TOTAL_BYTES, values, filteredValues);
        if (values.getAsInteger(BluetoothShare.VISIBILITY) == null) {
            filteredValues.put(BluetoothShare.VISIBILITY, Integer.valueOf(0));
        }
        Integer dir = values.getAsInteger(BluetoothShare.DIRECTION);
        Integer con = values.getAsInteger(BluetoothShare.USER_CONFIRMATION);
        String address = values.getAsString(BluetoothShare.DESTINATION);
        if (values.getAsInteger(BluetoothShare.DIRECTION) == null) {
            dir = Integer.valueOf(0);
        }
        if (dir.intValue() == 0 && con == null) {
            con = Integer.valueOf(2);
        }
        if (dir.intValue() == 1 && con == null) {
            con = Integer.valueOf(0);
        }
        filteredValues.put(BluetoothShare.USER_CONFIRMATION, con);
        filteredValues.put(BluetoothShare.DIRECTION, dir);
        filteredValues.put(BluetoothShare.STATUS, Integer.valueOf(BluetoothShare.STATUS_PENDING));
        filteredValues.put(Constants.MEDIA_SCANNED, Integer.valueOf(0));
        Long ts = values.getAsLong("timestamp");
        if (ts == null) {
            ts = Long.valueOf(System.currentTimeMillis());
        }
        filteredValues.put("timestamp", ts);
        Context context = getContext();
        context.startService(new Intent(context, BluetoothOppService.class));
        long rowID = db.insert(DB_TABLE, null, filteredValues);
        if (rowID != -1) {
            context.startService(new Intent(context, BluetoothOppService.class));
            Uri ret = Uri.parse(BluetoothShare.CONTENT_URI + "/" + rowID);
            context.getContentResolver().notifyChange(uri, null);
            return ret;
        }
        Log.d(TAG, "couldn't insert into btopp database");
        return null;
    }

    public boolean onCreate() {
        this.mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = this.mOpenHelper.getReadableDatabase();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (sURIMatcher.match(uri)) {
            case 1:
                qb.setTables(DB_TABLE);
                break;
            case 2:
                qb.setTables(DB_TABLE);
                qb.appendWhere("_id=");
                qb.appendWhere((CharSequence) uri.getPathSegments().get(1));
                break;
            case 3:
                qb.setTables(DB_TABLE);
                qb.setProjectionMap(LIVE_FOLDER_PROJECTION_MAP);
                qb.appendWhere("direction=1 AND status=200");
                sortOrder = "_id DESC, " + sortOrder;
                break;
            default:
                Log.d(TAG, "querying unknown URI: " + uri);
                throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        Cursor ret = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if (ret != null) {
            ret.setNotificationUri(getContext().getContentResolver(), uri);
        } else {
            Log.d(TAG, "query failed in downloads database");
        }
        return ret;
    }

    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        SQLiteDatabase db = this.mOpenHelper.getWritableDatabase();
        int match = sURIMatcher.match(uri);
        switch (match) {
            case 1:
            case 2:
                String myWhere;
                int count;
                if (selection == null) {
                    myWhere = "";
                } else if (match == 1) {
                    myWhere = "( " + selection + " )";
                } else {
                    myWhere = "( " + selection + " ) AND ";
                }
                if (match == 2) {
                    myWhere = myWhere + " ( _id = " + Long.parseLong((String) uri.getPathSegments().get(1)) + " ) ";
                }
                if (values.size() > 0) {
                    count = db.update(DB_TABLE, values, myWhere, selectionArgs);
                } else {
                    count = 0;
                }
                getContext().getContentResolver().notifyChange(uri, null);
                return count;
            default:
                Log.d(TAG, "updating unknown/invalid URI: " + uri);
                throw new UnsupportedOperationException("Cannot update URI: " + uri);
        }
    }

    public int delete(Uri uri, String selection, String[] selectionArgs) {
        SQLiteDatabase db = this.mOpenHelper.getWritableDatabase();
        int match = sURIMatcher.match(uri);
        switch (match) {
            case 1:
            case 2:
                String myWhere;
                if (selection == null) {
                    myWhere = "";
                } else if (match == 1) {
                    myWhere = "( " + selection + " )";
                } else {
                    myWhere = "( " + selection + " ) AND ";
                }
                if (match == 2) {
                    myWhere = myWhere + " ( _id = " + Long.parseLong((String) uri.getPathSegments().get(1)) + " ) ";
                }
                int count = db.delete(DB_TABLE, myWhere, selectionArgs);
                getContext().getContentResolver().notifyChange(uri, null);
                return count;
            default:
                Log.d(TAG, "deleting unknown/invalid URI: " + uri);
                throw new UnsupportedOperationException("Cannot delete URI: " + uri);
        }
    }
}
