package com.pauldemarco.frccblue

import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.util.*
import kotlin.collections.HashMap


class FrccbluePlugin() : MethodCallHandler {

    companion object {
        var activity: Activity? = null
        var channel: MethodChannel? = null
        var registerReceiver: Boolean = false

        @JvmStatic
        fun registerWith(registrar: Registrar): Unit {
            var channel = MethodChannel(registrar.messenger(), "bluetooth_peripheral")
            channel.setMethodCallHandler(FrccbluePlugin())
            FrccbluePlugin.activity = registrar.activity()
            FrccbluePlugin.channel = channel
        }
    }

    override fun onMethodCall(call: MethodCall, result: Result): Unit {
        if (call.method.equals("getPlatformVersion")) {
            result.success("Android ${android.os.Build.VERSION.RELEASE}")
        }
        if (call.method.equals("startPeripheral")) {
            print("startPeripheral")
            Service_UUID = call.argument<String>("serviceUUID").toString()
            Read_Characteristic_UUID = call.argument<String>("readCharacteristicUUID").toString()
            Write_Characteristic_UUID = call.argument<String>("writeCharacteristicUUID").toString()
            startPeripheral()
        }
        if (call.method.equals("stopPeripheral")) {
            print("stopPeripheral")
            stopAdvertising()
        }
        if (call.method.equals("peripheralUpdateValue")) {
            var centraluuidString = call.argument<String>("centraluuidString")
            var characteristicuuidString = call.argument<String>("characteristicuuidString")
            var data = call.argument<ByteArray>("data")
            var isRead = call.argument<Boolean>("isRead")

            val device = centralsDic.get(centraluuidString)
//            val characteristic = characteristicsDic.get(characteristicuuidString)
//            characteristic?.setValue(data)
            if (isRead == true) {
                readCharacteristic?.value = data
                mGattServer?.notifyCharacteristicChanged(device, readCharacteristic, false)
            } else {
                writeCharacteristic?.value = data
                mGattServer?.notifyCharacteristicChanged(device, writeCharacteristic, false)
            }

        }
    }

    private var mBluetoothManager: BluetoothManager? = null
    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mGattServer: BluetoothGattServer? = null
    private var Service_UUID: String = UUID.randomUUID().toString()
    private var Read_Characteristic_UUID: String = UUID.randomUUID().toString()
    private var Write_Characteristic_UUID: String = UUID.randomUUID().toString()
    private var centralsDic: MutableMap<String, BluetoothDevice> = HashMap()
    private var characteristicsDic: MutableMap<String, BluetoothGattCharacteristic> = HashMap()
    private var descriptorsDic: MutableMap<String, BluetoothGattDescriptor> = HashMap()
    private val handler: Handler = Handler(Looper.getMainLooper())


