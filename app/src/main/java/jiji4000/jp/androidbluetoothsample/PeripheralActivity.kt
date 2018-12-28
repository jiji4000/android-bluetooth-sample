package jiji4000.jp.androidbluetoothsample

import android.annotation.TargetApi
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelUuid
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.widget.Toast
import java.util.*
import kotlinx.android.synthetic.main.activity_peripheral.*

class PeripheralActivity : AppCompatActivity() {
    private companion object {
        val TAG = PeripheralActivity::class.java.simpleName
    }

    var connectedCentralDevices = ArrayList<BleDevice>()
    // timer
    private var timer: Timer? = null
    private var peripheralDataTimerTask: PeripheralDataTimerTask? = null
    private lateinit var bleGattCharacteristic: BluetoothGattCharacteristic
    private var bleGattServer: BluetoothGattServer? = null
    private lateinit var bleLeAdvertiser: BluetoothLeAdvertiser
    private lateinit var bleAdapter: BluetoothAdapter
    private lateinit var bleManager: BluetoothManager
    private val random = Random()

    private var receivedNum: String = ""

    /**
     * 定期的にcentralに定期送信するタイマー
     */
    inner class PeripheralDataTimerTask : TimerTask() {
        override fun run() {
            for (device in connectedCentralDevices) {
                notifyConnectedDevice()
            }
        }
    }

    interface CentralListAdapterListener {
        fun onClick(playList: DeviceData)
    }

    // listener for central device list
    internal var itemListener: CentralListAdapterListener = object : CentralListAdapterListener {
        override fun onClick(playList: DeviceData) {

        }
    }

    private val listAdapter = CentralListAdapter(ArrayList(0), itemListener)

    /**
     * BluetoothGattServerCallback
     */
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onServiceAdded(status: Int, service: BluetoothGattService) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("Peripheral", "service added " + service.uuid.toString())
                runOnUiThread({
                    state_text.setText("service added")
                })
            } else {
                Log.d("Peripheral", "couldn't add service")
            }
        }

        override fun onConnectionStateChange(device: android.bluetooth.BluetoothDevice, status: Int,
                                             newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val address = device.address
                // connect
                Log.d(TAG, "call onConnectionStateChange newState = " + newState)
                Log.d(TAG, "device name = " + device.name)
                Log.d(TAG, "device address = " + address)

                // create centralDeviceData
                val bleDevice = BleDevice(device, true)
                connectedCentralDevices.add(bleDevice)
                // add list
                val deviceData = DeviceData(device.address, "new Device")
                listAdapter.centralList.add(deviceData)
                runOnUiThread {
                    listAdapter.notifyItemInserted(listAdapter.centralList.size - 1)
                }

                // timer start
                if (timer == null) {
                    timer = Timer()
                    peripheralDataTimerTask = PeripheralDataTimerTask()
                    // 第二引数:最初の処理までのミリ秒 第三引数:以降の処理実行の間隔(ミリ秒).
                    timer?.schedule(peripheralDataTimerTask, 1000, 1000)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "call onConnectionStateChange STATE_DISCONNECTED")
                for (myDevice in connectedCentralDevices) {
                    if (myDevice.bleDevice == device) {
                        connectedCentralDevices.remove(myDevice)
                        listAdapter.centralList.removeAt(connectedCentralDevices.size)
                        break;
                    }
                }
                runOnUiThread {
                    listAdapter.notifyDataSetChanged()
                }
            }
        }

        /**
         * A remote client has requested to write to a local characteristic.
         * @param device
         * @param requestId
         * @param characteristic
         * @param preparedWrite
         * @param responseNeeded
         * @param offset
         * @param value
         */
        override fun onCharacteristicWriteRequest(device: BluetoothDevice,
                                                  requestId: Int,
                                                  characteristic: BluetoothGattCharacteristic,
                                                  preparedWrite: Boolean,
                                                  responseNeeded: Boolean,
                                                  offset: Int,
                                                  value: ByteArray) {
            Log.d(TAG, "call onCharacteristicWriteRequest")
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)

            // set written value to characteristic.
            characteristic.value = value
            // get value
            receivedNum = characteristic.getStringValue(offset)

            // search uuid and update message
            for (list in listAdapter.centralList) {
                if (device.address == list.uuid) {
                    list.message = receivedNum
                }
            }

            runOnUiThread { listAdapter.notifyDataSetChanged() }

            if (responseNeeded) {
                bleGattServer?.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        value)
            }
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            Log.d(TAG, "call onDescriptorWriteRequest")
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            if (responseNeeded) {
                bleGattServer?.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        value)
            }
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            Log.d(TAG, "ondescriptorReadrequest UUID: " + descriptor.uuid.toString())
        }

        /**
         * Callback invoked when a notification or indication has been sent to a remote device.
         * @param device
         * @param status
         */
        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            super.onNotificationSent(device, status)
            Log.d(TAG, "call onNotificationSent")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_peripheral)

        bleManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bleAdapter = bleManager.getAdapter()

        central_list.apply {
            // 1.adapterにセット
            adapter = listAdapter
            // 2.LayoutMangerをセット
            layoutManager = LinearLayoutManager(context)
        }

        advertise_button.setOnClickListener { prepareBle() }
    }

    override fun onDestroy() {
        bleGattServer?.close()
        bleGattServer = null
        // timer
        timer?.cancel()
        peripheralDataTimerTask?.cancel()
        super.onDestroy()
    }

    /**
     * central端末に一斉送信する
     */
    private fun notifyConnectedDevice() {
        // 繋がったcentral端末に一斉送信する
        for (device in connectedCentralDevices) {
            // get device connect State
            val connectState = bleManager.getConnectionState(device.bleDevice,BluetoothProfile.GATT)
            // if timer interval is short need check device is CONNECTED.
            if(connectState == BluetoothProfile.STATE_CONNECTED) {
                val value = random.nextInt(100).toString()
                bleGattCharacteristic.setValue(value)
                if (!bleGattServer?.notifyCharacteristicChanged(device.bleDevice, bleGattCharacteristic, true)!!) {
                    Log.d(TAG, "notifyCharacteristicChanged failed value = " + value)
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun prepareBle() {
        bleLeAdvertiser = bleAdapter.getBluetoothLeAdvertiser()
        if (bleLeAdvertiser != null) {
            val btGattService = BluetoothGattService(UUID.fromString(getString(R.string.uuid_service)), BluetoothGattService.SERVICE_TYPE_PRIMARY)

            bleGattCharacteristic = BluetoothGattCharacteristic(UUID.fromString(getString(R.string.uuid_characteristic)), BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ)
            btGattService.addCharacteristic(bleGattCharacteristic)

            val dataDescriptor = BluetoothGattDescriptor(
                    UUID.fromString(getString(R.string.uuid_characteristic_config)), BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ)
            bleGattCharacteristic.addDescriptor(dataDescriptor)
            bleGattServer = bleManager.openGattServer(this, gattServerCallback)
            bleGattServer?.addService(btGattService)
            val dataBuilder = AdvertiseData.Builder()
            val settingsBuilder = AdvertiseSettings.Builder()
            dataBuilder.setIncludeTxPowerLevel(false)
            dataBuilder.addServiceUuid(ParcelUuid.fromString(getString(R.string.uuid_service)))
            settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            // Start Bluetooth LE Advertising
            bleLeAdvertiser.startAdvertising(settingsBuilder.build(), dataBuilder.build(), object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {

                }

                override fun onStartFailure(errorCode: Int) {

                }
            })
        } else {
            Toast.makeText(this, "Bluetooth Le Advertiser not supported", Toast.LENGTH_SHORT).show()
        }
    }
}