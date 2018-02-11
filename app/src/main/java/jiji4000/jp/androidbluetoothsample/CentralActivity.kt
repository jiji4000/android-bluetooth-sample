package jiji4000.jp.androidbluetoothsample

import android.annotation.TargetApi
import android.bluetooth.*
import android.bluetooth.le.*
import android.bluetooth.le.ScanSettings.CALLBACK_TYPE_ALL_MATCHES
import android.content.Context
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_central.*
import java.util.*

class CentralActivity : AppCompatActivity() {
    companion object {
        val TAG = CentralActivity::class.java.simpleName
    }

    private lateinit var bleGatt: BluetoothGatt
    private lateinit var bleScanner: BluetoothLeScanner
    private lateinit var bleCharacteristic: BluetoothGattCharacteristic
    private lateinit var bleAdapter: BluetoothAdapter
    private lateinit var bleManager: BluetoothManager

    private var isConnect: Boolean = false

    /**
     * BluetoothLe scan callback
     */
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (callbackType == CALLBACK_TYPE_ALL_MATCHES) {
                super.onScanResult(callbackType, result)
                val bluetoothDevice = result.device
                // try to connect device
                bleGatt = bluetoothDevice.connectGatt(applicationContext, false, gattCallBack)
                bleScanner.stopScan(this)
            }
        }

        override fun onScanFailed(intErrorCode: Int) {
            Log.d(TAG, "onScanFailed ErrorCode = " + intErrorCode)
            super.onScanFailed(intErrorCode)
        }
    }

    private val gattCallBack = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            // 接続状況が変化したら実行.
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (gatt.requestMtu(512)) {
                    Log.d(TAG, "Requested MTU successfully")
                } else {
                    Log.d(TAG, "Failed to request MTU")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "call onConnectionStateChange STATE_DISCONNECTED")
                // 接続が切れたらGATTを空にする.
                if (bleGatt != null) {
                    bleGatt.close()
                }
            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                Log.d(TAG, "call onConnectionStateChange STATE_CONNECTING")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                Log.d(TAG, "call onConnectionStateChange STATE_DISCONNECTING")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            // Serviceが見つかったら実行.
            if (!isConnect) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "call onServicesDiscovered() GATT_SUCCESS")
                    // UUIDが同じかどうかを確認する.
                    val bleService = gatt.getService(UUID.fromString(getString(R.string.uuid_service)))
                    if (bleService != null) {
                        // 指定したUUIDを持つCharacteristicを確認する.
                        bleCharacteristic = bleService.getCharacteristic(UUID.fromString(getString(R.string.uuid_characteristic)))
                        if (bleCharacteristic != null) {
                            // Service, CharacteristicのUUIDが同じならBluetoothGattを更新する.
                            bleGatt = gatt
                            // キャラクタリスティックが見つかったら、Notificationをリクエスト.
                            bleGatt.setCharacteristicNotification(bleCharacteristic, true)

                            // Characteristic の Notificationを有効化する.
                            val bleDescriptor = bleCharacteristic.getDescriptor(
                                    UUID.fromString(getString(R.string.uuid_characteristic_config)))
                            bleDescriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            // Write the value of a given descriptor to the associated remote device.
                            bleGatt.writeDescriptor(bleDescriptor)
                            // 接続が完了したらデータ送信を開始する.
                            isConnect = true
                        }
                    }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged")
            // discover services
            if (gatt != null) {
                if (gatt.discoverServices()) {
                    Log.d(TAG, "Started discovering services")
                } else {
                    Log.d(TAG, "Failed to start discovering services")
                }
            }
        }

        /**
         * Callback triggered as a result of a remote characteristic notification.
         * peripheralから情報が送られた時に呼ばれる
         *
         * @param gatt
         * @param characteristic
         */
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            Log.d(TAG, "call onCharacteristicChanged")
            //キャラクタリスティックのUUIDをチェック(getUuidの結果が全て小文字で帰ってくるのでUpperCaseに変換)
            if (getString(R.string.uuid_characteristic).equals(characteristic.uuid.toString().toUpperCase())) {
                val peripheralValue = characteristic.getStringValue(0)
                Log.d(TAG, "call onCharacteristicChanged = " + peripheralValue)
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt,
                                          characteristic: BluetoothGattCharacteristic,
                                          status: Int) {
            Log.d(TAG, "call onCharacteristicRead")

        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt,
                                           characteristic: BluetoothGattCharacteristic,
                                           status: Int) {
            val id = characteristic.getStringValue(0)
            Log.d(TAG, "call onCharacteristicWrite = " + id)
        }

        override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorRead(gatt, descriptor, status)
            Log.d(TAG, "call onDescriptorRead")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            super.onDescriptorWrite(gatt, descriptor, status)
            Log.d(TAG, "call onDescriptorWrite")
            val message = input_message.text.toString()
            bleCharacteristic.setValue(message)
            bleGatt.writeCharacteristic(bleCharacteristic)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_central)
        // scanButton
        search_button.setOnClickListener { scanNewDevice() }
        // sendButton
        send_button.setOnClickListener { sendCentralData(input_message.text.toString()) }
        // init
        bleManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bleAdapter = bleManager.getAdapter()
    }

    fun scanNewDevice() {
        if(!bleAdapter.isEnabled){
            Toast.makeText(this,"Please enable Bluetooth",Toast.LENGTH_SHORT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startScanByBleScanner()
        } else {
            bleAdapter.startLeScan(BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
                runOnUiThread(Runnable {
                    // スキャン中に見つかったデバイスに接続を試みる.第三引数には接続後に呼ばれるBluetoothGattCallbackを指定する.
                    Log.d(TAG, "find device")
                    bleGatt = device.connectGatt(getApplicationContext(), false, gattCallBack)
                })
            })
        }
    }

    /**
     * sendData to Peripheral
     */
    fun sendCentralData(data: String) {
        if (isConnect) {
            bleCharacteristic.setValue(data)
            bleGatt.writeCharacteristic(bleCharacteristic)
        }
    }

    /**
     * Bleによる端末検索を行う
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun startScanByBleScanner() {
        bleScanner = bleAdapter.getBluetoothLeScanner()
        // フィルターするuuid
        val parcelUuid = ParcelUuid.fromString(getString(R.string.uuid_service))
        // uuidにフィルターをかける
        val scanFilter = ScanFilter.Builder().setServiceUuid(parcelUuid).build()
        val scanFilters = ArrayList<ScanFilter>()
        scanFilters.add(scanFilter)
        // setting
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        // デバイスの検出.
        bleScanner.startScan(scanFilters, settings, scanCallback)

        // set text
        state.text = "scanning peripheral"
    }
}
