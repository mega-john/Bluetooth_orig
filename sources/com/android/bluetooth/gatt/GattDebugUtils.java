package com.android.bluetooth.gatt;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import java.util.UUID;

class GattDebugUtils {
    private static final String ACTION_GATT_PAIRING_CONFIG = "android.bluetooth.action.GATT_PAIRING_CONFIG";
    private static final String ACTION_GATT_TEST_CONNECT = "android.bluetooth.action.GATT_TEST_CONNECT";
    private static final String ACTION_GATT_TEST_DISCONNECT = "android.bluetooth.action.GATT_TEST_DISCONNECT";
    private static final String ACTION_GATT_TEST_DISCOVER = "android.bluetooth.action.GATT_TEST_DISCOVER";
    private static final String ACTION_GATT_TEST_ENABLE = "android.bluetooth.action.GATT_TEST_ENABLE";
    private static final String ACTION_GATT_TEST_USAGE = "android.bluetooth.action.GATT_TEST_USAGE";
    private static final boolean DEBUG_ADMIN = true;
    private static final String EXTRA_ADDRESS = "address";
    private static final String EXTRA_ADDR_TYPE = "addr_type";
    private static final String EXTRA_AUTH_REQ = "auth_req";
    private static final String EXTRA_EHANDLE = "end";
    private static final String EXTRA_ENABLE = "enable";
    private static final String EXTRA_INIT_KEY = "init_key";
    private static final String EXTRA_IO_CAP = "io_cap";
    private static final String EXTRA_MAX_KEY = "max_key";
    private static final String EXTRA_RESP_KEY = "resp_key";
    private static final String EXTRA_SHANDLE = "start";
    private static final String EXTRA_TYPE = "type";
    private static final String EXTRA_UUID = "uuid";
    private static final String TAG = "BtGatt.DebugUtils";

    GattDebugUtils() {
    }

    static boolean handleDebugAction(GattService svc, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "handleDebugAction() action=" + action);
        if (ACTION_GATT_TEST_USAGE.equals(action)) {
            logUsageInfo();
        } else if (ACTION_GATT_TEST_ENABLE.equals(action)) {
            svc.gattTestCommand(1, null, null, intent.getBooleanExtra(EXTRA_ENABLE, true) ? 1 : 0, 0, 0, 0, 0);
        } else if (ACTION_GATT_TEST_CONNECT.equals(action)) {
            svc.gattTestCommand(2, null, intent.getStringExtra(EXTRA_ADDRESS), intent.getIntExtra(EXTRA_TYPE, 2), intent.getIntExtra(EXTRA_ADDR_TYPE, 0), 0, 0, 0);
        } else if (ACTION_GATT_TEST_DISCONNECT.equals(action)) {
            svc.gattTestCommand(3, null, null, 0, 0, 0, 0, 0);
        } else if (ACTION_GATT_TEST_DISCOVER.equals(action)) {
            GattService gattService = svc;
            gattService.gattTestCommand(4, getUuidExtra(intent), null, intent.getIntExtra(EXTRA_TYPE, 1), getHandleExtra(intent, EXTRA_SHANDLE, 1), getHandleExtra(intent, EXTRA_EHANDLE, 65535), 0, 0);
        } else if (!ACTION_GATT_PAIRING_CONFIG.equals(action)) {
            return false;
        } else {
            svc.gattTestCommand(240, null, null, intent.getIntExtra(EXTRA_AUTH_REQ, 5), intent.getIntExtra(EXTRA_IO_CAP, 4), intent.getIntExtra(EXTRA_INIT_KEY, 7), intent.getIntExtra(EXTRA_RESP_KEY, 7), intent.getIntExtra(EXTRA_MAX_KEY, 16));
        }
        return true;
    }

    private static int getHandleExtra(Intent intent, String extra, int default_value) {
        Bundle extras = intent.getExtras();
        Object uuid = extras != null ? extras.get(extra) : null;
        if (uuid == null || !uuid.getClass().getName().equals("java.lang.String")) {
            return intent.getIntExtra(extra, default_value);
        }
        try {
            return Integer.parseInt(extras.getString(extra), 16);
        } catch (NumberFormatException e) {
            return default_value;
        }
    }

    private static UUID getUuidExtra(Intent intent) {
        String uuidStr = intent.getStringExtra(EXTRA_UUID);
        if (uuidStr != null && uuidStr.length() == 4) {
            uuidStr = String.format("0000%s-0000-1000-8000-00805f9b34fb", new Object[]{uuidStr});
        }
        return uuidStr != null ? UUID.fromString(uuidStr) : null;
    }

    private static void logUsageInfo() {
        StringBuilder b = new StringBuilder();
        b.append("------------ GATT TEST ACTIONS  ----------------");
        b.append("\nGATT_TEST_ENABLE");
        b.append("\n  [--ez enable <bool>] Enable or disable,");
        b.append("\n                       defaults to true (enable).\n");
        b.append("\nGATT_TEST_CONNECT");
        b.append("\n   --es address <bda>");
        b.append("\n  [--ei addr_type <type>] Possible values:");
        b.append("\n                         0 = Static (default)");
        b.append("\n                         1 = Random\n");
        b.append("\n  [--ei type <type>]   Default is 2 (LE Only)\n");
        b.append("\nGATT_TEST_DISCONNECT\n");
        b.append("\nGATT_TEST_DISCOVER");
        b.append("\n  [--ei type <type>]   Possible values:");
        b.append("\n                         1 = Discover all services (default)");
        b.append("\n                         2 = Discover services by UUID");
        b.append("\n                         3 = Discover included services");
        b.append("\n                         4 = Discover characteristics");
        b.append("\n                         5 = Discover descriptors\n");
        b.append("\n  [--es uuid <uuid>]   Optional; Can be either full 128-bit");
        b.append("\n                       UUID hex string, or 4 hex characters");
        b.append("\n                       for 16-bit UUIDs.\n");
        b.append("\n  [--ei start <hdl>]   Start of handle range (default 1)");
        b.append("\n  [--ei end <hdl>]     End of handle range (default 65355)");
        b.append("\n    or");
        b.append("\n  [--es start <hdl>]   Start of handle range (hex format)");
        b.append("\n  [--es end <hdl>]     End of handle range (hex format)\n");
        b.append("\nGATT_PAIRING_CONFIG");
        b.append("\n  [--ei auth_req]      Authentication flag (default 5)");
        b.append("\n  [--ei io_cap]        IO capabilities (default 4)");
        b.append("\n  [--ei init_key]      Initial key size (default 7)");
        b.append("\n  [--ei resp_key]      Response key size (default 7)");
        b.append("\n  [--ei max_key]       Maximum key size (default 16)");
        b.append("\n------------------------------------------------");
        Log.i(TAG, b.toString());
    }
}
