package com.velocityconnect;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final UUID SERVICE_UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");
    private static final UUID DATA_UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final String BIKE_NAME = "DL-D21A05120867";

    private TextView statusView, dataView;
    private Button scanButton, connectButton;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private BluetoothDevice bike;
    private BluetoothGatt gatt;
    private boolean scanning = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(28, 36, 28, 28);

        TextView title = new TextView(this);
        title.setText("VELOCITY CONNECT");
        title.setTextSize(26);
        title.setPadding(0, 0, 0, 22);
        page.addView(title);

        statusView = new TextView(this);
        statusView.setText("Bike: not connected");
        statusView.setTextSize(16);
        page.addView(statusView);

        scanButton = new Button(this);
        scanButton.setText("SCAN FOR BIKE");
        page.addView(scanButton);

        connectButton = new Button(this);
        connectButton.setText("CONNECT");
        connectButton.setEnabled(false);
        page.addView(connectButton);

        Button disconnectButton = new Button(this);
        disconnectButton.setText("DISCONNECT");
        page.addView(disconnectButton);

        TextView heading = new TextView(this);
        heading.setText("LIVE BLE DATA (RAW)");
        heading.setTextSize(18);
        heading.setPadding(0, 24, 0, 8);
        page.addView(heading);

        dataView = new TextView(this);
        dataView.setText("Waiting for FFF1 notifications…");
        dataView.setTextIsSelectable(true);
        page.addView(dataView);
        setContentView(page);

        BluetoothManager manager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        scanner = adapter == null ? null : adapter.getBluetoothLeScanner();

        scanButton.setOnClickListener(v -> startScan());
        connectButton.setOnClickListener(v -> connectBike());
        disconnectButton.setOnClickListener(v -> disconnectBike());
        requestPermissionsIfNeeded();
    }

    private void requestPermissionsIfNeeded() {
        ArrayList<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 31) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (!needed.isEmpty()) requestPermissions(needed.toArray(new String[0]), 10);
    }

    private void startScan() {
        if (adapter == null || !adapter.isEnabled()) {
            statusView.setText("Please turn Bluetooth on.");
            return;
        }
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            statusView.setText("BLE scanner unavailable.");
            return;
        }
        try {
            bike = null;
            scanning = true;
            scanButton.setEnabled(false);
            statusView.setText("Scanning for " + BIKE_NAME + "…");
            scanner.startScan(scanCallback);
            scanButton.postDelayed(() -> {
                if (scanning) {
                    try { scanner.stopScan(scanCallback); } catch (Exception ignored) {}
                    scanning = false;
                    scanButton.setEnabled(true);
                    if (bike == null) statusView.setText("Bike not found. Keep bike on and scan nearby.");
                }
            }, 15000);
        } catch (SecurityException e) {
            statusView.setText("Bluetooth permission not granted.");
            scanButton.setEnabled(true);
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice found = result.getDevice();
            String name = null;
            try { name = found.getName(); } catch (SecurityException ignored) {}
            if (name != null && (name.equalsIgnoreCase(BIKE_NAME) || name.toLowerCase(Locale.ROOT).contains("dl-d21"))) {
                bike = found;
                try { scanner.stopScan(this); } catch (Exception ignored) {}
                scanning = false;
                runOnUiThread(() -> {
                    statusView.setText("Bike found: " + name);
                    connectButton.setEnabled(true);
                    scanButton.setEnabled(true);
                });
            }
        }
        @Override public void onScanFailed(int errorCode) {
            scanning = false;
            runOnUiThread(() -> {
                statusView.setText("Scan failed (code " + errorCode + ").");
                scanButton.setEnabled(true);
            });
        }
    };

    private void connectBike() {
        if (bike == null) {
            statusView.setText("Scan for the bike first.");
            return;
        }
        try {
            statusView.setText("Connecting…");
            if (Build.VERSION.SDK_INT >= 23)
                gatt = bike.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            else
                gatt = bike.connectGatt(this, false, gattCallback);
        } catch (SecurityException e) {
            statusView.setText("Bluetooth connect permission not granted.");
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt bluetoothGatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread(() -> statusView.setText("Connected — discovering services…"));
                try { bluetoothGatt.discoverServices(); } catch (SecurityException ignored) {}
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread(() -> statusView.setText("Bike: disconnected"));
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt bluetoothGatt, int status) {
            BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
            if (service == null) {
                runOnUiThread(() -> statusView.setText("Connected, but service FFF0 not found."));
                return;
            }
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(DATA_UUID);
            if (characteristic == null) {
                runOnUiThread(() -> statusView.setText("FFF0 found, but FFF1 not found."));
                return;
            }
            try {
                bluetoothGatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(descriptor);
                }
                runOnUiThread(() -> statusView.setText("Bike connected — listening to FFF1."));
            } catch (SecurityException e) {
                runOnUiThread(() -> statusView.setText("Permission error while enabling notifications."));
            }
        }

        @Override public void onCharacteristicChanged(BluetoothGatt bluetoothGatt,
                BluetoothGattCharacteristic characteristic) {
            if (DATA_UUID.equals(characteristic.getUuid())) {
                byte[] value = characteristic.getValue();
                StringBuilder hex = new StringBuilder();
                for (byte b : value) hex.append(String.format(Locale.ROOT, "%02X ", b));
                String output = hex.toString().trim();
                runOnUiThread(() -> dataView.setText("FFF1: " + output));
            }
        }
    };

    private void disconnectBike() {
        try {
            if (gatt != null) {
                gatt.disconnect();
                gatt.close();
                gatt = null;
            }
        } catch (SecurityException ignored) {}
        statusView.setText("Bike: disconnected");
        connectButton.setEnabled(false);
    }

    @Override protected void onDestroy() {
        disconnectBike();
        super.onDestroy();
    }
}