    private var mAdvData: AdvertiseData? = null
    private var mAdvScanResponse: AdvertiseData? = null
    private var mAdvSettings: AdvertiseSettings? = null
    private var mBluetoothGattService: BluetoothGattService? = null
    private var mAdvertiser: BluetoothLeAdvertiser? = null
    private var readCharacteristic: BluetoothGattCharacteristic? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private val mAdvCallback: AdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "Not broadcasting: $errorCode")
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            super.onStartSuccess(settingsInEffect)
            Log.v(TAG, "Broadcasting")
        }
    }

    open fun startPeripheral() {

        if (FrccbluePlugin.registerReceiver == false) {
            FrccbluePlugin.registerReceiver = true
            val mR = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action

                    if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                        val state = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

                        var statestr = "unknown"
                        when (state) {
                            BluetoothAdapter.STATE_OFF -> statestr = "poweredOff"
                            BluetoothAdapter.STATE_ON -> statestr = "poweredOn"
                        }
                        handler.post(Runnable {
                            channel?.invokeMethod("peripheralManagerDidUpdateState", statestr)
                        })
                        if (statestr == "poweredOn") {
                            this@FrccbluePlugin.startPeripheral()
                        }
                    }
                }
            }
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            FrccbluePlugin.activity?.registerReceiver(mR, filter)
        }

        mBluetoothManager = activity?.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        mBluetoothAdapter = mBluetoothManager?.adapter

        mAdvSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .build()
        mAdvData = AdvertiseData.Builder()
                .setIncludeTxPowerLevel(true)
                .addServiceUuid(ParcelUuid(UUID.fromString(Service_UUID)))
                .build()
        mAdvScanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()

        mGattServer = mBluetoothManager!!.openGattServer(activity, mGattServerCallback);
        if (mGattServer == null) {
            ensureBleFeaturesAvailable();
            return;
        }

        mBluetoothGattService = BluetoothGattService(UUID.fromString(Service_UUID),
                BluetoothGattService.SERVICE_TYPE_PRIMARY)

        readCharacteristic = BluetoothGattCharacteristic(UUID.fromString(Read_Characteristic_UUID),
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY
        )
        val bluetoothGattReadDescriptor = BluetoothGattDescriptor(UUID.fromString("00002901-0000-1000-8000-00805f9b34fb"), BluetoothGattDescriptor.PERMISSION_READ)
        readCharacteristic!!.addDescriptor(bluetoothGattReadDescriptor)


        writeCharacteristic = BluetoothGattCharacteristic(UUID.fromString(Write_Characteristic_UUID),
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY
        )
        val bluetoothGattWriteDescriptor = BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), BluetoothGattDescriptor.PERMISSION_WRITE)
        writeCharacteristic!!.addDescriptor(bluetoothGattWriteDescriptor)

        mBluetoothGattService = BluetoothGattService(UUID.fromString(Service_UUID),
                BluetoothGattService.SERVICE_TYPE_PRIMARY)
        mBluetoothGattService!!.addCharacteristic(readCharacteristic)
        mBluetoothGattService!!.addCharacteristic(writeCharacteristic)

        mGattServer!!.addService(mBluetoothGattService);

        if (mBluetoothAdapter!!.isMultipleAdvertisementSupported()) {
            mAdvertiser = mBluetoothAdapter!!.getBluetoothLeAdvertiser();
            mAdvertiser!!.startAdvertising(mAdvSettings, mAdvData, mAdvScanResponse, mAdvCallback);
        } else {
            Toast.makeText(FrccbluePlugin.activity?.applicationContext, "MultipleAdvertisement not Supported", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureBleFeaturesAvailable() {
        if (mBluetoothAdapter == null) {
            Toast.makeText(FrccbluePlugin.activity?.applicationContext, "Not support host.", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Bluetooth not supported")
        }
    }

    /*
     * Create the GATT server instance, attaching all services and
     * characteristics that should be exposed
     */
    private fun initServer() {
        val service = BluetoothGattService(UUID.fromString(Service_UUID),
                BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val readElapsedCharacteristic = BluetoothGattCharacteristic(UUID.fromString(Read_Characteristic_UUID),
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY)

        val bluetoothGattReadDescriptor = BluetoothGattDescriptor(UUID.fromString("00002901-0000-1000-8000-00805f9b34fb"), BluetoothGattDescriptor.PERMISSION_READ)

        readElapsedCharacteristic.addDescriptor(bluetoothGattReadDescriptor)

        val writeElapsedCharacteristic = BluetoothGattCharacteristic(UUID.fromString(Write_Characteristic_UUID),
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY)

        val bluetoothGattWriteDescriptor = BluetoothGattDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), BluetoothGattDescriptor.PERMISSION_WRITE)

        writeElapsedCharacteristic.addDescriptor(bluetoothGattWriteDescriptor)

        service.addCharacteristic(readElapsedCharacteristic)
        service.addCharacteristic(writeElapsedCharacteristic)

        mGattServer!!.addService(service)
    }

    private val mGattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                centralsDic.put(device.address, device)
                print("onConnectionStateChange STATE_CONNECTED " + device.address)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                centralsDic.remove(device.address)
                print("onConnectionStateChange STATE_DISCONNECTED " + device.address)
                handler.post(Runnable {
                    channel?.invokeMethod("didUnsubscribeFrom", hashMapOf("centraluuidString" to device?.address!!, "characteristicuuidString" to ""))
                })
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice,
                                                 requestId: Int,
                                                 offset: Int,
                                                 characteristic: BluetoothGattCharacteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)

            if (UUID.fromString(Read_Characteristic_UUID) == characteristic.uuid) {

                val cb = object : MethodChannel.Result {
                    override fun success(p0: Any?) {
                        mGattServer?.sendResponse(device,
                                requestId,
                                BluetoothGatt.GATT_SUCCESS,
                                0,
                                p0 as ByteArray)
                    }

                    override fun error(p0: String?, p1: String?, p2: Any?) {

                    }

                    override fun notImplemented() {

                    }
                }
                handler.post(Runnable {
                    channel?.invokeMethod("didReceiveRead", hashMapOf("centraluuidString" to device?.address, "characteristicuuidString" to characteristic.uuid.toString()), cb);
                })
            }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice,
                                                  requestId: Int,
                                                  characteristic: BluetoothGattCharacteristic,
                                                  preparedWrite: Boolean,
                                                  responseNeeded: Boolean,
                                                  offset: Int,
                                                  value: ByteArray) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            Log.i(TAG, "onCharacteristicWriteRequest " + characteristic.uuid.toString())
            mGattServer?.sendResponse(device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    null)
