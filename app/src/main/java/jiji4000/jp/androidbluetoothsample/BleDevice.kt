package jiji4000.jp.androidbluetoothsample

import android.bluetooth.BluetoothDevice

data class BleDevice(
    var bleDevice: BluetoothDevice,
    var isCoonect: Boolean
)
