package com.example.bledemo

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlin.collections.*
import java.util.*
import java.util.UUID


//useful android constants
private const val ENABLE_BLUETOOTH_REQUEST_CODE = 1
private const val LOCATION_PERMISSION_REQUEST_CODE = 2
//the Client Characteristic Configuration Descriptor UUID is assigned by the Bluetooth foundation to Google and is the basis for all UUIDs used in Android (https://devzone.nordicsemi.com/f/nordic-q-a/24974/client-characteristic-configuration-descriptor-uuid)
private  const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805F9B34FB"

//lidar constants
private const val LIDAR_UUID="c273880d-114c-48b7-8ad2-30af640b3712"//TODO put real one
private const val LIDAR_SERVICE_UUID="c273880d-114c-48b7-8ad2-30af640b3712"//TODO put real one
private const val LIDAR_CHARACTERISTIC_UUID="c273880d-114c-48b7-8ad2-30af640b3712"//TODO put real one
private const val GATT_MAX_MTU_SIZE = 517//TODO put real one

//lidar data packets constants
private const val END_LINE=0x0A // '\n'
private const val DATA_SEPARATOR=0x3B //';'


class MainActivity : AppCompatActivity() {


    //get bluetooth adapter and scanner
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }


    //variable to check if fine location permission has been granted
    private val isLocationPermissionGranted
        get() = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)


    //build filter to only scan for the lidar device with known UUID
    private val filter = ScanFilter.Builder().setServiceUuid(
        ParcelUuid.fromString(LIDAR_UUID)
    ).build()

    private val filters=listOf(filter)


    //scan settings, SCAN_MODE_LOW_LATENCY is used since we need to only search once
    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    //ui components
    private val scanButton: Button by lazy{
        findViewById(R.id.scan_button)
    }
    private val scanResultsRecyclerView:RecyclerView by lazy{
        findViewById(R.id.scan_results_recycler_view)
    }

    //variable to check if device is scanning, if set also change ui text
    private var isScanning = false
        set(value) {
            field = value
            runOnUiThread { scanButton.text = if (value) "Stop Scan" else "Start Scan" }
        }

    //variables to hold and define how to interact with scan results
    private val scanResults = mutableListOf<ScanResult>()
    private val scanResultAdapter: ScanResultAdapter by lazy {
        ScanResultAdapter(scanResults) {result ->
            // User tapped on a scan result, stop scanning, log the devide address and connect to device
            if (isScanning) {
                stopBleScan()
            }

            Log.w("ScanResultAdapter", "Connecting to ${result.device.address}")
            result.device.connectGatt(this, false, gattCallback)
        }
    }

    //ScanCallback variable to save scan results in the ScanResultAdapter object and log errors
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val indexQuery = scanResults.indexOfFirst { it.device.address == result.device.address }
            if (indexQuery != -1) { // A scan result already exists with the same address
                scanResults[indexQuery] = result
                scanResultAdapter.notifyItemChanged(indexQuery)
            } else {
                with(result.device) {
                    Log.i("ScanCallback", "Found BLE device! Name: ${name ?: "Unnamed"}, address: $address")
                }
                scanResults.add(result)
                scanResultAdapter.notifyItemInserted(scanResults.size - 1)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("ScanCallback", "onScanFailed: code $errorCode")
        }
    }

    //gatt service variable, is initialized on connection complete
    private lateinit var bluetoothGatt:BluetoothGatt


    //BluetoothGattCallback object, it is our main way to interact with the gatt server
    private val gattCallback = object : BluetoothGattCallback() {


        //when connection is established we get the BluetoothGatt object here
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceAddress = gatt.device.address

            //connected/disconnected from a device
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully connected to $deviceAddress")

                    //save the BluetoothGatt object, change MTU size and discover the services offered by the device
                    bluetoothGatt = gatt
                    bluetoothGatt.requestMtu(GATT_MAX_MTU_SIZE)
                    Handler(Looper.getMainLooper()).post {
                        bluetoothGatt.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.w("BluetoothGattCallback", "Successfully disconnected from $deviceAddress")
                    gatt.close()
                }
            } else {//error encountered in connecting
                Log.w("BluetoothGattCallback", "Error $status encountered for $deviceAddress! Disconnecting...")
                gatt.close()
            }
        }

        //after connection is established we discover services to complete the setup
        //we can also set up a notification for the lidar data characteristic here
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                Log.w("BluetoothGattCallback", "Discovered ${services.size} services for ${device.address}")
                printGattTable() // See implementation just above this section
                // Consider connection setup as complete here

                //set up notifications for the lidar characteristic so we get a callback every time the data updates
                val lidarServiceUuid = UUID.fromString(LIDAR_SERVICE_UUID)
                val lidarDataCharUuid = UUID.fromString(LIDAR_CHARACTERISTIC_UUID)
                val lidarDataChar = bluetoothGatt.getService(lidarServiceUuid)?.getCharacteristic(lidarDataCharUuid)
                if (lidarDataChar != null) {
                    enableNotifications(lidarDataChar)
                }else{
                    Log.e("BluetoothGattCallback","Error: Lidar characteristic not found")
                }


            }
        }

        //callback happens after we change the MTU size after we established the connection in onConnectionStateChange
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.w("BluetoothGattCallback", "ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
        }

        //after we make a read operation this callback will be called with the data requested in characteristic.value or an error
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Read characteristic $uuid:\n${value.toHexString()}")

                        //we parse the raw data received from the server into a list of LidarPoints
                        val lidarData=parseLidarData(value)
                    }
                    BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Read not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic read failed for $uuid, error: $status")
                    }
                }
            }
        }

        //this call back tells us the result of a write operation on the server
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        Log.i("BluetoothGattCallback", "Wrote to characteristic $uuid | value: ${value.toHexString()}")
                    }
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH -> {
                        Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!")
                    }
                    BluetoothGatt.GATT_WRITE_NOT_PERMITTED -> {
                        Log.e("BluetoothGattCallback", "Write not permitted for $uuid!")
                    }
                    else -> {
                        Log.e("BluetoothGattCallback", "Characteristic write failed for $uuid, error: $status")
                    }
                }
            }
        }

        //this callback happens after we set up a notification for a characteristic and the characteristic is updated by the server
        //if the characteristic is the lidar data we can then attempt to read it and we will receive the data on the onCharacteristicRead callback
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            with(characteristic) {
                Log.i("BluetoothGattCallback", "Characteristic $uuid changed | value: ${value.toHexString()}")

                if(uuid.toString()==LIDAR_CHARACTERISTIC_UUID){
                    readRawLidarData()
                }
            }
        }
    }

    //check if we have a certain permission
    private fun Context.hasPermission(permissionType: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permissionType) ==
                PackageManager.PERMISSION_GRANTED
    }

    //write on a characteristic descriptor on the server, used in setting up notifications
    private fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        bluetoothGatt.let { gatt ->
            descriptor.value = payload
            gatt.writeDescriptor(descriptor)
        }
    }

    //this function enables notifications for the BluetoothGattCharacteristic passed as input
    fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                Log.e("ConnectionManager", "${characteristic.uuid} doesn't support notifications/indications")
                return
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (bluetoothGatt.setCharacteristicNotification(characteristic, true) == false) {
                Log.e("ConnectionManager", "setCharacteristicNotification failed for ${characteristic.uuid}")
                return
            }
            writeDescriptor(cccDescriptor, payload)
        } ?: Log.e("ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!")
    }

    //this function disables notifications for the BluetoothGattCharacteristic passed as input
    fun disableNotifications(characteristic: BluetoothGattCharacteristic) {
        if (!characteristic.isNotifiable() && !characteristic.isIndicatable()) {
            Log.e("ConnectionManager", "${characteristic.uuid} doesn't support indications/notifications")
            return
        }

        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (bluetoothGatt.setCharacteristicNotification(characteristic, false) == false) {
                Log.e("ConnectionManager", "setCharacteristicNotification failed for ${characteristic.uuid}")
                return
            }
            writeDescriptor(cccDescriptor, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)
        } ?: Log.e("ConnectionManager", "${characteristic.uuid} doesn't contain the CCC descriptor!")
    }

    //transforms a ByteArray object in a string, mostly for logging purposes
    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }


    //log discovered services in a device for debug
    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.i("printGattTable", "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }


    //these small functions are used to simplify checking characteristic properties using containsProperty
    private fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    private fun BluetoothGattCharacteristic.isWritable(): Boolean =
    containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    private fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    private fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    private fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

    private fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    //using the UUIDs provided in the constants read the lidar characteristic
    private fun readRawLidarData(){
        val lidarServiceUuid = UUID.fromString(LIDAR_SERVICE_UUID)
        val lidarDataCharUuid = UUID.fromString(LIDAR_CHARACTERISTIC_UUID)
        val lidarDataChar = bluetoothGatt.getService(lidarServiceUuid)?.getCharacteristic(lidarDataCharUuid)
        if (lidarDataChar?.isReadable() == true) {
            bluetoothGatt.readCharacteristic(lidarDataChar)
        }
    }

    //parse the lidar data in the desired format, returns null if the header is wrong
    private fun parseLidarData(data:ByteArray):List<LidarPoint>?{

        if(data[0]!='H'.toByte()){
            Log.e("parseLidarDataError","Header line does not contain control character")
            return null
        }

        val temp=mutableListOf<Char>()
        var i=2

        do{
            temp.add(data[i].toChar())
            i++
        }while(data[i]!=END_LINE.toByte())

        var angle=temp.joinToString("").toInt()
        temp.clear()
        i++

        val list=mutableListOf<LidarPoint>()
        var tempIntensity=0
        var tempDistance=0

        while(i<data.size){
            do{
                temp.add(data[i].toChar())
                i++
            }while(data[i]!=DATA_SEPARATOR.toByte())

            tempDistance=temp.joinToString("").toInt()
            temp.clear()
            i++

            do{
                temp.add(data[i].toChar())
                i++
            }while(i!=data.size && data[i]!=END_LINE.toByte())

            tempIntensity=temp.joinToString("").toInt()
            temp.clear()
            i++

            list.add(LidarPoint(angle++,tempDistance,tempIntensity))
        }

        return list
    }

    //class that contains a single lidar data point
    data class LidarPoint(val angle: Int,val distance: Int, val intensity: Int)

    //function to write data in ByteArray format in a characteristic on the server
    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        val writeType = when {
            characteristic.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.isWritableWithoutResponse() -> {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> error("Characteristic ${characteristic.uuid} cannot be written to")
        }

        bluetoothGatt.let { gatt ->
            characteristic.writeType = writeType
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //setup listener on the scan button and the recycler view
        scanButton.setOnClickListener {
            if (isScanning) {
                stopBleScan()
            } else {
                startBleScan()
            }
        }
        setupRecyclerView()
    }

        override fun onResume(){
            super.onResume()

            //after create completes or when the app is reopened we check if bluetooth is enabled and prompt to enable it
            if (!bluetoothAdapter.isEnabled) {
                promptEnableBluetooth()
            }
        }

    //if bluetooth is not enabled prompt on the screen to enable it
    private fun promptEnableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST_CODE)
        }
    }

    //if user did not enable the bluetooth in the prompt show it again
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ENABLE_BLUETOOTH_REQUEST_CODE -> {
                if (resultCode != Activity.RESULT_OK) {
                    promptEnableBluetooth()
                }
            }
        }
    }


    //clear the previous scan results and start a new scan
    private fun startBleScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isLocationPermissionGranted) {
            requestLocationPermission()
        }
        else {
            scanResults.clear()
            scanResultAdapter.notifyDataSetChanged()
            //bleScanner.startScan(filters, scanSettings, scanCallback)
            bleScanner.startScan(null, scanSettings, scanCallback)
            isScanning = true
        }
    }

    //stop an ongoing scan
    private fun stopBleScan() {
        bleScanner.stopScan(scanCallback)
        isScanning = false
    }

    //to scan for devices the fine location permission is required, prompt the user to give location permission
    private fun requestLocationPermission() {
        if (isLocationPermissionGranted) {
            return
        }
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Location permission required")
                .setMessage("Starting from Android M (6.0), the system requires apps to be granted " +
                        "location access in order to scan for BLE devices.")
                .setPositiveButton(android.R.string.ok){ _: DialogInterface, _: Int ->
                    requestPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
                .show()
        }
    }

    //if user did not give location permission in the prompt show it again else start the scan
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_DENIED) {
                    requestLocationPermission()
                } else {
                    startBleScan()
                }
            }
        }
    }

    //request a specified permission
    private fun Activity.requestPermission(permission: String, requestCode: Int) {
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    //set up the recycler view using the ScanResultAdapter class
    private fun setupRecyclerView() {
        scanResultsRecyclerView.apply {
            adapter = scanResultAdapter
            layoutManager = LinearLayoutManager(
                this@MainActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false
        }

        val animator = scanResultsRecyclerView.itemAnimator
        if (animator is SimpleItemAnimator) {
            animator.supportsChangeAnimations = false
        }
    }



}