//
            if (UUID.fromString(Write_Characteristic_UUID) == characteristic.uuid) {
                handler.post(Runnable {
                    channel?.invokeMethod("didReceiveWrite", hashMapOf("centraluuidString" to device?.address, "characteristicuuidString" to characteristic.uuid.toString(), "data" to value))
                })
            }
        }

        override fun onNotificationSent(device: BluetoothDevice?, status: Int) {
            super.onNotificationSent(device, status)
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor?) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            Log.i(TAG, "onDescriptorReadRequest " + descriptor?.uuid.toString())
            mGattServer?.sendResponse(device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    null)
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice?, requestId: Int, descriptor: BluetoothGattDescriptor?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value)
            Log.i(TAG, "onDescriptorWriteRequest " + descriptor?.uuid.toString() + "preparedWrite:" + preparedWrite + "responseNeeded:" + responseNeeded + "value:" + value)

            mGattServer?.sendResponse(device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    null)

            if (descriptorsDic.containsKey(descriptor?.uuid.toString())) {
                descriptorsDic.remove(descriptor?.uuid.toString())
                characteristicsDic.remove(descriptor?.characteristic?.uuid.toString())
                handler.post(Runnable {
                    channel?.invokeMethod("didUnsubscribeFrom", hashMapOf("centraluuidString" to device?.address!!, "characteristicuuidString" to descriptor?.characteristic?.uuid.toString()))
                })
            } else {
                descriptorsDic.put(descriptor?.uuid.toString(), descriptor!!)
                characteristicsDic.put(descriptor?.characteristic?.uuid.toString(), descriptor?.characteristic!!)
                handler.post(Runnable {
                    channel?.invokeMethod("didSubscribeTo", hashMapOf("centraluuidString" to device?.address!!, "characteristicuuidString" to descriptor?.characteristic?.uuid.toString()))
                })
            }
        }
    }

    /*
     * Initialize the advertiser
     */
//    private fun startAdvertising() {
//        if (mBluetoothLeAdvertiser == null) return
//
//        val settings = AdvertiseSettings.Builder()
//                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
//                .setConnectable(true)
//                .setTimeout(0)
//                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
//                .build()
//
//        val data = AdvertiseData.Builder()
//                .setIncludeDeviceName(true)
//                .addServiceUuid(ParcelUuid(UUID.fromString(Service_UUID)))
//                .build()
//
//        mBluetoothLeAdvertiser!!.startAdvertising(settings, data, mAdvertiseCallback)
//    }

    /*
     * Terminate the advertiser
     */
    private fun stopAdvertising() {
        if (mGattServer != null) {
            mGattServer!!.close()
        }

        // If stopAdvertising() gets called before close() a null
        // pointer exception is raised.
        mAdvertiser!!.stopAdvertising(mAdvCallback)
    }

    /*
     * Callback handles events from the framework describing
     * if we were successful in starting the advertisement requests.
     */
    private val mAdvertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            print("Peripheral Advertise Started.")
        }

        override fun onStartFailure(errorCode: Int) {
            print("Peripheral Advertise Failed: $errorCode")
        }
    }
}

